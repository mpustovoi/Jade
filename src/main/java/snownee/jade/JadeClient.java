package snownee.jade;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.InfestedBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import snownee.jade.addon.vanilla.VanillaPlugin;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.config.IWailaConfig.DisplayMode;
import snownee.jade.api.config.IWailaConfig.IConfigOverlay;
import snownee.jade.api.config.IWailaConfig.TTSMode;
import snownee.jade.gui.HomeConfigScreen;
import snownee.jade.impl.config.PluginConfig;
import snownee.jade.overlay.DisplayHelper;
import snownee.jade.overlay.WailaTickHandler;
import snownee.jade.util.ClientPlatformProxy;
import snownee.jade.util.ModIdentification;
import snownee.jade.util.PlatformProxy;

public final class JadeClient {

	public static KeyMapping openConfig;
	public static KeyMapping showOverlay;
	public static KeyMapping toggleLiquid;
	public static KeyMapping showDetails;
	public static KeyMapping narrate;
	public static KeyMapping showRecipes;
	public static KeyMapping showUses;

	public static void initClient() {
		openConfig = ClientPlatformProxy.registerKeyBinding("config", 320);
		showOverlay = ClientPlatformProxy.registerKeyBinding("show_overlay", 321);
		toggleLiquid = ClientPlatformProxy.registerKeyBinding("toggle_liquid", 322);
		if (ClientPlatformProxy.shouldRegisterRecipeViewerKeys()) {
			showRecipes = ClientPlatformProxy.registerKeyBinding("show_recipes", 323);
			showRecipes = ClientPlatformProxy.registerKeyBinding("show_uses", 324);
		}
		narrate = ClientPlatformProxy.registerKeyBinding("narrate", 325);
		showDetails = ClientPlatformProxy.registerKeyBinding("show_details", 340);

		((ReloadableResourceManager) Minecraft.getInstance().getResourceManager()).registerReloadListener(ModIdentification.INSTANCE);
	}

	public static void onKeyPressed(int action) {
		if (action != 1)
			return;

		if (openConfig.isDown()) {
			Jade.CONFIG.invalidate();
			Minecraft.getInstance().setScreen(new HomeConfigScreen(null));
		}

		if (showOverlay.isDown()) {
			DisplayMode mode = Jade.CONFIG.get().getGeneral().getDisplayMode();
			if (mode == IWailaConfig.DisplayMode.TOGGLE) {
				Jade.CONFIG.get().getGeneral().setDisplayTooltip(!Jade.CONFIG.get().getGeneral().shouldDisplayTooltip());
			}
		}

		if (toggleLiquid.isDown()) {
			Jade.CONFIG.get().getGeneral().setDisplayFluids(!Jade.CONFIG.get().getGeneral().shouldDisplayFluids());
		}

		if (narrate.isDown()) {
			if (Jade.CONFIG.get().getGeneral().getTTSMode() == TTSMode.TOGGLE) {
				Jade.CONFIG.get().getGeneral().toggleTTS();
			} else if (WailaTickHandler.instance().tooltipRenderer != null) {
				WailaTickHandler.narrate(WailaTickHandler.instance().tooltipRenderer.getTooltip(), false);
			}
		}
	}

	//public static boolean hasJEI = ModList.get().isLoaded("jei");
	public static boolean hideModName;

	public static void onTooltip(List<Component> tooltip, ItemStack stack) {
		appendModName(tooltip, stack);
		if (Jade.CONFIG.get().getGeneral().isDebug() && stack.hasTag()) {
			tooltip.add(NbtUtils.toPrettyComponent(stack.getTag()));
		}
	}

	private static void appendModName(List<Component> tooltip, ItemStack stack) {
		if (hideModName || !Jade.CONFIG.get().getGeneral().showItemModNameTooltip())
			return;
		String name = String.format(Jade.CONFIG.get().getFormatting().getModName(), ModIdentification.getModName(stack));
		tooltip.add(new TextComponent(name));
	}

	@Nullable
	public static Accessor<?> builtInOverrides(HitResult hitResult, @Nullable Accessor<?> accessor, @Nullable Accessor<?> originalAccessor) {
		Player player = accessor.getPlayer();
		if (player.isCreative() || player.isSpectator())
			return accessor;
		IWailaClientRegistration client = VanillaPlugin.CLIENT_REGISTRATION;
		if (accessor instanceof BlockAccessor target) {
			if (target.getBlock() instanceof TrappedChestBlock) {
				BlockState state = VanillaPlugin.getCorrespondingNormalChest(target.getBlockState());
				if (state != target.getBlockState()) {
					return client.createBlockAccessor(state, target.getBlockEntity(), target.getLevel(), player, target.getServerData(), target.getHitResult(), target.isServerConnected());
				}
			} else if (target.getBlock() instanceof InfestedBlock) {
				Block block = ((InfestedBlock) target.getBlock()).getHostBlock();
				return client.createBlockAccessor(block.defaultBlockState(), target.getBlockEntity(), target.getLevel(), player, target.getServerData(), target.getHitResult(), target.isServerConnected());
			} else if (target.getBlock() == Blocks.POWDER_SNOW) {
				Block block = Blocks.SNOW_BLOCK;
				return client.createBlockAccessor(block.defaultBlockState(), null, target.getLevel(), player, target.getServerData(), target.getHitResult(), target.isServerConnected());
			}
		}
		return accessor;
	}

	private static float savedProgress;
	private static float progressAlpha;
	private static boolean canHarvest;

	public static void drawBreakingProgress(ITooltip tooltip, Rect2i rect, PoseStack matrixStack, Accessor<?> accessor) {
		if (!PluginConfig.INSTANCE.get(Identifiers.MC_BREAKING_PROGRESS)) {
			progressAlpha = 0;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		MultiPlayerGameMode playerController = mc.gameMode;
		if (playerController == null || playerController.destroyBlockPos == null) {
			return;
		}
		BlockState state = mc.level.getBlockState(playerController.destroyBlockPos);
		if (playerController.isDestroying())
			canHarvest = PlatformProxy.isCorrectToolForDrops(state, mc.player);
		int color = canHarvest ? 0xFFFFFF : 0xFF4444;
		int height = rect.getHeight();
		int width = rect.getWidth();
		if (!VanillaPlugin.CLIENT_REGISTRATION.getConfig().getOverlay().getSquare()) {
			height -= 1;
			width -= 2;
		}
		progressAlpha += mc.getDeltaFrameTime() * (playerController.isDestroying() ? 0.1F : -0.1F);
		if (playerController.isDestroying()) {
			progressAlpha = Math.min(progressAlpha, 0.53F); //0x88 = 0.53 * 255
			float progress = state.getDestroyProgress(mc.player, mc.player.level, playerController.destroyBlockPos);
			if (playerController.destroyProgress + progress >= 1) {
				progressAlpha = 1;
			}
			progress = playerController.destroyProgress + mc.getFrameTime() * progress;
			progress = Mth.clamp(progress, 0, 1);
			savedProgress = progress;
		} else {
			progressAlpha = Math.max(progressAlpha, 0);
		}
		color = IConfigOverlay.applyAlpha(color, progressAlpha);
		DisplayHelper.fill(matrixStack, 0, height - 1, width * savedProgress, height, color);
	}

}
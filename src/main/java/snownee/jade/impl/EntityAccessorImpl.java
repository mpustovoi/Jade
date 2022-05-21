package snownee.jade.impl;

import java.util.List;
import java.util.function.Function;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import snownee.jade.Jade;
import snownee.jade.api.AccessorImpl;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IJadeProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.IElement;
import snownee.jade.impl.config.PluginConfig;
import snownee.jade.impl.ui.ElementHelper;
import snownee.jade.impl.ui.ItemStackElement;
import snownee.jade.network.RequestEntityPacket;
import snownee.jade.overlay.RayTracing;
import snownee.jade.util.WailaExceptionHandler;

/**
 * Class to get information of entity target and context.
 */
public class EntityAccessorImpl extends AccessorImpl<EntityHitResult> implements EntityAccessor {

	private final Entity entity;

	public EntityAccessorImpl(Entity entity, Level level, Player player, CompoundTag serverData, EntityHitResult hit, boolean serverConnected) {
		super(level, player, serverData, hit, serverConnected);
		this.entity = entity;
	}

	@Override
	public Entity getEntity() {
		return entity;
	}

	@Override
	public ItemStack getPickedResult() {
		return getEntity().getPickedResult(getHitResult());
	}

	@Override
	public IElement _getIcon() {
		IElement icon = null;
		if (entity instanceof ItemEntity) {
			icon = ItemStackElement.of(((ItemEntity) entity).getItem());
		} else {
			ItemStack stack = getPickedResult();
			if ((!(stack.getItem() instanceof SpawnEggItem) || !(entity instanceof LivingEntity)))
				icon = ItemStackElement.of(stack);
		}

		for (IEntityComponentProvider provider : WailaClientRegistration.INSTANCE.getEntityIconProviders(entity, PluginConfig.INSTANCE::get)) {
			try {
				IElement element = provider.getIcon(this, PluginConfig.INSTANCE, icon);
				if (!RayTracing.isEmptyElement(element))
					icon = element;
			} catch (Throwable e) {
				WailaExceptionHandler.handleErr(e, provider, null);
			}
		}
		return icon;
	}

	@Override
	public void _gatherComponents(Function<IJadeProvider, ITooltip> tooltipProvider) {
		List<IEntityComponentProvider> providers = WailaClientRegistration.INSTANCE.getEntityProviders(getEntity(), PluginConfig.INSTANCE::get);
		for (IEntityComponentProvider provider : providers) {
			ITooltip tooltip = tooltipProvider.apply(provider);
			try {
				ElementHelper.INSTANCE.setCurrentUid(provider.getUid());
				provider.appendTooltip(tooltip, this, PluginConfig.INSTANCE);
			} catch (Throwable e) {
				WailaExceptionHandler.handleErr(e, provider, tooltip);
			} finally {
				ElementHelper.INSTANCE.setCurrentUid(null);
			}
		}
	}

	@Override
	public boolean shouldDisplay() {
		return Jade.CONFIG.get().getGeneral().getDisplayEntities();
	}

	@Override
	public void _requestData(boolean showDetails) {
		Jade.NETWORK.sendToServer(new RequestEntityPacket(entity, showDetails));
	}

	@Override
	public boolean shouldRequestData() {
		return !WailaCommonRegistration.INSTANCE.getEntityNBTProviders(entity).isEmpty();
	}

	@Override
	public boolean _verifyData(CompoundTag serverData) {
		if (!serverData.contains("WailaEntityID"))
			return false;
		return serverData.getInt("WailaEntityID") == entity.getId();
	}

	@Override
	public Object _getTrackObject() {
		return getEntity();
	}

}

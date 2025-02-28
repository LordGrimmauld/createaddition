package com.mrh0.createaddition.entities.overcharged_hammer;

import javax.annotation.Nullable;

import com.mrh0.createaddition.index.CAEntities;
import com.mrh0.createaddition.index.CAItems;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.NetworkHooks;

public class OverchargedHammerEntity extends AbstractArrowEntity {
	private static final DataParameter<Byte> LOYALTY_LEVEL = EntityDataManager.defineId(OverchargedHammerEntity.class,
			DataSerializers.BYTE);
	private static final DataParameter<Boolean> ENCHANTED = EntityDataManager.defineId(OverchargedHammerEntity.class,
			DataSerializers.BOOLEAN);
	private ItemStack thrownStack = new ItemStack(CAItems.OVERCHARGED_HAMMER.get());
	private boolean dealtDamage;
	public int returningTicks;
	
	public OverchargedHammerEntity(EntityType<OverchargedHammerEntity> type, World world) {
		super(type, world);
	}

	public OverchargedHammerEntity(World world, LivingEntity living, ItemStack stack) {
		super(CAEntities.OVERCHARGED_HAMMER_ENTITY.get(), living, world);
		this.thrownStack = stack.copy();
		this.entityData.set(LOYALTY_LEVEL, (byte) EnchantmentHelper.getLoyalty(stack));
		this.entityData.set(ENCHANTED, stack.hasFoil());
	}

	public OverchargedHammerEntity(World world, double x, double y, double z) {
		super(CAEntities.OVERCHARGED_HAMMER_ENTITY.get(), x, y, z, world);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		this.entityData.define(LOYALTY_LEVEL, (byte) 0);
		this.entityData.define(ENCHANTED, false);
	}

	/**
	 * Called to update the entity's position/logic.
	 */
	@Override
	public void tick() {
		if (this.inGroundTime > 4) {
			this.dealtDamage = true;
		}

		Entity entity = this.getOwner();
		if ((this.dealtDamage || this.isNoPhysics()) && entity != null) {
			int i = 3;
			this.setNoPhysics(true);
			Vector3d vector3d = new Vector3d(entity.getX() - this.getX(), entity.getEyeY() - this.getY(),
					entity.getZ() - this.getZ());
			this.setPosRaw(this.getX(), this.getY() + vector3d.y * 0.015D * (double) i, this.getZ());
			if (this.level.isClientSide) {
				this.yOld = this.getY();
			}

			double d0 = 0.05D * (double) i;
			this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(vector3d.normalize().scale(d0)));
			if (this.returningTicks == 0) {
				this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
			}

			++this.returningTicks;
		}

		super.tick();
	}

	@Override
	protected ItemStack getPickupItem() {
		return this.thrownStack.copy();
	}

	@OnlyIn(Dist.CLIENT)
	public boolean isEnchanted() {
		return this.entityData.get(ENCHANTED);
	}

	/**
	 * Gets the EntityRayTraceResult representing the entity hit
	 */
	@Nullable
	@Override
	protected EntityRayTraceResult findHitEntity(Vector3d v1, Vector3d v2) {
		return this.dealtDamage ? null : super.findHitEntity(v1, v2);
	}

	/**
	 * Called when the arrow hits an entity
	 */
	@Override
	protected void onHitEntity(EntityRayTraceResult entityRay) {
		Entity entity = entityRay.getEntity();
		float f = 8.0F;
		if (entity instanceof LivingEntity) {
			LivingEntity livingentity = (LivingEntity) entity;
			f += EnchantmentHelper.getDamageBonus(this.thrownStack, livingentity.getMobType());
		}

		Entity entity1 = this.getOwner();
		DamageSource damagesource = DamageSource.trident(this, (Entity) (entity1 == null ? this : entity1));
		this.dealtDamage = true;
		SoundEvent soundevent = SoundEvents.ANVIL_HIT;
		if (entity.hurt(damagesource, f)) {
			if (entity instanceof LivingEntity) {
				LivingEntity livingentity1 = (LivingEntity) entity;
				if (entity1 instanceof LivingEntity) {
					EnchantmentHelper.doPostHurtEffects(livingentity1, entity1);
					EnchantmentHelper.doPostDamageEffects((LivingEntity) entity1, livingentity1);
				}

				this.doPostHurtEffects(livingentity1);
			}
		}

		this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
		float f1 = 1.0F;
		if (this.level instanceof ServerWorld && this.level.isThundering()
				&& EnchantmentHelper.hasChanneling(this.thrownStack)) {
			BlockPos blockpos = entity.blockPosition();
			if (this.level.canSeeSky(blockpos)) {
				LightningBoltEntity lightningboltentity = EntityType.LIGHTNING_BOLT.create(this.level);
				lightningboltentity.moveTo(Vector3d.atBottomCenterOf(blockpos));
				lightningboltentity
						.setCause(entity1 instanceof ServerPlayerEntity ? (ServerPlayerEntity) entity1 : null);
				this.level.addFreshEntity(lightningboltentity);
				soundevent = SoundEvents.TRIDENT_THUNDER;
				f1 = 5.0F;
			}
		}

		this.playSound(soundevent, f1, 1.0F);
	}

	/**
	 * The sound made when an entity is hit by this projectile
	 */
	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.ANVIL_LAND;
	}

	/**
	 * Called by a player entity when they collide with an entity
	 */
	@Override
	public void playerTouch(PlayerEntity player) {
		Entity entity = this.getOwner();
		if (entity == null || entity.getUUID() == player.getUUID()) {
			super.playerTouch(player);
		}
	}

	/**
	 * (abstract) Protected helper method to read subclass entity data from NBT.
	 */
	@Override
	public void readAdditionalSaveData(CompoundNBT nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("Hammer", 10)) {
			this.thrownStack = ItemStack.of(nbt.getCompound("Hammer"));
		}

		this.dealtDamage = nbt.getBoolean("DealtDamage");
		this.entityData.set(LOYALTY_LEVEL, (byte) EnchantmentHelper.getLoyalty(this.thrownStack));
	}

	@Override
	public void addAdditionalSaveData(CompoundNBT nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.put("Hammer", this.thrownStack.save(new CompoundNBT()));
		nbt.putBoolean("DealtDamage", this.dealtDamage);
	}

	@Override
	public void tickDespawn() {
		int i = this.entityData.get(LOYALTY_LEVEL);
		if (this.pickup != AbstractArrowEntity.PickupStatus.ALLOWED || i <= 0) {
			super.tickDespawn();
		}
	}

	@Override
	protected float getWaterInertia() {
		return 0.99F;
	}

	@OnlyIn(Dist.CLIENT)
	@Override
	public boolean shouldRender(double x, double y, double z) {
		return true;
	}
	
	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<OverchargedHammerEntity> entityBuilder = (EntityType.Builder<OverchargedHammerEntity>) builder;
		return entityBuilder.sized(0.25f, 0.25f);
	}

	@Override
	public IPacket<?> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}
}

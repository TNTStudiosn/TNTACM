// src/main/java/com/TNTStudios/tntacm/entity/custom/NebulaEntity.java
package com.TNTStudios.tntacm.entity.custom;

import com.TNTStudios.tntacm.entity.custom.projectile.BlueLaserProjectileEntity;
import com.TNTStudios.tntacm.mixin.LivingEntityAccessor;
import com.TNTStudios.tntacm.networking.ModMessages;
import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class NebulaEntity extends LivingEntity implements GeoEntity {

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // ===== Dimensiones del modelo (referencia para el AABB rotatorio) =====
    private static final float ENTITY_WIDTH = 2.5f;
    private static final float ENTITY_LENGTH = 9.0f;
    private static final float ENTITY_HEIGHT = 2.5f;

    // ===== Modelo de vuelo (thrusts y damping) =====
    private static final double FORWARD_THRUST = 0.08D;   // empuje W/S
    private static final double STRAFE_THRUST = 0.06D;    // empuje A/D (sin girar la nave)
    private static final double VERTICAL_THRUST = 0.07D;    // empuje vertical (Espacio/Shift)
    private static final double MAX_SPEED = 1.8D;       // límite de velocidad lineal
    private static final double DAMPENING_FACTOR = 0.975D;    // amortiguación inercial

    // ===== Armamento =====
    private int fireCooldown = 0;
    // PERF: Cooldown bajo para alta cadencia, controlado en servidor.
    private static final int MAX_FIRE_COOLDOWN = 1;
    private static final double PROJECTILE_SPEED = 5.0D;

    // ===== Límites y velocidades de rotación =====
    private static final float MIN_PITCH = -20.0f;
    private static final float MAX_PITCH = 50.0f;

    public NebulaEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
        this.setNoGravity(true);
        this.setStepHeight(1.0F);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 50.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ==================== TICK / ROTACIÓN ====================
    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient()) {
            if (this.fireCooldown > 0) {
                this.fireCooldown--;
            }
        }

        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            final float newPitch = MathHelper.clamp(pilot.getPitch(), MIN_PITCH, MAX_PITCH);
            final float newYaw = pilot.getYaw();

            this.setYaw(newYaw);
            this.setPitch(newPitch);
            this.setBodyYaw(newYaw);
            this.setHeadYaw(newYaw);

            if (this.getWorld().isClient()) {
                this.prevYaw = newYaw;
                this.prevPitch = newPitch;
                this.prevBodyYaw = newYaw;
                this.prevHeadYaw = newYaw;
            }
        }

        if (!this.hasPassengers()) {
            this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
        }
    }

    @Override
    protected Box calculateBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        float yawRad = this.getYaw() * (MathHelper.PI / 180.0F);
        float cos = Math.abs(MathHelper.cos(yawRad));
        float sin = Math.abs(MathHelper.sin(yawRad));

        double halfWidth = (ENTITY_WIDTH * cos + ENTITY_LENGTH * sin) / 2.0;
        double halfLength = (ENTITY_WIDTH * sin + ENTITY_LENGTH * cos) / 2.0;

        double minY = y;
        double maxY = y + ENTITY_HEIGHT;

        return new Box(
                x - halfWidth, minY, z - halfLength,
                x + halfWidth, maxY, z + halfLength
        );
    }

    // ==================== MOVIMIENTO ====================
    @Override
    public void travel(Vec3d movementInput) {
        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            float forwardInput = pilot.forwardSpeed;
            float sidewaysInput = pilot.sidewaysSpeed;
            boolean ascend = ((LivingEntityAccessor) pilot).isJumpingInput();
            boolean descend = pilot.isSneaking();

            Vec3d forwardDir = this.getRotationVector();
            Vec3d upDir = new Vec3d(0, 1, 0);
            Vec3d sideDir = forwardDir.crossProduct(upDir).normalize();

            Vec3d totalThrust = Vec3d.ZERO;

            if (forwardInput != 0.0F) {
                totalThrust = totalThrust.add(forwardDir.multiply(forwardInput * FORWARD_THRUST));
            }
            if (sidewaysInput != 0.0F) {
                totalThrust = totalThrust.add(sideDir.multiply(-sidewaysInput * STRAFE_THRUST));
            }
            if (ascend) {
                totalThrust = totalThrust.add(upDir.multiply(VERTICAL_THRUST));
            }
            if (descend) {
                totalThrust = totalThrust.add(upDir.multiply(-VERTICAL_THRUST));
            }

            this.addVelocity(totalThrust);
            if (this.getVelocity().lengthSquared() > MAX_SPEED * MAX_SPEED) {
                this.setVelocity(this.getVelocity().normalize().multiply(MAX_SPEED));
            }

            this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
            this.move(MovementType.SELF, this.getVelocity());
            return;
        }
        super.travel(movementInput);
    }

    //region Armamento
    /**
     * Dispara un proyectil desde la perspectiva del piloto.
     * @param shootingDir La dirección de disparo, calculada desde la cámara del cliente.
     */
    public void fireProjectile(Vec3d shootingDir) {
        if (this.getWorld().isClient() || this.fireCooldown > 0) return;

        Entity pilot = this.getControllingPassenger();
        if (!(pilot instanceof PlayerEntity player)) return;

        this.fireCooldown = MAX_FIRE_COOLDOWN;
        World world = this.getWorld();

        // El proyectil nace en la posición de los "ojos" del jugador para que coincida con la retícula.
        Vec3d spawnPos = player.getCameraPosVec(1.0f);

        BlueLaserProjectileEntity projectile = new BlueLaserProjectileEntity(world, spawnPos.x, spawnPos.y, spawnPos.z);
        projectile.setOwner(pilot);

        // La velocidad es la dirección de la cámara + la inercia de la nave.
        Vec3d projectileVelocity = shootingDir.multiply(PROJECTILE_SPEED).add(this.getVelocity());
        projectile.setVelocity(projectileVelocity);

        world.spawnEntity(projectile);
    }
    //endregion

    // ==================== INTERACCIÓN / PASAJEROS ====================
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!this.getWorld().isClient() && passenger instanceof ServerPlayerEntity player) {
            ServerPlayNetworking.send(player, ModMessages.ENTER_SHIP_VIEW_ID, PacketByteBufs.empty());
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if (!this.getWorld().isClient() && passenger instanceof ServerPlayerEntity player) {
            ServerPlayNetworking.send(player, ModMessages.EXIT_SHIP_VIEW_ID, PacketByteBufs.empty());
        }
        super.removePassenger(passenger);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient && !player.isSneaking() && !this.hasPassengers()) {
            player.startRiding(this);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public double getMountedHeightOffset() {
        return 0.90D;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity firstPassenger = this.getFirstPassenger();
        return firstPassenger instanceof PlayerEntity ? (PlayerEntity) firstPassenger : null;
    }

    // ==================== Sonidos / Comportamiento base ====================
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return source.isSourceCreativePlayer(); }
    @Override public boolean isPushable() { return false; }
    @Override public void fall(double height, boolean onGround, BlockState state, BlockPos pos) { }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { }
    @Override public Iterable<ItemStack> getArmorItems() { return ImmutableList.of(); }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) { }
    @Override public Arm getMainArm() { return Arm.RIGHT; }

    // ==================== GECKOLIB ====================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private <E extends GeoEntity> PlayState predicate(AnimationState<E> event) {
        event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.nebula.flotar"));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
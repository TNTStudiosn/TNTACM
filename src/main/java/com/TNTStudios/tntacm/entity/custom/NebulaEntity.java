package com.TNTStudios.tntacm.entity.custom;

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

    //region Flight Model Constants
    private static final double FORWARD_THRUST = 0.08D;
    private static final double STRAFE_THRUST = 0.06D;
    private static final double VERTICAL_THRUST = 0.07D;
    private static final double MAX_SPEED = 1.8D;
    private static final double DAMPENING_FACTOR = 0.975D;
    //endregion

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

    //region Movement & Ticking
    @Override
    public void tick() {
        super.tick();

        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            // --- NUEVA LÓGICA DE ROTACIÓN CON LÍMITES HORIZONTAL Y VERTICAL ---

            // --- 1. CONFIGURACIÓN DE LÍMITES (¡Ajusta estos valores!) ---
            final float minPitch = -30.0f; // Ángulo máximo hacia ARRIBA (más negativo = más arriba)
            final float maxPitch = 50.0f;  // Ángulo máximo hacia ABAJO (más positivo = más abajo)
            final float maxYawSpeed = 3.0f;  // Velocidad máxima de giro en grados por tick

            // --- 2. LÍMITE VERTICAL (PITCH) ---
            float clampedPitch = MathHelper.clamp(pilot.getPitch(), minPitch, maxPitch);

            // --- 3. LÍMITE HORIZONTAL (YAW) ---
            // Obtenemos la rotación actual de la nave antes de cualquier cambio.
            float currentYaw = this.getYaw();
            // Calculamos cuánto ha intentado girar el jugador desde el último tick.
            float yawDelta = MathHelper.wrapDegrees(pilot.getYaw() - currentYaw);
            // Limitamos esa diferencia a la velocidad máxima de giro.
            float clampedYawDelta = MathHelper.clamp(yawDelta, -maxYawSpeed, maxYawSpeed);
            // Calculamos la nueva rotación horizontal de la nave.
            float newYaw = currentYaw + clampedYawDelta;

            // --- 4. APLICACIÓN DE LÍMITES ---
            // Forzamos la rotación del piloto para que coincida con los límites de la nave.
            // Esto evita que la cámara se "desenganche" y vuelva de golpe.
            pilot.setYaw(newYaw);
            pilot.setPitch(clampedPitch);

            // Sincronizamos la rotación de la nave con los nuevos valores limitados.
            this.setRotation(newYaw, clampedPitch);
            this.setBodyYaw(newYaw);
            this.setHeadYaw(newYaw);
        }

        if (!this.hasPassengers()) {
            this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
        }
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            float forwardInput = pilot.forwardSpeed; // W/S
            float sidewaysInput = pilot.sidewaysSpeed; // A/D
            boolean ascend = ((LivingEntityAccessor) pilot).isJumpingInput(); // Espacio
            boolean descend = pilot.isSneaking(); // Shift

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
    //endregion

    //region Player Interaction & Collision
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
        if (firstPassenger instanceof PlayerEntity) {
            return (PlayerEntity) firstPassenger;
        }
        return null;
    }
    //endregion

    //region Entity Properties & Required Overrides
    @Override
    public boolean damage(DamageSource source, float amount) { return false; }
    @Override
    public boolean isInvulnerableTo(DamageSource source) { return source.isSourceCreativePlayer(); }
    @Override
    public boolean isPushable() { return false; }
    @Override
    public void fall(double height, boolean onGround, BlockState state, BlockPos pos) { }
    @Override
    public Iterable<ItemStack> getArmorItems() { return ImmutableList.of(); }
    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) { }
    @Override
    public Arm getMainArm() { return Arm.RIGHT; }
    //endregion

    //region GeckoLib Animations
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
    //endregion
}
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

    // ==================== MODELO DE VUELO ====================
    private static final double FORWARD_THRUST   = 0.08D;
    private static final double STRAFE_THRUST    = 0.06D;
    private static final double VERTICAL_THRUST  = 0.07D;
    private static final double MAX_SPEED        = 1.8D;
    private static final double DAMPENING_FACTOR = 0.975D;

    // Límites y velocidades de rotación (en grados/tick @20TPS)
    private static final float MIN_PITCH = -30.0f;  // arriba
    private static final float MAX_PITCH =  50.0f;  // abajo
    private static final float MAX_YAW_SPEED_DEG = 6.0f; // 3.0f era muy lento y generaba sensación de “lag”
    // =========================================================

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

        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            // 1) Leo intención del jugador
            final float desiredPitch = MathHelper.clamp(pilot.getPitch(), MIN_PITCH, MAX_PITCH);

            // 2) Calculo delta yaw hacia el objetivo del jugador (envuelvo para evitar saltos ±180)
            final float currentYaw = this.getYaw();
            final float yawDelta    = MathHelper.wrapDegrees(pilot.getYaw() - currentYaw);

            // 3) Limito velocidad de giro por tick para que la nave tenga inercia horizontal
            final float clampedYawDelta = MathHelper.clamp(yawDelta, -MAX_YAW_SPEED_DEG, MAX_YAW_SPEED_DEG);
            final float newYaw = currentYaw + clampedYawDelta;

            // 4) Mantengo cámara/jugador acoplados a la nave (evito que la cámara “se me vaya” delante)
            pilot.setYaw(newYaw);
            pilot.setPitch(desiredPitch);

            // 5) Aplico la rotación a la nave…
            this.setYaw(newYaw);
            this.setPitch(desiredPitch);
            this.setBodyYaw(newYaw);
            this.setHeadYaw(newYaw);

            // 6) …y **en cliente** sincronizo prev* = current* para que el render NO interpole este frame.
            //    Esto elimina el efecto de “el modelo va un poquito tarde” al girar con teclado + ratón.
            if (this.getWorld().isClient()) {
                this.prevYaw = newYaw;
                this.prevPitch = desiredPitch;
                this.prevBodyYaw = newYaw;
                this.prevHeadYaw = newYaw;
            }
        }

        // Freno cuando voy suelto, nada raro aquí.
        if (!this.hasPassengers()) {
            this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
        }
    }

    // ==================== MOVIMIENTO ====================
    @Override
    public void travel(Vec3d movementInput) {
        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            // Notas: mantengo mis direcciones derivadas de la rotación actual,
            // y compongo empuje con una leve amortiguación para “nave espacial”
            float forwardInput  = pilot.forwardSpeed;   // W/S
            float sidewaysInput = pilot.sidewaysSpeed;  // A/D
            boolean ascend  = ((LivingEntityAccessor) pilot).isJumpingInput(); // Espacio
            boolean descend = pilot.isSneaking();                              // Shift

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
        if (firstPassenger instanceof PlayerEntity) {
            return (PlayerEntity) firstPassenger;
        }
        return null;
    }

    // ==================== REQUERIDOS / GECKOLIB ====================
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return source.isSourceCreativePlayer(); }
    @Override public boolean isPushable() { return false; }
    @Override public void fall(double height, boolean onGround, BlockState state, BlockPos pos) { }
    @Override public Iterable<ItemStack> getArmorItems() { return ImmutableList.of(); }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) { }
    @Override public Arm getMainArm() { return Arm.RIGHT; }

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
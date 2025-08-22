package com.TNTStudios.tntacm.entity.custom;

import com.TNTStudios.tntacm.mixin.LivingEntityAccessor;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
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
            // Sincroniza la rotación de la nave con la del piloto en AMBOS LADOS (cliente y servidor).
            // Esto es crucial para que el servidor calcule el movimiento en travel() con la orientación correcta.
            float pilotYaw = pilot.getYaw();
            float pilotPitch = pilot.getPitch();

            this.setRotation(pilotYaw, pilotPitch);
            this.setBodyYaw(pilotYaw); // El bodyYaw es importante para la lógica de movimiento y animación.
            this.setHeadYaw(pilotYaw);

            // La actualización de las rotaciones "previas" solo es necesaria en el cliente para la interpolación visual.
            if (this.getWorld().isClient) {
                this.prevYaw = pilotYaw;
                this.prevPitch = pilotPitch;
                this.prevHeadYaw = pilotYaw;
            }
        }

        // Aplica amortiguación para simular la resistencia y evitar el deslizamiento infinito.
        if (!this.hasPassengers()) {
            this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
        }
    }

    @Override
    public void travel(Vec3d movementInput) {
        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            // --- SISTEMA DE VUELO 6DOF (Seis Grados de Libertad) ---

            // 1. Obtener inputs del piloto.
            float forwardInput = pilot.forwardSpeed; // W/S
            float sidewaysInput = pilot.sidewaysSpeed; // A/D
            boolean ascend = ((LivingEntityAccessor) pilot).isJumpingInput(); // Espacio
            boolean descend = pilot.isSneaking(); // Shift

            // 2. Calcular vectores de dirección 3D basados en la orientación actual de la nave.
            // Gracias a tick(), 'this.getRotationVector()' ahora es correcto en el servidor.
            Vec3d forwardDir = this.getRotationVector();
            Vec3d upDir = new Vec3d(0, 1, 0);
            Vec3d sideDir = forwardDir.crossProduct(upDir).normalize();

            // 3. Calcular el empuje total combinando todos los inputs.
            Vec3d totalThrust = Vec3d.ZERO;
            if (forwardInput != 0.0F) {
                totalThrust = totalThrust.add(forwardDir.multiply(forwardInput * FORWARD_THRUST));
            }
            if (sidewaysInput != 0.0F) {
                // El input lateral aplica una fuerza de strafe, sin rotar la nave.
                totalThrust = totalThrust.add(sideDir.multiply(-sidewaysInput * STRAFE_THRUST));
            }
            if (ascend) {
                totalThrust = totalThrust.add(upDir.multiply(VERTICAL_THRUST));
            }
            if (descend) {
                totalThrust = totalThrust.add(upDir.multiply(-VERTICAL_THRUST));
            }

            // 4. Aplicar físicas.
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
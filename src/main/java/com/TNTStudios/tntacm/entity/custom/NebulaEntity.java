// src/main/java/com/TNTStudios/tntacm/entity/custom/NebulaEntity.java
package com.TNTStudios.tntacm.entity.custom;

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
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

public class NebulaEntity extends LivingEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private static final double MOVE_SPEED = 0.6D;
    private static final double MAX_SPEED = 0.8D;
    private static final double DAMPENING_FACTOR = 0.98D;

    public NebulaEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
        this.setNoGravity(true);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 50.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    // --- LÓGICA DE MOVIMIENTO Y COMPORTAMIENTO ---

    @Override
    public void tick() {
        super.tick();

        if (getControllingLivingPassenger() != null) {
            this.setYaw(getControllingLivingPassenger().getYaw());
            this.setBodyYaw(getControllingLivingPassenger().getYaw());
            this.headYaw = getControllingLivingPassenger().getYaw();
        }

        this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
    }

    @Override
    public void travel(Vec3d movementInput) {
        LivingEntity passenger = this.getControllingLivingPassenger();
        if (passenger == null) {
            super.travel(movementInput); // Si no hay pasajero, que se comporte normal
            return;
        }

        // --- ESTA ES LA PARTE CORREGIDA ---
        // Ignoro el 'movementInput' y leo directamente las teclas del pasajero.
        // passenger.forwardSpeed será > 0 para 'W' y < 0 para 'S'.
        // passenger.sidewaysSpeed será > 0 para 'A' y < 0 para 'D'.
        float forwardInput = passenger.forwardSpeed;
        float sidewaysInput = passenger.sidewaysSpeed;
        // --- FIN DE LA CORRECCIÓN ---

        Vec3d lookVector = passenger.getRotationVector();

        // Uso las nuevas variables para calcular el movimiento
        Vec3d forwardMovement = lookVector.multiply(forwardInput * MOVE_SPEED);

        Vec3d strafeVector = Vec3d.fromPolar(0, passenger.getYaw() - 90.0F);
        Vec3d strafeMovement = strafeVector.multiply(sidewaysInput * MOVE_SPEED);

        this.addVelocity(forwardMovement.x + strafeMovement.x, forwardMovement.y, forwardMovement.z + strafeMovement.z);

        if (this.getVelocity().lengthSquared() > MAX_SPEED * MAX_SPEED) {
            this.setVelocity(this.getVelocity().normalize().multiply(MAX_SPEED));
        }

        this.move(MovementType.SELF, this.getVelocity());
    }

    // --- INVULNERABILIDAD Y PROPIEDADES ---

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean isCustomNameVisible() {
        return false;
    }

    @Override
    public void fall(double height, boolean onGround, BlockState state, BlockPos pos) {
    }

    // --- INTERACCIÓN CON EL JUGADOR ---

    @Override
    public void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, this.getX(), this.getY() + 0.5D, this.getZ());
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.isSneaking()) {
            return ActionResult.PASS;
        }
        if (!this.getWorld().isClient && !this.hasPassengers()) {
            player.startRiding(this);
            this.triggerAnim("controller", "abrir");
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.getWorld().isClient) {
            this.triggerAnim("controller", "cerrar");
        }
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        // Aseguramos que el juego sepa quién tiene el control
        Entity firstPassenger = this.getFirstPassenger();
        return firstPassenger instanceof LivingEntity ? (LivingEntity) firstPassenger : null;
    }

    // Dejé el helper por si lo usamos en otro lado, pero el de arriba es el que Minecraft usa
    @Nullable
    public LivingEntity getControllingLivingPassenger() {
        return getControllingPassenger();
    }


    // --- MÉTODOS REQUERIDOS POR LIVINGENTITY ---

    @Override
    public Iterable<ItemStack> getArmorItems() { return ImmutableList.of(); }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) { }

    @Override
    public Arm getMainArm() { return Arm.RIGHT; }


    // --- LÓGICA DE ANIMACIONES (GeckoLib) ---

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }



    private <E extends GeoEntity> PlayState predicate(AnimationState<E> event) {
        if (event.getController().getAnimationState().equals(AnimationController.State.STOPPED)) {
            if (!this.isOnGround()) {
                event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.nebula.flotar"));
            } else {
                event.getController().setAnimation(RawAnimation.begin().thenLoop("animation.nebula.quieto"));
            }
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
// src/main/java/com/TNTStudios/tntacm/entity/custom/NebulaEntity.java
package com.TNTStudios.tntacm.entity.custom;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
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
import software.bernie.geckolib.core.object.PlayState;

public class NebulaEntity extends LivingEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // Constantes para un fácil ajuste del comportamiento de la nave
    private static final double MOVE_SPEED = 0.6D; // Multiplicador de la velocidad base
    private static final double MAX_SPEED = 1.2D;  // Velocidad máxima que puede alcanzar la nave
    private static final double DAMPENING_FACTOR = 0.98D; // Factor de "fricción" para frenar suavemente

    public NebulaEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
        // La gravedad no afecta a nuestra nave
        this.setNoGravity(true);
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 50.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D); // Inmune al empuje
    }

    // --- LÓGICA DE MOVIMIENTO Y COMPORTAMIENTO ---

    @Override
    public void tick() {
        super.tick();

        // Si hay un pasajero, sincronizo la rotación de la nave con la suya
        if (getControllingLivingPassenger() != null) {
            this.setYaw(getControllingLivingPassenger().getYaw());
            this.setBodyYaw(getControllingLivingPassenger().getYaw());
            this.headYaw = getControllingLivingPassenger().getYaw();
        }

        // Aplico una pequeña fricción para que la nave se detenga gradualmente
        this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
    }

    @Override
    public void travel(Vec3d movementInput) {
        // Solo aplico mi lógica de movimiento si hay un jugador controlando la nave
        LivingEntity passenger = this.getControllingLivingPassenger();
        if (passenger == null) {
            super.travel(movementInput);
            return;
        }

        // Obtengo el vector de la mirada del jugador. Esto me da la dirección 3D (arriba/abajo/lados)
        Vec3d lookVector = passenger.getRotationVector();

        // Calculo el movimiento hacia adelante/atrás basado en la tecla W/S del jugador
        Vec3d forwardMovement = lookVector.multiply(movementInput.z * MOVE_SPEED);

        // Calculo el movimiento lateral (strafe) basado en la tecla A/D
        // Para esto, obtengo el vector "derecha" del jugador rotando su vector de mirada
        Vec3d strafeVector = Vec3d.fromPolar(0, passenger.getYaw() - 90.0F);
        Vec3d strafeMovement = strafeVector.multiply(movementInput.x * MOVE_SPEED);

        // Combino ambos movimientos y los aplico a la velocidad actual
        this.addVelocity(forwardMovement.x + strafeMovement.x, forwardMovement.y, forwardMovement.z + strafeMovement.z);

        // Limito la velocidad máxima para que no sea incontrolable
        if (this.getVelocity().lengthSquared() > MAX_SPEED * MAX_SPEED) {
            this.setVelocity(this.getVelocity().normalize().multiply(MAX_SPEED));
        }

        // Finalmente, muevo la entidad con la nueva velocidad calculada
        this.move(MovementType.SELF, this.getVelocity());
    }

    // --- INVULNERABILIDAD Y PROPIEDADES ---

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Hago que la nave sea invulnerable a cualquier tipo de daño.
        // En el futuro, aquí podría añadir una excepción para un proyectil específico.
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Confirmo su invulnerabilidad para mayor robustez
        return true;
    }

    @Override
    public boolean isCustomNameVisible() {
        // Me aseguro de que el nombre de la nave nunca sea visible
        return false;
    }

    @Override
    public void fall(double height, boolean onGround, BlockState state, BlockPos pos) {
        // La nave no recibe daño por caída
    }

    // --- INTERACCIÓN CON EL JUGADOR ---

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.isSneaking()) {
            return ActionResult.PASS; // Si el jugador se agacha, no hace nada para permitir otras interacciones
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
    public LivingEntity getControllingLivingPassenger() {
        Entity firstPassenger = this.getFirstPassenger();
        return firstPassenger instanceof LivingEntity ? (LivingEntity) firstPassenger : null;
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
        // Mantengo la lógica de animación simple: flotar si está en el aire, quieto si está en el suelo.
        // Esto solo se activa cuando una animación anterior (como abrir/cerrar) ha terminado.
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
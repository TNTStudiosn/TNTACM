// src/main/java/com/TNTStudios/tntacm/entity/custom/NebulaEntity.java
package com.TNTStudios.tntacm.entity.custom;

import com.TNTStudios.tntacm.entity.ModEntities;
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
    private static final float ENTITY_WIDTH  = 2.5f;
    private static final float ENTITY_LENGTH = 9.0f;
    private static final float ENTITY_HEIGHT = 2.5f;

    // ===== Modelo de vuelo (thrusts y damping) =====
    private static final double FORWARD_THRUST   = 0.08D;    // empuje W/S
    private static final double STRAFE_THRUST    = 0.06D;    // empuje A/D (sin girar la nave)
    private static final double VERTICAL_THRUST  = 0.07D;    // empuje vertical (Espacio/Shift)
    private static final double MAX_SPEED        = 1.8D;     // límite de velocidad lineal
    private static final double DAMPENING_FACTOR = 0.975D;   // amortiguación inercial

    // ===== Armamento =====
    private int fireCooldown = 0;
    private static final int MAX_FIRE_COOLDOWN = 1; // 20 disparos por segundo. ¡Casi instantáneo!
    private static final double PROJECTILE_SPEED = 5.0D; // Mayor velocidad del proyectil
    private static final double PROJECTILE_SPAWN_OFFSET = 5.0D; // Distancia desde el centro de la nave

    // ===== Límites y velocidades de rotación =====
    // Pitch limitado para no romper cámara y UI
    private static final float MIN_PITCH = -20.0f;     // arriba
    private static final float MAX_PITCH =  50.0f;     // abajo

    // Velocidad máxima por tick (20 TPS) — límite duro
    private static final float MAX_YAW_SPEED_DEG   = 3.0f;
    private static final float MAX_PITCH_SPEED_DEG = 3.0f;

    // Control PD (suavidad de seguimiento hacia la cámara)
    // ROT_P: qué tan fuerte acelero hacia el objetivo (ángulo de la cámara)
    // ROT_DAMP: amortiguación de la velocidad acumulada (0..1)
    private static final float ROT_P    = 0.18f;
    private static final float ROT_DAMP = 0.80f;

    // Deadzone angular para evitar jitter cuando la cámara casi coincide
    private static final float ANGLE_DEADZONE_DEG = 0.15f;

    // Estado interno de rotación (velocidades angulares suavizadas)
    private float yawVelDeg   = 0.0f;
    private float pitchVelDeg = 0.0f;

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

        // Actualizar cooldown de disparo en el servidor
        if (!this.getWorld().isClient()) {
            if (this.fireCooldown > 0) {
                this.fireCooldown--;
            }
        }

        if (this.getControllingPassenger() instanceof PlayerEntity pilot) {
            // 1:1 — la nave copia la cámara en el mismo tick
            final float newPitch = MathHelper.clamp(pilot.getPitch(), MIN_PITCH, MAX_PITCH);
            final float newYaw   = pilot.getYaw();

            // (opcional) limpia estados del controlador anterior
            this.yawVelDeg = 0f;
            this.pitchVelDeg = 0f;

            // Aplica a la nave (no toco la cámara del jugador)
            this.setYaw(newYaw);
            this.setPitch(newPitch);
            this.setBodyYaw(newYaw);
            this.setHeadYaw(newYaw);

            // Evita interpolación visual ese frame
            if (this.getWorld().isClient()) {
                this.prevYaw = newYaw;
                this.prevPitch = newPitch;
                this.prevBodyYaw = newYaw;
                this.prevHeadYaw = newYaw;
            }
        } else {
            // Sin piloto, amortiguo un poco por si quedó algo de inercia
            this.yawVelDeg   *= 0.90f;
            this.pitchVelDeg *= 0.90f;
        }

        // Freno suave cuando no hay input (o incluso con input, simulando inercia)
        if (!this.hasPassengers()) {
            this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
        }
    }

    // AABB rotatorio en función del yaw para colisiones coherentes
    @Override
    protected Box calculateBoundingBox() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        float yawRad = this.getYaw() * (MathHelper.PI / 180.0F);
        float cos = Math.abs(MathHelper.cos(yawRad));
        float sin = Math.abs(MathHelper.sin(yawRad));

        double halfWidth  = (ENTITY_WIDTH * cos + ENTITY_LENGTH * sin) / 2.0;
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
            // Mantengo A/D como strafe puro (no rotan la nave)
            float forwardInput  = pilot.forwardSpeed;   // W/S
            float sidewaysInput = pilot.sidewaysSpeed;  // A/D
            boolean ascend  = ((LivingEntityAccessor) pilot).isJumpingInput(); // Espacio
            boolean descend = pilot.isSneaking();                             // Shift

            // Dirección hacia donde apunta la nave (influida por la cámara)
            Vec3d forwardDir = this.getRotationVector();      // incluye pitch -> W sube si apunto arriba
            Vec3d upDir      = new Vec3d(0, 1, 0);
            Vec3d sideDir    = forwardDir.crossProduct(upDir).normalize(); // horizontal, ortogonal a forward

            Vec3d totalThrust = Vec3d.ZERO;

            if (forwardInput != 0.0F) {
                totalThrust = totalThrust.add(forwardDir.multiply(forwardInput * FORWARD_THRUST));
            }
            if (sidewaysInput != 0.0F) {
                // strafe puro a izquierda/derecha sin girar
                totalThrust = totalThrust.add(sideDir.multiply(-sidewaysInput * STRAFE_THRUST));
            }
            if (ascend) {
                totalThrust = totalThrust.add(upDir.multiply(VERTICAL_THRUST));
            }
            if (descend) {
                totalThrust = totalThrust.add(upDir.multiply(-VERTICAL_THRUST));
            }

            // Aplico empuje y capeo velocidad máxima
            this.addVelocity(totalThrust);
            if (this.getVelocity().lengthSquared() > MAX_SPEED * MAX_SPEED) {
                this.setVelocity(this.getVelocity().normalize().multiply(MAX_SPEED));
            }

            // Amortiguación inercial suave
            this.setVelocity(this.getVelocity().multiply(DAMPENING_FACTOR));
            this.move(MovementType.SELF, this.getVelocity());
            return;
        }
        super.travel(movementInput);
    }

    // ==================== ARMAMENTO ====================
    public void fireProjectile() {
        if (this.getWorld().isClient() || this.fireCooldown > 0) return;

        // --- CAMBIO: Lógica de disparo desde la nave ---
        // Se revierte a la lógica original para disparar desde la punta de la nave, no desde el jugador.
        Entity pilot = this.getControllingPassenger();
        if (pilot == null) return;

        this.fireCooldown = MAX_FIRE_COOLDOWN;
        World world = this.getWorld();

        Vec3d forwardVec = this.getRotationVector(); // Usamos la dirección de la nave
        // El proyectil nace en frente del modelo de la nave
        Vec3d spawnPos = this.getPos()
                .add(forwardVec.multiply(PROJECTILE_SPAWN_OFFSET))
                .add(0, this.getMountedHeightOffset(), 0);

        BlueLaserProjectileEntity projectile = new BlueLaserProjectileEntity(world, spawnPos.x, spawnPos.y, spawnPos.z);
        projectile.setOwner(pilot);

        // La velocidad del proyectil es la dirección de la nave + la inercia actual de la nave.
        Vec3d projectileVelocity = forwardVec.multiply(PROJECTILE_SPEED).add(this.getVelocity());
        projectile.setVelocity(projectileVelocity);

        world.spawnEntity(projectile);
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

    // ==================== Sonidos / Comportamiento base ====================
    @Override public boolean damage(DamageSource source, float amount) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) { return source.isSourceCreativePlayer(); }
    @Override public boolean isPushable() { return false; }
    @Override public void fall(double height, boolean onGround, BlockState state, BlockPos pos) { }

    // Anulo por completo el sonido de pasos al tocar bloques
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // silencio total
    }

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
// src/main/java/com/TNTStudios/tntacm/entity/custom/NebulaEntity.java
package com.TNTStudios.tntacm.entity.custom;

import com.TNTStudios.tntacm.entity.custom.projectile.BlueLaserProjectileEntity;
import com.TNTStudios.tntacm.entity.custom.projectile.LaserProjectileEntity;
import com.TNTStudios.tntacm.entity.custom.projectile.RedLaserProjectileEntity;
import com.TNTStudios.tntacm.mixin.LivingEntityAccessor;
import com.TNTStudios.tntacm.networking.ModMessages;
import com.TNTStudios.tntacm.sound.ModSounds; // Importamos nuestros sonidos
import com.google.common.collect.ImmutableList;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory; // Importamos la categoría de sonido
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
    private static final double FORWARD_THRUST = 0.08D;      // empuje W/S
    private static final double STRAFE_THRUST = 0.06D;       // empuje A/D (sin girar la nave)
    private static final double VERTICAL_THRUST = 0.07D;     // empuje vertical (Espacio/Shift)
    private static final double MAX_SPEED = 1.8D;            // límite de velocidad lineal
    private static final double DAMPENING_FACTOR = 0.975D;   // amortiguación inercial

    // ===== Armamento =====
    private int fireCooldown = 0;
    private static final int MAX_FIRE_COOLDOWN = 2; // Cadencia de disparo
    private static final double PROJECTILE_SPEED = 5.0D;
    private static final int MAX_AMMO = 150; // Tamaño del cargador
    // Hago esta constante pública para poder acceder a ella desde el HUD.
    public static final int RELOAD_TIME = 80; // 4 segundos (80 ticks)

    // ===== Estado de la nave =====
    private static final int RECOVERY_TIME = 200; // 10 segundos para recuperarse
    private int recoveryTimer = 0;

    // DataTracker para sincronizar con el cliente (HUD)
    private static final TrackedData<Integer> AMMO_COUNT = DataTracker.registerData(NebulaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> IS_RELOADING = DataTracker.registerData(NebulaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    // Nuevo: DataTracker para el estado "desactivado"
    private static final TrackedData<Boolean> IS_DISABLED = DataTracker.registerData(NebulaEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    // FIX: Añado un tracker para el temporizador de recarga, así el cliente conoce el valor real.
    private static final TrackedData<Integer> RELOAD_TIMER = DataTracker.registerData(NebulaEntity.class, TrackedDataHandlerRegistry.INTEGER);


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
                // Aumento la vida de la nave como solicitaste.
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 150.0D)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        // Inicializo los valores para la munición y el estado de recarga.
        this.dataTracker.startTracking(AMMO_COUNT, MAX_AMMO);
        this.dataTracker.startTracking(IS_RELOADING, false);
        // Inicializo el nuevo estado.
        this.dataTracker.startTracking(IS_DISABLED, false);
        // FIX: Inicializo el nuevo tracker del temporizador.
        this.dataTracker.startTracking(RELOAD_TIMER, 0);
    }

    // ==================== LÓGICA DE DAÑO ====================
    @Override
    public boolean damage(DamageSource source, float amount) {
        // Si la nave está desactivada, es invulnerable mientras se repara.
        if (this.isDisabled()) {
            return false;
        }

        // Solo permito el daño si la fuente es un láser de los nuestros.
        Entity sourceEntity = source.getSource();
        if (!(sourceEntity instanceof RedLaserProjectileEntity || sourceEntity instanceof BlueLaserProjectileEntity)) {
            return false; // Ignoro cualquier otro tipo de daño.
        }

        // Si el daño va a "matar" la nave, en lugar de eso la desactivo.
        if (this.getHealth() - amount <= 1.0f) {
            this.setHealth(1.0f); // La dejo con 1 de vida.
            this.setDisabled(true); // Activo el estado "desactivado".
            this.recoveryTimer = RECOVERY_TIME; // Inicio el temporizador de recuperación.
            this.setVelocity(Vec3d.ZERO); // Detengo la nave en seco.

            // === SONIDO DE FALLA CRÍTICA ('falla.ogg') ===
            // Lo reproduzco en el servidor para que todos lo escuchen.
            if (!this.getWorld().isClient()) {
                this.getWorld().playSound(null, this.getBlockPos(), ModSounds.ENTITY_NEBULA_DISABLED, SoundCategory.NEUTRAL, 0.8f, 1.0f);
            }

            return true; // Indico que el daño fue procesado.
        }

        // Si no, aplico el daño normalmente.
        return super.damage(source, amount);
    }


    // ==================== TICK / LÓGICA DE ESTADO ====================
    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient()) {

            // Si la nave está desactivada, proceso la recuperación.
            if (this.isDisabled()) {
                if (this.recoveryTimer > 0) {
                    this.recoveryTimer--;
                } else {
                    // Se acabó el tiempo: la restauro por completo.
                    this.setHealth(this.getMaxHealth());
                    this.setDisabled(false);
                }
                return; // No hago nada más si está desactivada.
            }

            // Gestiono el cooldown de disparo entre cada proyectil de la ráfaga.
            if (this.fireCooldown > 0) {
                this.fireCooldown--;
            }

            // Gestiono la lógica de recarga.
            if (this.isReloading()) {
                // FIX: Leo y escribo el valor del temporizador desde el DataTracker.
                int currentTimer = this.dataTracker.get(RELOAD_TIMER);
                if (currentTimer > 0) {
                    this.dataTracker.set(RELOAD_TIMER, currentTimer - 1);
                } else {
                    // Termina la recarga: reseteo munición y estado.
                    this.setAmmo(MAX_AMMO);
                    this.setReloading(false);
                }
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
        // Si la nave está desactivada, la detengo y no permito movimiento.
        if (this.isDisabled()) {
            this.setVelocity(this.getVelocity().multiply(0.9, 0.9, 0.9)); // Amortiguación rápida
            if(this.getVelocity().lengthSquared() < 0.01) {
                this.setVelocity(Vec3d.ZERO);
            }
            this.move(MovementType.SELF, this.getVelocity());
            return;
        }

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
     * @param originPos La posición de origen del disparo (la cámara del cliente).
     * @param shootingDir La dirección de disparo, calculada desde la cámara del cliente.
     */
    public void fireProjectile(Vec3d originPos, Vec3d shootingDir) { // <-- CAMBIO EN LA FIRMA
        // No puedo disparar si estoy recargando o la nave está desactivada.
        if (this.getWorld().isClient() || this.fireCooldown > 0 || this.isReloading() || this.isDisabled()) return;

        Entity pilot = this.getControllingPassenger();
        if (!(pilot instanceof PlayerEntity player)) return;

        int currentAmmo = this.getAmmo();
        if (currentAmmo <= 0) return; // Doble chequeo por si acaso

        this.fireCooldown = MAX_FIRE_COOLDOWN;
        this.setAmmo(currentAmmo - 1); // Reduzco la munición

        World world = this.getWorld();

        // === SONIDO DE LÁSER ('lase.ogg') ===
        world.playSound(null, this.getBlockPos(), ModSounds.ENTITY_NEBULA_LASER, SoundCategory.PLAYERS, 0.6f, 1.0f);

        // USO EL 'originPos' RECIBIDO DEL CLIENTE EN LUGAR DE CALCULARLO AQUÍ
        BlueLaserProjectileEntity projectile = new BlueLaserProjectileEntity(world, originPos.x, originPos.y, originPos.z);
        projectile.setOwner(pilot);

        Vec3d projectileVelocity = shootingDir.multiply(PROJECTILE_SPEED).add(this.getVelocity());
        projectile.setVelocity(projectileVelocity);
        world.spawnEntity(projectile);

        // Si se acaba la munición, inicio la recarga.
        if (this.getAmmo() <= 0) {
            this.setReloading(true);
            // FIX: Establezco el temporizador en el DataTracker para que se sincronice.
            this.dataTracker.set(RELOAD_TIMER, RELOAD_TIME);
            // === SONIDO DE RECARGA ('sinbalas.ogg') ===
            world.playSound(null, this.getBlockPos(), ModSounds.ENTITY_NEBULA_RELOAD, SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
    }

    //region Getters y Setters para DataTracker
    public int getAmmo() {
        return this.dataTracker.get(AMMO_COUNT);
    }

    public void setAmmo(int amount) {
        this.dataTracker.set(AMMO_COUNT, amount);
    }

    public boolean isReloading() {
        return this.dataTracker.get(IS_RELOADING);
    }

    public void setReloading(boolean reloading) {
        this.dataTracker.set(IS_RELOADING, reloading);
    }

    public boolean isDisabled() {
        return this.dataTracker.get(IS_DISABLED);
    }

    public void setDisabled(boolean disabled) {
        this.dataTracker.set(IS_DISABLED, disabled);
    }

    // Métodos para el HUD
    public int getMaxAmmo() {
        return MAX_AMMO;
    }

    public float getReloadProgress() {
        if (!isReloading()) return 0f;
        // FIX: Leo el valor sincronizado del DataTracker en lugar de la variable local.
        float reloadTimer = this.dataTracker.get(RELOAD_TIMER);
        return 1.0f - reloadTimer / (float)RELOAD_TIME;
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
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source) || source.isSourceCreativePlayer();
    }
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
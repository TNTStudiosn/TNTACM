// src/main/java/com/TNTStudios/tntacm/entity/custom/projectile/LaserProjectileEntity.java
package com.TNTStudios.tntacm.entity.custom.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public abstract class LaserProjectileEntity extends ProjectileEntity {

    // Vida del proyectil (ticks)
    private int life = 0;
    private final int maxLife = 100; // ~5s a 20 tps

    // Daño ajustado para ráfaga rápida
    private static final float LASER_DAMAGE = 10.0f; //

    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> entityType, World world) {
        super(entityType, world);
        this.setNoGravity(true); // sin caída
    }

    @Override
    public void tick() {
        // Autodespawn por tiempo de vida
        if (this.life++ >= this.maxLife) {
            this.discard();
            return;
        }

        // 1) Calculo colisión (entidades/bloques) ignorando dueño y su vehículo
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onCollision(hit);
            // Si me descarté durante onCollision, corto aquí
            if (!this.isAlive()) return;
        }

        // 2) Muevo según la velocidad actual (una sola vez)
        Vec3d v = this.getVelocity();
        this.setPosition(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);

        // 3) Ajusto rotación basada en la velocidad (por si quiero efectos luego)
        ProjectileUtil.setRotationFromVelocity(this, 0.2F);

        // 4) Rozamiento y gravedad (no hay gravedad, pero conservo rozamiento vanilla)
        float drag = this.isTouchingWater() ? 0.8F : 0.99F;
        this.setVelocity(this.getVelocity().multiply(drag));
        if (!this.hasNoGravity()) {
            this.setVelocity(this.getVelocity().add(0.0D, -0.03D, 0.0D));
        }

        // 5) Colisión con bloques dentro del AABB actual
        this.checkBlockCollision();
    }

    @Override
    protected boolean canHit(Entity entity) {
        // Evito chocar con el dueño y con la nave que conduce
        Entity owner = this.getOwner();
        if (owner != null && (entity == owner || entity == owner.getVehicle())) {
            return false;
        }
        return super.canHit(entity);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);

        Entity target = entityHitResult.getEntity();
        Entity owner  = this.getOwner();

        // Seguridad extra (debería quedar cubierta por canHit, pero prefiero doble chequeo)
        if (owner != null && (target == owner || target == owner.getVehicle())) {
            return;
        }

        if (!this.getWorld().isClient) {
            target.damage(this.getDamageSources().thrown(this, owner), LASER_DAMAGE);
        }

        // El láser se consume al impactar
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        super.onBlockHit(blockHitResult);
        this.discard();
    }

    @Override
    protected void initDataTracker() {
        // No uso data tracker acá
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // Nada que persistir
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        // Nada que persistir
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        // Sincronizo velocidad inicial para el cliente
        super.onSpawnPacket(packet);
        this.setVelocity(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }
}

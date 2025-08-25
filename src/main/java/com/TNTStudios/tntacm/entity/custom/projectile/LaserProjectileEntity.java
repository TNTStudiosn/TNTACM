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



    private int life = 0;

    private final int maxLife = 100; // 5 segundos de vida

// FIX: Reduzco el daño para que sea adecuado para una ráfaga rápida.

    private static final float LASER_DAMAGE = 1.5f; // 0.75 corazones de daño



    public LaserProjectileEntity(EntityType<? extends LaserProjectileEntity> entityType, World world) {

        super(entityType, world);

        this.setNoGravity(true);

    }



    @Override

    public void tick() {

// --- OPTIMIZACIÓN: Se simplifica el tick ---

// super.tick() ya gestiona la colisión y llama a onCollision(),

// por lo que el cálculo manual de `ProjectileUtil` es redundante y menos eficiente.

        super.tick();



// Actualizamos la posición con la velocidad actual

        this.updatePosition(this.getX() + this.getVelocity().x, this.getY() + this.getVelocity().y, this.getZ() + this.getVelocity().z);



// Eliminamos el proyectil si ha excedido su tiempo de vida

        if (this.life++ >= this.maxLife) {

            this.discard();

        }

    }



    @Override

    protected void onEntityHit(EntityHitResult entityHitResult) {

        super.onEntityHit(entityHitResult);

        Entity target = entityHitResult.getEntity();

        Entity owner = this.getOwner();



        if (owner != null && (target == owner || target == owner.getVehicle())) {

            return;

        }



        if (!this.getWorld().isClient) {

// --- CORRECCIÓN: Se usa .thrown() en lugar de .projectile() ---

            target.damage(this.getDamageSources().thrown(this, owner), LASER_DAMAGE);

        }



        this.discard();

    }



    @Override

    protected void onBlockHit(BlockHitResult blockHitResult) {

        super.onBlockHit(blockHitResult);

        this.discard();

    }



    @Override

    protected void initDataTracker() {

    }



    @Override

    protected void readCustomDataFromNbt(NbtCompound nbt) {}



    @Override

    protected void writeCustomDataToNbt(NbtCompound nbt) {}



    @Override

    public void onSpawnPacket(EntitySpawnS2CPacket packet) {

        super.onSpawnPacket(packet);

        double vx = packet.getVelocityX();

        double vy = packet.getVelocityY();

        double vz = packet.getVelocityZ();

        this.setVelocity(vx, vy, vz);

    }

}
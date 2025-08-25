// src/main/java/com/TNTStudios/tntacm/entity/custom/projectile/RedLaserProjectileEntity.java
package com.TNTStudios.tntacm.entity.custom.projectile;

import com.TNTStudios.tntacm.entity.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public class RedLaserProjectileEntity extends LaserProjectileEntity {
    public RedLaserProjectileEntity(EntityType<? extends RedLaserProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    public RedLaserProjectileEntity(World world, double x, double y, double z) {
        super(ModEntities.RED_LASER_PROJECTILE, world);
        this.setPosition(x, y, z);
    }
}
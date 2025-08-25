// src/main/java/com/TNTStudios/tntacm/entity/custom/projectile/BlueLaserProjectileEntity.java
package com.TNTStudios.tntacm.entity.custom.projectile;

import com.TNTStudios.tntacm.entity.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public class BlueLaserProjectileEntity extends LaserProjectileEntity {
    public BlueLaserProjectileEntity(EntityType<? extends BlueLaserProjectileEntity> entityType, World world) {
        super(entityType, world);
    }

    public BlueLaserProjectileEntity(World world, double x, double y, double z) {
        super(ModEntities.BLUE_LASER_PROJECTILE, world);
        this.setPosition(x, y, z);
    }
}
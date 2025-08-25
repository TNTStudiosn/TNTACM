// src/main/java/com/TNTStudios/tntacm/entity/ModEntities.java
package com.TNTStudios.tntacm.entity;

import com.TNTStudios.tntacm.Tntacm;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.TNTStudios.tntacm.entity.custom.projectile.BlueLaserProjectileEntity;
import com.TNTStudios.tntacm.entity.custom.projectile.RedLaserProjectileEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    // 1. Defino el tipo de entidad para mi nave Nébula.
    // La configuro para que sea una criatura miscelánea y ajusto sus dimensiones.
    public static final EntityType<NebulaEntity> NEBULA = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Tntacm.MOD_ID, "nebula"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, NebulaEntity::new)
                    .dimensions(EntityDimensions.fixed(9.0f, 2.5f))
                    .build()
    );

    // 2. Definimos los proyectiles
    public static final EntityType<BlueLaserProjectileEntity> BLUE_LASER_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Tntacm.MOD_ID, "blue_laser"),
            FabricEntityTypeBuilder.<BlueLaserProjectileEntity>create(SpawnGroup.MISC, BlueLaserProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(128).trackedUpdateRate(10) // Asegura que se vea bien a distancia y velocidad
                    .build()
    );

    public static final EntityType<RedLaserProjectileEntity> RED_LASER_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Tntacm.MOD_ID, "red_laser"),
            FabricEntityTypeBuilder.<RedLaserProjectileEntity>create(SpawnGroup.MISC, RedLaserProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(128).trackedUpdateRate(10)
                    .build()
    );


    // 3. Método de registro que llamaré desde la clase principal.
    public static void registerModEntities() {
        Tntacm.LOGGER.info("Registrando Entidades para " + Tntacm.MOD_ID);
    }
}
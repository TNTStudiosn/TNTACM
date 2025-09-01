// src/main/java/com/TNTStudios/tntacm/Tntacm.java
package com.TNTStudios.tntacm;

import com.TNTStudios.tntacm.block.ModBlocks;
import com.TNTStudios.tntacm.dimension.ModDimensions;
import com.TNTStudios.tntacm.entity.ModEntities;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.TNTStudios.tntacm.item.ModItemGroups;
import com.TNTStudios.tntacm.networking.ModMessages;
import com.TNTStudios.tntacm.sound.ModSounds; // <-- Importamos la nueva clase
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tntacm implements ModInitializer {
    public static final String MOD_ID = "tntacm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Registro base
        ModBlocks.registerModBlocks();
        ModItemGroups.registerItemGroups();
        ModDimensions.register();
        ModEntities.registerModEntities();
        ModSounds.registerSounds(); // <-- Añadimos el registro de sonidos

        // REGISTRO CLAVE: atributos de la Nebula en el lado común/servidor
        FabricDefaultAttributeRegistry.register(ModEntities.NEBULA, NebulaEntity.setAttributes());

        // Networking C2S
        ModMessages.registerC2SPackets();

        LOGGER.info("TNTACM inicializado.");
    }
}
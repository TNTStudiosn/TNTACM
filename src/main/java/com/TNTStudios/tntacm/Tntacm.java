package com.TNTStudios.tntacm;

import com.TNTStudios.tntacm.block.ModBlocks;
import com.TNTStudios.tntacm.item.ModItemGroups;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tntacm implements ModInitializer {
    // Defino el ID de mi mod para usarlo en todos lados.
    public static final String MOD_ID = "tntacm";
    // El Logger es una herramienta para imprimir mensajes en la consola, muy útil para depurar.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Llamo a los métodos de registro que creé en las otras clases.
        ModBlocks.registerModBlocks();
        ModItemGroups.registerItemGroups();
    }
}
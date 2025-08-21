// src/main/java/com/TNTStudios/tntacm/dimension/ModDimensions.java
package com.TNTStudios.tntacm.dimension;

import com.TNTStudios.tntacm.Tntacm;
import com.TNTStudios.tntacm.world.gen.SpaceChunkGenerator;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;

public class ModDimensions {

    // 1. Defino el Identificador único para mi dimensión.
    public static final Identifier SPACE_ID = new Identifier(Tntacm.MOD_ID, "space");

    // 2. Creo una "llave" para referenciar mi mundo. Es como un acceso directo.
    public static final RegistryKey<World> SPACE_KEY = RegistryKey.of(RegistryKeys.WORLD, SPACE_ID);

    // 3. Y otra llave para las opciones de la dimensión (que definiremos en JSON).
    public static final RegistryKey<DimensionOptions> SPACE_OPTIONS_KEY = RegistryKey.of(RegistryKeys.DIMENSION, SPACE_ID);


    // Este método se encargará de registrar todo lo necesario.
    public static void register() {
        Tntacm.LOGGER.info("Registrando la dimensión 'Space' para " + Tntacm.MOD_ID);

        // 4. Registro mi generador de chunks personalizado para que Minecraft lo reconozca.
        Registry.register(Registries.CHUNK_GENERATOR, SPACE_ID, SpaceChunkGenerator.CODEC);
    }
}
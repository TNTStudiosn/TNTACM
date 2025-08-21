package com.TNTStudios.tntacm.block;

import com.TNTStudios.tntacm.Tntacm;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    // 1. Defino la instancia de mi bloque "Universo".
    // Copio las propiedades de la Bedrock para que sea irrompible.
    // Le agrego una luminancia de 15, el máximo nivel de luz.
    public static final Block UNIVERSO_BLOCK = registerBlock("universo",
            new UniversoBlock(FabricBlockSettings.copyOf(Blocks.BEDROCK).luminance(15)));


    // 2. Un método de ayuda para registrar un bloque y su item correspondiente.
    // Esto me evita repetir código más adelante.
    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, new Identifier(Tntacm.MOD_ID, name), block);
    }

    // 3. Método para registrar el BlockItem, que permite que el bloque exista en el inventario.
    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, new Identifier(Tntacm.MOD_ID, name),
                new BlockItem(block, new FabricItemSettings()));
    }

    // 4. Este método lo llamaré desde la clase principal para iniciar el registro.
    public static void registerModBlocks() {
        Tntacm.LOGGER.info("Registrando bloques para " + Tntacm.MOD_ID);
    }
}
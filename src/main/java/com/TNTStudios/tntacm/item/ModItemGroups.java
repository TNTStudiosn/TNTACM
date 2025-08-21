package com.TNTStudios.tntacm.item;

import com.TNTStudios.tntacm.Tntacm;
import com.TNTStudios.tntacm.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    // Defino mi grupo de items (pestaña en el creativo).
    public static final ItemGroup TNTACM_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(Tntacm.MOD_ID, "tntacm"),
            FabricItemGroup.builder()
                    // El nombre que se mostrará en el juego.
                    .displayName(Text.translatable("itemgroup.tntacm"))
                    // El ícono que tendrá la pestaña (nuestro bloque Universo).
                    .icon(() -> new ItemStack(ModBlocks.UNIVERSO_BLOCK))
                    // Aquí agrego todos los items que quiero que aparezcan en esta pestaña.
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.UNIVERSO_BLOCK);
                    }).build());

    // Este método lo llamaré desde la clase principal.
    public static void registerItemGroups() {
        Tntacm.LOGGER.info("Registrando grupos de items para " + Tntacm.MOD_ID);
    }
}
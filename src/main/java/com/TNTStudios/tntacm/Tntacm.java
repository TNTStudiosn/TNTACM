package com.TNTStudios.tntacm;

import com.TNTStudios.tntacm.block.ModBlocks;
import com.TNTStudios.tntacm.dimension.ModDimensions;
import com.TNTStudios.tntacm.entity.ModEntities;
import com.TNTStudios.tntacm.item.ModItemGroups;
import com.TNTStudios.tntacm.networking.ModMessages;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tntacm implements ModInitializer {
    public static final String MOD_ID = "tntacm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.registerModBlocks();
        ModItemGroups.registerItemGroups();
        ModDimensions.register();
        ModEntities.registerModEntities();

        ModMessages.registerC2SPackets();
    }
}
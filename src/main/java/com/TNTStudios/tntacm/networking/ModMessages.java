package com.TNTStudios.tntacm.networking;

import com.TNTStudios.tntacm.Tntacm;
import net.minecraft.util.Identifier;

public class ModMessages {

    //region S2C Packet Identifiers
    public static final Identifier ENTER_SHIP_VIEW_ID = new Identifier(Tntacm.MOD_ID, "enter_ship_view");
    public static final Identifier EXIT_SHIP_VIEW_ID = new Identifier(Tntacm.MOD_ID, "exit_ship_view");
    //endregion

    //region Registration
    // C2S packets would be registered here
    public static void registerC2SPackets() {
    }

    // S2C packet handlers are registered on the client side in TntacmClient
    //endregion
}
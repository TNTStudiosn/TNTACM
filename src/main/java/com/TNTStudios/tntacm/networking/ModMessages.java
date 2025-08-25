// src/main/java/com/TNTStudios/tntacm/networking/ModMessages.java
package com.TNTStudios.tntacm.networking;

import com.TNTStudios.tntacm.Tntacm;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class ModMessages {

    //region S2C Packet Identifiers
    public static final Identifier ENTER_SHIP_VIEW_ID = new Identifier(Tntacm.MOD_ID, "enter_ship_view");
    public static final Identifier EXIT_SHIP_VIEW_ID = new Identifier(Tntacm.MOD_ID, "exit_ship_view");
    //endregion

    //region C2S Packet Identifiers
    public static final Identifier FIRE_PROJECTILE_ID = new Identifier(Tntacm.MOD_ID, "fire_projectile");
    //endregion


    //region Registration
    // C2S packets would be registered here
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(FIRE_PROJECTILE_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                // Ejecutamos en el hilo del servidor para seguridad
                if (player.getVehicle() instanceof NebulaEntity ship) {
                    ship.fireProjectile();
                }
            });
        });
    }

    // S2C packet handlers are registered on the client side in TntacmClient
    //endregion
}
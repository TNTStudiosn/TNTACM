// src/main/java/com/TNTStudios/tntacm/networking/ModMessages.java
package com.TNTStudios.tntacm.networking;

import com.TNTStudios.tntacm.Tntacm;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class ModMessages {

    //region S2C Packet Identifiers
    public static final Identifier ENTER_SHIP_VIEW_ID = new Identifier(Tntacm.MOD_ID, "enter_ship_view");
    public static final Identifier EXIT_SHIP_VIEW_ID = new Identifier(Tntacm.MOD_ID, "exit_ship_view");
    //endregion

    //region C2S Packet Identifiers
    public static final Identifier FIRE_PROJECTILE_ID = new Identifier(Tntacm.MOD_ID, "fire_projectile");
    //endregion


    //region Registration
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(FIRE_PROJECTILE_ID, (server, player, handler, buf, responseSender) -> {
            // LEO LOS DOS VECTORES DEL BUFFER, EN EL MISMO ORDEN QUE LOS ENVIÉ
            final Vec3d originPos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            final Vec3d shootingDir = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());

            server.execute(() -> {
                // Ejecutamos en el hilo del servidor para seguridad
                if (player.getVehicle() instanceof NebulaEntity ship) {
                    // PASO AMBOS VECTORES AL MÉTODO ACTUALIZADO DE LA NAVE
                    ship.fireProjectile(originPos, shootingDir);
                }
            });
        });
    }
}
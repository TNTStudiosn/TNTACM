// src/client/java/com/TNTStudios/tntacm/client/TntacmClient.java
package com.TNTStudios.tntacm.client;

import com.TNTStudios.tntacm.client.entity.NebulaRenderer;
import com.TNTStudios.tntacm.client.entity.projectile.LaserProjectileRenderer;
import com.TNTStudios.tntacm.entity.ModEntities;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.TNTStudios.tntacm.networking.ModMessages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.client.option.Perspective;

public class TntacmClient implements ClientModInitializer {

    private int clientFireCooldown = 0;

    @Override
    public void onInitializeClient() {
        // Renderers
        EntityRendererRegistry.register(ModEntities.NEBULA, NebulaRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLUE_LASER_PROJECTILE, LaserProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.RED_LASER_PROJECTILE, LaserProjectileRenderer::new);

        // Atributos (necesarios también en cliente para predicción)
        FabricDefaultAttributeRegistry.register(ModEntities.NEBULA, NebulaEntity.setAttributes());

        // Paquetes y Eventos
        registerPacketHandlers();
        registerInputHandlers();
    }

    //region Networking
    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.ENTER_SHIP_VIEW_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                ShipViewController.isInShipView = true;
                client.options.setPerspective(Perspective.FIRST_PERSON);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.EXIT_SHIP_VIEW_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                ShipViewController.isInShipView = false;
            });
        });
    }
    //endregion

    //region Input Handling
    private void registerInputHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || !ShipViewController.isInShipView) {
                return;
            }

            if (this.clientFireCooldown > 0) {
                this.clientFireCooldown--;
            }

            if (client.options.attackKey.isPressed() && this.clientFireCooldown <= 0) {
                ClientPlayNetworking.send(ModMessages.FIRE_PROJECTILE_ID, PacketByteBufs.empty());
                // --- CAMBIO: Sincronizar cooldown con el servidor ---
                this.clientFireCooldown = 1; // Debe ser idéntico al MAX_FIRE_COOLDOWN del servidor
            }
        });
    }
    //endregion
}
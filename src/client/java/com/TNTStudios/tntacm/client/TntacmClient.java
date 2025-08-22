package com.TNTStudios.tntacm.client;

import com.TNTStudios.tntacm.client.entity.NebulaRenderer;
import com.TNTStudios.tntacm.entity.ModEntities;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.TNTStudios.tntacm.networking.ModMessages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.client.option.Perspective;

public class TntacmClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.NEBULA, NebulaRenderer::new);
        FabricDefaultAttributeRegistry.register(ModEntities.NEBULA, NebulaEntity.setAttributes());
        registerPacketHandlers();
    }

    //region Networking
    private static void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.ENTER_SHIP_VIEW_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                ShipViewController.isInShipView = true;
                // Forzamos la perspectiva en primera persona al entrar a la nave.
                client.options.setPerspective(Perspective.FIRST_PERSON);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.EXIT_SHIP_VIEW_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                ShipViewController.isInShipView = false;
                // El nuevo GameRendererMixin se encargará de restaurar la cámara al jugador.
            });
        });
    }
    //endregion
}
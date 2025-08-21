// src/client/java/com/TNTStudios/tntacm/client/TntacmClient.java
package com.TNTStudios.tntacm.client;

import com.TNTStudios.tntacm.client.entity.NebulaRenderer;
import com.TNTStudios.tntacm.entity.ModEntities;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public class TntacmClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Le digo al juego cómo renderizar (dibujar) mi entidad Nébula.
        EntityRendererRegistry.register(ModEntities.NEBULA, NebulaRenderer::new);

        // 2. Registro los atributos (vida, velocidad, etc.) de mi Nébula.
        FabricDefaultAttributeRegistry.register(ModEntities.NEBULA, NebulaEntity.setAttributes());
    }
}
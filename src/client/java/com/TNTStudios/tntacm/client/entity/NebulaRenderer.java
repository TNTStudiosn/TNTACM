// src/client/java/com/TNTStudios/tntacm/client/entity/NebulaRenderer.java
package com.TNTStudios.tntacm.client.entity;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NebulaRenderer extends GeoEntityRenderer<NebulaEntity> {
    public NebulaRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new NebulaModel());
        // Ajusto el radio de la sombra para que sea más grande.
        this.shadowRadius = 3.5f;
    }
}
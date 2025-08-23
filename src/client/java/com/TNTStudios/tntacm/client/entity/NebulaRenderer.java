// src/main/java/com/TNTStudios/tntacm/client/entity/NebulaRenderer.java
package com.TNTStudios.tntacm.client.entity;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NebulaRenderer extends GeoEntityRenderer<NebulaEntity> {

    public NebulaRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new NebulaModel());
        this.shadowRadius = 3.0f;
    }

    //region Name Tag Rendering
    /**
     * Sobrescribimos este método para que siempre devuelva 'false'.
     * Esto evita que Minecraft intente renderizar la etiqueta del nombre de la entidad.
     */
    @Override
    public boolean hasLabel(NebulaEntity animatable) { // <-- CORRECCIÓN AQUÍ
        return false;
    }
    //endregion
}
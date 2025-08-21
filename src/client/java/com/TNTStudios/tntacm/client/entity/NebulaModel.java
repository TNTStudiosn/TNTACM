package com.TNTStudios.tntacm.client.entity;

import com.TNTStudios.tntacm.Tntacm;
import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class NebulaModel extends GeoModel<NebulaEntity> {
    @Override
    public Identifier getModelResource(NebulaEntity animatable) {
        // Le indico la ruta a mi archivo de modelo .geo.json
        return new Identifier(Tntacm.MOD_ID, "geo/nebula.geo.json");
    }

    @Override
    public Identifier getTextureResource(NebulaEntity animatable) {
        // Le indico la ruta a mi archivo de textura .png
        return new Identifier(Tntacm.MOD_ID, "textures/entity/nebula.png");
    }

    @Override
    public Identifier getAnimationResource(NebulaEntity animatable) {
        // Le indico la ruta a mi archivo de animaciones .animation.json
        return new Identifier(Tntacm.MOD_ID, "animations/nebula.animation.json");
    }
}
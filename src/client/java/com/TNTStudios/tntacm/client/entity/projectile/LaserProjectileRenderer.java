// src/client/java/com/TNTStudios/tntacm/client/entity/projectile/LaserProjectileRenderer.java
package com.TNTStudios.tntacm.client.entity.projectile;

import com.TNTStudios.tntacm.entity.custom.projectile.BlueLaserProjectileEntity;
import com.TNTStudios.tntacm.entity.custom.projectile.LaserProjectileEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class LaserProjectileRenderer extends EntityRenderer<LaserProjectileEntity> {

    public LaserProjectileRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(LaserProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();

        // Seleccionamos el color basado en el tipo de entidad
        int r, g, b;
        if (entity instanceof BlueLaserProjectileEntity) {
            r = 70; g = 180; b = 255;
        } else { // RedLaserProjectileEntity
            r = 255; g = 50; b = 50;
        }

        // Hacemos que el proyectil siempre mire a la cámara (billboarding)
        matrices.multiply(this.dispatcher.getRotation());

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        // Usamos un RenderLayer que no requiere textura y brilla
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLightning());
        float size = 0.15f; // El tamaño del láser

        // Dibujamos un cuadrado simple
        buffer.vertex(positionMatrix, -size, -size, 0).color(r, g, b, 255).next();
        buffer.vertex(positionMatrix, -size,  size, 0).color(r, g, b, 255).next();
        buffer.vertex(positionMatrix,  size,  size, 0).color(r, g, b, 255).next();
        buffer.vertex(positionMatrix,  size, -size, 0).color(r, g, b, 255).next();

        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(LaserProjectileEntity entity) {
        // No usamos textura, pero el método debe ser sobrescrito.
        // Podemos devolver cualquier identificador, no se usará.
        return new Identifier("minecraft", "textures/particle/flame.png");
    }
}
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
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

public class LaserProjectileRenderer extends EntityRenderer<LaserProjectileEntity> {

    public LaserProjectileRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(LaserProjectileEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // Mi objetivo es mejorar el aspecto del láser. En lugar de un cuadrado simple,
        // voy a renderizar un núcleo brillante, un resplandor exterior (glow) y una
        // pequeña estela para dar sensación de velocidad.

        matrices.push();

        // 1. Defino los colores para el núcleo (core) y el resplandor (glow)
        // basándome en el tipo de proyectil.
        int core_r, core_g, core_b;
        int glow_r, glow_g, glow_b;

        if (entity instanceof BlueLaserProjectileEntity) {
            // Para el láser azul, uso un núcleo casi blanco y un resplandor cian.
            core_r = 200; core_g = 230; core_b = 255;
            glow_r = 70;  glow_g = 180; glow_b = 255;
        } else { // RedLaserProjectileEntity
            // Para el láser rojo, un núcleo blanco-rosado y un resplandor rojo intenso.
            core_r = 255; core_g = 200; core_b = 200;
            glow_r = 255; glow_g = 50;  glow_b = 50;
        }

        // 2. Hago que el proyectil siempre mire a la cámara (billboarding).
        // Esto simplifica mucho el renderizado, ya que solo trabajo en 2D (X, Y).
        matrices.multiply(this.dispatcher.getRotation());

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        // 3. Uso un RenderLayer que no se vea afectado por la luz del mundo y que
        // tenga un blending aditivo. `getLightning()` es perfecto para esto.
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLightning());

        // 4. Defino los tamaños para cada parte del efecto.
        float coreSize = 0.08f;
        float glowSize = 0.25f;
        // La longitud de la estela dependerá de la velocidad para un efecto más dinámico.
        float speed = (float) entity.getVelocity().length();
        float tailLength = MathHelper.clamp(speed * 0.4f, 0.1f, 0.75f);

        // 5. Renderizo las partes de atrás hacia adelante: Resplandor -> Estela -> Núcleo.

        // --- Renderizo el Resplandor (Glow) ---
        // Es un cuadrado grande y semitransparente que da el color base.
        int glowAlpha = 150;
        vertex(buffer, positionMatrix, -glowSize, -glowSize, 0, glow_r, glow_g, glow_b, glowAlpha);
        vertex(buffer, positionMatrix, -glowSize,  glowSize, 0, glow_r, glow_g, glow_b, glowAlpha);
        vertex(buffer, positionMatrix,  glowSize,  glowSize, 0, glow_r, glow_g, glow_b, glowAlpha);
        vertex(buffer, positionMatrix,  glowSize, -glowSize, 0, glow_r, glow_g, glow_b, glowAlpha);


        // --- Renderizo la Estela (Tail) ---
        // La dibujo como dos triángulos que forman un quad estirado hacia atrás (Z negativo).
        // Se desvanece (alpha va de 255 a 0), creando una estela suave.
        vertex(buffer, positionMatrix, -coreSize,  coreSize, 0,    core_r, core_g, core_b, 255);
        vertex(buffer, positionMatrix, -coreSize, -coreSize, 0,    core_r, core_g, core_b, 255);
        vertex(buffer, positionMatrix, 0,         0, -tailLength, glow_r, glow_g, glow_b, 0);
        vertex(buffer, positionMatrix, 0,         0, -tailLength, glow_r, glow_g, glow_b, 0);

        vertex(buffer, positionMatrix,  coreSize, -coreSize, 0,    core_r, core_g, core_b, 255);
        vertex(buffer, positionMatrix,  coreSize,  coreSize, 0,    core_r, core_g, core_b, 255);
        vertex(buffer, positionMatrix, 0,         0, -tailLength, glow_r, glow_g, glow_b, 0);
        vertex(buffer, positionMatrix, 0,         0, -tailLength, glow_r, glow_g, glow_b, 0);


        // --- Renderizo el Núcleo (Core) ---
        // Es un cuadrado más pequeño, brillante y opaco en el centro. Lo dibujo al final
        // para que siempre esté por encima del resplandor y la estela.
        int coreAlpha = 255;
        vertex(buffer, positionMatrix, -coreSize, -coreSize, 0, core_r, core_g, core_b, coreAlpha);
        vertex(buffer, positionMatrix, -coreSize,  coreSize, 0, core_r, core_g, core_b, coreAlpha);
        vertex(buffer, positionMatrix,  coreSize,  coreSize, 0, core_r, core_g, core_b, coreAlpha);
        vertex(buffer, positionMatrix,  coreSize, -coreSize, 0, core_r, core_g, core_b, coreAlpha);


        matrices.pop();
        // No llamo a super.render() porque ya he hecho todo el renderizado que necesito.
    }

    /**
     * Un método helper para añadir un vértice al buffer.
     * Esto hace el código de renderizado principal más limpio y fácil de leer.
     */
    private void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, int r, int g, int b, int a) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, a).next();
    }


    @Override
    public Identifier getTexture(LaserProjectileEntity entity) {
        // Sigo sin usar una textura, pero el método es necesario.
        // El identificador que devuelvo es irrelevante, ya que el RenderLayer `getLightning` no lo usa.
        return new Identifier("minecraft", "textures/particle/flame.png");
    }
}
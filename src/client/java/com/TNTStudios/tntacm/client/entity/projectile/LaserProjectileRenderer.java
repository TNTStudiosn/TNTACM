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
        matrices.push();

        // ========== CONFIGURACIÓN DE COLORES DINÁMICOS ==========
        int core_r, core_g, core_b;
        int glow_r, glow_g, glow_b;
        int outer_r, outer_g, outer_b;

        if (entity instanceof BlueLaserProjectileEntity) {
            // Láser azul: núcleo blanco-azulado, resplandor cian, exterior azul profundo
            core_r = 220; core_g = 240; core_b = 255;
            glow_r = 80;  glow_g = 200; glow_b = 255;
            outer_r = 30; outer_g = 120; outer_b = 200;
        } else { // RedLaserProjectileEntity
            // Láser rojo: núcleo blanco-amarillento, resplandor rojo-naranja, exterior rojo oscuro
            core_r = 255; core_g = 220; core_b = 180;
            glow_r = 255; glow_g = 80;  glow_b = 40;
            outer_r = 180; outer_g = 20; outer_b = 20;
        }

        // ========== EFECTOS DINÁMICOS ==========
        float age = entity.age + tickDelta;
        float speed = (float) entity.getVelocity().length();

        // Pulsación basada en la edad del proyectil
        float pulse = 0.8f + 0.2f * MathHelper.sin(age * 0.5f);

        // Intensidad del brillo basada en velocidad
        float intensity = MathHelper.clamp(speed * 1.2f, 0.6f, 1.4f);

        // Rotación sutil para efecto de energía
        matrices.multiply(this.dispatcher.getRotation());

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLightning());

        // ========== TAMAÑOS DINÁMICOS ==========
        float baseCore = 0.06f * pulse * intensity;
        float baseGlow = 0.18f * intensity;
        float baseOuter = 0.35f * intensity;
        float tailLength = MathHelper.clamp(speed * 0.6f, 0.15f, 1.2f);

        // ========== RENDERIZADO EN CAPAS ==========

        // CAPA 1: Resplandor exterior (muy suave y grande)
        renderQuad(buffer, positionMatrix, baseOuter, outer_r, outer_g, outer_b, (int)(60 * intensity));

        // CAPA 2: Resplandor principal (tamaño medio)
        renderQuad(buffer, positionMatrix, baseGlow, glow_r, glow_g, glow_b, (int)(120 * intensity));

        // CAPA 3: Estela dinámica mejorada
        renderEnhancedTail(buffer, positionMatrix, baseCore, tailLength,
                core_r, core_g, core_b, glow_r, glow_g, glow_b, intensity);

        // CAPA 4: Núcleo pulsante
        renderQuad(buffer, positionMatrix, baseCore, core_r, core_g, core_b, (int)(255 * pulse));

        // CAPA 5: Destellos adicionales para más impacto
        if (speed > 0.5f) {
            renderSparkles(buffer, positionMatrix, age, baseCore * 1.5f,
                    core_r, core_g, core_b, intensity);
        }

        matrices.pop();
    }

    /**
     * Renderiza un quad básico centrado
     */
    private void renderQuad(VertexConsumer buffer, Matrix4f matrix, float size, int r, int g, int b, int alpha) {
        vertex(buffer, matrix, -size, -size, 0, r, g, b, alpha);
        vertex(buffer, matrix, -size,  size, 0, r, g, b, alpha);
        vertex(buffer, matrix,  size,  size, 0, r, g, b, alpha);
        vertex(buffer, matrix,  size, -size, 0, r, g, b, alpha);
    }

    /**
     * Renderiza una estela mejorada con múltiples segmentos para mayor suavidad
     */
    private void renderEnhancedTail(VertexConsumer buffer, Matrix4f matrix, float coreSize, float length,
                                    int coreR, int coreG, int coreB, int glowR, int glowG, int glowB, float intensity) {

        int segments = 6; // Más segmentos = estela más suave

        for (int i = 0; i < segments; i++) {
            float progress = (float) i / segments;
            float z = -length * progress;
            float width = coreSize * (1.0f - progress * 0.7f); // Se va reduciendo
            int alpha = (int)(255 * (1.0f - progress) * intensity);

            // Interpolación de color del núcleo al resplandor
            int r = (int)(coreR * (1.0f - progress) + glowR * progress);
            int g = (int)(coreG * (1.0f - progress) + glowG * progress);
            int b = (int)(coreB * (1.0f - progress) + glowB * progress);

            // Renderizar segmento como quad
            vertex(buffer, matrix, -width,  width, z, r, g, b, alpha);
            vertex(buffer, matrix, -width, -width, z, r, g, b, alpha);
            vertex(buffer, matrix,  width, -width, z, r, g, b, alpha);
            vertex(buffer, matrix,  width,  width, z, r, g, b, alpha);
        }
    }

    /**
     * Añade pequeños destellos dinámicos para proyectiles rápidos
     */
    private void renderSparkles(VertexConsumer buffer, Matrix4f matrix, float age, float baseSize,
                                int r, int g, int b, float intensity) {

        // Crear algunos destellos que rotan alrededor del núcleo
        for (int i = 0; i < 4; i++) {
            float angle = age * 0.3f + (i * MathHelper.PI * 0.5f);
            float distance = baseSize * 0.8f;
            float sparkleSize = baseSize * 0.3f;

            float x = MathHelper.cos(angle) * distance;
            float y = MathHelper.sin(angle) * distance;

            int sparkleAlpha = (int)(180 * intensity * (0.7f + 0.3f * MathHelper.sin(age * 0.7f + i)));

            vertex(buffer, matrix, x - sparkleSize, y - sparkleSize, 0, r, g, b, sparkleAlpha);
            vertex(buffer, matrix, x - sparkleSize, y + sparkleSize, 0, r, g, b, sparkleAlpha);
            vertex(buffer, matrix, x + sparkleSize, y + sparkleSize, 0, r, g, b, sparkleAlpha);
            vertex(buffer, matrix, x + sparkleSize, y - sparkleSize, 0, r, g, b, sparkleAlpha);
        }
    }

    /**
     * Método helper para añadir vértices
     */
    private void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, int r, int g, int b, int a) {
        buffer.vertex(matrix, x, y, z).color(r, g, b, a).next();
    }

    @Override
    public Identifier getTexture(LaserProjectileEntity entity) {
        return new Identifier("minecraft", "textures/particle/flame.png");
    }
}
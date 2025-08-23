// src/client/java/com/TNTStudios/tntacm/client/hud/ShipHudRenderer.java
package com.TNTStudios.tntacm.client.hud;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.MathHelper;

public class ShipHudRenderer {

    //region Animation & State
    private static float displayedHealthPercent = 1.0f;
    private static float lastHealth = -1f;
    private static long lastDamageTime = 0;
    private static final long DAMAGE_FLASH_DURATION = 300L; // ms

    // Suavizado de velocidad para lectura estable
    private static float displayedSpeedBps = 0f;
    //endregion

    //region Constants
    private static final int COLOR_PRIMARY_ACCENT   = 0xFF33FFFF;  // Cyan brillante
    private static final int COLOR_SECONDARY_ACCENT = 0x9033FFFF;  // Cyan 56% transparente
    private static final int COLOR_BACKGROUND       = 0x80000000;  // Negro 50% transparente
    private static final int COLOR_HEALTH_HIGH      = 0xFF00FF00;  // Verde
    private static final int COLOR_HEALTH_MID       = 0xFFFFFF00;  // Amarillo
    private static final int COLOR_HEALTH_LOW       = 0xFFFF0000;  // Rojo
    private static final int COLOR_WHITE            = 0xFFFFFFFF;
    private static final int COLOR_RED_FLASH        = 0x55FF0000;

    // Escala de lectura "realista" (solo visual). 6x ~ números de nave sin tocar física.
    private static final float SPEED_DISPLAY_SCALE = 6.0f;
    private static final float SPEED_SMOOTHING     = 0.15f; // lerp por frame
    //endregion

    public static void render(DrawContext context, float tickDelta) {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        var vehicle = client.player.getVehicle();
        if (!(vehicle instanceof NebulaEntity ship)) return;

        updateAnimationState(ship, tickDelta);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        final int width = context.getScaledWindowWidth();
        final int height = context.getScaledWindowHeight();

        drawVignette(context, width, height);
        drawCrosshair(context, width, height, client.player.getPitch());
        drawHealthIndicatorCompact(context, width, height, ship);
        drawFlightData(context, width, height, ship, tickDelta); // velocidad + cinta espacial
        drawDamageFlash(context, width, height);

        RenderSystem.disableBlend();
    }

    //region State Update
    private static void updateAnimationState(NebulaEntity ship, float tickDelta) {
        float currentHealth = ship.getHealth();
        if (lastHealth == -1) lastHealth = currentHealth;

        if (currentHealth < lastHealth) {
            lastDamageTime = System.currentTimeMillis();
        }
        lastHealth = currentHealth;

        float targetHealthPercent = ship.getHealth() / ship.getMaxHealth();
        // Interpolación suave para animación de vida
        displayedHealthPercent = MathHelper.lerp(tickDelta * 0.15f, displayedHealthPercent, targetHealthPercent);
    }
    //endregion

    //region HUD Components
    private static void drawVignette(DrawContext context, int width, int height) {
        // Soportes de esquina estilo cabina (ligeramente más sutiles)
        int cornerSize = 18;
        int thickness = 2;
        // Superior-izquierda
        context.fill(5, 5, 5 + cornerSize, 5 + thickness, COLOR_SECONDARY_ACCENT);
        context.fill(5, 5, 5 + thickness, 5 + cornerSize, COLOR_SECONDARY_ACCENT);
        // Superior-derecha
        context.fill(width - 5 - cornerSize, 5, width - 5, 5 + thickness, COLOR_SECONDARY_ACCENT);
        context.fill(width - 5 - thickness, 5, width - 5, 5 + cornerSize, COLOR_SECONDARY_ACCENT);
    }

    private static void drawCrosshair(DrawContext context, int width, int height, float pitch) {
        final int centerX = width / 2;
        final int centerY = height / 2;

        // Pulso leve para puntos internos
        float time = (System.currentTimeMillis() % 2000L) / 2000.0f;
        int pulseGap = 4 + (int)(Math.sin(time * 2 * Math.PI) * 2);

        // Líneas exteriores
        context.fill(centerX - 12, centerY, centerX - 8, centerY + 1, COLOR_PRIMARY_ACCENT);
        context.fill(centerX + 8, centerY, centerX + 12, centerY + 1, COLOR_PRIMARY_ACCENT);

        // Puntos interiores
        context.fill(centerX - pulseGap - 1, centerY, centerX - pulseGap, centerY + 1, COLOR_WHITE);
        context.fill(centerX + pulseGap, centerY, centerX + pulseGap + 1, centerY + 1, COLOR_WHITE);

        // Escalera de cabeceo (Pitch Ladder) con números en ambos lados
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);

        int ladderWidth = 80;
        for (int p = -90; p <= 90; p += 10) {
            if (p == 0) continue; // no dibujar 0
            float yOffset = (p - pitch) * -2.5f; // invertido para subir/ bajar
            if (Math.abs(yOffset) < 60) {
                // marcas cortas a izquierda y derecha
                context.fill(-ladderWidth / 2, (int)yOffset, -ladderWidth / 2 + 15, (int)yOffset + 1, COLOR_SECONDARY_ACCENT);
                context.fill(ladderWidth / 2 - 15, (int)yOffset, ladderWidth / 2, (int)yOffset + 1, COLOR_SECONDARY_ACCENT);

                // etiquetas a ambos lados (antes solo izquierda)
                var tr = MinecraftClient.getInstance().textRenderer;
                String label = String.valueOf(p);
                // izquierda
                context.drawText(tr, label, -ladderWidth / 2 - 20, (int)yOffset - 4, COLOR_WHITE, true);
                // derecha
                context.drawText(tr, label, ladderWidth / 2 + 6, (int)yOffset - 4, COLOR_WHITE, true);
            }
        }
        context.getMatrices().pop();
    }

    // Indicador de estructura compacto y moderno
    private static void drawHealthIndicatorCompact(DrawContext context, int width, int height, NebulaEntity ship) {
        final var tr = MinecraftClient.getInstance().textRenderer;

        // Cápsula mini centrada, más pequeña que antes
        final int barWidth = 140;
        final int barHeight = 8;
        final int x = (width - barWidth) / 2;
        final int y = height - 30;

        // Título pequeño
        context.drawTextWithShadow(tr, "ESTRUCTURA", x, y - 11, COLOR_WHITE);

        // Borde fino
        context.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, COLOR_PRIMARY_ACCENT);
        // Fondo
        context.fill(x, y, x + barWidth, y + barHeight, COLOR_BACKGROUND);

        // Barra de vida
        final int healthBarWidth = (int) (barWidth * displayedHealthPercent);
        final int healthColor = getHealthColor(displayedHealthPercent);
        context.fill(x, y, x + healthBarWidth, y + barHeight, healthColor);

        // Línea de brillo superior (1 px) para look moderno
        context.fill(x, y, x + healthBarWidth, y + 1, 0x80FFFFFF);

        // Micro-ticks cada 10%
        for (int i = 1; i < 10; i++) {
            int tx = x + (barWidth * i) / 10;
            context.fill(tx, y + barHeight - 2, tx + 1, y + barHeight, COLOR_SECONDARY_ACCENT);
        }

        // Porcentaje
        final String percentText = String.format("%d%%", (int)(displayedHealthPercent * 100));
        context.drawTextWithShadow(tr, percentText, x + barWidth + 6, y - 1, COLOR_WHITE);
    }

    private static void drawFlightData(DrawContext context, int width, int height, NebulaEntity ship, float tickDelta) {
        final var tr = MinecraftClient.getInstance().textRenderer;

        // --- Velocidad (suavizada) ---
        float rawBps = (float) (ship.getVelocity().length() * 20.0); // bloques/segundo ~ m/s
        // Suavizado para evitar jitter en la lectura
        displayedSpeedBps = MathHelper.lerp(SPEED_SMOOTHING, displayedSpeedBps, rawBps);
        float simMS = displayedSpeedBps * SPEED_DISPLAY_SCALE; // solo visual
        String speedText = String.format("VEL: %.0f m/s", simMS);
        context.drawTextWithShadow(tr, speedText, 15, height - 35, COLOR_WHITE);

        // --- Cinta espacial de orientación (Yaw en grados, sin puntos cardinales) ---
        int tapeWidth = 160;
        int tapeX = (width - tapeWidth) / 2;
        int tapeY = 12;

        // Fondo fino
        context.fill(tapeX, tapeY, tapeX + tapeWidth, tapeY + 2, COLOR_BACKGROUND);

        float yaw = MathHelper.wrapDegrees(ship.getYaw());
        // Ticks cada 15°, etiquetas cada 45°
        for (int i = -180; i <= 180; i += 15) {
            float relativeAngle = MathHelper.wrapDegrees(i - yaw);
            if (Math.abs(relativeAngle) <= 60) {
                int tickX = tapeX + tapeWidth / 2 + (int) (relativeAngle * 1.3f);
                boolean major = (i % 45 == 0);
                // Tick
                context.fill(tickX, tapeY - (major ? 3 : 1), tickX + 1, tapeY + (major ? 5 : 3), major ? COLOR_WHITE : COLOR_SECONDARY_ACCENT);
                // Etiqueta en grados para ticks mayores (sin N/E/S/O)
                if (major) {
                    String label = (i == 0 ? "0°" : (i > 0 ? "+" + i + "°" : i + "°"));
                    context.drawText(tr, label, tickX - tr.getWidth(label) / 2, tapeY + 7, COLOR_WHITE, true);
                }
            }
        }
        // Indicador central
        context.fill(tapeX + tapeWidth / 2, tapeY - 5, tapeX + tapeWidth / 2 + 1, tapeY + 7, COLOR_PRIMARY_ACCENT);

        // Lectura numérica principal de yaw a la derecha
        String yawReadout = String.format("YAW %s%d°", (Math.round(yaw) > 0 ? "+" : (Math.round(yaw) < 0 ? "-" : "")), Math.abs(Math.round(yaw)));
        context.drawTextWithShadow(tr, yawReadout, tapeX + tapeWidth + 8, tapeY + 4, COLOR_WHITE);
    }

    private static void drawDamageFlash(DrawContext context, int width, int height) {
        long timeSinceDamage = System.currentTimeMillis() - lastDamageTime;
        if (timeSinceDamage < DAMAGE_FLASH_DURATION) {
            float alpha = 1.0f - ((float) timeSinceDamage / DAMAGE_FLASH_DURATION);
            int alphaBits = (Math.min(255, (int)(alpha * 0x80))) << 24;
            context.fill(0, 0, width, height, alphaBits | (COLOR_RED_FLASH & 0x00FFFFFF));
        }
    }
    //endregion

    //region Helpers
    private static int getHealthColor(float percent) {
        if (percent > 0.66f) return COLOR_HEALTH_HIGH;
        if (percent > 0.33f) return COLOR_HEALTH_MID;
        return COLOR_HEALTH_LOW;
    }
    //endregion
}

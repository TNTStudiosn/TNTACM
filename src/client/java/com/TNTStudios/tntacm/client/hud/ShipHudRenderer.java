// src/client/java/com/TNTStudios/tntacm/client/hud/ShipHudRenderer.java
package com.TNTStudios.tntacm.client.hud;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ShipHudRenderer {

    //region Animation & State
    private static float displayedHealthPercent = 1.0f;
    private static float lastHealth = -1f;
    private static long lastDamageTime = 0;
    private static final long DAMAGE_FLASH_DURATION = 300L; // en milisegundos
    //endregion

    //region Constants
    private static final int COLOR_PRIMARY_ACCENT = 0xFF33FFFF;   // Cyan brillante
    private static final int COLOR_SECONDARY_ACCENT = 0x9033FFFF; // Cyan, 56% transparente
    private static final int COLOR_BACKGROUND = 0x80000000;       // Negro, 50% transparente
    private static final int COLOR_HEALTH_HIGH = 0xFF00FF00;        // Verde
    private static final int COLOR_HEALTH_MID = 0xFFFFFF00;         // Amarillo
    private static final int COLOR_HEALTH_LOW = 0xFFFF0000;          // Rojo
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_RED_FLASH = 0x55FF0000;      // Rojo para flash de daño
    //endregion

    public static void render(DrawContext context, float tickDelta) {
        final MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof NebulaEntity ship)) return;

        // Actualiza el estado de las animaciones antes de dibujar
        updateAnimationState(ship, tickDelta);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        final int width = context.getScaledWindowWidth();
        final int height = context.getScaledWindowHeight();

        // Renderizar componentes
        drawVignette(context, width, height);
        drawCrosshair(context, width, height, client.player.getPitch());
        drawHealthIndicator(context, width, height, ship);
        drawFlightData(context, width, height, ship);
        drawDamageFlash(context, width, height);

        RenderSystem.disableBlend();
    }

    //region State Update
    private static void updateAnimationState(NebulaEntity ship, float tickDelta) {
        float currentHealth = ship.getHealth();
        if (lastHealth == -1) lastHealth = currentHealth;

        // Detecta si la nave ha recibido daño
        if (currentHealth < lastHealth) {
            lastDamageTime = System.currentTimeMillis();
        }
        lastHealth = currentHealth;

        float targetHealthPercent = ship.getHealth() / ship.getMaxHealth();
        // Interpola suavemente la barra de vida para una animación fluida
        displayedHealthPercent = MathHelper.lerp(tickDelta * 0.15f, displayedHealthPercent, targetHealthPercent);
    }
    //endregion

    //region HUD Components
    private static void drawVignette(DrawContext context, int width, int height) {
        // Soportes de esquina para un look de cabina
        int cornerSize = 20;
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

        // Animación de pulso para el espacio del crosshair
        float time = (System.currentTimeMillis() % 2000L) / 2000.0f;
        int pulseGap = 4 + (int)(Math.sin(time * 2 * Math.PI) * 2);

        // Líneas exteriores estáticas
        context.fill(centerX - 12, centerY, centerX - 8, centerY + 1, COLOR_PRIMARY_ACCENT);
        context.fill(centerX + 8, centerY, centerX + 12, centerY + 1, COLOR_PRIMARY_ACCENT);

        // Puntos interiores con pulso
        context.fill(centerX - pulseGap - 1, centerY, centerX - pulseGap, centerY + 1, COLOR_WHITE);
        context.fill(centerX + pulseGap, centerY, centerX + pulseGap + 1, centerY + 1, COLOR_WHITE);

        // Escalera de cabeceo (Pitch Ladder)
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);

        int ladderWidth = 80;
        for (int p = -90; p <= 90; p += 10) {
            if (p == 0) continue; // No dibujar la línea de 0
            float yOffset = (p - pitch) * -2.5f; // Invertido y escalado para visibilidad
            if (Math.abs(yOffset) < 60) {
                context.fill(-ladderWidth / 2, (int)yOffset, -ladderWidth / 2 + 15, (int)yOffset + 1, COLOR_SECONDARY_ACCENT);
                context.fill(ladderWidth / 2 - 15, (int)yOffset, ladderWidth / 2, (int)yOffset + 1, COLOR_SECONDARY_ACCENT);
                context.drawText(MinecraftClient.getInstance().textRenderer, String.valueOf(p), -ladderWidth / 2 - 20, (int)yOffset - 4, COLOR_WHITE, false);
            }
        }
        context.getMatrices().pop();
    }

    private static void drawHealthIndicator(DrawContext context, int width, int height, NebulaEntity ship) {
        final MinecraftClient client = MinecraftClient.getInstance();
        final int barWidth = 180;
        final int barHeight = 12;
        final int x = (width - barWidth) / 2;
        final int y = height - 40;

        context.drawTextWithShadow(client.textRenderer, "ESTRUCTURA", x, y - 12, COLOR_WHITE);

        // Borde y fondo
        context.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, COLOR_PRIMARY_ACCENT);
        context.fill(x, y, x + barWidth, y + barHeight, COLOR_BACKGROUND);

        // Barra de vida animada
        final int healthBarWidth = (int) (barWidth * displayedHealthPercent);
        final int healthColor = getHealthColor(displayedHealthPercent);
        context.fill(x, y, x + healthBarWidth, y + barHeight, healthColor);

        // Texto de porcentaje
        final String percentText = String.format("%d%%", (int) (displayedHealthPercent * 100));
        context.drawTextWithShadow(client.textRenderer, percentText, x + barWidth + 5, y + 2, COLOR_WHITE);
    }

    private static void drawFlightData(DrawContext context, int width, int height, NebulaEntity ship) {
        final MinecraftClient client = MinecraftClient.getInstance();

        // --- Velocidad ---
        double speedBps = ship.getVelocity().length() * 20.0; // Bloques/segundo
        String speedText = String.format("VEL: %.1f m/s", speedBps);
        context.drawTextWithShadow(client.textRenderer, speedText, 15, height - 35, COLOR_WHITE);

        // --- Compás de Rumbo (Yaw) ---
        int compassWidth = 120;
        int compassX = (width - compassWidth) / 2;
        int compassY = 15;
        context.fill(compassX, compassY, compassX + compassWidth, compassY + 2, COLOR_BACKGROUND);

        float yaw = MathHelper.wrapDegrees(ship.getYaw());
        String[] headings = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};

        for (int i = 0; i < 360; i += 15) {
            float relativeAngle = MathHelper.wrapDegrees(i - yaw);
            if (Math.abs(relativeAngle) < 45) { // Solo dibuja lo visible
                int tickX = compassX + compassWidth / 2 + (int)(relativeAngle * 1.3);
                if (i % 45 == 0) { // Marca principal con etiqueta
                    context.fill(tickX, compassY - 2, tickX + 1, compassY + 4, COLOR_WHITE);
                    String h = headings[(i / 45)];
                    context.drawText(client.textRenderer, h, tickX - client.textRenderer.getWidth(h) / 2, compassY + 8, COLOR_WHITE, true);
                } else { // Marca secundaria
                    context.fill(tickX, compassY, tickX + 1, compassY + 2, COLOR_SECONDARY_ACCENT);
                }
            }
        }
        // Indicador central del compás
        context.fill(compassX + compassWidth / 2, compassY - 4, compassX + compassWidth / 2 + 1, compassY + 6, COLOR_PRIMARY_ACCENT);
    }

    private static void drawDamageFlash(DrawContext context, int width, int height) {
        long timeSinceDamage = System.currentTimeMillis() - lastDamageTime;
        if (timeSinceDamage < DAMAGE_FLASH_DURATION) {
            float alpha = 1.0f - ((float) timeSinceDamage / DAMAGE_FLASH_DURATION);
            int alphaBits = (Math.min(255, (int)(alpha * 0x80))) << 24; // Aumenté un poco la opacidad
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
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

    // Suavizado de velocidad para lectura estable (visual)
    private static float displayedSpeedBps = 0f;
    //endregion

    //region Constants
    private static final int COLOR_PRIMARY_ACCENT     = 0xFF33FFFF;  // Cyan brillante
    private static final int COLOR_SECONDARY_ACCENT = 0x9033FFFF;  // Cyan 56% transparente
    private static final int COLOR_BACKGROUND         = 0x80000000;  // Negro 50% transparente
    private static final int COLOR_HEALTH_HIGH        = 0xFF00C850;  // Verde tecnológico
    private static final int COLOR_HEALTH_MID         = 0xFFFFFF00;  // Amarillo
    private static final int COLOR_HEALTH_LOW         = 0xFFFF0000;  // Rojo
    private static final int COLOR_WHITE              = 0xFFFFFFFF;
    private static final int COLOR_RED_FLASH          = 0x55FF0000;

    // Escala "de nave" (solo visual). Mantengo 6x.
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

        // HUD
        drawVignette(context, width, height);
        drawBrandingACM(context);
        drawCrosshair(context, width, height, client.player.getPitch());
        drawHealthIndicatorMicro(context, width, height, ship);
        drawSpeedReadout(context, width, height, ship);
        drawSystemsPanel(context, width, height);
        drawDamageFlash(context, width, height);
        // Añado el nuevo panel de armamento.
        drawArmamentPanel(context, height, ship);
        // Nuevo: renderizo la alerta de nave desactivada.
        drawDisabledOverlay(context, width, height, ship);


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
        // Suavizo la barra para que no pegue saltos bruscos
        displayedHealthPercent = MathHelper.lerp(tickDelta * 0.15f, displayedHealthPercent, targetHealthPercent);
    }
    //endregion

    //region HUD Components
    private static void drawVignette(DrawContext context, int width, int height) {
        // Soportes de esquina estilo cabina en las 4 esquinas
        int cornerSize = 18;
        int thickness = 2;
        // Superior-izquierda
        context.fill(5, 5, 5 + cornerSize, 5 + thickness, COLOR_SECONDARY_ACCENT);
        context.fill(5, 5, 5 + thickness, 5 + cornerSize, COLOR_SECONDARY_ACCENT);
        // Superior-derecha
        context.fill(width - 5 - cornerSize, 5, width - 5, 5 + thickness, COLOR_SECONDARY_ACCENT);
        context.fill(width - 5 - thickness, 5, width - 5, 5 + cornerSize, COLOR_SECONDARY_ACCENT);
        // Inferior-izquierda
        context.fill(5, height - 5 - thickness, 5 + cornerSize, height - 5, COLOR_SECONDARY_ACCENT);
        context.fill(5, height - 5 - cornerSize, 5 + thickness, height - 5, COLOR_SECONDARY_ACCENT);
        // Inferior-derecha
        context.fill(width - 5 - cornerSize, height - 5 - thickness, width - 5, height - 5, COLOR_SECONDARY_ACCENT);
        context.fill(width - 5 - thickness, height - 5 - cornerSize, width - 5, height - 5, COLOR_SECONDARY_ACCENT);
    }

    // Placa de branding "ACM" (dueña de la nave)
    private static void drawBrandingACM(DrawContext context) {
        final var tr = MinecraftClient.getInstance().textRenderer;
        int x = 10, y = 8;
        int w = 40, h = 12;

        // Borde fino y fondo translúcido
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_PRIMARY_ACCENT);
        context.fill(x, y, x + w, y + h, COLOR_BACKGROUND);

        // Etiqueta ACM centrada
        String brand = "ACM";
        int tx = x + (w - tr.getWidth(brand)) / 2;
        int ty = y + 2;
        context.drawTextWithShadow(tr, brand, tx, ty, COLOR_WHITE);

        // Línea inferior dinámica tipo "actividad"
        long t = System.currentTimeMillis() % 1000L;
        int seg = (int) (w * (t / 1000f));
        context.fill(x, y + h, x + seg, y + h + 1, COLOR_PRIMARY_ACCENT);
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

        // Escalera de cabeceo (Pitch Ladder) con números a ambos lados
        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);

        int ladderWidth = 80;
        for (int p = -90; p <= 90; p += 10) {
            if (p == 0) continue; // omito 0 para mantener limpio el centro
            float yOffset = (p - pitch) * -2.5f; // invertido para coherencia visual
            if (Math.abs(yOffset) < 60) {
                // marcas cortas a izquierda y derecha
                context.fill(-ladderWidth / 2, (int)yOffset, -ladderWidth / 2 + 15, (int)yOffset + 1, COLOR_SECONDARY_ACCENT);
                context.fill(ladderWidth / 2 - 15, (int)yOffset, ladderWidth / 2, (int)yOffset + 1, COLOR_SECONDARY_ACCENT);

                // etiquetas a ambos lados
                var tr = MinecraftClient.getInstance().textRenderer;
                String label = String.valueOf(p);
                context.drawText(tr, label, -ladderWidth / 2 - 20, (int)yOffset - 4, COLOR_WHITE, true);
                context.drawText(tr, label,  ladderWidth / 2 + 6,  (int)yOffset - 4, COLOR_WHITE, true);
            }
        }
        context.getMatrices().pop();
    }

    // Indicador de estructura ahora llamado "CASCO"
    private static void drawHealthIndicatorMicro(DrawContext context, int width, int height, NebulaEntity ship) {
        final var tr = MinecraftClient.getInstance().textRenderer;

        // Cápsula micro centrada
        final int barWidth = 110;  // más pequeño
        final int barHeight = 6;   // más delgado
        final int x = (width - barWidth) / 2;
        final int y = height - 26;

        // Título compacto
        context.drawTextWithShadow(tr, "CASCO", x, y - 10, COLOR_WHITE);

        // Borde fino
        context.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, COLOR_PRIMARY_ACCENT);
        // Fondo
        context.fill(x, y, x + barWidth, y + barHeight, COLOR_BACKGROUND);

        // Barra de vida
        final int healthBarWidth = (int) (barWidth * displayedHealthPercent);
        final int healthColor = getHealthColor(displayedHealthPercent);
        context.fill(x, y, x + healthBarWidth, y + barHeight, healthColor);

        // Shimmer de “sistemas” barrido de izquierda a derecha
        long t = System.currentTimeMillis() % 1200L;
        int sweep = (int) (healthBarWidth * (t / 1200f));
        int sweepWidth = Math.max(4, barWidth / 12);
        int sx0 = x + Math.max(0, Math.min(healthBarWidth - 1, sweep));
        int sx1 = Math.min(x + healthBarWidth, sx0 + sweepWidth);
        if (sx1 > sx0) {
            context.fill(sx0, y, sx1, y + barHeight, 0x40FFFFFF); // brillo suave
            context.fill(sx0, y, sx1, y + 1, 0x80FFFFFF);       // línea superior
        }

        // Micro-ticks cada 10% (sutiles)
        for (int i = 1; i < 10; i++) {
            int tx = x + (barWidth * i) / 10;
            context.fill(tx, y + barHeight - 2, tx + 1, y + barHeight, COLOR_SECONDARY_ACCENT);
        }

        // Pulso si la vida es crítica (<25%)
        if (displayedHealthPercent < 0.25f && !ship.isDisabled()) {
            float pulse = 0.5f + 0.5f * (float)Math.sin((System.currentTimeMillis() % 600L) / 600f * (float)(2 * Math.PI));
            int alpha = (int)(80 * pulse) << 24;
            context.fill(x - 2, y - 2, x + barWidth + 2, y + barHeight + 2, alpha | (COLOR_HEALTH_LOW & 0x00FFFFFF));
        }

        // Porcentaje
        final String percentText = String.format("%d%%", (int)(displayedHealthPercent * 100));
        context.drawTextWithShadow(tr, percentText, x + barWidth + 6, y - 1, COLOR_WHITE);
    }

    // Solo velocidad (sin yaw)
    private static void drawSpeedReadout(DrawContext context, int width, int height, NebulaEntity ship) {
        final var tr = MinecraftClient.getInstance().textRenderer;

        float rawBps = (float) (ship.getVelocity().length() * 20.0); // bloques/segundo ~ m/s
        // Suavizo para que la lectura no tiemble
        displayedSpeedBps = MathHelper.lerp(SPEED_SMOOTHING, displayedSpeedBps, rawBps);
        float simMS = displayedSpeedBps * SPEED_DISPLAY_SCALE; // solo visual

        String speedText = String.format("VEL: %.0f m/s", simMS);
        context.drawTextWithShadow(tr, speedText, 15, height - 35, COLOR_WHITE);
    }

    // Panel de munición y recarga, ahora con más detalle visual
    private static void drawArmamentPanel(DrawContext context, int height, NebulaEntity ship) {
        final var tr = MinecraftClient.getInstance().textRenderer;

        // Aumento el tamaño del panel para un diseño más claro y estilizado.
        int panelW = 100, panelH = 40;
        int x = 10;
        int y = height - panelH - 45; // Lo posiciono arriba del indicador de velocidad.

        // Marco y fondo, sin cambios.
        context.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, COLOR_PRIMARY_ACCENT);
        context.fill(x, y, x + panelW, y + panelH, COLOR_BACKGROUND);

        // Título del panel "ARMAMENTO" con una línea divisoria debajo.
        context.drawTextWithShadow(tr, "ARMAMENTO", x + 6, y + 4, COLOR_WHITE);
        context.fill(x + 4, y + 14, x + panelW - 4, y + 15, COLOR_SECONDARY_ACCENT);

        // Defino el área para la barra de progreso.
        final int barX = x + 6;
        final int barY = y + 28;
        final int barH = 6;
        final int innerWidth = panelW - 12;

        if (ship.isReloading()) {
            // --- ESTADO DE RECARGA MEJORADO ---
            final float totalReloadSeconds = NebulaEntity.RELOAD_TIME / 20.0f; // Convierto ticks a segundos
            float reloadProgress = ship.getReloadProgress();
            // Gracias al DataTracker, el progreso y el tiempo restante ahora son correctos.
            float remainingSeconds = (1.0f - reloadProgress) * totalReloadSeconds;

            // Etiqueta de estado y contador de tiempo.
            String statusText = "CARGANDO...";
            String timeText = String.format("%.1f S", remainingSeconds);
            context.drawTextWithShadow(tr, statusText, x + 6, y + 18, COLOR_HEALTH_MID);
            context.drawTextWithShadow(tr, timeText, x + panelW - tr.getWidth(timeText) - 6, y + 18, COLOR_HEALTH_MID);

            // Barra de progreso de recarga con efecto visual.
            int progressWidth = (int) (innerWidth * reloadProgress);
            // Fondo de la barra.
            context.fill(barX, barY, barX + innerWidth, barY + barH, 0x55AAAAAA);
            // Barra de progreso.
            context.fill(barX, barY, barX + progressWidth, barY + barH, COLOR_PRIMARY_ACCENT);
            // Borde brillante en el progreso.
            context.fill(barX, barY, barX + progressWidth, barY + 1, 0xAAFFFFFF);

        } else {
            // --- ESTADO DE MUNICIÓN MEJORADO ---
            int currentAmmo = ship.getAmmo();
            int maxAmmo = ship.getMaxAmmo();
            float ammoRatio = (maxAmmo > 0) ? (float) currentAmmo / maxAmmo : 0f;

            // Etiqueta de estado "LISTO" y contador numérico de munición.
            String statusText = "LISTO";
            String ammoText = String.format("%03d", currentAmmo); // Formato con ceros a la izquierda, ej: 025

            context.drawTextWithShadow(tr, statusText, x + 6, y + 18, COLOR_HEALTH_HIGH);
            context.drawTextWithShadow(tr, ammoText, x + panelW - tr.getWidth(ammoText) - 6, y + 18, COLOR_WHITE);

            // Barra de munición.
            int ammoBarWidth = (int) (innerWidth * ammoRatio);
            int ammoColor = getAmmoColor(ammoRatio);

            // Fondo de la barra.
            context.fill(barX, barY, barX + innerWidth, barY + barH, 0x55000000);
            // Barra de munición.
            context.fill(barX, barY, barX + ammoBarWidth, barY + barH, ammoColor);

            // Pequeño borde para dar profundidad.
            if (ammoBarWidth > 0) {
                context.fill(barX, barY, barX + ammoBarWidth, barY + 1, 0x60FFFFFF);
            }
        }
    }


    // Panel de “sistemas funcionando”: barras animadas y secuencia de puntos
    private static void drawSystemsPanel(DrawContext context, int width, int height) {
        final var tr = MinecraftClient.getInstance().textRenderer;

        int panelW = 90, panelH = 32;
        int x = width - panelW - 10;
        int y = 8;

        // Marco y fondo
        context.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, COLOR_PRIMARY_ACCENT);
        context.fill(x, y, x + panelW, y + panelH, COLOR_BACKGROUND);

        // Título
        context.drawText(tr, "SISTEMAS", x + 6, y + 2, COLOR_WHITE, true);

        // Barras animadas (tres subsistemas)
        long now = System.currentTimeMillis();
        drawAnimBar(context, x + 6, y + 12, panelW - 12, 4, now, 0);  // ENG
        drawAnimBar(context, x + 6, y + 18, panelW - 12, 4, now, 180); // COM
        drawAnimBar(context, x + 6, y + 24, panelW - 12, 4, now, 360); // LIFE

        // Secuencia de puntos "online"
        int dots = (int)((now / 250) % 4); // 0..3
        String status = switch (dots) {
            case 0 -> "●○○";
            case 1 -> "●●○";
            case 2 -> "●●●";
            default -> "○○○";
        };
        context.drawText(tr, status, x + panelW - tr.getWidth(status) - 6, y + 2, COLOR_WHITE, true);
    }

    private static void drawAnimBar(DrawContext ctx, int x, int y, int w, int h, long now, int phaseDeg) {
        // Hago un diente de sierra que recorre la barra para dar sensación de flujo de datos/energía
        float phase = (phaseDeg / 360f);
        float t = ((now % 1200L) / 1200f + phase) % 1f;
        int runW = Math.max(6, w / 6);
        int sx = x + (int)(t * (w - runW));
        // Fondo tenue
        ctx.fill(x, y, x + w, y + h, 0x40222222);
        // “Carga” principal
        ctx.fill(sx, y, sx + runW, y + h, COLOR_PRIMARY_ACCENT);
        // Borde superior claro
        ctx.fill(sx, y, sx + runW, y + 1, 0x80FFFFFF);
    }

    private static void drawDamageFlash(DrawContext context, int width, int height) {
        long timeSinceDamage = System.currentTimeMillis() - lastDamageTime;
        if (timeSinceDamage < DAMAGE_FLASH_DURATION) {
            float alpha = 1.0f - ((float) timeSinceDamage / DAMAGE_FLASH_DURATION);
            int alphaBits = (Math.min(255, (int)(alpha * 0x80))) << 24;
            context.fill(0, 0, width, height, alphaBits | (COLOR_RED_FLASH & 0x00FFFFFF));
        }
    }

    /**
     * Nuevo: Dibuja una viñeta roja parpadeante cuando la nave está desactivada.
     */
    private static void drawDisabledOverlay(DrawContext context, int width, int height, NebulaEntity ship) {
        if (ship.isDisabled()) {
            // Creo un parpadeo usando una onda sinusoidal para el alfa.
            float pulse = 0.6f + 0.4f * (float)Math.sin((System.currentTimeMillis() % 1000L) / 1000f * (float)(2 * Math.PI));
            int alpha = (int)(100 * pulse) << 24; // Intensidad máxima de ~40%
            int color = alpha | (COLOR_RED_FLASH & 0x00FFFFFF);
            context.fill(0, 0, width, height, color);

            // Mensaje de alerta en el centro de la pantalla.
            final var tr = MinecraftClient.getInstance().textRenderer;
            String warningMsg = "DAÑO CRÍTICO - SISTEMAS EN RECUPERACIÓN";
            int textX = (width - tr.getWidth(warningMsg)) / 2;
            int textY = height / 2 + 70;
            context.drawTextWithShadow(tr, warningMsg, textX, textY, COLOR_HEALTH_LOW);
        }
    }

    //endregion

    //region Helpers
    private static int getHealthColor(float percent) {
        if (percent > 0.66f) return COLOR_HEALTH_HIGH;
        if (percent > 0.33f) return COLOR_HEALTH_MID;
        return COLOR_HEALTH_LOW;
    }

    /**
     * Nuevo helper para colorear la barra de munición según la cantidad restante.
     * Verde -> Amarillo -> Rojo
     */
    private static int getAmmoColor(float percent) {
        if (percent > 0.5f) return COLOR_HEALTH_HIGH;
        if (percent > 0.2f) return COLOR_HEALTH_MID;
        return COLOR_HEALTH_LOW;
    }
    //endregion
}
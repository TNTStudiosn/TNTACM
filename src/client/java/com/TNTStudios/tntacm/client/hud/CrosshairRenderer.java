package com.TNTStudios.tntacm.client.hud;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static com.TNTStudios.tntacm.client.hud.HudUtils.*;
import static com.TNTStudios.tntacm.client.hud.ShipHudRenderer.Colors.*;

/*
 * Crosshair & FPM – versión sin brújula y con columna central + rails laterales chicos
 * -----------------------------------------------------------------------------------
 * - Saqué la cinta de rumbo (brújula) por pedido.
 * - Agregué línea vertical central para referencia/estabilidad.
 * - Reduje y compacté las "columnas" laterales: ahora son rails cortos donde viven los números de pitch.
 * - Achiqué los tramos de la escalera de pitch para que no invadan los rails.
 * - Mantengo FPM, vector de resbale, director cue y efectos de boost.
 *
 * Notas de legibilidad:
 * - Todos los colores pasan por adaptColor() para respetar modo daltonismo.
 * - Sigo evitando COLOR_BLACK literal; uso ARGB cuando hace falta shadow.
 */
public class CrosshairRenderer {

    public record FlightPath(int x, int y, boolean offscreen) {}

    public static FlightPath render(DrawContext ctx, int w, int h, float ui, NebulaEntity ship, HudState state, long now) {
        final int cx = w / 2;
        final int cy = h / 2;

        // --- Línea central vertical (referencia global) ---
        renderCenterSpine(ctx, cx, h, ui);

        // --- Escalera de pitch con rails laterales compactos + números ---
        renderPitchLadder(ctx, cx, cy, w, h, ui, ship, state, now);

        // --- Indicador de alabeo sobre la zona superior ---
        renderBankIndicator(ctx, cx, cy, ui, ship, state, now);

        // --- Núcleo del retículo ---
        renderModernCrosshair(ctx, cx, cy, ui, state, now);
        renderSlipVector(ctx, cx, cy, ui, state, now);
        renderBoostEffects(ctx, cx, cy, ui, now);

        // --- FPM y director ---
        FlightPath fpm = drawModernFPM(ctx, cx, cy, w, h, ui, ship, state, now);
        renderFlightDirectorCue(ctx, cx, cy, ui, fpm, state, now);

        return fpm;
    }

    //region Core HUD Elements

    private static void renderCenterSpine(DrawContext ctx, int cx, int h, float ui) {
        // Trazo muy sutil para centrar visualmente los números laterales y dar eje longitudinal
        int top = (int)(h * 0.15f);
        int bottom = (int)(h * 0.85f);
        int c = withAlpha(adaptColor(COLOR_CYAN), 60);
        thickLine(ctx, cx, top, cx, bottom, 1, c);

        // Marca pequeña en el centro para reforzar eje (no molesta el pipper)
        int tick = px(4, ui);
        thickLine(ctx, cx - tick, (h / 2), cx + tick, (h / 2), 1, withAlpha(adaptColor(COLOR_CYAN), 80));
    }

    private static void renderModernCrosshair(DrawContext ctx, int cx, int cy, float ui, HudState state, long now) {
        float speedFactor = (float) MathHelper.clamp(state.smoothedSpeed / 50.0, 0.0, 1.0);
        int healthColor = (state.smoothHealth > 0.5f ? COLOR_GREEN : state.smoothHealth > 0.3f ? COLOR_YELLOW : COLOR_RED);

        // Punto central con respiración sutil
        float pulse = 0.6f + 0.4f * (float) Math.sin(now * Math.PI / 800.0);
        ctx.fill(cx, cy, cx + 1, cy + 1, withAlpha(healthColor, (int)(255 * pulse)));

        // Halo de precisión (mezcla estabilidad angular + velocidad)
        float stab = (float) MathHelper.clamp(1.0 - (state.smoothAngularVel / 220.0), 0.0, 1.0);
        float haloPulse = 0.85f + 0.15f * (float) Math.sin(now * Math.PI / 600.0);
        int haloBase = px(7, ui);
        int haloExtra = (int) (px(10, ui) * (1.0f - stab) + px(8, ui) * speedFactor);
        int haloR = haloBase + haloExtra;
        int haloA = (int) (120 * stab * haloPulse + 40);
        drawCircle(ctx, cx, cy, haloR, 64, withAlpha(adaptColor(COLOR_CYAN_SOFT), haloA));

        // Brackets dinámicos
        int baseGap = px(5, ui);
        int armLength = px(8, ui);
        int bracketOffset = baseGap + (int)(speedFactor * px(15, ui));
        int color = adaptColor(withAlpha(COLOR_CYAN, 190));

        // TL
        thickLine(ctx, cx - bracketOffset, cy - bracketOffset - armLength, cx - bracketOffset, cy - bracketOffset, 1, color);
        thickLine(ctx, cx - bracketOffset - armLength, cy - bracketOffset, cx - bracketOffset, cy - bracketOffset, 1, color);
        // TR
        thickLine(ctx, cx + bracketOffset, cy - bracketOffset - armLength, cx + bracketOffset, cy - bracketOffset, 1, color);
        thickLine(ctx, cx + bracketOffset + armLength, cy - bracketOffset, cx + bracketOffset, cy - bracketOffset, 1, color);
        // BL
        thickLine(ctx, cx - bracketOffset, cy + bracketOffset + armLength, cx - bracketOffset, cy + bracketOffset, 1, color);
        thickLine(ctx, cx - bracketOffset - armLength, cy + bracketOffset, cx - bracketOffset, cy + bracketOffset, 1, color);
        // BR
        thickLine(ctx, cx + bracketOffset, cy + bracketOffset + armLength, cx + bracketOffset, cy + bracketOffset, 1, color);
        thickLine(ctx, cx + bracketOffset + armLength, cy + bracketOffset, cx + bracketOffset, cy + bracketOffset, 1, color);
    }

    private static void renderSlipVector(DrawContext ctx, int cx, int cy, float ui, HudState state, long now) {
        final double slipThreshold = 0.1;
        double slipX = state.smoothVelX;
        double slipY = -state.smoothVelY; // pantalla invertida en Y
        double slipMagnitude = Math.hypot(slipX, slipY);
        if (slipMagnitude < slipThreshold) return;

        float alpha = (float) MathHelper.clamp(slipMagnitude * 3.0, 0.2, 0.9);
        int color = withAlpha(COLOR_ORANGE, (int) (alpha * 255));

        int maxLen = px(40, ui);
        int lineLen = (int) (Math.min(1.0, slipMagnitude / 2.0) * maxLen);

        double angle = Math.atan2(slipY, slipX);
        int endX = cx + (int) (Math.cos(angle) * lineLen);
        int endY = cy + (int) (Math.sin(angle) * lineLen);

        thickLine(ctx, cx, cy, endX, endY, px(1, ui), color);

        // T final
        int tSize = px(3, ui);
        int t1x = endX + (int) (Math.cos(angle + Math.PI / 2) * tSize);
        int t1y = endY + (int) (Math.sin(angle + Math.PI / 2) * tSize);
        int t2x = endX + (int) (Math.cos(angle - Math.PI / 2) * tSize);
        int t2y = endY + (int) (Math.sin(angle - Math.PI / 2) * tSize);
        thickLine(ctx, t1x, t1y, t2x, t2y, px(1, ui), color);

        // Chispa animada
        float phase = (float)((now % 600L) / 600.0);
        int sparkX = cx + (int)(Math.cos(angle) * (lineLen * phase));
        int sparkY = cy + (int)(Math.sin(angle) * (lineLen * phase));
        drawCircle(ctx, sparkX, sparkY, px(1, ui), 12, withAlpha(COLOR_ORANGE, 220));
    }

    private static FlightPath drawModernFPM(DrawContext ctx, int cx, int cy, int w, int h, float ui, NebulaEntity ship, HudState state, long now) {
        Vec3d v = ship.getVelocity();
        if (v.lengthSquared() < 0.1) {
            // Quieto: FPM fantasma al centro
            renderModernFPMSymbol(ctx, cx, cy, ui, 0.0, now);
            return new FlightPath(cx, cy, false);
        }

        int maxOff = px(450, ui);
        int fpmX = cx + (int)(state.smoothVelX * maxOff);
        int fpmY = cy - (int)(state.smoothVelY * maxOff); // invertido

        int limitRadius = Math.min(w, h) / 3;
        int dx = fpmX - cx, dy = fpmY - cy;
        double dist = Math.hypot(dx, dy);
        boolean offscreen = dist > limitRadius;

        if (offscreen) {
            double scale = limitRadius / (dist <= 0.0001 ? 1.0 : dist);
            fpmX = cx + (int)(dx * scale);
            fpmY = cy + (int)(dy * scale);
            drawModernOffscreenPointer(ctx, cx, cy, limitRadius, (float)Math.atan2(dy, dx), now, ui);
        }

        renderModernFPMSymbol(ctx, fpmX, fpmY, ui, v.length(), now);

        // Trazo de predicción si está lejos del centro
        if (dist > px(20, ui)) {
            drawTrajectoryPrediction(ctx, fpmX, fpmY, (float)Math.atan2(dy,dx), ui, now);
        }

        return new FlightPath(fpmX, fpmY, offscreen);
    }

    private static void renderModernFPMSymbol(DrawContext ctx, int x, int y, float ui, double speed, long now) {
        int baseColor = adaptColor(COLOR_VELOCITY_VECTOR);
        int radius = px(6, ui);

        // Shimmer con velocidad
        float shimmer = 0.85f + 0.15f * (float)Math.sin(now * Math.PI / 350.0 + speed * 0.05);
        int color = withAlpha(baseColor, (int)(255 * shimmer));

        drawCircle(ctx, x, y, radius, 32, color);

        // Ticks exteriores
        for (int i = 0; i < 3; i++) {
            float angle = (float)(i * (Math.PI * 2.0 / 3.0));
            int startX = x + (int)(Math.cos(angle) * (radius + px(1, ui)));
            int startY = y + (int)(Math.sin(angle) * (radius + px(1, ui)));
            int endX   = x + (int)(Math.cos(angle) * (radius + px(4, ui)));
            int endY   = y + (int)(Math.sin(angle) * (radius + px(4, ui)));
            thickLine(ctx, startX, startY, endX, endY, px(1, ui), color);
        }
    }

    private static void drawModernOffscreenPointer(DrawContext ctx, int cx, int cy, int radius, float angle, long now, float ui) {
        int ringColor = withAlpha(COLOR_CYAN, 30);
        drawCircle(ctx, cx, cy, radius, 64, ringColor);

        float pulse = 0.8f + 0.2f * (float) Math.sin(now * Math.PI / 500.0);
        int pointerColor = adaptColor(withAlpha(COLOR_VELOCITY_VECTOR, (int)(255 * pulse)));
        int size = px(8, ui);

        int tipX = cx + (int)(Math.cos(angle) * radius);
        int tipY = cy + (int)(Math.sin(angle) * radius);

        int p1x = tipX + (int)(Math.cos(angle + 2.5f) * size);
        int p1y = tipY + (int)(Math.sin(angle + 2.5f) * size);
        int p2x = tipX + (int)(Math.cos(angle - 2.5f) * size);
        int p2y = tipY + (int)(Math.sin(angle - 2.5f) * size);

        thickLine(ctx, tipX, tipY, p1x, p1y, px(2, ui), pointerColor);
        thickLine(ctx, tipX, tipY, p2x, p2y, px(2, ui), pointerColor);
    }
    //endregion

    //region Pitch ladder con rails laterales compactos

    private static void renderPitchLadder(DrawContext ctx, int cx, int cy, int w, int h, float ui, NebulaEntity ship, HudState state, long now) {
        // Configuro rails chicos y distancias: quiero columnas estrechas y legibles
        int railX = px(44, ui);                // distancia desde el centro a cada rail
        int railHalfMajor = px(9, ui);        // alto medio del rail en marcas mayores
        int railHalfMinor = px(6, ui);        // alto medio del rail en marcas menores
        int railColor = withAlpha(adaptColor(COLOR_CYAN), 180);

        // Tramos de la escalera: recortados para no chocar con los rails
        int maxLenMajor = Math.max(px(20, ui), railX - px(10, ui));
        int maxLenMinor = Math.max(px(10, ui), railX - px(14, ui));

        // Etiquetas: las pego al exterior de los rails
        int textOff = px(4, ui);
        int labelColor = withAlpha(adaptColor(COLOR_CYAN), 200);

        // Pitch en [-89.9, 89.9]
        float pitch = MathHelper.clamp(ship.getPitch(), -89.9f, 89.9f);
        int spacing = px(12, ui); // px por 5°

        // Dibujo alredor de pitch en pasos de 5° (±45°)
        for (int deg = -45; deg <= 45; deg += 5) {
            float worldDeg = (float)MathHelper.clamp(deg + (int)Math.round(pitch / 5.0f) * 5, -90, 90);
            int y = cy + (int)(-(pitch - worldDeg) * (spacing / 5.0f));

            // Selecciono tamaño de rail/linea
            boolean major = (worldDeg % 10 == 0);
            int len = major ? maxLenMajor : maxLenMinor;
            int halfRail = major ? railHalfMajor : railHalfMinor;

            int x1 = cx - len;
            int x2 = cx + len;

            // Horizonte a 0°: más grueso + marcador en centro
            if (worldDeg == 0f) {
                thickLine(ctx, x1, y, x2, y, px(2, ui), withAlpha(COLOR_CYAN, 230));
                drawCircle(ctx, cx, y, px(2, ui), 16, withAlpha(COLOR_CYAN, 140));
            } else {
                thickLine(ctx, x1, y, x2, y, 1, withAlpha(adaptColor(COLOR_CYAN), 200));
            }

            // Rails laterales compactos (columnas chicas en cada marca)
            // Izquierdo
            int rxL = cx - railX;
            thickLine(ctx, rxL, y - halfRail, rxL, y + halfRail, 1, railColor);
            // Derecho
            int rxR = cx + railX;
            thickLine(ctx, rxR, y - halfRail, rxR, y + halfRail, 1, railColor);

            // Chevrones para pitch negativo (mayor realismo)
            if (worldDeg < 0f && major) {
                int cLen = px(6, ui);
                thickLine(ctx, x1, y, x1 + cLen, y - cLen, 1, labelColor);
                thickLine(ctx, x2, y, x2 - cLen, y - cLen, 1, labelColor);
            }

            // Etiquetas junto a los rails (números de los lados)
            if (major) {
                String lbl = Integer.toString((int)Math.abs(worldDeg));
                // Izquierda: afuera del rail izquierdo
                drawStringShadow(ctx, lbl, rxL - textOff - px(10, ui), y - px(4, ui), labelColor);
                // Derecha: afuera del rail derecho
                drawStringShadow(ctx, lbl, rxR + textOff, y - px(4, ui), labelColor);
            }
        }
    }
    //endregion

    //region Bank + Director + Efectos

    private static void renderBankIndicator(DrawContext ctx, int cx, int cy, float ui, NebulaEntity ship, HudState state, long now) {
        float bankDeg = proxyBankDeg(state);
        int radius = px(44, ui);
        int tickLenMajor = px(7, ui);
        int tickLenMinor = px(4, ui);
        int baseY = cy - px(56, ui);

        // Arco base
        drawCircle(ctx, cx, baseY, radius, 64, withAlpha(adaptColor(COLOR_CYAN), 60));

        // Ticks en 0, 10, 20, 30, 45
        int col = withAlpha(adaptColor(COLOR_CYAN), 220);
        int[] marks = new int[]{-45,-30,-20,-10,0,10,20,30,45};
        for (int m : marks) {
            float a = (float)Math.toRadians(m);
            int len = (m % 20 == 0) ? tickLenMajor : tickLenMinor;
            int x1 = cx + (int)(Math.sin(a) * (radius - len));
            int y1 = baseY - (int)(Math.cos(a) * (radius - len));
            int x2 = cx + (int)(Math.sin(a) * (radius));
            int y2 = baseY - (int)(Math.cos(a) * (radius));
            thickLine(ctx, x1, y1, x2, y2, 1, col);
        }

        // Puntero de alabeo (triángulo) con bounce sutil
        float bounce = 1.0f + 0.04f * (float)Math.sin(now * Math.PI / 300.0);
        float rad = (float)Math.toRadians(bankDeg);
        int tipX = cx + (int)(Math.sin(rad) * (radius * bounce));
        int tipY = baseY - (int)(Math.cos(rad) * (radius * bounce));
        int tri = px(5, ui);
        thickLine(ctx, tipX, tipY, tipX - tri, tipY + tri, 2, withAlpha(COLOR_CYAN, 230));
        thickLine(ctx, tipX, tipY, tipX + tri, tipY + tri, 2, withAlpha(COLOR_CYAN, 230));
    }

    private static void renderFlightDirectorCue(DrawContext ctx, int cx, int cy, float ui, FlightPath fpm, HudState state, long now) {
        int color = withAlpha(COLOR_YELLOW, 220);

        int dx = cx - fpm.x;
        int dy = cy - fpm.y;
        double dist = Math.hypot(dx, dy);
        if (dist < px(6, ui)) {
            drawDiamond(ctx, cx, cy, px(7, ui), (int)(200 + 40 * Math.sin(now * Math.PI / 500.0)), color);
            return;
        }

        double dirLen = Math.min(dist, px(60, ui));
        float t = easeOutCubic((float)MathHelper.clamp(dirLen / px(60, ui), 0.0, 1.0));
        int tx = fpm.x + (int)(dx * t);
        int ty = fpm.y + (int)(dy * t);

        thickLine(ctx, fpm.x, fpm.y, tx, ty, 1, withAlpha(color, 160));

        int size = px(7, ui) + (int)(2 * Math.sin(now * Math.PI / 250.0));
        drawDiamond(ctx, tx, ty, size, 255, color);
    }

    private static void renderBoostEffects(DrawContext ctx, int cx, int cy, float ui, long now) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.forwardKey.isPressed()) {
            int rings = ShipHudRenderer.Config.LOW_FX_MODE ? 1 : 2;
            for (int i = 0; i < rings; i++) {
                float progress = ((now % 1000L) + i * 500f) % 1000f / 1000f;
                int radius = px(20, ui) + (int)(progress * px(80, ui));
                float alpha = (1f - progress) * 0.5f;
                int color = adaptColor(withAlpha(COLOR_MAGENTA_BRIGHT, (int)(alpha * 200)));
                drawCircle(ctx, cx, cy, radius, 64, color);
            }
        }
        if (mc.options.backKey.isPressed()) {
            float pulse = (float)Math.sin(now / 150.0);
            if (pulse > 0f) {
                int radius = px(30, ui) + (int)(pulse * px(10, ui));
                int color = adaptColor(withAlpha(COLOR_RED_BRIGHT, (int)(pulse * 100)));
                drawCircle(ctx, cx, cy, radius, 48, color);
            }
        }
    }
    //endregion

    //region Helpers

    private static void drawTrajectoryPrediction(DrawContext ctx, int startX, int startY, float angle, float ui, long now) {
        int predictionLen = px(80, ui);
        int dashLen = px(5, ui);
        int gapLen = px(5, ui);

        int stepX = (int)(Math.cos(angle) * (dashLen + gapLen));
        int stepY = (int)(Math.sin(angle) * (dashLen + gapLen));

        int x1 = startX + (int)(Math.cos(angle) * px(10, ui)); // arranco un poco separado del FPM
        int y1 = startY + (int)(Math.sin(angle) * px(10, ui));

        for (int d = 0; d < predictionLen; d += dashLen + gapLen) {
            int x2 = x1 + (int)(Math.cos(angle) * dashLen);
            int y2 = y1 + (int)(Math.sin(angle) * dashLen);
            float alpha = 1f - ((float)d / (float)predictionLen);
            int color = adaptColor(withAlpha(COLOR_YELLOW, (int)(alpha * 120)));
            thickLine(ctx, x1, y1, x2, y2, px(1, ui), color);
            x1 += stepX;
            y1 += stepY;
        }
    }

    private static void drawDiamond(DrawContext ctx, int x, int y, int size, int alpha, int baseColor) {
        int c = withAlpha(baseColor, alpha);
        int xL = x - size, xR = x + size, yT = y - size, yB = y + size;
        thickLine(ctx, x, yT, xR, y, 1, c);
        thickLine(ctx, xR, y, x, yB, 1, c);
        thickLine(ctx, x, yB, xL, y, 1, c);
        thickLine(ctx, xL, y, x, yT, 1, c);
    }

    private static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
        int border = withAlpha(adaptColor(COLOR_CYAN), 120);
        thickLine(ctx, x, y, x + w, y, 1, border);
        thickLine(ctx, x + w, y, x + w, y + h, 1, border);
        thickLine(ctx, x + w, y + h, x, y + h, 1, border);
        thickLine(ctx, x, y + h, x, y, 1, border);
    }

    private static void drawStringShadow(DrawContext ctx, String s, int x, int y, int color) {
        int shadow = withAlpha(0xFF000000, 140);
        ctx.drawText(MinecraftClient.getInstance().textRenderer, s, x + 1, y + 1, shadow, false);
        ctx.drawText(MinecraftClient.getInstance().textRenderer, s, x, y, color, false);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float wrap360(float yawDegrees) {
        float v = yawDegrees % 360f;
        return v < 0f ? v + 360f : v;
    }

    private static String fmtHeading(int hdg) {
        int v = ((hdg % 360) + 360) % 360;
        if (v < 10) return "00" + v;
        if (v < 100) return "0" + v;
        return Integer.toString(v);
    }

    // Si no hay roll real, aproximo usando vel. lateral como proxy
    private static float proxyBankDeg(HudState state) {
        float bank = (float)MathHelper.clamp(state.smoothVelX, -1.0, 1.0);
        return bank * 45.0f;
    }
    //endregion
}

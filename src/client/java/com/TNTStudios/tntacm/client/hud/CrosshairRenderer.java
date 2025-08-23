package com.TNTStudios.tntacm.client.hud;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import static com.TNTStudios.tntacm.client.hud.HudUtils.*;
import static com.TNTStudios.tntacm.client.hud.ShipHudRenderer.Colors.*;

//region Crosshair & FPM Renderer
public class CrosshairRenderer {

    public record FlightPath(int x, int y, boolean offscreen) {}

    public static FlightPath render(DrawContext ctx, int w, int h, float ui, NebulaEntity ship, HudState state, long now) {
        final int cx = w / 2;
        final int cy = h / 2;

        float speedFactor = (float)Math.min(1.0, state.smoothedSpeed / 50.0);
        float damageFactor = 1f - (float)state.smoothHealth;

        int r1 = px(8 + speedFactor * 6, ui);
        int r2 = px(18 + speedFactor * 8, ui);
        int r3 = px(28 + speedFactor * 12, ui);

        int healthColor = (state.smoothHealth > 0.5f ? COLOR_GREEN : state.smoothHealth > 0.3f ? COLOR_YELLOW : COLOR_RED);
        drawPulsingCircle(ctx, cx, cy, r1, 48, adaptColor(healthColor), now, 1200);

        drawCircle(ctx, cx, cy, r2, 64, adaptColor(withAlpha(COLOR_CYAN, (int)(100 + 80 * speedFactor))));

        float spinSpeed = damageFactor > 0.3f ? 2000f : 3000f;
        float spin = (now % (long)spinSpeed) / spinSpeed * (float)(Math.PI * 2);
        int arcThickness = px(2 + damageFactor * 2, ui);

        drawEnhancedArc(ctx, cx, cy, r3, spin, spin + 0.8f, arcThickness, adaptColor(COLOR_CYAN_BRIGHT), now);
        drawEnhancedArc(ctx, cx, cy, r3, spin + 2.09f, spin + 2.89f, arcThickness, adaptColor(COLOR_CYAN_BRIGHT), now);
        drawEnhancedArc(ctx, cx, cy, r3, spin + 4.18f, spin + 4.98f, arcThickness, adaptColor(COLOR_CYAN_BRIGHT), now);

        int armLen = px(12 + speedFactor * 8, ui);
        int armThick = px(2 + speedFactor, ui);
        int bracketGap = px(4 + speedFactor * 2, ui);
        renderAdaptiveBrackets(ctx, cx, cy, armLen, armThick, bracketGap, adaptColor(healthColor), now);

        renderBoostEffects(ctx, cx, cy, ui, now);

        if (state.smoothAngularVel > 5.0) {
            renderAngularVelocityIndicator(ctx, cx, cy, r3 + px(8, ui), state.smoothAngularVel, ui, now);
        }

        return drawEnhancedFPM(ctx, cx, cy, w, h, ui, ship, state, now);
    }

    // Internal rendering methods...
    private static void drawPulsingCircle(DrawContext ctx, int cx, int cy, int radius, int segments, int color, long now, long period) {
        float pulse = 0.6f + 0.4f * (float)Math.sin(now * Math.PI * 2 / period);
        int pulseColor = withAlpha(color, (int)(((color >>> 24) & 0xFF) * pulse));
        drawCircle(ctx, cx, cy, radius, segments, pulseColor);
    }

    private static void drawEnhancedArc(DrawContext ctx, int cx, int cy, int radius, float a0, float a1, int thickness, int color, long now) {
        float flow = (now % 1500L) / 1500f;
        float segment = (a1 - a0) / 10f;
        for (int i = 0; i < 10; i++) {
            float s = a0 + i * segment;
            float e = s + segment;
            float intensity = 0.3f + 0.7f * (float)Math.sin(flow * Math.PI * 2 + i * 0.8f);
            int c = withAlpha(color, (int)(intensity * ((color >>> 24) & 0xFF)));
            drawArc(ctx, cx, cy, radius, s, e, thickness, c);
            if (ShipHudRenderer.Config.ENABLE_BLOOM_FAKE) drawArc(ctx, cx, cy, radius + 1, s, e, 1, withAlpha(c, 40));
        }
    }

    private static void renderAdaptiveBrackets(DrawContext ctx, int cx, int cy, int armLen, int armThick, int gap, int color, long now) {
        float pulse = 0.8f + 0.2f * (float)Math.sin(now / 300.0);
        int pulseColor = withAlpha(color, (int)(200 * pulse));
        thickLine(ctx, cx - armLen, cy, cx - gap, cy, armThick, pulseColor);
        thickLine(ctx, cx + gap, cy, cx + armLen, cy, armThick, pulseColor);
        thickLine(ctx, cx, cy - armLen, cx, cy - gap, armThick, pulseColor);
        thickLine(ctx, cx, cy + gap, cx, cy + armLen, armThick, pulseColor);
        drawEnergyDot(ctx, cx - armLen, cy, px(3, armThick), adaptColor(COLOR_CYAN_BRIGHT), now);
        drawEnergyDot(ctx, cx + armLen, cy, px(3, armThick), adaptColor(COLOR_CYAN_BRIGHT), now);
        drawEnergyDot(ctx, cx, cy - armLen, px(3, armThick), adaptColor(COLOR_CYAN_BRIGHT), now);
        drawEnergyDot(ctx, cx, cy + armLen, px(3, armThick), adaptColor(COLOR_CYAN_BRIGHT), now);
    }

    private static void drawEnergyDot(DrawContext ctx, int x, int y, int size, int color, long now) {
        float pulse = 0.5f + 0.5f * (float)Math.sin(now / 200.0);
        int pulseColor = withAlpha(color, (int)(255 * pulse));
        ctx.fill(x - size, y - size, x + size + 1, y + size + 1, pulseColor);
    }

    private static void renderBoostEffects(DrawContext ctx, int cx, int cy, float ui, long now) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.forwardKey.isPressed()) {
            int rings = ShipHudRenderer.Config.LOW_FX_MODE ? 2 : 3;
            for (int i = 0; i < rings; i++) {
                float off = (now % 800L) / 800f + i * 0.33f; off %= 1f;
                int radius = px(40 + off * 60, ui);
                float alpha = 1f - off;
                int color = adaptColor(withAlpha(COLOR_MAGENTA_BRIGHT, (int)(alpha * 120)));
                drawCircle(ctx, cx, cy, radius, 48, color);
            }
            if (ShipHudRenderer.Config.ENABLE_PARTICLES && now % 50 < 16) {
                int count = ShipHudRenderer.Config.LOW_FX_MODE ? 2 : 3;
                for (int i = 0; i < count; i++) {
                    double ang = Math.random() * Math.PI * 2;
                    double dist = 30 + Math.random() * 20;
                    ShipHudRenderer.addParticle(
                            cx + Math.cos(ang) * dist, cy + Math.sin(ang) * dist,
                            Math.cos(ang) * -100, Math.sin(ang) * -100,
                            COLOR_MAGENTA, 0.5f + (float)Math.random() * 0.3f, 2f
                    );
                }
            }
        }
        if (mc.options.backKey.isPressed()) {
            drawCircle(ctx, cx, cy, px(35, ui), 32, withAlpha(COLOR_RED, 80));
            float pulse = (float)Math.sin(now / 100.0);
            if (pulse > 0.7f) drawCircle(ctx, cx, cy, px(45, ui), 40, withAlpha(COLOR_RED_BRIGHT, 100));
        }
    }

    private static void renderAngularVelocityIndicator(DrawContext ctx, int cx, int cy, int radius, double angVel, float ui, long now) {
        int num = (int)Math.min(8, angVel / 10);
        float rot = (now % 1000L) / 1000f * (float)Math.PI * 2;
        for (int i = 0; i < num; i++) {
            float a = rot + i * (float)Math.PI * 2 / 8;
            int x = cx + (int)(Math.cos(a) * radius);
            int y = cy + (int)(Math.sin(a) * radius);
            drawCurvedArrow(ctx, x, y, a, px(6, ui), adaptColor(COLOR_YELLOW_BRIGHT));
        }
    }

    private static FlightPath drawEnhancedFPM(DrawContext ctx, int cx, int cy, int w, int h, float ui, NebulaEntity ship, HudState state, long now) {
        Vec3d v = ship.getVelocity();
        if (v.lengthSquared() < 1e-4) {
            drawEnergyDot(ctx, cx, cy, px(2, ui), adaptColor(COLOR_MAGENTA), now);
            return new FlightPath(cx, cy, false);
        }

        int maxOff = px(600, ui);
        int fmpX = cx + (int)(state.smoothVelX * maxOff);
        int fmpY = cy - (int)(state.smoothVelY * maxOff);

        int limR = px(Math.min(w, h) * 0.4f, ui);
        int dx = fmpX - cx, dy = fmpY - cy;
        double d = Math.sqrt(dx*dx + dy*dy);
        boolean offscreen = d > limR;

        if (offscreen) {
            double k = limR / d;
            fmpX = cx + (int)(dx * k);
            fmpY = cy + (int)(dy * k);
        }

        renderEnhancedFPMSymbol(ctx, fmpX, fmpY, ui, v.length(), now);
        drawEnhancedDirectionChevrons(ctx, cx, cy, fmpX, fmpY, ui, now);
        drawTrajectoryPrediction(ctx, cx, cy, fmpX, fmpY, ui, ship, now);
        if (offscreen) drawEnhancedPointer(ctx, cx, cy, fmpX, fmpY, px(12, ui), now);

        return new FlightPath(fmpX, fmpY, offscreen);
    }

    private static void renderEnhancedFPMSymbol(DrawContext ctx, int x, int y, float ui, double speed, long now) {
        float speedFactor = (float)Math.min(1.0, speed * 2);
        int coreRadius = px(5 + speedFactor * 4, ui);
        float pulse = 0.7f + 0.3f * (float)Math.sin(now / 250.0);
        drawCircle(ctx, x, y, coreRadius, 32, adaptColor(withAlpha(COLOR_VELOCITY_VECTOR, (int)(255 * pulse))));
        int outerRadius = px(8 + speedFactor * 6, ui);
        drawEnergyRing(ctx, x, y, outerRadius, adaptColor(COLOR_GREEN_BRIGHT), now);
        int wingLen = px(10 + speedFactor * 6, ui);
        int wingThick = px(2 + speedFactor, ui);
        thickLine(ctx, x - wingLen - coreRadius, y, x - coreRadius - px(2, ui), y, wingThick, adaptColor(COLOR_VELOCITY_VECTOR));
        thickLine(ctx, x + coreRadius + px(2, ui), y, x + wingLen + coreRadius, y, wingThick, adaptColor(COLOR_VELOCITY_VECTOR));
        drawSpeedTicks(ctx, x - wingLen - coreRadius, y, x - coreRadius - px(2, ui), y, speed, ui, now);
        drawSpeedTicks(ctx, x + coreRadius + px(2, ui), y, x + wingLen + coreRadius, y, speed, ui, now);
    }

    private static void drawEnergyRing(DrawContext ctx, int x, int y, int radius, int color, long now) {
        float rotation = (now % 2000L) / 2000f * (float)Math.PI * 2;
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            float angle = rotation + i * (float)Math.PI * 2 / segments;
            float intensity = 0.4f + 0.6f * (float)Math.sin(angle * 3);
            int segColor = withAlpha(color, (int)(intensity * 180));
            float segStart = angle - (float)Math.PI / segments * 0.4f;
            float segEnd   = angle + (float)Math.PI / segments * 0.4f;
            drawArc(ctx, x, y, radius, segStart, segEnd, px(2, 1), segColor);
        }
    }

    private static void drawSpeedTicks(DrawContext ctx, int x1, int y1, int x2, int y2, double speed, float ui, long now) {
        int numTicks = (int)Math.min(5, speed / 5);
        float flow = (now % 800L) / 800f;
        for (int i = 0; i < numTicks; i++) {
            float t = (flow + i * 0.2f) % 1f;
            int tickX = (int)(x1 + t * (x2 - x1));
            int tickY = (int)(y1 + t * (y2 - y1));
            int tickSize = px(3, ui);
            ctx.fill(tickX - tickSize/2, tickY - 1, tickX + tickSize/2 + 1, tickY + 2, withAlpha(COLOR_CYAN_BRIGHT, (int)(200 * (1f - t))));
        }
    }

    private static void drawEnhancedDirectionChevrons(DrawContext ctx, int x0, int y0, int x1, int y1, float ui, long now) {
        double ang = Math.atan2(y1 - y0, x1 - x0);
        double dist = Math.hypot(x1 - x0, y1 - y0);
        int step = px(25, ui);
        int len = px(12, ui);
        int thick = px(3, ui);
        double t = (now % 1000L) / 1000.0;
        double flow = t * t * step;
        for (double s = flow; s < dist - len - step; s += step) {
            int ax = x0 + (int)(Math.cos(ang) * s);
            int ay = y0 + (int)(Math.sin(ang) * s);
            int bx = x0 + (int)(Math.cos(ang) * (s + len));
            int by = y0 + (int)(Math.sin(ang) * (s + len));
            float intensity = 1f - (float)(s / dist);
            int color = withAlpha(COLOR_CYAN_BRIGHT, (int)(intensity * 220));
            thickLine(ctx, ax, ay, bx, by, thick, adaptColor(color));
            drawEnergyDot(ctx, bx, by, px(2, ui), COLOR_WHITE, now);
        }
    }

    private static void drawTrajectoryPrediction(DrawContext ctx, int cx, int cy, int fmpX, int fmpY, float ui, NebulaEntity ship, long now) {
        double dist = Math.hypot(fmpX - cx, fmpY - cy);
        if (dist < px(50, ui)) return;
        double ang = Math.atan2(fmpY - cy, fmpX - cx);
        int predictionLen = px((float) Math.min(200.0, dist * 2.0), ui);
        int dashLen = px(8, ui);
        int gapLen = px(6, ui);
        float fade = (now % 2000L) / 2000f;
        for (int d = 0; d < predictionLen; d += dashLen + gapLen) {
            int sx = fmpX + (int)(Math.cos(ang) * d);
            int sy = fmpY + (int)(Math.sin(ang) * d);
            int ex = fmpX + (int)(Math.cos(ang) * Math.min(d + dashLen, predictionLen));
            int ey = fmpY + (int)(Math.sin(ang) * Math.min(d + dashLen, predictionLen));
            float alpha = (1f - (float)d / predictionLen) * (0.6f + 0.4f * (float)Math.sin(fade * Math.PI * 2 + d * 0.1f));
            int color = adaptColor(withAlpha(COLOR_YELLOW, (int)(alpha * 150)));
            thickLine(ctx, sx, sy, ex, ey, px(2, ui), color);
        }
    }

    private static void drawEnhancedPointer(DrawContext ctx, int cx, int cy, int px_, int py_, int size, long now) {
        double angle = Math.atan2(py_ - cy, px_ - cx);
        double edgeDist = Math.hypot(px_ - cx, py_ - cy);
        int tipX = cx + (int)(Math.cos(angle) * (edgeDist + size));
        int tipY = cy + (int)(Math.sin(angle) * (edgeDist + size));
        float pulse = 0.7f + 0.3f * (float)Math.sin(now / 300.0);
        int pulseSize = (int)(size * pulse);
        drawTriangle(ctx, tipX, tipY, pulseSize, adaptColor(withAlpha(COLOR_VELOCITY_VECTOR, (int)(255 * pulse))), angle);
        for (int i = 1; i <= 5; i++) {
            int tx = tipX - (int)(Math.cos(angle) * i * px(8, 1f));
            int ty = tipY - (int)(Math.sin(angle) * i * px(8, 1f));
            float a = (6f - i) / 6f * 0.6f;
            drawEnergyDot(ctx, tx, ty, px(2, 1f), withAlpha(COLOR_VELOCITY_VECTOR, (int)(a * 255)), now);
        }
    }

    private static void drawCurvedArrow(DrawContext ctx, int x, int y, float angle, int size, int color) {
        int x1 = x + (int)(Math.cos(angle) * size);
        int y1 = y + (int)(Math.sin(angle) * size);
        int x2 = x + (int)(Math.cos(angle + 0.5f) * size * 0.7f);
        int y2 = y + (int)(Math.sin(angle + 0.5f) * size * 0.7f);
        thickLine(ctx, x, y, x1, y1, px(2, 1f), color);
        drawTriangle(ctx, x1, y1, px(4, 1f), color, angle);
        thickLine(ctx, x1, y1, x2, y2, px(2, 1f), withAlpha(color, 150));
    }
}
//endregion
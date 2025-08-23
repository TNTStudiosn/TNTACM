package com.TNTStudios.tntacm.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

//region Drawing & Math Utilities
public final class HudUtils {

    //region Configuration Accessor
    public static boolean isColorblindMode() { return ShipHudRenderer.Config.COLORBLIND_MODE; }
    //endregion

    //region Layout Helpers
    public static float uiScale(int w, int h) {
        float base = Math.min(w, h) / 720f;
        return MathHelper.clamp(base, 0.9f, 2.8f);
    }

    public static int px(float value, float ui) { return Math.max(1, Math.round(value * ui)); }
    //endregion

    //region Math Helpers
    public static double lerpExp(double from, double to, float speed, float dt) {
        final double k = 1.0 - Math.pow(1.0 - MathHelper.clamp(speed, 0f, 1f), Math.max(1.0, dt * 60.0));
        return MathHelper.lerp((float)k, (float)from, (float)to);
    }

    public static float angleDiff(float a, float b) {
        float d = (float)Math.atan2(Math.sin(a - b), Math.cos(a - b));
        return Math.abs(d);
    }
    //endregion

    //region Color Helpers
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((MathHelper.clamp(alpha, 0, 255) & 0xFF) << 24);
    }

    public static int adaptColor(int argb) {
        if (!isColorblindMode()) return argb;
        final int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        if (r > g && r > b) { r = (int)(r * 0.75); b = Math.min(255, (int)(b * 1.25)); }
        if (g > r && g > b) { b = Math.min(255, (int)(b * 1.15)); }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    //endregion

    //region Drawing Primitives
    public static void drawCircle(DrawContext ctx, int cx, int cy, int radius, int segments, int argb) {
        if (radius <= 0) return;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        int a = (argb >>> 24) & 0xFF; int r = (argb >>> 16) & 0xFF; int g = (argb >>> 8) & 0xFF; int bl = argb & 0xFF;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        b.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float ang = (float)(i * (Math.PI * 2) / segments);
            b.vertex(m, cx + MathHelper.sin(ang) * radius, cy + MathHelper.cos(ang) * radius, 0).color(r, g, bl, a).next();
        }
        t.draw();
    }

    public static void drawArc(DrawContext ctx, int cx, int cy, int radius, float a0, float a1, int thickness, int color) {
        for (int t = 0; t < Math.max(1, thickness); t++) drawArcThin(ctx, cx, cy, radius + t, a0, a1, color);
    }

    private static void drawArcThin(DrawContext ctx, int cx, int cy, int radius, float a0, float a1, int color) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        int a = (color >>> 24) & 0xFF; int r = (color >>> 16) & 0xFF; int g = (color >>> 8) & 0xFF; int bl = color & 0xFF;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        b.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);
        int segs = Math.max(6, (int)(Math.abs(a1 - a0) * 48));
        for (int i = 0; i <= segs; i++) {
            float ang = a0 + (a1 - a0) * (i / (float)segs);
            b.vertex(m, cx + MathHelper.sin(ang) * radius, cy + MathHelper.cos(ang) * radius, 0).color(r, g, bl, a).next();
        }
        t.draw();
    }

    public static void drawRectOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawRadialLine(DrawContext ctx, int cx, int cy, int radius, float angleRad, int argb) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        int a = (argb >>> 24) & 0xFF; int r = (argb >>> 16) & 0xFF; int g = (argb >>> 8) & 0xFF; int bl = argb & 0xFF;
        float x2 = cx + MathHelper.sin(angleRad) * radius;
        float y2 = cy - MathHelper.cos(angleRad) * radius; // Corrected for standard angle representation
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        b.begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);
        b.vertex(m, cx, cy, 0).color(r, g, bl, a).next();
        b.vertex(m, x2, y2, 0).color(r, g, bl, a).next();
        t.draw();
    }

    public static void drawBlip(DrawContext ctx, int x, int y, int size, int argb) {
        int half = Math.max(1, size / 2);
        ctx.fill(x - half, y - half, x + half + 1, y + half + 1, argb);
    }

    public static void thickLine(DrawContext ctx, int x1, int y1, int x2, int y2, int thickness, int color) {
        if (thickness <= 1) {
            Matrix4f positionMatrix = ctx.getMatrices().peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            // CORRECTION: Use getPositionColorProgram for drawing simple colored lines.
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(positionMatrix, (float)x1, (float)y1, 0.0F).color(color).next();
            buffer.vertex(positionMatrix, (float)x2, (float)y2, 0.0F).color(color).next();
            tessellator.draw();
            return;
        }

        double angle = Math.atan2(y2 - y1, x2 - x1);
        double offsetX = Math.sin(angle) * thickness / 2.0;
        double offsetY = -Math.cos(angle) * thickness / 2.0;

        float[] x = new float[] {(float)(x1 - offsetX), (float)(x1 + offsetX), (float)(x2 + offsetX), (float)(x2 - offsetX)};
        float[] y = new float[] {(float)(y1 - offsetY), (float)(y1 + offsetY), (float)(y2 + offsetY), (float)(y2 - offsetY)};

        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        b.vertex(m, x[0], y[0], 0).color(color).next();
        b.vertex(m, x[1], y[1], 0).color(color).next();
        b.vertex(m, x[2], y[2], 0).color(color).next();
        b.vertex(m, x[3], y[3], 0).color(color).next();
        t.draw();
    }

    public static void drawTriangleUp(DrawContext ctx, int x, int y, int size, int argb) {
        drawTriangle(ctx, x, y, size, argb, -Math.PI / 2);
    }

    public static void drawTriangle(DrawContext ctx, int x, int y, int s, int argb, double directionRad) {
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        int a = (argb >>> 24) & 0xFF; int r = (argb >>> 16) & 0xFF; int g = (argb >>> 8) & 0xFF; int bl = argb & 0xFF;
        double ang0 = directionRad;
        double ang1 = ang0 + 2*Math.PI/3;
        double ang2 = ang0 + 4*Math.PI/3;
        int x1 = x + (int)(Math.cos(ang0) * s); int y1 = y + (int)(Math.sin(ang0) * s);
        int x2 = x + (int)(Math.cos(ang1) * s); int y2 = y + (int)(Math.sin(ang1) * s);
        int x3 = x + (int)(Math.cos(ang2) * s); int y3 = y + (int)(Math.sin(ang2) * s);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        b.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        b.vertex(m, x1, y1, 0).color(r, g, bl, a).next();
        b.vertex(m, x2, y2, 0).color(r, g, bl, a).next();
        b.vertex(m, x3, y3, 0).color(r, g, bl, a).next();
        t.draw();
        RenderSystem.disableBlend();
    }
    //endregion
}
//endregion
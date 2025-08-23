package com.TNTStudios.tntacm.client.hud;

import com.TNTStudios.tntacm.entity.custom.NebulaEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

import static com.TNTStudios.tntacm.client.hud.HudUtils.*;
import static com.TNTStudios.tntacm.client.hud.ShipHudRenderer.Colors.*;

public class ShipHudRenderer {

    //region Configuration
    public static final class Config {
        public static boolean ENABLE_PARTICLES   = true;
        public static boolean ENABLE_SWEEP_TRAIL = true;
        public static boolean ENABLE_BLOOM_FAKE  = true;
        public static boolean COLORBLIND_MODE    = false;
        public static boolean LOW_FX_MODE        = false;
        public static int MAX_PARTICLES() { return LOW_FX_MODE ? 120 : 220; }
    }
    //endregion

    //region Color Palette
    public static final class Colors {
        public static final int COLOR_WHITE = 0xFFFFFFFF, COLOR_TEXT_DIM = 0xFFD0D0D0, COLOR_TEXT_BRIGHT = 0xFFF8F8F8;
        public static final int COLOR_GREEN = 0xFF2DFF7A, COLOR_GREEN_BRIGHT = 0xFF4AFFAA;
        public static final int COLOR_YELLOW = 0xFFFBD14A, COLOR_YELLOW_BRIGHT = 0xFFFFE066;
        public static final int COLOR_RED = 0xFFFF4D5A, COLOR_RED_BRIGHT = 0xFFFF7788;
        public static final int COLOR_CYAN = 0xFF00D1FF, COLOR_CYAN_BRIGHT = 0xFF33EEFF, COLOR_CYAN_SOFT = 0x8000D1FF;
        public static final int COLOR_MAGENTA = 0xFFB15CFF, COLOR_MAGENTA_BRIGHT = 0xFFD088FF;
        public static final int COLOR_ORANGE = 0xFFFF8A00;
        public static final int COLOR_BG_HUD = 0x500A0A0A, COLOR_BG_CARD = 0x60000000, COLOR_BG_CARD_BRIGHT = 0x80000000;
        public static final int COLOR_PANEL_BORDER = 0x80FFFFFF;
        public static final int COLOR_VELOCITY_VECTOR = 0xE000FF7A;
        public static final int COLOR_WARNING_FLASH = 0xFFFF3333;
        public static final int COLOR_ENERGY_CORE = 0xFF00FFDD;
    }
    //endregion

    //region State & Particles
    private static final HudState hudState = new HudState();
    private static long lastTickMs = 0L;

    private static class HudParticle {
        double x, y, vx, vy; int color; float life, maxLife, size;
        HudParticle(double x, double y, double vx, double vy, int color, float life, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = color;
            this.life = this.maxLife = life; this.size = size;
        }
        boolean update(float dt) { x += vx*dt; y += vy*dt; life -= dt; return life > 0; }
        void render(DrawContext ctx, float ui) {
            float alpha = life / maxLife;
            int col = withAlpha(color, (int)(alpha * ((color >>> 24) & 0xFF)));
            int s = (int)(size * ui * alpha);
            ctx.fill((int)x - s/2, (int)y - s/2, (int)x + s/2 + 1, (int)y + s/2 + 1, col);
        }
    }
    private static final List<HudParticle> particles = new ArrayList<>();

    public static void addParticle(double x, double y, double vx, double vy, int color, float life, float size) {
        if (!Config.ENABLE_PARTICLES) return;
        particles.add(new HudParticle(x, y, vx, vy, color, life, size));
    }
    //endregion

    //region Main Render Loop
    public static void render(DrawContext ctx, float tickDelta) {
        final MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !mc.player.hasVehicle()) return;

        final Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof NebulaEntity ship)) return;

        final int w = ctx.getScaledWindowWidth();
        final int h = ctx.getScaledWindowHeight();
        final float ui = uiScale(w, h);
        final long now = System.currentTimeMillis();
        final float dt = (lastTickMs == 0L) ? 0f : Math.min(0.06f, (now - lastTickMs) / 1000f);
        lastTickMs = now;

        hudState.update(ship, tickDelta, dt, now);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        final boolean criticalDamage = hudState.smoothHealth <= 0.15f;
        if (criticalDamage) renderCriticalDamageEffects(ctx, w, h, ui, now);

        renderEnhancedFrame(ctx, w, h, ui, now);

        CrosshairRenderer.render(ctx, w, h, ui, ship, hudState, now);
        // RadarRenderer.render(ctx, w, h, ui, ship, tickDelta, hudState, now); // Placeholder for brevity
        // VitalsRenderer.render(ctx, w, h, ui, ship, hudState, now); // Placeholder
        // SideGaugesRenderer.render(ctx, h, ui, mc, hudState, now); // Placeholder
        // InfoPanelRenderer.render(ctx, w, h, ui, ship, hudState, now); // Placeholder
        // NavigationRenderer.render(ctx, w, h, ui, ship, now); // Placeholder

        updateAndRenderParticles(ctx, ui, dt);

        RenderSystem.disableBlend();
    }
    //endregion

    //region Global Effects
    private static void renderEnhancedFrame(DrawContext ctx, int w, int h, float ui, long now) {
        int thick = px(4, ui);
        float pulse = 0.6f + 0.4f * (float)Math.sin(now / 800.0);
        int pulseColor = adaptColor(withAlpha(COLOR_CYAN, (int)(120 * pulse)));
        ctx.fill(0, 0, w, thick, COLOR_BG_HUD);
        ctx.fill(0, h - thick, w, h, COLOR_BG_HUD);
        ctx.fill(0, 0, thick, h, COLOR_BG_HUD);
        ctx.fill(w - thick, 0, w, h, COLOR_BG_HUD);
        int cornerLen = px(180, ui);
        int cornerThick = px(2, ui);
        int margin = px(12, ui);
        renderEnergyCorner(ctx, margin, margin, cornerLen, cornerThick, 0, pulseColor, now);
        renderEnergyCorner(ctx, w - margin - cornerLen, margin, cornerLen, cornerThick, 1, pulseColor, now);
        renderEnergyCorner(ctx, margin, h - margin - cornerThick, cornerLen, cornerThick, 2, pulseColor, now);
        renderEnergyCorner(ctx, w - margin - cornerLen, h - margin - cornerThick, cornerLen, cornerThick, 3, pulseColor, now);
        renderScanLines(ctx, w, h, ui, now);
    }

    private static void renderEnergyCorner(DrawContext ctx, int x, int y, int len, int thick, int corner, int color, long now) {
        float flow = (now % 2000L) / 2000f;
        int flowLen = len / 4;
        int flowPos = (int)(flow * (len - flowLen));
        int brightColor = adaptColor(withAlpha(COLOR_CYAN_BRIGHT, 200));
        switch (corner) {
            case 0 -> { ctx.fill(x, y, x + len, y + thick, color); ctx.fill(x, y, x + thick, y + len / 3, color); ctx.fill(x + flowPos, y, x + flowPos + flowLen, y + thick, brightColor); }
            case 1 -> { ctx.fill(x, y, x + len, y + thick, color); ctx.fill(x + len - thick, y, x + len, y + len / 3, color); ctx.fill(x + len - flowPos - flowLen, y, x + len - flowPos, y + thick, brightColor); }
            case 2 -> { ctx.fill(x, y, x + len, y + thick, color); ctx.fill(x, y - len / 3 + thick, x + thick, y + thick, color); ctx.fill(x + flowPos, y, x + flowPos + flowLen, y + thick, brightColor); }
            case 3 -> { ctx.fill(x, y, x + len, y + thick, color); ctx.fill(x + len - thick, y - len / 3 + thick, x + len, y + thick, color); ctx.fill(x + len - flowPos - flowLen, y, x + len - flowPos, y + thick, brightColor); }
        }
    }

    private static void renderScanLines(DrawContext ctx, int w, int h, float ui, long now) {
        float scanPos = ((now % 4000L) / 4000f);
        int scanY = (int)(scanPos * h);
        int scanHeight = px(3, ui);
        int scanColor = adaptColor(withAlpha(COLOR_CYAN, 60));
        ctx.fill(0, scanY, w, scanY + scanHeight, scanColor);
        for (int i = 1; i <= 3; i++) {
            int secondaryY = (scanY - px(20 * i, ui) + h) % h;
            int secondaryColor = adaptColor(withAlpha(COLOR_CYAN, 20 / i));
            ctx.fill(0, secondaryY, w, secondaryY + px(1, ui), secondaryColor);
        }
    }

    private static void renderCriticalDamageEffects(DrawContext ctx, int w, int h, float ui, long now) {
        if ((now / 100) % 7 == 0) {
            for (int i = 0; i < 8; i++) {
                int lineY = (int)(Math.random() * h);
                int lineW = px(2 + (int)(Math.random() * 4), ui);
                int alpha = 60 + (int)(Math.random() * 120);
                ctx.fill(0, lineY, w, lineY + lineW, withAlpha(COLOR_RED, alpha));
            }
        }
        if ((now / 150) % 3 == 0) {
            int borderSize = px(2, ui);
            int c = withAlpha(COLOR_WARNING_FLASH, 150);
            ctx.fill(0, 0, w, borderSize, c); ctx.fill(0, h - borderSize, w, h, c);
            ctx.fill(0, 0, borderSize, h, c); ctx.fill(w - borderSize, 0, w, h, c);
        }
        if (Config.ENABLE_PARTICLES && (now % 200) < 50) {
            for (int i = 0; i < 3; i++) {
                addParticle(
                        Math.random() * w, Math.random() * h,
                        (Math.random() - 0.5) * 200, (Math.random() - 0.5) * 200,
                        COLOR_WARNING_FLASH, 0.3f + (float)Math.random() * 0.2f, 1.5f
                );
            }
        }
    }
    //endregion

    //region Particle Management
    private static void updateAndRenderParticles(DrawContext ctx, float ui, float dt) {
        if (!Config.ENABLE_PARTICLES) return;
        particles.removeIf(p -> !p.update(dt));
        for (HudParticle p : particles) {
            p.render(ctx, ui);
        }
        int cap = Config.MAX_PARTICLES();
        if (particles.size() > cap) {
            particles.subList(0, particles.size() - cap).clear();
        }
    }
    //endregion
}
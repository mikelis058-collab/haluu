package dev.halo;

import dev.halo.module.ModuleManager;
import dev.halo.module.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Glass versions of the vanilla hotbar, health/food/armor/air, XP and crosshair. */
public final class HudStyle {
    private HudStyle() {}

    private interface Draw { void run(DrawContext ctx); }

    private static float selX = -1f, hp, hpLag, fd, fdLag, armor, air, airShown, gap = 3f;

    public static void register() {
        replace(VanillaHudElements.HOTBAR, HudStyle::hotbar, true);
        replace(VanillaHudElements.HEALTH_BAR, null, false);
        replace(VanillaHudElements.FOOD_BAR, null, false);
        replace(VanillaHudElements.ARMOR_BAR, null, false);
        replace(VanillaHudElements.AIR_BAR, null, false);
        replace(VanillaHudElements.EXPERIENCE_LEVEL, null, false);
        replace(VanillaHudElements.INFO_BAR, null, false);

        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (ctx, tick) -> {
            var c = ModuleManager.CROSSHAIRS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (c != null && c.enabled && mc.player != null) crosshair(ctx);
            else original.render(ctx, tick);
        });
    }

    private static void replace(Identifier id, Draw draw, boolean hotbar) {
        HudElementRegistry.replaceElement(id, original -> (ctx, tick) -> {
            if (active()) { if (draw != null) draw.run(ctx); }
            else original.render(ctx, tick);
        });
    }

    private static boolean active() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var t = ModuleManager.HUDTHEME;
        return t != null && t.enabled && mc.player != null && !mc.player.isSpectator();
    }

    // ------------------------------------------------------------------ hotbar
    private static void hotbar(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        float dt = ModuleManager.dt;
        int acc = Gfx.accent();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        int w = 188, h = 24, px = (sw - w) / 2, py = sh - 28;

        Gfx.glass(ctx, px, py, w, h, 7, 1f);

        int sel = p.getInventory().getSelectedSlot();
        selX = selX < 0 ? sel : Anim.approach(selX, sel, 22f, dt);
        int hx = px + 2 + Math.round(selX * 20f);
        Gfx.rrect(ctx, hx, py + 2, 20, 20, 5, Gfx.alpha(0xFFFFFF, 0.17f));
        Gfx.rrect(ctx, hx + 4, py + h - 4, 12, 2, 1, Gfx.alpha(acc, 1f));

        for (int i = 0; i < 9; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isEmpty()) continue;
            int ix = px + 4 + i * 20, iy = py + 4;
            ctx.drawItem(st, ix, iy);
            ctx.drawStackOverlay(mc.textRenderer, st, ix, iy);
        }

        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty()) {
            Gfx.glass(ctx, px - 27, py + 1, 22, 22, 6, 1f);
            ctx.drawItem(off, px - 24, py + 4);
            ctx.drawStackOverlay(mc.textRenderer, off, px - 24, py + 4);
        }

        if (mc.interactionManager == null || !mc.interactionManager.hasStatusBars()) return;

        // xp: thin line under the panel + level above it
        float xp = Math.max(0f, Math.min(1f, p.experienceProgress));
        Gfx.rrect(ctx, px + 10, py + h + 1, w - 20, 2, 1, Gfx.alpha(0xFFFFFF, 0.12f));
        if (xp > 0.01f) Gfx.rrect(ctx, px + 10, py + h + 1, Math.max(2, Math.round((w - 20) * xp)), 2, 1, Gfx.alpha(acc, 0.95f));
        if (p.experienceLevel > 0) Gfx.centered(ctx, String.valueOf(p.experienceLevel), sw / 2, py - 11, Gfx.alpha(0xFFFFFF, 1f));

        if (!ModuleManager.HUDTHEME.stats.value) return;

        float maxHp = Math.max(1f, p.getMaxHealth());
        hp = Anim.approach(hp, p.getHealth() / maxHp, 14f, dt);
        hpLag = hpLag > hp ? Anim.approach(hpLag, hp, 2.5f, dt) : hp;
        float food = p.getHungerManager().getFoodLevel() / 20f;
        fd = Anim.approach(fd, food, 14f, dt);
        fdLag = fdLag > fd ? Anim.approach(fdLag, fd, 2.5f, dt) : fd;

        boolean low = p.getHealth() <= 6f;
        float pulse = low ? 0.62f + 0.38f * (float) Math.sin(System.nanoTime() / 1.6e8) : 1f;
        float abs = p.getAbsorptionAmount();
        String hpText = Math.round(p.getHealth()) + (abs > 0.5f ? "+" + Math.round(abs) : "");

        statPill(ctx, px, py - 19, hpText, hp, hpLag, pulse);
        statPill(ctx, px + w - 92, py - 19, String.valueOf(p.getHungerManager().getFoodLevel()), fd, fdLag, 1f);

        float a = p.getArmor() / 20f;
        armor = Anim.approach(armor, a, 12f, dt);
        if (p.getArmor() > 0) statPill(ctx, px, py - 36, String.valueOf(p.getArmor()), armor, armor, 0.75f);

        // air bar, only while it matters
        boolean needAir = p.getAir() < p.getMaxAir();
        airShown = Anim.approach(airShown, needAir ? 1f : 0f, 12f, dt);
        if (airShown > 0.02f) {
            air = Anim.approach(air, Math.max(0f, p.getAir() / (float) p.getMaxAir()), 14f, dt);
            int aw = 92, ax = sw / 2 - aw / 2, ay = py - 36 - Math.round((1f - airShown) * 6f);
            Gfx.glass(ctx, ax, ay, aw, 15, 5, airShown);
            Gfx.text(ctx, "AIR", ax + 7, ay + 4, Gfx.alpha(0x8A8A8A, airShown));
            int bx = ax + 34, bw = aw - 34 - 7;
            Gfx.rrect(ctx, bx, ay + 5, bw, 5, 2, Gfx.alpha(0xFFFFFF, 0.12f * airShown));
            if (air > 0.01f) Gfx.rrect(ctx, bx, ay + 5, Math.max(2, Math.round(bw * air)), 5, 2, Gfx.alpha(0xFFFFFF, airShown));
        }
    }

    private static void statPill(DrawContext ctx, int x, int y, String text, float ratio, float lag, float alpha) {
        int w = 92;
        Gfx.glass(ctx, x, y, w, 15, 5, 1f);
        Gfx.text(ctx, text, x + 7, y + 4, Gfx.alpha(0xFFFFFF, 1f));
        int bx = x + 34, bw = w - 34 - 7;
        Gfx.rrect(ctx, bx, y + 5, bw, 5, 2, Gfx.alpha(0xFFFFFF, 0.12f));
        if (lag > ratio + 0.01f) Gfx.rrect(ctx, bx, y + 5, Math.max(2, Math.round(bw * lag)), 5, 2, Gfx.alpha(0x8A8A8A, 0.75f));
        if (ratio > 0.01f) Gfx.rrect(ctx, bx, y + 5, Math.max(2, Math.round(bw * Math.min(1f, ratio))), 5, 2, Gfx.alpha(0xFFFFFF, alpha));
    }

    // --------------------------------------------------------------- crosshair
    private static void crosshair(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !mc.options.getPerspective().isFirstPerson()) return;
        var t = ModuleManager.CROSSHAIRS;
        int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() / 2;

        Vec3d v = mc.player.getVelocity();
        float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
        boolean onTarget = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY;
        float target = t.gap.f() + (t.dynamic.value ? Math.min(5f, speed * 18f) : 0f) + (onTarget ? 1.5f : 0f);
        gap = Anim.approach(gap, target, 16f, ModuleManager.dt);

        int g = Math.round(gap), len = t.size.i(), th = t.thickness.i();
        int accent = t.color.rgb;
        int main = Gfx.alpha(accent, onTarget ? 1f : 0.88f), edge = Gfx.alpha(0x000000, 0.55f);
        int mode = t.style.index;

        // 0 Dot, 1 Cross, 2 Cross+Dot, 3 Ring, 4 Diamond, 5 Chevron, 6 Four Corners, 7 Triangle
        if (mode == 1 || mode == 2) {
            armT(ctx, cx - g - len, cy, cx - g, cy + th, main, edge);
            armT(ctx, cx + th + g, cy, cx + th + g + len, cy + th, main, edge);
            armT(ctx, cx, cy - g - len, cx + th, cy - g, main, edge);
            armT(ctx, cx, cy + th + g, cx + th, cy + th + g + len, main, edge);
        }
        if (mode == 0 || mode == 2) {
            ctx.fill(cx - th, cy - th, cx + th + 1, cy + th + 1, edge);
            ctx.fill(cx - th + 1, cy - th + 1, cx + th, cy + th, main);
        }
        if (mode == 3) { // ring
            int r = g + len;
            for (int i = 0; i < 360; i += 10) {
                double rad = Math.toRadians(i);
                int px = cx + (int) (Math.cos(rad) * r), py = cy + (int) (Math.sin(rad) * r);
                ctx.fill(px - th, py - th, px + th, py + th, main);
            }
        }
        if (mode == 4) { // diamond
            armT(ctx, cx, cy - g - len, cx - th, cy - g, main, edge);
            armT(ctx, cx, cy - g - len, cx + th, cy - g, main, edge);
            armT(ctx, cx, cy + g + len, cx - th, cy + g, main, edge);
            armT(ctx, cx, cy + g + len, cx + th, cy + g, main, edge);
        }
        if (mode == 5) { // chevron (pointing down)
            for (int i = 0; i <= len; i++) {
                int yy = cy + g + i;
                ctx.fill(cx - i, yy, cx - i + th, yy + th, main);
                ctx.fill(cx + i, yy, cx + i + th, yy + th, main);
            }
        }
        if (mode == 6) { // four corners
            int r = g + len;
            corner(ctx, cx - r, cy - r, len, th, 1, 1, main);
            corner(ctx, cx + r, cy - r, len, th, -1, 1, main);
            corner(ctx, cx - r, cy + r, len, th, 1, -1, main);
            corner(ctx, cx + r, cy + r, len, th, -1, -1, main);
        }
        if (mode == 7) { // triangle, pointing down
            int r = g + len;
            for (int i = 0; i <= len; i++) {
                int yy = cy - r + i;
                ctx.fill(cx - i, yy, cx - i + th, yy + th, main);
                ctx.fill(cx + i - th, yy, cx + i, yy + th, main);
            }
        }

        if (t.cooldown.value) {
            float prog = mc.player.getAttackCooldownProgress(0f);
            if (prog < 0.999f) {
                int barW = 20, barH = 2, bx = cx - barW / 2, by = cy + g + len + 6;
                Gfx.rrect(ctx, bx, by, barW, barH, 1, Gfx.alpha(0x000000, 0.5f));
                Gfx.rrect(ctx, bx, by, Math.max(1, Math.round(barW * prog)), barH, 1, Gfx.alpha(accent, 0.9f));
            }
        }
    }

    private static void corner(DrawContext ctx, int x, int y, int len, int th, int dx, int dy, int col) {
        ctx.fill(x, y, x + dx * len, y + th * dy, col);
        ctx.fill(x, y, x + th * dx, y + dy * len, col);
    }

    private static void armT(DrawContext ctx, int x1, int y1, int x2, int y2, int main, int edge) {
        ctx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, edge);
        ctx.fill(x1, y1, x2, y2, main);
    }
}

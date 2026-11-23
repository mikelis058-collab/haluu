package dev.halo.module;

import dev.halo.Anim;
import dev.halo.gui.ClickGui;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private ModuleManager() {}

    public static final List<Module> ALL = new ArrayList<>();
    public static Modules.Interface INTERFACE;
    public static Modules.Zoom ZOOM;
    public static Modules.Fps FPS;
    public static Modules.CpsDisplay CPS;
    public static Modules.Coords COORDS;
    public static Modules.Keystrokes KEYSTROKES;
    public static Modules.Crosshairs CROSSHAIRS;
    public static Modules.Fog FOG;
    public static Modules.Mist MIST;
    public static Modules.Xray XRAY;
    public static Modules.Hats HATS;
    public static Modules.Wings WINGS;
    public static Modules.Auras AURAS;
    public static Modules.Knives KNIVES;
    public static Modules.HudTheme HUDTHEME;
    public static Modules.ShieldStatus SHIELD;
    public static Modules.Atmosphere ATMOS;
    public static Modules.Fire FIRE;
    public static Modules.Drugs DRUGS;
    public static Modules.BlockOutliner OUTLINER;
    public static Modules.Impact IMPACT;
    public static Modules.GhostBlock GHOSTBLOCK;
    public static Modules.HitSfx HITSFX;
    public static Modules.BoatFly BOATFLY;
    public static Modules.FakePotion FAKEPOTION;

    public static float dt = 0.016f;
    private static long last = System.nanoTime();
    private static boolean menuDown;

    private static <T extends Module> T reg(T m) { ALL.add(m); return m; }

    public static void init() {
        INTERFACE = reg(new Modules.Interface());
        HUDTHEME = reg(new Modules.HudTheme());
        CROSSHAIRS = reg(new Modules.Crosshairs());
        reg(new Modules.TriggerBot());
        reg(new Modules.AimAssist());
        HITSFX = reg(new Modules.HitSfx());
        SHIELD = reg(new Modules.ShieldStatus());
        XRAY = reg(new Modules.Xray());
        FOG = reg(new Modules.Fog());
        MIST = reg(new Modules.Mist());
        ATMOS = reg(new Modules.Atmosphere());
        reg(new Modules.Fullbright());
        ZOOM = reg(new Modules.Zoom());
        KNIVES = reg(new Modules.Knives());
        HATS = reg(new Modules.Hats());
        WINGS = reg(new Modules.Wings());
        AURAS = reg(new Modules.Auras());
        FIRE = reg(new Modules.Fire());
        DRUGS = reg(new Modules.Drugs());
        OUTLINER = reg(new Modules.BlockOutliner());
        IMPACT = reg(new Modules.Impact());
        GHOSTBLOCK = reg(new Modules.GhostBlock());
        CPS = reg(new Modules.CpsDisplay());
        FPS = reg(new Modules.Fps());
        COORDS = reg(new Modules.Coords());
        KEYSTROKES = reg(new Modules.Keystrokes());
        reg(new Modules.ToggleSprint());
        BOATFLY = reg(new Modules.BoatFly());
        FAKEPOTION = reg(new Modules.FakePotion());
    }

    public static List<Module> in(Module.Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }

    /** Client tick: keybinds + module ticks. */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        long h = mc.getWindow().getHandle();

        boolean md = mc.player != null && GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (md && !menuDown && mc.currentScreen == null) mc.setScreen(new ClickGui());
        menuDown = md;

        for (Module m : ALL) {
            if (m.key > 0) {
                if (mc.currentScreen != null) {
                    if (m.keyDown && m.holdMode.value) m.setEnabled(false);
                    m.keyDown = false;
                } else {
                    boolean down = GLFW.glfwGetKey(h, m.key) == GLFW.GLFW_PRESS;
                    if (down != m.keyDown) {
                        // BoatFly needs an explicit one-time warning before its keybind can turn it on.
                        boolean blocked = (m == BOATFLY && !BOATFLY.confirmedThisSession && down && !m.enabled);
                        if (!blocked) {
                            if (m.holdMode.value) m.setEnabled(down);
                            else if (down) m.toggle();
                        }
                        m.keyDown = down;
                    }
                }
            }
            if (m.enabled && mc.player != null) m.onTick();
        }
        Modules.Knives.sync();
    }

    /** Called every rendered frame (deduplicated, so calling it from several places is fine). */
    public static void frame() {
        long now = System.nanoTime();
        float d = (now - last) / 1_000_000_000f;
        if (d < 0.002f) return;
        last = now;
        dt = Math.min(d, 0.1f);

        MinecraftClient mc = MinecraftClient.getInstance();
        Modules.ClickTracker.update();
        for (Module m : ALL) {
            m.anim = Anim.approach(m.anim, m.enabled ? 1f : 0f, 12f, dt);
            m.update(dt);
            if (m.enabled && mc.player != null && mc.world != null) m.onFrame(dt);
        }
    }
}

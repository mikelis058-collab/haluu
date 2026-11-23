package dev.halo.module;

import dev.halo.Gfx;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Modules {
    private Modules() {}
    private static final Random RNG = new Random();
    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }

    // =====================================================================
    //  Shared helpers
    // =====================================================================
    /** Left / right click counter used by CPS + Keystrokes. */
    public static final class ClickTracker {
        private static final ArrayDeque<Long> L = new ArrayDeque<>(), R = new ArrayDeque<>();
        private static boolean pl, pr;
        public static boolean leftDown, rightDown;

        public static void update() {
            MinecraftClient mc = mc();
            if (mc.getWindow() == null) return;
            long h = mc.getWindow().getHandle();
            boolean l = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean r = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            long now = System.currentTimeMillis();
            if (mc.currentScreen == null) {
                if (l && !pl) L.add(now);
                if (r && !pr) R.add(now);
                leftDown = l; rightDown = r;
            } else { leftDown = false; rightDown = false; }
            pl = l; pr = r;
            while (!L.isEmpty() && now - L.peekFirst() > 1000) L.pollFirst();
            while (!R.isEmpty() && now - R.peekFirst() > 1000) R.pollFirst();
        }
        public static int left() { return L.size(); }
        public static int right() { return R.size(); }
    }

    static boolean validTarget(Entity e, boolean players, boolean hostile, boolean passive) {
        MinecraftClient mc = mc();
        if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || e instanceof ArmorStandEntity || le.isSpectator()) return false;
        if (e instanceof PlayerEntity) return players;
        if (e instanceof Monster) return hostile;
        if (e instanceof PassiveEntity) return passive;
        return false;
    }

    // =====================================================================
    //  HUD / Interface
    // =====================================================================
    public static class Interface extends Module {
        public final Bool moduleList = bool("Module List", true);
        public final Color accent = color("Accent Color", 0xFFFFFF);
        public final Num opacity = num("Panel Opacity", 30, 100, 80, 1);
        public final Num listScale = num("Module List Size", 60, 140, 100, 5);
        public Interface() {
            super("Interface", "The glass HUD and accent theme.", Category.HUD);
            enabled = true; anim = 1f; showInList.value = false;
        }
    }

    public static class Fps extends Module {
        public final Bool label = bool("Show Label", true);
        public Fps() { super("FPS", "Shows your frame rate.", Category.HUD); }
    }

    public static class CpsDisplay extends Module {
        public final Bool right = bool("Show Right Click", true);
        public CpsDisplay() { super("CPS", "Clicks per second.", Category.HUD); }
    }

    public static class Coords extends Module {
        public final Bool direction = bool("Show Direction", true);
        public final Num decimals = num("Decimals", 0, 2, 0, 1);
        public Coords() { super("Coordinates", "Shows your position.", Category.HUD); }
    }

    public static class Keystrokes extends Module {
        public final Bool mouse = bool("Show Mouse", true);
        public final Bool space = bool("Show Space", true);
        public Keystrokes() { super("Keystrokes", "WASD and mouse overlay.", Category.HUD); }
    }

    public static class Crosshairs extends Module {
        public final Mode style = mode("Style", 1, "Dot", "Cross", "Cross + Dot", "Ring", "Diamond", "Chevron", "Four Corners", "Triangle");
        public final Bool cooldown = bool("Attack Cooldown Indicator", true);
        public final Color color = color("Color", 0xFFFFFF);
        public final Num size = num("Size", 2, 10, 4, 1);
        public final Num gap = num("Gap", 0, 8, 3, 1);
        public final Num thickness = num("Thickness", 1, 3, 1, 1);
        public final Bool dynamic = bool("Dynamic Spread", true);
        public Crosshairs() { super("Crosshairs", "A custom crosshair with several styles and an optional cooldown indicator.", Category.HUD); }
    }

    // =====================================================================
    //  Movement
    // =====================================================================
    public static class ToggleSprint extends Module {
        public final Bool ignoreHunger = bool("Ignore Hunger", false);
        public ToggleSprint() { super("ToggleSprint", "Keeps you sprinting while moving forward.", Category.MOVEMENT); }
        @Override public void onTick() {
            var p = mc().player;
            if (p == null || mc().currentScreen != null) return;
            if (mc().options.forwardKey.isPressed() && !p.isSneaking() && !p.horizontalCollision
                    && (ignoreHunger.value || p.getHungerManager().getFoodLevel() > 6)) p.setSprinting(true);
        }
    }

    public static class BoatFly extends Module {
        public final Num speed = num("Speed", 0.1, 5, 1.0, 0.1);
        public final Num vertical = num("Vertical Speed", 0.1, 5, 1.0, 0.1);
        /** Not a Setting: not saved, not shown as a row. The GUI must set this true (after its warning
         *  prompt is accepted) before it will let this module be switched or key-bound on. Resets each session. */
        public boolean confirmedThisSession = false;
        public BoatFly() {
            super("BoatFly", "Flies the vehicle you are riding (boat, happy ghast, horse, etc). Can get you kicked on servers that check movement.", Category.MOVEMENT);
        }
        @Override public void onTick() {
            var p = mc().player;
            if (p == null || !(p.getVehicle() instanceof Entity v)) return;
            double fwd = (mc().options.forwardKey.isPressed() ? 1 : 0) - (mc().options.backKey.isPressed() ? 1 : 0);
            double strafe = (mc().options.leftKey.isPressed() ? 1 : 0) - (mc().options.rightKey.isPressed() ? 1 : 0);
            double up = (mc().options.jumpKey.isPressed() ? 1 : 0) - (mc().options.sneakKey.isPressed() ? 1 : 0);
            float yaw = (float) Math.toRadians(p.getYaw());
            double sin = Math.sin(yaw), cos = Math.cos(yaw);
            double vx = (fwd * -sin + strafe * cos) * speed.value;
            double vz = (fwd * cos + strafe * -sin) * speed.value;
            v.setVelocity(vx, up * vertical.value, vz);
            v.velocityModified = true;
            v.fallDistance = 0;
        }
    }

    // =====================================================================
    //  Visual
    // =====================================================================
    public static class Zoom extends Module {
        public final Num factor = num("Zoom Factor", 1.5, 12, 4, 0.1);
        public final Bool smooth = bool("Smooth", true);
        public final Num speed = num("Smooth Speed", 2, 30, 12, 1);
        public float current = 1f;
        public Zoom() {
            super("Zoom", "Hold the key to zoom in.", Category.VISUAL);
            key = GLFW.GLFW_KEY_C; holdMode.value = true;
        }
        @Override public void update(float dt) {
            float target = enabled ? factor.f() : 1f;
            current = smooth.value ? dev.halo.Anim.approach(current, target, speed.f(), dt) : target;
        }
    }
    public static float zoomFactor() { return ModuleManager.ZOOM == null ? 1f : ModuleManager.ZOOM.current; }

    public static class Fullbright extends Module {
        private boolean added;
        public Fullbright() { super("Fullbright", "See clearly in caves and at night.", Category.VISUAL); }
        @Override public void onTick() {
            var p = mc().player;
            if (!p.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100000, 0, false, false, false));
                added = true;
            }
        }
        @Override public void onDisable() {
            var p = mc().player;
            if (p != null && added) p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            added = false;
        }
    }

    public static class Xray extends Module {
        public final Bool ores = bool("Ores", true);
        public final Bool debris = bool("Ancient Debris", true);
        public final Bool containers = bool("Chests & Spawners", true);
        public final Bool leaves = bool("See Through Leaves", false);
        public final Bool stone = bool("See Through Stone/Dirt (full X-ray)", false);
        public final Bool water = bool("See Through Water", false);
        private static final Map<Block, Boolean> CACHE = new ConcurrentHashMap<>();

        public Xray() {
            super("Xray", "Hides terrain so ores stand out. Pair it with Fullbright.", Category.VISUAL);
            Runnable r = this::refresh;
            ores.onChange = r; debris.onChange = r; containers.onChange = r; leaves.onChange = r; stone.onChange = r; water.onChange = r;
        }
        public void refresh() {
            CACHE.clear();
            if (mc().world != null && enabled) mc().worldRenderer.reload();
        }
        @Override public void onEnable() { CACHE.clear(); if (mc().world != null) mc().worldRenderer.reload(); }
        @Override public void onDisable() { CACHE.clear(); if (mc().world != null) mc().worldRenderer.reload(); }

        public static boolean active() { return ModuleManager.XRAY != null && ModuleManager.XRAY.enabled; }

        public static boolean visible(BlockState s) {
            Xray x = ModuleManager.XRAY;
            return CACHE.computeIfAbsent(s.getBlock(), b -> {
                String p = Registries.BLOCK.getId(b).getPath();
                if (x.ores.value && p.endsWith("_ore")) return true;
                if (x.debris.value && p.equals("ancient_debris")) return true;
                if (x.containers.value && (p.equals("chest") || p.equals("trapped_chest") || p.equals("ender_chest")
                        || p.equals("barrel") || p.equals("spawner") || p.equals("trial_spawner"))) return true;
                if (x.leaves.value && p.endsWith("_leaves")) return true;
                if (x.water.value && (p.equals("water") || p.equals("flowing_water"))) return true;
                if (x.stone.value) {
                    return p.equals("stone") || p.equals("dirt") || p.equals("deepslate") || p.equals("netherrack")
                            || p.equals("grass_block") || p.equals("andesite") || p.equals("diorite") || p.equals("granite")
                            || p.equals("tuff") || p.equals("cobblestone") || p.equals("gravel") || p.equals("sand");
                }
                return false;
            });
        }
    }

    public static class Fog extends Module {
        public final Num density = num("Density", 0, 150, 35, 1);
        public final Num start = num("Start Distance", 0, 100, 70, 1);
        public final Num tint = num("Color Strength", 0, 100, 65, 1);
        public final Color color = color("Fog Color", 0xB8C4D6);
        public final Bool overworldOnly = bool("Overworld Only", true);
        public Fog() { super("Fog", "Distance fog with your own color. Clouds are left alone.", Category.VISUAL); }

        public static float blend() {
            Fog f = ModuleManager.FOG;
            MinecraftClient mc = mc();
            if (f == null || f.anim < 0.005f || mc.world == null) return 0f;
            if (f.overworldOnly.value && mc.world.getRegistryKey() != World.OVERWORLD) return 0f;
            if (mc.player != null && (mc.player.isSubmergedInWater() || mc.player.isInLava())) return 0f;
            return f.anim;
        }
        public static float endScale(float b) { return Math.max(0.03f, 1f - 0.0095f * ModuleManager.FOG.density.f() * b); }
        public static float startScale(float b) {
            float s = ModuleManager.FOG.start.f() / 100f;
            return endScale(b) * (1f - b + b * (0.2f + 0.8f * s));
        }
    }

    /** A lighter, lower-density cousin of Fog, blended separately. */
    public static class Mist extends Module {
        public final Num density = num("Density", 0, 40, 15, 1);
        public final Color color = color("Mist Color", 0xDDE4EC);
        public final Bool overworldOnly = bool("Overworld Only", true);
        public Mist() { super("Mist", "A light, soft haze near the ground. Less dense than Fog.", Category.VISUAL); }

        public static float blend() {
            Mist m = ModuleManager.MIST;
            MinecraftClient mc = mc();
            if (m == null || m.anim < 0.005f || mc.world == null) return 0f;
            if (m.overworldOnly.value && mc.world.getRegistryKey() != World.OVERWORLD) return 0f;
            return m.anim;
        }
        public static float endScale(float b) { return Math.max(0.35f, 1f - 0.012f * ModuleManager.MIST.density.f() * b); }
    }

    public static class Knives extends Module {
        public final Bool wood = bool("Wooden Sword", true);
        public final Bool stone = bool("Stone Sword", true);
        public final Bool iron = bool("Iron Sword", true);
        public final Bool golden = bool("Golden Sword", true);
        public final Bool diamond = bool("Diamond Sword", true);
        public final Bool netherite = bool("Netherite Sword", true);
        private static final Map<String, Boolean> applied = new HashMap<>();
        public Knives() { super("Knives", "Swords become knives (client side). Minecraft has no copper sword, so that tier is not included.", Category.VISUAL); }

        private Map<String, Boolean> wanted() {
            Map<String, Boolean> m = new LinkedHashMap<>();
            m.put("knives_wood", enabled && wood.value);
            m.put("knives_stone", enabled && stone.value);
            m.put("knives_iron", enabled && iron.value);
            m.put("knives_golden", enabled && golden.value);
            m.put("knives_diamond", enabled && diamond.value);
            m.put("knives_netherite", enabled && netherite.value);
            return m;
        }

        /** Keeps the built-in knife packs in sync with the module state. */
        public static void sync() {
            MinecraftClient mc = mc();
            Knives k = ModuleManager.KNIVES;
            if (k == null || mc.getOverlay() != null || mc.getResourcePackManager() == null) return;
            Map<String, Boolean> want = k.wanted();
            if (want.equals(applied)) return;
            ResourcePackManager pm = mc.getResourcePackManager();
            List<String> enabled = new ArrayList<>(pm.getEnabledIds());
            boolean changed = false;
            for (ResourcePackProfile prof : pm.getProfiles()) {
                String id = prof.getId();
                for (var e : want.entrySet()) {
                    if (!id.contains(e.getKey())) continue;
                    boolean on = e.getValue();
                    if (on && !enabled.contains(id)) { enabled.add(id); changed = true; }
                    if (!on && enabled.remove(id)) changed = true;
                }
            }
            applied.clear(); applied.putAll(want);
            if (changed) { pm.setEnabledProfiles(enabled); mc.reloadResources(); }
        }
    }

    public static class Hats extends Module {
        public final Mode type = mode("Hat Type", 0, "Top Hat", "Cap", "Wizard", "Crown", "Halo", "Beanie", "Headphones", "Antlers");
        public final Color color = color("Hat Color", 0x1E1E1E);
        public final Num size = num("Size", 0.7, 1.5, 1.0, 0.05);
        public final Num height = num("Height Offset", -1.0, 1.0, 0, 0.05);
        public final Bool bob = bool("Idle Bob", false);
        public Hats() { super("Hats", "Client-side hats on your character (visible in third person).", Category.VISUAL); }
    }

    public static class Wings extends Module {
        public final Mode type = mode("Wing Type", 0, "Angel", "Bat", "Dragon", "Fairy", "Mechanical");
        public final Color color = color("Wing Color", 0xFFFFFF);
        public final Num size = num("Size", 0.6, 1.6, 1.0, 0.05);
        public Wings() { super("Wings", "Client-side wings on your back (visible in third person).", Category.VISUAL); }
    }

    public static class Auras extends Module {
        public final Mode type = mode("Aura Type", 0, "Glitched", "Embers", "Frost", "Void", "Arcane");
        public final Num density = num("Density", 1, 10, 4, 1);
        public Auras() { super("Auras", "Particle effects orbiting close around you. No color setting; each aura has its own look.", Category.VISUAL); }

        @Override public void onFrame(float dt) {
            var p = mc().player;
            var w = mc().world;
            if (p == null || w == null) return;
            if (RNG.nextFloat() > density.value / 10f) return;
            double a = RNG.nextDouble() * Math.PI * 2, r = 0.5 + RNG.nextDouble() * 0.35;
            double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r, y = p.getY() + RNG.nextDouble() * p.getHeight();
            switch (type.index) {
                case 0 -> w.addParticleClient(ParticleTypes.CRIT, x, y, z, 0, 0, 0);
                case 1 -> w.addParticleClient(ParticleTypes.FLAME, x, y, z, 0, 0.02, 0);
                case 2 -> w.addParticleClient(ParticleTypes.SNOWFLAKE, x, y, z, 0, -0.01, 0);
                case 3 -> w.addParticleClient(ParticleTypes.SQUID_INK, x, y, z, 0, 0, 0);
                default -> w.addParticleClient(ParticleTypes.PORTAL, x, y, z, 0, 0, 0);
            }
        }
    }

    public static class Fire extends Module {
        public final Color color = color("Fire Color", 0xFF7A1A);
        public final Num intensity = num("Tint Strength", 10, 100, 70, 5);
        public Fire() { super("Fire", "Recolors the fire overlay that covers your screen while you are burning.", Category.VISUAL); }

        public static boolean active() {
            Fire f = ModuleManager.FIRE;
            var p = mc().player;
            return f != null && f.enabled && p != null && (p.isOnFire() || p.getFireTicks() > 0);
        }
    }

    public static class Drugs extends Module {
        public final Mode drug = mode("Drug", 0, "LSD", "Salvia", "Meth");
        public Drugs() { super("Drugs", "A screen-tint visual effect. Not a real shader, just an overlay, so it only approximates each effect.", Category.VISUAL); }
    }

    public static class BlockOutliner extends Module {
        public final Mode style = mode("Style", 0, "Outline", "Corners", "Pulse", "Scan");
        public final Color color = color("Color", 0x7CF2FF);
        public BlockOutliner() { super("Block Outliner", "Outlines the block you are looking at using colored particles.", Category.VISUAL); }

        @Override public void onFrame(float dt) {
            var mc = mc();
            if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || mc.world == null) return;
            BlockPos pos = bhr.getBlockPos();
            if (mc.world.getBlockState(pos).isAir()) return;
            Vector3f col = new Vector3f(((color.rgb >> 16) & 255) / 255f, ((color.rgb >> 8) & 255) / 255f, (color.rgb & 255) / 255f);
            DustParticleEffect dust = new DustParticleEffect(col, 1f);
            double x0 = pos.getX(), y0 = pos.getY(), z0 = pos.getZ();
            long t = System.currentTimeMillis();
            switch (style.index) {
                case 1 -> { // corners
                    for (int cx = 0; cx <= 1; cx++) for (int cy = 0; cy <= 1; cy++) for (int cz = 0; cz <= 1; cz++)
                        mc.world.addParticleClient(dust, x0 + cx, y0 + cy, z0 + cz, 0, 0, 0);
                }
                case 2 -> { // pulse: density breathes with time
                    float phase = (float) (0.5 + 0.5 * Math.sin(t / 250.0));
                    if (RNG.nextFloat() < phase) edges(mc, dust, x0, y0, z0, 0.35f);
                }
                case 3 -> { // scan: a moving band along Y
                    double band = (t % 1000) / 1000.0;
                    for (double[] e : EDGES) {
                        double ya = y0 + e[1] + (e[4] - e[1]) * 0.5;
                        if (Math.abs((ya - y0) - band) < 0.15) mc.world.addParticleClient(dust, x0 + (e[0] + e[3]) / 2, ya, z0 + (e[2] + e[5]) / 2, 0, 0, 0);
                    }
                }
                default -> edges(mc, dust, x0, y0, z0, 1f);
            }
        }

        private static final double[][] EDGES = {
                {0,0,0, 1,0,0}, {1,0,0, 1,0,1}, {1,0,1, 0,0,1}, {0,0,1, 0,0,0},
                {0,1,0, 1,1,0}, {1,1,0, 1,1,1}, {1,1,1, 0,1,1}, {0,1,1, 0,1,0},
                {0,0,0, 0,1,0}, {1,0,0, 1,1,0}, {1,0,1, 1,1,1}, {0,0,1, 0,1,1} };

        private void edges(MinecraftClient mc, DustParticleEffect dust, double x0, double y0, double z0, float chance) {
            for (double[] e : EDGES) {
                int steps = 4;
                for (int i = 0; i <= steps; i++) {
                    if (RNG.nextFloat() > chance) continue;
                    double t = i / (double) steps;
                    double x = x0 + e[0] + (e[3] - e[0]) * t;
                    double y = y0 + e[1] + (e[4] - e[1]) * t;
                    double z = z0 + e[2] + (e[5] - e[2]) * t;
                    mc.world.addParticleClient(dust, x, y, z, 0, 0, 0);
                }
            }
        }
    }

    /** Places a block that only you can see: it is written straight into your local client world,
     *  never sent to the server, so nobody else sees it and the server can wipe it back any time
     *  (e.g. when that chunk re-syncs). Not a duplication of any kind — no item is consumed. */
    public static class GhostBlock extends Module {
        public final Bool replaceSolid = bool("Replace Existing Blocks", false);
        public final Num maxTracked = num("Max Ghost Blocks", 10, 300, 100, 10);
        public final Mode shape = mode("Shape", 0, "Single Block", "3x3 Wall", "3x3 Floor");
        private static final LinkedHashMap<BlockPos, BlockState> placed = new LinkedHashMap<>();

        public GhostBlock() {
            super("GhostBlock", "Bind a key and press it while looking at a block to place a client-only fake copy of the block in your hand. Nobody else sees it, and the server can overwrite it the moment that chunk re-syncs. Works like a button, not a toggle.", Category.VISUAL);
        }

        private void place(MinecraftClient mc, BlockState state, BlockPos pos) {
            BlockState existing = mc.world.getBlockState(pos);
            if (!existing.isAir() && !replaceSolid.value) return;
            placed.putIfAbsent(pos, existing);
            mc.world.setBlockState(pos, state);
            while (placed.size() > maxTracked.i()) {
                var it = placed.entrySet().iterator();
                var e = it.next();
                mc.world.setBlockState(e.getKey(), e.getValue());
                it.remove();
            }
        }

        @Override public void onEnable() {
            var mc = mc();
            var p = mc.player;
            if (p != null && mc.world != null && mc.crosshairTarget instanceof BlockHitResult bhr
                    && p.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem bi) {
                BlockPos base = bhr.getBlockPos().offset(bhr.getSide());
                BlockState state = bi.getBlock().getDefaultState();
                switch (shape.index) {
                    case 1 -> { // 3x3 wall: flat on X/Z, stacked on Y regardless of which face was hit
                        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++)
                            place(mc, state, base.add(i, j, 0));
                    }
                    case 2 -> { // 3x3 floor
                        for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++)
                            place(mc, state, base.add(i, 0, j));
                    }
                    default -> place(mc, state, base);
                }
            }
            setEnabled(false);
        }
    }

    public static class Impact extends Module {
        public final Num blocks = num("Ring Size (blocks)", 4, 80, 8, 1);
        public final Mode animation = mode("Animation", 0, "Ring", "Expanding Rings", "Spiral", "Burst");
        public final Color color = color("Color", 0xFFFFFF);
        private boolean wasOnGround = true;
        public Impact() { super("Impact", "Draws a ring of particles when you land after a fall or jump.", Category.VISUAL); }

        @Override public void onFrame(float dt) {
            var p = mc().player;
            var w = mc().world;
            if (p == null || w == null) return;
            boolean onGround = p.isOnGround();
            if (onGround && !wasOnGround && p.fallDistance >= 0.1f) spawnRing(w, p.getX(), p.getY(), p.getZ());
            wasOnGround = onGround;
        }

        private void spawnRing(World w, double cx, double cy, double cz) {
            Vector3f col = new Vector3f(((color.rgb >> 16) & 255) / 255f, ((color.rgb >> 8) & 255) / 255f, (color.rgb & 255) / 255f);
            DustParticleEffect dust = new DustParticleEffect(col, 1.3f);
            double r = blocks.value;
            int points = (int) Math.max(12, r * 4);
            switch (animation.index) {
                case 1 -> { for (double rr = r * 0.3; rr <= r; rr += r * 0.3) ring(w, dust, cx, cy, cz, rr, points); }
                case 2 -> {
                    for (int i = 0; i < points * 2; i++) {
                        double t = i / (double) (points * 2);
                        double a = t * Math.PI * 6, rr = t * r;
                        w.addParticleClient(dust, cx + Math.cos(a) * rr, cy + t * 1.2, cz + Math.sin(a) * rr, 0, 0, 0);
                    }
                }
                case 3 -> {
                    for (int i = 0; i < points; i++) {
                        double a = i / (double) points * Math.PI * 2;
                        w.addParticleClient(dust, cx, cy, cz, Math.cos(a) * r * 0.05, 0.1, Math.sin(a) * r * 0.05);
                    }
                }
                default -> ring(w, dust, cx, cy, cz, r, points);
            }
        }
        private void ring(World w, DustParticleEffect dust, double cx, double cy, double cz, double r, int points) {
            for (int i = 0; i < points; i++) {
                double a = i / (double) points * Math.PI * 2;
                w.addParticleClient(dust, cx + Math.cos(a) * r, cy + 0.05, cz + Math.sin(a) * r, 0, 0, 0);
            }
        }
    }

    // =====================================================================
    //  Combat
    // =====================================================================
    public static class TriggerBot extends Module {
        public final Mode style = mode("Combat Style", 1, "1.21 (charge + sweep)", "1.8 (steady CPS)");
        public final Num chance = num("Hit Chance %", 0, 100, 97, 1);
        public final Num cps = num("Attacks Per Second (1.8)", 2, 20, 11, 1);
        public final Num minDelay = num("Min Delay (ms)", 0, 300, 10, 1);
        public final Num maxDelay = num("Max Delay (ms)", 0, 400, 45, 1);
        public final Num charge = num("Charge Needed % (1.21)", 50, 100, 90, 1);
        public final Bool sweepMode = bool("Require Sweep Stance (1.21)", false);
        public final Bool requireSword = bool("Require A Sword", false);
        public final Num range = num("Range", 2, 6, 4.2, 0.1);
        public final Bool players = bool("Players", true);
        public final Bool hostile = bool("Hostile Mobs", true);
        public final Bool passive = bool("Passive Mobs", false);

        private long scheduleAt, pauseUntil, nextCpsHit;
        private boolean decision;

        public TriggerBot() { super("TriggerBot", "Attacks what you are looking at, with human-like timing. Defaults work with any item on any server.", Category.COMBAT); }

        @Override public void onDisable() { scheduleAt = 0; }

        @Override public void onFrame(float dt) {
            MinecraftClient mc = mc();
            var p = mc.player;
            if (p == null || mc.currentScreen != null || mc.interactionManager == null) { scheduleAt = 0; return; }
            long now = System.currentTimeMillis();
            if (now < pauseUntil) return;

            Entity t = (mc.crosshairTarget instanceof EntityHitResult ehr) ? ehr.getEntity() : null;
            if (t == null || !validTarget(t, players.value, hostile.value, passive.value)) { scheduleAt = 0; return; }
            if (p.distanceTo(t) > range.value) { scheduleAt = 0; return; }
            if (requireSword.value && !p.getMainHandStack().isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) { scheduleAt = 0; return; }

            boolean legacy = style.index == 1;
            if (!legacy) {
                float need = Math.min(1f, charge.f() / 100f + RNG.nextFloat() * 0.03f);
                if (p.getAttackCooldownProgress(0.5f) < need) { scheduleAt = 0; return; }
                if (sweepMode.value && (!p.isOnGround() || p.isSprinting())) { scheduleAt = 0; return; }
            } else {
                if (now < nextCpsHit) return;
            }

            if (scheduleAt == 0) {
                double lo = Math.min(minDelay.value, maxDelay.value), hi = Math.max(minDelay.value, maxDelay.value);
                scheduleAt = now + (long) (lo + (hi - lo) * (RNG.nextDouble() + RNG.nextDouble()) / 2.0);
                decision = RNG.nextInt(100) < chance.value;
            }
            if (now >= scheduleAt) {
                if (decision) {
                    mc.interactionManager.attackEntity(p, t);
                    p.swingHand(Hand.MAIN_HAND);
                    if (legacy) {
                        long period = (long) (1000.0 / cps.value);
                        nextCpsHit = now + period + RNG.nextInt((int) Math.max(1, period / 6));
                    } else {
                        pauseUntil = now + 25 + RNG.nextInt(40);
                    }
                } else {
                    pauseUntil = now + 120 + RNG.nextInt(220);
                }
                scheduleAt = 0;
            }
        }
    }

    public static class AimAssist extends Module {
        public final Num speed = num("Speed", 1, 20, 6, 0.5);
        public final Num range = num("Range", 2, 8, 4.5, 0.5);
        public final Num fov = num("FOV", 10, 180, 90, 5);
        public final Num delay = num("Reaction Delay (ms)", 0, 250, 40, 5);
        public final Num wobble = num("Human Wobble %", 0, 100, 30, 1);
        public final Mode aimAt = mode("Aim At", 1, "Head", "Chest", "Body");
        public final Bool vertical = bool("Vertical Aim", true);
        public final Bool players = bool("Players", true);
        public final Bool hostile = bool("Hostile Mobs", true);
        public final Bool passive = bool("Passive Mobs", false);

        private Entity target;
        private long acquireAt;
        private Vec3d offset = Vec3d.ZERO;

        public AimAssist() { super("AimAssist", "Smoothly turns your crosshair onto targets at all times while enabled, not only while attacking.", Category.COMBAT); }

        @Override public void onDisable() { target = null; }

        private Vec3d point(Entity e) {
            double h = e.getHeight();
            double y = switch (aimAt.index) { case 0 -> e.getEyeY(); case 1 -> e.getY() + h * 0.65; default -> e.getY() + h * 0.5; };
            return new Vec3d(e.getX(), y, e.getZ()).add(offset);
        }

        @Override public void onFrame(float dt) {
            MinecraftClient mc = mc();
            var p = mc.player;
            if (p == null || mc.currentScreen != null || mc.world == null) { target = null; return; }

            Entity best = null; double bestAng = fov.value / 2.0;
            Vec3d eye = p.getEyePos();
            var box = p.getBoundingBox().expand(range.value);
            for (Entity e : mc.world.getOtherEntities(p, box)) {
                if (!validTarget(e, players.value, hostile.value, passive.value)) continue;
                if (p.distanceTo(e) > range.value || !p.canSee(e)) continue;
                Vec3d d = point(e).subtract(eye);
                double ang = Math.abs(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(d.z, d.x)) - 90 - p.getYaw()));
                if (ang < bestAng) { bestAng = ang; best = e; }
            }
            long now = System.currentTimeMillis();
            if (best != target) {
                target = best;
                if (best != null) {
                    acquireAt = now + (long) (delay.value * (0.7 + RNG.nextDouble() * 0.6));
                    double w = best.getWidth() * 0.18;
                    offset = new Vec3d((RNG.nextDouble() - 0.5) * w, (RNG.nextDouble() - 0.5) * 0.18, (RNG.nextDouble() - 0.5) * w);
                }
            }
            if (target == null || now < acquireAt) return;

            Vec3d d = point(target).subtract(eye);
            double hd = Math.sqrt(d.x * d.x + d.z * d.z);
            float wantYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
            float wantPitch = (float) -Math.toDegrees(Math.atan2(d.y, hd));
            float dy = MathHelper.wrapDegrees(wantYaw - p.getYaw());
            float dp = wantPitch - p.getPitch();

            double t = now / 1000.0;
            float wob = wobble.f() / 100f;
            float nY = (float) ((Math.sin(t * 7.1) + Math.sin(t * 3.3 + 1.7)) * 0.5 * wob * 0.6);
            float nP = (float) ((Math.sin(t * 5.7 + 0.4) + Math.sin(t * 2.9 + 2.2)) * 0.5 * wob * 0.4);

            float k = 1f - (float) Math.exp(-speed.f() * dt);
            float maxStep = 600f * dt;
            float sy = MathHelper.clamp(dy * k + nY * dt * 20f * 0.1f, -maxStep, maxStep);
            float sp = vertical.value ? MathHelper.clamp(dp * k + nP * dt * 20f * 0.1f, -maxStep, maxStep) : 0f;
            if (Math.abs(dy) < 0.05f) sy = 0f;
            if (Math.abs(dp) < 0.05f) sp = 0f;
            if (sy != 0f || sp != 0f) p.changeLookDirection(sy / 0.15, sp / 0.15);
        }
    }

    public static class HitSfx extends Module {
        private static final String[] NAMES = {
                "Note: Bell", "Note: Harp", "Note: Bass", "Note: Snare", "Note: Hat", "Note: Pling",
                "Note: Bass Drum", "Note: Bit", "Note: Didgeridoo", "Note: Guitar", "Note: Flute", "Note: Chime",
                "Note: Xylophone", "Note: Iron Xylophone", "Note: Cow Bell", "Note: Banjo",
                "Anvil Land", "XP Orb", "Firework Blast", "Totem", "Arrow Hit" };
        private static final SoundEvent[] SOUNDS = {
        SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
        SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
        SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(),
        SoundEvents.BLOCK_NOTE_BLOCK_GUITAR.value(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
        SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE.value(), SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE.value(), SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL.value(),
        SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value(), SoundEvents.BLOCK_ANVIL_LAND.value(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP.value(),
        SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST.value(), SoundEvents.ITEM_TOTEM_USE.value(), SoundEvents.ENTITY_ARROW_HIT_PLAYER.value() };
        public final Mode sound = mode("Sound", 0, NAMES);
        public final Num pitch = num("Pitch", 0.5, 2.0, 1.0, 0.05);
        public HitSfx() { super("HitSfx", "Plays a custom sound (client side) whenever you hit an entity.", Category.COMBAT); }

        public static void onHit() {
            HitSfx h = ModuleManager.HITSFX;
            var mc = mc();
            if (h == null || !h.enabled || mc.player == null) return;
            mc.player.playSound(SOUNDS[h.sound.index], SoundCategory.PLAYERS, 1f, h.pitch.f());
        }
    }

    // =====================================================================
    //  Server
    // =====================================================================
    public static class FakePotion extends Module {
        public final Mode mode = mode("Mode", 0, "Client Side", "Server Side", "Both");
        private long disableAt;
        public FakePotion() {
            super("FakePotion", "Bind a key and press it to fake-throw a potion: a throw animation and sound with no real item used. 'Server Side' also sends a hand-swing packet other players can see. Works like a button, not a toggle.", Category.SERVER);
        }
        @Override public void onEnable() {
            var mc = mc();
            var p = mc.player;
            if (p == null) { setEnabled(false); return; }
            if (mode.index != 0) p.swingHand(Hand.MAIN_HAND);
            mc.world.playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS, 0.5f, 0.9f + RNG.nextFloat() * 0.2f);
            disableAt = System.currentTimeMillis() + 50;
        }
        @Override public void onTick() {
            if (System.currentTimeMillis() >= disableAt) setEnabled(false);
        }
    }

    // =====================================================================
    //  Shield Status: enemy shield turns glowing red while it is disabled
    // =====================================================================
    public static class ShieldStatus extends Module {
        public final Num duration = num("Disabled Duration (s)", 1, 10, 5, 0.5);
        public final Bool glow = bool("Glow Effect", true);

        private static final Map<Integer, Long> UNTIL = new HashMap<>();
        private static final Map<Integer, ItemStack[]> ORIG = new HashMap<>();
        private static final EquipmentSlot[] HANDS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };

        public ShieldStatus() { super("ShieldStatus", "Enemy shields glow red while they are disabled.", Category.COMBAT); }

        /** Called by LivingEntityMixin when the shield-break status arrives. */
        public static void onBreak(LivingEntity e) {
            ShieldStatus s = ModuleManager.SHIELD;
            if (s == null || !s.enabled || e == mc().player) return;
            UNTIL.put(e.getId(), System.currentTimeMillis() + (long) (s.duration.value * 1000));
        }

        private static boolean red(ItemStack st) { return st.get(DataComponentTypes.BASE_COLOR) == DyeColor.RED; }

        private void setRed(LivingEntity le, boolean on) {
            ItemStack[] orig = ORIG.computeIfAbsent(le.getId(), k -> new ItemStack[2]);
            for (int i = 0; i < 2; i++) {
                ItemStack cur = le.getEquippedStack(HANDS[i]);
                if (on) {
                    if (cur.isOf(Items.SHIELD) && !red(cur)) {
                        orig[i] = cur;
                        ItemStack c = cur.copy();
                        c.set(DataComponentTypes.BASE_COLOR, DyeColor.RED);
                        if (glow.value) c.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                        le.equipStack(HANDS[i], c);
                    }
                } else if (orig[i] != null && cur.isOf(Items.SHIELD) && red(cur)) {
                    le.equipStack(HANDS[i], orig[i]);
                    orig[i] = null;
                }
            }
        }

        @Override public void onTick() {
            if (mc().world == null) { UNTIL.clear(); ORIG.clear(); return; }
            long now = System.currentTimeMillis();
            for (Integer id : new ArrayList<>(UNTIL.keySet())) {
                Entity e = mc().world.getEntityById(id);
                if (!(e instanceof LivingEntity le) || !le.isAlive()) { UNTIL.remove(id); ORIG.remove(id); continue; }
                if (now < UNTIL.get(id)) setRed(le, true);
                else { setRed(le, false); UNTIL.remove(id); ORIG.remove(id); }
            }
        }

        @Override public void onDisable() {
            if (mc().world != null)
                for (Integer id : new ArrayList<>(UNTIL.keySet()))
                    if (mc().world.getEntityById(id) instanceof LivingEntity le) setRed(le, false);
            UNTIL.clear(); ORIG.clear();
        }
    }

    // =====================================================================
    //  Atmosphere: sky + horizon tint with presets (not real shaders)
    // =====================================================================
    public static class Atmosphere extends Module {
        private static final int[][] PRESETS = {
                { 0, 0 }, { 0xE8965A, 0xFFC27A }, { 0x5B3E96, 0xE07FA8 },
                { 0x6FB7F2, 0xDDF1FF }, { 0x9B4DFF, 0x00E5FF }, { 0x8E959E, 0xB7BCC4 } };
        public final Mode preset = mode("Preset", 3, "Custom", "Golden Hour", "Twilight", "Frost", "Neon", "Overcast");
        public final Color sky = color("Sky Color", 0x6FB7F2);
        public final Color horizon = color("Horizon Color", 0xDDF1FF);
        public final Num strength = num("Strength", 0, 100, 70, 1);
        public final Bool tintHorizon = bool("Tint Horizon", true);
        public final Bool voidSky = bool("Void Sky (End-style)", false);

        public Atmosphere() {
            super("Atmosphere", "Tints the sky and horizon, or replaces it with the void (End) sky. Not real shaders.", Category.VISUAL);
            preset.onChange = () -> {
                if (preset.index > 0) { sky.rgb = PRESETS[preset.index][0]; horizon.rgb = PRESETS[preset.index][1]; }
            };
        }

        public static float blend() {
            Atmosphere a = ModuleManager.ATMOS;
            MinecraftClient mc = mc();
            if (a == null || a.anim < 0.005f || mc.world == null) return 0f;
            if (mc.world.getRegistryKey() != World.OVERWORLD) return 0f;
            return a.anim;
        }
        public static int tintSky(int color) {
            if (voidSkyActive()) return (color & 0xFF000000); // solid black, End-portal-like void
            float b = blend();
            if (b <= 0f) return color;
            Atmosphere a = ModuleManager.ATMOS;
            return (color & 0xFF000000) | Gfx.mix(color & 0xFFFFFF, a.sky.rgb, a.strength.f() / 100f * b);
        }
        public static boolean voidSkyActive() {
            Atmosphere a = ModuleManager.ATMOS;
            return a != null && a.enabled && a.voidSky.value && mc().world != null && mc().world.getRegistryKey() == World.OVERWORLD;
        }
    }

    // =====================================================================
    //  HUD Style: glass hotbar and status bars (crosshair moved to Crosshairs)
    // =====================================================================
    public static class HudTheme extends Module {
        public final Bool stats = bool("Show Health / Food / Armor", true);
        public HudTheme() {
            super("HUD Style", "Replaces the vanilla hotbar and status bars with glass versions.", Category.HUD);
            enabled = true; anim = 1f; showInList.value = false;
        }
    }
}

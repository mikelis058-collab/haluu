package dev.halo.module;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import dev.halo.Gfx;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.DyeColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
        private static final String[] ORE_NAMES = { "Coal", "Iron", "Copper", "Gold", "Redstone", "Lapis", "Diamond", "Emerald", "Nether Quartz", "Nether Gold" };
        private static final String[] ORE_KEYS  = { "coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore", "emerald_ore", "nether_quartz_ore", "nether_gold_ore" };
        private static final String[] OTHER_NAMES = { "Ancient Debris", "Chests", "Ender Chests", "Barrels", "Shulker Boxes", "Spawners", "Raw Ore Blocks", "Obsidian", "Budding Amethyst", "Torches" };

        public final Mode preset = mode("Preset", 1, "Custom", "All Ores", "Diamonds + Debris", "Valuables", "Redstone + Lapis", "Storage Only");
        public final Multi ores = multi("Ores", true, ORE_NAMES);
        public final Multi other = multi("Other Blocks", true, OTHER_NAMES);
        private static final Map<Block, Boolean> CACHE = new ConcurrentHashMap<>();

        public Xray() {
            super("Xray", "Hides terrain so ores stand out. Pair it with Fullbright.", Category.VISUAL);
            other.on[4] = false; other.on[6] = false; other.on[7] = false; other.on[8] = false; other.on[9] = false;
            ores.onChange = () -> { preset.index = 0; refresh(); };
            other.onChange = () -> { preset.index = 0; refresh(); };
            preset.onChange = () -> { applyPreset(); refresh(); };
        }

        private void set(Multi m, String[] names, String... wanted) {
            java.util.Arrays.fill(m.on, false);
            List<String> w = java.util.Arrays.asList(wanted);
            for (int i = 0; i < names.length; i++) if (w.contains(names[i])) m.on[i] = true;
        }

        private void applyPreset() {
            switch (preset.index) {
                case 1 -> { java.util.Arrays.fill(ores.on, true); set(other, OTHER_NAMES, "Ancient Debris", "Chests", "Ender Chests", "Barrels", "Spawners"); }
                case 2 -> { set(ores, ORE_NAMES, "Diamond"); set(other, OTHER_NAMES, "Ancient Debris"); }
                case 3 -> { set(ores, ORE_NAMES, "Diamond", "Emerald", "Gold", "Nether Gold", "Nether Quartz"); set(other, OTHER_NAMES, "Ancient Debris", "Spawners"); }
                case 4 -> { set(ores, ORE_NAMES, "Redstone", "Lapis"); set(other, OTHER_NAMES); }
                case 5 -> { set(ores, ORE_NAMES); set(other, OTHER_NAMES, "Chests", "Ender Chests", "Barrels", "Shulker Boxes", "Spawners"); }
                default -> {}
            }
        }

        public void refresh() {
            CACHE.clear();
            if (mc().world != null && enabled) mc().worldRenderer.reload();
        }
        @Override public void onEnable() { CACHE.clear(); if (mc().world != null) mc().worldRenderer.reload(); }
        @Override public void onDisable() { CACHE.clear(); if (mc().world != null) mc().worldRenderer.reload(); }

        public static boolean active() { return ModuleManager.XRAY != null && ModuleManager.XRAY.enabled; }

        private static boolean oreMatch(String p, int i) {
            String k = ORE_KEYS[i];
            if (k.equals("gold_ore")) return p.endsWith("gold_ore") && !p.equals("nether_gold_ore");
            return p.endsWith(k);
        }

        private static boolean test(Xray x, String p) {
            for (int i = 0; i < ORE_KEYS.length; i++) if (x.ores.on[i] && oreMatch(p, i)) return true;
            boolean[] o = x.other.on;
            if (o[0] && p.equals("ancient_debris")) return true;
            if (o[1] && (p.equals("chest") || p.equals("trapped_chest"))) return true;
            if (o[2] && p.equals("ender_chest")) return true;
            if (o[3] && p.equals("barrel")) return true;
            if (o[4] && p.endsWith("shulker_box")) return true;
            if (o[5] && (p.equals("spawner") || p.equals("trial_spawner"))) return true;
            if (o[6] && p.startsWith("raw_") && p.endsWith("_block")) return true;
            if (o[7] && (p.equals("obsidian") || p.equals("crying_obsidian"))) return true;
            if (o[8] && p.equals("budding_amethyst")) return true;
            return o[9] && p.endsWith("torch");
        }

        public static boolean visible(BlockState s) {
            Xray x = ModuleManager.XRAY;
            return CACHE.computeIfAbsent(s.getBlock(), b -> test(x, Registries.BLOCK.getId(b).getPath()));
        }
    }

    public static class Fog extends Module {
        public final Num density = num("Density", 0, 150, 35, 1);
        public final Num start = num("Start Distance", 0, 100, 70, 1);
        public final Num tint = num("Color Strength", 0, 100, 65, 1);
        public final Color color = color("Fog Color", 0xB8C4D6);
        public final Bool overworldOnly = bool("Overworld Only", true);
        public Fog() { super("Fog", "Distance fog with your own color. Clouds are left alone.", Category.VISUAL); }

        /** 0..1 blend, includes the fade in/out animation. */
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

    public static class Knives extends Module {
        public static final String[] SWORDS = { "Wooden", "Stone", "Copper", "Iron", "Golden", "Diamond", "Netherite" };
        public static final String[] ITEMS = { "wooden_sword", "stone_sword", "copper_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword" };
        public final Multi swords = multi("Swords", true, SWORDS);
        private static final boolean[] applied = new boolean[7];
        public Knives() { super("Knives", "Swords become butterfly knives (client side).", Category.VISUAL); }

        /** Keeps the per-sword knife packs in sync with the module state. */
        public static void sync() {
            MinecraftClient mc = mc();
            Knives k = ModuleManager.KNIVES;
            if (k == null || mc.getOverlay() != null || mc.getResourcePackManager() == null) return;
            boolean[] want = new boolean[ITEMS.length];
            boolean same = true;
            for (int i = 0; i < want.length; i++) { want[i] = k.enabled && k.swords.on[i]; if (want[i] != applied[i]) same = false; }
            if (same) return;
            ResourcePackManager pm = mc.getResourcePackManager();
            List<String> enabled = new ArrayList<>(pm.getEnabledIds());
            boolean changed = false;
            for (ResourcePackProfile prof : pm.getProfiles()) {
                String id = prof.getId();
                for (int i = 0; i < ITEMS.length; i++) {
                    if (!id.contains("knives_" + ITEMS[i])) continue;
                    if (want[i] && !enabled.contains(id)) { enabled.add(id); changed = true; }
                    if (!want[i] && enabled.remove(id)) changed = true;
                }
            }
            System.arraycopy(want, 0, applied, 0, want.length);
            if (changed) { pm.setEnabledProfiles(enabled); mc.reloadResources(); }
        }
    }

    public static class Hats extends Module {
        public static final String[] TYPES = { "Top Hat", "Cap", "Wizard", "Crown", "Halo", "Cowboy", "Beanie", "Santa", "Fedora", "Devil Horns", "Cat Ears", "Chef", "Propeller" };
        public final Mode type = mode("Hat Type", 0, TYPES);
        public final Color color = color("Hat Color", 0x1E1E1E);
        public final Num size = num("Size", 0.5, 2.0, 1.0, 0.05);
        public final Num height = num("Height", -4, 12, 0, 0.5);
        public final Num tilt = num("Tilt", -30, 30, 0, 1);
        public final Num spin = num("Spin Speed", 0, 10, 0, 1);
        public final Bool others = bool("Show On Other Players", false);
        public Hats() { super("Hats", "Client-side hats on your character (visible in third person).", Category.VISUAL); }
    }

    // =====================================================================
    //  Combat
    // =====================================================================
    public static class TriggerBot extends Module {
        public final Num chance = num("Hit Chance %", 0, 100, 97, 1);
        public final Num minDelay = num("Min Delay (ms)", 0, 300, 18, 1);
        public final Num maxDelay = num("Max Delay (ms)", 0, 400, 70, 1);
        public final Bool fullCharge = bool("Wait For Full Charge", true);
        public final Num charge = num("Charge Needed %", 50, 100, 95, 1);
        public final Bool sweepMode = bool("Sweep Mode (ground, no sprint)", true);
        public final Bool swordOnly = bool("Swords Only", true);
        public final Bool players = bool("Players", true);
        public final Bool hostile = bool("Hostile Mobs", true);
        public final Bool passive = bool("Passive Mobs", false);

        private long scheduleAt, pauseUntil;
        private boolean decision;

        public TriggerBot() { super("TriggerBot", "Attacks what you are looking at, with human-like timing.", Category.COMBAT); }

        @Override public void onDisable() { scheduleAt = 0; }

        @Override public void onFrame(float dt) {
            MinecraftClient mc = mc();
            var p = mc.player;
            if (p == null || mc.currentScreen != null || mc.interactionManager == null) { scheduleAt = 0; return; }
            long now = System.currentTimeMillis();
            if (now < pauseUntil) return;

            Entity t = (mc.crosshairTarget instanceof EntityHitResult ehr) ? ehr.getEntity() : null;
            if (t == null || !validTarget(t, players.value, hostile.value, passive.value)) { scheduleAt = 0; return; }
            if (swordOnly.value && !p.getMainHandStack().isIn(ItemTags.SWORDS)) { scheduleAt = 0; return; }
            if (fullCharge.value) {
                float need = Math.min(1f, charge.f() / 100f + RNG.nextFloat() * 0.03f);
                if (p.getAttackCooldownProgress(0.5f) < need) { scheduleAt = 0; return; }
            }
            if (sweepMode.value && (!p.isOnGround() || p.isSprinting())) { scheduleAt = 0; return; }

            if (scheduleAt == 0) {
                double lo = Math.min(minDelay.value, maxDelay.value), hi = Math.max(minDelay.value, maxDelay.value);
                scheduleAt = now + (long) (lo + (hi - lo) * (RNG.nextDouble() + RNG.nextDouble()) / 2.0);
                decision = RNG.nextInt(100) < chance.value;
            }
            if (now >= scheduleAt) {
                if (decision) {
                    mc.interactionManager.attackEntity(p, t);
                    p.swingHand(Hand.MAIN_HAND);
                    pauseUntil = now + 25 + RNG.nextInt(40);
                } else {
                    pauseUntil = now + 120 + RNG.nextInt(220);
                }
                scheduleAt = 0;
            }
        }
    }

    public static class AimHelper extends Module {
        public final Num speed = num("Speed", 1, 20, 6, 0.5);
        public final Num range = num("Range", 2, 8, 4.5, 0.5);
        public final Num fov = num("FOV", 10, 180, 90, 5);
        public final Num delay = num("Reaction Delay (ms)", 0, 250, 40, 5);
        public final Num wobble = num("Human Wobble %", 0, 100, 30, 1);
        public final Mode aimAt = mode("Aim At", 1, "Head", "Chest", "Body");
        public final Bool vertical = bool("Vertical Aim", true);
        public final Bool onlyAttacking = bool("Only While Attacking", true);
        public final Bool players = bool("Players", true);
        public final Bool hostile = bool("Hostile Mobs", true);
        public final Bool passive = bool("Passive Mobs", false);

        private Entity target;
        private long acquireAt;
        private Vec3d offset = Vec3d.ZERO;

        public AimHelper() { super("AimHelper", "Smoothly nudges your crosshair toward targets.", Category.COMBAT); }

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
            if (onlyAttacking.value && !mc.options.attackKey.isPressed()) { target = null; return; }

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

    // =====================================================================
    //  Hit SFX: plays a sound when you hit something (fixed pitch)
    // =====================================================================
    public static class HitSfx extends Module {
        private static final String[] NAMES = {
                "Note Block: Pling", "Note Block: Bell", "Note Block: Harp", "Note Block: Bit", "Note Block: Chime",
                "Note Block: Xylophone", "Note Block: Banjo", "Note Block: Guitar", "Note Block: Flute", "Note Block: Cow Bell",
                "Note Block: Snare", "Note Block: Hat", "Note Block: Bass Drum",
                "XP Orb", "Arrow Hit", "Anvil Land", "Glass Break", "Crit", "Knockback", "Strong Attack", "Weak Attack",
                "Sweep", "Trident Hit", "Shield Block", "Amethyst Chime", "Firework Blast", "Explosion", "Enderman Teleport",
                "Iron Golem Hit", "Slime Squish", "Lever Click", "Blaze Hurt" };
        private static final String[] IDS = {
                "block.note_block.pling", "block.note_block.bell", "block.note_block.harp", "block.note_block.bit", "block.note_block.chime",
                "block.note_block.xylophone", "block.note_block.banjo", "block.note_block.guitar", "block.note_block.flute", "block.note_block.cow_bell",
                "block.note_block.snare", "block.note_block.hat", "block.note_block.basedrum",
                "entity.experience_orb.pickup", "entity.arrow.hit_player", "block.anvil.land", "block.glass.break", "entity.player.attack.crit",
                "entity.player.attack.knockback", "entity.player.attack.strong", "entity.player.attack.weak",
                "entity.player.attack.sweep", "item.trident.hit", "item.shield.block", "block.amethyst_block.chime", "entity.firework_rocket.blast",
                "entity.generic.explode", "entity.enderman.teleport",
                "entity.iron_golem.damage", "entity.slime.squish", "block.lever.click", "entity.blaze.hurt" };

        public final Mode sound = mode("Sound", 0, NAMES);
        public final Num volume = num("Volume", 5, 100, 80, 1);
        public final Bool playersOnly = bool("Players Only", false);

        private final Map<Integer, Integer> lastHurt = new HashMap<>();
        private long swingAt, crossAt;
        private int crossId = -1;
        private boolean wasSwing;

        public HitSfx() { super("Hit SFX", "Plays a sound when you hit someone.", Category.COMBAT); }

        @Override public void onDisable() { lastHurt.clear(); }

        @Override public void onTick() {
            MinecraftClient mc = mc();
            var p = mc.player;
            if (p == null || mc.world == null) { lastHurt.clear(); return; }
            long now = System.currentTimeMillis();
            if (p.handSwinging && !wasSwing) swingAt = now;
            wasSwing = p.handSwinging;
            if (mc.crosshairTarget instanceof EntityHitResult ehr) { crossId = ehr.getEntity().getId(); crossAt = now; }
            if (lastHurt.size() > 256) lastHurt.clear();

            for (Entity e : mc.world.getOtherEntities(p, p.getBoundingBox().expand(8))) {
                if (!(e instanceof LivingEntity le)) continue;
                int prev = lastHurt.getOrDefault(le.getId(), 0), cur = le.hurtTime;
                lastHurt.put(le.getId(), cur);
                boolean fresh = cur > prev && cur >= le.maxHurtTime - 2;
                if (!fresh || now - swingAt > 450 || le.getId() != crossId || now - crossAt > 450) continue;
                if (playersOnly.value && !(le instanceof PlayerEntity)) continue;
                play();
            }
        }

        public void play() {
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft", IDS[sound.index]));
            if (ev == null) return;
                    if (mc().player != null) mc().player.playSound(ev, volume.f() / 100f, 1.0f);
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

        /** Called by LivingEntityMixin when the shield-break status (30) arrives. */
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

        public Atmosphere() {
            super("Atmosphere", "Tints the sky and horizon. Pick a preset or set your own colors.", Category.VISUAL);
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
            float b = blend();
            if (b <= 0f) return color;
            Atmosphere a = ModuleManager.ATMOS;
            return (color & 0xFF000000) | Gfx.mix(color & 0xFFFFFF, a.sky.rgb, a.strength.f() / 100f * b);
        }
    }

    // =====================================================================
    //  HUD Style: glass hotbar, status bars and crosshair
    // =====================================================================
    public static class HudTheme extends Module {
        public final Mode crosshair = mode("Crosshair", 1, "Dot", "Cross", "Cross + Dot");
        public final Num size = num("Crosshair Size", 2, 10, 4, 1);
        public final Num gap = num("Crosshair Gap", 0, 8, 3, 1);
        public final Bool dynamic = bool("Dynamic Crosshair", true);
        public final Bool stats = bool("Show Health / Food / Armor", true);
        public HudTheme() {
            super("HUD Style", "Replaces the vanilla hotbar, status bars and crosshair with glass versions.", Category.HUD);
            enabled = true; anim = 1f; showInList.value = false;
        }
    }
}

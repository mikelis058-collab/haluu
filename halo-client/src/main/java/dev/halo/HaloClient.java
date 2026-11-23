package dev.halo;

import dev.halo.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public class HaloClient implements ClientModInitializer {
    public static final String ID = "halo";

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Config.load();

        // Knife skins live in optional built-in packs (one per sword tier) that the Knives module switches on/off.
        FabricLoader.getInstance().getModContainer(ID).ifPresent(c -> {
            for (String tier : new String[] { "wood", "stone", "iron", "golden", "diamond", "netherite" })
                ResourceManagerHelper.registerBuiltinResourcePack(Identifier.of(ID, "knives_" + tier), c, ResourcePackActivationType.NORMAL);
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.of(ID, "hud"),
                (ctx, tickCounter) -> Hud.render(ctx));

        ClientTickEvents.END_CLIENT_TICK.register(c -> ModuleManager.tick());

        // HUD restyle (also by reflection so the build can exclude HudStyle.java if needed).
        try {
            Class.forName("dev.halo.HudStyle").getMethod("register").invoke(null);
        } catch (Throwable ignored) {
            System.out.println("[Halo] HUD style not loaded.");
        }

        // Hats and Wings are registered by reflection so the build can exclude either file if needed.
        try {
            Class.forName("dev.halo.render.HatFeature").getMethod("register").invoke(null);
        } catch (Throwable ignored) {
            System.out.println("[Halo] Hats renderer not loaded.");
        }
        try {
            Class.forName("dev.halo.render.WingFeature").getMethod("register").invoke(null);
        } catch (Throwable ignored) {
            System.out.println("[Halo] Wings renderer not loaded.");
        }
    }
}

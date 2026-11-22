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

        // Knife skins: one tiny pack per sword type, switched on/off by the Knives module.
        FabricLoader.getInstance().getModContainer(ID).ifPresent(c -> {
            for (String item : dev.halo.module.Modules.Knives.ITEMS)
                ResourceManagerHelper.registerBuiltinResourcePack(Identifier.of(ID, "knives_" + item), c, ResourcePackActivationType.NORMAL);
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

        // Hats are registered by reflection so the build can exclude HatFeature.java if needed.
        try {
            Class.forName("dev.halo.render.HatFeature").getMethod("register").invoke(null);
        } catch (Throwable ignored) {
            System.out.println("[Halo] Hats renderer not loaded.");
        }
    }
}

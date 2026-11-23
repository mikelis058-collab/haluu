package dev.halo.mixin;

import dev.halo.module.Modules;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the vanilla orange fire overlay while the Fire module is active, so only our recolored
 * tint (drawn in Hud.java) shows. require = 0 because this private method's exact parameter list
 * has changed across Minecraft versions; if it doesn't match here, the vanilla orange overlay will
 * still show underneath our tint instead of being replaced.
 */
@Mixin(InGameOverlayRenderer.class)
public class FireOverlayMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void halo$cancelFire(CallbackInfo ci) {
        if (Modules.Fire.active()) ci.cancel();
    }
}

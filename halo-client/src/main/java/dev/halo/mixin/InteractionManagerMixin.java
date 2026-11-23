package dev.halo.mixin;

import dev.halo.module.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HitSfx: plays a custom sound after every attack this client sends, manual or TriggerBot. */
@Mixin(ClientPlayerInteractionManager.class)
public class InteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("TAIL"), require = 0)
    private void halo$hitSfx(PlayerEntity player, Entity target, CallbackInfo ci) {
        Modules.HitSfx.onHit();
    }
}

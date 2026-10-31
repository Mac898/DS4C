package au.mltech.ds4c.mixin;

import au.mltech.ds4c.haptics.SoundPcmCache;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sounds are reloaded with resource packs; drop decoded haptic PCM so it can't go stale.
 * The handler takes no target arguments because {@code SoundManager.Preparations} is protected.
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @Inject(method = "apply(Lnet/minecraft/client/sounds/SoundManager$Preparations;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void ds4c$onReload(CallbackInfo ci) {
        SoundPcmCache.clear();
    }

    /** RETURN: the sound events are registered now, so their variants can be decoded ahead of use. */
    @Inject(method = "apply(Lnet/minecraft/client/sounds/SoundManager$Preparations;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"))
    private void ds4c$afterReload(CallbackInfo ci) {
        SoundPcmCache.preloadMapped((SoundManager) (Object) this);
    }
}

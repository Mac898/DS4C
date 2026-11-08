package au.mltech.ds4c.mixin;

import au.mltech.ds4c.events.Ds4cEvents;
import au.mltech.ds4c.haptics.SoundHaptics;
import au.mltech.ds4c.speaker.ControllerSpeaker;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    /**
     * Just after the sound event has been resolved to the variant that will play, and empty or
     * unknown sounds have been rejected.
     */
    @Inject(
            method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/Sound;getAttenuationDistance(F)F")
    )
    private void ds4c$onPlay(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        Ds4cEvents.onSoundPlayed(instance);
        Sound sound = instance.getSound();
        if (sound != null) SoundHaptics.onSoundPlayed(instance, sound);
    }

    /** After subtitles are notified: sounds moved to the controller speaker skip the main mix. */
    @Inject(
            method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"),
            cancellable = true
    )
    private void ds4c$routeToSpeaker(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        Sound sound = instance.getSound();
        if (sound != null && ControllerSpeaker.onSoundPlayed(instance, sound)) {
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        }
    }
}

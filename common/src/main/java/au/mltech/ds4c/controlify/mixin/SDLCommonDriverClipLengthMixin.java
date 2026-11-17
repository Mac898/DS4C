package au.mltech.ds4c.controlify.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.isxander.controlify.controller.haptic.CompleteSoundData;
import dev.isxander.controlify.driver.sdl.SDLCommonDriver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import javax.sound.sampled.AudioFormat;

/**
 * Controlify works out a haptic clip's length in ticks with chained integer division, so any clip
 * shorter than a second gets 0 and its stream counts as free at once. The next clip of the same
 * format is then queued behind it instead of mixed with it. Rounds the length up instead.
 */
@Mixin(SDLCommonDriver.class)
public abstract class SDLCommonDriverClipLengthMixin {
    @Unique
    private static int ds4c$clipTicks(CompleteSoundData sound, int ticks) {
        AudioFormat format = sound.format();
        double bytesPerSecond = format.getSampleRate() * format.getChannels() * (format.getSampleSizeInBits() / 8);
        if (bytesPerSecond <= 0) return ticks;
        return Math.max(ticks, Math.max(1, (int) Math.ceil(sound.audio().length * 20.0 / bytesPerSecond)));
    }

    @ModifyArg(
            method = "playHaptic",
            at = @At(value = "INVOKE", target = "Ldev/isxander/controlify/driver/sdl/SDLCommonDriver$AudioStreamHandle;queueAudio([BI)V"),
            index = 1
    )
    private int ds4c$clipTicksQueued(int ticks, @Local(argsOnly = true) CompleteSoundData sound) {
        return ds4c$clipTicks(sound, ticks);
    }

    @ModifyArg(
            method = "playHaptic",
            at = @At(value = "INVOKE", target = "Ldev/isxander/controlify/driver/sdl/SDLCommonDriver$AudioStreamHandle;createWithAudio(Ldev/isxander/sdl/Sdl;Ldev/isxander/sdl/SdlAudioDeviceId;Ldev/isxander/sdl/SdlAudioSpec;Ldev/isxander/sdl/SdlAudioSpec;[BI)Ldev/isxander/controlify/driver/sdl/SDLCommonDriver$AudioStreamHandle;"),
            index = 5
    )
    private int ds4c$clipTicksCreated(int ticks, @Local(argsOnly = true) CompleteSoundData sound) {
        return ds4c$clipTicks(sound, ticks);
    }
}

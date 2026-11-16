package au.mltech.ds4c.controlify.mixin;

import dev.isxander.controlify.driver.sdl.SDLCommonDriver;
import dev.isxander.sdl.Sdl;
import dev.isxander.sdl.SdlAudioDeviceId;
import dev.isxander.sdl.SdlHints;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every DualSense's audio device has the same name, so the stream SDL opens on it is named after
 * the controller and its serial (the Bluetooth address). An OS audio policy can then route each
 * controller's stream to its own pad (DS4L does this on Linux).
 */
@Mixin(SDLCommonDriver.class)
public abstract class SDLCommonDriverStreamNameMixin {
    @Shadow
    @Final
    protected Sdl sdl;
    @Shadow
    @Final
    protected String serial;
    @Shadow
    @Final
    protected String name;

    @Unique
    private boolean ds4c$streamNamed;

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Ldev/isxander/sdl/SdlAudio;SDL_OpenAudioDevice(Ldev/isxander/sdl/SdlAudioDeviceId;Ldev/isxander/sdl/SdlAudioSpec;)Ldev/isxander/sdl/SdlAudioDeviceId;"),
            index = 0
    )
    private SdlAudioDeviceId ds4c$nameStream(SdlAudioDeviceId device) {
        if (serial != null) {
            sdl.hints().SDL_SetHint(SdlHints.SDL_HINT_AUDIO_DEVICE_STREAM_NAME, name + " HD haptics (" + serial + ")");
            ds4c$streamNamed = true;
        }
        return device;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ds4c$resetStreamName(CallbackInfo ci) {
        if (ds4c$streamNamed) {
            sdl.hints().SDL_ResetHint(SdlHints.SDL_HINT_AUDIO_DEVICE_STREAM_NAME);
            ds4c$streamNamed = false;
        }
    }
}

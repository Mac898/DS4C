package au.mltech.ds4c.controlify.mixin;

import au.mltech.ds4c.controlify.SpeakerOutput;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.haptic.HDHapticComponent;
import dev.isxander.controlify.driver.sdl.SDLCommonDriver;
import dev.isxander.sdl.Sdl;
import dev.isxander.sdl.SdlAudioDeviceId;
import dev.isxander.sdl.SdlAudioSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the DualSense audio device Controlify opened to DS4C's speaker output. */
@Mixin(SDLCommonDriver.class)
public abstract class SDLCommonDriverSpeakerMixin {
    @Shadow
    @Final
    protected Sdl sdl;
    @Shadow
    protected SdlAudioDeviceId dualsenseAudioDev;
    @Shadow
    protected SdlAudioSpec dualsenseAudioSpec;
    @Shadow
    protected HDHapticComponent hdHapticComponent;

    @Inject(method = "addComponents", at = @At("TAIL"))
    private void ds4c$attachSpeaker(ControllerEntity controller, CallbackInfo ci) {
        if (hdHapticComponent != null && dualsenseAudioDev != null && dualsenseAudioSpec != null) {
            SpeakerOutput.attach(hdHapticComponent, sdl, dualsenseAudioDev, dualsenseAudioSpec);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void ds4c$detachSpeaker(CallbackInfo ci) {
        if (hdHapticComponent != null) SpeakerOutput.detach(hdHapticComponent);
    }
}

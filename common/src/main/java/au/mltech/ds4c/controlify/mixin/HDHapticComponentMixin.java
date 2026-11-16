package au.mltech.ds4c.controlify.mixin;

import au.mltech.ds4c.controlify.Ds4cHdHaptic;
import dev.isxander.controlify.config.settings.profile.HDHapticSettings;
import dev.isxander.controlify.controller.haptic.CompleteSoundData;
import dev.isxander.controlify.controller.haptic.HDHapticComponent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.sound.sampled.AudioFormat;
import java.util.function.Consumer;

/** PCM playback on Controlify versions without HDHapticsApi. */
@Mixin(HDHapticComponent.class)
public abstract class HDHapticComponentMixin implements Ds4cHdHaptic {
    @Shadow
    private Consumer<CompleteSoundData> playHapticConsumer;

    @Shadow
    public abstract HDHapticSettings settings();

    @Override
    public boolean ds4c$canPlay() {
        return settings().enabled && playHapticConsumer != null;
    }

    @Override
    public void ds4c$playPcm(byte[] pcm, AudioFormat format) {
        if (pcm.length == 0 || !ds4c$canPlay()) return;
        CompleteSoundData data = new CompleteSoundData(pcm.clone(), format);
        // the driver may only be used from the client thread
        Minecraft.getInstance().execute(() -> {
            Consumer<CompleteSoundData> consumer = playHapticConsumer;
            if (consumer != null) consumer.accept(data);
        });
    }
}

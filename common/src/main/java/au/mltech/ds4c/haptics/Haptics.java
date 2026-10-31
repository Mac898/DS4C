package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import dev.isxander.controlify.api.haptic.HDHapticsApi;
import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioFormat;

/**
 * DS4C's single path to the DualSense HD haptics output, through Controlify's
 * {@link HDHapticsApi}. Every play goes through {@link #play(byte[], AudioFormat, float)}, which
 * applies DS4C's own switch and master intensity once; Controlify handles thread delivery.
 */
public final class Haptics {
    private static HapticTickHandler tickHandler = new HapticTickHandler();
    private static boolean wasActive = false;

    private Haptics() {
    }

    /** True when the current controller can play DS4C haptics right now. */
    public static boolean isAvailable() {
        return Ds4cSettings.hapticsEnabled() && HDHapticsApi.isAvailable();
    }

    public static boolean isAvailable(FeedbackCategory category) {
        return Ds4cSettings.isCategoryEnabled(category) && isAvailable();
    }

    public static void play(HapticWaveform waveform) {
        play(waveform.pcm(), waveform.format(), 1f);
    }

    public static void play(HapticWaveform waveform, float gain) {
        play(waveform.pcm(), waveform.format(), gain);
    }

    /** Plays 16-bit little-endian PCM at {@code gain} times the master intensity. Any thread. */
    public static void play(byte[] pcm, AudioFormat format, float gain) {
        if (!isAvailable()) return;
        float total = gain * Ds4cSettings.masterIntensity();
        if (total <= 0.001f) return;
        HDHapticsApi.playPcm(HapticWaveform.scaleSamples(pcm, total), format);
    }

    /**
     * Start of every client tick. Haptics run only in a world, unpaused, with an HD-capable
     * controller and haptics enabled; leaving that state clears every loop and resets the tick
     * handler so nothing keeps playing.
     */
    public static void clientTick(Minecraft minecraft) {
        boolean active = minecraft.player != null
                && minecraft.level != null
                && !minecraft.isPaused()
                && isAvailable();
        if (!active) {
            if (wasActive) reset();
            wasActive = false;
            return;
        }
        wasActive = true;

        tickHandler.tick(minecraft.player);
        HapticLoops.tick();
        SoundHaptics.tick();
    }

    private static void reset() {
        HapticLoops.clear();
        SoundHaptics.clear();
        tickHandler = new HapticTickHandler();
    }
}

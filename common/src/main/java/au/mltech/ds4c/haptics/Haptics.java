package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import dev.isxander.controlify.api.haptic.HDHapticsApi;
import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioFormat;

/**
 * DS4C's single path to the DualSense HD haptics output, through Controlify's
 * {@link HDHapticsApi}. Every play goes through here, which gates on the category, runs the
 * {@link HapticBudget} (priority, ducking, layer cap), applies the master intensity once and pans
 * between the two grip actuators. Client thread only.
 */
public final class Haptics {
    /** Base gain for DS4C's synthesised effects (tuned in testing: +20%), times the synthesis intensity setting. */
    static final float SYNTH_GAIN = 1.2f;

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

    /** A one-off effect (an event). Effects of the action and danger tiers duck lower tiers. */
    public static void play(FeedbackCategory category, HapticWaveform waveform) {
        play(category, waveform, 1f, 0f);
    }

    public static void play(FeedbackCategory category, HapticWaveform waveform, float gain) {
        play(category, waveform, gain, 0f);
    }

    /** @param pan -1 = left grip only, 0 = both, 1 = right grip only */
    public static void play(FeedbackCategory category, HapticWaveform waveform, float gain, float pan) {
        submit(category, HapticTier.of(category), waveform.pcm(), waveform.format(), gain, pan, false);
    }

    /**
     * A repeating cue (heartbeat, compass ticks, wind chunks, loop chunks). Ranked like
     * {@link #play} but never ducks anything else, so a cue that keeps firing can't hold the
     * other effects down.
     */
    public static void playSustained(FeedbackCategory category, HapticWaveform waveform, float gain, float pan) {
        submit(category, HapticTier.of(category), waveform.pcm(), waveform.format(), gain, pan, true);
    }

    /** A decoded game sound (16-bit little-endian PCM, mono or stereo). */
    static void playSound(FeedbackCategory category, byte[] pcm, AudioFormat format, float gain) {
        submit(category, HapticTier.of(category), pcm, format, gain, 0f, false);
    }

    private static void submit(FeedbackCategory category, HapticTier tier, byte[] pcm, AudioFormat format,
                               float gain, float pan, boolean sustained) {
        if (!isAvailable(category) || pcm.length == 0) return;
        float total = gain * Ds4cSettings.masterIntensity() * Ds4cSettings.categoryIntensity(category);
        if (total <= 0.001f) return;

        int frameBytes = Math.max(1, format.getFrameSize());
        int durationMs = (int) (1000L * (pcm.length / frameBytes) / Math.max(1f, format.getSampleRate()));
        total *= HapticBudget.admit(tier, durationMs, sustained);
        if (total <= 0.001f) return;

        if (format.getChannels() == 1 && Math.abs(pan) > 0.01f) {
            HDHapticsApi.playPcm(HapticWaveform.panToStereo(pcm, total, pan), HapticWaveform.stereoFormat(format));
        } else {
            HDHapticsApi.playPcm(HapticWaveform.scaleSamples(pcm, total), format);
        }
    }

    /**
     * Start of every client tick. Haptics run only in a world, unpaused, with an HD-capable
     * controller and haptics enabled; leaving that state clears every loop and resets the tick
     * handler so nothing keeps playing.
     */
    public static void clientTick(Minecraft minecraft) {
        HapticsSetupCheck.tick(minecraft);
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
        HapticBudget.clear();
        tickHandler = new HapticTickHandler();
    }
}

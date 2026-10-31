package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.haptic.CompleteSoundData;
import dev.isxander.controlify.controller.haptic.HDHapticComponent;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * DS4C's single path to the DualSense HD haptics output.
 * <p>
 * Controlify 3.5 has no public API for raw PCM: {@link HDHapticComponent} only plays .ogg files.
 * Its driver registers a {@code Consumer<CompleteSoundData>} in a private field, which we read by
 * reflection. If that field ever changes, DS4C haptics switch off with a warning instead of
 * crashing. Replace this with the HD haptics API once Controlify has one.
 * <p>
 * Every play goes through {@link #play(byte[], AudioFormat, float)}, which applies the master
 * intensity once and delivers on the client thread.
 */
public final class Haptics {
    private static final @Nullable Field CONSUMER_FIELD = findConsumerField();

    private static HapticTickHandler tickHandler = new HapticTickHandler();
    private static boolean wasActive = false;

    private Haptics() {
    }

    private static @Nullable Field findConsumerField() {
        try {
            Field field = HDHapticComponent.class.getDeclaredField("playHapticConsumer");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Constants.LOG.warn("DS4C HD haptics disabled: Controlify's HDHapticComponent has no playHapticConsumer field", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Consumer<CompleteSoundData> output() {
        if (CONSUMER_FIELD == null || !Ds4cSettings.hapticsEnabled()) return null;
        HDHapticComponent component = ControlifyApi.get().getCurrentController()
                .flatMap(ControllerEntity::hdHaptics)
                .orElse(null);
        if (component == null || !component.settings().enabled) return null;
        try {
            return (Consumer<CompleteSoundData>) CONSUMER_FIELD.get(component);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** True when the current controller can play DS4C haptics right now. */
    public static boolean isAvailable() {
        return output() != null;
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
        Consumer<CompleteSoundData> output = output();
        if (output == null) return;
        float total = gain * Ds4cSettings.masterIntensity();
        if (total <= 0.001f) return;

        byte[] scaled = HapticWaveform.scaleSamples(pcm, total);
        CompleteSoundData data = new CompleteSoundData(scaled, format);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            output.accept(data);
        } else {
            // The driver's audio stream list isn't thread-safe.
            minecraft.execute(() -> output.accept(data));
        }
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

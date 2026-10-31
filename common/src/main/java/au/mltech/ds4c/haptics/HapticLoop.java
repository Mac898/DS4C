package au.mltech.ds4c.haptics;

import au.mltech.ds4c.FeedbackCategory;

import java.util.function.Supplier;

/**
 * A continuous haptic bed (rain, water, rides) played as back-to-back chunks. Each chunk comes
 * fresh from the generator, so noise and random accents never repeat exactly, and short fades at
 * the chunk ends hide the seams.
 * <p>
 * Steady touch fades from awareness within seconds to minutes (docs/haptics-review.md,
 * guideline 8), so a loop whose level hasn't changed for {@link #HABITUATION_START_TICKS} eases
 * down to {@link #HABITUATION_FLOOR} of its level. Any marked change ({@link #accent()} or a level
 * change of 25% or more) brings it back to full.
 */
public final class HapticLoop {
    static final int HABITUATION_START_TICKS = 20 * 20;
    static final int HABITUATION_FADE_TICKS = 10 * 20;
    static final float HABITUATION_FLOOR = 0.5f;
    private static final int FADE_MS = 10;

    private final FeedbackCategory category;
    private final Supplier<HapticWaveform> generator;
    private final int chunkTicks;
    private float amplitude = 1f;
    private float pan = 0f;
    private int ticksSinceAccent = 0;
    /** Ticks of audio currently buffered downstream. */
    private int ticksRemaining = 0;

    /**
     * @param generator produces one chunk; called for every chunk. Chunks should be a whole
     *                  number of ticks long (multiples of 50 ms) so they queue back to back.
     */
    public HapticLoop(FeedbackCategory category, Supplier<HapticWaveform> generator) {
        this.category = category;
        this.generator = generator;
        this.chunkTicks = Math.max(1, generator.get().durationMs() / 50);
    }

    public FeedbackCategory category() {
        return category;
    }

    public void setAmplitude(float amp) {
        float next = Math.max(0f, Math.min(1f, amp));
        if (Math.abs(next - amplitude) >= 0.25f * Math.max(amplitude, 0.05f)) accent();
        this.amplitude = next;
    }

    public float amplitude() {
        return amplitude;
    }

    public void setPan(float pan) {
        this.pan = pan;
    }

    /** Something changed (a bump, a gust, a new state): play at full level again. */
    public void accent() {
        ticksSinceAccent = 0;
    }

    /** The level this loop plays at right now, after habituation. */
    float effectiveAmplitude() {
        float faded = Math.min(1f, Math.max(0f, (ticksSinceAccent - HABITUATION_START_TICKS) / (float) HABITUATION_FADE_TICKS));
        return amplitude * (1f - (1f - HABITUATION_FLOOR) * faded);
    }

    /** Advances one tick. Returns true if the next chunk should be queued now. */
    boolean tick() {
        ticksSinceAccent++;
        if (ticksRemaining > 0) ticksRemaining--;
        return ticksRemaining <= 0;
    }

    /** Plays the next chunk, or skips it (silence) if {@code audible} is false. */
    void queueNext(boolean audible) {
        ticksRemaining += chunkTicks;
        float amp = effectiveAmplitude();
        if (!audible || amp <= 0.001f) return;
        HapticWaveform chunk = HapticWaveform.withASR(generator.get(), FADE_MS, Math.max(0, chunkTicks * 50 - 2 * FADE_MS), FADE_MS);
        Haptics.playSustained(category, chunk, amp, pan);
    }
}

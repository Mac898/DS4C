package au.mltech.ds4c.haptics;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides, as each haptic is about to play, whether it plays and how loud, so overlapping effects
 * stay readable:
 * <ul>
 *     <li>At most {@link #MAX_LAYERS} effects play at once. A new effect that ranks no higher than
 *     everything already playing is dropped.</li>
 *     <li>While an action or danger effect plays, new effects of a lower tier play at
 *     {@link #DUCK_GAIN}. Sustained cues (heartbeat, loops, wind) don't duck anything, so they
 *     can't hold everything else down.</li>
 * </ul>
 * Effects are queued as finished clips, so the budget applies when an effect starts; a clip that
 * is already playing isn't changed. Client thread only.
 */
final class HapticBudget {
    static final int MAX_LAYERS = 3;
    static final float DUCK_GAIN = 0.4f;

    private record Voice(HapticTier tier, boolean ducks, long endNanos) {
    }

    private static final List<Voice> ACTIVE = new ArrayList<>();

    private HapticBudget() {
    }

    /**
     * Registers an effect about to play and returns its gain multiplier, or 0 if it shouldn't play.
     *
     * @param sustained a repeating cue or loop chunk: never ducks other effects
     */
    static float admit(HapticTier tier, int durationMs, boolean sustained) {
        long now = System.nanoTime();
        ACTIVE.removeIf(v -> v.endNanos <= now);

        if (ACTIVE.size() >= MAX_LAYERS) {
            int lowest = Integer.MAX_VALUE;
            for (Voice v : ACTIVE) lowest = Math.min(lowest, v.tier.ordinal());
            if (tier.ordinal() <= lowest) return 0f;
        }

        float gain = 1f;
        for (Voice v : ACTIVE) {
            if (v.ducks && v.tier.ordinal() > tier.ordinal()) {
                gain = DUCK_GAIN;
                break;
            }
        }

        ACTIVE.add(new Voice(tier, !sustained && tier.ducksLowerTiers(), now + durationMs * 1_000_000L));
        return gain;
    }

    static void clear() {
        ACTIVE.clear();
    }
}

package au.mltech.ds4c;

import java.util.EnumMap;
import java.util.Map;

/**
 * Runtime settings read by the fact contributors. Not persisted yet; the config phase will load
 * and save these and add a settings screen.
 */
public final class Ds4cSettings {
    private static final Map<FeedbackCategory, Boolean> CATEGORIES = new EnumMap<>(FeedbackCategory.class);

    private static volatile boolean triggerEffectsEnabled = true;
    private static volatile boolean hapticsEnabled = true;
    /** Footstep and small-landing taps (frequent, so some players want them off). */
    private static volatile boolean footstepsEnabled = true;
    /** Adaptive trigger strength in percent: 100, 75 or 50. */
    private static volatile int triggerStrength = 100;
    /** Scales every DS4C HD haptic, applied once in {@code Haptics.play}. */
    private static volatile float masterIntensity = 1.0f;
    /** Additionally scales synthesised waveforms (not passed-through game sounds). */
    private static volatile float synthesisIntensity = 1.0f;
    /** Sounds within this many blocks play at full strength. */
    private static volatile float falloffNearBlocks = 0.0f;
    /** Sounds fade to silence by this many blocks (each mapping can cap it lower). */
    private static volatile float falloffFarBlocks = 64.0f;

    private Ds4cSettings() {
    }

    public static boolean triggerEffectsEnabled() {
        return triggerEffectsEnabled;
    }

    public static void setTriggerEffectsEnabled(boolean enabled) {
        triggerEffectsEnabled = enabled;
    }

    public static boolean hapticsEnabled() {
        return hapticsEnabled;
    }

    public static void setHapticsEnabled(boolean enabled) {
        hapticsEnabled = enabled;
    }

    public static boolean footstepsEnabled() {
        return footstepsEnabled;
    }

    public static void setFootstepsEnabled(boolean enabled) {
        footstepsEnabled = enabled;
    }

    public static int triggerStrength() {
        return triggerStrength;
    }

    /** Rounds to the nearest supported level (100, 75, 50). */
    public static void setTriggerStrength(int percent) {
        triggerStrength = percent >= 88 ? 100 : percent >= 63 ? 75 : 50;
    }

    public static float masterIntensity() {
        return masterIntensity;
    }

    public static void setMasterIntensity(float intensity) {
        masterIntensity = Math.max(0f, intensity);
    }

    public static float synthesisIntensity() {
        return synthesisIntensity;
    }

    public static void setSynthesisIntensity(float intensity) {
        synthesisIntensity = Math.max(0f, intensity);
    }

    public static float falloffNearBlocks() {
        return falloffNearBlocks;
    }

    public static float falloffFarBlocks() {
        return falloffFarBlocks;
    }

    public static void setFalloff(float nearBlocks, float farBlocks) {
        falloffNearBlocks = Math.max(0f, nearBlocks);
        falloffFarBlocks = Math.max(0.5f, farBlocks);
    }

    public static boolean isCategoryEnabled(FeedbackCategory category) {
        synchronized (CATEGORIES) {
            return CATEGORIES.getOrDefault(category, true);
        }
    }

    public static void setCategoryEnabled(FeedbackCategory category, boolean enabled) {
        synchronized (CATEGORIES) {
            CATEGORIES.put(category, enabled);
        }
    }
}

package au.mltech.ds4c;

import java.util.EnumMap;
import java.util.Map;

/**
 * Runtime settings, loaded and saved by {@code Ds4cConfig}. Every on/off is its strength at 0:
 * trigger effects, HD haptics, each category and footsteps.
 */
public final class Ds4cSettings {
    /**
     * Per-category strength, on top of the master and synthesis intensities. 0 switches the
     * category off, trigger effects included.
     */
    private static final Map<FeedbackCategory, Float> CATEGORY_INTENSITY = new EnumMap<>(FeedbackCategory.class);

    /** Footstep and small-landing taps (frequent, so some players want them off); 0 is off. */
    private static volatile float footstepIntensity = 1.0f;
    /** Bow and trident: pressing Use through the resistance to the bottom looses the shot. */
    private static volatile boolean pullThroughToShoot = true;
    /** Adaptive trigger strength in percent: 100, 75 or 50, or 0 for trigger effects off. */
    private static volatile int triggerStrength = 100;
    /** Scales every DS4C HD haptic, applied once in {@code Haptics.play}; 0 is HD haptics off. */
    private static volatile float masterIntensity = 1.0f;
    /** Additionally scales synthesised waveforms (not passed-through game sounds). */
    private static volatile float synthesisIntensity = 1.0f;
    /** Controller speaker volume; 0 keeps every sound on the main mix. */
    private static volatile float speakerVolume = 1.0f;
    /** How late the controller speaker plays relative to the game's audio (Bluetooth audio bridges). */
    private static volatile int speakerLatencyMs = 0;
    /** Sounds within this many blocks play at full strength. */
    private static volatile float falloffNearBlocks = 0.0f;
    /** Sounds fade to silence by this many blocks (each mapping can cap it lower). */
    private static volatile float falloffFarBlocks = 64.0f;

    private Ds4cSettings() {
    }

    public static boolean triggerEffectsEnabled() {
        return triggerStrength > 0;
    }

    public static boolean hapticsEnabled() {
        return masterIntensity > 0.001f;
    }

    public static boolean footstepsEnabled() {
        return footstepIntensity > 0.001f;
    }

    public static float footstepIntensity() {
        return footstepIntensity;
    }

    public static void setFootstepIntensity(float intensity) {
        footstepIntensity = Math.max(0f, intensity);
    }

    public static boolean pullThroughToShoot() {
        return pullThroughToShoot;
    }

    public static void setPullThroughToShoot(boolean enabled) {
        pullThroughToShoot = enabled;
    }

    public static int triggerStrength() {
        return triggerStrength;
    }

    /** Rounds to the nearest supported level (100, 75, 50, or 0 for off). */
    public static void setTriggerStrength(int percent) {
        triggerStrength = percent >= 88 ? 100 : percent >= 63 ? 75 : percent >= 25 ? 50 : 0;
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

    public static float speakerVolume() {
        return speakerVolume;
    }

    public static void setSpeakerVolume(float volume) {
        speakerVolume = Math.max(0f, volume);
    }

    public static int speakerLatencyMs() {
        return speakerLatencyMs;
    }

    public static void setSpeakerLatencyMs(int ms) {
        speakerLatencyMs = Math.max(0, Math.min(500, ms));
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
        return categoryIntensity(category) > 0.001f;
    }

    public static float categoryIntensity(FeedbackCategory category) {
        synchronized (CATEGORY_INTENSITY) {
            return CATEGORY_INTENSITY.getOrDefault(category, 1.0f);
        }
    }

    public static void setCategoryIntensity(FeedbackCategory category, float intensity) {
        synchronized (CATEGORY_INTENSITY) {
            CATEGORY_INTENSITY.put(category, Math.max(0f, intensity));
        }
    }
}

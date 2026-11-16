package au.mltech.ds4c.haptics;

import au.mltech.ds4c.FeedbackCategory;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Maps sound ids to a passthrough {@link HapticProfile} and/or a synthesised overlay. Sounds
 * without an entry produce no haptics. Exact ids win over suffix rules, which let one entry cover
 * every material variant of a sound.
 */
public final class HapticSoundRegistry {
    private static final Map<Identifier, HapticProfile> EXACT = new HashMap<>();
    private static final List<RuleEntry> RULES = new ArrayList<>();
    private static final Map<Identifier, OverlayEntry> OVERLAYS = new HashMap<>();
    private static final List<OverlayRule> OVERLAY_RULES = new ArrayList<>();

    private record OverlayRule(String pathSuffix, OverlayEntry overlay) {}

    private record RuleEntry(Predicate<Identifier> matcher, HapticProfile profile) {}

    /**
     * A synthesised waveform played alongside any passthrough. The supplier runs on every play, so
     * the waveform can vary.
     *
     * @param requiresLocalSource only fire for sounds within about 0.5 blocks of the local player
     * @param maxDistance         when above 0, amplitude falls off linearly to silence at this
     *                            distance; 0 means no distance gate
     */
    public record OverlayEntry(Supplier<HapticWaveform> waveform, FeedbackCategory category,
                               boolean requiresLocalSource, float maxDistance) {}

    private HapticSoundRegistry() {}

    public static void register(Identifier soundLocation, HapticProfile profile) {
        EXACT.put(soundLocation, profile);
    }

    public static void register(SoundEvent event, HapticProfile profile) {
        register(event.location(), profile);
    }

    /** Matches every {@code minecraft} sound whose path ends with {@code pathSuffix}. */
    public static void registerSuffix(String pathSuffix, HapticProfile profile) {
        RULES.add(new RuleEntry(
                id -> "minecraft".equals(id.getNamespace()) && id.getPath().endsWith(pathSuffix),
                profile
        ));
    }

    public static void registerRule(Predicate<Identifier> matcher, HapticProfile profile) {
        RULES.add(new RuleEntry(matcher, profile));
    }

    /** An overlay with no distance gate. */
    public static void registerOverlay(Identifier soundLocation, FeedbackCategory category, Supplier<HapticWaveform> overlay) {
        OVERLAYS.put(soundLocation, new OverlayEntry(overlay, category, false, 0f));
    }

    public static void registerOverlay(SoundEvent event, FeedbackCategory category, Supplier<HapticWaveform> overlay) {
        registerOverlay(event.location(), category, overlay);
    }

    /** An overlay that only fires for sounds at the local player. */
    public static void registerLocalOverlay(SoundEvent event, FeedbackCategory category, Supplier<HapticWaveform> overlay) {
        OVERLAYS.put(event.location(), new OverlayEntry(overlay, category, true, 0f));
    }

    /** An overlay that fades linearly with distance, silent beyond {@code maxDistance}. */
    public static void registerSpatialOverlay(SoundEvent event, FeedbackCategory category, float maxDistance,
                                              Supplier<HapticWaveform> overlay) {
        OVERLAYS.put(event.location(), new OverlayEntry(overlay, category, false, maxDistance));
    }

    /** A distance-gated overlay for every {@code minecraft} sound whose path ends with {@code pathSuffix}. */
    public static void registerSuffixOverlay(String pathSuffix, FeedbackCategory category, float maxDistance,
                                             Supplier<HapticWaveform> overlay) {
        OVERLAY_RULES.add(new OverlayRule(pathSuffix, new OverlayEntry(overlay, category, false, maxDistance)));
    }

    public static @Nullable OverlayEntry getOverlay(Identifier soundLocation) {
        OverlayEntry exact = OVERLAYS.get(soundLocation);
        if (exact != null || !"minecraft".equals(soundLocation.getNamespace())) return exact;
        for (OverlayRule rule : OVERLAY_RULES) {
            if (soundLocation.getPath().endsWith(rule.pathSuffix())) return rule.overlay();
        }
        return null;
    }

    public static @Nullable HapticProfile get(Identifier soundLocation) {
        HapticProfile exact = EXACT.get(soundLocation);
        if (exact != null) return exact;
        for (RuleEntry rule : RULES) {
            if (rule.matcher.test(soundLocation)) return rule.profile;
        }
        return null;
    }

    /** Every sound with an exact passthrough mapping or a synth overlay (suffix rules aren't listable). */
    public static java.util.Set<Identifier> mappedSounds() {
        java.util.Set<Identifier> ids = new java.util.TreeSet<>(EXACT.keySet());
        ids.addAll(OVERLAYS.keySet());
        return ids;
    }

    public static Map<Identifier, HapticProfile> view() {
        return Collections.unmodifiableMap(EXACT);
    }
}

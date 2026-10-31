package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Ds4cSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Game sounds to haptics: plays the decoded sound itself ("passthrough") for sounds mapped in
 * {@link HapticSoundRegistry}, plus any synthesised overlay, scaled by distance.
 */
public final class SoundHaptics {
    /** Minimum ticks between passthroughs of the same sound event, so bursts don't stack up. */
    private static final int COOLDOWN_TICKS = 3;
    private static final Map<Identifier, Integer> COOLDOWNS = new HashMap<>();

    private SoundHaptics() {
    }

    /**
     * Called from {@code SoundEngine.play} once the variant to play is known.
     *
     * @param sound the variant actually being played, so the haptic matches what the player hears
     */
    public static void onSoundPlayed(SoundInstance instance, Sound sound) {
        if (!Haptics.isAvailable()) return;
        Identifier id = instance.getIdentifier();
        LocalPlayer player = Minecraft.getInstance().player;
        float near = Ds4cSettings.falloffNearBlocks();
        float far = Ds4cSettings.falloffFarBlocks();

        HapticSoundRegistry.OverlayEntry overlay = HapticSoundRegistry.getOverlay(id);
        if (overlay != null && Ds4cSettings.isCategoryEnabled(overlay.category())
                && (!overlay.requiresLocalSource() || isLocalSource(instance, player))) {
            float gain = falloff(overlay.maxDistance(), near, far, instance, player) * Ds4cSettings.synthesisIntensity();
            HapticWaveform wave = overlay.waveform().get();
            if (wave != null && gain > 0.001f) Haptics.play(overlay.category(), wave, gain);
        }

        HapticProfile profile = HapticSoundRegistry.get(id);
        if (profile == null || sound.shouldStream()) return;
        if (!Ds4cSettings.isCategoryEnabled(profile.category())) return;
        if (COOLDOWNS.getOrDefault(id, 0) > 0) return;
        if (profile.requiresLocalSource() && !isLocalSource(instance, player)) return;

        float gain = profile.intensity() * falloff(profile.maxDistance(), near, far, instance, player);
        if (gain <= 0.001f) return;
        COOLDOWNS.put(id, COOLDOWN_TICKS);

        Minecraft minecraft = Minecraft.getInstance();
        SoundPcmCache.get(sound.getPath()).thenAccept(pcm -> pcm.ifPresent(
                data -> minecraft.execute(() -> Haptics.playSound(profile.category(), data.data(), data.format(), gain))));
    }

    static void tick() {
        Iterator<Map.Entry<Identifier, Integer>> it = COOLDOWNS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Identifier, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) it.remove();
            else entry.setValue(remaining);
        }
    }

    static void clear() {
        COOLDOWNS.clear();
    }

    /**
     * Linear falloff: full strength inside {@code near}, silent at the smaller of the mapping's
     * range and the user's {@code far}. A range of 0 means "no distance limit".
     */
    private static float falloff(float featureRange, float near, float far, SoundInstance instance, @Nullable LocalPlayer player) {
        if (featureRange <= 0f || player == null) return 1f;
        float effectiveFar = Math.min(featureRange, Math.max(0.5f, far));
        float effectiveNear = Math.min(Math.max(0f, near), Math.max(0f, effectiveFar - 0.5f));

        double dx = instance.getX() - player.getX();
        double dy = instance.getY() - player.getY();
        double dz = instance.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= effectiveNear) return 1f;
        if (dist >= effectiveFar) return 0f;
        return (float) (1.0 - (dist - effectiveNear) / (effectiveFar - effectiveNear));
    }

    private static boolean isLocalSource(SoundInstance instance, @Nullable LocalPlayer player) {
        if (player == null) return false;
        double dx = instance.getX() - player.getX();
        double dy = instance.getY() - player.getY();
        double dz = instance.getZ() - player.getZ();
        return dx * dx + dy * dy + dz * dz < 0.25;
    }
}

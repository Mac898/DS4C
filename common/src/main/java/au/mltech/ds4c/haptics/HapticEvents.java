package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Synthesised HD haptics for game events, called from DS4C's mixins on the client thread.
 * Each checks its category and scales by the synthesis intensity; {@link Haptics} applies the
 * master intensity.
 */
public final class HapticEvents {
    private HapticEvents() {
    }

    private static float intensity(FeedbackCategory category) {
        return Haptics.isAvailable(category) ? Ds4cSettings.synthesisIntensity() : 0f;
    }

    // --- movement --------------------------------------------------------------------------------

    /** Per-step thump shaped by the surface's sound type, plus a gait component. */
    public static void onFootstep(LocalPlayer player, BlockState state) {
        if (player.getVehicle() != null) return;
        float intensity = intensity(FeedbackCategory.MOVEMENT);
        if (intensity <= 0.001f) return;

        HapticWaveform body = groundBody(state, intensity);
        HapticWaveform gait = player.isSprinting()
                ? HapticWaveform.withASR(HapticWaveform.sine(50f, 65, 0.18f * intensity), 3, 30, 32)
                : HapticWaveform.withASR(HapticWaveform.sine(45f, 50, 0.05f * intensity), 2, 25, 23);
        Haptics.play(HapticWaveform.mix(body, gait));
    }

    private static HapticWaveform groundBody(BlockState state, float intensity) {
        SoundType st = state.getSoundType();
        float freq;
        float bodyAmp;
        float noiseAmp;
        int bodyMs;
        if (st == SoundType.STONE || st == SoundType.METAL || st == SoundType.NETHERITE_BLOCK
                || st == SoundType.ANCIENT_DEBRIS || st == SoundType.AMETHYST || st == SoundType.AMETHYST_CLUSTER
                || st == SoundType.COPPER || st == SoundType.DEEPSLATE || st == SoundType.DEEPSLATE_BRICKS
                || st == SoundType.TUFF || st == SoundType.CALCITE) {
            freq = 90; bodyAmp = 0.35f; noiseAmp = 0.30f; bodyMs = 50;
        } else if (st == SoundType.WOOD || st == SoundType.BAMBOO || st == SoundType.BAMBOO_WOOD
                || st == SoundType.HANGING_SIGN) {
            freq = 55; bodyAmp = 0.40f; noiseAmp = 0.12f; bodyMs = 80;
        } else if (st == SoundType.WOOL) {
            freq = 35; bodyAmp = 0.30f; noiseAmp = 0.05f; bodyMs = 60;
        } else if (st == SoundType.GRAVEL || st == SoundType.SAND) {
            freq = 28; bodyAmp = 0.18f; noiseAmp = 0.45f; bodyMs = 60;
        } else if (st == SoundType.GRASS) {
            freq = 32; bodyAmp = 0.22f; noiseAmp = 0.20f; bodyMs = 70;
        } else if (st == SoundType.SNOW || st == SoundType.POWDER_SNOW) {
            freq = 24; bodyAmp = 0.18f; noiseAmp = 0.30f; bodyMs = 80;
        } else if (st == SoundType.MUD || st == SoundType.MUDDY_MANGROVE_ROOTS) {
            freq = 22; bodyAmp = 0.45f; noiseAmp = 0.25f; bodyMs = 100;
        } else if (st == SoundType.HONEY_BLOCK || st == SoundType.SLIME_BLOCK) {
            freq = 25; bodyAmp = 0.40f; noiseAmp = 0.05f; bodyMs = 110;
        } else if (state.is(BlockTags.ICE)) {
            freq = 70; bodyAmp = 0.30f; noiseAmp = 0.35f; bodyMs = 60;
        } else {
            freq = 40; bodyAmp = 0.30f; noiseAmp = 0.18f; bodyMs = 70;
        }

        HapticWaveform body = HapticWaveform.withASR(
                HapticWaveform.sine(freq, bodyMs, bodyAmp * intensity), 3, Math.max(1, bodyMs - 20), 17);
        HapticWaveform noise = HapticWaveform.withASR(HapticWaveform.pinkNoise(45, noiseAmp * intensity), 2, 30, 13);
        return HapticWaveform.mix(body, noise);
    }

    /** Landing thump from 2+ blocks, growing with the fall; a mace adds a ground-slam crack. */
    public static void onLanding(LocalPlayer player, double fallDistance) {
        if (fallDistance < 2.0) return;
        float intensity = intensity(FeedbackCategory.MOVEMENT);
        if (intensity <= 0.001f) return;

        double normalised = Math.min(1.0, Math.max(0.0, (fallDistance - 2.0) / 16.0));
        float amp = (float) (normalised * normalised) * intensity;
        if (amp > 0.001f) {
            Haptics.play(HapticWaveform.withASR(HapticWaveform.sine(45f, 90, 0.95f * amp), 5, 25, 60));
        }

        if (fallDistance >= 3.0 && player.getMainHandItem().is(Items.MACE)) {
            float maceAmp = (float) Math.min(1.0, fallDistance / 5.0) * intensity(FeedbackCategory.COMBAT);
            if (maceAmp <= 0.001f) return;
            HapticWaveform spike = HapticWaveform.withASR(HapticWaveform.sine(28f, 200, 0.9f * maceAmp), 3, 110, 87);
            HapticWaveform crackle = HapticWaveform.withASR(HapticWaveform.brownNoise(220, 0.5f * maceAmp), 5, 90, 125);
            Haptics.play(HapticWaveform.mix(spike, crackle));
        }
    }

    // --- combat ----------------------------------------------------------------------------------

    public static void onLocalPlayerDamaged(DamageSource source) {
        float intensity = intensity(FeedbackCategory.COMBAT);
        if (intensity <= 0.001f) return;
        HapticWaveform overlay = damageOverlay(source, intensity);
        if (overlay != null) Haptics.play(overlay);
    }

    private static @Nullable HapticWaveform damageOverlay(DamageSource source, float intensity) {
        if (source.is(DamageTypes.FALL)) {
            return HapticWaveform.withASR(HapticWaveform.sine(30f, 200, 0.9f * intensity), 5, 50, 150);
        }
        if (source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)
                || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.CAMPFIRE)) {
            return HapticWaveform.pinkNoise(180, 0.7f * intensity);
        }
        if (source.is(DamageTypes.DROWN)) {
            return HapticWaveform.am(HapticWaveform.sine(40f, 300, 0.7f * intensity), HapticWaveform.sine(2f, 300, 0.5f));
        }
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.MAGIC)) {
            return HapticWaveform.am(HapticWaveform.sine(50f, 400, 0.6f * intensity), HapticWaveform.sine(4f, 400, 0.5f));
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return HapticWaveform.withASR(HapticWaveform.brownNoise(250, 1.0f * intensity), 2, 30, 220);
        }
        return null;
    }

    // --- environment -----------------------------------------------------------------------------

    /** Started mining a block the held item can't harvest: a dull scrape instead of progress. */
    public static void onIneffectiveTool() {
        float intensity = intensity(FeedbackCategory.ENVIRONMENT);
        if (intensity <= 0.001f) return;
        Haptics.play(HapticWaveform.withASR(HapticWaveform.pinkNoise(120, 0.5f * intensity), 5, 60, 55));
    }

    /** Two pink-noise transients separated by a tiny gap: a scissor snip. */
    public static void onShearsSnip() {
        float intensity = intensity(FeedbackCategory.ENVIRONMENT);
        if (intensity <= 0.001f) return;
        HapticWaveform snip1 = HapticWaveform.withASR(HapticWaveform.pinkNoise(40, 0.55f * intensity), 1, 25, 14);
        HapticWaveform gap = HapticWaveform.sine(40f, 25, 0f);
        HapticWaveform snip2 = HapticWaveform.withASR(HapticWaveform.pinkNoise(35, 0.65f * intensity), 1, 22, 12);
        Haptics.play(HapticWaveform.concat(snip1, gap, snip2));
    }

    /** Strike within 16 blocks: a sharp crack then a low rumble, fading with distance. */
    public static void onLightningStrike(LightningBolt bolt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        double dist = Math.sqrt(bolt.distanceToSqr(player));
        if (dist > 16.0) return;
        float intensity = intensity(FeedbackCategory.AMBIENT) * (float) (1.0 - dist / 16.0);
        if (intensity <= 0.001f) return;

        HapticWaveform crack = HapticWaveform.withASR(HapticWaveform.brownNoise(150, 1.0f * intensity), 1, 60, 89);
        HapticWaveform rumble = HapticWaveform.withASR(HapticWaveform.sine(35f, 600, 0.85f * intensity), 30, 250, 320);
        Haptics.play(HapticWaveform.concat(crack, rumble));
    }

    // --- items -----------------------------------------------------------------------------------

    /** Soft fishing cue (fish approaching, nibble, bobber bob). */
    public static void onFishingCue(float amplitude, float frequency, int durationMs) {
        float intensity = intensity(FeedbackCategory.ITEMS);
        if (intensity <= 0.001f) return;
        Haptics.play(HapticWaveform.withASR(
                HapticWaveform.sine(frequency, durationMs, amplitude * intensity), 3, Math.max(1, durationMs - 30), 27));
    }
}

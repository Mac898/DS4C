package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Synthesised HD haptics for game events, called from DS4C's mixins on the client thread.
 * Each checks its category and scales by the synthesis intensity; {@link Haptics} applies the
 * master intensity, priority and panning.
 * <p>
 * Voicing: energy in the 80-250 Hz band; impacts are decaying {@link HapticWaveform#thump thumps}
 * whose weight comes from amplitude and decay length;
 * textures are {@link HapticWaveform#bandNoise band-passed noise}. Frequent events are subtle,
 * rare ones get the headroom. Effects with a direction lean towards that grip.
 * <p>
 * DS4C expects Controlify's own vibration to be off (otherwise those events would be felt twice),
 * so this also covers the events Controlify rumbles for: damage, explosions, lightning, mining, landings
 * in water, slowing blocks and item breaks.
 */
public final class HapticEvents {
    /**
     * Ticks after a jump during which sprint footsteps are boosted (sprint-jumping lands about every 12).
     */
    private static final int SPRINT_JUMP_WINDOW = 15;
    private static int lastJumpTick = Integer.MIN_VALUE / 2;
    private static boolean leftFoot;

    private HapticEvents() {
    }

    private static float intensity(FeedbackCategory category) {
        return Haptics.isAvailable(category) ? Ds4cSettings.synthesisIntensity() * Haptics.SYNTH_GAIN : 0f;
    }

    /**
     * Pan towards {@code target} from the player's view: -1 fully left, 1 fully right, 0 ahead or
     * behind.
     */
    public static float panTowards(LocalPlayer player, Vec3 target) {
        Vec3 view = player.getViewVector(1f);
        double tx = target.x - player.getX(), tz = target.z - player.getZ();
        double vl = Math.sqrt(view.x * view.x + view.z * view.z), tl = Math.sqrt(tx * tx + tz * tz);
        if (vl < 1e-4 || tl < 1e-4) return 0f;
        return (float) ((view.x * tz - view.z * tx) / (vl * tl));
    }

    // --- movement --------------------------------------------------------------------------------

    public static void onJump(LocalPlayer player) {
        lastJumpTick = player.tickCount;
    }

    /**
     * Per-step tap shaped by the surface, alternating feet.
     */
    public static void onFootstep(LocalPlayer player, BlockState state) {
        if (player.getVehicle() != null) return;
        boolean sprintJumping = player.isSprinting() && player.tickCount - lastJumpTick <= SPRINT_JUMP_WINDOW;
        footstep(state, player.isSprinting(), sprintJumping ? 1.6f : 1f);
    }

    /**
     * One footstep on {@code state}. Also used by the effect gallery.
     */
    public static void footstep(BlockState state, boolean sprinting, float boost) {
        if (!Ds4cSettings.footstepsEnabled()) return;
        float intensity = intensity(FeedbackCategory.MOVEMENT);
        if (intensity <= 0.001f) return;
        leftFoot = !leftFoot;
        float gain = intensity * Ds4cSettings.footstepIntensity() * (sprinting ? 1.25f : 1f) * boost;
        Haptics.play(FeedbackCategory.MOVEMENT, surfaceTap(state, 1f), Math.min(1f, gain), leftFoot ? -0.3f : 0.3f);
    }

    /**
     * A foot meeting {@code state}: hard surfaces click, wood knocks, loose ground crunches, soft
     * ground thuds. {@code weight} scales the body (1 = a step).
     */
    private static HapticWaveform surfaceTap(BlockState state, float weight) {
        SoundType st = state.getSoundType();
        float carrier, bodyAmp, noiseHz, noiseAmp;
        int bodyMs, noiseMs;
        if (st == SoundType.STONE || st == SoundType.METAL || st == SoundType.NETHERITE_BLOCK
                || st == SoundType.ANCIENT_DEBRIS || st == SoundType.AMETHYST || st == SoundType.AMETHYST_CLUSTER
                || st == SoundType.COPPER || st == SoundType.DEEPSLATE || st == SoundType.DEEPSLATE_BRICKS
                || st == SoundType.TUFF || st == SoundType.CALCITE || st == SoundType.NETHER_BRICKS) {
            carrier = 180;
            bodyAmp = 0.30f;
            bodyMs = 45;
            noiseHz = 220;
            noiseAmp = 0.12f;
            noiseMs = 30;
        } else if (st == SoundType.WOOD || st == SoundType.BAMBOO || st == SoundType.BAMBOO_WOOD
                || st == SoundType.HANGING_SIGN || st == SoundType.NETHER_WOOD || st == SoundType.CHERRY_WOOD) {
            carrier = 120;
            bodyAmp = 0.32f;
            bodyMs = 80;
            noiseHz = 150;
            noiseAmp = 0.06f;
            noiseMs = 30;
        } else if (st == SoundType.WOOL || st == SoundType.MOSS || st == SoundType.MOSS_CARPET) {
            carrier = 90;
            bodyAmp = 0.18f;
            bodyMs = 60;
            noiseHz = 0;
            noiseAmp = 0;
            noiseMs = 0;
        } else if (st == SoundType.GRAVEL || st == SoundType.SAND || st == SoundType.SUSPICIOUS_GRAVEL
                || st == SoundType.SUSPICIOUS_SAND) {
            carrier = 110;
            bodyAmp = 0.10f;
            bodyMs = 40;
            noiseHz = 150;
            noiseAmp = 0.30f;
            noiseMs = 70;
        } else if (st == SoundType.GRASS || st == SoundType.ROOTED_DIRT) {
            carrier = 110;
            bodyAmp = 0.20f;
            bodyMs = 60;
            noiseHz = 140;
            noiseAmp = 0.10f;
            noiseMs = 50;
        } else if (st == SoundType.SNOW || st == SoundType.POWDER_SNOW) {
            carrier = 100;
            bodyAmp = 0.10f;
            bodyMs = 50;
            noiseHz = 130;
            noiseAmp = 0.25f;
            noiseMs = 80;
        } else if (st == SoundType.MUD || st == SoundType.MUDDY_MANGROVE_ROOTS) {
            carrier = 85;
            bodyAmp = 0.32f;
            bodyMs = 110;
            noiseHz = 100;
            noiseAmp = 0.08f;
            noiseMs = 80;
        } else if (st == SoundType.HONEY_BLOCK || st == SoundType.SLIME_BLOCK) {
            carrier = 80;
            bodyAmp = 0.30f;
            bodyMs = 120;
            noiseHz = 0;
            noiseAmp = 0;
            noiseMs = 0;
        } else if (state.is(BlockTags.ICE)) {
            carrier = 210;
            bodyAmp = 0.28f;
            bodyMs = 40;
            noiseHz = 240;
            noiseAmp = 0.12f;
            noiseMs = 30;
        } else {
            carrier = 120;
            bodyAmp = 0.22f;
            bodyMs = 60;
            noiseHz = 140;
            noiseAmp = 0.08f;
            noiseMs = 40;
        }
        HapticWaveform body = HapticWaveform.thump(carrier, Math.round(bodyMs * (0.8f + 0.2f * weight)), Math.min(1f, bodyAmp * weight));
        if (noiseAmp <= 0f) return body;
        HapticWaveform grit = HapticWaveform.withASR(HapticWaveform.bandNoise(noiseHz, noiseMs, Math.min(1f, noiseAmp * weight)),
                2, noiseMs / 3, noiseMs - 2 - noiseMs / 3);
        return HapticWaveform.mix(body, grit);
    }

    public static void onLanding(LocalPlayer player, BlockState landedOn, double fallDistance) {
        if (player.getVehicle() != null) return;
        landing(landedOn, fallDistance, player.getMainHandItem().is(Items.MACE), player.isSprinting());
    }

    /**
     * Landing after falling {@code fallDistance} blocks onto {@code state}. A jump or a one-block
     * drop (about 0.6 to 2 blocks) is a surface tap a little heavier than a step; real falls are a
     * thump that grows with the height. Also used by the effect gallery.
     */
    public static void landing(BlockState state, double fallDistance, boolean holdingMace, boolean sprinting) {
        if (fallDistance < 0.6) return;
        float intensity = intensity(FeedbackCategory.MOVEMENT);
        if (intensity > 0.001f) {
            if (fallDistance < 2.0) {
                if (Ds4cSettings.footstepsEnabled()) {
                    float weight = 1.2f + 0.5f * (float) ((fallDistance - 0.6) / 1.4) + (sprinting ? 0.3f : 0f);
                    Haptics.play(FeedbackCategory.MOVEMENT, surfaceTap(state, weight),
                            intensity * Ds4cSettings.footstepIntensity());
                }
            } else {
                float amp = 0.35f + 0.6f * (float) Math.min(1.0, (fallDistance - 2.0) / 10.0);
                int ms = 120 + (int) Math.min(200, fallDistance * 12);
                Haptics.play(FeedbackCategory.MOVEMENT, HapticWaveform.mix(
                        HapticWaveform.thump(90f, ms, amp),
                        HapticWaveform.withASR(HapticWaveform.bandNoise(130f, 80, 0.4f * amp), 2, 30, 48)), intensity);
            }
        }

        if (fallDistance >= 3.0 && holdingMace) {
            float mace = (float) Math.min(1.0, fallDistance / 6.0) * intensity(FeedbackCategory.COMBAT);
            if (mace <= 0.001f) return;
            Haptics.play(FeedbackCategory.COMBAT, HapticWaveform.mix(
                    HapticWaveform.thump(80f, 300, 0.95f),
                    HapticWaveform.withASR(HapticWaveform.bandNoise(150f, 240, 0.5f), 3, 90, 147)), mace);
        }
    }

    // --- combat ----------------------------------------------------------------------------------

    public static void onLocalPlayerDamaged(LocalPlayer player, DamageSource source) {
        float intensity = intensity(FeedbackCategory.COMBAT);
        if (intensity <= 0.001f) return;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return; // the explosion itself is felt
        HapticWaveform overlay = damageOverlay(source);
        Vec3 from = source.getSourcePosition();
        float pan = from == null ? 0f : 0.7f * panTowards(player, from);
        Haptics.play(FeedbackCategory.COMBAT, overlay, intensity, pan);
    }

    /**
     * What getting hurt feels like, by cause; anything else is a plain hit from its direction.
     */
    private static HapticWaveform damageOverlay(DamageSource source) {
        if (source.is(DamageTypes.FALL)) {
            return HapticWaveform.thump(85f, 240, 0.9f);
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return HapticWaveform.withASR(HapticWaveform.bandNoise(170f, 200, 0.65f), 5, 120, 75);
        }
        if (source.is(DamageTypes.DROWN)) {
            return HapticWaveform.withASR(HapticWaveform.amSine(100f, 4f, 300, 0.65f, 0.8f), 10, 200, 90);
        }
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return HapticWaveform.withASR(HapticWaveform.amSine(120f, 6f, 400, 0.55f, 0.9f), 10, 250, 140);
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return HapticWaveform.mix(
                    HapticWaveform.withASR(HapticWaveform.bandNoise(160f, 150, 1f), 1, 60, 89),
                    HapticWaveform.thump(80f, 300, 0.7f));
        }
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return HapticWaveform.withASR(HapticWaveform.bandNoise(230f, 220, 0.4f), 5, 120, 95);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypes.WIND_CHARGE)) {
            return HapticWaveform.thump(150f, 90, 0.8f); // a sharp thwack
        }
        return HapticWaveform.mix(
                HapticWaveform.thump(110f, 140, 0.75f),
                HapticWaveform.withASR(HapticWaveform.bandNoise(140f, 50, 0.3f), 1, 15, 34));
    }

    /**
     * How strongly an explosion at {@code center} is felt: full within its radius, fading to
     * nothing 32 blocks beyond it, bigger explosions stronger (Controlify's rumble curve).
     */
    public static float explosionMagnitude(LocalPlayer player, Vec3 center, float radius) {
        double surface = Math.max(0.0, Math.sqrt(player.distanceToSqr(center)) - Math.max(0f, radius));
        if (surface >= 32.0) return 0f;
        float proximity = 1f - (float) surface / 32f;
        return proximity * proximity * proximity * Math.min(1f, (float) Math.sqrt(Math.max(0f, radius) / 6f));
    }

    /**
     * A blast: a crack, a heavy body and a rolling aftershock, leaning towards the explosion.
     */
    public static void explosion(float magnitude, float pan) {
        float intensity = intensity(FeedbackCategory.COMBAT) * magnitude;
        if (intensity <= 0.01f) return;
        Haptics.play(FeedbackCategory.COMBAT, HapticWaveform.mix(
                HapticWaveform.withASR(HapticWaveform.bandNoise(140f, 100, 1f), 1, 30, 69),
                HapticWaveform.thump(80f, 650, 0.95f),
                HapticWaveform.withASR(HapticWaveform.bandNoise(95f, 700, 0.4f), 80, 200, 420).delayed(60)
        ), intensity, 0.5f * pan);
    }

    // --- environment -----------------------------------------------------------------------------

    /**
     * One pulse per crack stage while mining, in step with the R2 pulse, voiced by the tool: a
     * pickaxe chinks, a shovel scrapes, an axe chops. Builds as the block gives.
     */
    public static void breakPulse(int stage, ItemStack tool) {
        float intensity = intensity(FeedbackCategory.ENVIRONMENT);
        if (intensity <= 0.001f) return;
        float a = 0.45f + 0.035f * Math.max(0, Math.min(9, stage));
        HapticWaveform pulse;
        if (tool.is(ItemTags.PICKAXES)) pulse = HapticWaveform.thump(150f, 60, a);
        else if (tool.is(ItemTags.SHOVELS))
            pulse = HapticWaveform.withASR(HapticWaveform.bandNoise(120f, 80, a), 3, 35, 42);
        else if (tool.is(ItemTags.AXES)) pulse = HapticWaveform.thump(110f, 80, a);
        else if (tool.is(ItemTags.HOES)) pulse = HapticWaveform.thump(130f, 60, a);
        else if (tool.getItem() instanceof ShearsItem)
            pulse = HapticWaveform.withASR(HapticWaveform.bandNoise(200f, 50, a), 1, 20, 29);
        else pulse = HapticWaveform.thump(120f, 60, a * 0.85f);
        Haptics.play(FeedbackCategory.ENVIRONMENT, pulse, intensity);
    }

    /**
     * You placed a block: the block seating, a low thunk of its weight, under a tap voiced by its
     * own material (stone clicks, wood knocks, wool is soft).
     */
    public static void blockPlaced(BlockState state) {
        float intensity = intensity(FeedbackCategory.ENVIRONMENT);
        if (intensity <= 0.001f) return;
        Haptics.play(FeedbackCategory.ENVIRONMENT, HapticWaveform.mix(
                HapticWaveform.thump(75f, 60, 0.30f),
                surfaceTap(state, 2.2f)), intensity);
    }

    /**
     * Started mining a block the held item can't harvest: a dull scrape instead of progress.
     */
    public static void onIneffectiveTool() {
        float intensity = intensity(FeedbackCategory.ENVIRONMENT);
        if (intensity <= 0.001f) return;
        Haptics.play(FeedbackCategory.ENVIRONMENT,
                HapticWaveform.withASR(HapticWaveform.bandNoise(130f, 140, 0.4f), 5, 70, 65), intensity);
    }

    /**
     * Two quick, bright snips.
     */
    public static void onShearsSnip() {
        float intensity = intensity(FeedbackCategory.ENVIRONMENT);
        if (intensity <= 0.001f) return;
        HapticWaveform snip = HapticWaveform.withASR(HapticWaveform.bandNoise(220f, 35, 0.55f), 1, 20, 14);
        Haptics.play(FeedbackCategory.ENVIRONMENT, HapticWaveform.mix(snip, snip.delayed(60)), intensity);
    }

    /**
     * Strike within 16 blocks: a sharp crack then a rolling rumble, fading with distance.
     */
    public static void onLightningStrike(LightningBolt bolt) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        double dist = Math.sqrt(bolt.distanceToSqr(player));
        if (dist > 16.0) return;
        lightning((float) (1.0 - dist / 16.0), 0.5f * panTowards(player, bolt.position()));
    }

    /**
     * Lightning strike at {@code proximity} (1 = on top of the player, 0 = 16 blocks). Also used by the effect gallery.
     */
    public static void lightning(float proximity, float pan) {
        float intensity = intensity(FeedbackCategory.AMBIENT) * proximity;
        if (intensity <= 0.001f) return;
        Haptics.play(FeedbackCategory.AMBIENT, HapticWaveform.mix(
                HapticWaveform.withASR(HapticWaveform.bandNoise(160f, 120, 1f), 1, 40, 79),
                HapticWaveform.thump(80f, 650, 0.85f).delayed(80)), intensity, pan);
    }

    // --- items -----------------------------------------------------------------------------------

    /**
     * Soft fishing cue (fish approaching, nibble, bobber bob).
     */
    public static void onFishingCue(float amplitude, float carrierHz, int durationMs) {
        float intensity = intensity(FeedbackCategory.ITEMS);
        if (intensity <= 0.001f) return;
        Haptics.play(FeedbackCategory.ITEMS, HapticWaveform.thump(carrierHz, durationMs, amplitude), intensity);
    }
}

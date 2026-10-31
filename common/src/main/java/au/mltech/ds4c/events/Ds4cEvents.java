package au.mltech.ds4c.events;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.mixin.FishingHookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns game events into {@link TimedFacts}. Mixins call these on the client thread; each opens a
 * short window on a {@code ds4c:event/*} fact, and the pack's rules map facts to trigger effects.
 * Durations are in client ticks (50 ms).
 */
public final class Ds4cEvents {
    // Attack swing: a three-stage swoop (heavy start, light middle, hard stop).
    public static final Identifier SWING_1 = event("swing_1");
    public static final Identifier SWING_2 = event("swing_2");
    public static final Identifier SWING_3 = event("swing_3");
    public static final Identifier CRIT = event("crit");
    public static final Identifier TOOL_WARNING_ATTACK = event("tool_warning_attack");
    public static final Identifier TOOL_WARNING_USE = event("tool_warning_use");

    // Attack impact sounds played at the player.
    public static final Identifier HIT_STRONG = event("hit_strong");
    public static final Identifier HIT_CRIT = event("hit_crit");
    public static final Identifier HIT_KNOCKBACK = event("hit_knockback");
    public static final Identifier HIT_SWEEP = event("hit_sweep");
    public static final Identifier HIT_WEAK = event("hit_weak");
    public static final Identifier HIT_NO_DAMAGE = event("hit_no_damage");

    public static final Identifier PROJECTILE_HIT = event("projectile_hit");
    public static final Identifier SHIELD_BLOCK = event("shield_block");
    public static final Identifier CROSSBOW_FIRE = event("crossbow_fire");
    public static final Identifier CROSSBOW_LOADED = event("crossbow_loaded");
    public static final Identifier BOW_RELEASE = event("bow_release");
    public static final Identifier TRIDENT_RELEASE = event("trident_release");
    public static final Identifier DRINK_FIZZ = event("drink_fizz");
    public static final Identifier JUMP = event("jump");
    public static final Identifier FIREWORK = event("firework");
    public static final Identifier FISHING_CAST = event("fishing_cast");

    // Using items on blocks and entities.
    public static final Identifier BLOCK_PLACE = event("block_place");
    public static final Identifier BUCKET = event("bucket");
    public static final Identifier BUCKET_LAVA = event("bucket_lava");
    public static final Identifier HOE_TILL = event("hoe_till");
    public static final Identifier SPAWN_EGG = event("spawn_egg");
    public static final Identifier SHEARS_CLIP = event("shears_clip");

    // Block breaking.
    public static final Identifier BLOCK_BROKEN = event("block_broken");
    public static final Identifier BREAK_SIGNATURE = event("break_signature");
    /** {@code ds4c:event/break_pulse/1..8}: decaying pulse at each block-crack stage. */
    public static final String BREAK_PULSE = "event/break_pulse/";
    /** {@code ds4c:event/mace_land/4..8}: mace ground slam, by fall distance. */
    public static final String MACE_LAND = "event/mace_land/";

    private static int lastBreakStage = -1;
    private static boolean signatureFired = false;
    private static boolean hadFishingHook = false;
    private static boolean crossbowWasLoaded = false;

    private Ds4cEvents() {
    }

    private static Identifier event(String name) {
        return Constants.id("event/" + name);
    }

    private static Identifier bucket(String prefix, int value) {
        return Constants.id(prefix + value);
    }

    // --- client tick -----------------------------------------------------------------------------

    /** Start of every client tick. Advances time and detects state transitions that have no hook. */
    public static void clientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            TimedFacts.clear();
            lastBreakStage = -1;
            signatureFired = false;
            hadFishingHook = false;
            crossbowWasLoaded = false;
            return;
        }
        TimedFacts.tick();

        boolean hasFishingHook = player.fishing != null;
        if (hasFishingHook && !hadFishingHook) {
            TimedFacts.pulse(FISHING_CAST, 2);
        }
        hadFishingHook = hasFishingHook;

        boolean crossbowLoaded = isCrossbowLoaded(player);
        if (crossbowLoaded && !crossbowWasLoaded) {
            TimedFacts.pulse(CROSSBOW_LOADED, 3);
        }
        crossbowWasLoaded = crossbowLoaded;
    }

    /** Still holding Use on a crossbow that has finished loading. */
    public static boolean isCrossbowLoaded(LocalPlayer player) {
        if (!player.isUsingItem()) return false;
        ItemStack stack = player.getUseItem();
        return stack.getUseAnimation() == ItemUseAnimation.CROSSBOW
                && player.getTicksUsingItem() >= CrossbowItem.getChargeDuration(stack, player);
    }

    public static boolean isFishingHookBiting(LocalPlayer player) {
        return player.fishing != null && ((FishingHookAccessor) player.fishing).ds4c$isBiting();
    }

    // --- attacking -------------------------------------------------------------------------------

    /** The player started an attack (entity hit, block hit, miss, or spear stab). */
    public static void onAttackSwing(LocalPlayer player) {
        if (!TimedFacts.isPending(SWING_1) && !TimedFacts.isPending(SWING_2) && !TimedFacts.isPending(SWING_3)) {
            TimedFacts.schedule(SWING_1, 0, 2);
            TimedFacts.schedule(SWING_2, 2, 2);
            TimedFacts.schedule(SWING_3, 4, 1);
        }

        // Same conditions vanilla uses for crit particles.
        boolean crit = player.fallDistance > 0f
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.isPassenger();
        if (crit) {
            TimedFacts.pulse(CRIT, 4);
        }

        if (isNearlyBroken(player.getMainHandItem())) {
            TimedFacts.pulse(TOOL_WARNING_ATTACK, 2);
        }
    }

    public static void onSoundPlayed(SoundInstance sound) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Identifier id = sound.getIdentifier();

        if (SoundEvents.SHIELD_BLOCK.is(id)) {
            if (player.isBlocking()) TimedFacts.pulse(SHIELD_BLOCK, 2);
            return;
        }

        Identifier hit;
        int duration;
        if (id.equals(SoundEvents.PLAYER_ATTACK_STRONG.location())) {
            hit = HIT_STRONG;
            duration = 4;
        } else if (id.equals(SoundEvents.PLAYER_ATTACK_CRIT.location())) {
            hit = HIT_CRIT;
            duration = 4;
        } else if (id.equals(SoundEvents.PLAYER_ATTACK_KNOCKBACK.location())) {
            hit = HIT_KNOCKBACK;
            duration = 5;
        } else if (id.equals(SoundEvents.PLAYER_ATTACK_SWEEP.location())) {
            hit = HIT_SWEEP;
            duration = 3;
        } else if (id.equals(SoundEvents.PLAYER_ATTACK_WEAK.location())) {
            hit = HIT_WEAK;
            duration = 2;
        } else if (id.equals(SoundEvents.PLAYER_ATTACK_NODAMAGE.location())) {
            hit = HIT_NO_DAMAGE;
            duration = 1;
        } else {
            return;
        }

        // Only attacks by this player: vanilla plays these at the attacker's position.
        double dx = sound.getX() - player.getX();
        double dy = sound.getY() - player.getY();
        double dz = sound.getZ() - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 1.0) return;
        TimedFacts.pulse(hit, duration);
    }

    public static void onLocalPlayerDamaged(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypes.WIND_CHARGE)) {
            TimedFacts.pulse(PROJECTILE_HIT, 3);
        }
    }

    public static void onLanding(LocalPlayer player, double fallDistance) {
        if (fallDistance < 3.0 || !player.getMainHandItem().is(Items.MACE)) return;
        int strength = Math.min(8, 4 + (int) Math.round(4.0 * Math.min(1.0, fallDistance / 5.0)));
        TimedFacts.pulse(bucket(MACE_LAND, strength), 4);
    }

    // --- movement --------------------------------------------------------------------------------

    public static void onJump(LocalPlayer player) {
        if (player.getVehicle() == null) {
            TimedFacts.pulse(JUMP, 2);
        }
    }

    /** Called every tick a firework is attached to the fall-flying player. */
    public static void onFireworkBoost() {
        TimedFacts.pulse(FIREWORK, 2);
    }

    // --- using items -----------------------------------------------------------------------------

    public static void onCrossbowFired() {
        TimedFacts.pulse(CROSSBOW_FIRE, 4);
    }

    /** Use was released early (bow shot, trident throw). Called before the use state is cleared. */
    public static void onReleaseUsingItem(LocalPlayer player) {
        ItemUseAnimation animation = player.getUseItem().getUseAnimation();
        if (animation == ItemUseAnimation.BOW) {
            TimedFacts.pulse(BOW_RELEASE, 2);
        } else if (animation == ItemUseAnimation.TRIDENT) {
            TimedFacts.pulse(TRIDENT_RELEASE, 2);
        }
    }

    /** Use ran to completion (finished eating or drinking). Called before the use state is cleared. */
    public static void onCompleteUsingItem(LocalPlayer player) {
        if (player.getUseItem().getUseAnimation() == ItemUseAnimation.DRINK) {
            TimedFacts.pulse(DRINK_FIZZ, 4);
        }
    }

    /**
     * An item use that consumed the action. {@code onBlock} is true for use-on-block, false for
     * use-in-air (where only buckets are worth a cue).
     */
    public static void onItemUsed(ItemStack stack, boolean onBlock) {
        if (isNearlyBroken(stack)) {
            TimedFacts.pulse(TOOL_WARNING_USE, 2);
        }

        if (stack.getItem() instanceof BucketItem) {
            TimedFacts.pulse(BUCKET, 4);
            if (stack.is(Items.LAVA_BUCKET)) TimedFacts.pulse(BUCKET_LAVA, 4);
            return;
        }
        if (!onBlock) return;

        if (stack.is(ItemTags.HOES)) {
            TimedFacts.pulse(HOE_TILL, 5);
        } else if (stack.getItem() instanceof SpawnEggItem) {
            TimedFacts.pulse(SPAWN_EGG, 1);
        } else if (stack.getItem() instanceof BlockItem) {
            TimedFacts.pulse(BLOCK_PLACE, 1);
        }
    }

    public static void onShearsUsedOnEntity() {
        TimedFacts.pulse(SHEARS_CLIP, 2);
    }

    // --- block breaking --------------------------------------------------------------------------

    /** Each tick of holding attack on a block. Pulses once per visible crack stage (0..9). */
    public static void onBreakProgress(float destroyProgress) {
        int stage = Math.max(0, Math.min(9, (int) (destroyProgress * 10f)));
        if (stage == lastBreakStage) return;
        lastBreakStage = stage;

        // Start hard and get harder; each pulse decays over three ticks.
        int amplitude = Math.min(8, 5 + (stage * 3) / 9);
        TimedFacts.schedule(bucket(BREAK_PULSE, amplitude), 0, 1);
        TimedFacts.schedule(bucket(BREAK_PULSE, Math.max(1, amplitude - 3)), 1, 1);
        TimedFacts.schedule(bucket(BREAK_PULSE, Math.max(1, amplitude - 6)), 2, 1);

        if (stage >= 8 && !signatureFired) {
            signatureFired = true;
            TimedFacts.pulse(BREAK_SIGNATURE, 2);
        }
    }

    public static void onBreakReset() {
        lastBreakStage = -1;
        signatureFired = false;
    }

    /** A block was broken (instantly, or at the end of breaking). */
    public static void onBlockBroken(LocalPlayer player, BlockState state) {
        onBreakReset();
        if (state.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(state)) return;
        TimedFacts.pulse(BLOCK_BROKEN, 3);
    }

    private static boolean isNearlyBroken(ItemStack stack) {
        return stack.isDamageableItem() && stack.getMaxDamage() > 0
                && stack.getDamageValue() > 0.9f * stack.getMaxDamage();
    }
}

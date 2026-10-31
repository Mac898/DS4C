package au.mltech.ds4c.facts;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import au.mltech.ds4c.events.Ds4cEvents;
import au.mltech.ds4c.events.TimedFacts;
import dev.isxander.controlify.api.contextual.ContextualStateSink;
import dev.isxander.controlify.api.contextual.InGameContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Exposes game state to Controlify's contextual rule engine as {@code ds4c:*} facts and slots.
 * <p>
 * Controlify evaluates the in-game domain separately for button guides and for trigger effects,
 * so this runs more than once per tick. Keep it cheap and free of side effects: anything
 * time-based must compare against an absolute tick, never count down here.
 * <p>
 * Simple block and item checks live in the pack's {@code facts/in_game.json} so they stay
 * data-driven; this class only provides what JSON predicates can't express. Bucketed facts
 * ({@code name/<n>}) only contribute the active bucket: a missing fact reads as false.
 */
public final class Ds4cFacts {
    /**
     * Phase 0 verification fact, enabled with {@code -Dds4c.debugProbe=true}. A rule in the built-in
     * pack uses it to override Controlify's default bow effect, proving the pack loads, this
     * contributor runs, and DS4C's rules are evaluated before Controlify's.
     */
    public static final Identifier DEBUG_PROBE = Constants.id("debug_probe");
    /** Master switch for every DS4C trigger rule. */
    public static final Identifier ENABLED = Constants.id("enabled");

    /** Slot: the block occupying the player's feet (portals, cobwebs). */
    public static final Identifier SLOT_BLOCK_AT_FEET = Constants.id("block_at_feet");
    /** Slot: the block supporting the player. */
    public static final Identifier SLOT_BLOCK_BELOW = Constants.id("block_below");

    public static final Identifier USING_ITEM = Constants.id("using_item");
    public static final Identifier UNDERWATER = Constants.id("underwater");
    public static final Identifier LOW_FOOD = Constants.id("low_food");
    public static final Identifier RIDING_MINECART = Constants.id("riding_minecart");
    public static final Identifier RIDING_BOAT = Constants.id("riding_boat");
    /** Still holding Use on a crossbow that has finished loading. */
    public static final Identifier CROSSBOW_LOADED = Constants.id("crossbow_loaded");
    /** Chewing/gulping beat while eating or drinking: two ticks on, two off. */
    public static final Identifier CHEW = Constants.id("chew");
    public static final Identifier FISHING_BITING = Constants.id("fishing_biting");

    /** {@code ds4c:use_anim/<animation>}: the use animation of the item being used (bow, crossbow, eat...). */
    public static final String USE_ANIM = "use_anim/";
    /** {@code ds4c:draw/0..7}: draw or charge progress of the bow, crossbow or trident being used. */
    public static final String DRAW = "draw/";
    /** {@code ds4c:horse_jump/0..7}: mount jump charge. */
    public static final String HORSE_JUMP = "horse_jump/";
    /** {@code ds4c:tool/<class>}: main-hand tool class, for block-break pulse frequency. */
    public static final String TOOL = "tool/";

    /** {@code ds4c:sticky/1..4}: how strongly the block at/under the player slows movement. */
    public static final String STICKY = "sticky/";
    /** {@code ds4c:vehicle_speed/2..6}: horizontal speed of a boat or minecart, as a trigger amplitude. */
    public static final String VEHICLE_SPEED = "vehicle_speed/";
    /** {@code ds4c:fall_charge/3..8}: airborne fall distance, as a trigger strength (mace wind-up). */
    public static final String FALL_CHARGE = "fall_charge/";
    /** {@code ds4c:target_resistance/1..8}: hardness of the harvestable block being looked at. */
    public static final String TARGET_RESISTANCE = "target_resistance/";

    private static final boolean DEBUG_PROBE_ENABLED = Boolean.getBoolean("ds4c.debugProbe");

    private Ds4cFacts() {
    }

    public static void contributeInGame(InGameContext context, ContextualStateSink sink) {
        sink.contributeFact(DEBUG_PROBE, DEBUG_PROBE_ENABLED);
        sink.contributeFact(ENABLED, Ds4cSettings.triggerEffectsEnabled());
        for (FeedbackCategory category : FeedbackCategory.values()) {
            sink.contributeFact(category.factId(), Ds4cSettings.isCategoryEnabled(category));
        }

        LocalPlayer player = context.player();
        ClientLevel level = context.level();

        BlockPos feetPos = player.blockPosition();
        BlockPos belowPos = player.getOnPos();
        BlockState feet = level.getBlockState(feetPos);
        BlockState below = level.getBlockState(belowPos);
        sink.contributeBlock(SLOT_BLOCK_AT_FEET, new BlockInWorld(level, feetPos, false));
        sink.contributeBlock(SLOT_BLOCK_BELOW, new BlockInWorld(level, belowPos, false));

        sink.contributeFact(USING_ITEM, player.isUsingItem());
        sink.contributeFact(UNDERWATER, player.isUnderWater());
        sink.contributeFact(LOW_FOOD, player.getFoodData().getFoodLevel() <= 6);

        contributeSticky(sink, feet, below);
        contributeVehicle(sink, player.getVehicle());
        contributeFallCharge(sink, player);
        contributeTargetResistance(sink, player, level, context.hitResult());

        contributeItemUse(sink, player);
        contributeHorseJump(sink, player);
        sink.contributeFact(FISHING_BITING, Ds4cEvents.isFishingHookBiting(player));
        sink.contributeFact(toolClass(player.getMainHandItem()));

        TimedFacts.contribute(sink);
    }

    private static void contributeItemUse(ContextualStateSink sink, LocalPlayer player) {
        if (!player.isUsingItem()) return;
        ItemStack stack = player.getUseItem();
        ItemUseAnimation animation = stack.getUseAnimation();
        int ticks = player.getTicksUsingItem();
        sink.contributeFact(Constants.id(USE_ANIM + animation.getSerializedName()));

        float progress = switch (animation) {
            case BOW -> BowItem.getPowerForTime(ticks);
            case CROSSBOW -> {
                int charge = CrossbowItem.getChargeDuration(stack, player);
                yield charge <= 0 ? 1f : (float) ticks / charge;
            }
            case TRIDENT -> (float) ticks / TridentItem.THROW_THRESHOLD_TIME;
            default -> -1f;
        };
        if (progress >= 0f) {
            sink.contributeFact(bucket(DRAW, Math.round(Math.min(1f, progress) * 7f)));
        }

        sink.contributeFact(CROSSBOW_LOADED, Ds4cEvents.isCrossbowLoaded(player));
        boolean consuming = animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
        sink.contributeFact(CHEW, consuming && ticks > 0 && ticks % 4 < 2);
    }

    private static void contributeHorseJump(ContextualStateSink sink, LocalPlayer player) {
        float charge = player.getJumpRidingScale();
        if (charge <= 0f || player.jumpableVehicle() == null) return;
        sink.contributeFact(bucket(HORSE_JUMP, Math.round(Math.min(1f, charge) * 7f)));
    }

    private static Identifier toolClass(ItemStack stack) {
        String name;
        if (stack.is(ItemTags.AXES)) name = "axe";
        else if (stack.is(ItemTags.SHOVELS)) name = "shovel";
        else if (stack.is(ItemTags.HOES)) name = "hoe";
        else if (stack.is(ItemTags.PICKAXES)) name = "pickaxe";
        else if (stack.getItem() instanceof ShearsItem) name = "shears";
        else if (stack.is(Items.MACE)) name = "mace";
        else name = "other";
        return Constants.id(TOOL + name);
    }

    /** Resistance proportional to how much the block slows movement; cobwebs slow via entity damping, so check them explicitly. */
    private static void contributeSticky(ContextualStateSink sink, BlockState feet, BlockState below) {
        float speedFactor = Math.min(feet.getBlock().getSpeedFactor(), below.getBlock().getSpeedFactor());
        boolean inCobweb = feet.is(Blocks.COBWEB);
        if (speedFactor >= 1.0f && !inCobweb) return;

        float slow = inCobweb ? 1.0f : Math.max(0f, 1.0f - speedFactor);
        int level = Math.min(4, 1 + Math.round(3.5f * slow));
        sink.contributeFact(bucket(STICKY, level));
    }

    private static void contributeVehicle(ContextualStateSink sink, Entity vehicle) {
        boolean minecart = vehicle instanceof AbstractMinecart;
        boolean boat = vehicle instanceof AbstractBoat;
        sink.contributeFact(RIDING_MINECART, minecart);
        sink.contributeFact(RIDING_BOAT, boat);
        if (!minecart && !boat) return;

        Vec3 v = vehicle.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        if (speed < 0.05) return;
        int amplitude = Math.min(6, 2 + (int) Math.round(speed * 12));
        sink.contributeFact(bucket(VEHICLE_SPEED, amplitude));
    }

    private static void contributeFallCharge(ContextualStateSink sink, LocalPlayer player) {
        if (player.onGround() || player.fallDistance <= 1.0) return;
        int strength = Math.min(8, 3 + (int) Math.round(5.0 * Math.min(1.0, player.fallDistance / 6.0)));
        sink.contributeFact(bucket(FALL_CHARGE, strength));
    }

    /** Only blocks the player can actually break and that would drop with the held item. */
    private static void contributeTargetResistance(ContextualStateSink sink, LocalPlayer player, ClientLevel level, HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) return;
        BlockState state = level.getBlockState(blockHit.getBlockPos());
        if (state.isAir()) return;
        if (state.getDestroySpeed(level, blockHit.getBlockPos()) < 0f) return; // unbreakable
        if (state.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(state)) return;

        float hardness = state.getBlock().defaultDestroyTime();
        int strength = hardness <= 0f ? 1 : Math.min(8, 2 + (int) Math.round(6.0 * Math.min(1.0, hardness / 10.0)));
        sink.contributeFact(bucket(TARGET_RESISTANCE, strength));
    }

    private static Identifier bucket(String prefix, int value) {
        return Constants.id(prefix + value);
    }
}

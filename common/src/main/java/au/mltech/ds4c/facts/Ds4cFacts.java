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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
    /** {@code ds4c:draw/0..3}: draw or charge progress of the bow, crossbow or trident being used. */
    public static final String DRAW = "draw/";
    /** The "pull through to shoot" setting is on: bow and trident draws give way at the end. */
    public static final Identifier PULL_THROUGH = Constants.id("pull_through");
    /** Holding a crossbow that is loaded. */
    public static final Identifier HOLDING_LOADED_CROSSBOW = Constants.id("holding_loaded_crossbow");
    /** {@code ds4c:tool/<class>}: main-hand tool class, for block-break pulse frequency. */
    public static final String TOOL = "tool/";

    /** {@code ds4c:trigger_strength/50|75}: the trigger strength setting, when below 100%. */
    public static final String TRIGGER_STRENGTH = "trigger_strength/";
    /** {@code ds4c:fall_charge/1..3}: airborne fall distance (mace wind-up). */
    public static final String FALL_CHARGE = "fall_charge/";
    /**
     * {@code ds4c:target_resistance/1..3}: hardness of the block a held tool is aimed at and can
     * harvest (soft, medium, hard). Held briefly through changes, see {@link Dwell}.
     */
    public static final String TARGET_RESISTANCE = "target_resistance/";
    /** Looking at a block, held briefly through changes so sweeping the crosshair doesn't flicker. */
    public static final Identifier AIMING_AT_BLOCK = Constants.id("aiming_at_block");

    /** How long a looked-at block change must persist before the trigger follows it: 150 ms. */
    private static final int AIM_DWELL_TICKS = 3;
    private static final Dwell aimDwell = new Dwell(AIM_DWELL_TICKS, 0);
    private static final Dwell resistanceDwell = new Dwell(AIM_DWELL_TICKS, 0);

    private Ds4cFacts() {
    }

    public static void contributeInGame(InGameContext context, ContextualStateSink sink) {
        sink.contributeFact(ENABLED, Ds4cSettings.triggerEffectsEnabled());
        for (FeedbackCategory category : FeedbackCategory.values()) {
            sink.contributeFact(category.factId(), Ds4cSettings.isCategoryEnabled(category));
        }
        int strength = Ds4cSettings.triggerStrength();
        if (strength < 100) sink.contributeFact(Constants.id(TRIGGER_STRENGTH + strength));

        LocalPlayer player = context.player();
        ClientLevel level = context.level();

        BlockPos feetPos = player.blockPosition();
        BlockPos belowPos = player.getOnPos();
        sink.contributeBlock(SLOT_BLOCK_AT_FEET, new BlockInWorld(level, feetPos, false));
        sink.contributeBlock(SLOT_BLOCK_BELOW, new BlockInWorld(level, belowPos, false));

        sink.contributeFact(USING_ITEM, player.isUsingItem());
        sink.contributeFact(UNDERWATER, player.isUnderWater());
        sink.contributeFact(LOW_FOOD, player.getFoodData().getFoodLevel() <= 6);

        Entity vehicle = player.getVehicle();
        sink.contributeFact(RIDING_MINECART, vehicle instanceof AbstractMinecart);
        sink.contributeFact(RIDING_BOAT, vehicle instanceof AbstractBoat);
        contributeFallCharge(sink, player);
        contributeAim(sink, player, level, context.hitResult());

        contributeItemUse(sink, player);
        sink.contributeFact(PULL_THROUGH, Ds4cSettings.pullThroughToShoot());
        ItemStack mainHand = player.getMainHandItem();
        sink.contributeFact(HOLDING_LOADED_CROSSBOW, mainHand.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(mainHand));
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
            sink.contributeFact(bucket(DRAW, Math.round(Math.min(1f, progress) * 3f)));
        }

        sink.contributeFact(CROSSBOW_LOADED, Ds4cEvents.isCrossbowLoaded(player));
        boolean consuming = animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
        sink.contributeFact(CHEW, consuming && ticks > 0 && ticks % 4 < 2);
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

    private static void contributeFallCharge(ContextualStateSink sink, LocalPlayer player) {
        if (player.onGround() || player.fallDistance <= 1.0) return;
        int level = player.fallDistance <= 2.5 ? 1 : player.fallDistance <= 4.5 ? 2 : 3;
        sink.contributeFact(bucket(FALL_CHARGE, level));
    }

    private static void contributeAim(ContextualStateSink sink, LocalPlayer player, ClientLevel level, HitResult hitResult) {
        long now = player.tickCount;
        boolean aiming = hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                && !level.getBlockState(blockHit.getBlockPos()).isAir();
        sink.contributeFact(AIMING_AT_BLOCK, aimDwell.update(aiming ? 1 : 0, now) == 1);

        int resistance = aiming ? targetResistance(player, level, (BlockHitResult) hitResult) : 0;
        resistance = resistanceDwell.update(resistance, now);
        if (resistance > 0) {
            sink.contributeFact(bucket(TARGET_RESISTANCE, resistance));
        }
    }

    /**
     * 1..3 for a block the held tool can harvest (soft below 1 hardness: dirt, sand; medium below 3:
     * stone, logs; hard: ores, deepslate, obsidian), or 0 for none. Only while holding a tool, since
     * looking around is constant and other items gain nothing from it.
     */
    private static int targetResistance(LocalPlayer player, ClientLevel level, BlockHitResult hit) {
        ItemStack stack = player.getMainHandItem();
        if (!isDiggingTool(stack)) return 0;
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (state.getDestroySpeed(level, hit.getBlockPos()) <= 0f) return 0; // instant or unbreakable
        if (state.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(state)) return 0;

        float hardness = state.getBlock().defaultDestroyTime();
        return hardness < 1f ? 1 : hardness < 3f ? 2 : 3;
    }

    private static boolean isDiggingTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.getItem() instanceof ShearsItem;
    }

    private static Identifier bucket(String prefix, int value) {
        return Constants.id(prefix + value);
    }
}

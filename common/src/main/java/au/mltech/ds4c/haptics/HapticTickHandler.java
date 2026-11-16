package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Per-tick haptics that aren't driven by a single sound or event: beds (rain, water, wind, rides,
 * slowing blocks, portals), repeating cues (heartbeat, compass, bow at full draw, proximity
 * pulses) and rhythmic accents (rail joins, hoof beats).
 * <p>
 * Voicing: energy in the 80-250 Hz band the palm feels best, beds that change with the game state
 * (speed, depth, rain strength) and carry accents, and repeating cues that never duck other effects.
 * <p>
 * Owned by {@link Haptics}, which only ticks it while haptics can play and replaces it (after
 * clearing every loop) when they can't, so no loop outlives a world, a pause or a settings change.
 */
public final class HapticTickHandler {
    private static final Identifier UNDERWATER = Constants.id("underwater");
    private static final Identifier RAIN = Constants.id("rain");
    private static final Identifier MAGMA = Constants.id("magma");
    private static final Identifier DRAG = Constants.id("drag");
    private static final Identifier ALTITUDE_WIND = Constants.id("altitude_wind");
    private static final Identifier ELYTRA_WIND = Constants.id("elytra_wind");
    private static final Identifier MINECART = Constants.id("minecart");
    private static final Identifier BOAT = Constants.id("boat");
    private static final Identifier PORTAL = Constants.id("portal");

    /** Loop chunk length: five ticks. */
    private static final int CHUNK_MS = 250;

    private int heartbeatCooldown;
    private int compassCooldown;
    private int conduitCooldown;
    private int beeCooldown;
    private int beaconCooldown;
    private long lastClockSlot = Long.MIN_VALUE;
    private @Nullable ResourceKey<Level> lastDimension;
    private int portalTicks;
    private boolean portalSuppressed;
    private int waterDepth;
    private int depthScanCooldown;
    private double railDistance;
    private @Nullable Vec3 lastVehiclePos;
    private boolean horseAirborne;
    private int strideCooldown;
    private int strideBeat;
    private boolean bowWasReady;
    private int bowTremble;
    private @Nullable Block dragBlock;
    /** Read by loop generators, which are created once and called for every chunk. */
    private float rainStrength;
    private float portalRate = 4f;

    public void tick(LocalPlayer player) {
        tickHeartbeat(player);
        tickBowDraw(player);

        tickCompass(player);
        tickClock(player);

        tickElytraWind(player);
        tickDrag(player);
        tickMinecart(player);
        tickBoat(player);
        tickHorse(player);

        tickUnderwater(player);
        tickRain(player);
        tickMagma(player);
        tickAltitudeWind(player);
        tickConduit(player);
        tickBees(player);
        tickBeacon(player);
        tickPortal(player);
        tickDimensionChange(player);
    }

    // --- helpers -----------------------------------------------------------------------------------

    private static float intensity() {
        return Ds4cSettings.synthesisIntensity() * Haptics.SYNTH_GAIN;
    }

    private static boolean on(FeedbackCategory category) {
        return Ds4cSettings.isCategoryEnabled(category);
    }

    /** Keeps loop {@code id} running at {@code level} while {@code active}, and stops it otherwise. */
    private static @Nullable HapticLoop bed(Identifier id, boolean active, FeedbackCategory category,
                                            Supplier<HapticWaveform> generator, float level) {
        if (!active || !on(category)) {
            HapticLoops.stopLoop(id);
            return null;
        }
        HapticLoop loop = HapticLoops.ensure(id, () -> new HapticLoop(category, generator));
        loop.setAmplitude(level * intensity());
        return loop;
    }

    private static float horizontalSpeed(Entity entity) {
        Vec3 v = entity.getDeltaMovement();
        return (float) Math.sqrt(v.x * v.x + v.z * v.z);
    }

    // --- danger and combat -----------------------------------------------------------------------

    /** Lub-dub heartbeat below 3 hearts, faster as health drops. */
    private void tickHeartbeat(LocalPlayer player) {
        float health = player.getHealth();
        if (!on(FeedbackCategory.COMBAT) || health > 6f || health <= 0f) {
            heartbeatCooldown = 0;
            return;
        }
        if (heartbeatCooldown-- > 0) return;

        HapticWaveform beat = HapticWaveform.mix(
                HapticWaveform.thump(80f, 110, 0.85f),
                HapticWaveform.thump(70f, 120, 0.6f).delayed(190));
        Haptics.playSustained(FeedbackCategory.COMBAT, beat, intensity(), 0f);

        // 1000 ms at 3 hearts down to 450 ms near death
        int periodMs = Math.max(450, 1000 - 90 * Math.round(6f - health));
        heartbeatCooldown = periodMs / 50 - 1;
    }

    /** At full bow draw: one "ready" click, then a faint arm tremble while it's held. */
    private void tickBowDraw(LocalPlayer player) {
        boolean ready = on(FeedbackCategory.COMBAT) && player.isUsingItem()
                && player.getUseItem().getUseAnimation() == ItemUseAnimation.BOW
                && BowItem.getPowerForTime(player.getTicksUsingItem()) >= 1f;
        if (ready && !bowWasReady) {
            Haptics.play(FeedbackCategory.COMBAT, HapticWaveform.thump(190f, 40, 0.45f), intensity());
            bowTremble = 10;
        } else if (ready && --bowTremble <= 0) {
            Haptics.playSustained(FeedbackCategory.COMBAT,
                    HapticWaveform.withASR(HapticWaveform.bandNoise(150f, 120, 0.14f), 20, 60, 40), intensity(), 0f);
            bowTremble = 6 + ThreadLocalRandom.current().nextInt(4);
        }
        bowWasReady = ready;
    }

    // --- items -----------------------------------------------------------------------------------

    /**
     * A ticking compass: clicks come faster as you face the target (like a metal detector) and lean
     * towards the side it's on. Rate reads more reliably than pitch, which the hand discriminates
     * poorly.
     */
    private void tickCompass(LocalPlayer player) {
        if (!on(FeedbackCategory.ITEMS)) return;
        ItemStack main = player.getMainHandItem();
        boolean mainHand = main.getItem() instanceof CompassItem;
        ItemStack compass = mainHand ? main : player.getOffhandItem();
        if (!(compass.getItem() instanceof CompassItem)) return;
        if (compassCooldown-- > 0) return;

        BlockPos target = compassTarget(player, compass);
        if (target == null) return;
        Vec3 to = Vec3.atCenterOf(target).subtract(player.position());
        if (to.x * to.x + to.z * to.z < 4.0) return; // standing on it

        Vec3 view = player.getViewVector(1f);
        double viewLen = Math.sqrt(view.x * view.x + view.z * view.z);
        double toLen = Math.sqrt(to.x * to.x + to.z * to.z);
        if (viewLen < 1e-4) return;
        double dot = (view.x * to.x + view.z * to.z) / (viewLen * toLen);
        float alignment = (float) ((dot + 1) / 2); // 0 behind, 1 ahead

        float amp = (0.12f + 0.2f * alignment) * (mainHand ? 1f : 0.6f);
        Haptics.playSustained(FeedbackCategory.ITEMS, HapticWaveform.thump(170f, 35, amp),
                intensity(), 0.6f * HapticEvents.panTowards(player, Vec3.atCenterOf(target)));
        compassCooldown = Math.round(16 - 13 * alignment * alignment);
    }

    /** Lodestone target in this dimension, else the respawn point. */
    private static @Nullable BlockPos compassTarget(LocalPlayer player, ItemStack compass) {
        LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null && tracker.target().isPresent()) {
            GlobalPos global = tracker.target().get();
            return global.dimension().equals(player.level().dimension()) ? global.pos() : null;
        }
        var respawn = player.level().getRespawnData();
        return respawn == null ? null : respawn.pos();
    }

    /** One click as a held clock passes noon (bright) or midnight (dull). */
    private void tickClock(LocalPlayer player) {
        boolean holding = player.getMainHandItem().is(Items.CLOCK) || player.getOffhandItem().is(Items.CLOCK);
        if (!holding || !on(FeedbackCategory.ITEMS)) {
            lastClockSlot = Long.MIN_VALUE;
            return;
        }
        long time = Math.floorMod(player.level().getOverworldClockTime(), 24000L);
        long slot = ((time + 18000L) % 24000L) / 12000L; // flips at 6000 (noon) and 18000 (midnight)
        if (lastClockSlot != Long.MIN_VALUE && slot != lastClockSlot) {
            boolean noon = time >= 6000 && time < 18000;
            Haptics.play(FeedbackCategory.ITEMS, HapticWaveform.thump(noon ? 220f : 120f, 70, 0.5f), intensity());
        }
        lastClockSlot = slot;
    }

    // --- movement --------------------------------------------------------------------------------

    /** Rushing air while gliding, by speed, with gusts. */
    private void tickElytraWind(LocalPlayer player) {
        float speed = (float) player.getDeltaMovement().length();
        boolean active = player.isFallFlying() && speed > 0.8f;
        float level = Math.min(1f, (speed - 0.8f) / 3f);
        HapticLoop loop = bed(ELYTRA_WIND, active, FeedbackCategory.MOVEMENT,
                () -> HapticWaveform.bandNoise(130f, 100, 1f), 0.1f + 0.45f * level);
        if (loop != null && ThreadLocalRandom.current().nextFloat() < 0.08f) loop.accent();
    }

    /**
     * Pushing through blocks that slow you (soul sand, honey, cobwebs, powder snow, berry bushes):
     * a texture per block while you're actually trying to move.
     */
    private void tickDrag(LocalPlayer player) {
        BlockState feet = player.level().getBlockState(player.blockPosition());
        BlockState below = player.level().getBlockState(player.getOnPos());
        Block block = null;
        if (feet.is(Blocks.COBWEB) || feet.is(Blocks.POWDER_SNOW) || feet.is(Blocks.SWEET_BERRY_BUSH)) {
            block = feet.getBlock();
        } else if (player.onGround() && (below.getBlock().getSpeedFactor() < 1f || below.is(Blocks.SLIME_BLOCK))) {
            block = below.getBlock();
        }
        float push = player.input.getMoveVector().length();
        boolean active = block != null && push > 0.1f && player.getVehicle() == null;
        if (active && block != dragBlock) HapticLoops.stopLoop(DRAG); // new texture
        dragBlock = active ? block : null;
        if (!active) {
            HapticLoops.stopLoop(DRAG);
            return;
        }

        Supplier<HapticWaveform> texture;
        if (block == Blocks.HONEY_BLOCK || block == Blocks.SLIME_BLOCK) {
            texture = () -> HapticWaveform.amSine(85f, 4f, CHUNK_MS, 1f, 0.8f); // sticky pull
        } else if (block == Blocks.COBWEB) {
            texture = () -> HapticWaveform.bandNoise(180f, CHUNK_MS, 0.7f); // fine strands
        } else if (block == Blocks.POWDER_SNOW) {
            texture = () -> HapticWaveform.bandNoise(140f, CHUNK_MS, 0.8f);
        } else {
            texture = () -> HapticWaveform.bandNoise(110f, CHUNK_MS, 1f); // soul sand, berries
        }
        float level = block == Blocks.SLIME_BLOCK ? 0.22f : 0.35f;
        bed(DRAG, true, FeedbackCategory.MOVEMENT, texture, level * Math.min(1f, push));
    }

    /** A low rolling bed by speed, plus a click at every rail join (each block travelled). */
    private void tickMinecart(LocalPlayer player) {
        boolean riding = player.getVehicle() instanceof AbstractMinecart;
        if (!riding) {
            HapticLoops.stopLoop(MINECART);
            if (!(player.getVehicle() instanceof AbstractBoat)) lastVehiclePos = null;
            return;
        }
        Entity cart = player.getVehicle();
        float speed = horizontalSpeed(cart);
        bed(MINECART, speed > 0.02f, FeedbackCategory.MOVEMENT,
                () -> HapticWaveform.bandNoise(100f, CHUNK_MS, 1f), Math.min(0.35f, speed * 0.6f));

        Vec3 pos = cart.position();
        if (lastVehiclePos != null) railDistance += pos.distanceTo(lastVehiclePos);
        lastVehiclePos = pos;
        if (railDistance >= 1.0) {
            railDistance %= 1.0;
            if (on(FeedbackCategory.MOVEMENT)) {
                float amp = Math.min(0.45f, 0.15f + speed * 0.5f);
                Haptics.playSustained(FeedbackCategory.MOVEMENT, HapticWaveform.thump(150f, 30, amp), intensity(), 0f);
            }
        }
    }

    /** Hull rocking on the water: a swell that rises and falls, stronger with speed. */
    private void tickBoat(LocalPlayer player) {
        boolean riding = player.getVehicle() instanceof AbstractBoat;
        if (!riding) {
            HapticLoops.stopLoop(BOAT);
            return;
        }
        float speed = horizontalSpeed(player.getVehicle());
        float swell = 0.5f + 0.5f * Mth.sin(player.tickCount * 0.25f);
        bed(BOAT, true, FeedbackCategory.MOVEMENT,
                () -> HapticWaveform.sine(90f, CHUNK_MS, 1f), (0.03f + Math.min(0.09f, speed * 0.2f)) * swell);
    }

    /** Hoof beats at the horse's stride rate (a three-beat canter when fast) and a landing thump. */
    private void tickHorse(LocalPlayer player) {
        if (!(player.getVehicle() instanceof AbstractHorse horse) || !on(FeedbackCategory.MOVEMENT)) {
            horseAirborne = false;
            strideCooldown = 0;
            return;
        }
        boolean airborne = !horse.onGround();
        if (horseAirborne && !airborne) {
            float amp = Math.min(1f, 0.45f + (float) horse.fallDistance / 4f);
            Haptics.play(FeedbackCategory.MOVEMENT, HapticWaveform.thump(85f, 180, amp), intensity());
        }
        horseAirborne = airborne;

        float speed = horizontalSpeed(horse);
        if (airborne || speed < 0.05f) {
            strideCooldown = 0;
            return;
        }
        if (strideCooldown-- > 0) return;
        boolean canter = speed > 0.25f;
        HapticWaveform beat = canter
                ? HapticWaveform.mix(
                        HapticWaveform.thump(100f, 45, 0.35f),
                        HapticWaveform.thump(100f, 45, 0.3f).delayed(70),
                        HapticWaveform.thump(95f, 55, 0.45f).delayed(140))
                : HapticWaveform.thump(100f, 45, 0.25f);
        Haptics.playSustained(FeedbackCategory.MOVEMENT, beat, intensity(), (strideBeat++ % 2 == 0) ? -0.25f : 0.25f);
        strideCooldown = Math.max(5, Math.round(12 - speed * 14));
    }

    // --- ambient ---------------------------------------------------------------------------------

    /** Water pressure: a swelling hum that deepens the further down you are. */
    private void tickUnderwater(LocalPlayer player) {
        boolean active = player.isUnderWater();
        if (active && depthScanCooldown-- <= 0) {
            depthScanCooldown = 10;
            BlockPos.MutableBlockPos pos = BlockPos.containing(player.getEyePosition()).mutable();
            int depth = 0;
            while (depth < 24 && player.level().getFluidState(pos.move(0, 1, 0)).is(FluidTags.WATER)) depth++;
            if (Math.abs(depth - waterDepth) >= 3) {
                HapticLoop loop = HapticLoops.getLoop(UNDERWATER);
                if (loop != null) loop.accent();
            }
            waterDepth = depth;
        }
        float swell = 0.75f + 0.25f * Mth.sin(player.tickCount * 0.08f);
        // Swimming has its own strokes; the pressure hum is for hanging still in the water.
        float stillness = player.getDeltaMovement().length() < 0.08 ? 1f : 0.3f;
        bed(UNDERWATER, active, FeedbackCategory.AMBIENT,
                () -> HapticWaveform.sine(90f, CHUNK_MS, 1f),
                (0.08f + 0.17f * Math.min(1f, waterDepth / 16f)) * swell * stillness);
    }

    /**
     * Raindrops: random taps whose density follows the rain (and storm) strength, never the same
     * twice. Fades further than other beds when nothing changes, since rain goes on for minutes.
     */
    private void tickRain(LocalPlayer player) {
        Level level = player.level();
        rainStrength = level.getRainLevel(1f) * (level.isThundering() ? 1f : 0.7f);
        boolean active = rainStrength > 0.05f && level.canSeeSky(player.blockPosition().above())
                && level.getBiome(player.blockPosition()).value()
                        .getPrecipitationAt(player.blockPosition(), level.getSeaLevel()) == Biome.Precipitation.RAIN;
        HapticLoop loop = bed(RAIN, active, FeedbackCategory.AMBIENT, () -> raindrops(rainStrength), 0.3f);
        if (loop != null) {
            loop.setHabituationFloor(RAIN_HABITUATION_FLOOR);
            loop.setPan((ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.8f);
        }
    }

    private static final float RAIN_HABITUATION_FLOOR = 0.2f;
    /** Slow random walk of the shower's density, so the rain ebbs and flows between chunks. */
    private static float rainSurge = 1f;

    /**
     * One chunk of rain, layered: a faint wash, mostly small light drops, some fuller ones, the
     * odd heavy drop (lower and longer), and drops that splash off into a smaller second tap.
     */
    private static HapticWaveform raindrops(float strength) {
        var rng = ThreadLocalRandom.current();
        rainSurge = Mth.clamp(rainSurge + (rng.nextFloat() - 0.5f) * 0.5f, 0.35f, 1.4f);
        int drops = Math.round(strength * 9 * rainSurge + rng.nextFloat() * 3);
        java.util.List<HapticWaveform> parts = new java.util.ArrayList<>(drops * 2 + 1);
        parts.add(HapticWaveform.bandNoise(130f + rng.nextInt(30), CHUNK_MS, (0.05f + 0.08f * rng.nextFloat()) * rainSurge));
        for (int i = 0; i < drops; i++) {
            float kind = rng.nextFloat();
            float hz, amp;
            int ms;
            if (kind < 0.65f) { // small
                hz = 200 + rng.nextInt(70); ms = 8 + rng.nextInt(8); amp = 0.2f + 0.4f * rng.nextFloat();
            } else if (kind < 0.94f) { // full
                hz = 160 + rng.nextInt(40); ms = 15 + rng.nextInt(12); amp = 0.45f + 0.45f * rng.nextFloat();
            } else { // heavy
                hz = 110 + rng.nextInt(35); ms = 30 + rng.nextInt(15); amp = 0.8f + 0.2f * rng.nextFloat();
            }
            int at = rng.nextInt(CHUNK_MS - ms - 45);
            parts.add(HapticWaveform.thump(hz, ms, amp).delayed(at));
            if (rng.nextFloat() < 0.2f) {
                parts.add(HapticWaveform.thump(hz + 40, 8, amp * 0.35f).delayed(at + ms + 10 + rng.nextInt(25)));
            }
        }
        return HapticWaveform.mix(parts.toArray(HapticWaveform[]::new));
    }

    /** Searing heat: a slow 2 Hz throb while standing on magma without sneaking (sneaking is safe). */
    private void tickMagma(LocalPlayer player) {
        boolean active = player.onGround() && !player.isShiftKeyDown()
                && player.level().getBlockState(player.getOnPos()).is(Blocks.MAGMA_BLOCK);
        // 500 ms chunks hold one whole 2 Hz cycle, so the seams line up with the throb
        bed(MAGMA, active, FeedbackCategory.AMBIENT, () -> HapticWaveform.amSine(110f, 2f, 500, 1f, 0.8f), 0.25f);
    }

    /** Wind on high ground under open sky, by altitude, with gusts. */
    private void tickAltitudeWind(LocalPlayer player) {
        double y = player.getY();
        boolean active = y > 180 && !player.isUnderWater() && !player.isFallFlying()
                && player.level().canSeeSky(player.blockPosition().above());
        float height = (float) Math.min(1.0, (y - 180) / 120.0);
        float gust = 0.7f + 0.3f * Mth.sin(player.tickCount * 0.05f) * Mth.sin(player.tickCount * 0.13f);
        bed(ALTITUDE_WIND, active, FeedbackCategory.AMBIENT,
                () -> HapticWaveform.bandNoise(110f, CHUNK_MS, 1f), 0.35f * height * gust);
    }

    /** A soft pulse every two seconds while the player has Conduit Power. */
    private void tickConduit(LocalPlayer player) {
        if (!on(FeedbackCategory.AMBIENT) || !player.hasEffect(MobEffects.CONDUIT_POWER)) {
            conduitCooldown = 0;
            return;
        }
        if (conduitCooldown-- > 0) return;
        Haptics.playSustained(FeedbackCategory.AMBIENT, HapticWaveform.thump(110f, 140, 0.4f), intensity(), 0f);
        conduitCooldown = 40;
    }

    /** Buzzing on the side of the nearest bee within 4 blocks. */
    private void tickBees(LocalPlayer player) {
        if (!on(FeedbackCategory.AMBIENT) || beeCooldown-- > 0) return;
        beeCooldown = 3;
        Bee nearest = null;
        double best = Double.MAX_VALUE;
        for (Bee bee : player.level().getEntitiesOfClass(Bee.class, player.getBoundingBox().inflate(4.0))) {
            double d = player.distanceToSqr(bee);
            if (d < best) {
                best = d;
                nearest = bee;
            }
        }
        if (nearest == null) return;
        float proximity = (float) Math.max(0, 1.0 - Math.sqrt(best) / 4.0);
        if (proximity < 0.05f) return;
        Haptics.playSustained(FeedbackCategory.AMBIENT,
                HapticWaveform.withASR(HapticWaveform.amSine(200f, 30f, 200, 0.4f * proximity, 0.7f), 20, 150, 30),
                intensity(), 0.7f * HapticEvents.panTowards(player, nearest.position()));
    }

    /** A soft pulse every two seconds within 16 blocks of a beacon, stronger when closer. */
    private void tickBeacon(LocalPlayer player) {
        if (!on(FeedbackCategory.AMBIENT) || beaconCooldown-- > 0) return;
        beaconCooldown = 40;
        Level level = player.level();
        AABB box = player.getBoundingBox().inflate(16.0);
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getBlockState(pos).is(Blocks.BEACON)) best = Math.min(best, Vec3.atCenterOf(pos).distanceToSqr(player.position()));
        }
        if (best == Double.MAX_VALUE) return;
        float proximity = (float) Math.max(0, 1.0 - Math.sqrt(best) / 16.0);
        if (proximity < 0.05f) return;
        Haptics.playSustained(FeedbackCategory.AMBIENT, HapticWaveform.thump(120f, 120, 0.35f * proximity), intensity(), 0f);
    }

    /**
     * Standing in a portal: a swirl that builds over the 4 s it takes to travel, pulsing faster as
     * it builds. Suppressed on arrival until you step out of the destination portal.
     */
    private void tickPortal(LocalPlayer player) {
        BlockState feet = player.level().getBlockState(player.blockPosition());
        boolean inPortal = feet.is(Blocks.NETHER_PORTAL) || feet.is(Blocks.END_PORTAL);
        if (!inPortal) portalSuppressed = false;
        boolean active = inPortal && !portalSuppressed;
        portalTicks = active ? Math.min(80, portalTicks + 1) : 0;

        float ramp = portalTicks / 80f;
        // whole cycles per 500 ms chunk keep the pulse seamless: 2, 4, 6 Hz
        portalRate = ramp < 0.4f ? 2f : ramp < 0.75f ? 4f : 6f;
        HapticLoop loop = bed(PORTAL, active, FeedbackCategory.AMBIENT,
                () -> HapticWaveform.amSine(100f, portalRate, 500, 1f, 0.7f), 0.15f + 0.75f * ramp * ramp);
        if (loop != null && portalTicks % 20 == 0) loop.accent();
    }

    /** A long sweep when changing dimension. */
    private void tickDimensionChange(LocalPlayer player) {
        ResourceKey<Level> current = player.level().dimension();
        if (lastDimension == null || current.equals(lastDimension)) {
            lastDimension = current;
            return;
        }
        lastDimension = current;
        HapticLoops.stopLoop(PORTAL);
        portalTicks = 0;
        portalSuppressed = true;
        if (on(FeedbackCategory.AMBIENT)) {
            Haptics.play(FeedbackCategory.AMBIENT,
                    HapticWaveform.withASR(HapticWaveform.amSine(90f, 2f, 1200, 0.8f, 0.6f), 60, 900, 240), intensity());
        }
    }
}

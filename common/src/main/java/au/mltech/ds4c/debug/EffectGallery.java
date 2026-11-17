package au.mltech.ds4c.debug;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.events.TimedFacts;
import au.mltech.ds4c.haptics.HapticEvents;
import au.mltech.ds4c.haptics.HapticSoundRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Plays individual DS4C effects on demand, so each can be judged on its own when tuning.
 * <ul>
 *   <li>Trigger effects: every DS4C rule has a matching top-priority gallery rule (see
 *   {@code tools/generate_rules.py}); playing one opens its {@code ds4c:gallery/<name>} fact for a
 *   few seconds. Controlify only applies trigger effects in-world with no screen open and with
 *   controller input active, so press the trigger after closing chat.</li>
 *   <li>Grip haptics: the synthesised event haptics, with fixed inputs.</li>
 *   <li>Sounds: plays a game sound at the player, so its passthrough and overlay play as in game.</li>
 * </ul>
 * Effects still respect DS4C's switches and category toggles. Off unless the effect gallery option
 * is on.
 */
public final class EffectGallery {
    private static final String TRIGGER_INDEX = "/assets/ds4c/gallery_triggers.json";
    private static final Map<String, Consumer<LocalPlayer>> HAPTICS = new LinkedHashMap<>();
    private static @Nullable List<String> triggerNames;
    private static @Nullable Identifier activeTrigger;

    static {
        Map<String, Block> surfaces = new LinkedHashMap<>();
        surfaces.put("stone", Blocks.STONE);
        surfaces.put("wood", Blocks.OAK_PLANKS);
        surfaces.put("wool", Blocks.WOOL.white());
        surfaces.put("sand", Blocks.SAND);
        surfaces.put("gravel", Blocks.GRAVEL);
        surfaces.put("grass", Blocks.GRASS_BLOCK);
        surfaces.put("snow", Blocks.SNOW_BLOCK);
        surfaces.put("mud", Blocks.MUD);
        surfaces.put("slime", Blocks.SLIME_BLOCK);
        surfaces.put("ice", Blocks.ICE);
        surfaces.forEach((name, block) -> {
            HAPTICS.put("footstep_" + name, p -> HapticEvents.footstep(block.defaultBlockState(), false, 1f));
            HAPTICS.put("footstep_sprint_" + name, p -> HapticEvents.footstep(block.defaultBlockState(), true, 1f));
            HAPTICS.put("footstep_sprint_jump_" + name, p -> HapticEvents.footstep(block.defaultBlockState(), true, 1.6f));
            HAPTICS.put("small_landing_" + name, p -> HapticEvents.landing(block.defaultBlockState(), 1.25, false, false));
        });
        for (int blocks : new int[]{2, 4, 8, 16}) {
            HAPTICS.put("landing_" + blocks, p -> HapticEvents.landing(Blocks.STONE.defaultBlockState(), blocks, false, false));
        }
        HAPTICS.put("mace_slam", p -> HapticEvents.landing(Blocks.STONE.defaultBlockState(), 6, true, false));
        HAPTICS.put("damage_generic", p -> HapticEvents.onLocalPlayerDamaged(p, sources(p).generic()));
        HAPTICS.put("damage_fall", p -> HapticEvents.onLocalPlayerDamaged(p, sources(p).fall()));
        HAPTICS.put("damage_fire", p -> HapticEvents.onLocalPlayerDamaged(p, sources(p).onFire()));
        HAPTICS.put("damage_drown", p -> HapticEvents.onLocalPlayerDamaged(p, sources(p).drown()));
        HAPTICS.put("damage_wither", p -> HapticEvents.onLocalPlayerDamaged(p, sources(p).wither()));
        HAPTICS.put("damage_freeze", p -> HapticEvents.onLocalPlayerDamaged(p, sources(p).freeze()));
        HAPTICS.put("damage_lightning", p -> HapticEvents.onLocalPlayerDamaged(p, sources(p).lightningBolt()));
        HAPTICS.put("explosion_near", p -> HapticEvents.explosion(1f, 0f));
        HAPTICS.put("explosion_mid", p -> HapticEvents.explosion(0.4f, 0f));
        HAPTICS.put("explosion_far_left", p -> HapticEvents.explosion(0.12f, -1f));
        HAPTICS.put("explosion_far_right", p -> HapticEvents.explosion(0.12f, 1f));
        Map<String, Item> tools = new LinkedHashMap<>();
        tools.put("pickaxe", Items.IRON_PICKAXE);
        tools.put("shovel", Items.IRON_SHOVEL);
        tools.put("axe", Items.IRON_AXE);
        tools.put("hoe", Items.IRON_HOE);
        tools.put("shears", Items.SHEARS);
        tools.put("hand", Items.AIR);
        tools.forEach((name, item) -> HAPTICS.put("mining_" + name, p -> {
            // one block's ten crack stages, 150 ms apart
            for (int stage = 0; stage < 10; stage++) {
                int s = stage;
                CompletableFuture.runAsync(() -> HapticEvents.breakPulse(s, new ItemStack(item)),
                        CompletableFuture.delayedExecutor(stage * 150L, TimeUnit.MILLISECONDS, Minecraft.getInstance()));
            }
        }));
        HAPTICS.put("wrong_tool", p -> HapticEvents.onIneffectiveTool());
        HAPTICS.put("shears_snip", p -> HapticEvents.onShearsSnip());
        HAPTICS.put("lightning_near", p -> HapticEvents.lightning(1f, 0f));
        HAPTICS.put("lightning_far", p -> HapticEvents.lightning(0.3f, 0f));
        HAPTICS.put("fishing_approach", p -> HapticEvents.onFishingCue(0.18f, 110f, 80));
        HAPTICS.put("fishing_nibble", p -> HapticEvents.onFishingCue(0.24f, 130f, 60));
        HAPTICS.put("fishing_bob", p -> HapticEvents.onFishingCue(0.30f, 160f, 50));
    }

    private EffectGallery() {
    }

    private static DamageSources sources(LocalPlayer player) {
        return player.damageSources();
    }

    public static Collection<String> triggerNames() {
        if (triggerNames == null) {
            List<String> names = new ArrayList<>();
            try (InputStream in = EffectGallery.class.getResourceAsStream(TRIGGER_INDEX)) {
                if (in != null) {
                    JsonArray entries = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                    for (JsonElement entry : entries) names.add(entry.getAsJsonObject().get("name").getAsString());
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to read {}", TRIGGER_INDEX, e);
            }
            triggerNames = Collections.unmodifiableList(names);
        }
        return triggerNames;
    }

    public static Collection<String> hapticNames() {
        return HAPTICS.keySet();
    }

    public static Set<Identifier> soundIds() {
        return HapticSoundRegistry.mappedSounds();
    }

    public static boolean playTrigger(String name, int seconds) {
        if (!Ds4cSettings.effectGallery() || !triggerNames().contains(name)) return false;
        stopTrigger();
        activeTrigger = Constants.id("gallery/" + name);
        TimedFacts.pulse(activeTrigger, seconds * 20);
        tell("ds4c.gallery.playing_trigger", name, seconds);
        return true;
    }

    public static void stopTrigger() {
        if (activeTrigger != null) {
            TimedFacts.remove(activeTrigger);
            activeTrigger = null;
        }
    }

    public static boolean playHaptic(String name) {
        Consumer<LocalPlayer> haptic = HAPTICS.get(name);
        LocalPlayer player = Minecraft.getInstance().player;
        if (!Ds4cSettings.effectGallery() || haptic == null || player == null) return false;
        haptic.accept(player);
        return true;
    }

    public static boolean playSound(String id) {
        Identifier soundId = Identifier.tryParse(id);
        LocalPlayer player = Minecraft.getInstance().player;
        if (!Ds4cSettings.effectGallery() || soundId == null || player == null) return false;
        SoundEvent event = SoundEvent.createVariableRangeEvent(soundId);
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
                event, SoundSource.PLAYERS, 1f, 1f, RandomSource.create(), player.getX(), player.getY(), player.getZ()));
        return true;
    }

    static void tell(String key, Object... args) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.translatable(key, args));
    }
}

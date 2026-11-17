package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.mixin.WeighedSoundEventsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decoded PCM for game sounds, kept separately from the sound engine. The engine uploads its
 * buffers to OpenAL and drops the data, so we decode the .ogg ourselves (16-bit LE PCM) and keep it.
 */
public final class SoundPcmCache {
    private static final Map<Identifier, CompletableFuture<Optional<Pcm>>> CACHE = new ConcurrentHashMap<>();

    private SoundPcmCache() {
    }

    /**
     * @param path resource path of the .ogg, e.g. {@code minecraft:sounds/random/click.ogg}
     */
    static CompletableFuture<Optional<Pcm>> get(Identifier path) {
        return CACHE.computeIfAbsent(path, p -> CompletableFuture.supplyAsync(() -> decode(p), Util.nonCriticalIoPool()));
    }

    /**
     * The decoded sound if it's ready now, else null (and decoding starts, if it hadn't).
     */
    public static @org.jspecify.annotations.Nullable Pcm getNow(Identifier path) {
        CompletableFuture<Optional<Pcm>> future = get(path);
        return future.isDone() && !future.isCompletedExceptionally() ? future.join().orElse(null) : null;
    }

    /**
     * Decodes every variant of the sounds with an exact passthrough mapping in the background, so
     * their first play isn't late (haptics trailing the sound are noticed sooner than haptics
     * leading it). Sounds matched only by suffix rules (block breaks, doors...) decode on first use.
     */
    public static void preloadMapped(SoundManager soundManager) {
        int count = 0;
        java.util.Set<Identifier> ids = new java.util.HashSet<>(HapticSoundRegistry.view().keySet());
        ids.addAll(au.mltech.ds4c.speaker.ControllerSpeaker.routedSounds());
        for (Identifier id : ids) {
            WeighedSoundEvents event = soundManager.getSoundEvent(id);
            if (event != null) count += preload(event, 0);
        }
        Constants.LOG.debug("Decoding {} sound variants for haptics", count);
    }

    private static int preload(WeighedSoundEvents event, int depth) {
        int count = 0;
        for (Weighted<Sound> entry : ((WeighedSoundEventsAccessor) event).ds4c$getList()) {
            if (entry instanceof Sound sound && !sound.shouldStream()) {
                get(sound.getPath());
                count++;
            } else if (entry instanceof WeighedSoundEvents nested && depth < 4) {
                count += preload(nested, depth + 1);
            }
        }
        return count;
    }

    /**
     * Resources reloaded: sound files may have changed.
     */
    public static void clear() {
        CACHE.clear();
    }

    private static Optional<Pcm> decode(Identifier path) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(path);
             JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
            ByteBuffer buffer = stream.readAll();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return Optional.of(new Pcm(data, stream.getFormat()));
        } catch (Exception e) {
            Constants.LOG.debug("Could not decode {} for haptics", path, e);
            return Optional.empty();
        }
    }

    public record Pcm(byte[] data, AudioFormat format) {
    }
}

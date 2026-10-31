package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
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
    record Pcm(byte[] data, AudioFormat format) {
    }

    private static final Map<Identifier, CompletableFuture<Optional<Pcm>>> CACHE = new ConcurrentHashMap<>();

    private SoundPcmCache() {
    }

    /** @param path resource path of the .ogg, e.g. {@code minecraft:sounds/random/click.ogg} */
    static CompletableFuture<Optional<Pcm>> get(Identifier path) {
        return CACHE.computeIfAbsent(path, p -> CompletableFuture.supplyAsync(() -> decode(p), Util.nonCriticalIoPool()));
    }

    /** Resources reloaded: sound files may have changed. */
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
            Constants.LOG.debug("DS4C could not decode {} for haptics", path, e);
            return Optional.empty();
        }
    }
}

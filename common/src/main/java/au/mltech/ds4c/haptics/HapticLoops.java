package au.mltech.ds4c.haptics;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Looping haptic beds (rain, underwater, rides...). Ticked and cleared by {@link Haptics}. */
public final class HapticLoops {
    private static final Map<Identifier, HapticLoop> LOOPS = new LinkedHashMap<>();

    private HapticLoops() {
    }

    public static void startLoop(Identifier id, HapticLoop loop) {
        LOOPS.put(id, loop);
    }

    public static void stopLoop(Identifier id) {
        LOOPS.remove(id);
    }

    public static boolean isLoopActive(Identifier id) {
        return LOOPS.containsKey(id);
    }

    public static @Nullable HapticLoop getLoop(Identifier id) {
        return LOOPS.get(id);
    }

    static void clear() {
        LOOPS.clear();
    }

    /** Queue the next chunk of each loop only once the previous one has been consumed. */
    static void tick() {
        for (HapticLoop loop : LOOPS.values()) {
            loop.onTickConsumed();
            if (loop.shouldQueue()) {
                Haptics.play(loop.nextChunk(), loop.base().format(), 1f);
                loop.onQueued();
            }
        }
    }
}

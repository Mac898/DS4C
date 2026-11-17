package au.mltech.ds4c.haptics;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * Looping haptic beds (rain, underwater, rides...). Ticked and cleared by {@link Haptics}.
 * Only the {@link #MAX_AUDIBLE} most important loops play at once (highest tier, then loudest);
 * the rest keep their state silently until they're among the top again.
 */
public final class HapticLoops {
    static final int MAX_AUDIBLE = 2;
    private static final Map<Identifier, HapticLoop> LOOPS = new LinkedHashMap<>();

    private HapticLoops() {
    }

    /**
     * Starts the loop if it isn't running and returns it.
     */
    public static HapticLoop ensure(Identifier id, Supplier<HapticLoop> factory) {
        return LOOPS.computeIfAbsent(id, k -> factory.get());
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

    static void tick() {
        if (LOOPS.isEmpty()) return;
        List<HapticLoop> ranked = new ArrayList<>(LOOPS.values());
        ranked.sort(Comparator
                .comparingInt((HapticLoop l) -> HapticTier.of(l.category()).ordinal())
                .thenComparingDouble(HapticLoop::effectiveAmplitude)
                .reversed());
        for (int i = 0; i < ranked.size(); i++) {
            HapticLoop loop = ranked.get(i);
            if (loop.tick()) loop.queueNext(i < MAX_AUDIBLE);
        }
    }
}

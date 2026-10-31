package au.mltech.ds4c.events;

import dev.isxander.controlify.api.contextual.ContextualStateSink;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Facts that are true for a window of client ticks after an event, so one-shot effects (a swing,
 * a crit, a bow release) can be expressed as ordinary contextual rules.
 * <p>
 * Time only moves in {@link #tick()}, called once at the start of every client tick. Contributors
 * just read the current window, so it doesn't matter how many times Controlify evaluates a domain
 * per tick. Client thread only.
 */
public final class TimedFacts {
    private static final Map<Identifier, Window> WINDOWS = new HashMap<>();
    private static long now = 0;

    private TimedFacts() {
    }

    private record Window(long start, long end) {
        boolean contains(long tick) {
            return tick >= this.start && tick < this.end;
        }
    }

    public static long now() {
        return now;
    }

    public static void tick() {
        now++;
        Iterator<Window> it = WINDOWS.values().iterator();
        while (it.hasNext()) {
            if (it.next().end <= now) it.remove();
        }
    }

    /** Fact is true from this tick for {@code durationTicks} ticks. Re-pulsing restarts the window. */
    public static void pulse(Identifier fact, int durationTicks) {
        schedule(fact, 0, durationTicks);
    }

    /** Fact is true from {@code delayTicks} after this tick, for {@code durationTicks} ticks. */
    public static void schedule(Identifier fact, int delayTicks, int durationTicks) {
        long start = now + Math.max(0, delayTicks);
        WINDOWS.put(fact, new Window(start, start + Math.max(1, durationTicks)));
    }

    /** True while the fact's window is open or still scheduled. */
    public static boolean isPending(Identifier fact) {
        Window window = WINDOWS.get(fact);
        return window != null && window.end > now;
    }

    public static void remove(Identifier fact) {
        WINDOWS.remove(fact);
    }

    public static void clear() {
        WINDOWS.clear();
    }

    public static void contribute(ContextualStateSink sink) {
        for (Map.Entry<Identifier, Window> entry : WINDOWS.entrySet()) {
            if (entry.getValue().contains(now)) {
                sink.contributeFact(entry.getKey());
            }
        }
    }
}

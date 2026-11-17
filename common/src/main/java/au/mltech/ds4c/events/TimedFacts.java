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
    /**
     * Whether facts were already read this tick. Most events (attacks, item use, block placing)
     * happen after Controlify has evaluated the trigger rules for the tick, so a window starting
     * "now" would lose its first tick, and a one-tick pulse would never be seen at all.
     */
    private static boolean contributedThisTick = false;

    private TimedFacts() {
    }

    public static long now() {
        return now;
    }

    public static void tick() {
        now++;
        contributedThisTick = false;
        Iterator<Window> it = WINDOWS.values().iterator();
        while (it.hasNext()) {
            if (it.next().end <= now) it.remove();
        }
    }

    /**
     * Fact is true from this tick for {@code durationTicks} ticks. Re-pulsing restarts the window.
     */
    public static void pulse(Identifier fact, int durationTicks) {
        schedule(fact, 0, durationTicks);
    }

    /**
     * Fact is true for {@code durationTicks} evaluations, starting {@code delayTicks} after the
     * next one.
     */
    public static void schedule(Identifier fact, int delayTicks, int durationTicks) {
        long start = (contributedThisTick ? now + 1 : now) + Math.max(0, delayTicks);
        WINDOWS.put(fact, new Window(start, start + Math.max(1, durationTicks)));
    }

    /**
     * True while the fact's window is open or still scheduled.
     */
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
        contributedThisTick = true;
        for (Map.Entry<Identifier, Window> entry : WINDOWS.entrySet()) {
            if (entry.getValue().contains(now)) {
                sink.contributeFact(entry.getKey());
            }
        }
    }

    private record Window(long start, long end) {
        boolean contains(long tick) {
            return tick >= this.start && tick < this.end;
        }
    }
}

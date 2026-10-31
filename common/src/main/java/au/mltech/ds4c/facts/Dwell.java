package au.mltech.ds4c.facts;

/**
 * Holds a value until a different one has persisted for a few ticks, so trigger state driven by
 * what the crosshair passes over doesn't flicker. Every change moves the trigger motor, and rapid
 * changes are felt as noise rather than information.
 * <p>
 * Timed by the caller's tick counter, so it is safe to update more than once per tick.
 */
final class Dwell {
    private final int ticks;
    private int held;
    private int candidate;
    private long candidateSince;

    Dwell(int ticks, int initial) {
        this.ticks = ticks;
        this.held = initial;
        this.candidate = initial;
    }

    int update(int value, long now) {
        if (value == held) {
            candidate = held;
            return held;
        }
        if (value != candidate) {
            candidate = value;
            candidateSince = now;
        }
        if (now - candidateSince >= ticks) {
            held = value;
        }
        return held;
    }

    void reset(int value) {
        held = value;
        candidate = value;
    }
}

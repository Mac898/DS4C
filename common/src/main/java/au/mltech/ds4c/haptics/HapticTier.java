package au.mltech.ds4c.haptics;

import au.mltech.ds4c.FeedbackCategory;

/**
 * Priority of a haptic, used by {@link HapticBudget} to decide what plays when several effects
 * overlap. People can follow about three simultaneous tactile signals and same-band layers mask
 * each other, so higher tiers duck lower ones instead of everything summing.
 */
public enum HapticTier {
    /** Weather, water, proximity cues. */
    AMBIENT,
    /** Footsteps, landings, rides. Very frequent, so they never duck anything. */
    MOVEMENT,
    /** The player's own actions: mining, placing, using items, UI. */
    ACTION,
    /** Damage, combat, explosions, threats. */
    DANGER;

    public static HapticTier of(FeedbackCategory category) {
        return switch (category) {
            case COMBAT -> DANGER;
            case ITEMS, ENVIRONMENT, UI -> ACTION;
            case MOVEMENT -> MOVEMENT;
            case AMBIENT -> AMBIENT;
        };
    }

    /** Whether an effect of this tier ducks lower tiers while it plays. */
    boolean ducksLowerTiers() {
        return this.ordinal() >= ACTION.ordinal();
    }
}

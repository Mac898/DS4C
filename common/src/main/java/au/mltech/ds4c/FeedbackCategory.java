package au.mltech.ds4c;

import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * User-toggleable groups of DS4C feedback. Each category is exposed to the contextual rule engine
 * as a fact ({@code ds4c:category/<name>}) that every DS4C rule in that category requires.
 */
public enum FeedbackCategory {
    MOVEMENT,
    COMBAT,
    AMBIENT,
    ITEMS,
    ENVIRONMENT,
    /** HD haptics only: pickups, level-ups, toasts, workstation results. */
    UI;

    private final Identifier factId = Constants.id("category/" + this.name().toLowerCase(Locale.ROOT));

    public Identifier factId() {
        return this.factId;
    }
}

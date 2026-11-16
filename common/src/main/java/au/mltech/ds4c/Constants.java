package au.mltech.ds4c;

import au.mltech.ds4c.controlify.BowEffect;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Constants {
    public static final String MOD_ID = "ds4c";
    public static final String MOD_NAME = "DS4C";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static final String EFFECTS_PACK_NAME_KEY = "ds4c.pack.effects.name";

    /**
     * Built-in resource pack holding DS4C's contextual facts and trigger-effect rules: the one using
     * the bow effect if Controlify has it, else the same rules with a weapon-effect fallback.
     */
    public static Identifier effectsPackId() {
        return id(BowEffect.isSupported() ? "effects" : "effects_no_bow");
    }

    private Constants() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

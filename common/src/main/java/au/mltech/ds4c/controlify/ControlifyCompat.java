package au.mltech.ds4c.controlify;

import au.mltech.ds4c.Constants;

/**
 * Checks at startup that DS4C's hooks into Controlify internals are in place.
 */
public final class ControlifyCompat {
    /**
     * Set by {@link ControlifyMixinPlugin}; game code reads the properties, never the plugin class.
     */
    static final String APPLIED_PROPERTY_PREFIX = "ds4c.controlify.applied.";

    private ControlifyCompat() {
    }

    public static void selfCheck() {
        // load the targets (without initialising them) so their mixins have been applied
        load("dev.isxander.controlify.controller.haptic.HDHapticComponent");
        load("dev.isxander.controlify.driver.sdl.SDLCommonDriver");

        if (!(HdHaptics.INSTANCE instanceof HdHaptics.Upstream)) {
            check("HD haptics", "nothing will play on the grips",
                    "HDHapticComponentMixin");
            check("Short HD haptic clip length", "short effects of the same format queue behind each other instead of overlapping",
                    "SDLCommonDriverClipLengthMixin", "ds4c$clipTicksQueued", "ds4c$clipTicksCreated");
        }
        check("Controller speaker", "sounds stay on the main mix",
                "SDLCommonDriverSpeakerMixin", "ds4c$attachSpeaker", "ds4c$detachSpeaker");
        check("Per-controller audio stream name", "OS audio routing can't tell controllers apart",
                "SDLCommonDriverStreamNameMixin", "ds4c$nameStream", "ds4c$resetStreamName");

        if (!BowEffect.isSupported()) {
            Constants.LOG.info("Controlify has no bow trigger effect; pull-through draw uses the weapon effect instead");
        }
    }

    static boolean applied(String key) {
        return Boolean.getBoolean(APPLIED_PROPERTY_PREFIX + key);
    }

    private static void check(String feature, String consequence, String... keys) {
        for (String key : keys) {
            if (!applied(key)) {
                Constants.LOG.warn("{} is off: this Controlify version doesn't match DS4C's hook ({}), so {}",
                        feature, key, consequence);
                return;
            }
        }
    }

    private static void load(String className) {
        try {
            Class.forName(className, false, ControlifyCompat.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            Constants.LOG.warn("Couldn't load {}: {}", className, e.toString());
        }
    }
}

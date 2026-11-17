package au.mltech.ds4c.api;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lets another mod mark more sounds as personal, so DS4C moves them from the main mix to the
 * DualSense speaker. DS4C still owns the output: the speaker volume (0 keeps everything on the main
 * mix), the high-pass filter, and the rule that a sound plays on one or the other, never both.
 * <p>
 * Register rules during mod initialisation, before resources load, so their sounds are decoded
 * ahead of time; a sound that isn't decoded yet when it starts stays on the main mix.
 */
public final class SpeakerRouting {
    private static final List<Rule> RULES = new CopyOnWriteArrayList<>();

    private SpeakerRouting() {
    }

    public static void register(Rule rule) {
        RULES.add(rule);
    }

    /**
     * True if any registered rule routes this sound to the speaker.
     */
    public static boolean isPersonal(SoundInstance instance) {
        for (Rule rule : RULES) {
            if (rule.isPersonal(instance)) return true;
        }
        return false;
    }

    /**
     * Every sound id the registered rules may route.
     */
    public static Set<Identifier> sounds() {
        Set<Identifier> all = new HashSet<>();
        for (Rule rule : RULES) all.addAll(rule.sounds());
        return all;
    }

    /**
     * One mod's routing rule.
     */
    public interface Rule {
        /**
         * Every sound id this rule may route, so DS4C can decode them ahead of time.
         */
        Set<Identifier> sounds();

        /**
         * Whether this sound, as it starts, is personal to the local player and should play on the
         * speaker instead of the main mix. Called on the client thread; the rule does its own checks
         * (whose sound it is, whether its mode is on).
         */
        boolean isPersonal(SoundInstance instance);
    }
}

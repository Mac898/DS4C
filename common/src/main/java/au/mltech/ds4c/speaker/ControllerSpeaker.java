package au.mltech.ds4c.speaker;

import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.api.SpeakerRouting;
import au.mltech.ds4c.controlify.SpeakerOutput;
import au.mltech.ds4c.haptics.SoundPcmCache;
import dev.isxander.controlify.api.ControlifyApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import javax.sound.sampled.AudioFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The DualSense's built-in speaker. A few sounds that belong in the player's hands (a bow
 * loosing, a crossbow winding, a tool breaking, a bundle, a book, the spyglass, the level-up
 * chime) move from the main mix to the controller speaker. Pickups keep their sound on the main
 * mix and get a quieter, brighter echo from the controller just after it, as if the item lands in
 * your hand (Super Mario Galaxy did this with the Wii Remote's Star Bits).
 * <p>
 * A sound is never played on both: the speaker can't be assumed to be in sync with the main
 * audio (Bluetooth audio bridges on Linux add their own latency). Speaker volume 0 keeps
 * everything on the main mix, and so does a controller without a usable speaker. A sound that
 * hasn't been decoded yet also stays on the main mix rather than arriving late.
 */
public final class ControllerSpeaker {
    /**
     * Played on the speaker instead of the main mix, when they come from the player.
     */
    private static final Set<Identifier> MOVED = new HashSet<>();
    /**
     * Stay on the main mix, plus an echo on the speaker.
     */
    private static final Set<Identifier> ECHOED = new HashSet<>();

    /**
     * When the echo should be heard, after the main-mix sound. Inside about 35 ms the two fuse into
     * one sound that moves into your hand; at 80 ms they were heard as two separate sounds.
     */
    private static final int ECHO_MS = 35;
    private static final float ECHO_GAIN = 0.55f;
    private static final float ECHO_PITCH = 1.25f;
    /**
     * The speaker has no bass: cut below this so it doesn't distort or rattle the shell.
     */
    private static final float HIGH_PASS_HZ = 250f;

    static {
        for (SoundEvent e : new SoundEvent[]{SoundEvents.ARROW_SHOOT, SoundEvents.CROSSBOW_SHOOT,
                SoundEvents.SPYGLASS_USE, SoundEvents.SPYGLASS_STOP_USING, SoundEvents.BOOK_PAGE_TURN,
                SoundEvents.BUNDLE_INSERT, SoundEvents.BUNDLE_INSERT_FAIL, SoundEvents.BUNDLE_REMOVE_ONE,
                SoundEvents.BUNDLE_DROP_CONTENTS, SoundEvents.PLAYER_LEVELUP}) {
            MOVED.add(e.location());
        }
        for (Holder<SoundEvent> h : java.util.List.of(SoundEvents.CROSSBOW_LOADING_START, SoundEvents.CROSSBOW_LOADING_MIDDLE,
                SoundEvents.CROSSBOW_LOADING_END, SoundEvents.CROSSBOW_QUICK_CHARGE_1, SoundEvents.CROSSBOW_QUICK_CHARGE_2,
                SoundEvents.CROSSBOW_QUICK_CHARGE_3, SoundEvents.ITEM_BREAK)) {
            MOVED.add(h.value().location());
        }
        ECHOED.add(SoundEvents.ITEM_PICKUP.location());
        ECHOED.add(SoundEvents.EXPERIENCE_ORB_PICKUP.location());
    }

    private ControllerSpeaker() {
    }

    /**
     * Every sound the speaker may play, so their variants can be decoded ahead of time.
     */
    public static Set<Identifier> routedSounds() {
        Set<Identifier> all = new HashSet<>(MOVED);
        all.addAll(ECHOED);
        all.addAll(SpeakerRouting.sounds());
        return all;
    }

    /**
     * Called as a sound starts, after subtitles are notified.
     *
     * @return true if the sound now plays on the speaker and the main mix should skip it
     */
    public static boolean onSoundPlayed(SoundInstance instance, Sound sound) {
        if (sound.shouldStream() || !isActive()) return false;
        Identifier id = instance.getIdentifier();
        boolean moved = MOVED.contains(id);
        boolean echoed = ECHOED.contains(id);
        // another mod's rule (api/SpeakerRouting) does its own check of whose sound it is
        boolean routed = !moved && !echoed && SpeakerRouting.isPersonal(instance);
        if (!moved && !echoed && !routed) return false;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (!routed && !isFromPlayer(instance, player, moved ? 0.25 : 9.0)) return false;

        SoundPcmCache.Pcm pcm = SoundPcmCache.getNow(sound.getPath());
        if (pcm == null) return false; // still decoding: play it normally rather than late

        float volume = Math.min(1f, instance.getVolume())
                * Minecraft.getInstance().options.getFinalSoundSourceVolume(instance.getSource())
                * Ds4cSettings.speakerVolume();
        if (moved || routed) {
            play(pcm, volume, instance.getPitch(), 0);
            return true;
        }
        int delay = Math.max(0, ECHO_MS - Ds4cSettings.speakerLatencyMs());
        play(pcm, volume * ECHO_GAIN, instance.getPitch() * ECHO_PITCH, delay);
        return false;
    }

    private static boolean isActive() {
        return Ds4cSettings.speakerVolume() > 0.001f
                && ControlifyApi.get().currentInputMode().isController()
                && SpeakerOutput.isAvailable();
    }

    private static boolean isFromPlayer(SoundInstance instance, LocalPlayer player, double maxDistanceSqr) {
        if (instance.isRelative()) return true; // UI sounds (bundles, books)
        double dx = instance.getX() - player.getX();
        double dy = instance.getY() - player.getY();
        double dz = instance.getZ() - player.getZ();
        return dx * dx + dy * dy + dz * dz < maxDistanceSqr;
    }

    private static void play(SoundPcmCache.Pcm pcm, float gain, float pitch, int delayMs) {
        if (gain <= 0.001f) return;
        byte[] mono = render(pcm, gain);
        if (mono == null) return;
        // Pitch by playing at a different rate; SDL resamples to the device rate.
        float rate = pcm.format().getSampleRate() * Math.max(0.5f, Math.min(2f, pitch));
        if (delayMs <= 0) {
            SpeakerOutput.playPcm(mono, rate);
        } else {
            CompletableFuture.runAsync(() -> SpeakerOutput.playPcm(mono, rate),
                    CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS, Minecraft.getInstance()));
        }
    }

    /**
     * 16-bit little-endian PCM (mono or stereo) to high-passed mono at {@code gain}.
     */
    private static byte[] render(SoundPcmCache.Pcm pcm, float gain) {
        AudioFormat f = pcm.format();
        if (f.getSampleSizeInBits() != 16 || f.isBigEndian()) return null;
        int channels = Math.max(1, f.getChannels());
        byte[] in = pcm.data();
        int frames = in.length / (2 * channels);
        byte[] out = new byte[frames * 2];
        float rc = 1f / (2f * (float) Math.PI * HIGH_PASS_HZ);
        float dt = 1f / f.getSampleRate();
        float alpha = rc / (rc + dt);
        float prevIn = 0f, prevOut = 0f;
        for (int i = 0; i < frames; i++) {
            float sum = 0f;
            for (int c = 0; c < channels; c++) {
                int o = (i * channels + c) * 2;
                sum += (short) ((in[o] & 0xFF) | (in[o + 1] << 8));
            }
            float x = sum / channels;
            float y = alpha * (prevOut + x - prevIn);
            prevIn = x;
            prevOut = y;
            int s = Math.round(y * gain);
            s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            out[i * 2] = (byte) s;
            out[i * 2 + 1] = (byte) (s >> 8);
        }
        return out;
    }
}

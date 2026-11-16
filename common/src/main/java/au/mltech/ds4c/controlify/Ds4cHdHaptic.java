package au.mltech.ds4c.controlify;

import javax.sound.sampled.AudioFormat;

/** Added to Controlify's HD haptic component when it has no public PCM API of its own. */
public interface Ds4cHdHaptic {
    boolean ds4c$canPlay();

    void ds4c$playPcm(byte[] pcm, AudioFormat format);
}

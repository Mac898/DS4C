package au.mltech.ds4c.controlify;

import au.mltech.ds4c.Constants;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.controller.haptic.HDHapticComponent;
import dev.isxander.sdl.Sdl;
import dev.isxander.sdl.SdlAudio;
import dev.isxander.sdl.SdlAudioDeviceId;
import dev.isxander.sdl.SdlAudioSpec;
import dev.isxander.sdl.SdlAudioStreamHandle;
import net.minecraft.client.Minecraft;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The DualSense speaker, played through DS4C's own SDL streams bound to the audio device Controlify
 * opened for HD haptics. Channels 3-4 of that device are the actuators; the speaker is fed from
 * channel 2 (channel 1 appears unused, so both get the audio).
 */
public final class SpeakerOutput {
    private static final Map<HDHapticComponent, SpeakerOutput> OUTPUTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int[] CHANNEL_MAP = {0, 0, -1, -1};
    private static final int MAX_STREAMS = 8;

    private final Sdl sdl;
    private final SdlAudioDeviceId device;
    private final SdlAudioSpec deviceSpec;
    private final List<SdlAudioStreamHandle> streams = new ArrayList<>();
    private boolean closed;
    private boolean loggedError;

    private SpeakerOutput(Sdl sdl, SdlAudioDeviceId device, SdlAudioSpec deviceSpec) {
        this.sdl = sdl;
        this.device = device;
        this.deviceSpec = deviceSpec;
    }

    /** Called by the driver mixin once the controller's components exist. */
    public static void attach(HDHapticComponent component, Sdl sdl, SdlAudioDeviceId device, SdlAudioSpec deviceSpec) {
        if (deviceSpec.channels() != CHANNEL_MAP.length) return;
        SpeakerOutput old = OUTPUTS.put(component, new SpeakerOutput(sdl, device, deviceSpec));
        if (old != null) old.close();
    }

    /** Called by the driver mixin before Controlify closes the audio device. */
    public static void detach(HDHapticComponent component) {
        SpeakerOutput output = OUTPUTS.remove(component);
        if (output != null) output.close();
    }

    /** Whether the current controller has a speaker DS4C can play on. */
    public static boolean isAvailable() {
        return current() != null;
    }

    /** 16-bit signed little-endian mono PCM. Plays on the client thread. */
    public static void playPcm(byte[] pcm, float sampleRate) {
        if (pcm.length == 0) return;
        Minecraft.getInstance().execute(() -> {
            SpeakerOutput output = current();
            if (output != null) output.play(pcm, Math.round(sampleRate));
        });
    }

    private static SpeakerOutput current() {
        return ControlifyApi.get().getCurrentController()
                .flatMap(ControllerEntity::hdHaptics)
                .map(OUTPUTS::get)
                .orElse(null);
    }

    private void play(byte[] pcm, int sampleRate) {
        if (closed) return;
        SdlAudio audio = sdl.audio();
        SdlAudioSpec spec = new SdlAudioSpec(sampleRate, SdlAudio.SDL_AUDIO_S16LE, 1);
        SdlAudioStreamHandle stream = idleStream(audio);
        if (stream == null) {
            if (streams.size() >= MAX_STREAMS) return; // everything still playing: drop it
            stream = audio.SDL_CreateAudioStream(spec, deviceSpec);
            if (stream == null) {
                error("create");
                return;
            }
            if (!audio.SDL_BindAudioStream(device, stream)) {
                error("bind");
                audio.SDL_DestroyAudioStream(stream);
                return;
            }
            streams.add(stream);
        } else if (!audio.SDL_SetAudioStreamFormat(stream, spec, deviceSpec)) {
            error("format");
            return;
        }
        // set every time: SDL can drop the map when the format changes
        if (!audio.SDL_SetAudioStreamOutputChannelMap(stream, CHANNEL_MAP)) {
            error("channel map");
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment memory = arena.allocateFrom(ValueLayout.JAVA_BYTE, pcm);
            if (!audio.SDL_PutAudioStreamData(stream, memory.asByteBuffer())) error("queue");
        }
    }

    private SdlAudioStreamHandle idleStream(SdlAudio audio) {
        for (SdlAudioStreamHandle stream : streams) {
            if (audio.SDL_GetAudioStreamQueued(stream) == 0) return stream;
        }
        return null;
    }

    private void error(String step) {
        if (loggedError) return;
        loggedError = true;
        Constants.LOG.warn("Controller speaker: SDL {} failed: {}", step, sdl.error().SDL_GetError());
    }

    private void close() {
        closed = true;
        for (SdlAudioStreamHandle stream : streams) sdl.audio().SDL_DestroyAudioStream(stream);
        streams.clear();
    }
}

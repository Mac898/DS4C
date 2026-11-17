package au.mltech.ds4c.controlify;

import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.controller.ControllerEntity;

import javax.sound.sampled.AudioFormat;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * PCM out to the current controller's HD haptics. Uses Controlify's HDHapticsApi when the installed
 * Controlify has it, otherwise DS4C's mixin on the haptic component.
 */
public interface HdHaptics {
    HdHaptics INSTANCE = Impl.choose();

    /**
     * False when neither path exists (the mixin didn't apply); nothing will ever play.
     */
    boolean isSupported();

    boolean isAvailable();

    /**
     * 16-bit signed little-endian PCM, mono or stereo. Client thread.
     */
    void playPcm(byte[] pcm, AudioFormat format);

    final class Impl {
        private static final String API_CLASS = "dev.isxander.controlify.api.haptic.HDHapticsApi";

        private Impl() {
        }

        private static HdHaptics choose() {
            HdHaptics upstream = upstream();
            return upstream != null ? upstream : new ComponentMixin();
        }

        private static HdHaptics upstream() {
            try {
                Class<?> api = Class.forName(API_CLASS, false, Impl.class.getClassLoader());
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodHandle available = lookup.findStatic(api, "isAvailable", MethodType.methodType(boolean.class));
                MethodHandle play = lookup.findStatic(api, "playPcm",
                        MethodType.methodType(void.class, byte[].class, AudioFormat.class));
                return new Upstream(available, play);
            } catch (ReflectiveOperationException | LinkageError e) {
                return null;
            }
        }
    }

    record Upstream(MethodHandle available, MethodHandle play) implements HdHaptics {
        @Override
        public boolean isSupported() {
            return true;
        }

        @Override
        public boolean isAvailable() {
            try {
                return (boolean) available.invokeExact();
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public void playPcm(byte[] pcm, AudioFormat format) {
            try {
                play.invokeExact(pcm, format);
            } catch (Throwable ignored) {
            }
        }
    }

    final class ComponentMixin implements HdHaptics {
        private static Ds4cHdHaptic current() {
            return ControlifyApi.get().getCurrentController()
                    .flatMap(ControllerEntity::hdHaptics)
                    .map(c -> c instanceof Ds4cHdHaptic haptic ? haptic : null)
                    .orElse(null);
        }

        @Override
        public boolean isSupported() {
            return ControlifyCompat.applied("HDHapticComponentMixin");
        }

        @Override
        public boolean isAvailable() {
            Ds4cHdHaptic haptic = current();
            return haptic != null && haptic.ds4c$canPlay();
        }

        @Override
        public void playPcm(byte[] pcm, AudioFormat format) {
            Ds4cHdHaptic haptic = current();
            if (haptic != null) haptic.ds4c$playPcm(pcm, format);
        }
    }
}

package au.mltech.ds4c.controlify;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.isxander.controlify.controller.dualsense.TriggerEffectCodecs;

/**
 * Whether the installed Controlify can decode the firmware's bow trigger effect. Without it one bow
 * rule would fail DS4C's whole trigger-effect file, so DS4C then ships a pack that uses the weapon
 * effect for the pull-through draw instead.
 */
public final class BowEffect {
    private static Boolean supported;

    private BowEffect() {
    }

    public static synchronized boolean isSupported() {
        if (supported == null) supported = probe();
        return supported;
    }

    private static boolean probe() {
        JsonObject bow = new JsonObject();
        bow.addProperty("type", "bow");
        bow.addProperty("start_position", 4);
        bow.addProperty("end_position", 8);
        bow.addProperty("strength", 4);
        bow.addProperty("snap_force", 8);
        try {
            return TriggerEffectCodecs.CODEC.parse(JsonOps.INSTANCE, bow).isSuccess();
        } catch (Throwable t) {
            return false;
        }
    }
}

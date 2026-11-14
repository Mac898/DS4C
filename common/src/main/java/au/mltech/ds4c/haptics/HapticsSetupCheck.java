package au.mltech.ds4c.haptics;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.Ds4cSettings;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.haptic.HDHapticsApi;
import dev.isxander.controlify.controller.ControllerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Tells the player once per session when a DualSense is in use but HD haptics can't play, with the
 * likely reason (Bluetooth, or on Linux the audio profile; see docs/linux-hd-haptics.md). Without
 * this, missing haptics just look like DS4C doing nothing.
 */
final class HapticsSetupCheck {
    /** Wait for the controller and its audio device to settle after joining a world. */
    private static final int DELAY_TICKS = 100;
    private static int ticksInWorld;
    private static boolean warned;

    private HapticsSetupCheck() {
    }

    static void tick(Minecraft minecraft) {
        if (warned || minecraft.player == null) {
            ticksInWorld = 0;
            return;
        }
        if (++ticksInWorld < DELAY_TICKS) return;
        if (!Ds4cSettings.hapticsEnabled() || HDHapticsApi.isAvailable()) {
            warned = true; // fine (or deliberately off): nothing to say this session
            return;
        }
        ControllerEntity controller = ControlifyApi.get().getCurrentController().orElse(null);
        if (controller == null || controller.dualSense().isEmpty()) return;

        warned = true;
        String reason;
        if (controller.bluetooth().isPresent()) {
            reason = Util.getPlatform() == Util.OS.LINUX
                    ? "ds4c.setup.no_haptics.bluetooth_linux" : "ds4c.setup.no_haptics.bluetooth";
        } else if (Util.getPlatform() == Util.OS.LINUX) {
            reason = "ds4c.setup.no_haptics.linux";
        } else {
            reason = "ds4c.setup.no_haptics.generic";
        }
        Constants.LOG.warn("DS4C: DualSense connected but HD haptics are unavailable ({}). See DS4C's docs/linux-hd-haptics.md.", reason);
        minecraft.player.sendSystemMessage(Component.translatable(reason));
    }
}

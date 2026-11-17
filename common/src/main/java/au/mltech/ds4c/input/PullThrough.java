package au.mltech.ds4c.input;

import au.mltech.ds4c.Ds4cSettings;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemUseAnimation;
import org.jspecify.annotations.Nullable;

/**
 * "Pull through to shoot": with a bow or trident drawn, pressing Use through the firmware Bow
 * effect's resistance to the bottom looses the shot. The next draw waits until the trigger is let
 * back out.
 * <p>
 * Controlify's press threshold (0.5) has no hysteresis, so a finger resting there against the Bow
 * effect flickers and vanilla fires a weak arrow on each flicker. While drawn, a release only counts
 * below {@link #RESET}.
 */
public final class PullThrough {
    /**
     * How far the trigger must be pressed to count as "through" (0..1).
     */
    private static final float THROUGH = 0.97f;
    /**
     * How far it must come back to count as let out: before another draw can start after a
     * pull-through shot, and before vanilla may loose a draw on release. Well below Controlify's
     * press threshold, so a trigger resting near that threshold can't flicker a shot.
     */
    private static final float RESET = 0.25f;
    private static final Identifier USE = Identifier.fromNamespaceAndPath("controlify", "use");

    private static @Nullable InputBindingSupplier useBinding;
    private static boolean waitingForRelease;
    private static boolean releasing;

    private PullThrough() {
    }

    /**
     * Start of every client tick, before vanilla handles the use key.
     */
    public static void clientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!Ds4cSettings.pullThroughToShoot() || player == null || minecraft.gameMode == null) {
            waitingForRelease = false;
            return;
        }
        float use = useAnalogue();
        if (waitingForRelease) {
            if (use < RESET) waitingForRelease = false;
            return;
        }
        if (use >= THROUGH && player.isUsingItem()) {
            ItemUseAnimation animation = player.getUseItem().getUseAnimation();
            if (animation == ItemUseAnimation.BOW || animation == ItemUseAnimation.TRIDENT) {
                releasing = true;
                try {
                    minecraft.gameMode.releaseUsingItem(player);
                } finally {
                    releasing = false;
                }
                waitingForRelease = true;
            }
        }
    }

    /**
     * True when vanilla's release of a drawn bow or trident should be held off, because the trigger
     * has only dipped under the press threshold, not been let out.
     */
    public static boolean holdsDraw(LocalPlayer player) {
        if (releasing || !Ds4cSettings.pullThroughToShoot()) return false;
        if (!player.isUsingItem()) return false;
        ItemUseAnimation animation = player.getUseItem().getUseAnimation();
        if (animation != ItemUseAnimation.BOW && animation != ItemUseAnimation.TRIDENT) return false;
        return useAnalogue() >= RESET;
    }

    /**
     * True while a shot was just pulled through and the trigger hasn't been let out yet.
     */
    public static boolean blocksUse() {
        return waitingForRelease;
    }

    private static float useAnalogue() {
        if (!ControlifyApi.get().currentInputMode().isController()) return 0f;
        var controller = ControlifyApi.get().getCurrentController().orElse(null);
        if (controller == null) return 0f;
        if (useBinding == null) useBinding = ControlifyBindApi.get().createSupplier(USE);
        InputBinding binding = useBinding.onOrNull(controller);
        return binding == null ? 0f : binding.analogueNow();
    }
}

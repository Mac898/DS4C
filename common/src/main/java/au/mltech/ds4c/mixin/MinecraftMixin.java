package au.mltech.ds4c.mixin;

import au.mltech.ds4c.events.Ds4cEvents;
import au.mltech.ds4c.haptics.Haptics;
import au.mltech.ds4c.input.PullThrough;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    public @Nullable LocalPlayer player;

    @Inject(method = "tick", at = @At("HEAD"))
    private void ds4c$onClientTick(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        Ds4cEvents.clientTick(minecraft);
        Haptics.clientTick(minecraft);
        PullThrough.clientTick(minecraft);
    }

    /** After a pull-through shot, don't start a new draw until the trigger has been let out. */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void ds4c$blockUseAfterPullThrough(CallbackInfo ci) {
        if (PullThrough.blocksUse()) ci.cancel();
    }

    /**
     * The swing at the start of an attack. Hold-to-dig swings in {@code continueAttack} are left
     * to the block-break pulses. The spear swing moved into {@code piercingAttack} in 26.3.
     */
    @Inject(
            method = "startAttack",
            at = {
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing"),
                    @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;piercingAttack")
            }
    )
    private void ds4c$onAttackSwing(CallbackInfoReturnable<Boolean> cir) {
        if (this.player != null) Ds4cEvents.onAttackSwing(this.player);
    }
}

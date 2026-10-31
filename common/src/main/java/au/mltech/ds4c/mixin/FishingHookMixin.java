package au.mltech.ds4c.mixin;

import au.mltech.ds4c.haptics.HapticEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Soft HD-haptic cues that mirror the bobber before a bite: fish approaching, nibbles, bobbing. */
@Mixin(FishingHook.class)
public class FishingHookMixin {
    @Shadow private int nibble;
    @Shadow private int timeUntilHooked;
    @Shadow private boolean biting;

    @Unique private int ds4c$lastTimeUntilHooked = 0;
    @Unique private int ds4c$nibbleCooldown = 0;
    @Unique private int ds4c$bobCooldown = 0;
    @Unique private double ds4c$prevDy = 0.0;

    @Inject(method = "tick", at = @At("RETURN"))
    private void ds4c$onTick(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (!self.level().isClientSide() || self.getPlayerOwner() != Minecraft.getInstance().player) return;

        if (this.ds4c$nibbleCooldown > 0) this.ds4c$nibbleCooldown--;
        if (this.ds4c$bobCooldown > 0) this.ds4c$bobCooldown--;

        // Fish approaching: the particle trail starts.
        if (this.ds4c$lastTimeUntilHooked == 0 && this.timeUntilHooked > 0) {
            HapticEvents.onFishingCue(0.18f, 110f, 80);
        }
        this.ds4c$lastTimeUntilHooked = this.timeUntilHooked;

        if (this.nibble > 0 && !this.biting && this.ds4c$nibbleCooldown == 0) {
            HapticEvents.onFishingCue(0.24f, 130f, 60);
            this.ds4c$nibbleCooldown = 8;
        }

        // Each reversal of the bobber's vertical motion is one bob.
        if (self.isInWater() && !this.biting) {
            double dy = self.getDeltaMovement().y;
            boolean reversed = this.ds4c$prevDy * dy < -1.0e-6
                    && (Math.abs(this.ds4c$prevDy) > 0.005 || Math.abs(dy) > 0.005);
            if (reversed && this.ds4c$bobCooldown == 0) {
                double magnitude = Math.max(Math.abs(this.ds4c$prevDy), Math.abs(dy));
                HapticEvents.onFishingCue((float) Math.min(0.40, 0.10 + magnitude * 6.0), 160f, 50);
                this.ds4c$bobCooldown = 4;
            }
            this.ds4c$prevDy = dy;
        } else {
            this.ds4c$prevDy = 0.0;
        }
    }
}

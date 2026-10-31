package au.mltech.ds4c.mixin;

import au.mltech.ds4c.haptics.HapticEvents;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public class LightningBoltMixin {
    @Shadow private int life;

    /** {@code life == 2} is the tick vanilla plays the thunder and impact sounds on the client. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void ds4c$onTick(CallbackInfo ci) {
        LightningBolt self = (LightningBolt) (Object) this;
        if (this.life == 2 && self.level().isClientSide()) HapticEvents.onLightningStrike(self);
    }
}

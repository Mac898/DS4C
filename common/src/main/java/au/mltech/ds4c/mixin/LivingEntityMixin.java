package au.mltech.ds4c.mixin;

import au.mltech.ds4c.haptics.HapticEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LivingEntity is shared with the integrated server, so every handler first checks that it is
 * running for the local player (a server-side entity is never that object).
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Unique
    private @Nullable LocalPlayer ds4c$asLocalPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        return (Object) this == player ? player : null;
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void ds4c$onDamage(DamageSource source, CallbackInfo ci) {
        LocalPlayer player = ds4c$asLocalPlayer();
        if (player != null) {
            HapticEvents.onLocalPlayerDamaged(player, source);
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void ds4c$onJump(CallbackInfo ci) {
        LocalPlayer player = ds4c$asLocalPlayer();
        if (player != null) HapticEvents.onJump(player);
    }
}

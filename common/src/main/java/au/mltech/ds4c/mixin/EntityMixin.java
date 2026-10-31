package au.mltech.ds4c.mixin;

import au.mltech.ds4c.events.Ds4cEvents;
import au.mltech.ds4c.haptics.HapticEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    /** HEAD: fallDistance hasn't been reset yet. */
    @Inject(method = "checkFallDamage", at = @At("HEAD"))
    private void ds4c$onCheckFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos, CallbackInfo ci) {
        if (!onGround) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if ((Object) this == player) {
            Ds4cEvents.onLanding(player, player.fallDistance);
            HapticEvents.onLanding(player, player.fallDistance);
        }
    }

    @Inject(method = "playStepSound", at = @At("HEAD"))
    private void ds4c$onPlayStepSound(BlockPos pos, BlockState state, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if ((Object) this == player) HapticEvents.onFootstep(player, state);
    }
}

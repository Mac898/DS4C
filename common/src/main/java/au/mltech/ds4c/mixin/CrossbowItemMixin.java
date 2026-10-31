package au.mltech.ds4c.mixin;

import au.mltech.ds4c.events.Ds4cEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void ds4c$onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide() && player instanceof LocalPlayer && CrossbowItem.isCharged(player.getItemInHand(hand))) {
            Ds4cEvents.onCrossbowFired();
        }
    }
}

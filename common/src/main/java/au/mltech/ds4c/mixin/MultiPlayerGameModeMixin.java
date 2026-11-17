package au.mltech.ds4c.mixin;

import au.mltech.ds4c.events.Ds4cEvents;
import au.mltech.ds4c.haptics.HapticEvents;
import au.mltech.ds4c.input.PullThrough;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private float destroyProgress;
    @Shadow
    private boolean isDestroying;

    /**
     * Pull through to shoot: a trigger dipping under the press threshold isn't a release.
     */
    @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
    private void ds4c$holdDrawUntilLetOut(Player player, CallbackInfo ci) {
        if (player instanceof LocalPlayer local && PullThrough.holdsDraw(local)) ci.cancel();
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void ds4c$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Ds4cEvents.onBreakReset();
        LocalPlayer player = this.minecraft.player;
        if (player != null && this.minecraft.level != null) {
            BlockState state = this.minecraft.level.getBlockState(pos);
            if (!state.isAir() && state.requiresCorrectToolForDrops() && !player.hasCorrectToolForDrops(state)) {
                HapticEvents.onIneffectiveTool();
            }
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("RETURN"))
    private void ds4c$onContinueDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        // Once the block breaks, progress resets to 0; don't treat that as a new crack stage.
        if (this.isDestroying) Ds4cEvents.onBreakProgress(this.destroyProgress);
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
    private void ds4c$onStopDestroyBlock(CallbackInfo ci) {
        Ds4cEvents.onBreakReset();
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void ds4c$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = this.minecraft.player;
        if (player != null && this.minecraft.level != null) {
            Ds4cEvents.onBlockBroken(player, this.minecraft.level.getBlockState(pos));
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void ds4c$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction()) Ds4cEvents.onItemUsed(player.getItemInHand(hand), true);
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void ds4c$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof LocalPlayer && cir.getReturnValue().consumesAction()) {
            Ds4cEvents.onItemUsed(player.getItemInHand(hand), false);
        }
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void ds4c$onInteract(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && player.getItemInHand(hand).getItem() instanceof ShearsItem) {
            Ds4cEvents.onShearsUsedOnEntity();
            HapticEvents.onShearsSnip();
        }
    }

    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void ds4c$onReleaseUsingItem(Player player, CallbackInfo ci) {
        if (player instanceof LocalPlayer localPlayer) Ds4cEvents.onReleaseUsingItem(localPlayer);
    }
}

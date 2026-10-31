package au.mltech.ds4c.mixin;

import au.mltech.ds4c.events.Ds4cEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalInt;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {
    @Shadow @Final private static EntityDataAccessor<OptionalInt> DATA_ATTACHED_TO_TARGET;

    @Inject(method = "tick", at = @At("RETURN"))
    private void ds4c$onTick(CallbackInfo ci) {
        FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        // In singleplayer the server-side rocket has the same attached ID as the client player.
        if (!self.level().isClientSide()) return;

        OptionalInt attached = self.getEntityData().get(DATA_ATTACHED_TO_TARGET);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && attached.isPresent() && attached.getAsInt() == player.getId() && player.isFallFlying()) {
            Ds4cEvents.onFireworkBoost(self.getId());
        }
    }
}

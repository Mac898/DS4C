package au.mltech.ds4c.mixin;

import au.mltech.ds4c.events.Ds4cEvents;
import au.mltech.ds4c.haptics.HapticEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    /** RETURN: only reached on the client thread (the handler re-dispatches itself off the network thread). */
    @Inject(method = "handleExplosion", at = @At("RETURN"))
    private void ds4c$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        float magnitude = HapticEvents.explosionMagnitude(player, packet.center(), packet.radius());
        if (magnitude <= 0f) return;
        HapticEvents.explosion(magnitude, HapticEvents.panTowards(player, packet.center()));
        Ds4cEvents.onExplosion(magnitude);
    }
}

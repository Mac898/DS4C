package au.mltech.ds4c;

import au.mltech.ds4c.debug.Ds4cCommands;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class Ds4c implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Identifier packId = Constants.effectsPackId();
        boolean registered = ResourceLoader.registerBuiltinPack(
                packId,
                FabricLoader.getInstance().getModContainer(Constants.MOD_ID).orElseThrow(),
                Component.translatable(Constants.EFFECTS_PACK_NAME_KEY),
                PackActivationType.ALWAYS_ENABLED
        );
        if (!registered) {
            Constants.LOG.error("Failed to register built-in pack {}", packId);
        }

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, context) -> dispatcher.register(Ds4cCommands.<FabricClientCommandSource>build()));
    }
}

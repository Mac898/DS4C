package au.mltech.ds4c;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class Ds4c {
    public Ds4c(IEventBus modBus) {
        modBus.addListener(AddPackFindersEvent.class, event -> event.addPackFinders(
                Constants.EFFECTS_PACK_ID.withPrefix("resourcepacks/"),
                PackType.CLIENT_RESOURCES,
                Component.translatable(Constants.EFFECTS_PACK_NAME_KEY),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP
        ));
    }
}

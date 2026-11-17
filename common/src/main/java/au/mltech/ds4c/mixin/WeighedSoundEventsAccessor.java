package au.mltech.ds4c.mixin;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.sounds.Weighted;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * The variants of a sound event, so DS4C can decode them ahead of first use.
 */
@Mixin(WeighedSoundEvents.class)
public interface WeighedSoundEventsAccessor {
    @Accessor("list")
    List<Weighted<Sound>> ds4c$getList();
}

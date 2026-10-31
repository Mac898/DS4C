package au.mltech.ds4c.config;

import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** DS4C settings, built with YACL (bundled with Controlify). Opened from Mod Menu or NeoForge's mod list. */
public final class Ds4cConfigScreen {
    private Ds4cConfigScreen() {
    }

    public static Screen create(Screen parent) {
        OptionGroup.Builder categories = OptionGroup.createBuilder()
                .name(Component.translatable("ds4c.config.group.categories"))
                .description(OptionDescription.of(Component.translatable("ds4c.config.group.categories.tooltip")));
        for (FeedbackCategory category : FeedbackCategory.values()) {
            String key = "ds4c.config.category." + Ds4cConfig.key(category);
            categories.option(toggle(key, true,
                    () -> Ds4cSettings.isCategoryEnabled(category),
                    value -> Ds4cSettings.setCategoryEnabled(category, value)));
        }

        OptionGroup triggers = OptionGroup.createBuilder()
                .name(Component.translatable("ds4c.config.group.triggers"))
                .option(toggle("ds4c.config.trigger_effects", true,
                        Ds4cSettings::triggerEffectsEnabled, Ds4cSettings::setTriggerEffectsEnabled))
                .build();

        OptionGroup haptics = OptionGroup.createBuilder()
                .name(Component.translatable("ds4c.config.group.haptics"))
                .option(toggle("ds4c.config.hd_haptics", true,
                        Ds4cSettings::hapticsEnabled, Ds4cSettings::setHapticsEnabled))
                .option(slider("ds4c.config.master_intensity", 1.0f, 0f, 2f, 0.05f,
                        Ds4cSettings::masterIntensity, Ds4cSettings::setMasterIntensity))
                .option(slider("ds4c.config.synthesis_intensity", 1.0f, 0f, 2f, 0.05f,
                        Ds4cSettings::synthesisIntensity, Ds4cSettings::setSynthesisIntensity))
                .option(slider("ds4c.config.falloff_near", 0f, 0f, 32f, 0.5f,
                        Ds4cSettings::falloffNearBlocks,
                        v -> Ds4cSettings.setFalloff(v, Ds4cSettings.falloffFarBlocks())))
                .option(slider("ds4c.config.falloff_far", 64f, 1f, 128f, 1f,
                        Ds4cSettings::falloffFarBlocks,
                        v -> Ds4cSettings.setFalloff(Ds4cSettings.falloffNearBlocks(), v)))
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("ds4c.config.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("ds4c.config.title"))
                        .group(triggers)
                        .group(haptics)
                        .group(categories.build())
                        .build())
                .save(Ds4cConfig::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> toggle(String key, boolean def, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(key))
                .description(OptionDescription.of(Component.translatable(key + ".tooltip")))
                .binding(def, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Float> slider(String key, float def, float min, float max, float step,
                                        Supplier<Float> getter, Consumer<Float> setter) {
        return Option.<Float>createBuilder()
                .name(Component.translatable(key))
                .description(OptionDescription.of(Component.translatable(key + ".tooltip")))
                .binding(def, getter, setter)
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }
}

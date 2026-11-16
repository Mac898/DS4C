package au.mltech.ds4c.debug;

import au.mltech.ds4c.Ds4cSettings;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * {@code /ds4c gallery ...} client commands, available while the effect gallery option is on.
 * Generic so each loader can register it with its own client command source type.
 */
public final class Ds4cCommands {
    private static final int DEFAULT_SECONDS = 5;

    private Ds4cCommands() {
    }

    public static <S> LiteralArgumentBuilder<S> build() {
        return LiteralArgumentBuilder.<S>literal("ds4c")
                .requires(source -> Ds4cSettings.effectGallery())
                .then(LiteralArgumentBuilder.<S>literal("gallery")
                        .then(LiteralArgumentBuilder.<S>literal("trigger")
                                .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                                        .suggests(suggest(EffectGallery::triggerNames))
                                        .executes(ctx -> trigger(ctx, DEFAULT_SECONDS))
                                        .then(RequiredArgumentBuilder.<S, Integer>argument("seconds", IntegerArgumentType.integer(1, 60))
                                                .executes(ctx -> trigger(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))))
                        .then(LiteralArgumentBuilder.<S>literal("haptic")
                                .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                                        .suggests(suggest(EffectGallery::hapticNames))
                                        .executes(ctx -> result(EffectGallery.playHaptic(StringArgumentType.getString(ctx, "name")),
                                                "ds4c.gallery.unknown_haptic"))))
                        .then(LiteralArgumentBuilder.<S>literal("sound")
                                .then(RequiredArgumentBuilder.<S, String>argument("id", StringArgumentType.greedyString())
                                        .suggests(suggest(() -> EffectGallery.soundIds().stream().map(Object::toString).toList()))
                                        .executes(ctx -> result(EffectGallery.playSound(StringArgumentType.getString(ctx, "id")),
                                                "ds4c.gallery.invalid_sound"))))
                        .then(LiteralArgumentBuilder.<S>literal("stop")
                                .executes(ctx -> {
                                    EffectGallery.stopTrigger();
                                    return 1;
                                })));
    }

    private static <S> int trigger(CommandContext<S> ctx, int seconds) {
        return result(EffectGallery.playTrigger(StringArgumentType.getString(ctx, "name"), seconds),
                "ds4c.gallery.unknown_trigger");
    }

    private static int result(boolean ok, String failureKey) {
        if (!ok) EffectGallery.tell(failureKey);
        return ok ? 1 : 0;
    }

    private static <S> SuggestionProvider<S> suggest(Supplier<Collection<String>> names) {
        return (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String name : names.get()) {
                if (name.startsWith(remaining) || name.contains(remaining)) builder.suggest(name);
            }
            return builder.buildFuture();
        };
    }
}

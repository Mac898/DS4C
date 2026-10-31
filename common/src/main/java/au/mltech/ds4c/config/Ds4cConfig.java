package au.mltech.ds4c.config;

import au.mltech.ds4c.Constants;
import au.mltech.ds4c.Ds4cSettings;
import au.mltech.ds4c.FeedbackCategory;
import au.mltech.ds4c.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * {@code config/ds4c.json}. Loaded into {@link Ds4cSettings} at startup and written back when
 * the settings screen saves. Missing fields keep their defaults; unknown categories are ignored.
 */
public final class Ds4cConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Serialised form. Field names are the JSON keys. */
    static final class Data {
        boolean triggerEffects = true;
        boolean hdHaptics = true;
        boolean footsteps = true;
        int triggerStrength = 100;
        float masterIntensity = 1.0f;
        float synthesisIntensity = 1.0f;
        float falloffNearBlocks = 0.0f;
        float falloffFarBlocks = 64.0f;
        Map<String, Boolean> categories = new LinkedHashMap<>();
    }

    private Ds4cConfig() {
    }

    private static Path path() {
        return Services.PLATFORM.getConfigDirectory().resolve(Constants.MOD_ID + ".json");
    }

    public static void load() {
        Path path = path();
        Data data = new Data();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Data read = GSON.fromJson(reader, Data.class);
                if (read != null) data = read;
            } catch (IOException | JsonParseException e) {
                Constants.LOG.error("Failed to read {}, using defaults", path, e);
            }
        }
        apply(data);
        if (!Files.exists(path)) save();
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(capture(), writer);
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to write {}", path, e);
        }
    }

    private static void apply(Data data) {
        Ds4cSettings.setTriggerEffectsEnabled(data.triggerEffects);
        Ds4cSettings.setHapticsEnabled(data.hdHaptics);
        Ds4cSettings.setFootstepsEnabled(data.footsteps);
        Ds4cSettings.setTriggerStrength(data.triggerStrength);
        Ds4cSettings.setMasterIntensity(data.masterIntensity);
        Ds4cSettings.setSynthesisIntensity(data.synthesisIntensity);
        Ds4cSettings.setFalloff(data.falloffNearBlocks, data.falloffFarBlocks);
        for (FeedbackCategory category : FeedbackCategory.values()) {
            Boolean enabled = data.categories == null ? null : data.categories.get(key(category));
            Ds4cSettings.setCategoryEnabled(category, enabled == null || enabled);
        }
    }

    private static Data capture() {
        Data data = new Data();
        data.triggerEffects = Ds4cSettings.triggerEffectsEnabled();
        data.hdHaptics = Ds4cSettings.hapticsEnabled();
        data.footsteps = Ds4cSettings.footstepsEnabled();
        data.triggerStrength = Ds4cSettings.triggerStrength();
        data.masterIntensity = Ds4cSettings.masterIntensity();
        data.synthesisIntensity = Ds4cSettings.synthesisIntensity();
        data.falloffNearBlocks = Ds4cSettings.falloffNearBlocks();
        data.falloffFarBlocks = Ds4cSettings.falloffFarBlocks();
        for (FeedbackCategory category : FeedbackCategory.values()) {
            data.categories.put(key(category), Ds4cSettings.isCategoryEnabled(category));
        }
        return data;
    }

    static String key(FeedbackCategory category) {
        return category.name().toLowerCase(Locale.ROOT);
    }
}

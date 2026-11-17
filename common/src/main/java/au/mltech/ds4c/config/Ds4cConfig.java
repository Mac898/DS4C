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
        Ds4cSettings.setTriggerStrength(isOff(data.triggerEffects) ? 0 : data.triggerStrength);
        Ds4cSettings.setPullThroughToShoot(data.pullThroughToShoot);
        Ds4cSettings.setMasterIntensity(isOff(data.hdHaptics) ? 0f : data.masterIntensity);
        Ds4cSettings.setSynthesisIntensity(data.synthesisIntensity);
        Ds4cSettings.setFalloff(data.falloffNearBlocks, data.falloffFarBlocks);
        Ds4cSettings.setFootstepIntensity(isOff(data.footsteps) ? 0f : data.footstepIntensity);
        Ds4cSettings.setSpeakerVolume(data.speakerVolume);
        Ds4cSettings.setSpeakerLatencyMs(data.speakerLatencyMs);
        Ds4cSettings.setEffectGallery(data.effectGallery);
        for (FeedbackCategory category : FeedbackCategory.values()) {
            Boolean enabled = data.categories == null ? null : data.categories.get(key(category));
            Float intensity = data.categoryIntensity == null ? null : data.categoryIntensity.get(key(category));
            Ds4cSettings.setCategoryIntensity(category, isOff(enabled) ? 0f : intensity == null ? 1.0f : intensity);
        }
    }

    private static Data capture() {
        Data data = new Data();
        data.triggerStrength = Ds4cSettings.triggerStrength();
        data.pullThroughToShoot = Ds4cSettings.pullThroughToShoot();
        data.masterIntensity = Ds4cSettings.masterIntensity();
        data.synthesisIntensity = Ds4cSettings.synthesisIntensity();
        data.falloffNearBlocks = Ds4cSettings.falloffNearBlocks();
        data.falloffFarBlocks = Ds4cSettings.falloffFarBlocks();
        data.footstepIntensity = Ds4cSettings.footstepIntensity();
        data.speakerVolume = Ds4cSettings.speakerVolume();
        data.speakerLatencyMs = Ds4cSettings.speakerLatencyMs();
        data.effectGallery = Ds4cSettings.effectGallery();
        for (FeedbackCategory category : FeedbackCategory.values()) {
            data.categoryIntensity.put(key(category), Ds4cSettings.categoryIntensity(category));
        }
        return data;
    }

    /**
     * A legacy tickbox that was saved as off.
     */
    private static boolean isOff(Boolean legacy) {
        return legacy != null && !legacy;
    }

    static String key(FeedbackCategory category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Serialised form. Field names are the JSON keys.
     */
    static final class Data {
        int triggerStrength = 100;
        boolean pullThroughToShoot = true;
        float masterIntensity = 1.0f;
        float synthesisIntensity = 1.0f;
        float falloffNearBlocks = 0.0f;
        float falloffFarBlocks = 64.0f;
        float footstepIntensity = 1.0f;
        float speakerVolume = 1.0f;
        int speakerLatencyMs = 0;
        boolean effectGallery = false;
        Map<String, Float> categoryIntensity = new LinkedHashMap<>();

        // Read only, from configs saved before the on/off tickboxes became strength 0. Left null
        // when saving, so Gson drops them.
        Boolean triggerEffects;
        Boolean hdHaptics;
        Boolean footsteps;
        Map<String, Boolean> categories;
    }
}

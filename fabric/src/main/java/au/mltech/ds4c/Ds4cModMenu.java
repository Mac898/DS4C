package au.mltech.ds4c;

import au.mltech.ds4c.config.Ds4cConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Only loaded by Mod Menu when it's installed. */
public class Ds4cModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return Ds4cConfigScreen::create;
    }
}

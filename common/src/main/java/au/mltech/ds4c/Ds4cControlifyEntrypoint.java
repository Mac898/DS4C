package au.mltech.ds4c;

import au.mltech.ds4c.facts.Ds4cFacts;
import au.mltech.ds4c.haptics.HapticSoundDefaults;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;

/**
 * Registered through {@code META-INF/services} so it loads on both Fabric and NeoForge.
 * Do not also register it as a Fabric {@code "controlify"} entrypoint: Controlify loads both
 * sources on Fabric without de-duplicating, which would register everything twice.
 */
public final class Ds4cControlifyEntrypoint implements ControlifyEntrypoint {
    @Override
    public void onControlifyPreInit(PreInitContext context) {
        context.contextualDomains().inGame().registerContributor(Ds4cFacts::contributeInGame);
        Constants.LOG.info("Registered DS4C contextual fact contributors");
    }

    @Override
    public void onControlifyInit(InitContext context) {
        HapticSoundDefaults.registerAll();
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlify) {
    }
}

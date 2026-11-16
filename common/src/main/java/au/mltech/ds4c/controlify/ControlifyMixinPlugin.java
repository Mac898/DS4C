package au.mltech.ds4c.controlify;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Records which Controlify mixins applied and which of their injectors found a target, for
 * {@link ControlifyCompat#selfCheck()}. Uses system properties because the plugin and the game may
 * be loaded by different class loaders.
 */
public final class ControlifyMixinPlugin implements IMixinConfigPlugin {
    private static final String HANDLER_PREFIX = "ds4c$";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        mark(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        // an injector that found its target leaves a call to its (renamed) handler in the target class
        for (MethodNode method : targetClass.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) {
                    int i = call.name.indexOf(HANDLER_PREFIX);
                    if (i >= 0) mark(call.name.substring(i));
                }
            }
        }
    }

    private static void mark(String key) {
        System.setProperty(ControlifyCompat.APPLIED_PROPERTY_PREFIX + key, "true");
    }
}

package dev.ss05.halley.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The plugin of the mixin configs for the mixins into The Shooting Star (compat/tss/mixin). The release jar has one such
 * config per supported version of The Shooting Star, each for its own copy of the mixins, mapped to that version's
 * names; only the running version's copy may apply, since another version's names could point at the wrong thing.
 * The dev runs have just the one, unmapped config.
 */
public final class TssMixins implements IMixinConfigPlugin {
    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        String pkg = mixinPackage.endsWith(".") ? mixinPackage.substring(0, mixinPackage.length() - 1) : mixinPackage;
        if (pkg.equals(StarBridge.UNVERSIONED + ".mixin")) {
            this.apply = true;
            return;
        }
        try {
            String version = TssVersion.running();
            this.apply = version != null && pkg.equals(StarBridge.VERSIONED + TssVersion.tag(version) + ".mixin");
        } catch (Throwable failed) {
            this.apply = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.apply;
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
    }
}

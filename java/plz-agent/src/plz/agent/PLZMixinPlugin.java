package plz.agent;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PLZMixinPlugin implements IMixinConfigPlugin {
    public static final String SIDE_PROPERTY = "plz.side";
    private static final String SERVER_PACKAGE = "plz.mixin.server.";
    private static final String CLIENT_PACKAGE = "plz.mixin.client.";

    public static boolean isServer() {
        String side = System.getProperty(SIDE_PROPERTY);
        if (side != null) {
            return "server".equalsIgnoreCase(side);
        }
        String command = System.getProperty("sun.java.command", "");
        return command.startsWith("zombie.network.GameServer");
    }

    @Override
    public void onLoad(String mixinPackage) {
        // Without this, @WrapOperation handlers merge as plain methods and the call site is never rewired, with no error.
        MixinExtrasBootstrap.init();
        PLZAgent.log("side " + (isServer() ? "server" : "client"));
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(SERVER_PACKAGE)) {
            return isServer();
        }
        if (mixinClassName.startsWith(CLIENT_PACKAGE)) {
            return !isServer();
        }
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
    }
}

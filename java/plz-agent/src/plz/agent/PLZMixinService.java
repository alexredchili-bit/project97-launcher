package plz.agent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;

public final class PLZMixinService extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    private static volatile IMixinTransformer transformer;

    private final ContainerHandleVirtual container = new ContainerHandleVirtual("plz");

    static IMixinTransformer transformer() {
        return transformer;
    }

    private static ClassLoader loader() {
        return ClassLoader.getSystemClassLoader();
    }

    @Override
    public String getName() {
        return "PLZAgent";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory factory) {
            transformer = factory.createTransformer();
        }
        super.offer(internal);
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_25;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return IAdviceProvider.GENERIC;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.singletonList("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return this.container;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return loader().getResourceAsStream(name);
    }

    @Override
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader());
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader());
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, PLZMixinService.class.getClassLoader());
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, true, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return this.getClassNode(name, runTransformers, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
        String path = name.replace('.', '/') + ".class";
        try (InputStream in = loader().getResourceAsStream(path)) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, readerFlags);
            return node;
        }
    }

    @Override
    public Collection<ITransformer> getTransformers() {
        return Collections.emptyList();
    }

    @Override
    public Collection<ITransformer> getDelegatedTransformers() {
        return Collections.emptyList();
    }

    @Override
    public void addTransformerExclusion(String name) {
    }

    @Override
    public void registerInvalidClass(String name) {
    }

    @Override
    public boolean isClassLoaded(String name) {
        return PLZAgent.DEFINED.contains(name);
    }

    @Override
    public String getClassRestrictions(String name) {
        return "";
    }
}

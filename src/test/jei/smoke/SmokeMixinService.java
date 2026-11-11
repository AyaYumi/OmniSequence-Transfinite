package smoke;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.LoggerAdapterConsole;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.util.IConsumer;

/** Class/resource adapter only: all parsing, selection and injection uses the real Mixin transformer. */
public final class SmokeMixinService extends MixinServiceAbstract
        implements IClassProvider, IClassBytecodeProvider {
    private final ClassLoader loader = SmokeMixinService.class.getClassLoader();
    private IConsumer<Phase> phaseConsumer;

    @Override public String getName() { return "StandaloneJeiSmoke"; }
    @Override public boolean isValid() { return true; }
    @Override public Phase getInitialPhase() { return Phase.PREINIT; }
    @Override public void wire(Phase phase, IConsumer<Phase> consumer) { phaseConsumer = consumer; }
    public void enterDefaultPhase() { phaseConsumer.accept(Phase.DEFAULT); }
    @Override public IClassProvider getClassProvider() { return this; }
    @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
    @Override public ITransformerProvider getTransformerProvider() { return null; }
    @Override public IClassTracker getClassTracker() { return null; }
    @Override public IMixinAuditTrail getAuditTrail() { return null; }
    @Override public List<String> getPlatformAgents() { return List.of(); }
    @Override public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual("standalone-jei-smoke");
    }
    @Override public InputStream getResourceAsStream(String name) { return loader.getResourceAsStream(name); }
    @Override protected ILogger createLogger(String name) { return new LoggerAdapterConsole(name); }
    @Override public URL[] getClassPath() { return new URL[0]; }
    @Override public Class<?> findClass(String name) throws ClassNotFoundException {
        return findClass(name, false);
    }
    @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader);
    }
    @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return findClass(name, initialize);
    }
    @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true);
    }
    @Override public ClassNode getClassNode(String name, boolean runTransformers)
            throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }
    @Override public ClassNode getClassNode(String name, boolean runTransformers, int flags)
            throws ClassNotFoundException, IOException {
        try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) throw new ClassNotFoundException(name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, flags);
            return node;
        }
    }
    public IMixinTransformer createTransformer() {
        return getInternal(IMixinTransformerFactory.class).createTransformer();
    }
}

package plz.agent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.ProtectionDomain;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class PLZAgent {
    public static final String CONFIG = "plz.mixins.json";
    public static final int EXIT_MIXIN_FAILED = 86;

    static final Set<String> DEFINED = ConcurrentHashMap.newKeySet();

    private static final String[] SKIP = {
        "java/", "javax/", "jdk/", "sun/", "com/sun/",
        "org/spongepowered/", "org/objectweb/asm/", "com/llamalad7/mixinextras/", "plz/agent/", "plz/mixin/"
    };

    private static final String WARMUP = "plz.agent.Warmup";

    private PLZAgent() {
    }

    private static byte[] warmupClass() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, WARMUP.replace('.', '/'), null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static void premain(String args, Instrumentation inst) {
        log("premain on " + System.getProperty("java.vm.name") + " " + System.getProperty("java.runtime.version")
            + ", main " + System.getProperty("sun.java.command"));
        try {
            System.setProperty("mixin.service", PLZMixinService.class.getName());
            System.setProperty("mixin.bootstrapService", PLZMixinServiceBootstrap.class.getName());
            MixinBootstrap.init();
            Mixins.addConfiguration(CONFIG);
            gotoPhase(MixinEnvironment.Phase.INIT);
            gotoPhase(MixinEnvironment.Phase.DEFAULT);

            IMixinTransformer transformer = PLZMixinService.transformer();
            if (transformer == null) {
                fail("Mixin never offered a transformer factory", null);
            }

            // Mixin selects its configs on the first class it sees; doing it here keeps the game's main class transformable.
            transformer.transformClassBytes(WARMUP, WARMUP, warmupClass());
            inst.addTransformer(new Transformer(transformer), false);
        } catch (Throwable t) {
            fail("premain failed", t);
        }
        log("mixins armed (" + CONFIG + ")");
        stampLaunch();
    }

    // The launcher's proof the session is patched; a later first-class stamp can miss its deadline on a slow boot.
    private static void stampLaunch() {
        try {
            Class.forName("zombie.plz.PLZPatchStamp").getMethod("record", String.class).invoke(null, "plz.agent.PLZAgent");
        } catch (ReflectiveOperationException | LinkageError e) {
            log("no launcher stamp: " + e);
        }
    }

    static synchronized void log(String line) {
        String stamped = "PLZAgent: " + line;
        System.out.println(stamped);
        try {
            Path jar = Path.of(PLZAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Files.writeString(jar.resolveSibling("plz-agent.log"), LocalDateTime.now() + " " + stamped + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            Method m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, phase);
        } catch (ReflectiveOperationException e) {
            fail("could not advance Mixin to " + phase, e);
        }
    }

    static void fail(String why, Throwable t) {
        StringWriter trace = new StringWriter();
        if (t != null) {
            t.printStackTrace(new PrintWriter(trace));
        }
        log("FATAL " + why + System.lineSeparator() + trace);
        System.err.flush();
        // A class the JVM defines while its transformer throws loads UNPATCHED, so carrying on would run vanilla silently.
        Runtime.getRuntime().halt(EXIT_MIXIN_FAILED);
    }

    private static final class Transformer implements ClassFileTransformer {
        private final IMixinTransformer mixin;

        Transformer(IMixinTransformer mixin) {
            this.mixin = mixin;
        }

        @Override
        public byte[] transform(ClassLoader loader, String internalName, Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
            if (internalName == null || redefining != null || loader == null) {
                return null;
            }

            for (String prefix : SKIP) {
                if (internalName.startsWith(prefix)) {
                    return null;
                }
            }

            String name = internalName.replace('/', '.');
            try {
                byte[] out = this.mixin.transformClassBytes(name, name, bytes);
                if (out == bytes) {
                    return null;
                }
                return LuaPublicPass.apply(out);
            } catch (Throwable t) {
                fail("mixin transform of " + name + " failed", t);
                return null;
            } finally {
                DEFINED.add(name);
            }
        }
    }
}

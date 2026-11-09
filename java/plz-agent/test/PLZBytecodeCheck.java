import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

public final class PLZBytecodeCheck {
    // MixinExtras defines these at runtime, so no classpath can show their hierarchy.
    private static final String GENERATED = "com/llamalad7/mixinextras/sugar/impl/ref/generated/";

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args[0]);
        List<Path> classes;
        try (Stream<Path> walk = Files.walk(root)) {
            classes = walk.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
        int bad = 0;
        for (Path file : classes) {
            List<String> errors = verify(Files.readAllBytes(file));
            if (!errors.isEmpty()) {
                bad++;
                System.out.println("BYTECODE: INVALID " + root.relativize(file));
                errors.stream().limit(6).forEach(e -> System.out.println("    " + e));
            }
        }
        System.out.println("BYTECODE: " + (classes.size() - bad) + "/" + classes.size() + " transformed class(es) verify");
        System.exit(bad == 0 ? 0 : 4);
    }

    private static List<String> verify(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.SKIP_DEBUG);
        Type self = Type.getObjectType(cn.name);
        Type parent = cn.superName == null ? null : Type.getObjectType(cn.superName);
        List<Type> interfaces = new ArrayList<>();
        for (String i : cn.interfaces) {
            interfaces.add(Type.getObjectType(i));
        }
        boolean isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0;
        List<String> errors = new ArrayList<>();
        for (MethodNode m : cn.methods) {
            SimpleVerifier verifier = new SimpleVerifier(Opcodes.ASM9, self, parent, interfaces, isInterface) {
                @Override
                protected boolean isAssignableFrom(Type type, Type other) {
                    if (generated(type) || generated(other)) {
                        return true;
                    }
                    return super.isAssignableFrom(type, other);
                }

                @Override
                protected boolean isInterface(Type type) {
                    return generated(type) ? false : super.isInterface(type);
                }

                @Override
                protected Type getSuperClass(Type type) {
                    return generated(type) ? Type.getObjectType("java/lang/Object") : super.getSuperClass(type);
                }
            };
            verifier.setClassLoader(ClassLoader.getSystemClassLoader());
            try {
                new Analyzer<BasicValue>(verifier).analyze(cn.name, m);
            } catch (AnalyzerException | RuntimeException e) {
                errors.add(m.name + m.desc + ": " + e.getMessage());
            }
        }
        return errors;
    }

    private static boolean generated(Type type) {
        return type != null && type.getSort() == Type.OBJECT && type.getInternalName().startsWith(GENERATED);
    }
}

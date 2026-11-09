import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class PLZMixinGate {
    private static final String LUA_PUBLIC = "Lplz/agent/LuaPublic;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

    public static void main(String[] args) throws Exception {
        boolean server = "server".equalsIgnoreCase(System.getProperty("plz.side", "client"));
        Map<String, TreeSet<String>> luaPublic = new TreeMap<>();
        TreeSet<String> targets = new TreeSet<>();
        List<String> banned = new ArrayList<>();
        Map<String, TreeSet<String>> bareInit = new TreeMap<>();
        try (JarFile jar = new JarFile(args[0])) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.startsWith("plz/mixin/") || !name.endsWith(".class")) {
                    continue;
                }
                boolean applies = !(name.startsWith("plz/mixin/server/") && !server) && !(name.startsWith("plz/mixin/client/") && server);
                try (InputStream in = jar.getInputStream(e)) {
                    List<String> mixinTargets = new ArrayList<>();
                    List<String> members = new ArrayList<>();
                    boolean[] usesBareInit = new boolean[1];
                    read(in.readAllBytes(), mixinTargets, members, banned, usesBareInit);
                    targets.addAll(mixinTargets);
                    if (usesBareInit[0]) {
                        for (String t : mixinTargets) {
                            bareInit.computeIfAbsent(t, k -> new TreeSet<>()).add(name);
                        }
                    }
                    if (applies) {
                        for (String t : mixinTargets) {
                            luaPublic.computeIfAbsent(t, k -> new TreeSet<>()).addAll(members);
                        }
                    }
                }
            }
        }

        if (!banned.isEmpty()) {
            banned.forEach(b -> System.out.println("GATE: BROKEN ACCESSOR  " + b));
            System.exit(4);
        }

        if (targets.isEmpty()) {
            System.err.println("GATE: no mixin targets found in " + args[0]);
            System.exit(2);
        }

        ClassLoader loader = ClassLoader.getSystemClassLoader();
        int failures = 0;
        for (String target : targets) {
            Class<?> cls = Class.forName(target, false, loader);
            System.out.println("GATE: applied  " + target);
            if (bareInit.containsKey(target) && cls.getDeclaredConstructors().length > 1) {
                System.out.println("GATE: BARE <init>  " + bareInit.get(target) + " on " + target + " matches only the first of "
                    + cls.getDeclaredConstructors().length + " constructors; use \"<init>*\" or a full descriptor");
                failures++;
            }
            for (String member : luaPublic.getOrDefault(target, new TreeSet<>())) {
                if (!isPublic(cls, member)) {
                    System.out.println("GATE: NOT PUBLIC  " + target + "." + member + " (marked @LuaPublic)");
                    failures++;
                }
            }
        }
        if (failures > 0) {
            System.exit(3);
        }
        System.out.println("GATE: OK " + targets.size() + " target class(es)");
    }

    private static boolean isPublic(Class<?> cls, String member) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(member) && Modifier.isPublic(m.getModifiers())) {
                return true;
            }
        }
        for (Field f : cls.getDeclaredFields()) {
            if (f.getName().equals(member) && Modifier.isPublic(f.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    private static void read(byte[] bytes, List<String> targets, List<String> luaPublic, List<String> banned, boolean[] usesBareInit) {
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            private String className;
            private boolean publicClass;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                this.className = name.replace('/', '.');
                this.publicClass = (access & Opcodes.ACC_PUBLIC) != 0;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(desc)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String n, Object value) {
                                if (value instanceof Type t) {
                                    targets.add(t.getClassName());
                                } else if (value instanceof String s) {
                                    targets.add(s.replace('/', '.'));
                                }
                            }
                        };
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (LUA_PUBLIC.equals(annotation)) {
                            luaPublic.add(name);
                        }
                        if (annotation.startsWith("Lorg/spongepowered/asm/mixin/injection/") || annotation.startsWith("Lcom/llamalad7/mixinextras/")) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitArray(String key) {
                                    if (!"method".equals(key)) {
                                        return null;
                                    }
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String n, Object value) {
                                            if ("<init>".equals(value)) {
                                                usesBareInit[0] = true;
                                            }
                                        }
                                    };
                                }
                            };
                        }
                        // Mixin never generates a static accessor body here, and a package-private accessor interface fails to link.
                        if (ACCESSOR.equals(annotation) || INVOKER.equals(annotation)) {
                            if ((access & Opcodes.ACC_STATIC) != 0) {
                                banned.add(className + "." + name + " is static; use a helper in the target's package instead");
                            } else if (!publicClass) {
                                banned.add(className + " is not public");
                            }
                        }
                        return null;
                    }
                };
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (LUA_PUBLIC.equals(annotation)) {
                            luaPublic.add(name);
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
    }
}

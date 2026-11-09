import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** args: agentJar gameJar baselineJson [accept] */
public final class PLZDriftCheck {
    private static final Set<String> INJECTORS = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
        "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
        "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;",
        "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
        "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
        "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;");
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    public static void main(String[] args) throws Exception {
        Path baselinePath = Path.of(args[2]);
        boolean accept = args.length > 3 && "accept".equals(args[3]);
        Map<String, Set<String>> touched = new TreeMap<>();
        try (JarFile agent = new JarFile(args[0])) {
            Enumeration<JarEntry> entries = agent.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                if (e.getName().startsWith("plz/mixin/") && e.getName().endsWith(".class")) {
                    try (InputStream in = agent.getInputStream(e)) {
                        readMixin(in.readAllBytes(), touched);
                    }
                }
            }
        }

        Map<String, Map<String, String>> now = new TreeMap<>();
        String jarSha;
        try (JarFile game = new JarFile(args[1])) {
            for (String cls : touched.keySet()) {
                JarEntry e = game.getJarEntry(cls.replace('.', '/') + ".class");
                Map<String, String> methods = new TreeMap<>();
                if (e != null) {
                    try (InputStream in = game.getInputStream(e)) {
                        ClassNode cn = new ClassNode();
                        new ClassReader(in.readAllBytes()).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        for (MethodNode m : cn.methods) {
                            methods.put(m.name + m.desc, fingerprint(m));
                        }
                    }
                }
                now.put(cls, methods);
            }
        }
        jarSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[1]))));

        if (!Files.exists(baselinePath) || accept) {
            Files.writeString(baselinePath, toJson(jarSha, now));
            System.out.println("DRIFT: baseline " + (accept ? "accepted" : "created") + " for jar " + jarSha.substring(0, 12)
                + " (" + now.size() + " target classes)");
            return;
        }

        Map<String, Map<String, String>> base = parse(Files.readString(baselinePath));
        List<String> review = new ArrayList<>();
        List<String> info = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> c : now.entrySet()) {
            Map<String, String> old = base.get(c.getKey());
            if (old == null) {
                info.add("new target class " + c.getKey() + " (no baseline yet)");
                continue;
            }
            Set<String> names = touched.get(c.getKey());
            Set<String> all = new HashSet<>(old.keySet());
            all.addAll(c.getValue().keySet());
            for (String key : new TreeSet<>(all)) {
                String a = old.get(key);
                String b = c.getValue().get(key);
                if (a != null && a.equals(b)) {
                    continue;
                }
                String what = a == null ? "added" : b == null ? "REMOVED" : "changed";
                String name = key.substring(0, key.indexOf('('));
                String line = c.getKey() + "." + key + " " + what;
                if (names.contains(name) || names.contains(key)) {
                    review.add(line);
                } else {
                    info.add(line);
                }
            }
        }
        info.forEach(l -> System.out.println("DRIFT: fyi    " + l));
        review.forEach(l -> System.out.println("DRIFT: REVIEW " + l + "  <- a PLZ mixin injects into, wraps or overwrites it"));
        System.out.println("DRIFT: " + review.size() + " targeted method(s) changed, " + info.size() + " other change(s) in patched classes");
        if (!review.isEmpty()) {
            System.out.println("DRIFT: review each mixin above against the new vanilla code, then rebuild with -AcceptDrift");
            System.exit(5);
        }
    }

    private static void readMixin(byte[] bytes, Map<String, Set<String>> touched) {
        List<String> targets = new ArrayList<>();
        Set<String> methods = new HashSet<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
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
                        if (OVERWRITE.equals(annotation)) {
                            methods.add(name);
                            return null;
                        }
                        if (!INJECTORS.contains(annotation)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitArray(String key) {
                                if (!"method".equals(key)) {
                                    return null;
                                }
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String n, Object value) {
                                        String selector = String.valueOf(value);
                                        int paren = selector.indexOf('(');
                                        String bare = paren < 0 ? selector : selector.substring(0, paren);
                                        methods.add(bare.endsWith("*") ? bare.substring(0, bare.length() - 1) : bare);
                                    }
                                };
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        for (String t : targets) {
            touched.computeIfAbsent(t, k -> new HashSet<>()).addAll(methods);
        }
    }

    private static String fingerprint(MethodNode m) throws Exception {
        Map<LabelNode, Integer> labels = new HashMap<>();
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof LabelNode l) {
                labels.put(l, labels.size());
            }
        }
        StringBuilder sb = new StringBuilder().append(m.access).append(';');
        for (AbstractInsnNode n : m.instructions) {
            if (n.getOpcode() < 0 && !(n instanceof LabelNode)) {
                continue;
            }
            sb.append(n.getOpcode()).append(' ');
            if (n instanceof LabelNode l) {
                sb.append('L').append(labels.get(l));
            } else if (n instanceof MethodInsnNode x) {
                sb.append(x.owner).append('.').append(x.name).append(x.desc);
            } else if (n instanceof FieldInsnNode x) {
                sb.append(x.owner).append('.').append(x.name).append(':').append(x.desc);
            } else if (n instanceof TypeInsnNode x) {
                sb.append(x.desc);
            } else if (n instanceof LdcInsnNode x) {
                sb.append(x.cst);
            } else if (n instanceof IntInsnNode x) {
                sb.append(x.operand);
            } else if (n instanceof VarInsnNode x) {
                sb.append(x.var);
            } else if (n instanceof IincInsnNode x) {
                sb.append(x.var).append('+').append(x.incr);
            } else if (n instanceof JumpInsnNode x) {
                sb.append('L').append(labels.get(x.label));
            } else if (n instanceof InvokeDynamicInsnNode x) {
                sb.append(x.name).append(x.desc);
            } else if (n instanceof TableSwitchInsnNode x) {
                sb.append(x.min).append('-').append(x.max);
            } else if (n instanceof LookupSwitchInsnNode x) {
                sb.append(x.keys);
            } else if (n instanceof MultiANewArrayInsnNode x) {
                sb.append(x.desc).append(x.dims);
            }
            sb.append('\n');
        }
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String toJson(String jarSha, Map<String, Map<String, String>> classes) {
        StringBuilder sb = new StringBuilder("{\n  \"jarSha256\": \"").append(jarSha).append("\",\n  \"classes\": {\n");
        int i = 0;
        for (Map.Entry<String, Map<String, String>> c : classes.entrySet()) {
            sb.append("    \"").append(c.getKey()).append("\": {\n");
            int j = 0;
            for (Map.Entry<String, String> m : c.getValue().entrySet()) {
                sb.append("      \"").append(m.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\": \"").append(m.getValue()).append('"');
                sb.append(++j < c.getValue().size() ? ",\n" : "\n");
            }
            sb.append("    }").append(++i < classes.size() ? ",\n" : "\n");
        }
        return sb.append("  }\n}\n").toString();
    }

    private static Map<String, Map<String, String>> parse(String json) {
        Map<String, Map<String, String>> out = new TreeMap<>();
        String current = null;
        for (String raw : json.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("\"classes\"") || line.startsWith("\"jarSha256\"")) {
                continue;
            }
            if (line.endsWith("{") && line.startsWith("\"")) {
                current = line.substring(1, line.indexOf("\":"));
                out.put(current, new TreeMap<>());
            } else if (current != null && line.startsWith("\"") && line.contains("\": \"")) {
                int split = line.lastIndexOf("\": \"");
                String key = line.substring(1, split).replace("\\\"", "\"").replace("\\\\", "\\");
                String value = line.substring(split + 4, line.lastIndexOf('"'));
                out.get(current).put(key, value);
            }
        }
        return out;
    }
}

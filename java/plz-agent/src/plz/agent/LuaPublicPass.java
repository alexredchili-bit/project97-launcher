package plz.agent;

import java.nio.charset.StandardCharsets;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class LuaPublicPass {
    static final String DESCRIPTOR = "Lplz/agent/LuaPublic;";
    private static final byte[] MARKER = DESCRIPTOR.getBytes(StandardCharsets.UTF_8);
    private static final int HIDDEN = Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;

    private LuaPublicPass() {
    }

    static byte[] apply(byte[] bytes) {
        if (!contains(bytes, MARKER)) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        java.util.Set<String> methods = new java.util.HashSet<>();
        java.util.Set<String> fields = new java.util.HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (DESCRIPTOR.equals(annotation)) {
                            methods.add(name + desc);
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
                        if (DESCRIPTOR.equals(annotation)) {
                            fields.add(name);
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        if (methods.isEmpty() && fields.isEmpty()) {
            return bytes;
        }
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                int flags = methods.contains(name + desc) ? (access & ~HIDDEN) | Opcodes.ACC_PUBLIC : access;
                return super.visitMethod(flags, name, desc, signature, exceptions);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                int flags = fields.contains(name) ? (access & ~HIDDEN) | Opcodes.ACC_PUBLIC : access;
                return super.visitField(flags, name, desc, signature, value);
            }
        }, 0);
        return writer.toByteArray();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}

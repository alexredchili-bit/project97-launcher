package zombie.plz;

import java.util.ArrayList;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;
import zombie.core.VBO.GLVertexBufferObject;
import zombie.debug.DebugType;
import zombie.plz.access.PLZGLVertexBufferObjectAccess;

public final class PLZPersistentVbo {
    public static final int STORAGE_FLAGS = GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;

    // Fenced at the next map of ANY buffer, so a run of unmaps inside one frame costs one sync point.
    private static final ArrayList<GLVertexBufferObject> awaitingFence = new ArrayList<>();
    private static boolean announced;

    private PLZPersistentVbo() {
    }

    public static boolean usePersistent() {
        return PLZPerf.PERSISTENT_VBO && GL.getCapabilities().OpenGL32 && GL.getCapabilities().GL_ARB_buffer_storage;
    }

    public static void fencePending() {
        for (int i = 0; i < awaitingFence.size(); i++) {
            PLZGLVertexBufferObjectAccess vbo = (PLZGLVertexBufferObjectAccess) awaitingFence.get(i);
            if (vbo.plzFence() != 0L) {
                GL32.glDeleteSync(vbo.plzFence());
            }

            vbo.plzSetFence(GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
        }

        awaitingFence.clear();
    }

    public static void awaitFence(GLVertexBufferObject vbo) {
        awaitingFence.add(vbo);
    }

    public static void forget(GLVertexBufferObject vbo) {
        awaitingFence.remove(vbo);
    }

    public static void announceOnce() {
        if (!announced) {
            announced = true;
            DebugType.Mod.println("PLZ: persistent VBO mapping active (GL_ARB_buffer_storage)");
        }
    }
}

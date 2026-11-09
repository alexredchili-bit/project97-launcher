package plz.mixin.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;
import org.lwjglx.opengl.OpenGLException;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.VBO.GLVertexBufferObject;
import zombie.core.VBO.IGLBufferObject;
import zombie.debug.DebugType;
import zombie.plz.PLZPersistentVbo;
import zombie.plz.access.PLZGLVertexBufferObjectAccess;

@Mixin(GLVertexBufferObject.class)
abstract class GLVertexBufferObjectMixin implements PLZGLVertexBufferObjectAccess {
    @Shadow
    public static IGLBufferObject funcs;

    @Shadow
    private long size;

    @Shadow
    @Final
    private int type;

    @Shadow
    private transient int id;

    @Shadow
    private transient boolean mapped;

    @Shadow
    private transient boolean cleared;

    @Shadow
    private transient ByteBuffer buffer;

    @Unique
    private boolean plz$persistent;

    @Unique
    private long plz$fence;

    @Override
    public long plzFence() {
        return this.plz$fence;
    }

    @Override
    public void plzSetFence(long fence) {
        this.plz$fence = fence;
    }

    // Immutable storage cannot be reallocated, and the permanent mapping leaves the per-batch orphan nothing to do.
    @Inject(method = "clear", at = @At("HEAD"), cancellable = true)
    private void plz$keepPersistentStorage(CallbackInfo ci) {
        if (this.plz$persistent) {
            ci.cancel();
        }
    }

    @Inject(
        method = "map()Ljava/nio/ByteBuffer;",
        at = @At(value = "INVOKE", target = "Lzombie/core/VBO/GLVertexBufferObject;clear()V"),
        cancellable = true
    )
    private void plz$mapPersistent(CallbackInfoReturnable<ByteBuffer> cir) {
        if (this.plz$persistent || this.buffer == null && PLZPersistentVbo.usePersistent()) {
            cir.setReturnValue(this.plz$mapPersistentBuffer());
        }
    }

    // Allocate and map once, then hand the same mapping back on every map().
    @Unique
    private ByteBuffer plz$mapPersistentBuffer() {
        PLZPersistentVbo.fencePending();
        if (this.buffer == null) {
            funcs.glBindBuffer(this.type, this.id);
            GL44.glBufferStorage(this.type, this.size, PLZPersistentVbo.STORAGE_FLAGS);
            this.buffer = GL30.glMapBufferRange(this.type, 0L, this.size, PLZPersistentVbo.STORAGE_FLAGS, null);
            if (this.buffer == null) {
                throw new OpenGLException("Failed to persistently map a buffer " + this.size + " bytes long");
            }

            this.plz$persistent = true;
            this.cleared = true;
            PLZPersistentVbo.announceOnce();
        } else if (this.plz$fence != 0L) {
            int result = GL32.glClientWaitSync(this.plz$fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1000000000L);
            GL32.glDeleteSync(this.plz$fence);
            this.plz$fence = 0L;
            if (result == GL32.GL_TIMEOUT_EXPIRED || result == GL32.GL_WAIT_FAILED) {
                DebugType.Mod.warn("PLZ: persistent VBO fence wait returned 0x" + Integer.toHexString(result));
            }
        }

        this.buffer.order(ByteOrder.nativeOrder()).clear().limit((int) this.size);
        this.mapped = true;
        return this.buffer;
    }

    @Inject(
        method = "unmap",
        at = @At(value = "FIELD", target = "Lzombie/core/VBO/GLVertexBufferObject;mapped:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
        cancellable = true
    )
    private void plz$unmapPersistent(CallbackInfoReturnable<Boolean> cir) {
        if (this.plz$persistent) {
            PLZPersistentVbo.awaitFence((GLVertexBufferObject) (Object) this);
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "doDestroy",
        at = @At(value = "INVOKE", target = "Lzombie/core/VBO/GLVertexBufferObject;unmap()Z", shift = At.Shift.AFTER)
    )
    private void plz$destroyPersistent(CallbackInfo ci) {
        if (this.plz$persistent) {
            funcs.glBindBuffer(this.type, this.id);
            funcs.glUnmapBuffer(this.type);
            if (this.plz$fence != 0L) {
                GL32.glDeleteSync(this.plz$fence);
                this.plz$fence = 0L;
            }

            this.plz$persistent = false;
            this.buffer = null;
            PLZPersistentVbo.forget((GLVertexBufferObject) (Object) this);
        }
    }
}

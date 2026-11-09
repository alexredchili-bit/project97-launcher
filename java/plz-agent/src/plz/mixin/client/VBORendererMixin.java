package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.opengl.VBOLinesShader;
import zombie.core.opengl.VBORenderer;
import zombie.plz.PLZPerf;
import zombie.plz.access.PLZVBORendererAccess;
import zombie.plz.access.PLZVBORendererRunAccess;

@Mixin(VBORenderer.class)
abstract class VBORendererMixin implements PLZVBORendererAccess {
    @Unique
    private static final String plz$ADD_QUAD = "addQuad(FFFFFFFFFFFFFFFFFFFFFFFF)V";

    // Vanilla's 4 KB holds 28 rain particles; capped at 1.5 MB because a short index cannot pass 65536 vertices.
    @Unique
    private static final int plz$MAX_BATCH_BYTES = 1572864;

    @Unique
    private static final int plz$BATCH_BYTES = plz$batchBytes();

    @Unique
    private Object plz$run;

    @Shadow
    private int elementSize;

    @Shadow
    private int vertexOffset;

    @Shadow
    private int colorOffset;

    @Shadow
    private int uv1Offset;

    @Shadow
    private ByteBuffer elements;

    @Shadow
    private ByteBuffer indices;

    @Shadow
    private float dx;

    @Shadow
    private float dy;

    @Shadow
    private float dz;

    @Shadow
    private VBOLinesShader shaderPositionColorUv;

    @Shadow
    private void create() {
        throw new AssertionError();
    }

    @Shadow
    abstract boolean isFull();

    @Shadow
    abstract boolean hasRoomFor(int numElements);

    @Shadow
    public abstract void flush();

    @Shadow
    private VBOLinesShader initShader(String name, VBOLinesShader shader) {
        throw new AssertionError();
    }

    @Unique
    private static int plz$batchBytes() {
        long bytes = PLZPerf.VBO_BATCH_KB * 1024L;
        return (int) Math.max(4096L, Math.min(plz$MAX_BATCH_BYTES, bytes));
    }

    // The same shader the UV run path uses, reachable so PLZRainTiles can draw its own batch.
    @Override
    public VBOLinesShader plzShaderPositionColorUv() {
        this.shaderPositionColorUv = this.initShader("vboRenderer_PositionColorUV", this.shaderPositionColorUv);
        return this.shaderPositionColorUv;
    }

    @ModifyConstant(method = "setFormat", constant = @Constant(intValue = 4096))
    private int plz$elementBytes(int vanillaBytes) {
        return plz$BATCH_BYTES;
    }

    @ModifyConstant(method = "create", constant = @Constant(intValue = 4096))
    private int plz$elementBuffer(int vanillaBytes) {
        return plz$BATCH_BYTES;
    }

    @ModifyConstant(method = "create", constant = @Constant(intValue = 1024))
    private int plz$indexBuffer(int vanillaBytes) {
        return plz$BATCH_BYTES / 4;
    }

    @ModifyConstant(method = "create", constant = @Constant(longValue = 4096L))
    private long plz$elementVbo(long vanillaBytes) {
        return plz$BATCH_BYTES;
    }

    @ModifyConstant(method = "create", constant = @Constant(longValue = 1024L))
    private long plz$indexVbo(long vanillaBytes) {
        return plz$BATCH_BYTES / 4;
    }

    @WrapOperation(
        method = plz$ADD_QUAD,
        at = @At(value = "INVOKE", target = "Lzombie/core/opengl/VBORenderer;currentRun()Lzombie/core/opengl/VBORenderer$Run;", ordinal = 0)
    )
    @Coerce
    private Object plz$keepRun(VBORenderer renderer, Operation<Object> original) {
        Object current = original.call(renderer);
        this.plz$run = current;
        return current;
    }

    @Inject(method = plz$ADD_QUAD, at = @At(value = "INVOKE", target = "Lzombie/core/opengl/VBORenderer;reserve(I)V", ordinal = 1), cancellable = true)
    private void plz$fastQuad(
        float x0,
        float y0,
        float z0,
        float u0,
        float v0,
        float x1,
        float y1,
        float z1,
        float u1,
        float v1,
        float x2,
        float y2,
        float z2,
        float u2,
        float v2,
        float x3,
        float y3,
        float z3,
        float u3,
        float v3,
        float r,
        float g,
        float b,
        float a,
        CallbackInfo ci
    ) {
        if (PLZPerf.VBO_FAST_QUADS && this.vertexOffset != -1 && this.colorOffset != -1) {
            this.plz$addQuad((PLZVBORendererRunAccess) this.plz$run, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3, r, g, b, a);
            ci.cancel();
        }
    }

    // Same bytes as four addElement calls, without re-deriving the offsets and the run four times.
    @Unique
    private void plz$addQuad(
        PLZVBORendererRunAccess run,
        float x0,
        float y0,
        float z0,
        float u0,
        float v0,
        float x1,
        float y1,
        float z1,
        float u1,
        float v1,
        float x2,
        float y2,
        float z2,
        float u2,
        float v2,
        float x3,
        float y3,
        float z3,
        float u3,
        float v3,
        float r,
        float g,
        float b,
        float a
    ) {
        if (this.elements == null) {
            this.create();
        }

        if (this.isFull() || !this.hasRoomFor(4)) {
            this.flush();
        }

        ByteBuffer e = this.elements;
        int stride = this.elementSize;
        int pos = e.position();
        int base = pos / stride;
        this.indices.putShort((short) base);
        this.indices.putShort((short) (base + 1));
        this.indices.putShort((short) (base + 2));
        this.indices.putShort((short) (base + 3));
        float ox = this.dx;
        float oy = this.dy;
        float oz = this.dz;
        this.plz$putVertex(e, pos, ox + x0, oy + y0, oz + z0, u0, v0, r, g, b, a);
        this.plz$putVertex(e, pos + stride, ox + x1, oy + y1, oz + z1, u1, v1, r, g, b, a);
        this.plz$putVertex(e, pos + 2 * stride, ox + x2, oy + y2, oz + z2, u2, v2, r, g, b, a);
        this.plz$putVertex(e, pos + 3 * stride, ox + x3, oy + y3, oz + z3, u3, v3, r, g, b, a);
        e.position(pos + 4 * stride);
        run.plzAddVertexCount(4);
    }

    @Unique
    private void plz$putVertex(ByteBuffer e, int at, float x, float y, float z, float u, float v, float r, float g, float b, float a) {
        int i = at + this.vertexOffset;
        e.putFloat(i, x);
        e.putFloat(i + 4, y);
        e.putFloat(i + 8, z);
        i = at + this.colorOffset;
        e.putFloat(i, r);
        e.putFloat(i + 4, g);
        e.putFloat(i + 8, b);
        e.putFloat(i + 12, a);
        if (this.uv1Offset != -1) {
            i = at + this.uv1Offset;
            e.putFloat(i, u);
            e.putFloat(i + 4, v);
        }
    }
}

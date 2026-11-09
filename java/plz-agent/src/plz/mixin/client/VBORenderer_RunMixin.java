package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.plz.access.PLZVBORendererRunAccess;

@Mixin(targets = "zombie.core.opengl.VBORenderer$Run")
abstract class VBORenderer_RunMixin implements PLZVBORendererRunAccess {
    @Shadow
    int vertexCount;

    @Override
    public void plzAddVertexCount(int count) {
        this.vertexCount += count;
    }
}

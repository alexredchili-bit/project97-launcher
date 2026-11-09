package plz.mixin.client;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.plz.access.PLZSceneObjectAccess;

@Mixin(targets = "zombie.vehicles.UI3DScene$SceneObject")
abstract class UI3DScene_SceneObjectMixin implements PLZSceneObjectAccess {
    // parent is typed as the private SceneObject, which no mixin source can name.
    @Unique
    private static final VarHandle plz$PARENT = plz$parentHandle();

    @Shadow
    boolean visible;

    @Shadow
    String attachment;

    @Shadow
    String parentAttachment;

    @Unique
    private Matrix4f plz$transform;

    @Shadow
    abstract Matrix4f getLocalTransform(Matrix4f transform);

    @Shadow
    abstract Matrix4f getGlobalTransform(Matrix4f transform);

    @Shadow
    abstract Matrix4f getAttachmentTransform(String attachmentName, Matrix4f transform);

    @Unique
    private static VarHandle plz$parentHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.unreflectVarHandle(lookup.lookupClass().getDeclaredField("parent"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Inject(method = "getLocalTransform", at = @At("HEAD"), cancellable = true)
    private void plz$solvedTransform(Matrix4f transform, CallbackInfoReturnable<Matrix4f> cir) {
        if (this.plz$transform != null) {
            cir.setReturnValue(transform.set(this.plz$transform));
        }
    }

    @Override
    public Matrix4f plzGetTransform() {
        return this.plz$transform;
    }

    @Override
    public void plzSetTransform(Matrix4f transform) {
        this.plz$transform = transform;
    }

    @Override
    public Matrix4f plzLocalTransform(Matrix4f transform) {
        return this.getLocalTransform(transform);
    }

    @Override
    public Matrix4f plzGlobalTransform(Matrix4f transform) {
        return this.getGlobalTransform(transform);
    }

    @Override
    public Matrix4f plzAttachmentTransform(String attachmentName, Matrix4f transform) {
        return this.getAttachmentTransform(attachmentName, transform);
    }

    @Override
    public Object plzParent() {
        return plz$PARENT.get((Object)this);
    }

    @Override
    public void plzSetParent(Object parent) {
        plz$PARENT.set((Object)this, parent);
    }

    @Override
    public boolean plzVisible() {
        return this.visible;
    }

    @Override
    public void plzSetAttachment(String attachment) {
        this.attachment = attachment;
    }

    @Override
    public String plzParentAttachment() {
        return this.parentAttachment;
    }

    @Override
    public void plzSetParentAttachment(String parentAttachment) {
        this.parentAttachment = parentAttachment;
    }
}

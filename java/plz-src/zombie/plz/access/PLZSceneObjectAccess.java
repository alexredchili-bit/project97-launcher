package zombie.plz.access;

import org.joml.Matrix4f;

public interface PLZSceneObjectAccess {
    Matrix4f plzGetTransform();

    void plzSetTransform(Matrix4f transform);

    Matrix4f plzLocalTransform(Matrix4f transform);

    Matrix4f plzGlobalTransform(Matrix4f transform);

    Matrix4f plzAttachmentTransform(String attachmentName, Matrix4f transform);

    Object plzParent();

    void plzSetParent(Object parent);

    boolean plzVisible();

    void plzSetAttachment(String attachment);

    String plzParentAttachment();

    void plzSetParentAttachment(String parentAttachment);
}

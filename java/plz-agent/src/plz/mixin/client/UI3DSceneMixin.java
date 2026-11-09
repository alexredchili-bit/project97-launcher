package plz.mixin.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import plz.agent.LuaPublic;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoGameCharacter;
import zombie.core.BoxedStaticValues;
import zombie.core.math.PZMath;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.visual.HumanVisual;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponPart;
import zombie.iso.sprite.IsoSprite;
import zombie.plz.PLZSceneTools;
import zombie.plz.access.PLZSceneCharacterAccess;
import zombie.plz.access.PLZSceneModelAccess;
import zombie.plz.access.PLZSceneObjectAccess;
import zombie.plz.access.PLZSceneVehicleAccess;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.ModelAttachment;
import zombie.scripting.objects.ModelScript;
import zombie.scripting.objects.ModelWeaponPart;
import zombie.util.StringUtils;
import zombie.vehicles.UI3DScene;

@Mixin(UI3DScene.class)
abstract class UI3DSceneMixin {
    // The scene object classes are private to UI3DScene, so they are named by Class and built through the nest's own lookup.
    @Unique
    private static final Class<?> plz$SCENE_OBJECT = plz$nested("SceneObject");

    @Unique
    private static final Class<?> plz$SCENE_MODEL = plz$nested("SceneModel");

    @Unique
    private static final Class<?> plz$SCENE_CHARACTER = plz$nested("SceneCharacter");

    @Unique
    private static final Class<?> plz$SCENE_VEHICLE = plz$nested("SceneVehicle");

    @Unique
    private static final MethodHandle plz$NEW_SCENE_MODEL = plz$sceneModelConstructor();

    @Shadow
    @Final
    private ArrayList<Object> objects;

    @Shadow
    @Final
    private Matrix4f projection;

    @Shadow
    @Final
    private Matrix4f modelView;

    @Unique
    private boolean plz$perspective;

    @Unique
    private float plz$fov = 50.0F;

    @Unique
    private float plz$near = 0.02F;

    @Unique
    private float plz$far = 500.0F;

    @Shadow
    abstract <C> C getSceneObjectById(String id, Class<C> clazz, boolean required);

    @Shadow
    private void calcMatrices(Matrix4f projection, Matrix4f modelView) {
        throw new AssertionError();
    }

    @Shadow
    private int screenWidth() {
        throw new AssertionError();
    }

    @Shadow
    private int screenHeight() {
        throw new AssertionError();
    }

    @Shadow
    private static Matrix4f allocMatrix4f() {
        throw new AssertionError();
    }

    @Shadow
    private static void releaseMatrix4f(Matrix4f matrix) {
        throw new AssertionError();
    }

    @Shadow
    private static Vector3f allocVector3f() {
        throw new AssertionError();
    }

    @Shadow
    private static void releaseVector3f(Vector3f vector3f) {
        throw new AssertionError();
    }

    @Unique
    private static Class<?> plz$nested(String name) {
        try {
            return Class.forName("zombie.vehicles.UI3DScene$" + name, false, UI3DScene.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private static MethodHandle plz$sceneModelConstructor() {
        try {
            return MethodHandles.lookup()
                .findConstructor(plz$SCENE_MODEL, MethodType.methodType(void.class, UI3DScene.class, String.class, ModelScript.class, Model.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private PLZSceneObjectAccess plz$object(String id) {
        return (PLZSceneObjectAccess)this.getSceneObjectById(id, plz$SCENE_OBJECT, true);
    }

    @Unique
    private PLZSceneCharacterAccess plz$character(String id) {
        return (PLZSceneCharacterAccess)this.getSceneObjectById(id, plz$SCENE_CHARACTER, true);
    }

    @Unique
    private Object plz$model(String id) {
        return this.getSceneObjectById(id, plz$SCENE_MODEL, true);
    }

    @Unique
    private PLZSceneVehicleAccess plz$vehicle(String id) {
        return (PLZSceneVehicleAccess)this.getSceneObjectById(id, plz$SCENE_VEHICLE, true);
    }

    @Unique
    @LuaPublic
    private static void plzHideCursor(boolean hidden) {
        PLZSceneTools.hideCursor(hidden);
    }

    @Unique
    @LuaPublic
    private static IsoSprite plzPosterSprite(String key, String paperPath, KahluaTable holes, boolean north, double widthM, double heightM) {
        return PLZSceneTools.posterSprite(key, paperPath, holes, north, widthM, heightM);
    }

    @Unique
    @LuaPublic
    private static void plzMuzzleFlash(IsoGameCharacter character) {
        PLZSceneTools.muzzleFlash(character);
    }

    @Inject(method = "fromLua1", at = @At("HEAD"), cancellable = true)
    private void plz$fromLua1(String func, Object arg0, CallbackInfoReturnable<Object> cir) {
        switch (func) {
            case "plzSetPerspective": {
                this.plz$perspective = (Boolean)arg0;
                this.calcMatrices(this.projection, this.modelView);
                cir.setReturnValue(null);
                break;
            }
            case "plzSetFov": {
                this.plz$fov = PZMath.clamp(((Double)arg0).floatValue(), 1.0F, 170.0F);
                this.calcMatrices(this.projection, this.modelView);
                cir.setReturnValue(null);
                break;
            }
            case "plzClearObjectTransform": {
                this.plz$object((String)arg0).plzSetTransform(null);
                cir.setReturnValue(null);
                break;
            }
            case "getVehicleSkinCount": {
                PLZSceneVehicleAccess vehicle = this.plz$vehicle((String)arg0);
                cir.setReturnValue(BoxedStaticValues.toDouble(vehicle.plzScript() == null ? 0.0 : (double)vehicle.plzScript().getSkinCount()));
                break;
            }
            default:
                break;
        }
    }

    @Inject(method = "fromLua2", at = @At("HEAD"), cancellable = true)
    private void plz$fromLua2(String func, Object arg0, Object arg1, CallbackInfoReturnable<Object> cir) {
        switch (func) {
            case "plzCreatePlayerCharacter": {
                cir.setReturnValue(this.plz$createPlayerCharacter((String)arg0, (IsoGameCharacter)arg1));
                break;
            }
            case "plzPoseCharacterToGun": {
                cir.setReturnValue(this.plz$poseCharacterToGun((String)arg0, (String)arg1, null));
                break;
            }
            case "plzHandsReport": {
                cir.setReturnValue(this.plz$handsReport((String)arg0, (String)arg1));
                break;
            }
            case "plzSetArmsOnly": {
                ((PLZSceneCharacterAccess)this.getSceneObjectById((String)arg0, plz$SCENE_OBJECT, true)).plzSetArmsRoot((String)arg1);
                cir.setReturnValue(PLZSceneTools.resolveArmsFields());
                break;
            }
            case "plzSetClip": {
                this.plz$near = Math.max(0.001F, ((Double)arg0).floatValue());
                this.plz$far = Math.max(this.plz$near + 0.01F, ((Double)arg1).floatValue());
                this.calcMatrices(this.projection, this.modelView);
                cir.setReturnValue(null);
                break;
            }
            case "plzSetObjectTransform": {
                PLZSceneObjectAccess sceneObject = this.plz$object((String)arg0);
                KahluaTable m = (KahluaTable)arg1;
                float[] v = new float[16];
                for (int i = 0; i < 16; i++) {
                    v[i] = ((Double)m.rawget(i + 1)).floatValue();
                }

                if (sceneObject.plzGetTransform() == null) {
                    sceneObject.plzSetTransform(new Matrix4f());
                }

                sceneObject.plzGetTransform().set(v);
                cir.setReturnValue(null);
                break;
            }
            case "plzCreateWeaponModel": {
                cir.setReturnValue(this.plz$createWeaponModel((String)arg0, (HandWeapon)arg1));
                break;
            }
            case "plzGetAttachment": {
                ModelScript modelScript = ScriptManager.instance.getModelScript((String)arg0);
                ModelAttachment attachment = modelScript == null ? null : modelScript.getAttachmentById((String)arg1);
                if (attachment == null) {
                    cir.setReturnValue(null);
                    break;
                }

                KahluaTable t = LuaManager.platform.newTable();
                t.rawset("x", (double)attachment.getOffset().x);
                t.rawset("y", (double)attachment.getOffset().y);
                t.rawset("z", (double)attachment.getOffset().z);
                t.rawset("rx", (double)attachment.getRotate().x);
                t.rawset("ry", (double)attachment.getRotate().y);
                t.rawset("rz", (double)attachment.getRotate().z);
                t.rawset("scale", (double)attachment.getScale());
                cir.setReturnValue(t);
                break;
            }
            case "setVehicleVisibleParts": {
                PLZSceneVehicleAccess vehicle = this.plz$vehicle((String)arg0);
                vehicle.plzSetVisibleParts(arg1 == null ? null : (String)arg1);
                cir.setReturnValue(null);
                break;
            }
            case "setVehicleSkinIndex": {
                PLZSceneVehicleAccess vehicle = this.plz$vehicle((String)arg0);
                int index = arg1 == null ? 0 : (int)Math.floor(((Double)arg1).doubleValue());
                if (index != vehicle.plzSkinIndex()) {
                    vehicle.plzSetSkinIndex(index);
                    vehicle.plzResetModels();
                }

                cir.setReturnValue(null);
                break;
            }
            default:
                break;
        }
    }

    @Inject(method = "fromLua3", at = @At("HEAD"), cancellable = true)
    private void plz$fromLua3(String func, Object arg0, Object arg1, Object arg2, CallbackInfoReturnable<Object> cir) {
        switch (func) {
            case "plzMirrorPose": {
                PLZSceneCharacterAccess mirrored = (PLZSceneCharacterAccess)this.getSceneObjectById((String)arg0, plz$SCENE_OBJECT, true);
                mirrored.plzSetPoseSource((IsoGameCharacter)arg1);
                mirrored.plzSetPoseWeight(PZMath.clamp(((Double)arg2).floatValue(), 0.0F, 1.0F));
                cir.setReturnValue(null);
                break;
            }
            case "plzPoseCharacterToGun": {
                cir.setReturnValue(this.plz$poseCharacterToGun((String)arg0, (String)arg1, (KahluaTable)arg2));
                break;
            }
            case "plzMeasureSights": {
                cir.setReturnValue(this.plz$measureSights((String)arg0, ((Double)arg1).floatValue(), ((Double)arg2).floatValue()));
                break;
            }
            case "plzSampleImage": {
                cir.setReturnValue(BoxedStaticValues.toDouble(PLZSceneTools.sampleImage((String)arg0, ((Double)arg1).floatValue(), ((Double)arg2).floatValue())));
                break;
            }
            default:
                break;
        }
    }

    @Inject(method = "fromLua4", at = @At("HEAD"), cancellable = true)
    private void plz$fromLua4(String func, Object arg0, Object arg1, Object arg2, Object arg3, CallbackInfoReturnable<Object> cir) {
        switch (func) {
            case "plzGunToHand": {
                cir.setReturnValue(this.plz$gunToHand((String)arg0, (String)arg1, (KahluaTable)arg2, ((Double)arg3).floatValue()));
                break;
            }
            case "setVehiclePaintColor": {
                PLZSceneVehicleAccess vehicle = this.plz$vehicle((String)arg0);
                float h = arg1 == null ? 0.0F : ((Double)arg1).floatValue();
                float sat = arg2 == null ? 0.0F : ((Double)arg2).floatValue();
                float v = arg3 == null ? 0.0F : ((Double)arg3).floatValue();
                vehicle.plzPaint().set(PZMath.clamp(h, 0.0F, 1.0F), PZMath.clamp(sat, 0.0F, 1.0F), PZMath.clamp(v, 0.0F, 1.0F));
                cir.setReturnValue(null);
                break;
            }
            default:
                break;
        }
    }

    @Inject(method = "calcMatrices", at = @At("HEAD"), cancellable = true)
    private void plz$perspectiveMatrices(Matrix4f projection, Matrix4f modelView, CallbackInfo ci) {
        if (this.plz$perspective) {
            float aspect = (float)this.screenWidth() / Math.max(1.0F, (float)this.screenHeight());
            projection.setPerspective(this.plz$fov * (float) (Math.PI / 180.0), aspect, this.plz$near, this.plz$far);
            modelView.identity();
            ci.cancel();
        }
    }

    @Unique
    private Boolean plz$createPlayerCharacter(String id, IsoGameCharacter source) {
        PLZSceneCharacterAccess character = (PLZSceneCharacterAccess)((UI3DScene)(Object)this).fromLua1("createCharacter", id);
        if (!(source instanceof zombie.core.skinnedmodel.visual.IHumanVisual human) || human.getHumanVisual() == null) {
            return Boolean.FALSE;
        }

        zombie.core.skinnedmodel.visual.ItemVisuals itemVisuals = new zombie.core.skinnedmodel.visual.ItemVisuals();
        source.getItemVisuals(itemVisuals);
        for (int i = itemVisuals.size() - 1; i >= 0; i--) {
            zombie.scripting.objects.Item script = itemVisuals.get(i).getScriptItem();
            if (script != null && script.getBodyLocation() != null && PLZSceneTools.isHeadLocation(script.getBodyLocation().toString())) {
                itemVisuals.remove(i);
            }
        }

        HumanVisual visual = new HumanVisual(character.plzAnimatedModel());
        visual.copyFrom(human.getHumanVisual());
        visual.setHairModel("Bald");
        visual.setBeardModel("");
        // initAnimatedModel queues a Naked outfit that the first update would dress over these clothes
        character.plzAnimatedModel().setOutfitName(null, visual.isFemale(), false);
        character.plzAnimatedModel().setModelData(visual, itemVisuals);
        character.plzAnimatedModel().setAlpha(1.0F);
        character.plzSetClearDepthBuffer(false);
        return Boolean.TRUE;
    }

    @Unique
    private static void plz$put(KahluaTable t, String prefix, Matrix4f m) {
        Vector3f p = m.getTranslation(allocVector3f());
        t.rawset(prefix + "x", (double)p.x);
        t.rawset(prefix + "y", (double)p.y);
        t.rawset(prefix + "z", (double)p.z);
        releaseVector3f(p);
    }

    @Unique
    private KahluaTable plz$handsReport(String characterId, String gunId) {
        PLZSceneCharacterAccess character = this.plz$character(characterId);
        PLZSceneObjectAccess gun = (PLZSceneObjectAccess)this.plz$model(gunId);
        KahluaTable t = LuaManager.platform.newTable();
        Matrix4f root = ((PLZSceneObjectAccess)character).plzGlobalTransform(allocMatrix4f());
        String[] bones = new String[]{"Bip01_Prop1", "Bip01_R_Hand", "Bip01_L_Hand", "Bip01_Head", "Bip01"};
        for (String bone : bones) {
            Matrix4f b = character.plzBoneMatrix(bone, allocMatrix4f());
            Matrix4f w = allocMatrix4f().set(root).mul(b);
            plz$put(t, bone + "_", w);
            plz$put(t, bone + "_local_", b);
            releaseMatrix4f(w);
            releaseMatrix4f(b);
        }

        Matrix4f g = gun.plzGlobalTransform(allocMatrix4f());
        plz$put(t, "gun_", g);
        Matrix4f head = allocMatrix4f().set(root).mul(character.plzBoneMatrix("Bip01_Head", allocMatrix4f()));
        Matrix4f inGun = allocMatrix4f().set(g).invert().mul(head);
        plz$put(t, "head_gun_", inGun);
        t.rawset("det", (double)root.determinant());
        releaseMatrix4f(inGun);
        releaseMatrix4f(head);
        plz$put(t, "root_", root);
        releaseMatrix4f(g);
        releaseMatrix4f(root);
        return t;
    }

    @Unique
    private Matrix4f plz$heldTransform(PLZSceneCharacterAccess character, PLZSceneModelAccess gun, KahluaTable correction, Matrix4f out) {
        character.plzBoneMatrix("Bip01_Prop1", out);
        ModelAttachment held = gun.plzModelScript().getAttachmentById("Bip01_Prop1");
        if (held != null) {
            Matrix4f self = ModelInstanceRenderData.makeAttachmentTransform(held, allocMatrix4f());
            out.mul(self);
            releaseMatrix4f(self);
        }

        Matrix4f inner = PLZSceneTools.handsCorrection(correction, allocMatrix4f());
        inner.mul(out, out);
        releaseMatrix4f(inner);
        Matrix4f outer = PLZSceneTools.rightCorrection(correction, allocMatrix4f());
        out.mul(outer);
        releaseMatrix4f(outer);
        return out;
    }

    @Unique
    private Boolean plz$gunToHand(String characterId, String gunId, KahluaTable correction, float weight) {
        PLZSceneCharacterAccess character = this.plz$character(characterId);
        Object gunObject = this.plz$model(gunId);
        PLZSceneObjectAccess gun = (PLZSceneObjectAccess)gunObject;
        PLZSceneObjectAccess characterObject = (PLZSceneObjectAccess)character;
        if (character.plzAimHeld() == null || gun.plzGetTransform() == null
            || character.plzAnimatedModel().getAnimationPlayer().getModelTransformsCount() == 0) {
            return Boolean.FALSE;
        }

        float w = PZMath.clamp(weight, 0.0F, 1.0F);
        float bodyScale = (float)PLZSceneTools.number(correction, "bodyScale", 1.0);
        Matrix4f aimRoot = allocMatrix4f().set(character.plzAimHeld()).invert();
        Matrix4f scratch = allocMatrix4f().set(gun.plzGetTransform()).scale(bodyScale);
        aimRoot.mulLocal(scratch);
        Vector3f look = character.plzBoneMatrix("Bip01_Prop1", scratch).getTranslation(new Vector3f());
        releaseMatrix4f(scratch);
        aimRoot.transformPosition(look);
        if (!character.plzFollowing()) {
            character.plzSetFollowing(true);
            character.plzLookAnchor().set(look);
            character.plzLook().set(look);
        } else {
            character.plzLook().lerp(look, (float)PLZSceneTools.number(correction, "reloadFollow", 0.08));
        }

        // the arms slide to keep the gun framed; turning the view instead would leave the 2D lane behind
        Vector3f anchor = character.plzLookAnchor();
        Vector3f current = character.plzLook();
        Matrix4f eyeRoot = allocMatrix4f().translation(
            anchor.x - current.x,
            anchor.y - current.y - (float)PLZSceneTools.number(correction, "reloadDrop", 0.04),
            anchor.z - current.z
        );
        eyeRoot.mul(aimRoot);
        if (characterObject.plzGetTransform() == null) {
            characterObject.plzSetTransform(new Matrix4f());
        }

        PLZSceneTools.lerp(aimRoot, eyeRoot, w, characterObject.plzGetTransform());
        Matrix4f held = this.plz$heldTransform(character, (PLZSceneModelAccess)gunObject, correction, allocMatrix4f());
        held.mulLocal(characterObject.plzGetTransform());
        held.scale(1.0F / bodyScale);
        PLZSceneTools.lerp(gun.plzGetTransform(), held, w, gun.plzGetTransform());
        character.plzAnimatedModel().setCullFace(characterObject.plzGetTransform().determinant() < 0.0F ? 1028 : 1029);
        releaseMatrix4f(held);
        releaseMatrix4f(eyeRoot);
        releaseMatrix4f(aimRoot);
        return Boolean.TRUE;
    }

    @Unique
    private Boolean plz$poseCharacterToGun(String characterId, String gunId, KahluaTable correction) {
        PLZSceneCharacterAccess character = this.plz$character(characterId);
        Object gunObject = this.plz$model(gunId);
        PLZSceneObjectAccess characterObject = (PLZSceneObjectAccess)character;
        if (character.plzAnimatedModel().getAnimationPlayer().getModelTransformsCount() == 0) {
            return Boolean.FALSE;
        }

        Matrix4f hand = character.plzBoneMatrix("Bip01_Prop1", allocMatrix4f());
        ModelAttachment held = ((PLZSceneModelAccess)gunObject).plzModelScript().getAttachmentById("Bip01_Prop1");
        if (held != null) {
            Matrix4f self = ModelInstanceRenderData.makeAttachmentTransform(held, allocMatrix4f());
            hand.mul(self);
            releaseMatrix4f(self);
        }

        Matrix4f inner = PLZSceneTools.handsCorrection(correction, allocMatrix4f());
        inner.mul(hand, hand);
        releaseMatrix4f(inner);
        Matrix4f outer = PLZSceneTools.rightCorrection(correction, allocMatrix4f());
        hand.mul(outer);
        releaseMatrix4f(outer);
        if (character.plzAimHeld() == null) {
            character.plzSetAimHeld(new Matrix4f());
        }

        character.plzAimHeld().set(hand);
        character.plzSetFollowing(false);
        hand.invert();
        Matrix4f gunXfrm = ((PLZSceneObjectAccess)gunObject).plzGlobalTransform(allocMatrix4f());
        float bodyScale = (float)PLZSceneTools.number(correction, "bodyScale", 1.0);
        gunXfrm.scale(bodyScale);
        if (characterObject.plzGetTransform() == null) {
            characterObject.plzSetTransform(new Matrix4f());
        }

        gunXfrm.mul(hand, characterObject.plzGetTransform());
        // the scene's own character transform is mirrored and culls front faces; a solved one may not be
        character.plzAnimatedModel().setCullFace(characterObject.plzGetTransform().determinant() < 0.0F ? 1028 : 1029);
        releaseMatrix4f(gunXfrm);
        releaseMatrix4f(hand);
        return Boolean.TRUE;
    }

    @Unique
    private void plz$collectPoints(Object sceneModel, Matrix4f toRoot, ArrayList<float[]> out) {
        PLZSceneModelAccess model = (PLZSceneModelAccess)sceneModel;
        float[] points = PLZSceneTools.pointsOf(model.plzModelScript(), model.plzModel());
        if (points == null) {
            return;
        }

        Matrix4f m = allocMatrix4f().set(toRoot);
        ModelInstanceRenderData.postMultiplyMeshTransform(m, model.plzModel().mesh);
        if (model.plzModelScript().scale != 1.0F) {
            m.scale(model.plzModelScript().scale);
        }

        Vector3f v = allocVector3f();
        for (int i = 0; i + 2 < points.length; i += 3) {
            m.transformPosition(v.set(points[i], points[i + 1], points[i + 2]));
            out.add(new float[]{v.x, v.y, v.z});
        }

        releaseVector3f(v);
        releaseMatrix4f(m);
    }

    @Unique
    private KahluaTable plz$measureSights(String id, float fwd, float up) {
        Object root = this.plz$model(id);
        ArrayList<float[]> pts = new ArrayList<>();
        Matrix4f identity = allocMatrix4f().identity();
        this.plz$collectPoints(root, identity, pts);
        for (int i = 0; i < this.objects.size(); i++) {
            Object object = this.objects.get(i);
            if (!plz$SCENE_MODEL.isInstance(object)) {
                continue;
            }

            PLZSceneObjectAccess child = (PLZSceneObjectAccess)object;
            if (child.plzParent() != root || !child.plzVisible()) {
                continue;
            }

            Matrix4f rel = allocMatrix4f().identity();
            if (child.plzParentAttachment() != null) {
                ((PLZSceneObjectAccess)root).plzAttachmentTransform(child.plzParentAttachment(), rel);
            }

            Matrix4f local = child.plzLocalTransform(allocMatrix4f());
            rel.mul(local);
            this.plz$collectPoints(object, rel, pts);
            releaseMatrix4f(local);
            releaseMatrix4f(rel);
        }

        releaseMatrix4f(identity);
        if (pts.isEmpty()) {
            return null;
        }

        HashMap<Integer, Float> profile = new HashMap<>();
        float ymin = Float.MAX_VALUE;
        float ymax = -Float.MAX_VALUE;
        for (float[] p : pts) {
            float y = p[1] * fwd;
            ymin = Math.min(ymin, y);
            ymax = Math.max(ymax, y);
            if (Math.abs(p[0]) > 0.012F) {
                continue;
            }

            int bin = Math.round(y / 0.001F);
            float h = p[2] * up;
            Float cur = profile.get(bin);
            if (cur == null || h > cur) {
                profile.put(bin, h);
            }
        }

        ArrayList<Integer> bins = new ArrayList<>(profile.keySet());
        java.util.Collections.sort(bins);
        ArrayList<float[]> hull = new ArrayList<>();
        for (int b : bins) {
            float[] p = new float[]{b * 0.001F, profile.get(b)};
            while (hull.size() >= 2) {
                float[] a = hull.get(hull.size() - 2);
                float[] c = hull.get(hull.size() - 1);
                if ((c[0] - a[0]) * (p[1] - a[1]) - (c[1] - a[1]) * (p[0] - a[0]) >= 0.0F) {
                    hull.remove(hull.size() - 1);
                } else {
                    break;
                }
            }

            hull.add(p);
        }

        float length = ymax - ymin;
        float[] bestA = null;
        float[] bestC = null;
        for (int i = 0; i + 1 < hull.size(); i++) {
            float[] a = hull.get(i);
            float[] c = hull.get(i + 1);
            if (c[0] - a[0] < length * 0.2F) {
                continue;
            }

            if (a[0] < ymin + length * 0.15F && c[0] < ymin + length * 0.6F) {
                continue;
            }

            if (bestA == null || c[0] - a[0] > bestC[0] - bestA[0]) {
                bestA = a;
                bestC = c;
            }
        }

        if (bestA == null) {
            return null;
        }

        KahluaTable t = LuaManager.platform.newTable();
        t.rawset("rearY", (double)bestA[0]);
        t.rawset("rearH", (double)bestA[1]);
        t.rawset("frontY", (double)bestC[0]);
        t.rawset("frontH", (double)bestC[1]);
        t.rawset("slope", Math.toDegrees(Math.atan2(bestC[1] - bestA[1], bestC[0] - bestA[0])));
        t.rawset("ymin", (double)ymin);
        t.rawset("ymax", (double)ymax);
        t.rawset("points", (double)pts.size());
        return t;
    }

    @Unique
    private Object plz$addModel(String id, String modelName) {
        ModelScript modelScript = ScriptManager.instance.getModelScript(modelName);
        if (modelScript == null) {
            return null;
        }

        Model model = ModelManager.instance.getLoadedModel(modelName);
        if (model == null) {
            return null;
        }

        Object sceneModel;
        try {
            sceneModel = plz$NEW_SCENE_MODEL.invoke((UI3DScene)(Object)this, id, modelScript, model);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }

        this.objects.add(sceneModel);
        return sceneModel;
    }

    @Unique
    private KahluaTable plz$createWeaponModel(String id, HandWeapon weapon) {
        if (this.getSceneObjectById(id, plz$SCENE_OBJECT, false) != null) {
            throw new IllegalStateException("scene object \"" + id + "\" exists");
        }

        String modelName = StringUtils.discardNullOrWhitespace(weapon.getStaticModel());
        Object root = modelName == null ? null : this.plz$addModel(id, modelName);
        if (root == null) {
            return null;
        }

        KahluaTable result = LuaManager.platform.newTable();
        KahluaTable parts = LuaManager.platform.newTable();
        result.rawset("model", modelName);
        result.rawset("parts", parts);
        ArrayList<ModelWeaponPart> modelWeaponParts = weapon.getModelWeaponPart();
        if (modelWeaponParts == null) {
            return result;
        }

        java.util.List<WeaponPart> fitted = weapon.getAllWeaponParts();
        int n = 0;
        for (int i = 0; i < fitted.size(); i++) {
            WeaponPart part = fitted.get(i);
            for (int j = 0; j < modelWeaponParts.size(); j++) {
                ModelWeaponPart mwp = modelWeaponParts.get(j);
                if (!part.getFullType().equals(mwp.partType)) {
                    continue;
                }

                String partModel = StringUtils.discardNullOrWhitespace(mwp.modelName);
                String partId = id + "#" + (n + 1);
                Object child = partModel == null ? null : this.plz$addModel(partId, partModel);
                if (child != null) {
                    PLZSceneObjectAccess childObject = (PLZSceneObjectAccess)child;
                    childObject.plzSetAttachment(mwp.attachmentNameSelf);
                    childObject.plzSetParent(root);
                    childObject.plzSetParentAttachment(mwp.attachmentParent);
                    KahluaTable entry = LuaManager.platform.newTable();
                    entry.rawset("id", partId);
                    entry.rawset("partType", mwp.partType);
                    entry.rawset("model", partModel);
                    entry.rawset("self", mwp.attachmentNameSelf);
                    entry.rawset("parent", mwp.attachmentParent);
                    n++;
                    parts.rawset(n, entry);
                }
                break;
            }
        }

        return result;
    }
}

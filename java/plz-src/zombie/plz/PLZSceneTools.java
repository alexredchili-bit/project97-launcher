package zombie.plz;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import org.joml.Matrix4f;
import org.lwjglx.BufferUtils;
import se.krka.kahlua.vm.KahluaTable;
import zombie.characters.IsoGameCharacter;
import zombie.core.SpriteRenderer;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.core.textures.Texture;
import zombie.debug.DebugType;
import zombie.iso.sprite.IsoSprite;
import zombie.scripting.objects.ModelScript;

public final class PLZSceneTools {
    private static final HashMap<String, java.awt.image.BufferedImage> images = new HashMap<>();
    private static final HashMap<String, float[]> meshPoints = new HashMap<>();
    private static final HashMap<String, IsoSprite> posters = new HashMap<>();
    private static final int TILE_W = 128;
    private static final int TILE_H = 256;
    private static final float PX_PER_M = 64.0F;
    private static final float WALL_FLOOR_Y = 208.0F;
    private static final float POSTER_CENTRE_UP = 96.0F;
    private static org.lwjglx.input.Cursor blankCursor;
    private static org.lwjglx.input.Cursor defaultCursor;
    private static final java.util.Set<String> HEAD_LOCATIONS = java.util.Set.of(
        "hat", "fullhat", "eyes", "lefteye", "righteye", "ears", "eartop", "nose", "fullsuithead", "fullsuitheadscba"
    );
    private static final FloatBuffer poseFrom = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer poseTo = BufferUtils.createFloatBuffer(16);
    private static java.lang.reflect.Field stateInfosField;
    private static java.lang.reflect.Field instDataField;
    private static java.lang.reflect.Field readyDataField;
    private static final float[] collapse = new float[16];
    private static final FloatBuffer tmp = BufferUtils.createFloatBuffer(16);
    private static final int[] linear = {0, 1, 2, 4, 5, 6, 8, 9, 10};
    private static Boolean armsFieldsResolved;

    private PLZSceneTools() {
    }

    private static java.awt.image.BufferedImage image(String path) {
        java.awt.image.BufferedImage image = images.get(path);
        if (image == null && !images.containsKey(path)) {
            try {
                String abs = zombie.ZomboidFileSystem.instance.getString(path);
                image = javax.imageio.ImageIO.read(new java.io.File(abs));
            } catch (Exception e) {
                DebugType.General.warn("plzImage: cannot read " + path);
                image = null;
            }

            images.put(path, image);
        }

        return image;
    }

    private static java.awt.image.BufferedImage scaled(java.awt.image.BufferedImage src, int w, int h) {
        java.awt.image.BufferedImage cur = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = draw(cur, cw, ch);
        }

        return draw(cur, w, h);
    }

    private static java.awt.image.BufferedImage draw(java.awt.image.BufferedImage src, int w, int h) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static int bilinear(java.awt.image.BufferedImage img, float fx, float fy) {
        int w = img.getWidth();
        int h = img.getHeight();
        int x0 = Math.max(0, Math.min(w - 1, (int)Math.floor(fx)));
        int y0 = Math.max(0, Math.min(h - 1, (int)Math.floor(fy)));
        int x1 = Math.min(w - 1, x0 + 1);
        int y1 = Math.min(h - 1, y0 + 1);
        float ax = Math.max(0.0F, Math.min(1.0F, fx - x0));
        float ay = Math.max(0.0F, Math.min(1.0F, fy - y0));
        int[] c = { img.getRGB(x0, y0), img.getRGB(x1, y0), img.getRGB(x0, y1), img.getRGB(x1, y1) };
        float[] wt = { (1 - ax) * (1 - ay), ax * (1 - ay), (1 - ax) * ay, ax * ay };
        float a = 0.0F;
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        for (int i = 0; i < 4; i++) {
            float ca = (c[i] >>> 24) / 255.0F * wt[i];
            a += ca;
            r += (c[i] >> 16 & 255) * ca;
            g += (c[i] >> 8 & 255) * ca;
            b += (c[i] & 255) * ca;
        }

        if (a <= 0.0F) {
            return 0;
        }

        return Math.round(a * 255.0F) << 24 | Math.round(r / a) << 16 | Math.round(g / a) << 8 | Math.round(b / a);
    }

    private static java.awt.image.BufferedImage posterImage(
        java.awt.image.BufferedImage paper, KahluaTable holes, boolean north, double widthM, double heightM
    ) {
        int pw = Math.max(4, Math.round((float)widthM * PX_PER_M));
        int ph = Math.max(4, Math.round((float)heightM * PX_PER_M));
        java.awt.image.BufferedImage small = scaled(paper, pw, ph);
        java.awt.Graphics2D g = small.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(24, 18, 14, 240));
        float r = Math.max(1.1F, pw * 0.025F);
        int n = holes == null ? 0 : holes.len();
        for (int i = 1; i <= n; i++) {
            Object row = holes.rawget(i);
            if (row instanceof KahluaTable hole) {
                float u = (float)number(hole, "u", -1.0);
                float v = (float)number(hole, "v", -1.0);
                if (u >= 0.0F && u <= 1.0F && v >= 0.0F && v <= 1.0F) {
                    g.fill(new java.awt.geom.Ellipse2D.Float(u * pw - r, v * ph - r, r * 2.0F, r * 2.0F));
                }
            }
        }

        g.dispose();
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(TILE_W, TILE_H, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        float cx = north ? TILE_W * 0.75F : TILE_W * 0.25F;
        float cy = WALL_FLOOR_Y - POSTER_CENTRE_UP;
        // the wall face runs half a pixel down per pixel across, rightward on a north wall, leftward on a west one
        float slope = north ? 0.5F : -0.5F;
        for (int y = 0; y < TILE_H; y++) {
            for (int x = 0; x < TILE_W; x++) {
                float dx = x + 0.5F - cx;
                float dy = y + 0.5F - cy;
                float su = 0.5F + dx / pw;
                float sv = 0.5F + (dy - slope * dx) / ph;
                if (su >= 0.0F && su < 1.0F && sv >= 0.0F && sv < 1.0F) {
                    out.setRGB(x, y, bilinear(small, su * pw - 0.5F, sv * ph - 0.5F));
                }
            }
        }

        return out;
    }

    // Mouse.setCursorVisible only hides the cursor the game draws itself; with the cursor not locked to the window the OS draws it
    public static void hideCursor(boolean hidden) {
        try {
            if (blankCursor == null) {
                blankCursor = zombie.input.Mouse.loadCursor("cursor_blank.png");
                defaultCursor = zombie.input.Mouse.loadCursor("cursor_white.png");
            }

            org.lwjglx.input.Mouse.setNativeCursor(hidden ? blankCursor : defaultCursor);
        } catch (Exception e) {
            DebugType.General.warn("plzHideCursor: " + e);
        }
    }

    public static IsoSprite posterSprite(String key, String paperPath, KahluaTable holes, boolean north, double widthM, double heightM) {
        IsoSprite cached = posters.get(key);
        if (cached != null) {
            return cached;
        }

        java.io.File file = new java.io.File(zombie.ZomboidFileSystem.instance.getCacheDirSub("plzposters"), key + ".png");
        if (!file.exists()) {
            java.awt.image.BufferedImage paper = image(paperPath);
            if (paper == null) {
                return null;
            }

            try {
                file.getParentFile().mkdirs();
                javax.imageio.ImageIO.write(posterImage(paper, holes, north, widthM, heightM), "png", file);
            } catch (java.io.IOException e) {
                DebugType.General.warn("plzPosterSprite: cannot write " + file + " " + e);
                return null;
            }
        }

        Texture texture;
        try {
            texture = new Texture(file.getAbsolutePath());
        } catch (Exception e) {
            DebugType.General.warn("plzPosterSprite: cannot load " + file + " " + e);
            return null;
        }

        IsoSprite sprite = new IsoSprite();
        sprite.texture = texture;
        sprite.setName("plzposter_" + key);
        sprite.getProperties().set(zombie.iso.SpriteDetails.IsoFlagType.WallOverlay);
        sprite.getProperties().set(north ? zombie.iso.SpriteDetails.IsoFlagType.attachedN : zombie.iso.SpriteDetails.IsoFlagType.attachedW);
        posters.put(key, sprite);
        return sprite;
    }

    public static double sampleImage(String path, float u, float v) {
        java.awt.image.BufferedImage image = image(path);
        if (image == null || u < 0.0F || v < 0.0F || u >= 1.0F || v >= 1.0F) {
            return -1.0;
        }

        int argb = image.getRGB((int)(u * image.getWidth()), (int)(v * image.getHeight()));
        if ((argb >>> 24) < 128) {
            return -1.0;
        }

        return argb & 16777215;
    }

    public static boolean isHeadLocation(String location) {
        String name = location.toLowerCase(java.util.Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }

        return HEAD_LOCATIONS.contains(name) || name.startsWith("mask") || name.startsWith("makeup");
    }

    public static double number(KahluaTable t, String key, double fallback) {
        Object v = t == null ? null : t.rawget(key);
        return v instanceof Double d ? d : fallback;
    }

    public static Matrix4f handsCorrection(KahluaTable t, Matrix4f out) {
        out.identity();
        out.rotateXYZ(
            (float)Math.toRadians(number(t, "rx", 0.0)),
            (float)Math.toRadians(number(t, "ry", 0.0)),
            (float)Math.toRadians(number(t, "rz", 0.0))
        );
        float sx = (float)number(t, "sx", 1.0);
        float s = (float)number(t, "scale", 1.0);
        out.scale(sx * s, s, s);
        return out;
    }

    public static Matrix4f rightCorrection(KahluaTable t, Matrix4f out) {
        out.identity();
        out.rotateXYZ(
            (float)Math.toRadians(number(t, "wrx", 0.0)),
            (float)Math.toRadians(number(t, "wry", 0.0)),
            (float)Math.toRadians(number(t, "wrz", 0.0))
        );
        return out;
    }

    public static void lerp(Matrix4f from, Matrix4f to, float w, Matrix4f out) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out.setRowColumn(r, c, from.getRowColumn(r, c) + (to.getRowColumn(r, c) - from.getRowColumn(r, c)) * w);
            }
        }
    }

    public static void copyPose(AnimationPlayer from, AnimationPlayer to, float weight) {
        if (weight <= 0.0F || from == null || to == null || from.getModelTransformsCount() != to.getModelTransformsCount()) {
            return;
        }

        int count = from.getModelTransformsCount();
        for (int i = 0; i < count; i++) {
            org.lwjgl.util.vector.Matrix4f target = to.getModelTransformAt(i);
            if (weight >= 1.0F) {
                target.load(from.getModelTransformAt(i));
                continue;
            }

            poseFrom.clear();
            from.getModelTransformAt(i).store(poseFrom);
            poseTo.clear();
            target.store(poseTo);
            for (int k = 0; k < 16; k++) {
                poseTo.put(k, poseTo.get(k) + (poseFrom.get(k) - poseTo.get(k)) * weight);
            }

            poseTo.rewind();
            target.load(poseTo);
        }

        to.UpdateSkinTransforms();
    }

    private static boolean isArmBone(SkinningBone bone, String root) {
        for (SkinningBone b = bone; b != null; b = b.parent) {
            if (b.name != null && b.name.endsWith(root)) {
                return true;
            }
        }

        return false;
    }

    private static org.lwjgl.util.vector.Matrix4f joint(AnimationPlayer player, String name) {
        SkinningData skel = player.getSkinningData();
        SkinningBone bone = skel == null ? null : skel.getBone(name);
        if (bone == null || bone.index >= player.getModelTransformsCount()) {
            return null;
        }

        return player.getModelTransformAt(bone.index);
    }

    private static void collapseTo(org.lwjgl.util.vector.Matrix4f joint, org.lwjgl.util.vector.Matrix4f other) {
        FloatBuffer tmp = PLZSceneTools.tmp;
        tmp.clear();
        joint.store(tmp);
        tmp.flip();
        tmp.get(collapse);
        if (other != null) {
            tmp.clear();
            other.store(tmp);
            tmp.flip();
            for (int k = 0; k < 16; k++) {
                collapse[k] = (collapse[k] + tmp.get(k)) * 0.5F;
            }
        }

        for (int k : linear) {
            collapse[k] = 0.0F;
        }
    }

    @SuppressWarnings("unchecked")
    public static void armsOnly(AnimatedModel model, String root) {
        AnimationPlayer player = model.getAnimationPlayer();
        if (player == null) {
            return;
        }

        org.lwjgl.util.vector.Matrix4f left = joint(player, "Bip01_L" + root);
        org.lwjgl.util.vector.Matrix4f right = joint(player, "Bip01_R" + root);
        if (left == null || right == null) {
            return;
        }

        if (!resolveArmsFields()) {
            return;
        }

        try {
            AnimatedModel.StateInfo stateInfo = ((AnimatedModel.StateInfo[])stateInfosField.get(model))[SpriteRenderer.instance.getMainStateIndex()];
            armsOnly((ArrayList<AnimatedModel.AnimatedModelInstanceRenderData>)instDataField.get(stateInfo), root, left, right);
            armsOnly((ArrayList<AnimatedModel.AnimatedModelInstanceRenderData>)readyDataField.get(stateInfo), root, left, right);
        } catch (ReflectiveOperationException | ClassCastException e) {
            armsFieldsResolved = Boolean.FALSE;
            DebugType.General.warn("PLZ gun range: arms-only render unavailable, " + e);
        }
    }

    // reads AnimatedModel internals by name; a game update that renames them must hide the arms, not crash
    public static Boolean resolveArmsFields() {
        if (armsFieldsResolved != null) {
            return armsFieldsResolved;
        }

        try {
            stateInfosField = AnimatedModel.class.getDeclaredField("stateInfos");
            stateInfosField.setAccessible(true);
            instDataField = AnimatedModel.StateInfo.class.getDeclaredField("instData");
            instDataField.setAccessible(true);
            readyDataField = AnimatedModel.StateInfo.class.getDeclaredField("readyData");
            readyDataField.setAccessible(true);
            armsFieldsResolved = Boolean.TRUE;
        } catch (ReflectiveOperationException | RuntimeException e) {
            armsFieldsResolved = Boolean.FALSE;
            DebugType.General.warn("PLZ gun range: arms-only render unavailable, " + e);
        }

        return armsFieldsResolved;
    }

    private static void armsOnly(
        ArrayList<AnimatedModel.AnimatedModelInstanceRenderData> list, String root, org.lwjgl.util.vector.Matrix4f left, org.lwjgl.util.vector.Matrix4f right
    ) {
        for (int i = 0; i < list.size(); i++) {
            AnimatedModel.AnimatedModelInstanceRenderData data = list.get(i);
            FloatBuffer palette = data.matrixPalette;
            if (palette == null || data.modelInstance == null || data.modelInstance.model == null) {
                continue;
            }

            SkinningData skin = (SkinningData)data.modelInstance.model.tag;
            if (skin == null) {
                continue;
            }

            zombie.core.rendering.ShaderParameter param = data.properties.GetParameter("MatrixPalette");
            org.lwjgl.util.vector.Matrix4f[] bones = param == null ? null : param.GetMatrix4Array();
            int count = Math.min(palette.limit() / 16, skin.numBones());
            for (int b = 0; b < count; b++) {
                SkinningBone bone = skin.getBoneAt(b);
                if (bone == null || isArmBone(bone, root)) {
                    continue;
                }

                if (bone.name != null && bone.name.contains("_L_")) {
                    collapseTo(left, null);
                } else if (bone.name != null && bone.name.contains("_R_")) {
                    collapseTo(right, null);
                } else {
                    collapseTo(left, right);
                }

                for (int k = 0; k < 16; k++) {
                    palette.put(b * 16 + k, collapse[k]);
                }

                if (bones != null && b < bones.length && bones[b] != null) {
                    palette.position(b * 16);
                    bones[b].load(palette);
                    palette.position(0);
                }
            }
        }
    }

    public static void muzzleFlash(IsoGameCharacter character) {
        if (character != null) {
            zombie.EffectsManager.getInstance().startMuzzleFlash(character, 1);
        }
    }

    public static float[] pointsOf(ModelScript modelScript, Model model) {
        String key = modelScript.getName();
        if (meshPoints.containsKey(key)) {
            return meshPoints.get(key);
        }

        float[] points = null;
        zombie.core.skinnedmodel.model.ModelMesh mesh = model.mesh;
        zombie.core.skinnedmodel.model.ModelMesh.MeshAssetParams saved = mesh.assetParams;
        zombie.core.skinnedmodel.model.ModelMesh.MeshAssetParams params = new zombie.core.skinnedmodel.model.ModelMesh.MeshAssetParams();
        params.isStatic = true;
        params.postProcess = mesh.postProcess;
        mesh.assetParams = params;
        try {
            Object scene = new zombie.core.skinnedmodel.model.FileTask_LoadMesh(mesh, zombie.GameWindow.fileSystem, null).call();
            if (scene instanceof zombie.core.skinnedmodel.model.jassimp.ProcessedAiScene processed) {
                zombie.core.physics.PhysicsShape shape = new zombie.core.physics.PhysicsShape(null, null, null);
                processed.applyToPhysicsShape(shape);
                if (!shape.meshes.isEmpty()) {
                    points = shape.meshes.get(0).points;
                }
            }
        } catch (Exception e) {
            DebugType.General.warn("plzMeasureSights: cannot re-import " + key + ": " + e);
        } finally {
            mesh.assetParams = saved;
        }

        meshPoints.put(key, points);
        return points;
    }
}

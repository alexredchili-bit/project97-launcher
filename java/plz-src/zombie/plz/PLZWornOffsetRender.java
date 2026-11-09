package zombie.plz;

import java.util.HashMap;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.core.skinnedmodel.visual.ItemVisual;
import zombie.debug.DebugLog;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.Item;
import zombie.scripting.objects.ItemBodyLocation;
import zombie.util.Type;

public final class PLZWornOffsetRender {
    // Diag de-dup is keyed on model name or worn location, never on the message: message keys leaked per frame.
    private static final int DIAG_MODELS_MAX = 256;

    private static final int DIAG_STATIC_NO_LOC = 0;
    private static final int DIAG_STATIC_MODEL = 1;
    private static final int DIAG_NULL_ANIM = 2;
    private static final int DIAG_NOT_PLAYER = 3;
    private static final int DIAG_VISUAL = 4;
    private static final int DIAG_NO_LOC = 5;
    private static final int DIAG_NO_DELTA = 6;

    private static final int DIAG_LOC_STATIC_APPLIED = 0;
    private static final int DIAG_LOC_NO_OFFSET = 1;
    private static final int DIAG_LOC_APPLIED = 2;

    private static final HashMap<String, Integer> DIAG_MODELS = new HashMap<>();
    private static final long[] DIAG_LOC_SEEN = new long[3];
    private static boolean diagNoInstanceSeen;

    // Per render data, not static: nothing promises one thread, and shared scratch corrupts under load.
    private final float[] wornOffset = new float[3];
    private final float[] wornDelta = new float[3];
    private final float[] wornScale = new float[3];
    private String wornItemType;
    private int wornLocation = -1;

    private static boolean diagModel(int site, String model) {
        if (model == null) {
            return false;
        }
        Integer seen = DIAG_MODELS.get(model);
        int bit = 1 << site;
        if (seen != null) {
            int mask = seen.intValue();
            if ((mask & bit) != 0) {
                return false;
            }
            DIAG_MODELS.put(model, Integer.valueOf(mask | bit));
            return true;
        }
        if (DIAG_MODELS.size() >= DIAG_MODELS_MAX) {
            return false;
        }
        DIAG_MODELS.put(model, Integer.valueOf(bit));
        return true;
    }

    private static boolean diagLoc(int site, int location) {
        if (location < 0 || location >= 64) {
            return false;
        }
        long bit = 1L << location;
        if ((DIAG_LOC_SEEN[site] & bit) != 0) {
            return false;
        }
        DIAG_LOC_SEEN[site] |= bit;
        return true;
    }

    private static void diag(String msg) {
        DebugLog.log("PLZWornDiag: " + msg);
    }

    // Static worn items are drawn from xfrm, not the skin palette, so they are nudged after transformToParent built it.
    public void applyStatic(AnimatedModel.AnimatedModelInstanceRenderData data, AnimatedModel.AnimatedModelInstanceRenderData parentData) {
        if (!PLZBoneScale.isActive() || parentData == null) {
            return;
        }

        ModelInstance instance = data.modelInstance;
        if (instance == null || instance.model == null || !instance.model.isStatic) {
            return;
        }

        AnimationPlayer animPlayer = parentData.modelInstance == null ? null : parentData.modelInstance.animPlayer;
        if (animPlayer == null) {
            return;
        }

        IsoPlayer player = Type.tryCastTo(animPlayer.getIsoGameCharacter(), IsoPlayer.class);
        if (player == null) {
            return;
        }

        int location = this.resolveWornLocation(instance);
        if (location < 0) {
            if (diagModel(DIAG_STATIC_NO_LOC, instance.model.name)) {
                diag("STATIC no worn location: " + instance.model.name
                    + " visual=" + (instance.getItemVisual() == null ? "NULL" : instance.getItemVisual().getItemType()));
            }
            return;
        }

        String username = player.getUsername();
        boolean hasOffset = PLZBoneScale.wornOffset(username, location, this.wornOffset);
        boolean hasScale = PLZBoneScale.wornScale(username, location, this.wornScale);
        if (!hasOffset && !hasScale) {
            return;
        }

        boolean haveDelta = hasOffset && this.headSpaceDelta(animPlayer, this.wornOffset, this.wornDelta);

        // xfrm is stored transposed; edit it in standard form and transpose back.
        data.xfrm.transpose();

        // Scale first: joml post-multiplies, so the mesh grows in place and the head attach point stays put.
        if (hasScale) {
            data.xfrm.scale(this.wornScale[0], this.wornScale[1], this.wornScale[2]);
        }

        if (haveDelta) {
            data.xfrm.setTranslation(
                data.xfrm.m30() + this.wornDelta[0],
                data.xfrm.m31() + this.wornDelta[1],
                data.xfrm.m32() + this.wornDelta[2]);
        }

        data.xfrm.transpose();

        if (diagLoc(DIAG_LOC_STATIC_APPLIED, location)) {
            diag("STATIC APPLIED loc=" + PLZBoneScale.locationAt(location)
                + " delta=" + this.wornDelta[0] + "," + this.wornDelta[1] + "," + this.wornDelta[2]
                + " scale=" + this.wornScale[0] + "," + this.wornScale[1] + "," + this.wornScale[2]
                + " (first sighting of this location; the figures are that frame's)");
        }
    }

    // Each worn model has its own palette copy, so displacing it moves this item only, still following the skeleton.
    public void applySkinned(AnimatedModel.AnimatedModelInstanceRenderData data) {
        if (!PLZBoneScale.isActive() || data.matrixPalette == null) {
            return;
        }

        ModelInstance instance = data.modelInstance;
        if (instance == null || instance.model == null) {
            if (!diagNoInstanceSeen) {
                diagNoInstanceSeen = true;
                diag("no model instance");
            }
            return;
        }
        if (instance.model.isStatic) {
            if (diagModel(DIAG_STATIC_MODEL, instance.model.name)) {
                diag("static model: " + instance.model.name);
            }
            return;
        }

        AnimationPlayer animPlayer = instance.animPlayer;
        if (animPlayer == null) {
            if (diagModel(DIAG_NULL_ANIM, instance.model.name)) {
                diag("null animPlayer for model: " + instance.model.name);
            }
            return;
        }

        IsoPlayer player = Type.tryCastTo(animPlayer.getIsoGameCharacter(), IsoPlayer.class);
        if (player == null) {
            if (diagModel(DIAG_NOT_PLAYER, instance.model.name)) {
                diag("not an IsoPlayer for model: " + instance.model.name);
            }
            return;
        }

        ItemVisual vis = instance.getItemVisual();
        if (diagModel(DIAG_VISUAL, instance.model.name)) {
            diag("model=" + instance.model.name + " visual=" + (vis == null ? "NULL" : vis.getItemType()));
        }

        int location = this.resolveWornLocation(instance);
        if (location < 0) {
            if (diagModel(DIAG_NO_LOC, instance.model.name)) {
                diag("no worn location for model: " + instance.model.name
                    + " visual=" + (vis == null ? "NULL" : vis.getItemType()));
            }
            return;
        }

        if (!PLZBoneScale.wornOffset(player.getUsername(), location, this.wornOffset)) {
            if (diagLoc(DIAG_LOC_NO_OFFSET, location)) {
                diag("no offset in rig at loc=" + PLZBoneScale.locationAt(location)
                    + " (first sighting, for '" + player.getUsername() + "'; later players are silent)");
            }
            return;
        }

        if (!this.headSpaceDelta(animPlayer, this.wornOffset, this.wornDelta)) {
            if (diagModel(DIAG_NO_DELTA, instance.model.name)) {
                diag("no head-space delta (missing head bone) for model: " + instance.model.name);
            }
            return;
        }

        if (diagLoc(DIAG_LOC_APPLIED, location)) {
            diag("APPLIED loc=" + PLZBoneScale.locationAt(location)
                + " off=" + this.wornOffset[0] + "," + this.wornOffset[1] + "," + this.wornOffset[2]
                + " delta=" + this.wornDelta[0] + "," + this.wornDelta[1] + "," + this.wornDelta[2]
                + " (first sighting of this location; the figures are that frame's)");
        }

        float deltaX = this.wornDelta[0];
        float deltaY = this.wornDelta[1];
        float deltaZ = this.wornDelta[2];

        int numBones = data.matrixPalette.limit() / 64;

        // Row-vector matrices keep their translation at offsets 3, 7 and 11 of each block.
        for (int bone = 0; bone < numBones; bone++) {
            int base = bone * 16;
            data.matrixPalette.put(base + 3, data.matrixPalette.get(base + 3) + deltaX);
            data.matrixPalette.put(base + 7, data.matrixPalette.get(base + 7) + deltaY);
            data.matrixPalette.put(base + 11, data.matrixPalette.get(base + 11) + deltaZ);
        }
    }

    // Row-vector convention: the head's local axes are the ROWS; reading columns swings the wrong way on a tilt.
    private boolean headSpaceDelta(AnimationPlayer animPlayer, float[] offset, float[] delta) {
        SkinningData skinningData = animPlayer.getSkinningData();
        if (skinningData == null) {
            return false;
        }

        SkinningBone head = skinningData.getBone(PLZBoneScale.WORN_OFFSET_BONE);
        if (head == null) {
            return false;
        }

        org.lwjgl.util.vector.Matrix4f m = animPlayer.getModelTransformAt(head.index);
        if (m == null) {
            return false;
        }

        delta[0] = 0.0F;
        delta[1] = 0.0F;
        delta[2] = 0.0F;

        addAxis(m.m00, m.m10, m.m20, offset[0], delta);
        addAxis(m.m01, m.m11, m.m21, offset[1], delta);
        addAxis(m.m02, m.m12, m.m22, offset[2], delta);
        return true;
    }

    // Normalised, so a scaled skeleton does not scale the slider distance with it.
    private static void addAxis(float x, float y, float z, float amount, float[] delta) {
        if (amount == 0.0F) {
            return;
        }

        float length = (float)Math.sqrt(x * x + y * y + z * z);
        if (length <= 1.0E-6F) {
            return;
        }

        float scaled = amount / length;
        delta[0] += x * scaled;
        delta[1] += y * scaled;
        delta[2] += z * scaled;
    }

    // Memoised on item type: a script lookup per worn model per character per frame is too dear.
    private int resolveWornLocation(ModelInstance instance) {
        ItemVisual visual = instance.getItemVisual();
        if (visual == null) {
            return -1;
        }

        String itemType = visual.getItemType();
        if (itemType == null || itemType.isEmpty()) {
            return -1;
        }

        if (!itemType.equals(this.wornItemType)) {
            this.wornItemType = itemType;
            this.wornLocation = -1;

            Item script = ScriptManager.instance.FindItem(itemType);
            ItemBodyLocation bodyLocation = script == null ? null : script.getBodyLocation();
            if (bodyLocation != null) {
                this.wornLocation = PLZBoneScale.locationIndex(bodyLocation.getTranslationName());
            }
        }

        return this.wornLocation;
    }
}

package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.math.PZMath;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.skinnedmodel.model.Model;
import zombie.plz.access.PLZSceneVehicleAccess;
import zombie.popman.ObjectPool;
import zombie.scripting.objects.VehicleScript;

@Mixin(targets = "zombie.vehicles.UI3DScene$SceneVehicle")
abstract class UI3DScene_SceneVehicleMixin implements PLZSceneVehicleAccess {
    // SceneVehicleModelInfo is private to UI3DScene; its pool is reached through the nest's own lookup.
    @Unique
    private static final VarHandle plz$MODEL_INFO_POOL = plz$modelInfoPool();

    @Shadow
    VehicleScript script;

    @Shadow
    @Final
    ArrayList<Object> modelInfo;

    @Shadow
    boolean init;

    @Unique
    private final Vector3f plz$paint = new Vector3f(0.0F, 0.5F, 0.5F);

    @Unique
    private int plz$skinIndex;

    // Which parts have something fitted, part id -> the item's full type, or null for "show what the script has".
    @Unique
    private HashMap<String, String> plz$visibleParts;

    @Unique
    private VehicleScript.Model plz$chosenModel;

    @Unique
    private static VarHandle plz$modelInfoPool() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> modelInfo = Class.forName("zombie.vehicles.UI3DScene$SceneVehicleModelInfo", false, lookup.lookupClass().getClassLoader());
            return lookup.unreflectVarHandle(modelInfo.getDeclaredField("s_pool"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // A body model that is not loaded yet leaves init false so the next frame retries, rather than drawing nothing forever.
    @WrapOperation(
        method = "renderMain",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/skinnedmodel/ModelManager;getLoadedModel(Ljava/lang/String;)Lzombie/core/skinnedmodel/model/Model;",
            ordinal = 0
        )
    )
    private Model plz$retryUnloadedBody(ModelManager manager, String modelName, Operation<Model> original) {
        Model model = original.call(manager, modelName);
        if (model == null) {
            this.init = false;
        }

        return model;
    }

    @ModifyArg(
        method = "renderMain",
        at = @At(value = "INVOKE", target = "Lzombie/scripting/objects/VehicleScript;getSkin(I)Lzombie/scripting/objects/VehicleScript$Skin;", ordinal = 0)
    )
    private int plz$bodySkin(int index) {
        return this.plzClampedSkinIndex();
    }

    // A real vehicle shows at most one model per part and none for a part with nothing fitted; the fitted variant is tried first.
    @WrapOperation(
        method = "renderMain",
        at = @At(value = "FIELD", target = "Lzombie/scripting/objects/VehicleScript$Part;wheel:Ljava/lang/String;", opcode = 180)
    )
    private String plz$choosePartModel(VehicleScript.Part scriptPart, Operation<String> original) {
        String wheel = original.call(scriptPart);
        this.plz$chosenModel = null;
        if (wheel != null) {
            return wheel;
        }

        String fittedItem = null;
        if (this.plz$visibleParts != null) {
            fittedItem = this.plz$visibleParts.get(scriptPart.id);
            if (fittedItem == null) {
                return wheel;
            }
        }

        int wanted = plz$modelIndexFor(scriptPart, fittedItem);
        if (plz$isLoadable(scriptPart, wanted)) {
            this.plz$chosenModel = scriptPart.getModel(wanted);
        } else {
            for (int j = 0; j < scriptPart.getModelCount(); j++) {
                if (j != wanted && plz$isLoadable(scriptPart, j)) {
                    this.plz$chosenModel = scriptPart.getModel(j);
                    break;
                }
            }
        }

        return wheel;
    }

    @WrapOperation(
        method = "renderMain",
        at = @At(value = "FIELD", target = "Lzombie/scripting/objects/VehicleScript$Model;file:Ljava/lang/String;", opcode = 180, ordinal = 2)
    )
    private String plz$onlyChosenModel(VehicleScript.Model scriptModel, Operation<String> original) {
        return scriptModel == this.plz$chosenModel ? original.call(scriptModel) : null;
    }

    @Unique
    private static boolean plz$isLoadable(VehicleScript.Part scriptPart, int index) {
        if (index < 0 || index >= scriptPart.getModelCount()) {
            return false;
        }

        VehicleScript.Model scriptModel = scriptPart.getModel(index);
        return scriptModel.file != null && ModelManager.instance.getLoadedModel(scriptModel.file) != null;
    }

    // Variant scripts list items and models in step (R32Spoiler0;R32Spoiler1 against Spoiler0, Spoiler1), so an item's position is its model's index.
    @Unique
    private static int plz$modelIndexFor(VehicleScript.Part scriptPart, String fittedItem) {
        if (fittedItem == null || fittedItem.isEmpty() || scriptPart.itemType == null) {
            return 0;
        }

        for (int i = 0; i < scriptPart.itemType.size(); i++) {
            if (fittedItem.equals(scriptPart.itemType.get(i))) {
                return i < scriptPart.getModelCount() ? i : 0;
            }
        }

        return 0;
    }

    // A part-id list belongs to one script, so a new car arrives with no filter rather than the last car's.
    @Inject(method = "setScriptName", at = @At("TAIL"))
    private void plz$dropPartFilter(String scriptName, CallbackInfo ci) {
        this.plz$visibleParts = null;
    }

    @Override
    public VehicleScript plzScript() {
        return this.script;
    }

    @Override
    public Vector3f plzPaint() {
        return this.plz$paint;
    }

    @Override
    public int plzSkinIndex() {
        return this.plz$skinIndex;
    }

    @Override
    public void plzSetSkinIndex(int skinIndex) {
        this.plz$skinIndex = skinIndex;
    }

    @Override
    public int plzClampedSkinIndex() {
        if (this.script == null || this.script.getSkinCount() <= 0) {
            return 0;
        }
        return PZMath.clamp(this.plz$skinIndex, 0, this.script.getSkinCount() - 1);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void plzResetModels() {
        ((ObjectPool)plz$MODEL_INFO_POOL.get()).releaseAll((List)this.modelInfo);
        this.modelInfo.clear();
        this.init = false;
    }

    // "partId=Base.SomeItem" pairs joined by semicolons, or null for no filter; a bare "partId" means fitted with something unknown.
    @Override
    public void plzSetVisibleParts(String spec) {
        HashMap<String, String> parsed = null;
        if (spec != null && !spec.isEmpty()) {
            parsed = new HashMap<>();

            for (String entry : spec.split(";")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    int eq = trimmed.indexOf(61);
                    if (eq < 0) {
                        parsed.put(trimmed, "");
                    } else {
                        parsed.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
                    }
                }
            }

            if (parsed.isEmpty()) {
                parsed = null;
            }
        }

        // The caller pushes this on every open, and a rebuild drops every cached model, so only a changed answer rebuilds.
        if (!Objects.equals(parsed, this.plz$visibleParts)) {
            this.plz$visibleParts = parsed;
            this.plzResetModels();
        }
    }
}

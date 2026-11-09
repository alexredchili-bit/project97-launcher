package plz.mixin.client;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.GameWindow;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.EditVehicleState;

@Mixin(VehicleScript.class)
abstract class VehicleScriptMixin {
    @Shadow
    @Final
    private ArrayList<VehicleScript.Part> parts;

    @Shadow
    public abstract VehicleScript.Part getPartById(String id);

    @Shadow
    public abstract String getFullName();

    public boolean plzAddPartModel(
        String partId,
        String modelId,
        String modelFile,
        float ox,
        float oy,
        float oz,
        float rx,
        float ry,
        float rz,
        float scale
    ) {
        VehicleScript.Part part = this.getPartById(partId);
        if (part == null) {
            return false;
        }

        if (part.itemType != null && !part.itemType.isEmpty()) {
            return false;
        }

        if (part.models == null) {
            part.models = new ArrayList<>();
        }

        VehicleScript.Model model = part.getModelById(modelId);
        if (model == null) {
            model = new VehicleScript.Model();
            model.id = modelId;
            part.models.add(model);
        }

        model.file = modelFile;
        model.offset.set(ox, oy, oz);
        model.rotate.set(rx, ry, rz);
        model.scale = scale;
        model.attachmentNameParent = null;
        model.attachmentNameSelf = null;
        return true;
    }

    public boolean plzSetPartModelTransform(
        String partId,
        String modelId,
        float ox,
        float oy,
        float oz,
        float rx,
        float ry,
        float rz,
        float scale
    ) {
        VehicleScript.Part part = this.getPartById(partId);
        if (part == null) {
            return false;
        }

        VehicleScript.Model model = part.getModelById(modelId);
        if (model == null) {
            return false;
        }

        model.offset.set(ox, oy, oz);
        model.rotate.set(rx, ry, rz);
        model.scale = scale;
        return true;
    }

    public String plzFindHostPartId() {
        for (int i = 0; i < this.parts.size(); i++) {
            VehicleScript.Part part = this.parts.get(i);
            if (part.id != null && (part.itemType == null || part.itemType.isEmpty())) {
                return part.id;
            }
        }

        return null;
    }

    public boolean plzOpenInVehicleEditor() {
        EditVehicleState state = EditVehicleState.checkInstance();
        if (state == null || GameWindow.states == null) {
            return false;
        }

        state.setScript(this.getFullName());
        GameWindow.states.forceNextState(state);
        return true;
    }

    public String plzDescribeParts() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.parts.size(); i++) {
            VehicleScript.Part part = this.parts.get(i);
            sb.append(part.id);
            sb.append(part.itemType == null || part.itemType.isEmpty() ? "(noitem)" : "(item)");
            if (part.models != null && !part.models.isEmpty()) {
                sb.append("[m").append(part.models.size()).append("]");
            }

            sb.append(" ");
        }

        return sb.toString();
    }
}

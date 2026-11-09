package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plz.agent.LuaPublic;
import zombie.GameTime;
import zombie.UsedFromLua;
import zombie.core.Translator;
import zombie.debug.DebugType;
import zombie.entity.components.crafting.CraftLogic;
import zombie.entity.components.crafting.DryingCraftLogic;
import zombie.entity.components.crafting.recipe.CraftRecipeData;
import zombie.entity.components.resources.Resource;
import zombie.ui.ObjectTooltip.Layout;
import zombie.ui.ObjectTooltip.LayoutItem;

@Mixin(DryingCraftLogic.class)
abstract class DryingCraftLogicMixin extends CraftLogic {
    @Shadow
    @Final
    private HashMap<CraftRecipeData, Double> previousTickElapsedTimes;

    @Shadow
    @Final
    private HashMap<CraftRecipeData, Double> temporaryWetnesses;

    DryingCraftLogicMixin() {
        super(null);
    }

    // Unboxing a missing key threw out of onUpdate past every catch, bricking the station; elapsed defaults to the craft's own, never 0.
    @WrapOperation(method = "onUpdate", at = @At(value = "INVOKE", target = "Ljava/util/HashMap;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object plz$noMissingKeyUnbox(
        HashMap<?, ?> map,
        Object key,
        Operation<Object> original,
        @Local(argsOnly = true) CraftRecipeData craftRecipeData
    ) {
        Object stored = original.call(map, key);
        if (stored != null) {
            return stored;
        }

        if (map == this.temporaryWetnesses) {
            return 0.0;
        }

        if (map == this.previousTickElapsedTimes) {
            return craftRecipeData.getElapsedTime();
        }

        return null;
    }

    // Vanilla never dropped these, so both grew per batch for the life of the process, each pinning a pooled CraftRecipeData.
    @Override
    public void finaliseRecipe(CraftRecipeData craftRecipeData) {
        this.temporaryWetnesses.remove(craftRecipeData);
        this.previousTickElapsedTimes.remove(craftRecipeData);
        super.finaliseRecipe(craftRecipeData);
    }

    @Unique
    @LuaPublic
    @UsedFromLua
    private static boolean plzIsPatched() {
        return true;
    }

    @WrapOperation(
        method = "doProgressTooltip",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/entity/components/crafting/CraftLogic;doProgressTooltip(Lzombie/ui/ObjectTooltip$Layout;Lzombie/entity/components/resources/Resource;Lzombie/entity/components/crafting/recipe/CraftRecipeData;)V"
        )
    )
    private void plz$gameTimeProgress(
        DryingCraftLogic self,
        Layout layout,
        Resource resource,
        CraftRecipeData craftRecipeData,
        Operation<Void> original
    ) {
        if (this.isRunning() && craftRecipeData != null) {
            this.plz$doGameTimeProgress(layout, craftRecipeData);
        } else {
            original.call(self, layout, resource, craftRecipeData);
        }
    }

    // Recipe time is counted in one-hour-day seconds whatever DayLength is, so vanilla printed a 3 real hour dry as 3 days.
    @Unique
    private void plz$doGameTimeProgress(Layout layout, CraftRecipeData craftRecipeData) {
        LayoutItem item = layout.addItem();
        item.setLabel(Translator.getText("EC_CraftLogicTooltip_Progress") + ":", 1.0F, 1.0F, 1.0F, 1.0F);
        int progress = (int)(this.getProgress(craftRecipeData) * 100.0);
        item.setValue(String.format(Locale.ENGLISH, "%d%%", progress), 1.0F, 1.0F, 0.8F, 1.0F);
        item = layout.addItem();
        item.setLabel(Translator.getText("EC_CraftLogicTooltip_TimeRemaining") + ":", 1.0F, 1.0F, 1.0F, 1.0F);
        double remaining = craftRecipeData.getRecipe().getTime() - craftRecipeData.getElapsedTime();
        int timeRemaining = (int)(remaining * 60.0 / GameTime.getInstance().getMinutesPerDay());
        int ss = timeRemaining % 60;
        int mm = timeRemaining / 60 % 60;
        int hh = timeRemaining / 3600 % 24;
        int dd = Math.floorDiv(timeRemaining, 86400);
        item.setValue(String.format(Locale.ENGLISH, "%02dd %02dh %02dm %02ds", dd, hh, mm, ss), 1.0F, 1.0F, 0.8F, 1.0F);
    }

    // Reads exactly what save() wrote whatever the counts: sync packs components with no length prefix, so a short read corrupts the rest.
    @Inject(
        method = "load",
        at = @At(value = "INVOKE", target = "Lzombie/entity/components/crafting/CraftLogic;load(Ljava/nio/ByteBuffer;I)V", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void plz$loadEveryWetness(ByteBuffer input, int worldVersion, CallbackInfo ci) {
        int wetnessCount = input.getInt();
        ArrayList<CraftRecipeData> inProgress = ((CraftLogicAccessorMixin) (Object) this).plz$getAllInProgressCraftData();
        if (wetnessCount != inProgress.size()) {
            DebugType.CraftLogic
                .error(
                    "DryingCraftLogic:load() - saved wetness count "
                        + wetnessCount
                        + " does not match "
                        + inProgress.size()
                        + " in progress recipes, defaulting the remainder to zero."
                );
        }

        // Survivors are always the leading crafts, so the leading values still line up.
        for (int i = 0; i < wetnessCount; i++) {
            double wetness = input.getDouble();
            if (i < inProgress.size()) {
                CraftRecipeData key = inProgress.get(i);
                this.temporaryWetnesses.put(key, wetness);
                this.previousTickElapsedTimes.put(key, key.getElapsedTime());
            }
        }

        for (int i = wetnessCount; i < inProgress.size(); i++) {
            CraftRecipeData key = inProgress.get(i);
            this.temporaryWetnesses.put(key, 0.0);
            this.previousTickElapsedTimes.put(key, key.getElapsedTime());
        }

        ci.cancel();
    }
}

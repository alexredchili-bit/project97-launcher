package plz.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.GameTime;
import zombie.SandboxOptions;
import zombie.characters.BodyDamage.Nutrition;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;
import zombie.plz.PLZBodyWeight;

@Mixin(Nutrition.class)
abstract class NutritionMixin {
    @Shadow
    @Final
    private IsoPlayer parent;

    @Shadow
    private int updatedWeight;

    @Shadow
    public abstract double getWeight();

    @Shadow
    public abstract void setWeight(double weight);

    @Shadow
    public abstract void setIncWeight(boolean incWeight);

    @Shadow
    public abstract void setIncWeightLot(boolean incWeightLot);

    @Shadow
    public abstract void setDecWeight(boolean decWeight);

    @Shadow
    public abstract void applyTraitFromWeight();

    // The sandbox flag gates only the engine's macro and calorie simulation; weight runs either way because PLZ_Diet feeds it.
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void plz$weightWithoutNutrition(CallbackInfo ci) {
        if (!SandboxOptions.instance.nutrition.getValue()) {
            if (this.parent != null && !this.parent.isDead() && !this.parent.isGodMod()) {
                this.updateWeight();
            }

            ci.cancel();
        }
    }

    public double getPlzWeightTarget() {
        return PLZBodyWeight.target(this.parent);
    }

    /**
     * @author PLZ
     * @reason Weight drifts toward the PLZ_Diet target inside a 70-80kg band instead of following calories; every line differs.
     */
    @Overwrite
    private void updateWeight() {
        this.setIncWeight(false);
        this.setIncWeightLot(false);
        this.setDecWeight(false);
        if (!GameClient.client) {
            double target = this.getPlzWeightTarget();
            double current = this.getWeight();
            double step = PLZBodyWeight.WEIGHT_RATE * GameTime.getInstance().getGameWorldSecondsSinceLastUpdate();
            if (current < target) {
                this.setWeight(Math.min(target, current + step));
                this.setIncWeight(true);
            } else if (current > target) {
                this.setWeight(Math.max(target, current - step));
                this.setDecWeight(true);
            }

            this.updatedWeight++;
            if (this.updatedWeight >= PLZBodyWeight.TRAIT_INTERVAL) {
                this.applyTraitFromWeight();
                this.updatedWeight = 0;
            }
        }
    }

    // Removals still strip legacy traits; the adds are gone because Underweight and friends carry real penalties.
    @Inject(
        method = "applyTraitFromWeight",
        at = @At(value = "INVOKE", target = "Lzombie/characters/BodyDamage/Nutrition;getWeight()D", ordinal = 0),
        cancellable = true
    )
    private void plz$noWeightTraitAdds(CallbackInfo ci) {
        ci.cancel();
    }

    // Every weight write goes through setWeight, so clamping here contains the whole band, including old saves on load.
    @ModifyVariable(method = "setWeight", at = @At("HEAD"), argsOnly = true)
    private double plz$clampWeight(double weight) {
        return PLZBodyWeight.clamp(weight);
    }
}

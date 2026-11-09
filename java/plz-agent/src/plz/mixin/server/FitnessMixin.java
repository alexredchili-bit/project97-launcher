package plz.mixin.server;

import java.util.HashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.BodyDamage.Fitness;
import zombie.debug.DebugLog;
import zombie.plz.PLZFixes;

@Mixin(Fitness.class)
abstract class FitnessMixin {
    @Shadow
    @Final
    private HashMap<String, Fitness.FitnessExercise> exercises;

    @Shadow
    private Fitness.FitnessExercise currentExe;

    @Inject(method = "setCurrentExercise", at = @At("TAIL"))
    private void plz$reportUnknownExercise(String type, CallbackInfo ci) {
        if (this.currentExe == null && PLZFixes.on(PLZFixes.FITNESS_CURRENT_EXERCISE)) {
            PLZFixes.hit(PLZFixes.FITNESS_CURRENT_EXERCISE);
            DebugLog.log("PLZFixes.fitnessCurrentExercise: no exercise named '" + type + "' (" + this.exercises.size() + " known), currentExe left null");
        }
    }

    // All five calls below dereference currentExe, so this is the one guard that covers them.
    @Inject(method = "exerciseRepeat", at = @At("HEAD"), cancellable = true)
    private void plz$skipUnknownExercise(CallbackInfo ci) {
        if (this.currentExe == null && PLZFixes.on(PLZFixes.FITNESS_CURRENT_EXERCISE)) {
            PLZFixes.hit(PLZFixes.FITNESS_CURRENT_EXERCISE);
            ci.cancel();
        }
    }
}

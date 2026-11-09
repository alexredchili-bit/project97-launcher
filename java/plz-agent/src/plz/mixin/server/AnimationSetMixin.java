package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import zombie.core.skinnedmodel.advancedanimation.AnimationSet;
import zombie.plz.PLZFixes;

@Mixin(AnimationSet.class)
abstract class AnimationSetMixin {
    // SPVThread loads animal vehicles through here while the main thread does too; the race corrupted THashMap and deleted vehicles.
    @Unique
    private static final Object plz$SET_MAP_LOCK = new Object();

    @WrapMethod(method = "GetAnimationSet")
    private static AnimationSet plz$lockGet(String name, boolean reload, Operation<AnimationSet> original) {
        if (PLZFixes.on(PLZFixes.ANIM_SET_LOCK)) {
            synchronized (plz$SET_MAP_LOCK) {
                PLZFixes.hit(PLZFixes.ANIM_SET_LOCK);
                return original.call(name, reload);
            }
        }

        return original.call(name, reload);
    }

    @WrapMethod(method = "Reset")
    private static void plz$lockReset(Operation<Void> original) {
        if (PLZFixes.on(PLZFixes.ANIM_SET_LOCK)) {
            synchronized (plz$SET_MAP_LOCK) {
                original.call();
                return;
            }
        }

        original.call();
    }
}

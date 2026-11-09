package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.plz.PLZSceneTools;
import zombie.plz.access.PLZSceneCharacterAccess;

@Mixin(targets = "zombie.vehicles.UI3DScene$CharacterDrawer")
abstract class UI3DScene_CharacterDrawerMixin {
    // character is typed as the private SceneCharacter, which no mixin source can name.
    @Unique
    private static final VarHandle plz$CHARACTER = plz$characterHandle();

    @Unique
    private static VarHandle plz$characterHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return lookup.unreflectVarHandle(lookup.lookupClass().getDeclaredField("character"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @WrapOperation(
        method = "init",
        at = @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/advancedanimation/AnimatedModel;renderMain()I")
    )
    private int plz$armsOnly(AnimatedModel animatedModel, Operation<Integer> original) {
        int result = original.call(animatedModel);
        String root = ((PLZSceneCharacterAccess)plz$CHARACTER.get((Object)this)).plzArmsRoot();
        if (root != null) {
            PLZSceneTools.armsOnly(animatedModel, root);
        }

        return result;
    }
}

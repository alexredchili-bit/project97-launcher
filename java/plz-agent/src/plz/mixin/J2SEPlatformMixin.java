package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.vm.KahluaTable;
import zombie.plz.PLZLuaLoad;

@Mixin(J2SEPlatform.class)
abstract class J2SEPlatformMixin {
    @WrapOperation(
        method = "setupEnvironment",
        at = @At(value = "INVOKE", target = "Lse/krka/kahlua/luaj/compiler/LuaCompiler;register(Lse/krka/kahlua/vm/KahluaTable;)V")
    )
    private void plz$registerLoadFunctions(KahluaTable env, Operation<Void> original) {
        PLZLuaLoad.register(env);
    }
}

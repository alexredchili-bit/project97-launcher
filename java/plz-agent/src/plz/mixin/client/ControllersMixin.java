package plz.mixin.client;

import org.lwjglx.input.Controllers;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.plz.PLZControllerMappings;

@Mixin(Controllers.class)
abstract class ControllersMixin {
    @Inject(
        method = "readGameControllerDB()V",
        at = @At(value = "FIELD", target = "Lzombie/ZomboidFileSystem;instance:Lzombie/ZomboidFileSystem;", opcode = Opcodes.GETSTATIC)
    )
    private static void plz$applyGuitarMappings(CallbackInfo ci) {
        PLZControllerMappings.apply();
    }
}

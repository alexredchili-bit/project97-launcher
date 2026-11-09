package plz.mixin;

import java.nio.ByteBuffer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.animals.IsoAnimal;
import zombie.iso.objects.IsoHutch;
import zombie.network.GameClient;
import zombie.plz.PLZFixes;

@Mixin(IsoHutch.NestBox.class)
abstract class IsoHutch_NestBoxMixin {
    @Shadow
    public IsoAnimal animal;

    // javac drops NestBox's outer reference because vanilla never reads it.
    @Unique
    private IsoHutch plz$hutch;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$keepHutch(IsoHutch hutch, int index, CallbackInfo ci) {
        this.plz$hutch = hutch;
    }

    // The perch loader does both of these for its birds, this one did neither.
    @Inject(
        method = "load",
        at = @At(value = "FIELD", target = "Lzombie/characters/animals/IsoAnimal;nestBox:I", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER)
    )
    private void plz$seatReloadedBird(ByteBuffer input, int worldVersion, CallbackInfo ci) {
        if (!GameClient.client && PLZFixes.on(PLZFixes.HUTCH_NEST_RELOAD)) {
            this.animal.removeFromSquare();
            this.animal.hutch = this.plz$hutch;
            PLZFixes.hit(PLZFixes.HUTCH_NEST_RELOAD);
        }
    }
}

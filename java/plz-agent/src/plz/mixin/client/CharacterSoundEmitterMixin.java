package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.characters.BaseCharacterSoundEmitter;
import zombie.characters.CharacterSoundEmitter;
import zombie.characters.IsoGameCharacter;
import zombie.plz.PLZAnimalForm;

@Mixin(CharacterSoundEmitter.class)
abstract class CharacterSoundEmitterMixin extends BaseCharacterSoundEmitter {
    private CharacterSoundEmitterMixin(IsoGameCharacter chr) {
        super(chr);
    }

    @ModifyExpressionValue(
        method = "playVocals",
        at = @At(value = "FIELD", target = "Lzombie/network/GameServer;server:Z", opcode = org.objectweb.asm.Opcodes.GETSTATIC)
    )
    private boolean plz$silentWhileMorphed(boolean server) {
        return server || PLZAnimalForm.isMorphed(this.character);
    }
}

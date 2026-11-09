package plz.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import zombie.characters.BodyDamage.BodyDamage;
import zombie.characters.IsoGameCharacter;
import zombie.plz.PLZDowned;

@Mixin(BodyDamage.class)
abstract class BodyDamageMixin {
    @Shadow
    @Final
    private IsoGameCharacter parentChar;

    @ModifyArg(
        method = "calculateOverallHealth",
        at = @At(value = "INVOKE", target = "Lzombie/characters/BodyDamage/BodyDamage;setOverallBodyHealth(F)V")
    )
    private float plz$downedHealth(float computed) {
        return PLZDowned.onOverallHealth(this.parentChar, computed);
    }
}

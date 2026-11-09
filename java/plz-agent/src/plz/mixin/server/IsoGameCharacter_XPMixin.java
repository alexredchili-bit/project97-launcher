package plz.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.skills.PerkFactory;
import zombie.plz.PLZDietScore;

@Mixin(IsoGameCharacter.XP.class)
abstract class IsoGameCharacter_XPMixin {
    @Shadow
    @Final
    private IsoGameCharacter chr;

    // Diet scales Fitness and Strength XP (no score = 1.0); the vanilla loop's perk has getType() == type.
    @ModifyVariable(
        method = "AddXP(Lzombie/characters/skills/PerkFactory$Perk;FZZZZ)V",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoGameCharacter$XP;getXP(Lzombie/characters/skills/PerkFactory$Perk;)F", ordinal = 0),
        argsOnly = true
    )
    private float plz$dietScaledXp(float amount, @Local(argsOnly = true) PerkFactory.Perk type) {
        if ((type == PerkFactory.Perks.Fitness || type == PerkFactory.Perks.Strength) && this.chr instanceof IsoPlayer) {
            return amount * PLZDietScore.multiplierFor(this.chr);
        }

        return amount;
    }
}

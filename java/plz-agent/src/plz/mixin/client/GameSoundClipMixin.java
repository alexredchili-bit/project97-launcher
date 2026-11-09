package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import plz.agent.LuaPublic;
import zombie.audio.GameSound;
import zombie.audio.GameSoundClip;
import zombie.plz.PLZHighFX;
import zombie.plz.PLZSoundGain;

// The PLZ statics hang here because GameSoundClip is already exposed to Lua; a zombie.plz class is not.
@Mixin(GameSoundClip.class)
abstract class GameSoundClipMixin {
    @Shadow
    @Final
    public GameSound gameSound;

    @ModifyReturnValue(method = {"getEffectiveVolume", "getEffectiveVolumeInMenu"}, at = @At("RETURN"), require = 2)
    private float plz$modSoundGain(float original) {
        return original * PLZSoundGain.forSound(this.gameSound);
    }

    @Unique
    @LuaPublic
    private static void setModSoundGain(String modId, float gain) {
        PLZSoundGain.setGain(modId, gain);
    }

    @Unique
    @LuaPublic
    private static float getModSoundGain(String modId) {
        return PLZSoundGain.getGain(modId);
    }

    @Unique
    @LuaPublic
    private static ArrayList<String> getSoundMods() {
        return PLZSoundGain.getMods();
    }

    @Unique
    @LuaPublic
    private static int getSoundModCount(String modId) {
        return PLZSoundGain.countSounds(modId);
    }

    // Rain is a parameter on the vanilla ambience event, not a mod, so its gain fades in with rain intensity.
    @Unique
    @LuaPublic
    private static void setRainSoundGain(float gain) {
        PLZSoundGain.setRainGain(gain);
    }

    @Unique
    @LuaPublic
    private static float getRainSoundGain() {
        return PLZSoundGain.getRainGain();
    }

    // Called from a Lua tick because vanilla overwrites the Ambience VCA at boot and on every Ambient slider change.
    @Unique
    @LuaPublic
    private static void updateAmbienceVca() {
        PLZSoundGain.updateAmbienceVca();
    }

    @Unique
    @LuaPublic
    private static String getAmbienceVcaStatus() {
        return PLZSoundGain.vcaStatus();
    }

    @Unique
    @LuaPublic
    private static String describeSoundOwner(GameSound sound) {
        return PLZSoundGain.describe(sound);
    }

    @Unique
    @LuaPublic
    private static void plzSetHighFX(int playerIndex, float visual, float wobble, float moveTau, float aimTau, int driveLevel) {
        PLZHighFX.set(playerIndex, visual, wobble, moveTau, aimTau, driveLevel);
    }
}

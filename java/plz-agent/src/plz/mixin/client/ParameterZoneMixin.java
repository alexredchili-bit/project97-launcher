package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.audio.FMODParameterUtils;
import zombie.audio.parameters.ParameterZone;
import zombie.characters.IsoGameCharacter;
import zombie.core.math.PZMath;
import zombie.iso.IsoWorld;
import zombie.plz.PLZPerf;

@Mixin(ParameterZone.class)
abstract class ParameterZoneMixin {
    @Unique
    private static final int plz$MAX_HOLD_FRAMES = 30;

    @Unique
    private int plz$squareX;

    @Unique
    private int plz$squareY;

    @Unique
    private int plz$frame;

    @Unique
    private float plz$value;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$initCache(String name, String zoneName, CallbackInfo ci) {
        this.plz$squareX = Integer.MIN_VALUE;
        this.plz$squareY = Integer.MIN_VALUE;
        this.plz$frame = Integer.MIN_VALUE;
    }

    @WrapMethod(method = "calculateCurrentValue")
    private float plz$cachePerSquare(Operation<Float> original) {
        IsoGameCharacter player = FMODParameterUtils.getFirstListener();
        if (player == null || !PLZPerf.SOUND_ZONE_CACHE) {
            return original.call();
        }

        int sx = PZMath.fastfloor(player.getX());
        int sy = PZMath.fastfloor(player.getY());
        int frame = IsoWorld.instance != null ? IsoWorld.instance.getFrameNo() : 0;
        if (sx == this.plz$squareX && sy == this.plz$squareY && frame >= this.plz$frame && frame - this.plz$frame < plz$MAX_HOLD_FRAMES) {
            return this.plz$value;
        }

        this.plz$value = original.call();
        this.plz$squareX = sx;
        this.plz$squareY = sy;
        this.plz$frame = frame;
        return this.plz$value;
    }
}

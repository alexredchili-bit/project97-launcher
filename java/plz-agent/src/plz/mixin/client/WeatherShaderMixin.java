package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.textures.TextureDraw;
import zombie.iso.weather.WeatherShader;
import zombie.plz.PLZHighFX;

@Mixin(WeatherShader.class)
abstract class WeatherShaderMixin {
    @Inject(
        method = "startMainThread",
        at = @At(value = "INVOKE", target = "Lzombie/core/opengl/RenderSettings$PlayerRenderSettings;getDrunkFactor()F")
    )
    private void plz$highFx(TextureDraw texd, int playerIndex, CallbackInfo ci) {
        texd.vars[20] = PLZHighFX.visual(playerIndex);
        texd.vars[21] = PLZHighFX.wobble(playerIndex);
    }
}

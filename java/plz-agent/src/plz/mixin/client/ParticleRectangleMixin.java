package plz.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.plz.PLZWeatherFX;
import zombie.iso.weather.fx.ParticleRectangle;
import zombie.iso.weather.fx.WeatherParticle;
import zombie.iso.weather.fx.WeatherParticleDrawer;
import zombie.plz.PLZRainTiles;
import zombie.plz.access.PLZWeatherParticleDrawerAccess;

@Mixin(ParticleRectangle.class)
abstract class ParticleRectangleMixin {
    @Shadow
    protected int id;

    @Shadow
    @Final
    private int width;

    @Shadow
    @Final
    private int height;

    @Shadow
    private WeatherParticle[] particles;

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lzombie/core/SpriteRenderer;StartShader(II)V", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void plz$renderTiled(
        CallbackInfo ci,
        @Local(ordinal = 3) int cellsW,
        @Local(ordinal = 4) int cellsH,
        @Local(ordinal = 7) int baseX,
        @Local(ordinal = 8) int baseY
    ) {
        if (!PLZRainTiles.enabled() || PLZWeatherFX.debugBounds()) {
            return;
        }

        WeatherParticleDrawer drawer = PLZWeatherFX.instance().getDrawer(this.id);
        PLZWeatherParticleDrawerAccess tiles = (PLZWeatherParticleDrawerAccess)(Object)drawer;
        PLZRainTiles.Tile tile = tiles.plzBeginTile();

        for (int i = 0; i < this.particles.length; i++) {
            WeatherParticle p = this.particles[i];
            if (!(((WeatherParticleMixin)p).plz$getRenderAlpha() <= 0.0F)) {
                p.render(0.0F, 0.0F);
            }
        }

        for (int cy = -1; cy < cellsH; cy++) {
            for (int cx = -1; cx < cellsW; cx++) {
                tile.addOrigin(baseX + cx * this.width, baseY + cy * this.height);
            }
        }

        tiles.plzEndTile();
        ci.cancel();
    }
}

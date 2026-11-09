package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import zombie.iso.weather.fx.WeatherParticle;

@Mixin(WeatherParticle.class)
public interface WeatherParticleMixin {
    @Accessor("renderAlpha")
    float plz$getRenderAlpha();
}

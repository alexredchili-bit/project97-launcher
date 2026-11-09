package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.characters.IsoPlayer;
import zombie.iso.areas.isoregion.regions.IWorldRegion;
import zombie.iso.weather.fx.WeatherFxMask;
import zombie.plz.access.PLZWeatherFxMaskPlayerFxMaskAccess;

@Mixin(WeatherFxMask.PlayerFxMask.class)
abstract class WeatherFxMask_PlayerFxMaskMixin implements PLZWeatherFxMaskPlayerFxMaskAccess {
    @Shadow
    private boolean requiresUpdate;

    @Shadow
    private boolean hasMaskToDraw;

    @Shadow
    private IsoPlayer player;

    @Shadow
    private IWorldRegion curIsoWorldRegion;

    @Override
    public boolean plzRequiresUpdate() {
        return this.requiresUpdate;
    }

    @Override
    public boolean plzHasMaskToDraw() {
        return this.hasMaskToDraw;
    }

    @Override
    public IsoPlayer plzPlayer() {
        return this.player;
    }

    @Override
    public IWorldRegion plzCurIsoWorldRegion() {
        return this.curIsoWorldRegion;
    }
}

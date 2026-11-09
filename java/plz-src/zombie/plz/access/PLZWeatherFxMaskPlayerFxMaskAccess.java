package zombie.plz.access;

import zombie.characters.IsoPlayer;
import zombie.iso.areas.isoregion.regions.IWorldRegion;

public interface PLZWeatherFxMaskPlayerFxMaskAccess {
    boolean plzRequiresUpdate();

    boolean plzHasMaskToDraw();

    IsoPlayer plzPlayer();

    IWorldRegion plzCurIsoWorldRegion();
}

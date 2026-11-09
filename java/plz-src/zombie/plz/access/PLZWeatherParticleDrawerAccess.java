package zombie.plz.access;

import zombie.plz.PLZRainTiles;

public interface PLZWeatherParticleDrawerAccess {
    PLZRainTiles.Tile plzBeginTile();

    void plzEndTile();
}

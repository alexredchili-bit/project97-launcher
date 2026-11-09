package zombie.plz;

import zombie.core.math.PZMath;
import zombie.iso.IsoLot;
import zombie.iso.LotHeader;
import zombie.iso.MapFiles;

public final class PLZZombieIntensityCache {
    private LotHeader owner;
    private LotHeader[] cache;
    private boolean[] cached;

    // LotHeader.getZombieIntensityForChunk re-fetches the same lot header for each of a cell's 1024 chunks.
    public int get(LotHeader lotHeader, int chunkX, int chunkY) {
        if (this.cache == null || lotHeader != this.owner) {
            this.owner = lotHeader;
            this.cache = new LotHeader[IsoLot.MapFiles.size()];
            this.cached = new boolean[IsoLot.MapFiles.size()];
        }

        if (chunkX >= 0 && chunkY >= 0 && chunkX < 32 && chunkY < 32) {
            if (lotHeader == null) {
                return -1;
            }

            for (int j = lotHeader.mapFiles.priority; j < IsoLot.MapFiles.size(); j++) {
                MapFiles mapFiles = IsoLot.MapFiles.get(j);
                int cell300X = PZMath.fastfloor((lotHeader.cellX * 256 + chunkX * 8) / 300.0F);
                int cell300Y = PZMath.fastfloor((lotHeader.cellY * 256 + chunkY * 8) / 300.0F);
                if (mapFiles.bgHasCell300.getValue(cell300X - mapFiles.minCell300X, cell300Y - mapFiles.minCell300Y)) {
                    if (!this.cached[j]) {
                        this.cache[j] = mapFiles.getLotHeader(lotHeader.cellX, lotHeader.cellY);
                        this.cached[j] = true;
                    }

                    LotHeader lotHeader2 = this.cache[j];
                    return lotHeader2.getZombieIntensity(chunkX + chunkY * 32) & 0xFF;
                }
            }

            return -1;
        } else {
            return -1;
        }
    }
}

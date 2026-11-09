package zombie.plz;

import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;

public final class PLZChunkSquareGetter implements IsoGridSquare.GetSquare {
    private final IsoChunk chunk;

    public PLZChunkSquareGetter(IsoChunk chunk) {
        this.chunk = chunk;
    }

    @Override
    public IsoGridSquare getGridSquare(int x, int y, int z) {
        x -= this.chunk.wx * 8;
        y -= this.chunk.wy * 8;
        return x >= 0 && x < 8 && y >= 0 && y < 8 && z >= -32 && z <= 31 ? this.chunk.getGridSquare(x, y, z) : null;
    }
}

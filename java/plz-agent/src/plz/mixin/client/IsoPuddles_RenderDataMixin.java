package plz.mixin.client;

import java.util.ArrayList;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoPuddlesGeometry;
import zombie.plz.access.PLZIsoPuddlesRenderDataAccess;
import zombie.tileDepth.TileSeamManager;

@Mixin(targets = "zombie.iso.IsoPuddles$RenderData")
abstract class IsoPuddles_RenderDataMixin implements PLZIsoPuddlesRenderDataAccess {
    @Shadow
    @Final
    int[] squaresPerLevel;

    @Shadow
    int numSquares;

    @Shadow
    int capacity;

    @Shadow
    float[] data;

    @Shadow
    abstract void addSquare(int z, IsoPuddlesGeometry pg, TileSeamManager.Tiles seamFix2);

    @Override
    public int plzNumSquares() {
        return this.numSquares;
    }

    @Override
    public float[] plzData() {
        return this.data;
    }

    @Override
    public void plzPack(ArrayList<IsoGridSquare> grid, int z, int playerIndex) {
        for (int i = 0; i < grid.size(); i++) {
            IsoPuddlesGeometry puddlesGeometry = grid.get(i).getPuddles();
            if (puddlesGeometry != null && puddlesGeometry.shouldRender()) {
                puddlesGeometry.updateLighting(playerIndex);
                this.addSquare(z, puddlesGeometry, null);
            }
        }
    }

    @Override
    public int plzAppend(float[] packed, int count, int z) {
        if (this.data == null) {
            this.data = new float[this.capacity * 4 * 8];
        }

        while (this.numSquares + count > this.capacity) {
            this.capacity += 128;
        }

        if (this.data.length < this.capacity * 4 * 8) {
            this.data = Arrays.copyOf(this.data, this.capacity * 4 * 8);
        }

        int first = this.numSquares;
        System.arraycopy(packed, 0, this.data, first * 32, count * 32);
        this.numSquares += count;
        this.squaresPerLevel[z + 32] = this.squaresPerLevel[z + 32] + count;
        return first;
    }
}

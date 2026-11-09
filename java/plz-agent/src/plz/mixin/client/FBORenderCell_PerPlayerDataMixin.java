package plz.mixin.client;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.iso.IsoChunk;
import zombie.plz.access.PLZFBOPerPlayerDataAccess;

@Mixin(targets = "zombie.iso.fboRenderChunk.FBORenderCell$PerPlayerData")
abstract class FBORenderCell_PerPlayerDataMixin implements PLZFBOPerPlayerDataAccess {
    @Shadow
    @Final
    private ArrayList<IsoChunk> onScreenChunks;

    @Shadow
    private int lightingUpdateCounter;

    @Override
    public ArrayList<IsoChunk> plzOnScreenChunks() {
        return this.onScreenChunks;
    }

    @Override
    public int plzLightingUpdateCounter() {
        return this.lightingUpdateCounter;
    }

    @Override
    public void plzSetLightingUpdateCounter(int counter) {
        this.lightingUpdateCounter = counter;
    }
}

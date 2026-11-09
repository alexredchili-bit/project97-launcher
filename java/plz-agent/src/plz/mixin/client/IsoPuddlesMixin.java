package plz.mixin.client;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.debug.DebugOptions;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoPuddles;
import zombie.plz.access.PLZIsoPuddlesAccess;
import zombie.plz.access.PLZIsoPuddlesRenderDataAccess;

@Mixin(IsoPuddles.class)
abstract class IsoPuddlesMixin implements PLZIsoPuddlesAccess {
    @Override
    public boolean plzCanRender(int z) {
        IsoPuddles self = (IsoPuddles)(Object)this;
        return DebugOptions.instance.weather.waterPuddles.getValue()
            && self.getShaderEnable()
            && Core.getInstance().getUseShaders()
            && Core.getInstance().getPerfPuddles() != 3
            && (z <= 0 || Core.getInstance().getPerfPuddles() <= 0)
            && (self.getWetGroundFinalValue() != 0.0 || self.getPuddlesSizeFinalValue() != 0.0);
    }

    // RenderData is private, so neither @Shadow nor @Accessor can name the array; a constant VarHandle folds to a field read.
    @Unique
    private static final VarHandle plz$RENDER_DATA = plz$renderDataHandle();

    @Unique
    private static VarHandle plz$renderDataHandle() {
        try {
            return MethodHandles.lookup().unreflectVarHandle(IsoPuddles.class.getDeclaredField("renderData"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private PLZIsoPuddlesRenderDataAccess plz$threadData() {
        Object[][] renderData = (Object[][])plz$RENDER_DATA.get((IsoPuddles)(Object)this);
        return (PLZIsoPuddlesRenderDataAccess)renderData[SpriteRenderer.instance.getMainStateIndex()][IsoCamera.frameState.playerIndex];
    }

    @Override
    public int plzNumSquares() {
        return this.plz$threadData().plzNumSquares();
    }

    @Override
    public float[] plzData() {
        return this.plz$threadData().plzData();
    }

    @Override
    public void plzPack(ArrayList<IsoGridSquare> grid, int z) {
        this.plz$threadData().plzPack(grid, z, IsoCamera.frameState.playerIndex);
    }

    @Override
    public int plzAppend(float[] packed, int count, int z) {
        return this.plz$threadData().plzAppend(packed, count, z);
    }

    @Override
    public void plzDraw(int z, int firstSquare, int numSquares) {
        SpriteRenderer.instance.drawPuddles(IsoCamera.frameState.playerIndex, z, firstSquare, numSquares);
    }
}

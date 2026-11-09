package plz.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.plz.PLZChunkRate;
import zombie.plz.PLZChunkSquareGetter;
import zombie.plz.access.PLZIsoChunkAccess;

@Mixin(IsoChunk.class)
abstract class IsoChunkMixin implements PLZIsoChunkAccess {
    @Shadow
    public int wx;

    @Shadow
    public int wy;

    @Shadow
    public int maxLevel;

    @Shadow
    public int minLevel;

    @Shadow
    @Final
    private FBORenderLevels[] renderLevels;

    @Unique
    private long[] plz$occluderMask;

    @Unique
    private long plz$occluderMaskSet;

    @Unique
    private int[][] plz$lightInfoFrame;

    @Unique
    private int[] plz$treeExportFp;

    @Shadow
    public abstract IsoGridSquare getGridSquare(int x, int y, int z);

    @Shadow
    public abstract void setSquare(int x, int y, int z, IsoGridSquare square);

    @Shadow
    public abstract int getMinLevel();

    @Shadow
    private void ensureNotNull3x3(int lx, int ly, int z) {
        throw new AssertionError();
    }

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void plz$initOccluderMask(CallbackInfo ci) {
        this.plz$occluderMask = new long[64];
    }

    @Override
    public long[] plzOccluderMask() {
        return this.plz$occluderMask;
    }

    @Override
    public long plzOccluderMaskSet() {
        return this.plz$occluderMaskSet;
    }

    @Override
    public void plzSetOccluderMaskSet(long mask) {
        this.plz$occluderMaskSet = mask;
    }

    @Override
    public int[][] plzLightInfoFrame() {
        return this.plz$lightInfoFrame;
    }

    @Override
    public void plzSetLightInfoFrame(int[][] stamps) {
        this.plz$lightInfoFrame = stamps;
    }

    @Override
    public int[] plzTreeExportFp() {
        return this.plz$treeExportFp;
    }

    @Override
    public void plzSetTreeExportFp(int[] fp) {
        this.plz$treeExportFp = fp;
    }

    @Override
    public void recalcLoop1() {
        for (int z = this.minLevel; z <= this.maxLevel; z++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    IsoGridSquare sq = this.getGridSquare(x, y, z);
                    if (sq == null && z == 0) {
                        sq = IsoGridSquare.getNew(IsoWorld.instance.currentCell, null, this.wx * 8 + x, this.wy * 8 + y, z);
                        this.setSquare(x, y, z, sq);
                    }

                    if (sq != null) {
                        if (!sq.getObjects().isEmpty()) {
                            this.ensureNotNull3x3(x, y, z);

                            for (int zz = z - 1; zz > this.minLevel; zz--) {
                                this.ensureNotNull3x3(x, y, zz);
                            }
                        }

                        sq.RecalcProperties();
                    }
                }
            }
        }
    }

    // PLZ: an own getter per call, so pool threads never share vanilla's static one.
    @Override
    public void recalcPooled() {
        PLZChunkSquareGetter getter = new PLZChunkSquareGetter((IsoChunk) (Object) this);

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                for (int z = this.maxLevel; z > 0; z--) {
                    IsoGridSquare sq = this.getGridSquare(x, y, z);
                    if (sq != null && sq.hasRainBlockingTile()) {
                        z--;

                        for (; z >= this.getMinLevel(); z--) {
                            sq = this.getGridSquare(x, y, z);
                            if (sq != null && !sq.haveRoof) {
                                sq.haveRoof = true;
                                sq.getProperties().unset(IsoFlagType.exterior);
                            }
                        }
                        break;
                    }
                }
            }
        }

        for (int z = this.minLevel; z <= this.maxLevel; z++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    IsoGridSquare sq = this.getGridSquare(x, y, z);
                    if (sq != null) {
                        sq.RecalcAllWithNeighbours(true, getter);
                    }
                }
            }
        }

        for (int z = this.minLevel; z <= this.maxLevel; z++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    IsoGridSquare sq = this.getGridSquare(x, y, z);
                    if (sq != null) {
                        sq.propertiesDirty = true;
                    }
                }
            }
        }
    }

    @Inject(method = "loadInMainThread", at = @At("HEAD"))
    private void plz$countLoaded(CallbackInfo ci) {
        PLZChunkRate.loaded();
    }

    @Inject(
        method = "resetForStore",
        at = @At(value = "INVOKE", target = "Lzombie/util/CappedConcurrentQueue;remove(Ljava/lang/Object;)Z", shift = At.Shift.AFTER)
    )
    private void plz$resetPerFrameState(CallbackInfo ci) {
        this.plz$occluderMaskSet = 0L;
        this.plz$treeExportFp = null;

        for (int playerIndex = 0; playerIndex < 4; playerIndex++) {
            FBORenderLevels renderLevels = this.renderLevels[playerIndex];
            if (renderLevels != null) {
                for (int z = this.minLevel; z <= this.maxLevel; z++) {
                    renderLevels.clearCachedSquares(z);
                }
            }
        }
    }
}

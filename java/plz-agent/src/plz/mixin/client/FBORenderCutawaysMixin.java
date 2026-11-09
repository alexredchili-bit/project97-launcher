package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import plz.agent.LuaPublic;
import zombie.characters.IsoPlayer;
import zombie.core.PerformanceSettings;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.plz.PLZPerf;
import zombie.plz.access.PLZCutawayPerPlayerDataAccess;
import zombie.plz.access.PLZCutawayWallAccess;
import zombie.plz.access.PLZOrphanStructuresAccess;

@Mixin(FBORenderCutaways.class)
abstract class FBORenderCutawaysMixin {
    @Shadow
    public IsoCell cell;

    @Shadow
    @Final
    private HashSet<IsoChunk> invalidatedChunks;

    @Unique
    @LuaPublic
    private static long plzRoofFlipsHeld;

    @Unique
    @LuaPublic
    private static long plzCutawayVisits;

    @Unique
    @LuaPublic
    private static long plzCutawayChunksInvalidated;

    @Unique
    @LuaPublic
    private static long plzCutawayChangedSquares;

    @Unique
    @LuaPublic
    private static long plzWallsSkipped;

    @Unique
    @LuaPublic
    private static long plzWallsVisited;

    // PerPlayerData and PlayerInRange are private to FBORenderCutaways, so their arrays are read through constant VarHandles.
    @Unique
    private static final VarHandle plz$PER_PLAYER_DATA = plz$handle(FBORenderCutaways.class, "perPlayerData");

    @Unique
    private static final VarHandle plz$ORPHAN_RANGE = plz$handle(FBORenderCutaways.OrphanStructures.class, "playerInRange");

    @Unique
    private static final Object plz$RANGE_UNSET = plz$rangeConstant(0);

    @Unique
    private static final Object plz$RANGE_TRUE = plz$rangeConstant(1);

    @Unique
    private final IdentityHashMap<IsoGridSquare, Integer> plz$oldFlags = new IdentityHashMap<>();

    @Unique
    private final ArrayList<IsoGridSquare> plz$touched = new ArrayList<>();

    @Unique
    private boolean plz$changed;

    @Unique
    private boolean plz$poiPrefilter;

    @Unique
    private boolean plz$poiVisited;

    @Unique
    private boolean plz$wallPrefilter;

    @Unique
    private ArrayList<Long> plz$roomIds;

    @Unique
    private boolean plz$peeking;

    @Unique
    private ArrayList<BuildingDef> plz$collapse;

    @Unique
    private IsoGridSquare plz$climbTarget;

    @Unique
    private boolean plz$skipWall;

    @Unique
    private FBORenderCutaways.CutawayWall plz$wall;

    @Unique
    private int plz$orphanPlayerIndex;

    @Unique
    private int plz$visitPlayerIndex;

    @Unique
    private static VarHandle plz$handle(Class<?> owner, String name) {
        try {
            return MethodHandles.lookup().unreflectVarHandle(owner.getDeclaredField(name));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private static Object plz$rangeConstant(int ordinal) {
        try {
            Field field = FBORenderCutaways.OrphanStructures.class.getDeclaredField("playerInRange");
            return field.getType().getComponentType().getEnumConstants()[ordinal];
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Unique
    private PLZCutawayPerPlayerDataAccess plz$playerData(int playerIndex) {
        return (PLZCutawayPerPlayerDataAccess)((Object[])plz$PER_PLAYER_DATA.get((FBORenderCutaways)(Object)this))[playerIndex];
    }

    // Per-frame paths capture their arguments with @ModifyVariable/@WrapOperation, which allocate nothing, unlike a CallbackInfo.
    @ModifyVariable(method = "checkOrphanStructures", at = @At("HEAD"), argsOnly = true)
    private int plz$beginOrphanCheck(int playerIndex) {
        this.plz$orphanPlayerIndex = playerIndex;
        return playerIndex;
    }

    @WrapOperation(
        method = "checkOrphanStructures",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderCutaways$OrphanStructures;shouldCutaway()Z")
    )
    private boolean plz$debounceRoofHide(FBORenderCutaways.OrphanStructures orphanStructures, Operation<Boolean> original) {
        int playerIndex = this.plz$orphanPlayerIndex;
        boolean wantHide = original.call(orphanStructures);
        int debounce = PLZPerf.ROOF_HIDE_DEBOUNCE_FRAMES;
        Object range = ((Object[])plz$ORPHAN_RANGE.get(orphanStructures))[playerIndex];
        if (debounce > 0 && range != plz$RANGE_UNSET) {
            boolean isHidden = range == plz$RANGE_TRUE;
            int[] pending = ((PLZOrphanStructuresAccess)(Object)orphanStructures).plzPendingFrames();
            if (wantHide == isHidden) {
                pending[playerIndex] = 0;
            } else if (++pending[playerIndex] < debounce) {
                plzRoofFlipsHeld++;
                // Answering with the current state makes both vanilla branches a no-op, which is the hold.
                return isHidden;
            }
        }

        return wantHide;
    }

    @ModifyVariable(method = "doCutawayVisitSquares(ILjava/util/ArrayList;)V", at = @At("HEAD"), argsOnly = true)
    private int plz$beginVisit(int playerIndex) {
        this.plz$visitPlayerIndex = playerIndex;
        this.plz$poiPrefilter = PLZPerf.CUTAWAY_VISIT_PREFILTER;
        this.plz$poiVisited = false;
        return playerIndex;
    }

    @WrapOperation(
        method = "doCutawayVisitSquares(ILjava/util/ArrayList;)V",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/fboRenderChunk/FBORenderCutaways;doCutawayVisitSquares(Lzombie/iso/IsoGridSquare;JLjava/util/ArrayList;)V"
        )
    )
    private void plz$visitFirstPointOnly(
        FBORenderCutaways self, IsoGridSquare sq, long currentTimeMillis, ArrayList<IsoChunk> onScreenChunks, Operation<Void> original
    ) {
        if (this.plz$poiPrefilter && this.plz$poiVisited) {
            return;
        }

        original.call(self, sq, currentTimeMillis, onScreenChunks);
        this.plz$poiVisited = true;
    }

    @WrapOperation(
        method = "doCutawayVisitSquares(ILjava/util/ArrayList;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/HashSet;clear()V", ordinal = 4)
    )
    private void plz$snapshotOldFlags(HashSet<IsoChunk> invalidated, Operation<Void> original) {
        original.call(invalidated);
        int playerIndex = this.plz$visitPlayerIndex;
        this.plz$changed = PLZPerf.CUTAWAY_INVALIDATE_CHANGED && PerformanceSettings.fboRenderChunk;
        if (this.plz$changed) {
            this.plz$oldFlags.clear();
            this.plz$touched.clear();
            ArrayList<IsoGridSquare> last = this.plz$playerData(playerIndex).plzLastCutawayVisitorResults();

            for (int i = 0; i < last.size(); i++) {
                IsoGridSquare square = last.get(i);
                if (this.plz$oldFlags.put(square, square.getPlayerCutawayFlag(playerIndex, 0L)) == null) {
                    this.plz$touched.add(square);
                }
            }
        }
    }

    @WrapOperation(
        method = "doCutawayVisitSquares(ILjava/util/ArrayList;)V",
        at = {
            @At(value = "INVOKE", target = "Ljava/util/HashSet;contains(Ljava/lang/Object;)Z", ordinal = 2),
            @At(value = "INVOKE", target = "Ljava/util/HashSet;contains(Ljava/lang/Object;)Z", ordinal = 3),
            @At(value = "INVOKE", target = "Ljava/util/HashSet;contains(Ljava/lang/Object;)Z", ordinal = 4)
        },
        require = 3
    )
    private boolean plz$deferInvalidation(HashSet<IsoChunk> set, Object chunk, Operation<Boolean> original) {
        return this.plz$changed || original.call(set, chunk);
    }

    @WrapOperation(
        method = "doCutawayVisitSquares(ILjava/util/ArrayList;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/HashSet;iterator()Ljava/util/Iterator;", ordinal = 0)
    )
    private Iterator<IsoGridSquare> plz$snapshotNewSquares(HashSet<IsoGridSquare> north, Operation<Iterator<IsoGridSquare>> original) {
        int playerIndex = this.plz$visitPlayerIndex;
        if (this.plz$changed) {
            PLZCutawayPerPlayerDataAccess data = this.plz$playerData(playerIndex);
            for (IsoGridSquare square : data.plzCutawayVisitorResultsNorth()) {
                if (!this.plz$oldFlags.containsKey(square)) {
                    this.plz$oldFlags.put(square, square.getPlayerCutawayFlag(playerIndex, 0L));
                    this.plz$touched.add(square);
                }
            }

            for (IsoGridSquare square : data.plzCutawayVisitorResultsWest()) {
                if (!this.plz$oldFlags.containsKey(square)) {
                    this.plz$oldFlags.put(square, square.getPlayerCutawayFlag(playerIndex, 0L));
                    this.plz$touched.add(square);
                }
            }
        }

        return original.call(north);
    }

    @WrapOperation(
        method = "doCutawayVisitSquares(ILjava/util/ArrayList;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/HashSet;iterator()Ljava/util/Iterator;", ordinal = 2)
    )
    private Iterator<IsoChunk> plz$invalidateChanged(HashSet<IsoChunk> invalidated, Operation<Iterator<IsoChunk>> original) {
        int playerIndex = this.plz$visitPlayerIndex;
        if (this.plz$changed) {
            for (int i = 0; i < this.plz$touched.size(); i++) {
                IsoGridSquare square = this.plz$touched.get(i);
                int oldFlag = this.plz$oldFlags.get(square);
                if (oldFlag != square.getPlayerCutawayFlag(playerIndex, 0L)) {
                    plzCutawayChangedSquares++;
                    IsoChunk chunk = square.getChunk();
                    if (chunk != null && !this.invalidatedChunks.contains(chunk)) {
                        this.invalidatedChunks.add(chunk);
                        FBORenderLevels renderLevels = chunk.getRenderLevels(playerIndex);
                        renderLevels.invalidateLevel(square.z, 2048L);
                        if (!chunk.IsOnScreen(false)) {
                            chunk.getCutawayData().invalidateOccludedSquaresMaskForSeenRooms(playerIndex, square.z);
                        }
                    }
                }
            }

            plzCutawayVisits++;
            plzCutawayChunksInvalidated = plzCutawayChunksInvalidated + this.invalidatedChunks.size();
            this.plz$oldFlags.clear();
            this.plz$touched.clear();
        }

        return original.call(invalidated);
    }

    @ModifyVariable(method = "cutawayVisit", at = @At("HEAD"), argsOnly = true)
    private IsoGridSquare plz$beginCutawayVisit(IsoGridSquare poiSquare) {
        int playerIndex = IsoCamera.frameState.playerIndex;
        IsoChunkMap chunkMap = this.cell.chunkMap[playerIndex];
        this.plz$wallPrefilter = false;
        if (chunkMap != null && !chunkMap.ignore) {
            boolean prefilter = PLZPerf.CUTAWAY_VISIT_PREFILTER;
            this.plz$roomIds = prefilter ? this.cell.tempPlayerCutawayRoomIds.get(playerIndex) : null;
            this.plz$peeking = prefilter && this.cell.playerWindowPeekingRoomId[playerIndex] != -1L;
            this.plz$collapse = prefilter ? ((FBORenderCutaways)(Object)this).getCollapsedBuildings() : null;
            IsoPlayer player = IsoPlayer.players[playerIndex];
            this.plz$climbTarget = prefilter && player.isClimbing() ? player.getClimbData().targetGridSquare : null;
            this.plz$wallPrefilter = prefilter;
        }

        return poiSquare;
    }

    @WrapOperation(
        method = "cutawayVisit",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderCutaways$CutawayWall;isHorizontal()Z")
    )
    private boolean plz$prefilterWall(FBORenderCutaways.CutawayWall wall, Operation<Boolean> original) {
        if (this.plz$wallPrefilter
            && !((PLZCutawayWallAccess)(Object)wall).plzCanCut(this.plz$roomIds, this.plz$peeking, this.plz$collapse, this.plz$climbTarget)) {
            plzWallsSkipped++;
            this.plz$skipWall = true;
        } else {
            plzWallsVisited++;
            this.plz$skipWall = false;
        }

        this.plz$wall = wall;
        return original.call(wall);
    }

    // A skipped wall still walks its squares, but finds none, so its body never runs.
    @WrapOperation(
        method = "cutawayVisit",
        at = @At(value = "INVOKE", target = "Lzombie/iso/IsoChunkMap;getGridSquare(III)Lzombie/iso/IsoGridSquare;")
    )
    private IsoGridSquare plz$skipNorthSquares(IsoChunkMap chunkMap, int x, int y, int z, Operation<IsoGridSquare> original) {
        return this.plz$skipWall ? null : original.call(chunkMap, x, y, z);
    }

    @WrapOperation(
        method = "cutawayVisit",
        at = @At(value = "INVOKE", target = "Lzombie/iso/IsoCell;getGridSquare(III)Lzombie/iso/IsoGridSquare;")
    )
    private IsoGridSquare plz$skipWestSquares(IsoCell cell, int x, int y, int z, Operation<IsoGridSquare> original) {
        return this.plz$skipWall ? null : original.call(cell, x, y, z);
    }

    @WrapOperation(
        method = "cutawayVisit",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/IsoChunk;getCutawayDataForLevel(I)Lzombie/iso/fboRenderChunk/FBORenderCutaways$ChunkLevelData;"
        ),
        require = 2
    )
    private FBORenderCutaways.ChunkLevelData plz$sameChunkLevelData(IsoChunk chunk, int level, Operation<FBORenderCutaways.ChunkLevelData> original) {
        FBORenderCutaways.ChunkLevelData own = ((PLZCutawayWallAccess)(Object)this.plz$wall).plzChunkLevelData();
        return chunk == own.levelsData.chunk ? own : original.call(chunk, level);
    }
}

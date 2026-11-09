package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import plz.agent.LuaPublic;
import zombie.IndieGL;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.math.PZMath;
import zombie.core.opengl.Shader;
import zombie.core.textures.ColorInfo;
import zombie.core.textures.Texture;
import zombie.debug.DebugOptions;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoDepthHelper;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoTreeJumbo;
import zombie.iso.IsoUtils;
import zombie.iso.IsoWorld;
import zombie.iso.LightingJNI;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.fboRenderChunk.FBORenderCell;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.fboRenderChunk.FBORenderChunkManager;
import zombie.iso.fboRenderChunk.FBORenderCutaways;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.iso.fboRenderChunk.FBORenderOcclusion;
import zombie.iso.fboRenderChunk.FBORenderTrees;
import zombie.iso.fboRenderChunk.ObjectRenderInfo;
import zombie.iso.fboRenderChunk.ObjectRenderLayer;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoTree;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.plz.PLZAnimalForm;
import zombie.plz.PLZBakeScheduler;
import zombie.plz.PLZChunkRate;
import zombie.plz.PLZLightRefresh;
import zombie.plz.PLZPerf;
import zombie.plz.PLZPuddleCache;
import zombie.plz.PLZRoomInvalidation;
import zombie.plz.PLZTorchZone;
import zombie.plz.PLZTreeBake;
import zombie.plz.PLZTreeBakePass;
import zombie.plz.access.PLZFBOPerPlayerDataAccess;
import zombie.plz.access.PLZIsoChunkAccess;
import zombie.util.list.PZArrayUtil;

@Mixin(FBORenderCell.class)
abstract class FBORenderCellMixin {
    @Shadow
    public IsoCell cell;

    @Shadow
    public boolean renderTranslucentOnly;

    @Shadow
    private long currentTimeMillis;

    @Shadow
    @Final
    private ArrayList<IsoChunk> sortedChunks;

    @Unique
    private PLZFBOPerPlayerDataAccess[] plz$ppd;

    @Unique
    private boolean plz$treesPerFrameNow;

    @Unique
    private int plz$treeModeFrame;

    @Unique
    private static long plz$treesPerFrameFrames;

    @Unique
    private PLZTreeBake.Rect plz$treeRect;

    @Unique
    private PLZTreeBake.Rect plz$treeOwnTexture;

    @Unique
    private PLZTreeBake.Rect plz$treeOtherTexture;

    @Unique
    private PLZTreeBakePass plz$treePass;

    @Unique
    private HashSet<IsoTree> plz$treesAwaitingTexture;

    @Unique
    private static long plz$treesWaited;

    @Unique
    private static long plz$treesArrived;

    @Unique
    private PLZBakeScheduler plz$bakes;

    @Unique
    private ArrayList<IsoChunk> plz$nearChunks;

    @Unique
    private IsoGridSquare plz$gridStackSquare;

    @Unique
    private IsoDirections plz$gridStackDir;

    @Unique
    private int plz$gridStackFrame;

    @Unique
    private PLZLightRefresh[] plz$lightBacklogs;

    @Unique
    private PLZTorchZone plz$torchZone;

    @Unique
    private ArrayList<IsoGameCharacter.TorchInfo> plz$torches;

    @Unique
    private long plz$lightEventUntilMs;

    @Unique
    private int plz$playerChunkX;

    @Unique
    private int plz$playerChunkY;

    @Unique
    @LuaPublic
    private static long plzLightInfoSkipped;

    @Unique
    @LuaPublic
    private static long plzLightInfoLevelsGated;

    @Unique
    @LuaPublic
    private static long plzOcclusionRebuildsSkipped;

    // Per-call state handed between injectors of one render-thread method, so no injector needs a LocalRef; none of them nest.
    @Unique
    private boolean plz$skipLevel;

    @Unique
    private boolean plz$refreshLevel;

    @Unique
    private IsoChunk plz$occChunk;

    @Unique
    private FBORenderLevels plz$occLevels;

    @Unique
    private int plz$occLevel;

    @Shadow
    public abstract boolean isTreeRenderedEveryFrame(IsoObject object);

    @Shadow
    abstract void renderMinusFloor_NotDoorOrWall(IsoObject object);

    @Shadow
    private ColorInfo sanitizeLightInfo(int playerIndex, IsoGridSquare square) {
        throw new AssertionError();
    }

    @Shadow
    private boolean hasAnyDirtyChunkTextures(int playerIndex) {
        throw new AssertionError();
    }

    @Shadow
    private void recalculateAnyGridStacks(int playerIndex) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isObjectRenderLayer_TranslucentFloor(IsoObject object) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isObjectRenderLayer_Floor(IsoObject object) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isObjectRenderLayer_Vegetation(IsoObject object) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isObjectRenderLayer_MinusFloor(IsoObject object) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isObjectRenderLayer_MinusFloorSE(IsoObject object) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isObjectRenderLayer_Translucent(IsoObject object) {
        throw new AssertionError();
    }

    @Shadow
    private void renderMinusFloor_DoorOrWall(IsoObject object) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$treeModeFrame = -1;
        this.plz$treeRect = new PLZTreeBake.Rect();
        this.plz$treeOwnTexture = new PLZTreeBake.Rect();
        this.plz$treeOtherTexture = new PLZTreeBake.Rect();
        this.plz$treePass = new PLZTreeBakePass(this.plz$treeOwnTexture, this.plz$treeOtherTexture);
        this.plz$treesAwaitingTexture = new HashSet<>();
        this.plz$bakes = new PLZBakeScheduler();
        this.plz$nearChunks = new ArrayList<>();
        this.plz$gridStackFrame = -1000;
        this.plz$lightBacklogs = new PLZLightRefresh[] {new PLZLightRefresh(), new PLZLightRefresh(), new PLZLightRefresh(), new PLZLightRefresh()};
        this.plz$torchZone = new PLZTorchZone();
        this.plz$torches = new ArrayList<>();
        this.plz$playerChunkX = Integer.MIN_VALUE;
        this.plz$playerChunkY = Integer.MIN_VALUE;
        this.plz$ppd = plz$perPlayerData((FBORenderCell) (Object) this);
    }

    // PerPlayerData is package-private, so only a lookup made from inside FBORenderCell can read the array once.
    @Unique
    private static PLZFBOPerPlayerDataAccess[] plz$perPlayerData(FBORenderCell self) {
        Object[] all;
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<?> type = lookup.findClass("zombie.iso.fboRenderChunk.FBORenderCell$PerPlayerData");
            all = (Object[]) lookup.findVarHandle(FBORenderCell.class, "perPlayerData", type.arrayType()).get(self);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        PLZFBOPerPlayerDataAccess[] out = new PLZFBOPerPlayerDataAccess[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = (PLZFBOPerPlayerDataAccess) all[i];
        }

        return out;
    }

    @Inject(method = "renderTilesInternal", at = @At("HEAD"))
    private void plz$treeModeForFrame(int maxHeight, CallbackInfo ci) {
        this.plz$updateTreeMode();
    }

    @Redirect(
        method = "renderTilesInternal",
        at = @At(value = "FIELD", target = "Lzombie/iso/fboRenderChunk/FBORenderLevels;clearCachedSquares:Z", opcode = Opcodes.PUTSTATIC)
    )
    private void plz$keepDeferredLists(boolean clear) {
        FBORenderLevels.clearCachedSquares = clear && !plz$keepPerFrameLists();
    }

    @Inject(
        method = "renderTilesInternal",
        at = @At(value = "FIELD", target = "Lzombie/iso/fboRenderChunk/FBORenderCell;currentTimeMillis:J", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER)
    )
    private void plz$lightUrgencyForFrame(int maxHeight, CallbackInfo ci, @Local(index = 3) IsoPlayer player) {
        this.plz$updateLightUrgency(player);
    }

    @ModifyArg(
        method = "renderTilesInternal",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderCutaways;doCutawayVisitSquares(ILjava/util/ArrayList;)V"),
        index = 1
    )
    private ArrayList<IsoChunk> plz$cutawayVisitChunks(ArrayList<IsoChunk> onScreenChunks) {
        int r = PLZPerf.CUTAWAY_RADIUS;
        if (r <= 0) {
            return onScreenChunks;
        }

        int cx = PZMath.fastfloor(IsoCamera.frameState.camCharacterX / 8.0F);
        int cy = PZMath.fastfloor(IsoCamera.frameState.camCharacterY / 8.0F);
        this.plz$nearChunks.clear();

        for (int i = 0; i < onScreenChunks.size(); i++) {
            IsoChunk c = onScreenChunks.get(i);
            if (Math.abs(c.wx - cx) <= r && Math.abs(c.wy - cy) <= r) {
                this.plz$nearChunks.add(c);
            }
        }

        return this.plz$nearChunks;
    }

    @Redirect(
        method = "renderTilesInternal",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderCell;hasAnyDirtyChunkTextures(I)Z")
    )
    private boolean plz$occlusionDirty(FBORenderCell self, int playerIndex) {
        return this.plz$hasDirtyChunkTexturesForOcclusion(playerIndex);
    }

    @Inject(
        method = "performRenderTiles",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderChunkManager;startFrame()V", shift = At.Shift.AFTER)
    )
    private void plz$bakesForFrame(CallbackInfo ci) {
        this.plz$bakes.beginFrame();
    }

    // PLZ: the bake scheduler may hold a dirty level. A held level with a texture takes vanilla's not-dirty path
    // (end + translucent lists); one without a usable texture is skipped outright, as the shadow returned there.
    @Redirect(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderChunkManager;beginRenderChunkLevel(Lzombie/iso/IsoChunk;IFZZ)Z")
    )
    private boolean plz$scheduleBake(
        FBORenderChunkManager manager,
        IsoChunk c,
        int level,
        float zoom,
        boolean canRender,
        boolean flag,
        IsoChunk chunk,
        int chunkLevel,
        IsoCell.PerPlayerRender perPlayerRender,
        int playerIndex,
        long currentTimeMillis,
        Shader floorRenderShader,
        Shader wallRenderShader
    ) {
        this.plz$skipLevel = false;
        FBORenderLevels renderLevels = c.getRenderLevels(playerIndex);
        if (PLZBakeScheduler.active() && renderLevels.isDirty(level, zoom)) {
            FBORenderChunk texture = renderLevels.getFBOForLevel(level, zoom);
            boolean defer;
            if (level == renderLevels.getMinLevel(level)) {
                defer = !this.plz$bakes.admit(
                    renderLevels.isDirty(level, PLZBakeScheduler.DIRTY_CREATE, zoom),
                    renderLevels.isDirty(level, PLZBakeScheduler.DIRTY_CONTENT, zoom),
                    renderLevels.isDirty(level, PLZBakeScheduler.DIRTY_CONTENT_NOT_REDRAW, zoom),
                    this.plz$lightUrgency(c),
                    texture,
                    IsoWorld.instance.getFrameNo(),
                    currentTimeMillis
                );
            } else {
                // Upper levels follow whatever the bottom level of the same chunk decided.
                defer = this.plz$bakes.wasDeferred(texture);
            }

            if (defer) {
                if (texture != null && !renderLevels.isDirty(level, PLZBakeScheduler.DIRTY_CREATE, zoom)) {
                    manager.renderChunk = texture;
                } else {
                    this.plz$skipLevel = true;
                }

                return false;
            }
        }

        return manager.beginRenderChunkLevel(c, level, zoom, canRender, flag);
    }

    @Redirect(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderChunkManager;endRenderChunkLevel(Lzombie/iso/IsoChunk;IFZ)V")
    )
    private void plz$endLevel(
        FBORenderChunkManager manager,
        IsoChunk c,
        int level,
        float zoom,
        boolean rendered,
        IsoChunk chunk,
        int chunkLevel,
        IsoCell.PerPlayerRender perPlayerRender,
        int playerIndex,
        long currentTimeMillis,
        Shader floorRenderShader,
        Shader wallRenderShader
    ) {
        if (this.plz$skipLevel) {
            return;
        }

        if (rendered && this.plz$treePassActive() && manager.renderChunk != null && manager.renderChunk.isTopLevel(level)) {
            this.plz$bakeTrees(c, playerIndex, zoom);
        }

        manager.endRenderChunkLevel(c, level, zoom, rendered);
    }

    @Redirect(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderLevels;getCachedSquares_AnimatedAttachments(I)Ljava/util/List;")
    )
    private List<IsoGridSquare> plz$skippedAnimatedAttachments(FBORenderLevels levels, int level) {
        return this.plz$skipLevel ? Collections.emptyList() : levels.getCachedSquares_AnimatedAttachments(level);
    }

    @Redirect(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderLevels;getCachedSquares_TranslucentFloor(I)Ljava/util/List;")
    )
    private List<IsoGridSquare> plz$skippedTranslucentFloor(FBORenderLevels levels, int level) {
        return this.plz$skipLevel ? Collections.emptyList() : levels.getCachedSquares_TranslucentFloor(level);
    }

    @Redirect(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderLevels;getCachedSquares_Items(I)Ljava/util/List;")
    )
    private List<IsoGridSquare> plz$skippedItems(FBORenderLevels levels, int level) {
        return this.plz$skipLevel ? Collections.emptyList() : levels.getCachedSquares_Items(level);
    }

    @Redirect(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderLevels;getCachedSquares_TranslucentNonFloor(I)Ljava/util/List;")
    )
    private List<IsoGridSquare> plz$skippedTranslucentNonFloor(FBORenderLevels levels, int level) {
        return this.plz$skipLevel ? Collections.emptyList() : levels.getCachedSquares_TranslucentNonFloor(level);
    }

    @Redirect(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderLevels;getCachedSquares_CutawayWindowFrames(I)Ljava/util/List;")
    )
    private List<IsoGridSquare> plz$skippedCutawayWindowFrames(FBORenderLevels levels, int level) {
        return this.plz$skipLevel ? Collections.emptyList() : levels.getCachedSquares_CutawayWindowFrames(level);
    }

    @Inject(
        method = "renderOneLevel",
        at = @At(value = "FIELD", target = "Lzombie/iso/IsoCell$PerPlayerRender;flattenGrassEtc:[[Z", opcode = Opcodes.GETFIELD)
    )
    private void plz$recordBake(
        IsoChunk c,
        int level,
        IsoCell.PerPlayerRender perPlayerRender,
        int playerIndex,
        long currentTimeMillis,
        Shader floorRenderShader,
        Shader wallRenderShader,
        CallbackInfo ci,
        @Local(index = 10) FBORenderLevels renderLevels
    ) {
        if (level == renderLevels.getMinLevel(level)) {
            this.plz$bakes.recordBaked(FBORenderChunkManager.instance.renderChunk, currentTimeMillis);
        }
    }

    @Inject(
        method = "renderOneLevel",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderLevels;clearCachedSquares(I)V", shift = At.Shift.AFTER)
    )
    private void plz$invalidatePuddleCache(
        IsoChunk c,
        int level,
        IsoCell.PerPlayerRender perPlayerRender,
        int playerIndex,
        long currentTimeMillis,
        Shader floorRenderShader,
        Shader wallRenderShader,
        CallbackInfo ci
    ) {
        PLZPuddleCache.invalidate(c, level);
    }

    // A translucent tile baked into the chunk texture is no longer per-frame translucent.
    @ModifyExpressionValue(
        method = {"isObjectRenderLayer_MinusFloor", "isObjectRenderLayer_Translucent"},
        at = @At(value = "FIELD", target = "Lzombie/iso/sprite/IsoSprite;depthFlags:I", opcode = Opcodes.GETFIELD, ordinal = 0)
    )
    private int plz$bakedTranslucentTile(int depthFlags) {
        return PLZPerf.TRANSLUCENT_TILES_IN_CHUNK_TEXTURE ? depthFlags & ~2 : depthFlags;
    }

    // PLZ: windows and doorTrans doors bake into the chunk texture, so neither is translucent here.
    @ModifyVariable(method = "isObjectRenderLayer_MinusFloor", at = @At(value = "STORE", ordinal = 1), index = 9)
    private boolean plz$bakedWindowsAndDoors(boolean translucent) {
        return !PLZPerf.WINDOWS_IN_CHUNK_TEXTURE && translucent;
    }

    @ModifyReturnValue(method = "isTreeRenderedEveryFrame", at = @At("RETURN"))
    private boolean plz$treesBakeOnceReady(boolean everyFrame, @Local(argsOnly = true) IsoObject object) {
        if (PLZPerf.TREES_IN_CHUNK_TEXTURE && !this.plz$treesPerFrameNow) {
            return everyFrame && (!plz$treeTextureReady(object) || plz$hasJumboCutaway(object));
        }

        return everyFrame;
    }

    // PLZ: the tree pass draws chunk trees into the texture itself, so the per-object pass leaves them out.
    @ModifyExpressionValue(
        method = "renderMinusFloor(Lzombie/iso/IsoChunk;Lzombie/iso/IsoGridSquare;Ljava/util/List;)V",
        at = @At(value = "FIELD", target = "Lzombie/iso/fboRenderChunk/ObjectRenderInfo;layer:Lzombie/iso/fboRenderChunk/ObjectRenderLayer;")
    )
    private ObjectRenderLayer plz$treesLeftToPass(ObjectRenderLayer layer, @Local(index = 8) IsoObject object) {
        return layer == ObjectRenderLayer.MinusFloor && object instanceof IsoTree && this.plz$treePassActive() ? null : layer;
    }

    /**
     * @author PLZ
     * @reason The whole vanilla body becomes the inner call, wrapped so a baked tree draws outside the per-frame tree batch.
     */
    @Overwrite
    private void renderMinusFloor(IsoObject object) {
        if (object instanceof IsoTree && PLZPerf.TREES_IN_CHUNK_TEXTURE && PLZPerf.TREE_BAKE_DIRECT) {
            FBORenderTrees batch = FBORenderTrees.current;
            FBORenderTrees.current = null;

            try {
                this.plz$renderMinusFloorInner(object);
            } finally {
                FBORenderTrees.current = batch;
            }
        } else {
            this.plz$renderMinusFloorInner(object);
        }
    }

    @Redirect(
        method = "renderMinusFloor_NotDoorOrWall",
        at = @At(value = "INVOKE", target = "Lzombie/iso/IsoObject;render(FFFLzombie/core/textures/ColorInfo;ZZLzombie/core/opengl/Shader;)V")
    )
    private void plz$nudgedCurtain(
        IsoObject object, float x, float y, float z, ColorInfo lightInfo, boolean bDoAttached, boolean bWallLightingPass, Shader shader
    ) {
        if (!this.renderTranslucentOnly && object instanceof IsoCurtain curtain && plz$curtainDepthNudge(curtain) > 0.0F) {
            plz$renderCurtainNudged(curtain, object.square, lightInfo, plz$curtainDepthNudge(curtain));
        } else {
            object.render(x, y, z, lightInfo, bDoAttached, bWallLightingPass, shader);
        }
    }

    // PLZ: a budgeted refresh replaces the whole pass; answering false to updateSquareLightInfo skips vanilla's.
    @ModifyExpressionValue(
        method = "updateChunkLighting",
        at = @At(value = "INVOKE", target = "Lzombie/debug/BooleanDebugOption;getValue()Z", ordinal = 1)
    )
    private boolean plz$budgetedLighting(boolean updateSquareLightInfo, @Local(index = 1) int playerIndex) {
        int budget = PLZPerf.LIGHTING_BUDGET;
        if (updateSquareLightInfo && budget > 0) {
            this.plz$updateChunkLightingBudgeted(playerIndex, this.plz$ppd[playerIndex], budget);
            return false;
        }

        return updateSquareLightInfo;
    }

    @Redirect(method = "updateChunkLevelLighting", at = @At(value = "INVOKE", target = "Lzombie/iso/IsoGridSquare;cacheLightInfo()V"))
    private void plz$levelLightOncePerFrame(IsoGridSquare square, int playerIndex, IsoChunk chunk, int level) {
        plz$cacheLightInfo(playerIndex, chunk, level, plz$squareIndex(chunk, square), square);
    }

    // IsoChunk.setSquare files a square at x + y * 8 of its level, so the index follows from its coordinates.
    @Unique
    private static int plz$squareIndex(IsoChunk chunk, IsoGridSquare square) {
        return square.x - chunk.wx * 8 + (square.y - chunk.wy * 8) * 8;
    }

    @Inject(
        method = "calculateOccludingSquares",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/fboRenderChunk/FBORenderCutaways$ChunkLevelsData;getDataForLevel(I)Lzombie/iso/fboRenderChunk/FBORenderCutaways$ChunkLevelData;",
            shift = At.Shift.AFTER
        )
    )
    private void plz$occluderLevel(
        int playerIndex, CallbackInfo ci, @Local(index = 4) IsoChunk c, @Local(index = 5) FBORenderLevels renderLevels, @Local(index = 6) int z
    ) {
        this.plz$occChunk = c;
        this.plz$occLevels = renderLevels;
        this.plz$occLevel = z;
    }

    // PLZ: a level whose occluders are cached and whose texture is clean re-applies the mask instead of rescanning.
    @Redirect(
        method = "calculateOccludingSquares",
        at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderCutaways$ChunkLevelData;calculateOccludingSquares(IIIII[I)Z")
    )
    private boolean plz$cachedOccluders(
        FBORenderCutaways.ChunkLevelData levelData,
        int playerIndex,
        int x1,
        int y1,
        int x2,
        int y2,
        int[] grid
    ) {
        IsoChunk c = this.plz$occChunk;
        FBORenderLevels renderLevels = this.plz$occLevels;
        int z = this.plz$occLevel;
        boolean fast = PLZPerf.CUTAWAY_FAST;
        int slot = z + 32;
        PLZIsoChunkAccess chunk = (PLZIsoChunkAccess) (Object) c;
        if (fast && (chunk.plzOccluderMaskSet() & 1L << slot) != 0L && !renderLevels.isDirty(z, Core.getInstance().getZoom(playerIndex))) {
            int width = x2 - x1 + 1;
            long mask = chunk.plzOccluderMask()[slot];

            while (mask != 0L) {
                int b = Long.numberOfTrailingZeros(mask);
                mask &= mask - 1L;
                int zeroX = c.wx * 8 + (b & 7) - z * 3 - x1;
                int zeroY = c.wy * 8 + (b >> 3) - z * 3 - y1;
                if (zeroX >= 0 && zeroY >= 0 && zeroX < width && zeroY <= y2 - y1) {
                    int idx = zeroX + zeroY * width;
                    grid[idx] = PZMath.max(grid[idx], z);
                }
            }

            return false;
        }

        boolean changed = levelData.calculateOccludingSquares(playerIndex, x1, y1, x2, y2, grid);
        if (fast) {
            chunk.plzOccluderMask()[slot] = plz$exactOccluderMask(playerIndex, c, z, levelData);
            chunk.plzSetOccluderMaskSet(chunk.plzOccluderMaskSet() | 1L << slot);
        }

        return changed;
    }

    @Inject(
        method = "prepareChunkForUpdating",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/IsoChunk;getCutawayDataForLevel(I)Lzombie/iso/fboRenderChunk/FBORenderCutaways$ChunkLevelData;",
            shift = At.Shift.AFTER
        )
    )
    private void plz$gateLevelLightInfo(int playerIndex, IsoChunk chunk, int z, CallbackInfo ci, @Local(index = 7) int z2) {
        this.plz$refreshLevel = !PLZPerf.LIGHT_INFO_CHUNK_GATE || LightingJNI.getChunkDirty(playerIndex, chunk.wx, chunk.wy, z2 + 32);
        if (!this.plz$refreshLevel) {
            plzLightInfoLevelsGated++;
        }
    }

    @Redirect(method = "prepareChunkForUpdating", at = @At(value = "INVOKE", target = "Lzombie/iso/IsoGridSquare;cacheLightInfo()V"))
    private void plz$gatedLightInfo(IsoGridSquare sq, int playerIndex, IsoChunk chunk, int z) {
        if (this.plz$refreshLevel || sq.getLightInfo(playerIndex) == null) {
            plz$cacheLightInfo(playerIndex, chunk, sq.z, plz$squareIndex(chunk, sq), sq);
        }
    }

    @Redirect(method = "renderMovingObject", at = @At(value = "INVOKE", target = "Lzombie/characters/IsoGameCharacter;renderShadow(FFF)V"))
    private void plz$animalFormShadow(IsoGameCharacter chr, float x, float y, float z) {
        if (!PLZAnimalForm.renderShadow(chr, x, y, z)) {
            chr.renderShadow(x, y, z);
        }
    }

    // PLZ: the puddle cache replaces the whole pass; answering "snow" makes vanilla skip its own.
    @ModifyExpressionValue(method = "renderPuddles", at = @At(value = "INVOKE", target = "Lzombie/iso/fboRenderChunk/FBORenderSnow;isSnowAnywhere()Z"))
    private boolean plz$cachedPuddles(boolean snow, @Local(index = 1) int playerIndex) {
        if (snow || !PLZPuddleCache.enabled()) {
            return snow;
        }

        int maxZ = this.cell.chunkMap[playerIndex].maxHeight;
        if (Core.getInstance().getPerfPuddles() > 0) {
            maxZ = 0;
        }

        PLZPuddleCache.render(playerIndex, this.plz$ppd[playerIndex].plzOnScreenChunks(), maxZ);
        return true;
    }

    @Inject(
        method = "checkBlackedOutBuildings",
        at = @At(value = "INVOKE", target = "Lzombie/iso/BuildingDef;setInvalidateCacheForAllChunks(IJ)V", shift = At.Shift.AFTER)
    )
    private void plz$buildingLightEvent(int playerIndex, CallbackInfoReturnable<Boolean> cir) {
        this.plz$lightEvent();
    }

    // Two of the room invalidations sit in forEach lambdas, so RoomDefMixin reports them while this call is armed.
    @Inject(method = "checkBlackedOutRooms", at = @At("HEAD"))
    private void plz$armRoomWatch(int playerIndex, CallbackInfo ci) {
        PLZRoomInvalidation.arm();
    }

    @Inject(method = "checkBlackedOutRooms", at = @At("RETURN"))
    private void plz$roomLightEvent(int playerIndex, CallbackInfo ci) {
        if (PLZRoomInvalidation.disarm()) {
            this.plz$lightEvent();
        }
    }

    /**
     * @author PLZ
     * @reason PLZ gates three of its four statements behind a grid-stack frame skip, so the gate is the method.
     */
    @Overwrite
    private boolean recalculateGridStacks(IsoPlayer player, int playerIndex) {
        boolean bForceCutawayUpdate = false;
        int plzInterval = PLZPerf.GRID_STACK_INTERVAL;
        boolean plzSkip = false;
        if (plzInterval > 0 && !player.dirtyRecalcGridStack) {
            IsoGridSquare sq = IsoCamera.frameState.camCharacterSquare;
            IsoDirections dir = player.getDir();
            int frame = IsoWorld.instance.getFrameNo();
            plzSkip = sq != null && sq == this.plz$gridStackSquare && dir == this.plz$gridStackDir && frame - this.plz$gridStackFrame < plzInterval;
            if (!plzSkip) {
                this.plz$gridStackSquare = sq;
                this.plz$gridStackDir = dir;
                this.plz$gridStackFrame = frame;
            }
        }

        if (!plzSkip) {
            FBORenderCutaways.getInstance().CalculatePointsOfInterest();
            bForceCutawayUpdate |= FBORenderCutaways.getInstance().CalculateBuildingsToCollapse();
            bForceCutawayUpdate |= FBORenderCutaways.getInstance().checkHiddenBuildingLevels();
        }

        bForceCutawayUpdate |= player.dirtyRecalcGridStack;
        this.recalculateAnyGridStacks(playerIndex);
        return bForceCutawayUpdate;
    }

    /**
     * @author PLZ
     * @reason Per-frame loop: new dirty gate, texture-arrival block that sets the local bChanged, aim test hoisted, copy invalidation.
     */
    @Overwrite
    private boolean checkTreeTranslucency(int playerIndex, FBORenderLevels renderLevels) {
        if (Core.getInstance().getOptionDoWindSpriteEffects()) {
            return false;
        }

        float zoom = Core.getInstance().getZoom(playerIndex);
        // A lighting-only hold is not a bake this frame, so it must not freeze the tree fade.
        if (PLZBakeScheduler.active() ? renderLevels.isDirty(0, PLZBakeScheduler.DIRTY_CONTENT, zoom) : renderLevels.isDirty(0, zoom)) {
            return false;
        }

        ArrayList<IsoGridSquare> squares = renderLevels.treeSquares;
        boolean bChanged = false;
        boolean plzAiming = PLZTreeBakePass.treeAiming(IsoCamera.frameState.playerIndex);

        for (int i = 0; i < squares.size(); i++) {
            IsoGridSquare square = squares.get(i);
            if (square.chunk != null) {
                IsoTree tree = square.getTree();
                if (tree != null && PLZPerf.TREES_IN_CHUNK_TEXTURE) {
                    if (!plz$treeTextureReady(tree)) {
                        if (this.plz$treesAwaitingTexture.add(tree)) {
                            plz$treesWaited++;
                        }
                    } else if (!this.plz$treesAwaitingTexture.isEmpty() && this.plz$treesAwaitingTexture.remove(tree)) {
                        plz$treesArrived++;
                        bChanged = true;
                        square.invalidateRenderChunkLevel(4096L);
                        IsoGridSquare renderSquare = tree.getRenderSquare();
                        if (renderSquare != null && renderSquare != square) {
                            renderSquare.invalidateRenderChunkLevel(4096L);
                        }

                        this.plz$invalidateTreeCopies(tree, playerIndex);
                    }
                }

                if (tree != null && !this.isTreeRenderedEveryFrame(tree)) {
                    boolean bChanged2 = false;
                    if (tree.fadeAlpha < 1.0F != tree.wasFaded) {
                        tree.wasFaded = tree.fadeAlpha < 1.0F;
                        bChanged = true;
                        bChanged2 = true;
                    }

                    if (this.plz$isTranslucentTree(tree, plzAiming) != tree.renderFlag) {
                        tree.renderFlag = !tree.renderFlag;
                        bChanged = true;
                        bChanged2 = true;
                    }

                    if (bChanged2) {
                        IsoGridSquare renderSquare = tree.getRenderSquare();
                        if (renderSquare != null && tree.getSquare() != renderSquare) {
                            renderSquare.invalidateRenderChunkLevel(4096L);
                        }

                        this.plz$invalidateTreeCopies(tree, playerIndex);
                    }
                }
            }
        }

        return bChanged;
    }

    /**
     * @author PLZ
     * @reason Per-object path: the curtain layer returns early mid-chain, which an injector could only do with a CallbackInfo per call.
     */
    @Overwrite
    private ObjectRenderLayer calculateObjectRenderLayer(IsoObject object) {
        if (!object.getDoRender()) {
            return ObjectRenderLayer.None;
        } else if (object instanceof IsoWorldInventoryObject) {
            return ObjectRenderLayer.WorldInventoryObject;
        } else if (this.isObjectRenderLayer_TranslucentFloor(object)) {
            return ObjectRenderLayer.TranslucentFloor;
        } else if (this.isObjectRenderLayer_Floor(object)) {
            return ObjectRenderLayer.Floor;
        } else if (this.isObjectRenderLayer_Vegetation(object)) {
            return ObjectRenderLayer.Vegetation;
        } else {
            ObjectRenderLayer curtainLayer = this.plz$curtainLayer(object);
            if (curtainLayer != null) {
                return curtainLayer;
            } else if (this.isObjectRenderLayer_MinusFloor(object)) {
                return ObjectRenderLayer.MinusFloor;
            } else if (this.isObjectRenderLayer_MinusFloorSE(object)) {
                return plz$perFrameTranslucentTile(object.getSprite()) ? ObjectRenderLayer.TranslucentSE : ObjectRenderLayer.MinusFloorSE;
            } else {
                return this.isObjectRenderLayer_Translucent(object) ? ObjectRenderLayer.Translucent : ObjectRenderLayer.None;
            }
        }
    }

    @Unique
    private ObjectRenderLayer plz$curtainLayer(IsoObject object) {
        if (!(object instanceof IsoCurtain curtain)) {
            return null;
        } else {
            if (!PLZPerf.WINDOWS_IN_CHUNK_TEXTURE && !PLZPerf.TRANSLUCENT_TILES_IN_CHUNK_TEXTURE) {
                return null;
            }

            IsoObjectType type = curtain.getType();
            if ((type == IsoObjectType.curtainN || type == IsoObjectType.curtainW) && curtain.getSpriteModel() == null) {
                IsoObject attached = curtain.getObjectAttachedTo();
                if (attached != null && attached.square == object.square) {
                    ObjectRenderLayer layer = attached.getRenderInfo(IsoCamera.frameState.playerIndex).layer;
                    return layer != ObjectRenderLayer.MinusFloor && layer != ObjectRenderLayer.Translucent ? null : layer;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    @Unique
    private static boolean plz$perFrameTranslucentTile(IsoSprite sprite) {
        return sprite != null && (sprite.depthFlags & 2) != 0 && (!PLZPerf.TRANSLUCENT_TILES_IN_CHUNK_TEXTURE);
    }

    // 42.21 fades an XXL canopy per frame inside IsoTree.render; a crown baked into the chunk texture cannot fade.
    @Unique
    private static boolean plz$hasJumboCutaway(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        return sprite != null && sprite.name != null && sprite.name.contains("XL") && IsoTreeJumbo.Jumbos.containsKey(sprite.name);
    }

    @Unique
    private boolean plz$treePassActive() {
        return PLZPerf.TREES_IN_CHUNK_TEXTURE
            && PLZPerf.TREE_BAKE_PASS
            && !this.plz$treesPerFrameNow
            && !Core.getInstance().getOptionDoWindSpriteEffects();
    }

    @Unique
    private void plz$updateTreeMode() {
        int frame = IsoWorld.instance.getFrameNo();
        if (frame != this.plz$treeModeFrame) {
            this.plz$treeModeFrame = frame;
            int max = PLZPerf.TREE_BAKE_MAX_CHUNKS_PER_SEC;
            this.plz$treesPerFrameNow = max > 0 && PLZChunkRate.perSecond() > max;
            if (this.plz$treesPerFrameNow) {
                plz$treesPerFrameFrames++;
            }
        }
    }

    @Unique
    private boolean plz$treeBakes(IsoTree tree, boolean aiming) {
        if (tree.square == null || tree.square.z >= 1000 || tree.isHighlighted() || tree.isAnimating()) {
            return false;
        } else {
            return tree.getObjectRenderEffects() == null && tree.getObjectRenderEffectsToApply() == null && !(tree.fadeAlpha < 1.0F)
                ? !this.isTreeRenderedEveryFrame(tree) && !this.plz$isTranslucentTree(tree, aiming)
                : false;
        }
    }

    @Unique
    private boolean plz$treeRect(IsoTree tree, int wx, int wy, float goX, float yoff, PLZTreeBake.Rect out) {
        IsoSprite sprite = tree.getSprite();
        if (sprite == null) {
            return false;
        }

        Texture texture = sprite.getTextureForCurrentFrame(tree.getForwardIsoDirection(), tree);
        if (texture == null) {
            return false;
        }

        int tileScale = Core.tileScale;
        float scale = PLZTreeBake.spriteScale(tileScale, texture.getWidthOrig(), texture.getHeightOrig());
        IsoGridSquare square = tree.square;
        PLZTreeBake.spriteRect(
            square.x,
            square.y,
            square.z,
            wx,
            wy,
            PLZTreeBake.offsetX(sprite.name, tileScale),
            PLZTreeBake.offsetY(sprite.name, tileScale),
            texture.getWidthOrig() * scale,
            texture.getHeightOrig() * scale,
            goX,
            yoff,
            tileScale,
            out
        );
        return true;
    }

    @Unique
    private void plz$invalidateTreeCopies(IsoTree tree, int playerIndex) {
        if (this.plz$treePassActive() && tree.square != null && tree.square.chunk != null) {
            IsoChunk c = tree.square.chunk;
            int minLevel = FBORenderLevels.calculateMinLevel(tree.square.z);
            float zoom = Core.getInstance().getZoom(playerIndex);
            PLZTreeBake.Rect sprite = this.plz$treeRect;
            PLZTreeBake.Rect own = this.plz$treeOwnTexture;
            PLZTreeBake.Rect other = this.plz$treeOtherTexture;
            float goX = 0.0F;
            float yoff = 0.0F;
            if (PLZTreeBakePass.chunkTextureRect(c, c, minLevel, zoom, goX, yoff, own) && this.plz$treeRect(tree, c.wx, c.wy, goX, yoff, sprite)) {
                for (int dwy = -2; dwy <= 2; dwy++) {
                    for (int dwx = -2; dwx <= 2; dwx++) {
                        if (dwx != 0 || dwy != 0) {
                            IsoChunk n = this.cell.getChunk(c.wx + dwx, c.wy + dwy);
                            if (n != null && PLZTreeBakePass.chunkTextureRect(n, c, minLevel, zoom, goX, yoff, other) && PLZTreeBake.needsCopy(sprite, other, own)) {
                                PLZTreeBakePass.invalidateTreeHolder(n, minLevel, playerIndex, zoom);
                            }
                        }
                    }
                }
            }
        }
    }

    // A crown spans up to 5x5 chunks, so a bake draws neighbours' trees too and re-bakes the neighbours ours spill into.
    @Unique
    private void plz$bakeTrees(IsoChunk c, int playerIndex, float zoom) {
        FBORenderChunk rc = FBORenderChunkManager.instance.renderChunk;
        int minLevel = rc.getMinLevel();
        if (minLevel < 0) {
            return;
        }

        PLZTreeBakePass pass = this.plz$treePass;
        pass.begin(this.cell, c, rc, playerIndex, zoom, minLevel);

        for (int slot = 0; slot < PLZTreeBakePass.SLOTS; slot++) {
            IsoChunk source = pass.sourceAt(slot);
            if (source != null && pass.sourceIsUsable(slot, source)) {
                this.plz$bakeTreesFrom(pass, slot, source);
            }
        }

        pass.reconcileExports();
        pass.finish();
    }

    @Unique
    private void plz$bakeTreesFrom(PLZTreeBakePass pass, int slot, IsoChunk source) {
        boolean own = slot == PLZTreeBakePass.OWN_SLOT;
        int from = Math.max(pass.minLevel, source.minLevel);
        int to = Math.min(pass.topLevel, source.maxLevel);

        for (int z = from; z <= to; z++) {
            IsoGridSquare[] squares = source.squares[source.squaresIndexOfLevel(z)];
            FBORenderCutaways.ChunkLevelData levelData = source.getCutawayDataForLevel(z);

            for (int i = 0; i < squares.length; i++) {
                IsoGridSquare square = squares[i];
                if (square == null || !square.has(IsoObjectType.tree)) {
                    continue;
                }

                IsoTree tree = square.getTree();
                if (tree == null || !this.plz$treeIsBakeable(pass, tree, square, levelData, own)) {
                    continue;
                }

                if (!this.plz$treeReachesTexture(pass, tree, own)) {
                    continue;
                }

                if (!own) {
                    // A neighbour's square is not lit as part of this chunk's own pass.
                    square.cacheLightInfo();
                }

                this.plz$drawBakedTree(pass, tree, square, z);
            }
        }
    }

    @Unique
    private boolean plz$treeIsBakeable(
        PLZTreeBakePass pass, IsoTree tree, IsoGridSquare square, FBORenderCutaways.ChunkLevelData levelData, boolean own
    ) {
        return this.plz$treeBakes(tree, pass.aiming)
            && (!own || tree.getRenderInfo(pass.playerIndex).layer == ObjectRenderLayer.MinusFloor)
            && levelData.shouldRenderSquare(pass.playerIndex, square)
            && !FBORenderOcclusion.getInstance().isOccluded(square.x, square.y, square.z)
            && !FBORenderCutaways.getInstance().isForceRenderSquare(pass.playerIndex, square);
    }

    @Unique
    private boolean plz$treeReachesTexture(PLZTreeBakePass pass, IsoTree tree, boolean own) {
        if (!this.plz$treeRect(tree, pass.chunk.wx, pass.chunk.wy, pass.goX, pass.yoff, this.plz$treeRect)) {
            return false;
        }

        if (!own) {
            return PLZTreeBake.needsCopy(this.plz$treeRect, this.plz$treeOwnTexture, this.plz$treeOtherTexture);
        }

        pass.recordExports(this.plz$treeRect, this.plz$treeOwnTexture, this.plz$treeOtherTexture, tree);
        // Drawn into the texture rather than per frame from here on, whether or not it lands here.
        tree.renderFlag = false;
        return PLZTreeBake.overlaps(this.plz$treeRect, this.plz$treeOwnTexture);
    }

    // Depth rises up the crown, so an upper floor behind the tree no longer slices through it.
    @Unique
    private void plz$drawBakedTree(PLZTreeBakePass pass, IsoTree tree, IsoGridSquare square, int z) {
        if (square.getLightInfo(pass.playerIndex) == null) {
            return;
        }

        float base = IsoDepthHelper.getSquareDepthData(pass.camX, pass.camY, square.x + 0.99F, square.y + 0.99F, z).depthStart
            - IsoDepthHelper.getChunkDepthData(
                PZMath.fastfloor(pass.camX / 8.0F), PZMath.fastfloor(pass.camY / 8.0F), pass.chunk.wx, pass.chunk.wy, z).depthStart;
        if (base < 0.0F) {
            return;
        }

        ColorInfo light = this.sanitizeLightInfo(pass.playerIndex, square);
        boolean unlit = tree.getSprite().getProperties().has(IsoFlagType.unlit);
        float r = unlit ? 1.0F : light.r;
        float g = unlit ? 1.0F : light.g;
        float b = unlit ? 1.0F : light.b;

        PLZTreeBake.Drawer drawer = pass.drawer();
        IsoDirections dir = tree.getForwardIsoDirection();
        PLZTreeBake.Rect sprite = this.plz$treeRect;
        this.plz$addTreeTexture(drawer, tree.getSprite().getTextureForCurrentFrame(dir, tree),
            sprite.x0, sprite.y0, sprite.ground, base, r, g, b, pass.tileScale);

        if (tree.attachedAnimSprite == null) {
            return;
        }

        for (int k = 0; k < tree.attachedAnimSprite.size(); k++) {
            IsoSpriteInstance attached = tree.attachedAnimSprite.get(k);
            this.plz$addTreeTexture(drawer, attached.parentSprite.getTextureForCurrentFrame(dir, tree),
                sprite.x0, sprite.y0, sprite.ground, base, r, g, b, pass.tileScale);
        }
    }

    @Unique
    private void plz$addTreeTexture(
        PLZTreeBake.Drawer drawer, Texture texture, float sx, float sy, float ground, float base, float r, float g, float b, int tileScale
    ) {
        if (texture != null && texture.isReady() && texture.getTextureId() != null) {
            float scale = PLZTreeBake.spriteScale(tileScale, texture.getWidthOrig(), texture.getHeightOrig());
            float x0 = sx + texture.getOffsetX() * scale;
            float y0 = sy + texture.getOffsetY() * scale;
            float x1 = x0 + texture.getWidth() * scale;
            float y1 = y0 + texture.getHeight() * scale;
            drawer.add(texture, x0, y0, x1, y1, PLZTreeBake.depthAtRow(base, ground, y0, tileScale), PLZTreeBake.depthAtRow(base, ground, y1, tileScale), r, g, b, 1.0F);
        }
    }

    @Unique
    private static boolean plz$treeTextureReady(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        if (sprite == null) {
            return true;
        }

        Texture texture = sprite.getTextureForCurrentFrame(object.getDir(), object);
        return texture != null && texture.isReady();
    }

    @Unique
    private boolean plz$isTranslucentTree(IsoObject object, boolean isAiming) {
        IsoGridSquare square = object.square;
        square.IsOnScreen();
        if (!isAiming
            && (
                square.x < PZMath.fastfloor(IsoCamera.frameState.camCharacterX)
                    || square.y < PZMath.fastfloor(IsoCamera.frameState.camCharacterY)
                    || IsoCamera.frameState.camCharacterSquare == null
            )) {
            return false;
        }

        float sx = square.cachedScreenX - IsoCamera.frameState.offX;
        float sy = square.cachedScreenY - IsoCamera.frameState.offY;
        IsoCell cell = IsoWorld.instance.currentCell;
        return cell.isInStencil(sx, sy);
    }

    @Unique
    private static float plz$curtainDepthNudge(IsoCurtain curtain) {
        if (PLZPerf.CURTAIN_DEPTH_NUDGE <= 0.0F) {
            return 0.0F;
        } else if (!PLZPerf.WINDOWS_IN_CHUNK_TEXTURE && !PLZPerf.TRANSLUCENT_TILES_IN_CHUNK_TEXTURE) {
            return 0.0F;
        } else {
            IsoObjectType type = curtain.getType();
            if (type != IsoObjectType.curtainN && type != IsoObjectType.curtainW) {
                return 0.0F;
            } else {
                return curtain.getSpriteModel() != null ? 0.0F : PLZPerf.CURTAIN_DEPTH_NUDGE;
            }
        }
    }

    @Unique
    private static void plz$renderCurtainNudged(IsoCurtain curtain, IsoGridSquare square, ColorInfo lightInfo, float nudge) {
        float dx = curtain.getType() == IsoObjectType.curtainW ? nudge : 0.0F;
        float dy = curtain.getType() == IsoObjectType.curtainN ? nudge : 0.0F;
        float offsetX = curtain.offsetX;
        float offsetY = curtain.offsetY;
        curtain.offsetX = curtain.offsetX + IsoUtils.XToScreen(dx, dy, 0.0F, 0);
        curtain.offsetY = curtain.offsetY + IsoUtils.YToScreen(dx, dy, 0.0F, 0);

        try {
            curtain.render(square.x + dx, square.y + dy, square.z, lightInfo, true, false, null);
        } finally {
            curtain.offsetX = offsetX;
            curtain.offsetY = offsetY;
        }
    }

    // A deferred texture re-reads its cached square lists next frame, so they cannot be cleared.
    @Unique
    private static boolean plz$keepPerFrameLists() {
        return PLZBakeScheduler.active();
    }

    @Unique
    private void plz$updateChunkLightingBudgeted(int playerIndex, PLZFBOPerPlayerDataAccess perPlayerData1, int budget) {
        if (perPlayerData1.plzLightingUpdateCounter() != LightingJNI.getUpdateCounter(playerIndex)) {
            perPlayerData1.plzSetLightingUpdateCounter(LightingJNI.getUpdateCounter(playerIndex));
            this.plz$queueDirtyLevels(playerIndex, perPlayerData1);
        }

        PLZLightRefresh backlog = this.plz$lightBacklogs[playerIndex];
        if (!backlog.isEmpty()) {
            backlog.drain(budget, (chunk, level) -> this.plz$refreshQueuedLevel(playerIndex, perPlayerData1, (IsoChunk)chunk, level));
        }
    }

    @Unique
    private void plz$updateLightUrgency(IsoPlayer player) {
        if (!PLZPerf.URGENT_LIGHTING) {
            this.plz$torchZone.clear();
            this.plz$lightEventUntilMs = 0L;
            this.plz$playerChunkX = Integer.MIN_VALUE;
            return;
        }

        if (Core.dirtyGlobalLightsCount > 0) {
            this.plz$lightEvent();
        }

        this.plz$playerChunkX = player == null ? Integer.MIN_VALUE : PZMath.fastfloor(player.getX()) >> 3;
        this.plz$playerChunkY = player == null ? Integer.MIN_VALUE : PZMath.fastfloor(player.getY()) >> 3;
        this.plz$torchZone.expire(this.currentTimeMillis);
        this.plz$torches.clear();
        LightingJNI.getTorches(this.plz$torches);
        for (int i = 0; i < this.plz$torches.size(); i++) {
            IsoGameCharacter.TorchInfo torch = this.plz$torches.get(i);
            this.plz$torchZone.light(torch.id, torch.x, torch.y, torch.dist, this.currentTimeMillis);
        }

        this.plz$torches.clear();
    }

    // A switched light, placed lamp, power or night change, or revealed room: the lit result trails by a few frames.
    @Unique
    private void plz$lightEvent() {
        if (PLZPerf.URGENT_LIGHTING) {
            this.plz$lightEventUntilMs = this.currentTimeMillis + PLZTorchZone.LINGER_MS;
        }
    }

    @Unique
    private int plz$lightUrgency(IsoChunk c) {
        if (this.plz$torchZone.covers(c.wx, c.wy)) {
            return PLZBakeScheduler.LIGHT_TORCH;
        }

        if (this.currentTimeMillis < this.plz$lightEventUntilMs
            || this.plz$playerChunkX != Integer.MIN_VALUE
                && Math.abs(c.wx - this.plz$playerChunkX) <= 2
                && Math.abs(c.wy - this.plz$playerChunkY) <= 2) {
            return PLZBakeScheduler.LIGHT_EVENT;
        }

        return PLZBakeScheduler.LIGHT_DRIFT;
    }

    // Oldest-lit chunks first, so a chunk does not sit stale while newer ones keep jumping it.
    @Unique
    private void plz$queueDirtyLevels(int playerIndex, PLZFBOPerPlayerDataAccess perPlayerData1) {
        PLZLightRefresh backlog = this.plz$lightBacklogs[playerIndex];
        this.sortedChunks.clear();
        PZArrayUtil.addAll(this.sortedChunks, perPlayerData1.plzOnScreenChunks());
        this.sortedChunks.sort(Comparator.comparingInt(a -> a.lightingUpdateCounter));

        for (int i = 0; i < this.sortedChunks.size(); i++) {
            IsoChunk chunk = this.sortedChunks.get(i);
            boolean nearTorch = this.plz$torchZone.covers(chunk.wx, chunk.wy);
            boolean queued = false;

            for (int z = chunk.minLevel; z <= chunk.maxLevel; z++) {
                if (this.plz$isChunkLevelLightingDirty(playerIndex, chunk, z)) {
                    if (nearTorch) {
                        this.plz$cacheChunkLevelLightInfo(playerIndex, chunk, z);
                    } else if (!plz$lightInfoReadThisFrame(playerIndex, chunk, z)) {
                        backlog.offer(chunk, z);
                    }
                    queued = true;
                }
            }

            if (queued) {
                chunk.lightingUpdateCounter = perPlayerData1.plzLightingUpdateCounter();
            }
        }
    }

    // False for work that no longer applies, so it does not spend the frame's budget.
    @Unique
    private boolean plz$refreshQueuedLevel(int playerIndex, PLZFBOPerPlayerDataAccess perPlayerData1, IsoChunk chunk, int level) {
        return perPlayerData1.plzOnScreenChunks().contains(chunk) && this.plz$cacheChunkLevelLightInfo(playerIndex, chunk, level);
    }

    @Unique
    private boolean plz$isChunkLevelLightingDirty(int playerIndex, IsoChunk chunk, int level) {
        if (level >= chunk.minLevel && level <= chunk.maxLevel) {
            FBORenderLevels renderLevels = chunk.getRenderLevels(playerIndex);
            return renderLevels.isOnScreen(level) && LightingJNI.getChunkDirty(playerIndex, chunk.wx, chunk.wy, level + 32);
        } else {
            return false;
        }
    }

    // prepareChunksForUpdating already re-read every dirty level of this generation earlier in the frame.
    @Unique
    private static boolean plz$lightInfoReadThisFrame(int playerIndex, IsoChunk chunk, int level) {
        int[][] stamps = ((PLZIsoChunkAccess) (Object) chunk).plzLightInfoFrame();
        if (!PLZPerf.LIGHT_INFO_ONCE_PER_FRAME || stamps == null || level < -32 || level >= 32) {
            return false;
        }

        int[] r = stamps[playerIndex * 64 + level + 32];
        if (r == null) {
            return false;
        }

        int frame = IsoWorld.instance.getFrameNo();
        for (int i = 0; i < r.length; i++) {
            if (r[i] == frame) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private static void plz$cacheLightInfo(int playerIndex, IsoChunk chunk, int level, int index, IsoGridSquare square) {
        if (PLZPerf.LIGHT_INFO_ONCE_PER_FRAME && level >= -32 && level < 32) {
            int[][] stamps = ((PLZIsoChunkAccess) (Object) chunk).plzLightInfoFrame();
            if (stamps == null) {
                stamps = new int[256][];
                ((PLZIsoChunkAccess) (Object) chunk).plzSetLightInfoFrame(stamps);
            }

            int row = playerIndex * 64 + level + 32;
            int[] r = stamps[row];
            if (r == null) {
                r = new int[64];
                Arrays.fill(r, Integer.MIN_VALUE);
                stamps[row] = r;
            }

            int frame = IsoWorld.instance.getFrameNo();
            if (r[index] == frame) {
                plzLightInfoSkipped++;
                return;
            }

            r[index] = frame;
        }

        square.cacheLightInfo();
    }

    @Unique
    private boolean plz$cacheChunkLevelLightInfo(int playerIndex, IsoChunk chunk, int level) {
        if (level >= chunk.minLevel && level <= chunk.maxLevel) {
            FBORenderCutaways.ChunkLevelData levelData = chunk.getCutawayDataForLevel(level);
            IsoGridSquare[] squares = chunk.squares[chunk.squaresIndexOfLevel(level)];

            for (int i = 0; i < squares.length; i++) {
                IsoGridSquare square = squares[i];
                if (square != null && levelData.shouldRenderSquare(playerIndex, square)) {
                    plz$cacheLightInfo(playerIndex, chunk, level, i, square);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Unique
    private boolean plz$hasDirtyChunkTexturesForOcclusion(int playerIndex) {
        if (PLZPerf.OCCLUSION_SKIP_LIGHTING_ONLY) {
            float zoom = Core.getInstance().getZoom(playerIndex);
            PLZFBOPerPlayerDataAccess perPlayerData1 = this.plz$ppd[playerIndex];
            boolean lightingOnly = false;

            for (int i = 0; i < perPlayerData1.plzOnScreenChunks().size(); i++) {
                IsoChunk c = perPlayerData1.plzOnScreenChunks().get(i);
                FBORenderLevels renderLevels = c.getRenderLevels(playerIndex);

                for (int z = c.minLevel; z <= c.maxLevel; z++) {
                    if (renderLevels.isOnScreen(z) && renderLevels.isDirty(z, zoom)) {
                        if (renderLevels.isDirty(z, -33L, zoom)) {
                            return true;
                        }

                        lightingOnly = true;
                    }
                }
            }

            if (lightingOnly) {
                plzOcclusionRebuildsSkipped++;
            }

            return false;
        } else {
            return this.hasAnyDirtyChunkTextures(playerIndex);
        }
    }

    @Unique
    private static long plz$exactOccluderMask(int playerIndex, IsoChunk chunk, int level, FBORenderCutaways.ChunkLevelData levelData) {
        IsoGridSquare[] squares = chunk.getSquaresForLevel(level);
        long mask = 0L;

        for (int i = 0; i < 64; i++) {
            IsoGridSquare square = squares[i];
            if (square != null
                && levelData.shouldRenderSquare(playerIndex, square)
                && (square.getVisionMatrix(0, 0, -1) || chunk.getGridSquare(i % 8, i / 8, square.z - 1) == null)) {
                mask |= 1L << i;
            }
        }

        return mask;
    }

    @Unique
    private void plz$renderMinusFloorInner(IsoObject object) {
        int playerIndex = IsoCamera.frameState.playerIndex;
        ObjectRenderInfo renderInfo = object.getRenderInfo(playerIndex);
        IsoObjectType t = IsoObjectType.MAX;
        if (object.sprite != null) {
            t = object.sprite.getTileType();
        }

        boolean isWestDoorOrWall = t == IsoObjectType.doorFrW || t == IsoObjectType.doorW || object.sprite != null && object.sprite.cutW;
        boolean isNorthDoorOrWall = t == IsoObjectType.doorFrN || t == IsoObjectType.doorN || object.sprite != null && object.sprite.cutN;
        IndieGL.glAlphaFunc(516, 0.0F);
        object.setAlphaAndTarget(playerIndex, renderInfo.targetAlpha);
        IsoGridSquare.setBlendFunc();
        if (object.sprite != null && (isWestDoorOrWall || isNorthDoorOrWall)) {
            if (DebugOptions.instance.terrain.renderTiles.isoGridSquare.doorsAndWalls.getValue()) {
                this.renderMinusFloor_DoorOrWall(object);
            }
        } else if (DebugOptions.instance.terrain.renderTiles.isoGridSquare.objects.getValue()) {
            this.renderMinusFloor_NotDoorOrWall(object);
        }
    }
}

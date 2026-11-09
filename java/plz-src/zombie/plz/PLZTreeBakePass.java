package zombie.plz;

import java.util.Arrays;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.math.PZMath;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.fboRenderChunk.FBORenderChunk;
import zombie.iso.fboRenderChunk.FBORenderChunkManager;
import zombie.iso.fboRenderChunk.FBORenderLevels;
import zombie.iso.objects.IsoTree;
import zombie.plz.access.PLZIsoChunkAccess;

// Per-bake state of the FBORenderCell tree pass, reused between bakes because the neighbourhood arrays are measurable.
public final class PLZTreeBakePass {
    // A crown reaches at most two chunks in each direction, so the neighbourhood is 5x5.
    public static final int REACH = 2;
    public static final int SPAN = REACH * 2 + 1;
    public static final int SLOTS = SPAN * SPAN;
    public static final int OWN_SLOT = SLOTS / 2;

    private final IsoChunk[] sources = new IsoChunk[SLOTS];
    private final int[] exports = new int[SLOTS];
    private final PLZTreeBake.Rect ownTexture;
    private final PLZTreeBake.Rect otherTexture;

    public IsoChunk chunk;
    public int playerIndex;
    public float zoom;
    public int minLevel;
    public int topLevel;
    public float goX;
    public float yoff;
    public int camX;
    public int camY;
    public int tileScale;
    public boolean aiming;
    private PLZTreeBake.Drawer drawer;

    public PLZTreeBakePass(PLZTreeBake.Rect ownTexture, PLZTreeBake.Rect otherTexture) {
        this.ownTexture = ownTexture;
        this.otherTexture = otherTexture;
    }

    public static boolean treeAiming(int playerIndex) {
        IsoPlayer player = IsoPlayer.getPlayer(playerIndex);
        return player.isAnyAimKeyDown() || player.getVehicle() != null;
    }

    public static float textureYOffset(int minLevel, int topLevel) {
        return (topLevel - minLevel + 1) * FBORenderChunk.PIXELS_PER_LEVEL
            + minLevel * FBORenderChunk.PIXELS_PER_LEVEL
            + FBORenderLevels.extraHeightForJumboTrees(minLevel, topLevel);
    }

    public static boolean chunkTextureRect(IsoChunk n, IsoChunk c, int minLevel, float zoom, float goX, float yoff, PLZTreeBake.Rect out) {
        if (minLevel >= 0 && minLevel >= n.minLevel && minLevel <= n.maxLevel) {
            int topLevel = Math.min(minLevel + 1, n.maxLevel);
            int scale = FBORenderLevels.getTextureScale(zoom);
            float w = (float)FBORenderLevels.calculateTextureWidthForLevels(minLevel, topLevel, zoom) / scale;
            float h = (float)FBORenderLevels.calculateTextureHeightForLevels(minLevel, topLevel, zoom) / scale;
            PLZTreeBake.neighbourTextureRect(n.wx - c.wx, n.wy - c.wy, goX, yoff, textureYOffset(minLevel, topLevel), w, h, Core.tileScale, out);
            return true;
        } else {
            return false;
        }
    }

    public static void invalidateTreeHolder(IsoChunk n, int minLevel, int playerIndex, float zoom) {
        FBORenderLevels renderLevels = n.getRenderLevels(playerIndex);
        if (renderLevels.getFBOForLevel(minLevel, zoom) != null) {
            renderLevels.invalidateLevel(minLevel, 4096L);
        }
    }

    public void begin(IsoCell cell, IsoChunk c, FBORenderChunk rc, int playerIndex, float zoom, int minLevel) {
        this.chunk = c;
        this.playerIndex = playerIndex;
        this.zoom = zoom;
        this.minLevel = minLevel;
        this.topLevel = rc.getTopLevel();
        this.goX = FBORenderChunkManager.instance.getXOffset();
        this.yoff = FBORenderChunkManager.instance.getYOffset();
        this.camX = PZMath.fastfloor(IsoCamera.frameState.camCharacterX);
        this.camY = PZMath.fastfloor(IsoCamera.frameState.camCharacterY);
        this.tileScale = Core.tileScale;
        this.aiming = treeAiming(playerIndex);
        this.drawer = null;
        Arrays.fill(this.exports, 0);

        float scale = rc.highRes ? 2.0F : 1.0F;
        PLZTreeBake.ownTextureRect(this.goX, rc.w / scale, rc.h / scale, this.ownTexture);

        for (int slot = 0; slot < SLOTS; slot++) {
            this.sources[slot] = slot == OWN_SLOT ? c : cell.getChunk(c.wx + slot % SPAN - REACH, c.wy + slot / SPAN - REACH);
        }
    }

    public IsoChunk sourceAt(int slot) {
        return this.sources[slot];
    }

    // A neighbour only matters if it has been lit and its texture rect can be placed.
    public boolean sourceIsUsable(int slot, IsoChunk source) {
        if (slot == OWN_SLOT) {
            return true;
        }

        return !source.lightingNeverDone[this.playerIndex]
            && chunkTextureRect(source, this.chunk, this.minLevel, this.zoom, this.goX, this.yoff, this.otherTexture);
    }

    // Order-sensitive mix rather than a sum: two trees swapping places must change the fingerprint.
    public void recordExports(PLZTreeBake.Rect sprite, PLZTreeBake.Rect own, PLZTreeBake.Rect other, IsoTree tree) {
        int id = System.identityHashCode(tree);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (slot == OWN_SLOT || this.sources[slot] == null) {
                continue;
            }

            if (chunkTextureRect(this.sources[slot], this.chunk, this.minLevel, this.zoom, this.goX, this.yoff, other)
                && PLZTreeBake.needsCopy(sprite, other, own)) {
                this.exports[slot] = this.exports[slot] * 31 + id;
            }
        }
    }

    // Re-bakes the neighbours whose share of this chunk's trees changed since last time.
    public void reconcileExports() {
        int[] last = ((PLZIsoChunkAccess) (Object) this.chunk).plzTreeExportFp();
        if (last == null) {
            last = new int[SLOTS];
            ((PLZIsoChunkAccess) (Object) this.chunk).plzSetTreeExportFp(last);
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.exports[slot] == last[slot]) {
                continue;
            }

            last[slot] = this.exports[slot];
            IsoChunk neighbour = this.sources[slot];
            if (neighbour != null && neighbour != this.chunk && this.minLevel >= neighbour.minLevel && this.minLevel <= neighbour.maxLevel) {
                invalidateTreeHolder(neighbour, this.minLevel, this.playerIndex, this.zoom);
            }
        }
    }

    public PLZTreeBake.Drawer drawer() {
        if (this.drawer == null) {
            this.drawer = PLZTreeBake.alloc();
        }

        return this.drawer;
    }

    public void finish() {
        if (this.drawer != null) {
            SpriteRenderer.instance.drawGeneric(this.drawer);
            this.drawer = null;
        }

        Arrays.fill(this.sources, null);
        this.chunk = null;
    }
}

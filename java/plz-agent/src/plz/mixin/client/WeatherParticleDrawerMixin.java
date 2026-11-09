package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import gnu.trove.list.array.TIntArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.opengl.VBORenderer;
import zombie.core.textures.Texture;
import zombie.iso.weather.fx.WeatherParticleDrawer;
import zombie.plz.PLZRainTiles;
import zombie.plz.access.PLZWeatherParticleDrawerAccess;

@Mixin(WeatherParticleDrawer.class)
abstract class WeatherParticleDrawerMixin implements PLZWeatherParticleDrawerAccess {
    @Shadow
    private ByteBuffer particleBuffer;

    @Shadow
    @Final
    private ArrayList<Texture> textures;

    @Shadow
    @Final
    private TIntArrayList[] particlesByTexture;

    @Unique
    private ArrayList<PLZRainTiles.Tile> plz$tiles;

    @Unique
    private PLZRainTiles.Tile plz$tile;

    @Unique
    private PLZRainTiles.Gl plz$gl;

    @Unique
    private boolean plz$skipQuad;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$tiles = new ArrayList<>();
        this.plz$gl = new PLZRainTiles.Gl();
    }

    @Override
    public PLZRainTiles.Tile plzBeginTile() {
        this.plz$tile = PLZRainTiles.begin(this.particlesByTexture);
        return this.plz$tile;
    }

    @Override
    public void plzEndTile() {
        if (this.plz$tile != null) {
            PLZRainTiles.end(this.plz$tile, this.particlesByTexture);
            this.plz$tiles.add(this.plz$tile);
            this.plz$tile = null;
        }
    }

    @Unique
    private boolean plz$tiled(int textureIndex, int position) {
        for (int t = 0; t < this.plz$tiles.size(); t++) {
            if (this.plz$tiles.get(t).covers(textureIndex, position)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private boolean plz$allTiled(int textureIndex) {
        int n = this.particlesByTexture[textureIndex].size();
        int covered = 0;

        for (int t = 0; t < this.plz$tiles.size(); t++) {
            covered += this.plz$tiles.get(t).rangeSize(textureIndex);
        }

        return covered >= n;
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/core/opengl/VBORenderer;getInstance()Lzombie/core/opengl/VBORenderer;",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void plz$drawTiles(CallbackInfo ci) {
        this.plz$gl.draw(this.plz$tiles, this.particleBuffer, this.textures, this.particlesByTexture);
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lgnu/trove/list/array/TIntArrayList;isEmpty()Z")
    )
    private boolean plz$skipTiledTexture(TIntArrayList positions, Operation<Boolean> original, @Local(ordinal = 2) int i) {
        return original.call(positions) || this.plz$allTiled(i);
    }

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lzombie/core/opengl/VBORenderer;addQuad(FFFFFFFFFFFFFFFFFFFFF)V")
    )
    private void plz$noteTiled(CallbackInfo ci, @Local(ordinal = 2) int i, @Local(ordinal = 3) int j) {
        this.plz$skipQuad = this.plz$tiled(i, j);
    }

    // Runs per particle per frame: a wrap or a @Local redirect allocates here, so the inject above decides.
    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lzombie/core/opengl/VBORenderer;addQuad(FFFFFFFFFFFFFFFFFFFFF)V")
    )
    private void plz$skipTiledParticle(
        VBORenderer vbor,
        float x1, float y1, float u1, float v1,
        float x2, float y2, float u2, float v2,
        float x3, float y3, float u3, float v3,
        float x4, float y4, float u4, float v4,
        float z, float r, float g, float b, float a
    ) {
        if (!this.plz$skipQuad) {
            vbor.addQuad(x1, y1, u1, v1, x2, y2, u2, v2, x3, y3, u3, v3, x4, y4, u4, v4, z, r, g, b, a);
        }
    }

    @Inject(
        method = "startFrame",
        at = @At(value = "INVOKE", target = "Ljava/nio/ByteBuffer;clear()Ljava/nio/ByteBuffer;", shift = At.Shift.AFTER)
    )
    private void plz$resetTiles(CallbackInfo ci) {
        this.plz$tiles.clear();
        this.plz$tile = null;
    }
}

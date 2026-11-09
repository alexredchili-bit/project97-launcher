package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.PerformanceSettings;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderSettings;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureFBO;
import zombie.iso.BuildingDef;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.weather.fx.IsoWeatherFX;
import zombie.iso.weather.fx.WeatherFxMask;
import zombie.plz.PLZPerf;
import zombie.plz.access.PLZWeatherFxMaskPlayerFxMaskAccess;

@Mixin(WeatherFxMask.class)
abstract class WeatherFxMaskMixin {
    @Shadow
    private static TextureFBO fboMask;

    @Shadow
    @Final
    private static WeatherFxMask.PlayerFxMask[] playerMasks;

    @Shadow
    private static boolean debugMask;

    @Shadow
    private static boolean debugMaskAndParticles;

    @Shadow
    private static boolean isOnScreen(int x, int y, int z) {
        throw new AssertionError();
    }

    @Shadow
    public static void addMaskLocation(IsoGridSquare square, int x, int y, int z) {
        throw new AssertionError();
    }

    @Unique
    private static float plz$fxScale() {
        if (IsoPlayer.numPlayers <= 1) {
            int pct = PLZPerf.WEATHER_FX_SCALE_PCT;
            return pct < 100 && pct >= 10 ? pct / 100.0F : 1.0F;
        } else {
            return 1.0F;
        }
    }

    @Unique
    private static int plz$fxSize(int screenPixels) {
        float s = plz$fxScale();
        return s == 1.0F ? screenPixels : Math.max(1, Math.round(screenPixels * s));
    }

    @Unique
    private static void plz$fxViewport() {
        if (plz$fxScale() != 1.0F && fboMask != null) {
            SpriteRenderer.instance.glViewport(0, 0, fboMask.getTexture().getWidth(), fboMask.getTexture().getHeight());
        }
    }

    @Unique
    private static boolean plz$scanIsNoop(PLZWeatherFxMaskPlayerFxMaskAccess playerFxMask) {
        if (!PLZPerf.WEATHER_MASK_IDLE_SKIP) {
            return false;
        } else {
            return playerFxMask.plzRequiresUpdate() && playerFxMask.plzHasMaskToDraw()
                ? playerFxMask.plzPlayer() != null && playerFxMask.plzPlayer().getBuilding() == null && playerFxMask.plzCurIsoWorldRegion() == null
                : true;
        }
    }

    @Unique
    private static boolean plz$scanBuildingOnly(PLZWeatherFxMaskPlayerFxMaskAccess playerFxMask, int nPlayer, int playerZ) {
        if (!PLZPerf.WEATHER_MASK_IDLE_SKIP) {
            return false;
        }

        IsoPlayer player = playerFxMask.plzPlayer();
        if (player == null || playerFxMask.plzCurIsoWorldRegion() != null || player.getBuilding() == null || player.getBuilding().getDef() == null) {
            return false;
        }

        BuildingDef def = player.getBuilding().getDef();
        IsoChunkMap chunkMap = IsoWorld.instance.getCell().getChunkMap(nPlayer);
        int x1 = def.getX() - 1;
        int y1 = def.getY() - 1;
        int x2 = def.getX2() + 1;
        int y2 = def.getY2() + 1;

        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                if (isOnScreen(x, y, playerZ)) {
                    addMaskLocation(chunkMap.getGridSquare(x, y, playerZ), x, y, playerZ);
                }
            }
        }

        return true;
    }

    @Unique
    private static boolean plz$maskIdle(int nPlayer, IsoWeatherFX fx) {
        if (!PLZPerf.WEATHER_MASK_IDLE_SKIP) {
            return false;
        }

        if (debugMask || debugMaskAndParticles) {
            return false;
        }

        if (!RenderSettings.getInstance().getPlayerSettings(nPlayer).isExterior()) {
            return false;
        }

        if (fx.hasCloudsToRender()) {
            return false;
        }

        return fx.hasFogToRender() && PerformanceSettings.fogQuality == 2
            ? false
            : Core.getInstance().getOptionRenderPrecipitation() != 1 || !fx.hasPrecipitationToRender();
    }

    @ModifyExpressionValue(method = "checkFbos", at = @At(value = "INVOKE", target = "Lzombie/core/Core;getScreenWidth()I"))
    private static int plz$fboWidth(int screenWidth) {
        return plz$fxSize(screenWidth);
    }

    @ModifyExpressionValue(method = "checkFbos", at = @At(value = "INVOKE", target = "Lzombie/core/Core;getScreenHeight()I"))
    private static int plz$fboHeight(int screenHeight) {
        return plz$fxSize(screenHeight);
    }

    @Inject(method = "scanForTiles", at = @At("HEAD"), cancellable = true)
    private static void plz$skipIdleScan(int nPlayer, CallbackInfo ci) {
        if (plz$scanIsNoop((PLZWeatherFxMaskPlayerFxMaskAccess) playerMasks[nPlayer])) {
            ci.cancel();
        }
    }

    @Inject(
        method = "scanForTiles",
        at = @At(value = "INVOKE", target = "Lzombie/GameProfiler;profile(Ljava/lang/String;)Lzombie/GameProfiler$ProfileArea;", ordinal = 1),
        cancellable = true
    )
    private static void plz$scanBuildingInstead(int nPlayer, CallbackInfo ci, @Local(index = 3) int playerZ) {
        if (plz$scanBuildingOnly((PLZWeatherFxMaskPlayerFxMaskAccess) playerMasks[nPlayer], nPlayer, playerZ)) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(
        method = "renderFxMask",
        at = @At(value = "FIELD", target = "Lzombie/iso/weather/fx/IsoWeatherFX;instance:Lzombie/iso/weather/fx/IsoWeatherFX;", ordinal = 0)
    )
    private static IsoWeatherFX plz$keepFx(IsoWeatherFX fx, @Share("plzFx") LocalRef<IsoWeatherFX> plzFx) {
        plzFx.set(fx);
        return fx;
    }

    // Reached only when masking is on and there is a mask to draw; an exterior frame with nothing to layer skips the whole pass.
    @Inject(method = "renderFxMask", at = @At(value = "INVOKE", target = "Lzombie/GameProfiler;getInstance()Lzombie/GameProfiler;"), cancellable = true)
    private static void plz$skipIdleMask(int nPlayer, CallbackInfo ci, @Share("plzFx") LocalRef<IsoWeatherFX> plzFx) {
        if (plz$maskIdle(nPlayer, plzFx.get())) {
            ci.cancel();
        }
    }

    @Inject(
        method = "drawFxMask",
        at = @At(value = "INVOKE", target = "Lzombie/core/SpriteRenderer;glDoStartFrameFx(III)V", shift = At.Shift.AFTER)
    )
    private static void plz$maskViewport(int nPlayer, CallbackInfo ci) {
        plz$fxViewport();
    }

    @Inject(
        method = "drawFxLayered",
        at = @At(value = "INVOKE", target = "Lzombie/core/SpriteRenderer;glDoStartFrameFx(III)V", shift = At.Shift.AFTER)
    )
    private static void plz$layeredViewport(int nPlayer, boolean doClouds, boolean doFog, boolean doPrecip, CallbackInfo ci) {
        plz$fxViewport();
    }

    @WrapOperation(method = "drawFxLayered", at = @At(value = "INVOKE", target = "Lzombie/core/textures/Texture;rendershader2(FFFFIIIIFFFF)V"))
    private static void plz$scaledMaskSource(
        Texture texture,
        float x,
        float y,
        float w,
        float h,
        int sx,
        int sy,
        int sw,
        int sh,
        float r,
        float g,
        float b,
        float a,
        Operation<Void> original
    ) {
        original.call(texture, x, y, w, h, plz$fxSize(sx), plz$fxSize(sy), plz$fxSize(sw), plz$fxSize(sh), r, g, b, a);
    }

    @WrapOperation(
        method = "drawFxLayered",
        at = @At(value = "INVOKE", target = "Lzombie/core/SpriteRenderer;render(Lzombie/core/textures/Texture;FFFFFFFFFFFFFFFF)V")
    )
    private static void plz$scaledParticleUvs(
        SpriteRenderer renderer,
        Texture tex,
        float x,
        float y,
        float w,
        float h,
        float r,
        float g,
        float b,
        float a,
        float u1,
        float v1,
        float u2,
        float v2,
        float u3,
        float v3,
        float u4,
        float v4,
        Operation<Void> original,
        @Local(index = 8) int sx,
        @Local(index = 9) int sy,
        @Local(index = 10) int sw,
        @Local(index = 11) int sh
    ) {
        float plzS = plz$fxScale();
        float sx1 = sx * plzS / tex.getWidthHW();
        float ey1 = sy * plzS / tex.getHeightHW();
        float ex1 = (sx + sw) * plzS / tex.getWidthHW();
        float sy1 = (sy + sh) * plzS / tex.getHeightHW();
        original.call(renderer, tex, x, y, w, h, r, g, b, a, sx1, sy1, ex1, sy1, ex1, ey1, sx1, ey1);
    }
}

package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.GameTime;
import zombie.MainThread;
import zombie.ZomboidFileSystem;
import zombie.characters.animals.AnimalPopulationManager;
import zombie.core.logger.ExceptionLogger;
import zombie.entity.GameEntityManager;
import zombie.inventory.types.MapItem;
import zombie.iso.ChunkSaveWorker;
import zombie.iso.IsoChunk;
import zombie.iso.IsoMetaCell;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoWorld;
import zombie.iso.SaveBufferMap;
import zombie.network.GameServer;
import zombie.plz.PLZPerf;
import zombie.savefile.PlayerDB;
import zombie.util.ByteBufferPooledObject;
import zombie.worldMap.WorldMapVisited;

@Mixin(ChunkSaveWorker.class)
abstract class ChunkSaveWorkerMixin {
    @Unique
    private static final int plz$HOTSAVE_STAGES = 9;

    @Unique
    private static long plz$lastHotsaveNs;

    @Unique
    private static boolean plz$hotsavePending;

    @Unique
    private int plz$hotsaveStage;

    @Shadow
    @Final
    private static SaveBufferMap saveBufferMap;

    @Shadow
    @Final
    public ConcurrentLinkedQueue<?> toSaveQueue;

    @Shadow
    private void HotsaveAncilliarySystems() {
        throw new AssertionError();
    }

    @Shadow
    private static void writeBufferToDisk(String outFilePath, ByteBufferPooledObject buffer) throws IOException {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$initStage(CallbackInfo ci) {
        this.plz$hotsaveStage = -1;
    }

    @Inject(method = "Update", at = @At("HEAD"))
    private void plz$continueStagedHotsave(IsoChunk aboutToLoad, CallbackInfo ci) {
        if (!GameServer.server && this.plz$hotsaveStage > 0) {
            this.plz$hotsaveStep();
        }
    }

    @WrapOperation(method = "Update", at = @At(value = "INVOKE", target = "Lzombie/iso/ChunkSaveWorker;HotsaveAncilliarySystems()V"))
    private void plz$deferHotsave(ChunkSaveWorker worker, Operation<Void> original) {
        plz$hotsavePending = true;
    }

    // A hot save held back by the interval still runs once it is due, not at some later drain.
    @Inject(method = "Update", at = @At("RETURN"))
    private void plz$runDueHotsave(IsoChunk aboutToLoad, CallbackInfo ci) {
        if (GameServer.server || !plz$hotsavePending || this.plz$hotsaveStage >= 0 || !this.toSaveQueue.isEmpty()) {
            return;
        }

        int interval = PLZPerf.HOTSAVE_INTERVAL_SEC;
        long now = System.nanoTime();
        if (interval > 0 && now - plz$lastHotsaveNs < interval * 1000000000L) {
            return;
        }

        plz$hotsavePending = false;
        plz$lastHotsaveNs = now;
        this.HotsaveAncilliarySystems();
    }

    @Inject(method = "HotsaveAncilliarySystems", at = @At("HEAD"), cancellable = true)
    private void plz$stagedHotsave(CallbackInfo ci) {
        if (PLZPerf.HOTSAVE_STAGED) {
            if (this.plz$hotsaveStage < 0) {
                saveBufferMap.clear();
                this.plz$hotsaveStage = 0;
                this.plz$hotsaveStep();
            }

            ci.cancel();
        }
    }

    @Unique
    private void plz$hotsaveStep() {
        if (this.plz$hotsaveStage >= 0) {
            int stage = this.plz$hotsaveStage++;
            MainThread.invokeOnMainThread(() -> {
                IsoMetaGrid mg = IsoWorld.instance.metaGrid;
                switch (stage) {
                    case 0:
                        mg.saveToSaveBufferMap(saveBufferMap, ZomboidFileSystem.instance.getFileNameInCurrentSave("map_meta.bin"), mg::save);
                        break;
                    case 1:
                        mg.saveToSaveBufferMap(saveBufferMap, ZomboidFileSystem.instance.getFileNameInCurrentSave("map_zone.bin"), mg::saveZone);
                        break;
                    case 2:
                        mg.saveToSaveBufferMap(saveBufferMap, ZomboidFileSystem.instance.getFileNameInCurrentSave("map_animals.bin"), mg::saveAnimalZones);
                        break;
                    case 3:
                        mg.saveCellsToSaveBufferMap(saveBufferMap, "metagrid", "metacell_%d_%d.bin", IsoMetaCell::save);
                        break;
                    case 4:
                        AnimalPopulationManager.getInstance().saveToBufferMap(saveBufferMap);
                        break;
                    case 5:
                        GameTime.instance.saveToBufferMap(saveBufferMap);
                        break;
                    case 6:
                        MapItem.SaveWorldMapToBufferMap(saveBufferMap);
                        break;
                    case 7:
                        WorldMapVisited.getInstance().saveToBufferMap(saveBufferMap);
                        break;
                    default:
                        GameEntityManager.saveToBufferMap(saveBufferMap);
                }
            });
            if (this.plz$hotsaveStage >= plz$HOTSAVE_STAGES) {
                this.plz$hotsaveStage = -1;
                if (PlayerDB.isAllow()) {
                    PlayerDB.getInstance().savePlayers();
                }

                try {
                    saveBufferMap.save((path, buffer) -> writeBufferToDisk(path, buffer));
                } catch (Exception e) {
                    ExceptionLogger.logException(e);
                }

                saveBufferMap.clear();
            }
        }
    }
}

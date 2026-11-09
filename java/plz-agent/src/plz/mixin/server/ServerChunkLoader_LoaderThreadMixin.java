package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.WorldReuserThread;
import zombie.network.ServerChunkLoader;
import zombie.network.ServerMap;
import zombie.plz.PLZMapScan;
import zombie.plz.access.PLZServerChunkLoaderAccess;

@Mixin(targets = "zombie.network.ServerChunkLoader$LoaderThread")
abstract class ServerChunkLoader_LoaderThreadMixin {
    @Shadow
    @Final
    ServerChunkLoader this$0;

    @Shadow
    @Final
    private LinkedBlockingQueue<ServerMap.ServerCell> toThread;

    @Shadow
    ArrayDeque<IsoGridSquare> isoGridSquareCache;

    @WrapOperation(
        method = "run",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/LinkedBlockingQueue;take()Ljava/lang/Object;")
    )
    private Object plz$takeOrScan(LinkedBlockingQueue<ServerMap.ServerCell> queue, Operation<Object> original) throws InterruptedException {
        while (true) {
            ServerMap.ServerCell cell = queue.poll(PLZMapScan.loaderWaitMs(), TimeUnit.MILLISECONDS);
            if (cell != null) {
                return cell;
            }

            if (PLZMapScan.wantsLoaderTime()) {
                this.plz$scanSlice();
            }
        }
    }

    @Unique
    private void plz$scanSlice() {
        if (this.isoGridSquareCache.size() < 10000) {
            IsoGridSquare.getSquaresForThread(this.isoGridSquareCache, 10000);
            IsoGridSquare.loadGridSquareCache = this.isoGridSquareCache;
        }

        long deadline = System.nanoTime() + PLZMapScan.workNanos();

        try {
            while (this.toThread.isEmpty() && System.nanoTime() < deadline) {
                long key = PLZMapScan.loaderNext();
                if (key == PLZMapScan.NONE) {
                    return;
                }

                if (key == PLZMapScan.BUSY) {
                    continue;
                }

                int wx = PLZMapScan.keyX(key);
                int wy = PLZMapScan.keyY(key);
                IsoChunk chunk = IsoChunkMap.chunkStore.poll();
                if (chunk == null) {
                    chunk = new IsoChunk((IsoCell)null);
                }

                try {
                    chunk.assignLoadID();
                    ByteBuffer pending = ((PLZServerChunkLoaderAccess)this.this$0).plzThreadSave().plzPendingChunkData(wx, wy);
                    chunk.wx = wx;
                    chunk.wy = wy;
                    if (pending == null) {
                        chunk.LoadFromDisk();
                    } else if (!chunk.LoadFromBuffer(wx, wy, pending)) {
                        throw new IOException("pending save of chunk " + wx + "," + wy + " did not load");
                    }

                    PLZMapScan.loaderChunk(chunk);
                } catch (Throwable t) {
                    PLZMapScan.loaderFailed(wx, wy, t);
                    if (t instanceof VirtualMachineError) {
                        PLZMapScan.fail(t);
                    }
                }

                WorldReuserThread.instance.addReuseChunk(chunk);
            }
        } catch (Throwable t) {
            PLZMapScan.fail(t);
        }
    }
}

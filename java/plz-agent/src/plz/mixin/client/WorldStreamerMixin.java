package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.nio.ByteBuffer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.logger.ExceptionLogger;
import zombie.iso.IsoChunk;
import zombie.iso.WorldStreamer;
import zombie.plz.PLZRecalcPool;
import zombie.plz.PLZStreamerWake;
import zombie.plz.access.PLZIsoChunkAccess;

@Mixin(WorldStreamer.class)
abstract class WorldStreamerMixin {
    @Shadow
    public Thread worldStreamer;

    @Inject(
        method = "updateMain",
        at = @At(value = "FIELD", target = "Lzombie/iso/WorldStreamer;chunkHeadMain:Lzombie/iso/IsoChunk;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER)
    )
    private void plz$wakeOnHeadMain(CallbackInfo ci) {
        PLZStreamerWake.signal();
    }

    @Inject(
        method = "threadLoop",
        at = @At(value = "FIELD", target = "Lzombie/iso/WorldStreamer;jobQueue:Ljava/util/concurrent/ConcurrentLinkedQueue;", opcode = Opcodes.GETFIELD, ordinal = 0)
    )
    private void plz$runRecalcRetries(CallbackInfo ci) {
        PLZRecalcPool.runRetries();
    }

    @Redirect(method = "threadLoop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V", ordinal = 0))
    private void plz$idlePending(long millis) throws InterruptedException {
        PLZStreamerWake.idle(millis);
    }

    @Redirect(method = "threadLoop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V", ordinal = 1))
    private void plz$idleNoJobs(long millis) throws InterruptedException {
        PLZStreamerWake.idle(millis);
    }

    @Redirect(method = "threadLoop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V", ordinal = 2))
    private void plz$idleInGame(long millis) throws InterruptedException {
        PLZStreamerWake.idle(millis);
    }

    // The thread body is a lambda, so the registration wraps the Runnable handed to the Thread.
    @ModifyArg(
        method = "create",
        at = @At(value = "INVOKE", target = "Ljava/lang/Thread;<init>(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;)V"),
        index = 1
    )
    private Runnable plz$registerStreamer(Runnable body) {
        return () -> {
            PLZStreamerWake.register();
            body.run();
        };
    }

    @Inject(
        method = "addJob",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedQueue;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER)
    )
    private void plz$wakeOnJob(IsoChunk chunk, int wx, int wy, boolean bDoServerRequest, CallbackInfo ci) {
        PLZStreamerWake.signal();
    }

    @Inject(
        method = "DoChunkAlways",
        at = @At(value = "FIELD", target = "Lzombie/iso/IsoChunk;refs:Ljava/util/ArrayList;", opcode = Opcodes.GETFIELD, ordinal = 0),
        cancellable = true
    )
    private void plz$recalcOnPool(IsoChunk chunk, ByteBuffer fromServer, CallbackInfo ci) {
        if (!PLZRecalcPool.active() || Thread.currentThread() != this.worldStreamer || chunk.refs.isEmpty()) {
            return;
        }

        boolean firstPassDone = false;
        boolean submitted = false;

        try {
            ((PLZIsoChunkAccess) (Object) chunk).recalcLoop1();
            firstPassDone = true;
            submitted = PLZRecalcPool.submit(chunk);
        } catch (Exception ex) {
            ExceptionLogger.logException(ex);
        }

        if (submitted) {
            ci.cancel();
            return;
        }

        if (firstPassDone) {
            try {
                ((PLZIsoChunkAccess) (Object) chunk).recalcPooled();
            } catch (Exception ex) {
                ExceptionLogger.logException(ex);
            }
        }

        IsoChunk.loadGridSquare.add(chunk);
        ci.cancel();
    }

    @ModifyReturnValue(method = "isBusy", at = @At("RETURN"))
    private boolean plz$busyWhileRecalcInFlight(boolean busy) {
        return busy || PLZRecalcPool.inFlight() > 0;
    }

    @Inject(
        method = "stop",
        at = @At(value = "INVOKE", target = "Lzombie/debug/DebugType;debugln(Ljava/lang/Object;)V", ordinal = 2, shift = At.Shift.AFTER)
    )
    private void plz$drainRecalcPool(CallbackInfo ci) {
        PLZRecalcPool.drain();
    }

    @Inject(
        method = "receiveChunkPart",
        at = @At(value = "FIELD", target = "Lzombie/iso/WorldStreamer$ChunkRequest;flagsUdp:I", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER)
    )
    private void plz$wakeOnChunkPart(CallbackInfo ci) {
        PLZStreamerWake.signal();
    }
}

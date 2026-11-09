package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import java.util.ArrayList;
import java.util.concurrent.FutureTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.logger.ExceptionLogger;
import zombie.debug.DebugType;
import zombie.fileSystem.FileSystemImpl;
import zombie.fileSystem.FileTask;
import zombie.plz.PLZPerf;
import zombie.plz.access.PLZFileSystemImplAsyncItemAccess;

@Mixin(FileSystemImpl.class)
abstract class FileSystemImplMixin {
    // inProgress is capped and new work is admitted only via the remaining count, so a future that never completes costs a slot for good.
    @Unique
    private static final long plz$STUCK_MS = 15000L;

    @Unique
    private static final long plz$REPORT_EVERY_MS = 5000L;

    @Unique
    private long plz$lastReportAt;

    @Unique
    private boolean plz$stalled;

    @Shadow
    @Final
    private ArrayList<?> inProgress;

    @Shadow
    @Final
    private ArrayList<?> pending;

    @ModifyArg(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Executors;newFixedThreadPool(I)Ljava/util/concurrent/ExecutorService;")
    )
    private int plz$fileThreads(int vanillaThreads) {
        return PLZPerf.FILE_THREADS;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$announce(CallbackInfo ci) {
        DebugType.Mod.println("PLZ: async file system - " + PLZPerf.FILE_THREADS + " threads, " + PLZPerf.FILE_IN_FLIGHT + " tasks in flight");
    }

    @ModifyConstant(method = "updateAsyncTransactions", constant = @Constant(intValue = 16))
    private int plz$slots(int vanillaSlots) {
        return PLZPerf.FILE_IN_FLIGHT;
    }

    // Vanilla leaves this unguarded: a throwing callback skips done(), leaks the slot for good, and aborts this frame's submits.
    @WrapOperation(method = "updateAsyncTransactions", at = @At(value = "INVOKE", target = "Lzombie/fileSystem/FileTask;handleResult(Ljava/lang/Object;)V"))
    private void plz$guardHandleResult(FileTask task, Object result, Operation<Void> original) {
        try {
            original.call(task, result);
        } catch (Throwable t) {
            DebugType.Mod.error("PLZ: async task " + plz$taskName(task) + " threw in handleResult: " + t);
            ExceptionLogger.logException(t, task.getErrorMessage());
        }
    }

    @WrapOperation(
        method = "updateAsyncTransactions",
        at = @At(value = "INVOKE", target = "Lzombie/fileSystem/FileTask;done()V", ordinal = 0)
    )
    private void plz$guardDone(FileTask task, Operation<Void> original) {
        try {
            original.call(task);
        } catch (Throwable t) {
            DebugType.Mod.error("PLZ: async task " + plz$taskName(task) + " threw in done(): " + t);
        }
    }

    @Inject(
        method = "updateAsyncTransactions",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicBoolean;compareAndSet(ZZ)Z", ordinal = 0)
    )
    private void plz$reportOnce(CallbackInfo ci, @Share("reported") LocalBooleanRef reported) {
        if (!reported.get()) {
            reported.set(true);
            this.plz$reportQueue();
        }
    }

    @WrapOperation(
        method = "updateAsyncTransactions",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z")
    )
    private boolean plz$stampSubmit(ArrayList<Object> list, Object item, Operation<Boolean> original) {
        PLZFileSystemImplAsyncItemAccess access = (PLZFileSystemImplAsyncItemAccess) item;
        access.plzSetSubmittedAt(System.currentTimeMillis());
        access.plzSetReported(false);
        return original.call(list, item);
    }

    @Unique
    private static String plz$taskName(FileTask task) {
        if (task == null) {
            return "<none>";
        }

        String detail = null;

        try {
            detail = task.getErrorMessage();
        } catch (Throwable ignored) {
        }

        String name = task.getClass().getSimpleName();
        return detail == null || detail.isEmpty() ? name : name + " (" + detail + ")";
    }

    @Unique
    private static String plz$name(PLZFileSystemImplAsyncItemAccess item) {
        return item == null ? "<none>" : plz$taskName(item.plzTask());
    }

    // Reported on the Mod channel, which DebugLog.init() enables unconditionally, so it reaches a player's console.txt.
    @Unique
    private void plz$reportQueue() {
        int slots = PLZPerf.FILE_IN_FLIGHT;
        long now = System.currentTimeMillis();
        int busy = this.inProgress.size();
        long oldest = 0L;
        PLZFileSystemImplAsyncItemAccess worst = null;

        for (int i = 0; i < busy; i++) {
            PLZFileSystemImplAsyncItemAccess item = (PLZFileSystemImplAsyncItemAccess) this.inProgress.get(i);
            FutureTask<Object> future = item.plzFuture();
            if (item.plzSubmittedAt() == 0L || future == null || future.isDone()) {
                continue;
            }

            long age = now - item.plzSubmittedAt();
            if (age > oldest) {
                oldest = age;
                worst = item;
            }
        }

        boolean stalled = busy >= slots && oldest >= plz$STUCK_MS;
        if (!stalled && !this.plz$stalled) {
            return;
        }

        if (now - this.plz$lastReportAt < plz$REPORT_EVERY_MS) {
            return;
        }

        this.plz$lastReportAt = now;

        if (!stalled) {
            this.plz$stalled = false;
            DebugType.Mod.warn("PLZ: async file queue recovered - " + busy + "/" + slots + " slots busy, " + this.pending.size() + " waiting");
            return;
        }

        boolean first = !this.plz$stalled;
        this.plz$stalled = true;
        DebugType.Mod.error(
            "PLZ: async file queue STALLED - " + busy + "/" + slots + " slots busy, " + this.pending.size()
                + " waiting, oldest " + oldest / 1000L + "s: " + plz$name(worst)
        );

        if (!first) {
            return;
        }

        for (int i = 0; i < busy; i++) {
            PLZFileSystemImplAsyncItemAccess item = (PLZFileSystemImplAsyncItemAccess) this.inProgress.get(i);
            if (!item.plzReported()) {
                item.plzSetReported(true);
                DebugType.Mod.error("PLZ:   slot " + i + " held " + (now - item.plzSubmittedAt()) / 1000L + "s by " + plz$name(item));
            }
        }
    }
}

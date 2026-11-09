package plz.mixin.client;

import java.util.concurrent.FutureTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import zombie.fileSystem.FileTask;
import zombie.plz.access.PLZFileSystemImplAsyncItemAccess;

@Mixin(targets = "zombie.fileSystem.FileSystemImpl$AsyncItem")
abstract class FileSystemImpl_AsyncItemMixin implements PLZFileSystemImplAsyncItemAccess {
    @Shadow
    FileTask task;

    @Shadow
    FutureTask<Object> future;

    @Unique
    private long plz$submittedAt;

    @Unique
    private boolean plz$reported;

    @Override
    public FileTask plzTask() {
        return this.task;
    }

    @Override
    public FutureTask<Object> plzFuture() {
        return this.future;
    }

    @Override
    public long plzSubmittedAt() {
        return this.plz$submittedAt;
    }

    @Override
    public void plzSetSubmittedAt(long at) {
        this.plz$submittedAt = at;
    }

    @Override
    public boolean plzReported() {
        return this.plz$reported;
    }

    @Override
    public void plzSetReported(boolean reported) {
        this.plz$reported = reported;
    }
}

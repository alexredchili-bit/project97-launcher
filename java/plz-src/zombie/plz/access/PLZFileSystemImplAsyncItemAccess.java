package zombie.plz.access;

import java.util.concurrent.FutureTask;
import zombie.fileSystem.FileTask;

public interface PLZFileSystemImplAsyncItemAccess {
    FileTask plzTask();

    FutureTask<Object> plzFuture();

    long plzSubmittedAt();

    void plzSetSubmittedAt(long at);

    boolean plzReported();

    void plzSetReported(boolean reported);
}

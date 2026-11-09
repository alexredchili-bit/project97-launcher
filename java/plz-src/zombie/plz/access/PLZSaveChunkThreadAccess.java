package zombie.plz.access;

import java.nio.ByteBuffer;

public interface PLZSaveChunkThreadAccess {
    ByteBuffer plzPendingChunkData(int wx, int wy);
}

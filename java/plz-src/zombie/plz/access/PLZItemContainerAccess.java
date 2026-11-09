package zombie.plz.access;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface PLZItemContainerAccess {
    void plzSaveLean(ByteBuffer output) throws IOException;
}

package zombie.plz.access;

import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;

public interface PLZChatServerAccess {
    void plzProcessPlayerStartWhisperChatPacket(ByteBufferReader bb, UdpConnection connection);

    boolean plzSetSayRange(float range);

    boolean plzResendJoins(short playerID, boolean full);
}

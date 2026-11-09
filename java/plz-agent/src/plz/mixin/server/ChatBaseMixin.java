package plz.mixin.server;

import java.util.concurrent.locks.Lock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.chat.ChatBase;
import zombie.plz.access.PLZChatBaseAccess;

@Mixin(ChatBase.class)
abstract class ChatBaseMixin implements PLZChatBaseAccess {
    @Shadow
    @Final
    private Lock memberLock;

    @Shadow
    private boolean hasMember(Short playerID) {
        throw new AssertionError();
    }

    @Override
    public boolean plzHasMember(Short playerID) {
        synchronized (this.memberLock) {
            return this.hasMember(playerID);
        }
    }
}

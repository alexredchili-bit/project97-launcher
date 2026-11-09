package plz.mixin.server;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.raknet.UdpConnection;
import zombie.plz.PLZFixes;
import zombie.plz.PLZNetProbe;
import zombie.plz.access.PLZUdpConnectionAccess;

@Mixin(UdpConnection.class)
abstract class UdpConnectionMixin implements PLZUdpConnectionAccess {
    @Unique
    private volatile PLZNetProbe.Tally plz$netTally;

    @Shadow
    public abstract boolean isFullyConnected();

    @Override
    public PLZNetProbe.Tally plzGetNetTally() {
        return this.plz$netTally;
    }

    @Override
    public void plzSetNetTally(PLZNetProbe.Tally tally) {
        this.plz$netTally = tally;
    }

    // Until its first PlayerPacket a connection sits at (0,0,0), so it would keep origin cells loaded and spawn zombies there.
    @Unique
    private boolean plz$handshaking() {
        if (!this.isFullyConnected() && PLZFixes.on(PLZFixes.HANDSHAKE_RELEVANCE)) {
            PLZFixes.hit(PLZFixes.HANDSHAKE_RELEVANCE);
            return true;
        }

        return false;
    }

    @Inject(method = "isRelevantTo", at = @At("HEAD"), cancellable = true)
    private void plz$isRelevantTo(float x, float y, CallbackInfoReturnable<Boolean> cir) {
        if (this.plz$handshaking()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getRelevantAndDistance", at = @At("HEAD"), cancellable = true)
    private void plz$getRelevantAndDistance(float x, float y, float z, CallbackInfoReturnable<Float> cir) {
        if (this.plz$handshaking()) {
            cir.setReturnValue(-1.0F);
        }
    }

    @Inject(method = "RelevantToPlayerIndex", at = @At("HEAD"), cancellable = true)
    private void plz$relevantToPlayerIndex(int n, float x, float y, CallbackInfoReturnable<Boolean> cir) {
        if (this.plz$handshaking()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "RelevantTo", at = @At("HEAD"), cancellable = true)
    private void plz$relevantTo(float x, float y, float radius, CallbackInfoReturnable<Boolean> cir) {
        if (this.plz$handshaking()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "flipSendUnlock", at = @At("HEAD"))
    private void plz$tally(ByteBuffer bb, int priority, int reliability, byte ordering, Lock lock, CallbackInfo ci) {
        PLZNetProbe.Tally tally = this.plz$netTally;
        if (tally != null && bb.position() >= 3) {
            tally.add(bb.getShort(1), bb.position());
        }
    }
}

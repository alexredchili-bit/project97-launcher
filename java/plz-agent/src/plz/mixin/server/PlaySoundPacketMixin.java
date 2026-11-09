package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.GameSounds;
import zombie.audio.GameSound;
import zombie.core.raknet.UdpConnection;
import zombie.iso.IsoMovingObject;
import zombie.network.PacketTypes;
import zombie.network.packets.sound.PlaySoundPacket;
import zombie.plz.PLZSoundCull;

@Mixin(PlaySoundPacket.class)
abstract class PlaySoundPacketMixin {
    @Shadow
    String name;

    @Unique
    private float plz$audibleRadius;

    @Unique
    private int plz$sent;

    @Unique
    private int plz$suppressed;

    @Unique
    private int plz$overBudget;

    @Shadow
    public abstract IsoMovingObject getMovingObject();

    @Shadow
    private boolean isLooped() {
        throw new AssertionError();
    }

    @Inject(method = "processServer", at = @At("HEAD"))
    private void plz$measure(PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci) {
        this.plz$audibleRadius = -1.0F;
        this.plz$sent = 0;
        this.plz$suppressed = 0;
        this.plz$overBudget = 0;
        GameSound gameSound = GameSounds.getSound(this.name);
        if (this.getMovingObject() != null && gameSound != null) {
            float maxDist = gameSound.getMaxDistanceOfClips();
            if (maxDist > 0.0F) {
                this.plz$audibleRadius = maxDist * PLZSoundCull.getSlack();
            }
        }
    }

    @WrapOperation(
        method = "processServer",
        at = @At(value = "INVOKE", target = "Lzombie/core/raknet/UdpConnection;RelevantTo(FFF)Z")
    )
    private boolean plz$audibleAndInBudget(UdpConnection c, float x, float y, float radius, Operation<Boolean> original) {
        if (!original.call(c, x, y, radius)) {
            return false;
        }

        if (this.plz$audibleRadius >= 0.0F && !c.RelevantTo(x, y, this.plz$audibleRadius)) {
            this.plz$suppressed++;
            return false;
        }

        if (!this.isLooped() && PLZSoundCull.overBudget(c.getConnectedGUID(), this.name)) {
            this.plz$overBudget++;
            return false;
        }

        this.plz$sent++;
        return true;
    }

    @Inject(method = "processServer", at = @At("TAIL"))
    private void plz$record(PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci) {
        PLZSoundCull.record(this.plz$sent, this.plz$suppressed, this.plz$overBudget);
    }
}

package plz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.network.ByteBufferReader;
import zombie.core.raknet.UdpConnection;
import zombie.core.skinnedmodel.advancedanimation.AdvancedAnimator;
import zombie.network.GameServer;
import zombie.network.packets.service.ChecksumPacket;
import zombie.plz.PLZChecksumExempt;
import zombie.scripting.ScriptManager;

@Mixin(ChecksumPacket.class)
abstract class ChecksumPacketMixin {
    @ModifyExpressionValue(
        method = "write",
        at = {
            @At(value = "FIELD", target = "Lzombie/network/GameClient;checksum:Ljava/lang/String;"),
            @At(value = "INVOKE", target = "Lzombie/scripting/ScriptManager;getChecksum()Ljava/lang/String;"),
            @At(value = "INVOKE", target = "Lzombie/core/skinnedmodel/advancedanimation/AdvancedAnimator;getChecksum()Ljava/lang/String;")
        },
        require = 3,
        allow = 3
    )
    private String plz$sendFiltered(String checksum) {
        return PLZChecksumExempt.forSend(checksum);
    }

    @WrapOperation(
        method = "parseServer",
        at = {
            @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 0),
            @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 1),
            @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 2)
        },
        require = 3,
        allow = 3
    )
    private boolean plz$acceptEitherTotal(String received, Object expected, Operation<Boolean> original) {
        return PLZChecksumExempt.accepts(received, (String)expected);
    }

    @Inject(
        method = "parseServer",
        at = @At(
            value = "FIELD",
            target = "Lzombie/network/packets/service/ChecksumPacket;okAnim:Z",
            opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void plz$logVariants(
        ByteBufferReader b,
        UdpConnection connection,
        CallbackInfo ci,
        @Local(ordinal = 0) String luaChecksum,
        @Local(ordinal = 1) String scriptChecksum,
        @Local(ordinal = 2) String animChecksum
    ) {
        System.out.println(
            "PLZ checksum: user "
                + connection.getUserName()
                + " lua="
                + PLZChecksumExempt.variantOf(luaChecksum, GameServer.checksum)
                + " script="
                + PLZChecksumExempt.variantOf(scriptChecksum, ScriptManager.instance.getChecksum())
                + " anim="
                + PLZChecksumExempt.variantOf(animChecksum, AdvancedAnimator.getChecksum())
        );
    }
}

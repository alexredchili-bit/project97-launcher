package plz.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.network.NetChecksum;
import zombie.plz.PLZChecksumExempt;

// md still takes every file so the full total matches an unpatched client; mdExempt is the parallel total without exempt mods.
@Mixin(NetChecksum.Checksummer.class)
abstract class NetChecksum_ChecksummerMixin {
    @Unique
    private MessageDigest plz$mdExempt;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$makeExemptDigest(CallbackInfo ci) {
        try {
            this.plz$mdExempt = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform implementation is required to support MD5", e);
        }
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void plz$resetExempt(CallbackInfo ci) {
        this.plz$mdExempt.reset();
        PLZChecksumExempt.beginBuild();
    }

    @Inject(
        method = "addFile",
        at = @At(value = "INVOKE", target = "Ljava/security/MessageDigest;update([BII)V", shift = At.Shift.AFTER),
        cancellable = true
    )
    private void plz$addExemptFile(
        String relPath, String absPath, CallbackInfo ci, @Local(name = "bytes") byte[] bytes, @Local(name = "count") int count
    ) {
        if (PLZChecksumExempt.isExempt(absPath)) {
            ci.cancel();
            return;
        }

        this.plz$mdExempt.update(bytes, 0, count);
    }

    @Inject(method = "checksumToString", at = @At("RETURN"))
    private void plz$recordExempt(CallbackInfoReturnable<String> cir) {
        PLZChecksumExempt.record(cir.getReturnValue(), HexFormat.of().formatHex(this.plz$mdExempt.digest()));
    }
}

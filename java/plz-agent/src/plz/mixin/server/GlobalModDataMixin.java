package plz.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.io.File;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.plz.PLZFixes;
import zombie.world.moddata.GlobalModData;

@Mixin(GlobalModData.class)
abstract class GlobalModDataMixin {
    @Shadow
    private static int lastBlockSize;

    // PLZ: +512 KB per overflow re-serialized whole tables ~240 times for 120 MB, a 13 s save.
    @ModifyExpressionValue(method = "ensureCapacity", at = @At(value = "CONSTANT", args = "intValue=524288"))
    private static int plz$growByHalf(int grow, @Local(argsOnly = true) ByteBuffer bb) {
        if (PLZFixes.on(PLZFixes.GLOBAL_MOD_DATA_SAVE)) {
            grow = Math.max(grow, bb.capacity() / 2);
            PLZFixes.hit(PLZFixes.GLOBAL_MOD_DATA_SAVE);
        }

        return grow;
    }

    // PLZ: vanilla dropped the grown buffer here, so the putInt below overflowed and the save failed.
    @WrapOperation(
        method = "save",
        at = @At(value = "INVOKE", target = "Lzombie/world/moddata/GlobalModData;ensureCapacity(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;", ordinal = 0)
    )
    private ByteBuffer plz$keepGrownBuffer(ByteBuffer bb, Operation<ByteBuffer> original, @Local LocalRef<ByteBuffer> bbRef) {
        ByteBuffer grown = original.call(bb);
        if (PLZFixes.on(PLZFixes.GLOBAL_MOD_DATA_SAVE)) {
            bbRef.set(grown);
        }

        return grown;
    }

    // PLZ: the first save of a session otherwise starts at 1 MB and grows into the file's size.
    @Inject(method = "load", at = @At(value = "INVOKE", target = "Ljava/io/FileInputStream;close()V", ordinal = 0))
    private void plz$presizeFromFile(CallbackInfo ci, @Local File path) {
        if (PLZFixes.on(PLZFixes.GLOBAL_MOD_DATA_SAVE)) {
            lastBlockSize = (int)Math.min(Integer.MAX_VALUE - 8L, Math.max((long)lastBlockSize, path.length() + 4194304L));
        }
    }
}

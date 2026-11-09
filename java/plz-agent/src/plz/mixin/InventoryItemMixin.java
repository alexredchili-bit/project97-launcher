package plz.mixin;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.debug.DebugLog;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoObject;
import zombie.plz.PLZFixes;
import zombie.plz.PLZItemBlob;
import zombie.plz.PLZItemWeightCache;

@Mixin(InventoryItem.class)
abstract class InventoryItemMixin {
    // Ceiling for the grow-on-overflow retry, mirroring PLZItemBlob's own cap.
    @Unique
    private static final int PLZ_MAX_ITEM_BYTES = 8388608;

    @Shadow
    @Final
    private static ByteBuffer tempBuffer;

    @Shadow
    public ByteBuffer byteData;

    @Shadow
    public ArrayList<String> extraItems;

    @Shadow
    public abstract boolean haveExtraItems();

    // BufferOverflowException escapes vanilla's catch (IOException), so a corpse whose ModData outgrew 20 KB could never be picked up.
    @Inject(method = "storeInByteData", at = @At("HEAD"), cancellable = true)
    private void plz$storeGrowing(IsoObject o, CallbackInfo ci) {
        if (!PLZFixes.on(PLZFixes.CORPSE_ITEM_BYTES)) {
            return;
        }

        ByteBuffer buffer = plz$serialiseGrowing(o);
        buffer.flip();
        if (this.byteData == null || this.byteData.capacity() < buffer.limit() - 2 + 8) {
            this.byteData = ByteBuffer.allocate(buffer.limit() - 2 + 8);
        }

        buffer.get();
        buffer.get();
        this.byteData.clear();
        this.byteData.put((byte)87);
        this.byteData.put((byte)86);
        this.byteData.put((byte)69);
        this.byteData.put((byte)82);
        this.byteData.putInt(251);
        this.byteData.put(buffer);
        this.byteData.flip();
        ci.cancel();
    }

    /**
     * @author PLZ
     * @reason Sum cached per-type weights instead of instancing a throwaway item for every extra item on every call.
     */
    @Overwrite
    public float getExtraItemsWeight() {
        if (!this.haveExtraItems()) {
            return 0.0F;
        }

        float extraWeight = 0.0F;

        for (int i = 0; i < this.extraItems.size(); i++) {
            extraWeight += PLZItemWeightCache.weightOf(this.extraItems.get(i));
        }

        return extraWeight * 0.6F;
    }

    // Lua cannot reach zombie.plz; null means "bank the property record instead". Pairs with ItemContainer.plzAddBlob.
    public String plzSaveBlob() {
        return PLZItemBlob.save((InventoryItem) (Object) this);
    }

    // The shared static stays 20 KB for every ordinary item; only an oversized object pays for a bigger local buffer.
    @Unique
    private static ByteBuffer plz$serialiseGrowing(IsoObject o) {
        ByteBuffer buffer = tempBuffer;

        while (true) {
            buffer.clear();

            try {
                o.save(buffer, false);
                return buffer;
            } catch (IOException e) {
                DebugType.General.printException(e, LogSeverity.Error);
                return buffer;
            } catch (BufferOverflowException overflow) {
                int capacity = buffer.capacity() * 4;
                if (capacity > PLZ_MAX_ITEM_BYTES) {
                    DebugLog.log("PLZFixes: object too large to store in byte data even at "
                        + PLZ_MAX_ITEM_BYTES + " bytes; leaving it unserialised");
                    buffer.clear();
                    return buffer;
                }

                PLZFixes.hit(PLZFixes.CORPSE_ITEM_BYTES);
                buffer = ByteBuffer.allocate(capacity);
            }
        }
    }
}

package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.characters.WornItems.WornItem;
import zombie.characters.WornItems.WornItems;
import zombie.network.fields.character.ConnectionPlayer;
import zombie.network.packets.SyncClothingPacket;
import zombie.plz.PLZWornLock;
import zombie.plz.access.PLZWornItemsAccess;

@Mixin(SyncClothingPacket.class)
abstract class SyncClothingPacketMixin {
    @Shadow
    @Final
    private ConnectionPlayer player;

    // This rebuilds a remote character's list, so it is the one place on that path that knows the owner.
    @Inject(method = "process", at = @At(value = "NEW", target = "java/util/ArrayList", ordinal = 0))
    private void plz$stampOwner(CallbackInfo ci) {
        IsoPlayer plzPlayer = this.player.getPlayer();
        if (plzPlayer.getWornItems() != null) {
            ((PLZWornItemsAccess)(Object)plzPlayer.getWornItems()).plzSetOwner(plzPlayer.getUsername());
        }
    }

    // A locked entry is never dropped for being absent: a lagging client would talk the server out of the lock.
    @WrapOperation(
        method = "process",
        at = @At(value = "INVOKE", target = "Lzombie/characters/WornItems/WornItems;forEach(Ljava/util/function/Consumer;)V", ordinal = 0)
    )
    private void plz$keepLocked(WornItems wornItems, Consumer<WornItem> action, Operation<Void> original) {
        IsoPlayer plzPlayer = this.player.getPlayer();
        original.call(wornItems, (Consumer<WornItem>)itemx -> {
            if (itemx.getLocation() != null
                && PLZWornLock.isLocked(plzPlayer.getUsername(), itemx.getLocation().getTranslationName())) {
                return;
            }

            action.accept(itemx);
        });
    }
}

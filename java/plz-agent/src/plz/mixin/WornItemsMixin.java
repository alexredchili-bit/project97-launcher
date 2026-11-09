package plz.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plz.agent.LuaPublic;
import zombie.characters.WornItems.BodyLocationGroup;
import zombie.characters.WornItems.WornItem;
import zombie.characters.WornItems.WornItems;
import zombie.inventory.InventoryItem;
import zombie.plz.PLZWornLock;
import zombie.plz.access.PLZWornItemsAccess;
import zombie.scripting.objects.ItemBodyLocation;

@Mixin(WornItems.class)
abstract class WornItemsMixin implements PLZWornItemsAccess {
    @Shadow
    @Final
    private BodyLocationGroup group;

    @Shadow
    @Final
    private List<WornItem> items;

    // Stamped by SyncClothingPacket.process and Lua; null on every unstamped character reads as "not locked".
    @Unique
    private volatile String plz$owner;

    @Shadow
    private int indexOf(ItemBodyLocation location) {
        throw new AssertionError();
    }

    @Shadow
    public abstract void remove(InventoryItem item);

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void plz$copyOwner(WornItems other, CallbackInfo ci) {
        this.plz$owner = ((PLZWornItemsAccess)(Object)other).plzGetOwner();
    }

    @Override
    public void plzSetOwner(String username) {
        this.plz$owner = username;
    }

    @Override
    public String plzGetOwner() {
        return this.plz$owner;
    }

    @Unique
    private boolean plz$isPinned(WornItem wornItem) {
        return wornItem != null && this.plz$isLockedLocation(wornItem.getLocation());
    }

    @Unique
    private boolean plz$isLockedLocation(ItemBodyLocation location) {
        if (!PLZWornLock.isActive() || this.plz$owner == null || location == null) {
            return false;
        }
        return PLZWornLock.isLocked(this.plz$owner, location.getTranslationName());
    }

    @Unique
    private boolean plz$isLayered() {
        return PLZWornLock.isActive() && this.plz$owner != null && PLZWornLock.isLayered(this.plz$owner);
    }

    /**
     * @author PLZ
     * @reason Worn locks refuse emptying, stack and skip exclusivity throughout the eviction logic.
     */
    @Overwrite
    public void setItem(ItemBodyLocation location, InventoryItem item) {
        // Every unequip route, local or rebuilt from a packet, lands here, so only a refusal here holds everywhere.
        boolean plzLocked = this.plz$isLockedLocation(location);
        if (plzLocked && item == null && this.indexOf(location) != -1) {
            return;
        }

        boolean plzLayered = this.plz$isLayered();
        if (plzLayered && item == null) {
            int index = this.indexOf(location);
            if (index != -1) {
                this.items.remove(index);
            }
        } else if (!plzLocked && !plzLayered && !this.group.isMultiItem(location)) {
            // Every entry, not the first, so layers left over from a layered session collapse on the next wear.
            int index;
            while ((index = this.indexOf(location)) != -1) {
                this.items.remove(index);
            }
        }

        for (int i = 0; !plzLayered && i < this.items.size(); i++) {
            WornItem wornItem = this.items.get(i);
            if (this.group.isExclusive(location, wornItem.getLocation())) {
                // A pinned entry survives exclusivity, so a hat goes on beside a locked costume head.
                if (this.plz$isPinned(wornItem)) {
                    continue;
                }
                this.items.remove(i--);
            }
        }

        if (item != null) {
            this.remove(item);
            int insertAt = this.items.size();

            for (int i = 0; i < this.items.size(); i++) {
                WornItem wornItem1 = this.items.get(i);
                if (this.group.indexOf(wornItem1.getLocation()) > this.group.indexOf(location)) {
                    insertAt = i;
                    break;
                }
            }

            WornItem wornItem = new WornItem(location, item);
            this.items.add(insertAt, wornItem);
        }
    }

    @Unique
    @LuaPublic
    private static void plzWornLockAdd(String username, String location) {
        PLZWornLock.add(username, location);
    }

    @Unique
    @LuaPublic
    private static void plzWornLockClear(String username) {
        PLZWornLock.clear(username);
    }

    @Unique
    @LuaPublic
    private static void plzWornLockClearAll() {
        PLZWornLock.clearAll();
    }

    @Unique
    @LuaPublic
    private static boolean plzWornLockIsLocked(String username, String location) {
        return PLZWornLock.isLocked(username, location);
    }

    @Unique
    @LuaPublic
    private static void plzWornLayerSet(String username, boolean on) {
        PLZWornLock.setLayered(username, on);
    }

    @Unique
    @LuaPublic
    private static boolean plzWornLayerIsOn(String username) {
        return PLZWornLock.isLayered(username);
    }

    @Unique
    @LuaPublic
    private static int plzWornLockCount() {
        return PLZWornLock.count();
    }

    @Unique
    @LuaPublic
    private static String plzWornLockStatus() {
        return PLZWornLock.status();
    }
}

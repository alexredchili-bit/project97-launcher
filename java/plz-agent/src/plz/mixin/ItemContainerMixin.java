package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.GameWindow;
import zombie.characters.IsoGameCharacter;
import zombie.debug.DebugLog;
import zombie.debug.DebugType;
import zombie.inventory.CompressIdenticalItems;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.InventoryContainer;
import zombie.plz.PLZItemBlob;
import zombie.plz.access.PLZItemContainerAccess;

@Mixin(ItemContainer.class)
abstract class ItemContainerMixin implements PLZItemContainerAccess {
    // A cycle is re-entered every inventory tick: log the first eight in full, then one in 1024; non-atomic on purpose.
    @Unique
    private static long plz$parentChainReports = 0L;

    @Shadow
    public String type;

    @Shadow
    public int capacity;

    @Shadow
    public InventoryItem containingItem;

    @Shadow
    public ArrayList<InventoryItem> items;

    @Shadow
    public abstract boolean isInside(InventoryItem item);

    @Shadow
    public abstract boolean isHasBeenLooted();

    @Shadow
    public abstract InventoryItem getContainingItem();

    @Shadow
    public abstract ArrayList<InventoryItem> save(ByteBuffer output, IsoGameCharacter noCompress) throws IOException;

    // 42.21's InventoryContainer.OnBeforeRemoveFromContainer recurses into the bag's contents, so a bag inside itself overflows the stack.
    @WrapOperation(
        method = {"Remove(Lzombie/inventory/InventoryItem;)V", "DoRemoveItem"},
        at = @At(value = "INVOKE", target = "Lzombie/inventory/InventoryItem;OnBeforeRemoveFromContainer(Lzombie/inventory/ItemContainer;)V")
    )
    private void plz$beforeRemove(InventoryItem item, ItemContainer container, Operation<Void> original) {
        if (item instanceof InventoryContainer && this.isInside(item)) {
            this.plz$reportParentChainCycle();
            return;
        }

        original.call(item, container);
    }

    // Same layout as save, minus the items; unexplored so opening it asks the server for them.
    @Override
    public void plzSaveLean(ByteBuffer output) throws IOException {
        if (this.items.isEmpty()) {
            this.save(output, null);
            return;
        }

        GameWindow.WriteString(output, this.type);
        output.put((byte)0);
        CompressIdenticalItems.save(output, new ArrayList<>(), null);
        output.put((byte)(this.isHasBeenLooted() ? 1 : 0));
        output.putInt(this.capacity);
    }

    // Lua cannot reach zombie.plz; keeps the banked item's id, null means "use the property record instead". Pairs with InventoryItem.plzSaveBlob.
    public InventoryItem plzAddBlob(String blob) {
        return PLZItemBlob.addTo((ItemContainer) (Object) this, blob);
    }

    /**
     * @author PLZ
     * @reason Returns this on a malformed chain, not the far end of the walk, so getSquare's recursion ends; and names the broken chain.
     */
    @Overwrite
    public ItemContainer getOutermostContainer() {
        ItemContainer self = (ItemContainer) (Object) this;
        ItemContainer container = self;

        for (int depth = 0; depth < 16; depth++) {
            InventoryItem outerItem = container.getContainingItem();
            if (outerItem == null) {
                return container;
            }

            ItemContainer outerContainer = outerItem.getContainer();
            if (outerContainer == null) {
                return container;
            }

            if (outerContainer == self) {
                break;
            }

            container = outerContainer;
        }

        this.plz$reportParentChainCycle();
        return self;
    }

    @Unique
    private void plz$reportParentChainCycle() {
        plz$reportParentChainCycle((ItemContainer) (Object) this);
    }

    @Unique
    private static void plz$reportParentChainCycle(ItemContainer from) {
        long seen = ++plz$parentChainReports;
        if (seen > 8L && seen % 1024L != 0L) {
            return;
        }

        // The ids are what an admin removes to break the loop, so the line is worth nothing without them.
        StringBuilder chain = new StringBuilder();
        ItemContainer container = from;

        for (int depth = 0; depth < 12; depth++) {
            InventoryItem item = container.containingItem;
            if (item == null) {
                break;
            }

            if (depth > 0) {
                chain.append(" -> ");
            }
            chain.append(item.getFullType()).append('#').append(item.getID());

            ItemContainer next = item.getContainer();
            if (next == null) {
                break;
            }
            container = next;
        }

        DebugLog.log(
            DebugType.General,
            "PLZ: container parent chain exceeded " + ItemContainer.CONTAINER_MAX_NESTING + " (occurrence " + seen
                + "); an item is inside itself and its outermost container cannot be resolved. chain: "
                + chain
        );
    }
}

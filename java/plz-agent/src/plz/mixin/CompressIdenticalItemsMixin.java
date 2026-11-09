package plz.mixin;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import zombie.characters.IsoGameCharacter;
import zombie.debug.DebugLog;
import zombie.inventory.CompressIdenticalItems;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.AnimalInventoryItem;
import zombie.plz.PLZFixes;

@Mixin(CompressIdenticalItems.class)
abstract class CompressIdenticalItemsMixin {
    // PLZ: AnimalInventoryItem.save NPEs on a null animal and aborts the whole server save; drop it from a copy instead.
    @ModifyVariable(
        method = "save(Ljava/nio/ByteBuffer;Ljava/util/ArrayList;Lzombie/characters/IsoGameCharacter;)Ljava/util/ArrayList;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private static ArrayList<InventoryItem> plz$withoutNullAnimals(ArrayList<InventoryItem> items) {
        if (!PLZFixes.on(PLZFixes.COMPRESS_IDENTICAL_ITEMS) || items == null) {
            return items;
        }

        int drop = -1;
        for (int i = 0; i < items.size(); i++) {
            InventoryItem item = items.get(i);
            if (item instanceof AnimalInventoryItem animalItem && animalItem.getAnimal() == null) {
                drop = i;
                break;
            }
        }

        if (drop == -1) {
            return items;
        }

        ArrayList<InventoryItem> filtered = new ArrayList<>(items.size() - 1);
        for (int i = 0; i < items.size(); i++) {
            InventoryItem item = items.get(i);
            if (item instanceof AnimalInventoryItem animalItem && animalItem.getAnimal() == null) {
                DebugLog.log("PLZFixes: dropping AnimalInventoryItem with a null animal from a container save (" + item.getFullType() + ")");
                continue;
            }

            filtered.add(item);
        }

        PLZFixes.hit(PLZFixes.COMPRESS_IDENTICAL_ITEMS);
        return filtered;
    }
}

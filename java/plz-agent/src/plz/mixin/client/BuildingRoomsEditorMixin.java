package plz.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.buildingRooms.BuildingRoomsEditor;
import zombie.plz.PLZPerf;

@Mixin(BuildingRoomsEditor.class)
abstract class BuildingRoomsEditorMixin {
    // Built once per cell, iterated backwards so the first occurrence wins exactly as indexOf does.
    @WrapOperation(
        method = "checkBuildingAndRoomIDs(Lzombie/iso/IsoMetaCell;)V",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;indexOf(Ljava/lang/Object;)I")
    )
    private int plz$indexedRoom(
        ArrayList<Object> roomList, Object roomDef, Operation<Integer> original, @Share("plzRoomIndex") LocalRef<IdentityHashMap<Object, Integer>> index
    ) {
        if (!PLZPerf.LOADER_CPU_FIXES) {
            return original.call(roomList, roomDef);
        }

        IdentityHashMap<Object, Integer> map = index.get();
        if (map == null) {
            map = new IdentityHashMap<>(roomList.size() * 2);
            for (int r = roomList.size() - 1; r >= 0; r--) {
                map.put(roomList.get(r), r);
            }

            index.set(map);
        }

        return map.getOrDefault(roomDef, -1);
    }
}

package plz.mixin.server;

import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.network.IsoObjectID;
import zombie.plz.PLZFixes;

@Mixin(IsoObjectID.class)
abstract class IsoObjectIDMixin<T> {
    @Shadow
    @Final
    private ConcurrentHashMap<Short, T> idToObjectMap;

    @Shadow
    private short nextId;

    // PLZ: a free-running counter lets put() displace a live holder, which the client then spawns twice ("mitosis").
    @Inject(method = "allocateID", at = @At("HEAD"), cancellable = true)
    private void plz$allocateFreeId(CallbackInfoReturnable<Short> cir) {
        if (PLZFixes.on(PLZFixes.OBJECT_ID_ALLOCATE)) {
            short free = plz$nextFreeId(this.nextId, this.idToObjectMap);
            if (free != -1) {
                this.nextId = free;
                PLZFixes.hit(PLZFixes.OBJECT_ID_ALLOCATE);
            }

            cir.setReturnValue(free);
        }
    }

    // -1 when every slot is taken, which callers already treat as pool exhausted.
    @Unique
    private static short plz$nextFreeId(short startNextId, ConcurrentHashMap<Short, ?> idToObjectMap) {
        short id = startNextId;

        for (int attempts = 0; attempts < 65535; attempts++) {
            id++;
            if (id == -1) {
                id++;
            }

            if (!idToObjectMap.containsKey(id)) {
                return id;
            }
        }

        return -1;
    }
}

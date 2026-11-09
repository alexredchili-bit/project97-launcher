package plz.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.SandboxOptions;
import zombie.plz.PLZFixes;
import zombie.worldMap.WorldMapVisitedServer;

@Mixin(WorldMapVisitedServer.class)
abstract class WorldMapVisitedServerMixin {
    @Unique
    private static final byte PLZ_ALL_KNOWN = (byte)170;

    // PLZ: 42.20.3's RequestData path dropped the all-known pass; OR BIT_KNOWN into the copy sent, never the stored bytes.
    @ModifyExpressionValue(
        method = "sendRequestData",
        at = @At(value = "INVOKE", target = "Ljava/util/HashMap;get(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object plz$applyAllKnown(Object value) {
        byte[] stored = (byte[])value;
        if (stored == null || stored.length == 0 || !PLZFixes.on(PLZFixes.WORLD_MAP_ALL_KNOWN)) {
            return stored;
        }

        try {
            if (!SandboxOptions.getInstance().map.mapAllKnown.getValue()) {
                return stored;
            }
        } catch (Throwable unavailable) {
            return stored;
        }

        byte[] entity = new byte[stored.length];
        for (int i = 0; i < stored.length; i++) {
            entity[i] = (byte)(stored[i] | PLZ_ALL_KNOWN);
        }

        PLZFixes.hit(PLZFixes.WORLD_MAP_ALL_KNOWN);
        return entity;
    }
}

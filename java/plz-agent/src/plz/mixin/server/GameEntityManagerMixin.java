package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.debug.DebugType;
import zombie.entity.Engine;
import zombie.entity.GameEntity;
import zombie.entity.GameEntityManager;
import zombie.entity.util.LongMap;
import zombie.plz.PLZFixes;

@Mixin(GameEntityManager.class)
abstract class GameEntityManagerMixin {
    @WrapOperation(
        method = "RegisterEntity",
        at = @At(value = "INVOKE", target = "Lzombie/entity/Engine;addEntity(Lzombie/entity/GameEntity;)Z")
    )
    private static boolean plz$relinkIdOnly(Engine engine, GameEntity gameEntity, Operation<Boolean> original) {
        if (gameEntity.isValidEngineEntity() && PLZFixes.on(PLZFixes.ENTITY_REREGISTER)) {
            PLZFixes.hit(PLZFixes.ENTITY_REREGISTER);
            DebugType.Entity.warn("PLZ: entity %s was still in the engine, re-linking its id only", gameEntity);
            return false;
        }

        return original.call(engine, gameEntity);
    }

    @WrapOperation(
        method = "UnregisterEntity(Lzombie/entity/GameEntity;Z)V",
        at = @At(value = "INVOKE", target = "Lzombie/entity/util/LongMap;remove(J)Ljava/lang/Object;", ordinal = 1)
    )
    private static Object plz$keepMismatchedId(
        LongMap<GameEntity> map,
        long entityNetID,
        Operation<Object> original,
        @Local(argsOnly = true) GameEntity gameEntity,
        @Local(argsOnly = true) LocalBooleanRef offloadToMeta
    ) {
        GameEntity stored = (GameEntity)original.call(map, entityNetID);
        if (stored == null || stored == gameEntity || !PLZFixes.on(PLZFixes.ENTITY_ID_MISMATCH)) {
            return stored;
        }

        // Throwing here aborted ServerCell.Unload halfway and later wedged the server on reload.
        map.put(entityNetID, stored);
        PLZFixes.hit(PLZFixes.ENTITY_ID_MISMATCH);
        DebugType.Entity.warn("PLZ: id %d belongs to %s, unregistering %s without it", entityNetID, stored, gameEntity);
        offloadToMeta.set(false);
        return gameEntity;
    }
}

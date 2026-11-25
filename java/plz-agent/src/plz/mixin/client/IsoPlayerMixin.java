package plz.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.ai.states.PlayerStrafeState;
import zombie.characters.IsoPlayer;
import zombie.characters.NetworkPlayerAI;
import zombie.iso.Vector2;
import zombie.pathfind.PathFindBehavior2;
import zombie.plz.PLZArenaInterp;

@Mixin(IsoPlayer.class)
abstract class IsoPlayerMixin {
    @Shadow
    @Final
    private static Vector2 tempo;

    @Shadow
    protected boolean isPlayerMoving;

    @Shadow
    private float deltaX;

    @Shadow
    private float deltaY;

    // Arena remotes are drawn from PLZArenaInterp's snapshot buffer instead of vanilla's chase-the-last-packet movement.
    @Inject(method = "updateRemotePlayer()Z", at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;isAnimal()Z", ordinal = 1))
    private void plz$arenaTarget(
        CallbackInfoReturnable<Boolean> cir, @Local NetworkPlayerAI networkAi, @Share("plzArena") LocalBooleanRef arena
    ) {
        arena.set(PLZArenaInterp.target((IsoPlayer)(Object)this, networkAi));
    }

    @ModifyExpressionValue(
        method = "updateRemotePlayer()Z",
        at = @At(value = "INVOKE", target = "Lzombie/characters/NetworkPlayerAI;isCollisionEnabled()Z")
    )
    private boolean plz$arenaTakesNetworkTarget(boolean enabled, @Share("plzArena") LocalBooleanRef arena) {
        return enabled || arena.get();
    }

    @WrapOperation(
        method = "updateRemotePlayer()Z",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;setCollidable(Z)V", ordinal = 1)
    )
    private void plz$arenaNoCollide(
        IsoPlayer self, boolean collidable, Operation<Void> original, @Local NetworkPlayerAI networkAi, @Share("plzArena") LocalBooleanRef arena
    ) {
        if (arena.get()) {
            original.call(self, false);
            networkAi.forcePathFinder = false;
        } else {
            original.call(self, collidable);
        }
    }

    @WrapOperation(
        method = "updateRemotePlayer()Z",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;isRemoteAndHasObstacleOnPath()Z")
    )
    private boolean plz$arenaNoObstaclePath(IsoPlayer self, Operation<Boolean> original, @Share("plzArena") LocalBooleanRef arena) {
        return !arena.get() && original.call(self);
    }

    @ModifyExpressionValue(
        method = "updateRemotePlayer()Z",
        at = @At(value = "FIELD", target = "Lzombie/characters/NetworkPlayerAI;needToMovingUsingPathFinder:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean plz$arenaNoPathFinder(boolean need, @Share("plzArena") LocalBooleanRef arena) {
        return need && !arena.get();
    }

    @WrapOperation(
        method = "updateRemotePlayer()Z",
        at = @At(value = "INVOKE", target = "Lzombie/pathfind/PathFindBehavior2;moveToPoint(FFF)V")
    )
    private void plz$arenaPlace(
        PathFindBehavior2 pfb2, float x, float y, float speedMul, Operation<Void> original, @Share("plzArena") LocalBooleanRef arena
    ) {
        if (arena.get()) {
            PLZArenaInterp.place((IsoPlayer)(Object)this, x, y);
        } else {
            original.call(pfb2, x, y, speedMul);
        }
    }

    @Inject(
        method = "updateRemotePlayer()Z",
        at = @At(value = "INVOKE", target = "Lzombie/ai/states/PlayerSitOnGroundState;instance()Lzombie/ai/states/PlayerSitOnGroundState;")
    )
    private void plz$arenaMoving(
        CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 1) boolean moveToEvent, @Share("plzArena") LocalBooleanRef arena
    ) {
        if (arena.get()) {
            this.isPlayerMoving = moveToEvent || PLZArenaInterp.isMoving((IsoPlayer)(Object)this);
        }
    }

    // Aim-strafe animates off deltaX/Y, so drive them from the interpolated velocity rather than the chase target.
    @Inject(method = "updateRemotePlayer()Z", at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;isAnimal()Z", ordinal = 2))
    private void plz$arenaFace(CallbackInfoReturnable<Boolean> cir, @Share("plzArena") LocalBooleanRef arena) {
        if (!arena.get()) {
            return;
        }

        IsoPlayer self = (IsoPlayer)(Object)this;
        PLZArenaInterp.face(self);
        PLZArenaInterp.Track track = PLZArenaInterp.track(self);
        if (track == null || !self.isAiming() && !self.isCurrentState(PlayerStrafeState.instance())) {
            return;
        }

        if (!track.outMoving) {
            this.deltaX = 0.0F;
            this.deltaY = 0.0F;
            return;
        }

        tempo.set(track.outVX, -track.outVY);
        tempo.normalize();
        float angle = self.getAnimationPlayer().getRenderedAngle();
        if (angle > Math.PI * 2) {
            angle = (float)(angle - Math.PI * 2);
        }

        if (angle < 0.0F) {
            angle = (float)(angle + Math.PI * 2);
        }

        tempo.rotate(angle);
        tempo.setLength(Math.min(track.outSpeed, 1.0F));
        this.deltaX = tempo.x;
        this.deltaY = tempo.y;
    }
}

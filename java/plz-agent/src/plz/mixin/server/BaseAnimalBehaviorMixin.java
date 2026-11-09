package plz.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.animals.IsoAnimal;
import zombie.characters.animals.behavior.BaseAnimalBehavior;
import zombie.core.skinnedmodel.advancedanimation.AnimationVariableHandles;
import zombie.iso.IsoUtils;
import zombie.plz.PLZAnimalLead;
import zombie.plz.access.PLZIsoAnimalAccess;

@Mixin(BaseAnimalBehavior.class)
abstract class BaseAnimalBehaviorMixin {
    @Unique
    private static final int PLZ_LEAD_WANDER = 2;
    @Unique
    private static final float PLZ_LEAD_FOLLOW_TICKS = 50.0F;
    @Unique
    private static final float PLZ_LEAD_SLACK = 3.0F;
    @Unique
    private static final float PLZ_LEAD_TAUT = 1.5F;
    @Unique
    private static final float PLZ_LEAD_FOLLOW_MOVING_TICKS = 15.0F;

    @Shadow
    protected IsoAnimal parent;

    @Shadow
    public boolean blockMovement;

    @Shadow
    private float followChrTimer;

    @Shadow
    private void walkToChr(IsoGameCharacter chr, Integer pushedLength) {
        throw new AssertionError();
    }

    @ModifyExpressionValue(
        method = "wanderIdle",
        at = @At(value = "INVOKE", target = "Lzombie/characters/animals/IsoAnimal;isAlerted()Z", ordinal = 0)
    )
    private boolean plz$ledHorseNeverWanders(boolean alerted) {
        return alerted || ((PLZIsoAnimalAccess) this.parent).plzIsLedHorse();
    }

    // Vanilla takes Rand.Next(16) - 8; a tethered animal keeps to PLZ_LEAD_WANDER tiles of its anchor instead.
    @WrapOperation(method = "wanderIdle", at = @At(value = "INVOKE", target = "Lzombie/core/random/Rand;Next(I)I"))
    private int plz$tetheredWander(int max, Operation<Integer> original) {
        boolean tethered = this.parent.getData().getAttachedPlayer() != null || this.parent.getData().getAttachedTree() != null;
        if (!tethered) {
            return original.call(max);
        }

        return original.call(PLZ_LEAD_WANDER * 2 + 1) - PLZ_LEAD_WANDER + 8;
    }

    /**
     * @author PLZ
     * @reason The lead goes taut the moment the player sets off, grazing or not; led horses path to their own target; tighter slack.
     */
    @Overwrite
    private void followChr() {
        if (this.blockMovement) {
            return;
        }

        IsoGameCharacter plzLeader = this.parent.getData().getAttachedPlayer();
        boolean plzEating = this.parent.eatFromTrough != null || this.parent.eatFromGround != null;
        boolean plzSetOff = plzLeader != null
            && plzLeader.isPlayerMoving()
            && !this.parent.isMoving()
            && IsoUtils.DistanceTo(this.parent.getX(), this.parent.getY(), plzLeader.getX(), plzLeader.getY()) > PLZ_LEAD_TAUT;
        if (plzEating) {
            if (!plzSetOff) {
                return;
            }

            this.parent.stopAllMovementNow();
        }

        if (plzLeader == null) {
            this.parent.setVariable("animalSpeed", 1.0F);
            this.followChrTimer = 0.0F;
            return;
        }

        PLZIsoAnimalAccess horse = (PLZIsoAnimalAccess) this.parent;
        if (horse.plzIsLedHorse()) {
            float plzDist = IsoUtils.DistanceTo(this.parent.getX(), this.parent.getY(), plzLeader.getX(), plzLeader.getY());
            if (plzDist >= 15.0F) {
                this.parent.getData().getAttachedPlayer().getAttachedAnimals().remove(this.parent);
                this.parent.getData().setAttachedPlayer(null);
            } else if (horse.plzHorseShouldMove() && !this.parent.isAnimalMoving()) {
                horse.plzMoveToHorseTarget(plzLeader);
            }

            return;
        }

        if (this.followChrTimer > 0.0F && !plzSetOff) {
            this.followChrTimer = this.followChrTimer - GameTime.getInstance().getMultiplier();
            return;
        }

        IsoGameCharacter chr = plzLeader;
        this.followChrTimer = chr.isPlayerMoving() ? PLZ_LEAD_FOLLOW_MOVING_TICKS : PLZ_LEAD_FOLLOW_TICKS;
        float dist = IsoUtils.DistanceTo(this.parent.getX(), this.parent.getY(), chr.getX(), chr.getY());
        if (chr.isPlayerMoving() && dist > PLZ_LEAD_TAUT) {
            this.walkToChr(chr, 2);
            if (PLZAnimalLead.leaderRuns(chr)) {
                this.parent.setVariable(AnimationVariableHandles.animalRunning, true);
            }
        }

        if (dist < PLZ_LEAD_SLACK && !this.parent.isMoving()) {
            return;
        }

        if (dist >= PLZ_LEAD_SLACK && !chr.isPlayerMoving()) {
            this.walkToChr(chr, 5);
        }

        if (dist >= 15.0F) {
            this.parent.getData().getAttachedPlayer().getAttachedAnimals().remove(this.parent);
            this.parent.getData().setAttachedPlayer(null);
        }
    }
}

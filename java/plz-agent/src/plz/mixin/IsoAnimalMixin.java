package plz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import plz.agent.LuaPublic;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import zombie.GameTime;
import zombie.MovingObjectUpdateScheduler;
import zombie.WorldSoundManager;
import zombie.ai.states.animals.AnimalPathFindState;
import zombie.ai.states.animals.AnimalWalkState;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.NetworkPlayerAI;
import zombie.characters.animals.AnimalDefinitions;
import zombie.characters.animals.IsoAnimal;
import zombie.characters.animals.datas.AnimalData;
import zombie.core.math.PZMath;
import zombie.core.random.Rand;
import zombie.core.skinnedmodel.BaseGrappleable;
import zombie.core.skinnedmodel.advancedanimation.AnimationVariableHandles;
import zombie.debug.DebugLog;
import zombie.inventory.types.Food;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoUtils;
import zombie.iso.Vector2;
import zombie.iso.areas.DesignationZoneAnimal;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.pathfind.PathFindBehavior2;
import zombie.plz.PLZAnimalCalm;
import zombie.plz.PLZAnimalForm;
import zombie.plz.PLZAnimalLead;
import zombie.plz.PLZAnimalOwner;
import zombie.plz.PLZAnimalProtect;
import zombie.plz.PLZAnimalZone;
import zombie.plz.PLZFixes;
import zombie.plz.access.PLZBaseGrappleableAccess;
import zombie.plz.access.PLZIsoAnimalAccess;
import zombie.popman.animal.AnimalInstanceManager;

@Mixin(IsoAnimal.class)
abstract class IsoAnimalMixin extends IsoPlayer implements PLZIsoAnimalAccess {
    @Unique
    private static final float PLZ_LEAD_ROPE = 6.0F;
    @Unique
    private static final float PLZ_TETHER_ROPE = 2.0F;
    @Unique
    private static final float PLZ_LEAD_PACE_FROM = 1.5F;
    @Unique
    private static final float PLZ_LEAD_PACE_PER_TILE = 0.4F;
    @Unique
    private static final float PLZ_LEAD_PACE_MAX = 1.8F;
    @Unique
    private static final float PLZ_HORSE_LEAD_MAX_SPEED = 9.0F;
    @Unique
    private static final float PLZ_HORSE_LEAD_SIDE = 0.85F;
    @Unique
    private static final float PLZ_HORSE_LEAD_FORWARD = -0.5F;
    @Unique
    private static final String PLZ_HORSE_LEAD_SLOT = "plzHorseLead";
    @Unique
    private static final float PLZ_HORSE_LEAD_WELD_RANGE = 4.0F;
    @Unique
    private static final float PLZ_HORSE_LEAD_LOOKAHEAD = 0.5F;
    @Unique
    private static final float PLZ_HORSE_REMOTE_CATCHUP = 4.0F;
    @Unique
    private static final float PLZ_HORSE_WALK_CLIP_PER_TILE = 0.94F;
    @Unique
    private static final float PLZ_HORSE_TROT_CLIP_PER_TILE = 0.275F;
    @Unique
    private static final float PLZ_HORSE_LEAD_GAIN = 3.0F;
    @Unique
    private static final float PLZ_HORSE_LEAD_SETTLE = 0.6F;
    @Unique
    private static final float PLZ_HORSE_LEAD_WALK_ON = 0.35F;
    @Unique
    private static final float PLZ_HORSE_LEAD_WALK_OFF = 0.15F;
    @Unique
    private static final float PLZ_HORSE_LEAD_SET_OFF = 0.8F;
    @Unique
    private static final float PLZ_HORSE_LEAD_MOVING = 0.3F;

    @Shadow
    private AnimalData data;

    @Shadow
    public AnimalDefinitions adef;

    @Shadow
    public boolean fromMeta;

    @Shadow
    private WorldSoundManager.WorldSound lastSoundRespondedTo;

    @Shadow
    public float stressLevel;

    @Shadow
    private DesignationZoneAnimal dZone;

    @Shadow
    public int attachBackToMother;

    @Unique
    private long plz$netSeenMs;
    @Unique
    private long plz$netAskedMs;
    @Unique
    private int plz$netAsks;
    // World-age hour this animal last ran live, -1 until it has.
    @Unique
    private double plz$liveHour;
    // World-age hour it came back from disk or an unloaded area, -1 once its catch-up has run.
    @Unique
    private double plz$backHour;
    @Unique
    private double plz$catchUpRest;

    @Unique
    private boolean plz$leadWelded;
    @Unique
    private long plz$remoteGlideNanos;
    @Unique
    private boolean plz$driveActive;
    @Unique
    private float plz$driveVX;
    @Unique
    private float plz$driveVY;
    @Unique
    private boolean plz$leadWalkVar;
    @Unique
    private float plz$repathTimer;
    @Unique
    private boolean plz$ledVar;
    @Unique
    private boolean plz$leadRunVar;
    @Unique
    private float plz$leaderLastX;
    @Unique
    private float plz$leaderLastY;
    @Unique
    private float plz$leaderVX;
    @Unique
    private float plz$leaderVY;
    @Unique
    private int plz$horseSide;
    @Unique
    private float plz$horseTargetX;
    @Unique
    private float plz$horseTargetY;
    @Unique
    private float plz$horseErr;
    @Unique
    private boolean plz$horseLeaderMoving;
    @Unique
    private long plz$leadDriveNanos;

    private IsoAnimalMixin() {
        super((IsoCell) null);
    }

    @Shadow
    public abstract boolean isWild();

    @Shadow
    public abstract String getAnimalType();

    @Shadow
    public abstract int getAnimalID();

    @Shadow
    public abstract boolean isAnimalMoving();

    @Shadow
    public abstract void pathToLocation(int x, int y, int z);

    @Unique
    private IsoAnimal plz$self() {
        return (IsoAnimal) (Object) this;
    }

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$liveHour = -1.0;
        this.plz$backHour = -1.0;
    }

    @Override
    public long plzGetNetSeenMs() {
        return this.plz$netSeenMs;
    }

    @Override
    public void plzSetNetSeenMs(long ms) {
        this.plz$netSeenMs = ms;
    }

    @Override
    public long plzGetNetAskedMs() {
        return this.plz$netAskedMs;
    }

    @Override
    public void plzSetNetAskedMs(long ms) {
        this.plz$netAskedMs = ms;
    }

    @Override
    public int plzGetNetAsks() {
        return this.plz$netAsks;
    }

    @Override
    public void plzSetNetAsks(int asks) {
        this.plz$netAsks = asks;
    }

    // CallbackInfo per tick is accepted here: the handler is tiny, so C2 scalar-replaces it, and only a cancel can skip update().
    @Inject(method = "update()V", at = @At("HEAD"), cancellable = true)
    private void plz$updateGuards(CallbackInfo ci) {
        if (this.adef == null && GameServer.server && PLZFixes.on(PLZFixes.ANIMAL_UPDATE_GUARD) && !this.plz$recoverOrDetachNullDef()) {
            ci.cancel();
            return;
        }

        if (GameServer.server && PLZFixes.on(PLZFixes.ANIMAL_REGISTRY)) {
            this.plz$ensureMapEntry();
        }

        if (!GameClient.client && this.plz$backHour < 0.0) {
            this.plz$liveHour = GameTime.getInstance().getWorldAgeHours();
        }
    }

    @Inject(
        method = "updateInternal",
        at = @At(value = "FIELD", target = "Lzombie/network/GameClient;client:Z", opcode = Opcodes.GETSTATIC, ordinal = 0)
    )
    private void plz$leadPace(CallbackInfo ci) {
        this.plz$updateLeadPace();
    }

    @Inject(
        method = "updateInternal",
        at = {
            @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;update()V", ordinal = 0, shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;update()V", ordinal = 1, shift = At.Shift.AFTER)
        }
    )
    private void plz$attachLedHorseOnClient(CallbackInfo ci) {
        this.plz$attachLedHorse();
    }

    @Inject(
        method = "updateInternal",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;update()V", ordinal = 2, shift = At.Shift.AFTER)
    )
    private void plz$driveLedHorseOnServer(CallbackInfo ci) {
        this.plz$driveLedHorse();
    }

    // Keeps one animal with an unresolvable definition from stopping the whole world: recover the type, else detach it.
    @Unique
    private boolean plz$recoverOrDetachNullDef() {
        AnimalDefinitions resolved = AnimalDefinitions.getDef(this.getAnimalType());
        if (resolved != null) {
            this.adef = resolved;
            DebugLog.log("PLZFixes: recovered null adef for animal type=" + this.getAnimalType() + " id=" + this.getAnimalID());
            PLZFixes.hit(PLZFixes.ANIMAL_UPDATE_GUARD);
            return true;
        }

        IsoCell cell = this.getCell();
        if (cell != null) {
            cell.getObjectList().remove(this);
            cell.getAddList().remove(this);
            cell.getRemoveList().remove(this);
            MovingObjectUpdateScheduler.instance.removeObject(this.plz$self());
            if (this.getCurrentSquare() != null) {
                this.getCurrentSquare().getMovingObjects().remove(this);
            }

            if (this.getLastSquare() != null) {
                this.getLastSquare().getMovingObjects().remove(this);
            }
        }

        DebugLog.log("PLZFixes: removed animal with unrecognised type=" + this.getAnimalType()
            + " id=" + this.getAnimalID() + " cellPresent=" + (cell != null));
        PLZFixes.hit(PLZFixes.ANIMAL_UPDATE_GUARD);
        return false;
    }

    // virtualizeAnimal deletes the id but the return trip never re-adds it, leaving a live animal unreachable by id.
    @Unique
    private void plz$ensureMapEntry() {
        AnimalInstanceManager manager = AnimalInstanceManager.getInstance();
        if (manager == null) {
            return;
        }

        short id = this.getOnlineID();
        IsoAnimal inMap = manager.get(id);
        if (inMap == (Object) this) {
            return;
        }

        // id 1 is "never assigned", -1 is "no free slot"; both need a fresh allocation, as does a slot someone else owns.
        if (id == 1 || id == -1 || inMap != null) {
            short fresh = manager.allocateID();
            if (fresh == -1) {
                return;
            }

            manager.add(this.plz$self(), fresh);
            PLZFixes.hit(PLZFixes.ANIMAL_REGISTRY);
            return;
        }

        manager.add(this.plz$self(), id);
        PLZFixes.hit(PLZFixes.ANIMAL_REGISTRY);
    }

    // A vehicle's animal list holds nulls by design; skipping the empty slot is enough. Never equal: attachBackToMother > 0 here.
    @WrapOperation(method = "reattachBackToMom", at = @At(value = "INVOKE", target = "Lzombie/characters/animals/IsoAnimal;getAnimalID()I"))
    private int plz$skipEmptySeat(IsoAnimal mom, Operation<Integer> original) {
        if (mom == null && PLZFixes.on(PLZFixes.ANIMAL_REATTACH)) {
            PLZFixes.hit(PLZFixes.ANIMAL_REATTACH);
            return -1;
        }

        return original.call(mom);
    }

    // Vanilla only clears ignoredTrough in removeFromWorld, so one failed approach blacklisted a stocked trough for good.
    @Inject(
        method = "checkZone",
        at = @At(value = "INVOKE", target = "Lzombie/iso/areas/DesignationZoneAnimal;getZoneF(FFF)Lzombie/iso/areas/DesignationZoneAnimal;")
    )
    private void plz$expireIgnoredTroughs(CallbackInfo ci) {
        IsoAnimal self = this.plz$self();
        if (!self.ignoredTrough.isEmpty() && PLZFixes.on(PLZFixes.ANIMAL_TROUGH_EXPIRY)) {
            self.ignoredTrough.clear();
            PLZFixes.hit(PLZFixes.ANIMAL_TROUGH_EXPIRY);
        }
    }

    // The ambient sources only; incStress stays true when skipped so a calmed animal winds down.
    @WrapOperation(method = "updateStress", at = @At(value = "INVOKE", target = "Lzombie/characters/animals/IsoAnimal;shouldAnimalStressAboveGround()Z"))
    private boolean plz$calmAboveGround(IsoAnimal animal, Operation<Boolean> original) {
        return !PLZAnimalCalm.isCalm() && original.call(animal);
    }

    @WrapOperation(
        method = "updateStress",
        at = @At(value = "FIELD", target = "Lzombie/characters/animals/AnimalDefinitions;stressUnderRain:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean plz$calmUnderRain(AnimalDefinitions def, Operation<Boolean> original) {
        return !PLZAnimalCalm.isCalm() && original.call(def);
    }

    // A settled animal neither bolts nor stresses from a noise; wild ones still do, or hunting breaks.
    @Inject(
        method = "respondToSound",
        at = @At(value = "INVOKE", target = "Lzombie/WorldSoundManager;getNew()Lzombie/WorldSoundManager$WorldSound;"),
        cancellable = true
    )
    private void plz$calmIgnoresSound(CallbackInfo ci) {
        if (PLZAnimalCalm.isCalm() && !this.isWild()) {
            this.lastSoundRespondedTo = WorldSoundManager.instance.release(this.lastSoundRespondedTo);
            ci.cancel();
        }
    }

    @WrapOperation(
        method = "spotted(Lzombie/iso/IsoMovingObject;ZF)V",
        at = @At(value = "INVOKE", target = "Lzombie/characters/animals/behavior/BaseAnimalBehavior;spotted(Lzombie/iso/IsoMovingObject;ZF)V")
    )
    private void plz$morphedUnseen(
        zombie.characters.animals.behavior.BaseAnimalBehavior behavior, IsoMovingObject other, boolean bForced, float dist, Operation<Void> original
    ) {
        if (!PLZAnimalForm.isMorphed(other)) {
            original.call(behavior, other, bForced, dist);
        }
    }

    // Somebody else's horse cannot be beaten to death; returns before the blood, the stress and the attack-back.
    @Inject(method = "hitConsequences", at = @At("HEAD"), cancellable = true)
    private void plz$protectedFromHarm(
        HandWeapon weapon, IsoGameCharacter wielder, boolean bIgnoreDamage, float damage, boolean bRemote, CallbackInfo ci
    ) {
        if (!PLZAnimalProtect.mayHarm(wielder, this.plz$self())) {
            ci.cancel();
        }
    }

    /**
     * @author PLZ
     * @reason A calm herd skips the whole per-animal body (stress roll and forced flee) when one of them is butchered.
     */
    @Overwrite
    public void killed(IsoPlayer chr) {
        if (this.getHealth() <= 0.0F && this.dZone != null) {
            ArrayList<IsoAnimal> animals = this.dZone.getAnimalsConnected();

            for (int i = 0; i < animals.size(); i++) {
                IsoAnimal animal = animals.get(i);
                if (PLZAnimalCalm.isCalm() && !animal.isWild()) {
                    continue;
                }

                animal.changeStress(Rand.Next(10.0F, 30.0F));
                if (chr != null && animal.DistToProper(chr) < 10.0F) {
                    animal.getBehavior().forceFleeFromChr(chr);
                }
            }
        }
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void plz$stampBackFromDisk(ByteBuffer input, int worldVersion, boolean isDebugSave, CallbackInfo ci) {
        this.plz$backHour = GameTime.getInstance().getWorldAgeHours();
    }

    // Starting stress scales with world age, so on an old server every new animal would start pinned at 70.
    @ModifyExpressionValue(method = "initStress", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"))
    private float plz$capInitialStress(float stress) {
        return PLZAnimalCalm.cap(stress, this.isWild());
    }

    @ModifyConstant(
        method = {
            "canGoThere(Lzombie/iso/IsoGridSquare;)Z",
            "pathToCharacter(Lzombie/characters/IsoGameCharacter;)V",
            "pathToLocation(III)V"
        },
        constant = @Constant(floatValue = 15.0F, ordinal = 0)
    )
    private float plz$leadRope(float vanilla) {
        return PLZ_LEAD_ROPE;
    }

    @ModifyConstant(
        method = {
            "canGoThere(Lzombie/iso/IsoGridSquare;)Z",
            "pathToCharacter(Lzombie/characters/IsoGameCharacter;)V",
            "pathToLocation(III)V"
        },
        constant = @Constant(floatValue = 15.0F, ordinal = 1)
    )
    private float plz$tetherRope(float vanilla) {
        return PLZ_TETHER_ROPE;
    }

    // The newborn takes its mother's farm here, the one moment the mother is in hand without an id lookup.
    @Inject(method = "addBaby", at = @At(value = "INVOKE", target = "Lzombie/characters/animals/IsoAnimal;getVehicle()Lzombie/vehicles/BaseVehicle;", ordinal = 0))
    private void plz$inheritOwner(CallbackInfoReturnable<IsoAnimal> cir, @Local(name = "baby") IsoAnimal baby) {
        PLZAnimalOwner.inherit(this.plz$self(), baby);
    }

    @ModifyVariable(method = "updateStatsAway", at = @At("HEAD"), argsOnly = true)
    private int plz$ownAwayHours(int hours) {
        if (this.fromMeta && !GameClient.client) {
            this.plz$backHour = GameTime.getInstance().getWorldAgeHours();
        }

        return this.plz$awayHours(hours);
    }

    // Vanilla drops the part-day, so a farm visited more often than every 24 game hours never ages.
    @ModifyVariable(method = "updateStatsAway", at = @At(value = "STORE", ordinal = 0), name = "deltaAge")
    private int plz$carryPartDay(int deltaAge, @Local(name = "age") int age, @Local(name = "hours") int hours) {
        this.plz$catchUpRest = 0.0;
        if (!PLZFixes.on(PLZFixes.ANIMAL_CATCH_UP)) {
            return deltaAge;
        }

        double carried = Math.min(23.0, Math.max(0.0, this.getHoursSurvived() - age * 24.0)) + hours;
        int carriedDays = (int) (carried / 24.0);
        this.plz$catchUpRest = carried - carriedDays * 24.0;
        return carriedDays;
    }

    @ModifyArg(method = "updateStatsAway", at = @At(value = "INVOKE", target = "Lzombie/characters/animals/IsoAnimal;setHoursSurvived(D)V"))
    private double plz$keepPartDay(double hoursSurvived) {
        return hoursSurvived + this.plz$catchUpRest;
    }

    // A zone's clock is not this animal's. Connected zones disagree, and one return is simulated more than once.
    @Unique
    private int plz$awayHours(int hours) {
        if (GameClient.client || !PLZFixes.on(PLZFixes.ANIMAL_AWAY_CLAMP)) {
            return hours;
        }

        // A zero-hour call is fromWorker before the zone is connected; stamping on it ate the real catch-up that follows.
        if (hours <= 0) {
            return hours;
        }

        double now = GameTime.getInstance().getWorldAgeHours();
        int own = hours;
        if (this.plz$backHour >= 0.0) {
            own = Math.min(own, hours - (int) Math.max(0.0, now - this.plz$backHour));
            if (this.plz$liveHour >= 0.0) {
                own = Math.min(own, (int) Math.max(0.0, this.plz$backHour - this.plz$liveHour));
            }
        } else if (this.plz$liveHour >= 0.0) {
            own = Math.min(own, (int) Math.max(0.0, now - this.plz$liveHour));
        }

        own = Math.max(0, own);
        this.plz$backHour = -1.0;
        this.plz$liveHour = now;
        if (own < hours) {
            PLZFixes.hit(PLZFixes.ANIMAL_AWAY_CLAMP);
        }

        return own;
    }

    // The chick is built hours later with no reference to this hen; the fertilised egg carries the farm.
    @Inject(
        method = "createEgg",
        at = @At(value = "FIELD", target = "Lzombie/inventory/types/Food;eggGenome:Ljava/util/HashMap;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER)
    )
    private void plz$stampEgg(CallbackInfoReturnable<Food> cir, @Local(name = "egg") Food egg) {
        PLZAnimalOwner.stampEgg(this.plz$self(), egg);
    }

    // copyFrom carries all ModData, but the pen stays behind because a player chose where the animal landed.
    @Inject(method = "copyFrom", at = @At("RETURN"))
    private void plz$dropPenKeys(IsoAnimal animal, CallbackInfo ci) {
        KahluaTable modData = this.getModData();
        ArrayList<String> penKeys = new ArrayList<>();
        KahluaTableIterator it = modData.iterator();
        while (it.advance()) {
            if (it.getKey() instanceof String key && key.startsWith(PLZAnimalOwner.PEN_PREFIX)) {
                penKeys.add(key);
            }
        }
        for (int i = 0; i < penKeys.size(); i++) {
            modData.rawset(penKeys.get(i), null);
        }
    }

    // The ceiling: stress raised from BaseAnimalBehavior and IsoHutch arrives here too.
    @ModifyExpressionValue(method = "changeStress", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"))
    private float plz$capStress(float stress) {
        return PLZAnimalCalm.cap(stress, this.isWild());
    }

    // Despite the name, live paths (a nearby zombie, being struck) write stress through here.
    @ModifyVariable(method = "setDebugStress", at = @At("HEAD"), argsOnly = true)
    private float plz$capDebugStress(float stress) {
        return PLZAnimalCalm.cap(stress, this.isWild());
    }

    // Every wander, flee, follow and forage target passes through here, so one clamp closes the zone's holes.
    @WrapMethod(method = "pathToLocation(III)V")
    private void plz$clampToZone(int x, int y, int z, Operation<Void> original) {
        long clamped = PLZAnimalZone.clampTarget(this.plz$self(), x, y, z);
        if (clamped != PLZAnimalZone.NO_CLAMP) {
            x = PLZAnimalZone.unpackX(clamped);
            y = PLZAnimalZone.unpackY(clamped);
        }

        original.call(x, y, z);
    }

    @ModifyReturnValue(method = "shouldBreakObstaclesDuringPathfinding", at = @At("RETURN"))
    private boolean plz$containedNeverBreaksOut(boolean vanilla) {
        return PLZAnimalZone.allowObstacleBreaking(this.plz$self(), vanilla);
    }

    // This method is the break-out: a calm tame animal never picks the fence in front of it as a thump target.
    @ModifyExpressionValue(
        method = "animalShouldThump",
        at = @At(value = "FIELD", target = "Lzombie/characters/animals/AnimalDefinitions;canThump:Z", opcode = Opcodes.GETFIELD, ordinal = 0)
    )
    private boolean plz$calmNeverThumps(boolean canThump) {
        return canThump && !(PLZAnimalCalm.isCalm() && !this.isWild());
    }

    @ModifyReturnValue(method = "animalShouldThump", at = @At("RETURN"))
    private boolean plz$containedNeverThumps(boolean vanilla) {
        return PLZAnimalZone.allowObstacleBreaking(this.plz$self(), vanilla);
    }

    // Re-adding moved the animal to the end of the list doMeta is walking by index, so the next animal was skipped.
    @Inject(method = "setDZone", at = @At("HEAD"), cancellable = true)
    private void plz$keepZoneSlot(DesignationZoneAnimal dZone, CallbackInfo ci) {
        if (dZone != null
            && dZone == this.dZone
            && !GameClient.client
            && PLZFixes.on(PLZFixes.ANIMAL_CATCH_UP)
            && dZone.getAnimalsInZone().contains(this)) {
            ci.cancel();
        }
    }

    // Postupdate sibling of the update() guard: doStairs calls this before a null-def animal leaves the bucket.
    @WrapOperation(
        method = "canClimbStairs",
        at = @At(value = "FIELD", target = "Lzombie/characters/animals/AnimalDefinitions;canClimbStairs:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean plz$nullDefNoStairs(AnimalDefinitions def, Operation<Boolean> original) {
        if (def == null && GameServer.server && PLZFixes.on(PLZFixes.ANIMAL_CLIMB_STAIRS_GUARD)) {
            PLZFixes.hit(PLZFixes.ANIMAL_CLIMB_STAIRS_GUARD);
            return false;
        }

        return original.call(def);
    }

    @WrapOperation(method = "updateLOS", at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;isGhostMode()Z"))
    private boolean plz$morphedPlayerUnseen(IsoPlayer player, Operation<Boolean> original) {
        return original.call(player) || PLZAnimalForm.isMorphed(player);
    }

    @Unique
    @LuaPublic
    private static boolean plzLeaderRuns(IsoGameCharacter leader) {
        return PLZAnimalLead.leaderRuns(leader);
    }

    // Lua bridges: zombie.plz is not exposed, while IsoAnimal statics are.
    @Unique
    @LuaPublic
    private static void plzSetAnimalCalm(boolean value) {
        PLZAnimalCalm.setCalm(value);
    }

    @Unique
    @LuaPublic
    private static boolean plzGetAnimalCalm() {
        return PLZAnimalCalm.isCalm();
    }

    @Unique
    @LuaPublic
    private static void plzAnimalProtectEnabled(boolean value) {
        PLZAnimalProtect.setEnabled(value);
    }

    @Unique
    @LuaPublic
    private static void plzAnimalProtectBegin() {
        PLZAnimalProtect.begin();
    }

    @Unique
    @LuaPublic
    private static void plzAnimalProtectAdd(String username, String ownerKeysCsv) {
        PLZAnimalProtect.add(username, ownerKeysCsv);
    }

    @Unique
    @LuaPublic
    private static void plzAnimalProtectApply() {
        PLZAnimalProtect.apply();
    }

    // Drives the plzLeadPathfind nodes, whose walk speeds up as the animal falls behind the player.
    @Unique
    private void plz$updateLeadPace() {
        IsoPlayer leader = this.data.getAttachedPlayer();
        boolean led = leader != null;
        if (led != this.plz$ledVar) {
            this.plz$ledVar = led;
            this.setVariable("plzLed", led);
        }

        boolean run = led && PLZAnimalLead.leaderRuns(leader);
        if (run != this.plz$leadRunVar) {
            this.plz$leadRunVar = run;
            this.setVariable("plzLeadRun", run);
        }

        if (led && this.getVariableBoolean("isHorse")) {
            float speed;
            if (this.plz$leadWelded) {
                speed = (float)Math.sqrt(this.plz$leaderVX * this.plz$leaderVX + this.plz$leaderVY * this.plz$leaderVY);
            } else if (GameClient.client) {
                speed = this.getNetworkCharacterAI().prediction.speed;
            } else {
                speed = (float)Math.sqrt(this.plz$driveVX * this.plz$driveVX + this.plz$driveVY * this.plz$driveVY);
            }

            float perTile = run ? PLZ_HORSE_TROT_CLIP_PER_TILE : PLZ_HORSE_WALK_CLIP_PER_TILE;
            this.setVariable("plzLeadSpeed", PZMath.clamp(speed * perTile, 0.6F, 2.5F));
        } else if (led) {
            float dist = IsoUtils.DistanceTo(this.getX(), this.getY(), leader.getX(), leader.getY());
            this.setVariable("plzLeadSpeed", PZMath.clamp(1.0F + (dist - PLZ_LEAD_PACE_FROM) * PLZ_LEAD_PACE_PER_TILE, 1.0F, PLZ_LEAD_PACE_MAX));
        }
    }

    // HorseMod's nodes turn root motion off, so the path follower gives a led horse no speed of its own.
    @Unique
    private void plz$driveLedHorse() {
        IsoPlayer leader = this.data.getAttachedPlayer();
        // HorseMod drops the lead on the rider's client only; its mount lock is the one thing that disables an animal's deferred movement.
        if (leader != null && !this.isDeferredMovementEnabled()) {
            leader.getAttachedAnimals().remove(this);
            this.data.setAttachedPlayer(null);
            leader = null;
        }

        if (leader == null || !this.getVariableBoolean("isHorse")) {
            this.plz$leadDriveNanos = 0L;
            this.plz$horseSide = 0;
            this.plz$driveActive = false;
            return;
        }

        float dt = this.plz$trackLeader(leader);
        if (dt <= 0.0F) {
            return;
        }

        float speed = (float)Math.sqrt(this.plz$leaderVX * this.plz$leaderVX + this.plz$leaderVY * this.plz$leaderVY);
        this.plz$horseLeaderMoving = speed > PLZ_HORSE_LEAD_MOVING;
        Vector2 facing = leader.getForwardDirection();
        float fLen = (float)Math.sqrt(facing.x * facing.x + facing.y * facing.y);
        float fx = fLen > 0.001F ? facing.x / fLen : 1.0F;
        float fy = fLen > 0.001F ? facing.y / fLen : 0.0F;
        this.plz$horseTargetX = leader.getX() + this.plz$leaderVX * PLZ_HORSE_LEAD_LOOKAHEAD + fx * PLZ_HORSE_LEAD_FORWARD - fy * PLZ_HORSE_LEAD_SIDE;
        this.plz$horseTargetY = leader.getY() + this.plz$leaderVY * PLZ_HORSE_LEAD_LOOKAHEAD + fy * PLZ_HORSE_LEAD_FORWARD + fx * PLZ_HORSE_LEAD_SIDE;

        this.plz$horseErr = IsoUtils.DistanceTo(this.getX(), this.getY(), this.plz$horseTargetX, this.plz$horseTargetY);
        PathFindBehavior2 pfb = this.getPathFindBehavior2();
        if (!this.isCurrentState(AnimalPathFindState.instance())) {
            this.plz$repathTimer = 1.0F;
        }

        this.plz$driveActive = false;
        if (this.isCurrentState(AnimalWalkState.instance())) {
            if (!this.plz$horseLeaderMoving && this.plz$horseErr < PLZ_HORSE_LEAD_SETTLE) {
                this.setVariable("bMoving", false);
                return;
            }

            float vx = this.plz$leaderVX + (this.plz$horseTargetX - this.getX()) * PLZ_HORSE_LEAD_GAIN;
            float vy = this.plz$leaderVY + (this.plz$horseTargetY - this.getY()) * PLZ_HORSE_LEAD_GAIN;
            float vLen = (float)Math.sqrt(vx * vx + vy * vy);
            if (vLen > PLZ_HORSE_LEAD_MAX_SPEED) {
                vx = vx / vLen * PLZ_HORSE_LEAD_MAX_SPEED;
                vy = vy / vLen * PLZ_HORSE_LEAD_MAX_SPEED;
                vLen = PLZ_HORSE_LEAD_MAX_SPEED;
            }

            this.plz$driveVX = vx;
            this.plz$driveVY = vy;
            this.plz$driveActive = true;
            if (vLen > 0.001F) {
                // AnimalWalkState drops bMoving within half a tile of its target, so its target is kept ahead of the horse.
                pfb.setData(this.getX() + vx / vLen * 3.0F, this.getY() + vy / vLen * 3.0F, leader.getZ());
            }

            this.setVariable("bMoving", true);
            pfb.walkingOnTheSpot.reset(this.getX(), this.getY());
        } else if (this.isCurrentState(AnimalPathFindState.instance())) {
            this.plz$repathTimer -= dt;
            if (this.plz$repathTimer <= 0.0F && this.plz$horseErr > PLZ_HORSE_LEAD_SET_OFF) {
                this.plz$repathTimer = 1.0F;
                this.plzMoveToHorseTarget(leader);
            }

            float px = pfb.pathNextX - this.getX();
            float py = pfb.pathNextY - this.getY();
            float pLen = (float)Math.sqrt(px * px + py * py);
            if (pfb.pathNextIsSet && pLen > 0.001F) {
                float mag = PZMath.min(speed + PLZ_HORSE_LEAD_GAIN * this.plz$horseErr, PLZ_HORSE_LEAD_MAX_SPEED);
                this.plz$driveVX = px / pLen * mag;
                this.plz$driveVY = py / pLen * mag;
                this.plz$driveActive = true;
            }
        }
    }

    // Walk state applies deferred movement as given, the path follower uses its length, and the network prediction sends it to clients.
    @Override
    protected Vector2 getDeferredMovement(Vector2 result, boolean reset) {
        super.getDeferredMovement(result, reset);
        if (this.plz$driveActive && !GameClient.client) {
            float td = GameTime.getInstance().getTimeDelta();
            result.set(this.plz$driveVX * td, this.plz$driveVY * td);
        }

        return result;
    }

    // Smoothed leader velocity in tiles a second; returns the seconds since the last call, or 0 when that is unusable.
    @Unique
    private float plz$trackLeader(IsoPlayer leader) {
        long now = System.nanoTime();
        float dt = this.plz$leadDriveNanos == 0L ? 0.0F : (now - this.plz$leadDriveNanos) / 1.0E9F;
        this.plz$leadDriveNanos = now;
        float lx = leader.getX();
        float ly = leader.getY();
        boolean usable = dt > 0.0F && dt < 0.5F;
        if (usable) {
            float sx = lx - this.plz$leaderLastX;
            float sy = ly - this.plz$leaderLastY;
            if (sx * sx + sy * sy < 4.0F) {
                float blend = PZMath.min(1.0F, dt * 6.0F);
                this.plz$leaderVX += (sx / dt - this.plz$leaderVX) * blend;
                this.plz$leaderVY += (sy / dt - this.plz$leaderVY) * blend;
            }
        }

        this.plz$leaderLastX = lx;
        this.plz$leaderLastY = ly;
        return usable ? dt : 0.0F;
    }

    // Welded to the leader's rendered position each frame on this client, as an escorted player is, so network lag never shows.
    @Unique
    private void plz$attachLedHorse() {
        BaseGrappleable grapple = this.getWrappedGrappleable();
        IsoPlayer leader = this.data.getAttachedPlayer();
        boolean want = grapple != null
            && leader != null
            && leader.getCurrentSquare() != null
            && this.getVariableBoolean("isHorse")
            && !this.getVariableBoolean("HorseRiding")
            && IsoUtils.DistanceTo(this.realx, this.realy, leader.getX(), leader.getY()) < PLZ_HORSE_LEAD_WELD_RANGE;
        if (want) {
            this.plz$trackLeader(leader);
            if (((PLZBaseGrappleableAccess) grapple).plzGetAnchor() != leader) {
                ((PLZBaseGrappleableAccess) grapple).plzAttach(leader, PLZ_HORSE_LEAD_SLOT);
            }

            if (!this.plz$leadWelded) {
                this.plz$leadWelded = true;
                this.setVariable("plzLeadWeld", true);
            }

            // The server's walk, idle and path states flap and each change would restart the clip, so the leader decides here.
            float speed = (float)Math.sqrt(this.plz$leaderVX * this.plz$leaderVX + this.plz$leaderVY * this.plz$leaderVY);
            boolean walk = this.plz$leadWalkVar ? speed > PLZ_HORSE_LEAD_WALK_OFF : speed > PLZ_HORSE_LEAD_WALK_ON;
            if (walk != this.plz$leadWalkVar) {
                this.plz$leadWalkVar = walk;
                this.setVariable("plzLeadWalk", walk);
            }
        } else if (this.plz$leadWelded) {
            this.plz$leadWelded = false;
            this.plz$leadDriveNanos = 0L;
            this.setVariable("plzLeadWeld", false);
            if (grapple != null) {
                ((PLZBaseGrappleableAccess) grapple).plzDetach();
            }
        }

        if (!this.plz$leadWelded) {
            this.plz$glideRemoteLedHorse();
        }
    }

    // The client moves a remote animal at its root-motion speed, which a HorseMod node does not have.
    @Unique
    private void plz$glideRemoteLedHorse() {
        if (!this.plzIsLedHorse() || this.getVariableBoolean("HorseRiding")) {
            this.plz$remoteGlideNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        float dt = this.plz$remoteGlideNanos == 0L ? 0.0F : (now - this.plz$remoteGlideNanos) / 1.0E9F;
        this.plz$remoteGlideNanos = now;
        if (dt <= 0.0F || dt >= 0.5F) {
            return;
        }

        NetworkPlayerAI ai = this.getNetworkCharacterAI();
        float dx = ai.targetX - this.getX();
        float dy = ai.targetY - this.getY();
        float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 0.02F || len > 10.0F) {
            return;
        }

        float move = PZMath.min((ai.prediction.speed + len * PLZ_HORSE_REMOTE_CATCHUP) * dt, len);
        this.moveUnmodded(dx / len * move, dy / len * move);
        if (len > 0.1F) {
            this.setForwardDirection(dx, dy);
        } else {
            this.setForwardDirection(ai.direction);
        }

        this.getForwardDirection().normalize();
    }

    @Override
    public boolean plzIsLedHorse() {
        return this.data != null && this.data.getAttachedPlayer() != null && this.getVariableBoolean("isHorse");
    }

    @Override
    public boolean plzHorseShouldMove() {
        return this.plz$horseLeaderMoving || this.plz$horseErr > PLZ_HORSE_LEAD_SET_OFF;
    }

    @Override
    public void plzMoveToHorseTarget(IsoGameCharacter leader) {
        this.pathToLocation(PZMath.fastfloor(this.plz$horseTargetX), PZMath.fastfloor(this.plz$horseTargetY), PZMath.fastfloor(leader.getZ()));
    }
}

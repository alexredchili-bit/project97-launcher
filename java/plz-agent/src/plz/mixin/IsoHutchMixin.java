package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.HashMap;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plz.agent.LuaPublic;
import se.krka.kahlua.j2se.KahluaTableImpl;
import zombie.GameTime;
import zombie.Lua.LuaManager;
import zombie.characters.animals.IsoAnimal;
import zombie.core.random.Rand;
import zombie.debug.DebugType;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoHutch;
import zombie.network.GameClient;
import zombie.plz.PLZFixes;

@Mixin(IsoHutch.class)
abstract class IsoHutchMixin {
    // Vanilla's rates are per tick and per real second, tuned at 60 fps on a 90-minute day.
    @Unique
    private static final float PLZ_BASELINE_GAME_SECONDS_PER_TICK = 86400.0F / (90.0F * 60.0F * 60.0F);

    @Shadow
    public HashMap<Integer, IsoAnimal> animalInside;

    @Shadow
    public ArrayList<IsoAnimal> animalOutside;

    @Shadow
    public String type;

    @Shadow
    public int savedX;

    @Shadow
    public int savedY;

    @Shadow
    public int savedZ;

    // Birds the hutch had no perch for, let out on the next update instead of dropped.
    @Unique
    private ArrayList<IsoAnimal> plz$unseated;

    @Shadow
    public abstract int getEnterSpotX();

    @Shadow
    public abstract int getEnterSpotY();

    @Shadow
    private void releaseAnimal(IsoGridSquare animalSq, IsoAnimal animal) {
        throw new AssertionError();
    }

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void plz$initUnseated(CallbackInfo ci) {
        this.plz$unseated = new ArrayList<>();
    }

    @Inject(
        method = "update()V",
        at = @At(value = "FIELD", target = "Lzombie/iso/objects/IsoHutch;updateAnimal:Lzombie/core/utils/UpdateLimit;", opcode = Opcodes.GETFIELD, ordinal = 0)
    )
    private void plz$releaseUnseatedFirst(CallbackInfo ci) {
        this.plz$releaseUnseated();
    }

    @WrapOperation(method = "update()V", at = @At(value = "INVOKE", target = "Lzombie/core/random/Rand;NextBool(I)Z", ordinal = 0))
    private boolean plz$rollFloorDirt(int prob, Operation<Boolean> original) {
        if (!PLZFixes.on(PLZFixes.HUTCH_GAME_TIME)) {
            return original.call(prob);
        }

        PLZFixes.hit(PLZFixes.HUTCH_GAME_TIME);
        return Rand.NextBool(plz$gameTimeScale() / prob);
    }

    // animalOutside is every animal in the zone, so a pen of cows filled a chicken hutch at 100% per hour.
    @WrapOperation(method = "doMeta", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;size()I", ordinal = 0))
    private int plz$outsideResidents(ArrayList<?> outside, Operation<Integer> original) {
        if (!PLZFixes.on(PLZFixes.HUTCH_META_RESIDENTS)) {
            return original.call(outside);
        }

        PLZFixes.hit(PLZFixes.HUTCH_META_RESIDENTS);
        int residents = 0;

        for (int i = 0; i < this.animalOutside.size(); i++) {
            IsoAnimal animal = this.animalOutside.get(i);
            if (animal != null && animal.adef != null && animal.adef.hutches != null && animal.adef.hutches.contains(this.type)) {
                residents++;
            }
        }

        return residents;
    }

    @WrapOperation(method = "updateAnimalHealthInside", at = @At(value = "INVOKE", target = "Lzombie/GameTime;getMultiplier()F"))
    private float plz$drainMultiplier(GameTime gameTime, Operation<Float> original) {
        if (!PLZFixes.on(PLZFixes.HUTCH_GAME_TIME)) {
            return original.call(gameTime);
        }

        PLZFixes.hit(PLZFixes.HUTCH_GAME_TIME);
        return 0.8F * plz$gameTimeScale();
    }

    // No death event fires in here, so Lua is told before the corpse copies the bird's ModData.
    @Inject(method = "killAnimal", at = @At("HEAD"))
    private void plz$reportDeath(IsoAnimal animal, CallbackInfo ci) {
        if (GameClient.client || !PLZFixes.on(PLZFixes.HUTCH_DEATH_LOG)) {
            return;
        }

        try {
            Object log = LuaManager.env == null ? null : LuaManager.env.rawget("AnimalDeathLog");
            Object function = log instanceof KahluaTableImpl table ? table.rawget("onHutchDeath") : null;
            if (function == null) {
                return;
            }

            LuaManager.caller.protectedCallVoid(LuaManager.thread, function, animal, this);
            PLZFixes.hit(PLZFixes.HUTCH_DEATH_LOG);
        } catch (Throwable failure) {
            DebugType.Animal.warn("PLZ hutch death log failed: " + failure);
        }
    }

    // Every caller but the walk-in ignores a false, and the bird is then in no list at all.
    @WrapOperation(
        method = "addAnimalInside(Lzombie/characters/animals/IsoAnimal;Z)Z",
        at = @At(value = "INVOKE", target = "Lzombie/iso/objects/IsoHutch;pickRandomUnoccupiedHutchPosition()I")
    )
    private int plz$keepUnseatedWhenFull(IsoHutch hutch, Operation<Integer> original, @Local(argsOnly = true) IsoAnimal animal) {
        int position = original.call(hutch);
        if (position == -1) {
            this.plz$keepUnseated(animal);
        }

        return position;
    }

    @Unique
    @LuaPublic
    private static boolean plzIsPatched() {
        return true;
    }

    @Unique
    private static float plz$gameTimeScale() {
        return GameTime.getInstance().getGameWorldSecondsSinceLastUpdate() / PLZ_BASELINE_GAME_SECONDS_PER_TICK;
    }

    @Unique
    private void plz$keepUnseated(IsoAnimal animal) {
        if (GameClient.client || !PLZFixes.on(PLZFixes.HUTCH_SEAT_OR_RELEASE)) {
            return;
        }

        // One that walked up is still standing outside and needs nothing.
        if (animal.getCurrentSquare() != null || this.plz$unseated.contains(animal)) {
            return;
        }

        this.plz$unseated.add(animal);
    }

    @Unique
    private void plz$releaseUnseated() {
        if (this.plz$unseated.isEmpty()) {
            return;
        }

        IsoGridSquare spot = IsoWorld.instance
            .currentCell
            .getGridSquare(this.savedX + this.getEnterSpotX(), this.savedY + this.getEnterSpotY(), this.savedZ);
        if (spot == null) {
            return;
        }

        for (int i = this.plz$unseated.size() - 1; i >= 0; i--) {
            IsoAnimal animal = this.plz$unseated.remove(i);
            if (animal.isDead() || animal.getCurrentSquare() != null || this.animalInside.containsValue(animal)) {
                continue;
            }

            animal.nestBox = -1;
            // releaseAnimal clears the perch this names, and a stale one belongs to another bird.
            animal.getData().setHutchPosition(-1);
            this.releaseAnimal(spot, animal);
            this.plz$tellLua("AnimalMoveLog", "onHutchUnseated", animal);
            PLZFixes.hit(PLZFixes.HUTCH_SEAT_OR_RELEASE);
        }
    }

    @Unique
    private void plz$tellLua(String module, String name, IsoAnimal animal) {
        try {
            Object table = LuaManager.env == null ? null : LuaManager.env.rawget(module);
            Object function = table instanceof KahluaTableImpl lua ? lua.rawget(name) : null;
            if (function != null) {
                LuaManager.caller.protectedCallVoid(LuaManager.thread, function, animal, this);
            }
        } catch (Throwable failure) {
            DebugType.Animal.warn("PLZ hutch report failed: " + failure);
        }
    }
}

package plz.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import plz.agent.LuaPublic;
import zombie.ai.State;
import zombie.characters.ClothingWetness;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.Moodles.Moodles;
import zombie.characters.WornItems.BodyLocationGroup;
import zombie.characters.WornItems.WornItem;
import zombie.characters.WornItems.WornItems;
import zombie.debug.DebugLog;
import zombie.debug.DebugType;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.Clothing;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.network.GameClient;
import zombie.network.GameServer;
import zombie.plz.PLZAfkKick;
import zombie.plz.PLZDowned;
import zombie.plz.PLZGrappleIK;
import zombie.plz.PLZLiveToken;
import zombie.plz.PLZStation;
import zombie.plz.PLZStationClamp;
import zombie.plz.PLZVisibilityResync;
import zombie.plz.PLZWornLock;
import zombie.scripting.objects.ItemBodyLocation;
import zombie.scripting.objects.MoodleType;

@Mixin(IsoGameCharacter.class)
abstract class IsoGameCharacterMixin {
    @Unique
    private static final float PLZ_HEARTY_APPETITE = 1.25F;
    @Unique
    private static final float PLZ_LIGHT_EATER = 0.85F;

    @Shadow
    protected WornItems wornItems;

    @Shadow
    protected ClothingWetness clothingWetness;

    @Shadow
    protected IsoPlayer isoPlayer;

    @Shadow
    private IsoGridSquare getSolidFloorAt(int x, int y, int z) {
        throw new AssertionError();
    }

    @Shadow
    private static void performDropItemOnSquare(IsoGridSquare onSquare, InventoryItem droppedHandItem) {
        throw new AssertionError();
    }

    @Unique
    private IsoGameCharacter plz$self() {
        return (IsoGameCharacter) (Object) this;
    }

    /**
     * @author PLZ
     * @reason Routes through plz$setWornItem, which reorders the body for PLZWornLock and layered wear.
     */
    @Overwrite
    public void setWornItem(ItemBodyLocation location, InventoryItem item, boolean forceDropTooHeavy) {
        this.plz$setWornItem(location, item, forceDropTooHeavy, null);
    }

    /**
     * @author PLZ
     * @reason setItem(location, null) takes the FIRST entry there, which is the wrong layer when several are stacked.
     */
    @Overwrite
    public void removeWornItem(InventoryItem item, boolean forceDropTooHeavy) {
        ItemBodyLocation location = this.wornItems.getLocation(item);
        boolean plzLayer = item != null && location != null && this.wornItems.getItem(location) != item
            && (Object) this instanceof IsoPlayer player && PLZWornLock.isLayered(player.getUsername());
        this.plz$setWornItem(location, null, forceDropTooHeavy, plzLayer ? item : null);
    }

    @Unique
    private void plz$setWornItem(ItemBodyLocation location, InventoryItem item, boolean forceDropTooHeavy, InventoryItem plzLayerToRemove) {
        // A locked location refuses down in WornItems.setItem; leave before the force-drop takes a piece still being worn.
        if (this.plz$refusesWornChange(location, item)) {
            return;
        }

        IsoGameCharacter self = this.plz$self();
        InventoryItem itemCur = plzLayerToRemove != null ? plzLayerToRemove : this.wornItems.getItem(location);
        if (item != itemCur) {
            IsoCell cell = IsoWorld.instance.currentCell;
            if (plzLayerToRemove != null) {
                this.wornItems.remove(plzLayerToRemove);
            } else {
                this.wornItems.setItem(location, item);
            }

            // A layered wear leaves the previous piece on, so it must not be un-processed or force-dropped.
            if (itemCur != null && this.wornItems.contains(itemCur)) {
                itemCur = null;
            }

            if (itemCur != null && cell != null) {
                cell.addToProcessItemsRemove(itemCur);
            }
            if (item != null && cell != null) {
                if (item.getContainer() != null) {
                    item.getContainer().parent = self;
                }

                cell.addToProcessItems(item);
            }

            if (forceDropTooHeavy && itemCur != null && self instanceof IsoPlayer && !self.getInventory().hasRoomFor(self, itemCur) && !GameClient.client) {
                IsoGridSquare sq = self.getCurrentSquare();
                sq = this.getSolidFloorAt(sq.x, sq.y, sq.z);
                if (sq != null) {
                    if (GameServer.server) {
                        GameServer.sendRemoveItemFromContainer(self.getInventory(), itemCur);
                    }

                    self.getInventory().Remove(itemCur);
                    performDropItemOnSquare(sq, itemCur);
                }
            }

            if (this.isoPlayer != null
                && this.isoPlayer.getHumanVisual().getHairModel().contains("Mohawk")
                && (Objects.equals(location, ItemBodyLocation.HAT) || Objects.equals(location, ItemBodyLocation.FULL_HAT))) {
                this.isoPlayer.getHumanVisual().setHairModel("MohawkFlat");
                self.resetModel();
            }

            self.resetModelNextFrame();
            if (this.clothingWetness != null) {
                this.clothingWetness.changed = true;
            }

            self.syncClothing();
            self.onWornItemsChanged();
        }
    }

    // Only an emptying: WornItems stacks an add at a locked location, so refusing one here would undo that.
    @Unique
    private boolean plz$refusesWornChange(ItemBodyLocation location, InventoryItem item) {
        if (item != null) {
            return false;
        }
        if (!PLZWornLock.isActive() || location == null || this.wornItems == null) {
            return false;
        }
        if (!((Object) this instanceof IsoPlayer player)) {
            return false;
        }
        if (this.wornItems.getItem(location) == null) {
            return false;
        }
        return PLZWornLock.isLocked(player.getUsername(), location.getTranslationName());
    }

    // A body location holds one item; duplicate entries are corruption that makes the save throw at 128.
    @Inject(method = "save(Ljava/nio/ByteBuffer;Z)V", at = @At("HEAD"))
    private void plz$pruneBeforeSave(ByteBuffer output, boolean isDebugSave, CallbackInfo ci) {
        this.plz$pruneDuplicateWornItems();
    }

    @Unique
    private void plz$pruneDuplicateWornItems() {
        WornItems worn = this.wornItems;
        if (worn == null || worn.size() < 2) {
            return;
        }

        HashSet<String> seen = new HashSet<>();
        List<InventoryItem> doomed = null;
        BodyLocationGroup group = worn.getBodyLocationGroup();

        for (int i = 0; i < worn.size(); i++) {
            WornItem entry = worn.get(i);
            if (entry == null) {
                continue;
            }

            if (entry.getLocation() != null && group != null && group.isMultiItem(entry.getLocation())) {
                continue;
            }

            String location = entry.getLocation() == null ? "<null>" : entry.getLocation().toString();
            InventoryItem item = entry.getItem();
            if (seen.add(location) && item != null) {
                continue;
            }

            if (doomed == null) {
                doomed = new ArrayList<>();
            }
            if (item != null) {
                doomed.add(item);
            }
        }

        if (doomed == null || doomed.isEmpty()) {
            return;
        }

        int before = worn.size();
        for (int i = 0; i < doomed.size(); i++) {
            worn.remove(doomed.get(i));
        }

        DebugLog.log(
            DebugType.General,
            "PLZ: pruned " + doomed.size() + " duplicate worn-item entr" + (doomed.size() == 1 ? "y" : "ies")
                + " from " + this.plz$whoAmI() + " (" + before + " -> " + worn.size()
                + "). A body location holds one item; the rest were corruption and would have made"
                + " the save throw at 128."
        );
    }

    @Unique
    private String plz$whoAmI() {
        if ((Object) this instanceof IsoPlayer player) {
            String username = player.getUsername();
            if (username != null && !username.isEmpty()) {
                return username;
            }
        }

        String name = this.plz$self().getFullName();
        return name == null || name.isEmpty() ? this.toString() : name;
    }

    public boolean plzKickAfk(String messageKey) {
        return PLZAfkKick.kick(this.plz$self(), messageKey);
    }

    @Inject(method = "hitConsequences", at = @At(value = "INVOKE", target = "Lzombie/characters/IsoGameCharacter;isDead()Z", ordinal = 0))
    private void plz$downedAfterHit(CallbackInfo ci) {
        PLZDowned.enforce(this.plz$self());
    }

    @Inject(
        method = "updateInternal",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoGameCharacter;updateBandages()V", shift = At.Shift.AFTER)
    )
    private void plz$downedAfterBodyUpdate(CallbackInfo ci) {
        PLZDowned.enforce(this.plz$self());
    }

    @Inject(
        method = "onHitByVehicleApplyDamage",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/characters/IsoGameCharacter;applyDamageFromVehicleHit(Lzombie/vehicles/BaseVehicle;FF)V",
            shift = At.Shift.AFTER
        )
    )
    private void plz$downedAfterVehicleHit(CallbackInfoReturnable<Float> cir) {
        PLZDowned.enforce(this.plz$self());
    }

    @Inject(method = "applyDamage(F)V", at = @At("RETURN"))
    private void plz$downedAfterDamage(float damageAmount, CallbackInfo ci) {
        PLZDowned.enforce(this.plz$self());
    }

    @Inject(
        method = "Kill(Lzombie/characters/IsoGameCharacter;Lzombie/inventory/types/HandWeapon;ZLzombie/characters/CharacterDiedListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void plz$downedNotKilled(CallbackInfo ci) {
        if (PLZDowned.preventDeath(this.plz$self())) {
            ci.cancel();
        }
    }

    @Inject(method = "die()V", at = @At("HEAD"), cancellable = true)
    private void plz$downedNotDead(CallbackInfo ci) {
        if (PLZDowned.preventDeath(this.plz$self())) {
            ci.cancel();
        }
    }

    @Inject(method = {"dieNetwork", "becomeCorpse"}, at = @At("HEAD"), cancellable = true)
    private void plz$downedNoCorpse(CallbackInfoReturnable<zombie.iso.objects.IsoDeadBody> cir) {
        if (PLZDowned.preventDeath(this.plz$self())) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
        method = {"throwGrappledTargetOutWindow", "throwGrappledOverFence", "throwGrappledIntoInventory"},
        at = @At("HEAD"),
        cancellable = true
    )
    private void plz$escortIsNeverThrown(CallbackInfo ci) {
        if ("PLZEscort".equals(this.plz$self().getSharedGrappleType())) {
            ci.cancel();
        }
    }

    // A locked piece never falls off: reading its chance as zero skips the roll and the removal.
    @WrapOperation(
        method = "helmetFallFromWornItems",
        at = @At(value = "INVOKE", target = "Lzombie/inventory/types/Clothing;getChanceToFall()I", ordinal = 1)
    )
    private int plz$lockedNeverFalls(Clothing clothing, Operation<Integer> original, @Local(name = "wornItem") WornItem wornItem) {
        if ((Object) this instanceof IsoPlayer player
            && PLZWornLock.isActive()
            && PLZWornLock.isLocked(player.getUsername(), wornItem.getLocation().getTranslationName())) {
            return 0;
        }

        return original.call(clothing);
    }

    // One hunger rate for every state, so diet pacing per in-game day never depends on jogging or Well Fed.
    @WrapOperation(method = "updateStats_Awake", at = @At(value = "INVOKE", target = "Lzombie/characters/IsoPlayer;IsRunning()Z"))
    private boolean plz$hungerIgnoresRunning(IsoPlayer player, Operation<Boolean> original) {
        return false;
    }

    @WrapOperation(
        method = "updateStats_Awake",
        at = @At(value = "INVOKE", target = "Lzombie/characters/IsoGameCharacter;isCurrentState(Lzombie/ai/State;)Z", ordinal = 0)
    )
    private boolean plz$hungerIgnoresSwipe(IsoGameCharacter character, State state, Operation<Boolean> original) {
        return false;
    }

    @WrapOperation(
        method = "updateStats_Awake",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/characters/Moodles/Moodles;getMoodleLevel(Lzombie/scripting/objects/MoodleType;)I",
            ordinal = 1
        )
    )
    private int plz$hungerIgnoresWellFed(Moodles moodles, MoodleType type, Operation<Integer> original) {
        return 0;
    }

    @ModifyConstant(method = "getAppetiteMultiplier", constant = @Constant(floatValue = 1.5F))
    private float plz$heartyAppetite(float vanilla) {
        return PLZ_HEARTY_APPETITE;
    }

    @ModifyConstant(method = "getAppetiteMultiplier", constant = @Constant(floatValue = 0.75F))
    private float plz$lightEater(float vanilla) {
        return PLZ_LIGHT_EATER;
    }

    @ModifyReturnValue(method = "getAnimationTimeDelta", at = @At("RETURN"))
    private float plz$frozenGrapple(float delta) {
        return PLZGrappleIK.frozen(this.plz$self()) ? 0.0F : delta;
    }

    // Becoming visible must replay the action state entered while invisible, or a seated player never reaches others.
    @WrapMethod(method = "setInvisible(Z)V")
    private void plz$resyncOnVisible(boolean b, Operation<Void> original) {
        boolean wasInvisible = this.plz$self().isInvisible();
        original.call(b);
        PLZVisibilityResync.onVisibilityChanged(this.plz$self(), wasInvisible, this.plz$self().isInvisible());
    }

    // The forced branch writes the cheat directly, so it needs its own edge check.
    @WrapMethod(method = "setInvisible(ZZ)V")
    private void plz$resyncOnForcedVisible(boolean b, boolean isForced, Operation<Void> original) {
        if (!isForced) {
            original.call(b, isForced);
            return;
        }

        boolean wasInvisible = this.plz$self().isInvisible();
        original.call(b, isForced);
        PLZVisibilityResync.onVisibilityChanged(this.plz$self(), wasInvisible, this.plz$self().isInvisible());
    }

    @Unique
    @LuaPublic
    private static void plzSetDowned(String username, boolean value) {
        PLZDowned.setDowned(username, value);
    }

    @Unique
    @LuaPublic
    private static boolean plzIsDowned(String username) {
        return PLZDowned.isDowned(username);
    }

    @Unique
    @LuaPublic
    private static int plzDownedCount() {
        return PLZDowned.count();
    }

    @Unique
    @LuaPublic
    private static void plzClearDowned() {
        PLZDowned.clear();
    }

    @Unique
    @LuaPublic
    private static void plzSetAllowDeath(String username, boolean value) {
        PLZDowned.setAllowDeath(username, value);
    }

    @Unique
    @LuaPublic
    private static String plzListDowned() {
        return PLZDowned.listDowned();
    }

    @Unique
    @LuaPublic
    private static String plzDownedPatchStatus() {
        return PLZDowned.patchStatus();
    }

    @Unique
    @LuaPublic
    private static String plzLiveNewStream() {
        return PLZLiveToken.newStream();
    }

    @Unique
    @LuaPublic
    private static String plzLiveToken(String stream, double ttlSeconds) {
        return PLZLiveToken.token(stream, (long) ttlSeconds);
    }

    @Unique
    @LuaPublic
    private static void plzStationSetFrequencies(String csv) {
        PLZStationClamp.setFrequencies(csv);
    }

    @Unique
    @LuaPublic
    private static void plzStationSetHosts(double freq, String csvUsernames) {
        PLZStationClamp.setHosts((int) freq, csvUsernames);
    }

    @Unique
    @LuaPublic
    private static double plzStationTrackMs(String soundName) {
        return (double) PLZStation.trackMs(soundName);
    }

    @Unique
    @LuaPublic
    private static String plzStationStatus() {
        return "freqs=" + PLZStation.frequencyList() + " hosts=" + PLZStationClamp.hostList()
            + " clamped=" + PLZStationClamp.clamped;
    }
}

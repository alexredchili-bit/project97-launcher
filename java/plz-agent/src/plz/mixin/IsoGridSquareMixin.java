package plz.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import plz.agent.LuaPublic;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.animals.IsoAnimal;
import zombie.chat.ChatManager;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.areas.IsoRoom;
import zombie.network.GameServer;
import zombie.network.ServerMap;
import zombie.network.chat.ChatServer;
import zombie.plz.PLZAssetRefusals;
import zombie.plz.PLZBarrierGrid;
import zombie.plz.PLZChannelProbe;
import zombie.plz.PLZDoorAccess;
import zombie.plz.PLZEventSkip;
import zombie.plz.PLZFixes;
import zombie.plz.PLZFlightRecorder;
import zombie.plz.PLZGatedGrid;
import zombie.plz.PLZItemWeightCache;
import zombie.plz.PLZLuaProfile;
import zombie.plz.PLZMapPin;
import zombie.plz.PLZMapScan;
import zombie.plz.PLZMusicStream;
import zombie.plz.PLZObjectDamage;
import zombie.plz.PLZObjectUpdate;
import zombie.plz.PLZRemoteHeal;
import zombie.plz.PLZSnowClear;
import zombie.plz.PLZSoundPriority;
import zombie.plz.PLZSoundProbe;
import zombie.plz.PLZSyncWatch;
import zombie.plz.PLZTextureRepair;
import zombie.plz.PLZUtilities;
import zombie.plz.PLZVariableSync;

@Mixin(IsoGridSquare.class)
abstract class IsoGridSquareMixin {
    @Shadow
    public int x;

    @Shadow
    public int y;

    @Shadow
    public int z;

    @Shadow
    @Final
    private PropertyContainer properties;

    // PLZ: a barrier tile collides with everything. The vanilla body is side-effect free, so forcing its
    // result equals the shadow's early return without a per-call CallbackInfo on this LosUtil-hot path.
    @ModifyReturnValue(method = "CalculateCollide(Lzombie/iso/IsoGridSquare;ZZZZLzombie/iso/IsoGridSquare$GetSquare;)Z", at = @At("RETURN"))
    private boolean plz$barrierCollides(boolean collides, @Local(argsOnly = true) IsoGridSquare gridSquare) {
        return collides || gridSquare != null && PLZBarrierGrid.isBlocked(gridSquare.x, gridSquare.y);
    }

    @Inject(
        method = "RecalcProperties",
        at = @At(value = "FIELD", target = "Lzombie/iso/IsoGridSquare;propertiesDirty:Z", opcode = Opcodes.PUTFIELD)
    )
    private void plz$barrierIsSolidTrans(CallbackInfo ci) {
        if (PLZBarrierGrid.isBlocked(this.x, this.y)) {
            this.properties.set(IsoFlagType.solidtrans);
        }
    }

    // PLZ: a gated area refuses a player exactly as a no-trespass safehouse does; allowTrepass is read nowhere else.
    @ModifyExpressionValue(
        method = "testCollideAdjacent(Lzombie/iso/IsoMovingObject;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lzombie/iso/areas/SafeHouse;isSafehouseAllowTrepass(Lzombie/iso/IsoGridSquare;Lzombie/characters/IsoPlayer;)Z"
        )
    )
    private boolean plz$gatedRefusesPlayer(
        boolean allowTrepass,
        @Local(index = 2) int x,
        @Local(index = 3) int y,
        @Local(index = 4) int z,
        @Local(index = 6) IsoPlayer isoPlayer
    ) {
        if (allowTrepass
            && (GameServer.server || isoPlayer.isLocalPlayer())
            && PLZGatedGrid.blocksPlayer(this.x + x, this.y + y, this.z + z, isoPlayer)) {
            return false;
        }

        return allowTrepass;
    }

    // PLZ: removeIsoRoom guts a live IsoRoom in place, and a square the re-stamp sweep misses serves the husk; consumers NPE on getRoomDef().
    @ModifyReturnValue(method = "getRoom", at = @At("RETURN"))
    private IsoRoom plz$noGuttedRoom(IsoRoom room) {
        if (room != null && PLZFixes.on(PLZFixes.GRID_SQUARE_ROOM_GUARD) && room.getRoomDef() == null) {
            PLZFixes.hit(PLZFixes.GRID_SQUARE_ROOM_GUARD);
            return null;
        }

        return room;
    }

    @ModifyReturnValue(method = "haveElectricity", at = @At("RETURN"))
    private boolean plz$powerEverywhere(boolean powered) {
        return powered || PLZUtilities.powerEverywhere();
    }

    // LuaManager's exposer is a whitelist without zombie.plz, so Lua reaches these statics on a class it already exposes.
    @Unique
    @LuaPublic
    private static void plzAnimalFormMark(IsoAnimal animal) {
        zombie.plz.PLZAnimalForm.mark(animal);
    }

    @Unique
    @LuaPublic
    private static void plzAnimalFormRelease(IsoAnimal animal) {
        zombie.plz.PLZAnimalForm.release(animal);
    }

    @Unique
    @LuaPublic
    private static boolean plzAnimalFormIsStandIn(Object object) {
        return zombie.plz.PLZAnimalForm.isStandIn(object);
    }

    @Unique
    @LuaPublic
    private static void plzAnimalFormBindStride(IsoGameCharacter mover, IsoAnimal animal) {
        zombie.plz.PLZAnimalForm.bindStride(mover, animal);
    }

    @Unique
    @LuaPublic
    private static void plzAnimalFormUnbindStride(IsoGameCharacter mover) {
        zombie.plz.PLZAnimalForm.unbindStride(mover);
    }

    @Unique
    @LuaPublic
    private static void plzAnimalFormSetMorphed(String username, boolean morphed) {
        zombie.plz.PLZAnimalForm.setMorphed(username, morphed);
    }

    @Unique
    @LuaPublic
    private static void plzBarrierBeginUpdate() {
        PLZBarrierGrid.beginUpdate();
    }

    @Unique
    @LuaPublic
    private static void plzBarrierAddSegment(double x1, double y1, double x2, double y2) {
        PLZBarrierGrid.addSegment(x1, y1, x2, y2);
    }

    @Unique
    @LuaPublic
    private static int plzBarrierEndUpdate() {
        return PLZBarrierGrid.endUpdate();
    }

    @Unique
    @LuaPublic
    private static boolean plzBarrierIsBlocked(double x, double y) {
        return PLZBarrierGrid.isBlocked((int)Math.floor(x), (int)Math.floor(y));
    }

    @Unique
    @LuaPublic
    private static int plzBarrierTileCount() {
        return PLZBarrierGrid.getTileCount();
    }

    @Unique
    @LuaPublic
    private static String plzBarrierPatchStatus() {
        return PLZBarrierGrid.patchStatus();
    }

    @Unique
    @LuaPublic
    private static void plzBarrierPrintStatusBanner() {
        PLZBarrierGrid.printStatusBanner();
    }

    @Unique
    @LuaPublic
    private static boolean plzTakeAssetRefusalReport() {
        return PLZAssetRefusals.takeReport();
    }

    @Unique
    @LuaPublic
    private static int plzAssetRefusalCount() {
        return PLZAssetRefusals.getTotal();
    }

    @Unique
    @LuaPublic
    private static String plzAssetRefusalMods() {
        return PLZAssetRefusals.getModList();
    }

    @Unique
    @LuaPublic
    private static void plzSoundSample() {
        PLZSoundProbe.sample();
    }

    @Unique
    @LuaPublic
    private static boolean plzVitalsAvailable() {
        return zombie.plz.PLZClientVitals.isAvailable();
    }

    @Unique
    @LuaPublic
    private static String plzVitalsFailure() {
        return zombie.plz.PLZClientVitals.getFailure();
    }

    @Unique
    @LuaPublic
    private static String plzVitalsKeys() {
        return zombie.plz.PLZClientVitals.keys();
    }

    @Unique
    @LuaPublic
    private static void plzVitalsStart(int intervalMs) {
        zombie.plz.PLZClientVitals.start(intervalMs);
    }

    @Unique
    @LuaPublic
    private static void plzVitalsStop() {
        zombie.plz.PLZClientVitals.stop();
    }

    @Unique
    @LuaPublic
    private static double plzVitalsGet(String key) {
        return zombie.plz.PLZClientVitals.get(key);
    }

    @Unique
    @LuaPublic
    private static String plzVitalsEnvironment() {
        return zombie.plz.PLZClientVitals.getEnvironment();
    }

    @Unique
    @LuaPublic
    private static String plzVitalsCrashEvidence() {
        return zombie.plz.PLZClientVitals.getCrashEvidence();
    }

    @Unique
    @LuaPublic
    private static boolean plzSoundProbeAvailable() {
        return PLZSoundProbe.isAvailable();
    }

    @Unique
    @LuaPublic
    private static String plzSoundProbeFailure() {
        return PLZSoundProbe.getFailure();
    }

    @Unique
    @LuaPublic
    private static int plzSoundInstances() {
        return PLZSoundProbe.getTotal();
    }

    @Unique
    @LuaPublic
    private static int plzSoundStarting() {
        return PLZSoundProbe.getStarting();
    }

    @Unique
    @LuaPublic
    private static int plzSoundPlaying() {
        return PLZSoundProbe.getPlaying();
    }

    @Unique
    @LuaPublic
    private static int plzSoundStateCount(int state) {
        return PLZSoundProbe.getStateCount(state);
    }

    @Unique
    @LuaPublic
    private static String plzSoundWorstEvent() {
        return PLZSoundProbe.getWorstEvent();
    }

    @Unique
    @LuaPublic
    private static int plzSoundWorstCount() {
        return PLZSoundProbe.getWorstCount();
    }

    @Unique
    @LuaPublic
    private static boolean plzChannelProbeAvailable() {
        return PLZChannelProbe.isAvailable();
    }

    @Unique
    @LuaPublic
    private static String plzChannelProbeFailure() {
        return PLZChannelProbe.getFailure();
    }

    @Unique
    @LuaPublic
    private static int plzVoiceVirtualFrames() {
        return (int)PLZChannelProbe.getVirtualFrames();
    }

    @Unique
    @LuaPublic
    private static int plzVoiceRealFrames() {
        return (int)PLZChannelProbe.getRealFrames();
    }

    @Unique
    @LuaPublic
    private static int plzVoiceVirtualPercent() {
        return PLZChannelProbe.getVirtualPercent();
    }

    @Unique
    @LuaPublic
    private static int plzVoiceSinceVirtualMs() {
        return (int)PLZChannelProbe.sinceLastVirtualMs();
    }

    @Unique
    @LuaPublic
    private static int plzVoiceMutedFrames() {
        return (int)PLZChannelProbe.getMutedFrames();
    }

    @Unique
    @LuaPublic
    private static int plzVoicePeakOpen() {
        return PLZChannelProbe.getPeakOpen();
    }

    @Unique
    @LuaPublic
    private static int plzVoicePeakAudible() {
        return PLZChannelProbe.getPeakAudible();
    }

    @Unique
    @LuaPublic
    private static int plzVoicePeakStolen() {
        return PLZChannelProbe.getPeakStolen();
    }

    @Unique
    @LuaPublic
    private static void plzVoiceVirtualReset() {
        PLZChannelProbe.reset();
    }

    @Unique
    @LuaPublic
    private static boolean plzSetSayRange(double tiles) {
        return GameServer.server && ((zombie.plz.access.PLZChatServerAccess) ChatServer.getInstance()).plzSetSayRange((float)tiles);
    }

    @Unique
    @LuaPublic
    private static void plzChatHealthCheck() {
        ChatManager manager = ChatManager.getInstance();
        if (manager != null) {
            ((zombie.plz.access.PLZChatManagerAccess) manager).plzHealthCheck();
        }
    }

    @Unique
    @LuaPublic
    private static void plzApplySoundPriority() {
        PLZSoundPriority.apply();
    }

    @Unique
    @LuaPublic
    private static boolean plzSoundPriorityRan() {
        return PLZSoundPriority.hasRun();
    }

    @Unique
    @LuaPublic
    private static int plzSoundPriorityApplied() {
        return PLZSoundPriority.getApplied();
    }

    @Unique
    @LuaPublic
    private static int plzSoundPriorityScanned() {
        return PLZSoundPriority.getScanned();
    }

    @Unique
    @LuaPublic
    private static String plzSoundPriorityFailure() {
        return PLZSoundPriority.getFailure();
    }

    @Unique
    @LuaPublic
    private static int plzMusicStreamsOpen() {
        return PLZMusicStream.getOpen();
    }

    @Unique
    @LuaPublic
    private static int plzMusicStreamsOpened() {
        return PLZMusicStream.getOpened();
    }

    @Unique
    @LuaPublic
    private static int plzMusicStreamsFailed() {
        return PLZMusicStream.getFailed();
    }

    @Unique
    @LuaPublic
    private static int plzMusicStreamsOrphaned() {
        return PLZMusicStream.getOrphansReleased();
    }

    @Unique
    @LuaPublic
    private static int plzNullTextureCount() {
        return PLZTextureRepair.deadCount();
    }

    @Unique
    @LuaPublic
    private static int plzRepairTextures() {
        return PLZTextureRepair.repair();
    }

    @Unique
    @LuaPublic
    private static void plzGatedBeginUpdate() {
        PLZGatedGrid.beginUpdate();
    }

    @Unique
    @LuaPublic
    private static void plzGatedBeginGroup(String flag, double policy) {
        PLZGatedGrid.beginGroup(flag, (int)Math.floor(policy));
    }

    @Unique
    @LuaPublic
    private static void plzGatedAddRect(double x1, double y1, double x2, double y2, double z) {
        PLZGatedGrid.addRect(x1, y1, x2, y2, z);
    }

    @Unique
    @LuaPublic
    private static int plzGatedEndUpdate() {
        return PLZGatedGrid.endUpdate();
    }

    @Unique
    @LuaPublic
    private static int plzGatedGroupCount() {
        return PLZGatedGrid.getGroupCount();
    }

    @Unique
    @LuaPublic
    private static void plzGatedSetPlayerFlags(String username, String flags) {
        PLZGatedGrid.setPlayerFlags(username, flags);
    }

    @Unique
    @LuaPublic
    private static boolean plzGatedHasFlag(String username, String flag) {
        return PLZGatedGrid.hasFlag(username, flag);
    }

    @Unique
    @LuaPublic
    private static void plzGatedClearPlayerFlags() {
        PLZGatedGrid.clearAllPlayerFlags();
    }

    @Unique
    @LuaPublic
    private static int plzGatedFlaggedPlayerCount() {
        return PLZGatedGrid.getFlaggedPlayerCount();
    }

    @Unique
    @LuaPublic
    private static void plzDoorAccessSet(String username, String keyIds) {
        PLZDoorAccess.set(username, keyIds);
    }

    @Unique
    @LuaPublic
    private static boolean plzDoorAccessHas(String username, double keyId) {
        return PLZDoorAccess.has(username, (int)Math.floor(keyId));
    }

    @Unique
    @LuaPublic
    private static void plzDoorAccessClear() {
        PLZDoorAccess.clearAll();
    }

    @Unique
    @LuaPublic
    private static int plzDoorAccessPlayerCount() {
        return PLZDoorAccess.getGrantedPlayerCount();
    }

    @Unique
    @LuaPublic
    private static int plzDoorAccessKeyCount(String username) {
        return PLZDoorAccess.getGrantedKeyCount(username);
    }

    // Only the refusal is bridged: arm() and takeRefusal() are the engine's half of the handshake.
    @Unique
    @LuaPublic
    private static void plzRefuseObjectDamage() {
        PLZObjectDamage.refuse();
    }

    @Unique
    @LuaPublic
    private static int plzBarrierRefresh() {
        IsoCell cell = IsoWorld.instance == null ? null : IsoWorld.instance.currentCell;
        if (cell == null) {
            return 0;
        }

        long[] chunks = PLZBarrierGrid.getChangedChunks();
        int touched = 0;

        for (long key : chunks) {
            int cx = PLZBarrierGrid.chunkX(key);
            int cy = PLZBarrierGrid.chunkY(key);
            IsoChunk chunk = GameServer.server
                ? (ServerMap.instance == null ? null : ServerMap.instance.getChunk(cx, cy))
                : cell.getChunk(cx, cy);
            if (chunk == null) {
                continue;
            }

            for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        IsoGridSquare sq = cell.getGridSquare((cx << 3) + x, (cy << 3) + y, z);
                        if (sq != null) {
                            sq.RecalcProperties();
                            sq.RecalcAllWithNeighbours(true);
                            touched++;
                        }
                    }
                }
            }
        }

        return touched;
    }

    // plzPinArea returning false means the engine is too busy, not an error: the caller must branch on it.
    @Unique
    @LuaPublic
    private static boolean plzPinArea(double x, double y) {
        return PLZMapPin.pin(x, y);
    }

    @Unique
    @LuaPublic
    private static boolean plzAreaIsPinned(double x, double y) {
        return PLZMapPin.isPinned(x, y);
    }

    @Unique
    @LuaPublic
    private static int plzPinnedAreaCount() {
        return PLZMapPin.pinnedCount();
    }

    @Unique
    @LuaPublic
    private static int plzPinAreaBudget() {
        return PLZMapPin.budget();
    }

    @Unique
    @LuaPublic
    private static String plzMapPinStatus() {
        return PLZMapPin.status();
    }

    @Unique
    @LuaPublic
    private static int plzSnowClearSquares(String packed) {
        return PLZSnowClear.clearSquares(packed);
    }

    @Unique
    @LuaPublic
    private static int plzSnowRestoreSquares(String packed) {
        return PLZSnowClear.restoreSquares(packed);
    }

    @Unique
    @LuaPublic
    private static boolean plzSnowIsCleared(double x, double y) {
        return PLZSnowClear.isCleared((int)Math.floor(x), (int)Math.floor(y));
    }

    @Unique
    @LuaPublic
    private static int plzSnowClearReset() {
        return PLZSnowClear.reset();
    }

    @Unique
    @LuaPublic
    private static int plzSnowClearCount() {
        return PLZSnowClear.count();
    }

    @Unique
    @LuaPublic
    private static String plzSnowClearStatus() {
        return PLZSnowClear.status();
    }

    @Unique
    @LuaPublic
    private static String plzMapScanStart(double objects, double items, boolean containers, boolean excludeMapOriginals, double speed,
        String ignoreList) {
        return PLZMapScan.start(objects, items, containers, excludeMapOriginals, speed, ignoreList);
    }

    @Unique
    @LuaPublic
    private static void plzMapScanCancel() {
        PLZMapScan.cancel();
    }

    @Unique
    @LuaPublic
    private static void plzMapScanSetSpeed(double speed) {
        PLZMapScan.setSpeed(speed);
    }

    @Unique
    @LuaPublic
    private static boolean plzMapScanActive() {
        return PLZMapScan.isActive();
    }

    @Unique
    @LuaPublic
    private static void plzMapScanPump() {
        PLZMapScan.pumpLive();
    }

    @Unique
    @LuaPublic
    private static double plzMapScanStat(String name) {
        return PLZMapScan.stat(name);
    }

    @Unique
    @LuaPublic
    private static String plzMapScanText(String name) {
        return PLZMapScan.text(name);
    }

    @Unique
    @LuaPublic
    private static int plzMapScanResultCount() {
        return PLZMapScan.resultCount();
    }

    @Unique
    @LuaPublic
    private static String plzMapScanResultRow(double index) {
        return PLZMapScan.resultRow((int)index);
    }

    @Unique
    @LuaPublic
    private static boolean plzJfrAvailable() {
        return PLZFlightRecorder.available();
    }

    @Unique
    @LuaPublic
    private static String plzJfrStatus() {
        return PLZFlightRecorder.status();
    }

    @Unique
    @LuaPublic
    private static String plzJfrDump(String label, double minutes) {
        return PLZFlightRecorder.dump(label, (int)Math.floor(minutes));
    }

    @Unique
    @LuaPublic
    private static boolean plzLuaProfileEnabled() {
        return PLZLuaProfile.isEnabled();
    }

    @Unique
    @LuaPublic
    private static void plzLuaProfileSetEnabled(boolean enabled) {
        PLZLuaProfile.setEnabled(enabled);
    }

    @Unique
    @LuaPublic
    private static void plzLuaProfileReset() {
        PLZLuaProfile.reset();
    }

    @Unique
    @LuaPublic
    private static String plzLuaProfileReport(double topN) {
        return PLZLuaProfile.report((int)Math.floor(topN));
    }

    @Unique
    @LuaPublic
    private static String plzLuaProfileStatus() {
        return PLZLuaProfile.status();
    }

    @Unique
    @LuaPublic
    private static boolean plzSyncWatchEnabled() {
        return PLZSyncWatch.isEnabled();
    }

    @Unique
    @LuaPublic
    private static void plzSyncWatchSetEnabled(boolean enabled) {
        PLZSyncWatch.setEnabled(enabled);
    }

    @Unique
    @LuaPublic
    private static void plzSyncWatchReset() {
        PLZSyncWatch.reset();
    }

    @Unique
    @LuaPublic
    private static String plzSyncWatchReport(double topN) {
        return PLZSyncWatch.report((int)Math.floor(topN));
    }

    @Unique
    @LuaPublic
    private static String plzSyncWatchStatus() {
        return PLZSyncWatch.status();
    }

    @Unique
    @LuaPublic
    private static String plzSyncWatchSummary() {
        return PLZSyncWatch.summary();
    }

    @Unique
    @LuaPublic
    private static boolean plzItemWeightCacheEnabled() {
        return PLZItemWeightCache.isEnabled();
    }

    @Unique
    @LuaPublic
    private static void plzItemWeightCacheSetEnabled(boolean enabled) {
        PLZItemWeightCache.setEnabled(enabled);
    }

    @Unique
    @LuaPublic
    private static void plzItemWeightCacheClear() {
        PLZItemWeightCache.clear();
    }

    @Unique
    @LuaPublic
    private static String plzItemWeightCacheStatus() {
        return PLZItemWeightCache.status();
    }

    @Unique
    @LuaPublic
    private static void plzItemWeightCacheSetVerifying(boolean on) {
        PLZItemWeightCache.setVerifying(on);
    }

    @Unique
    @LuaPublic
    private static double plzItemWeightCacheWeightOf(String fullType) {
        return PLZItemWeightCache.weightOf(fullType);
    }

    @Unique
    @LuaPublic
    private static void plzEventSkipAdd(String event, String filePart) {
        PLZEventSkip.skip(event, filePart);
    }

    @Unique
    @LuaPublic
    private static void plzEventSkipClear() {
        PLZEventSkip.clear();
    }

    @Unique
    @LuaPublic
    private static void plzEventSkipRefresh() {
        PLZEventSkip.refresh();
    }

    @Unique
    @LuaPublic
    private static String plzEventSkipStatus() {
        return PLZEventSkip.status();
    }

    @Unique
    @LuaPublic
    private static boolean plzSyncRelayFilter() {
        return PLZSyncWatch.isRelevantOnly();
    }

    @Unique
    @LuaPublic
    private static void plzSyncSetRelayFilter(boolean on) {
        PLZSyncWatch.setRelevantOnly(on);
    }

    @Unique
    @LuaPublic
    private static String plzObjectUpdateStatus() {
        return PLZObjectUpdate.status();
    }

    @Unique
    @LuaPublic
    private static boolean plzObjectUpdateSkipIdle() {
        return PLZObjectUpdate.isSkipIdle();
    }

    @Unique
    @LuaPublic
    private static void plzObjectUpdateSetSkipIdle(boolean on) {
        PLZObjectUpdate.setSkipIdle(on);
    }

    @Unique
    @LuaPublic
    private static void plzObjectUpdateReset() {
        PLZObjectUpdate.reset();
    }

    @Unique
    @LuaPublic
    private static String plzVariableSyncStatus() {
        return PLZVariableSync.status();
    }

    @Unique
    @LuaPublic
    private static boolean plzVariableSyncThrottle() {
        return PLZVariableSync.isThrottle();
    }

    @Unique
    @LuaPublic
    private static void plzVariableSyncSetThrottle(boolean on) {
        PLZVariableSync.setThrottle(on);
    }

    @Unique
    @LuaPublic
    private static void plzVariableSyncReset() {
        PLZVariableSync.reset();
    }

    @Unique
    @LuaPublic
    private static String plzRemoteHealStatus() {
        return PLZRemoteHeal.status();
    }

    @Unique
    @LuaPublic
    private static boolean plzRemoteHealEnabled() {
        return PLZRemoteHeal.isEnabled();
    }

    @Unique
    @LuaPublic
    private static void plzRemoteHealSetEnabled(boolean on) {
        PLZRemoteHeal.setEnabled(on);
    }

    @Unique
    @LuaPublic
    private static void plzRemoteHealReset() {
        PLZRemoteHeal.reset();
    }

    @Unique
    @LuaPublic
    private static String plzAnimalSyncStatus() {
        return zombie.plz.PLZAnimalSync.status();
    }

    @Unique
    @LuaPublic
    private static void plzAnimalSyncReset() {
        zombie.plz.PLZAnimalSync.reset();
    }

    @Unique
    @LuaPublic
    private static double plzSetServerFps(double fps) {
        return zombie.plz.PLZTickRate.apply((int)Math.floor(fps));
    }

    @Unique
    @LuaPublic
    private static double plzGetServerFps() {
        return zombie.plz.PLZTickRate.serverFps();
    }

    @Unique
    @LuaPublic
    private static String plzTickRateStatus() {
        return zombie.plz.PLZTickRate.status();
    }
}

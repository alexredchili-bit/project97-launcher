package plz.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoPlayer;
import zombie.characters.Role;
import zombie.core.network.ByteBufferReader;
import zombie.core.profiling.AbstractPerformanceProfileProbe;
import zombie.core.profiling.PerformanceProfileProbe;
import zombie.core.raknet.UdpConnection;
import zombie.core.raknet.UdpEngine;
import zombie.core.utils.UpdateLimit;
import zombie.core.znet.SteamGameServer;
import zombie.network.GameServer;
import zombie.network.IConnection;
import zombie.network.IZomboidPacket;
import zombie.plz.PLZSlots;
import zombie.network.ZomboidNetData;
import zombie.network.ZomboidNetDataPool;
import zombie.network.chat.ChatServer;
import zombie.plz.PLZChatRecovery;
import zombie.plz.PLZChunkCrc;
import zombie.plz.PLZConnectWatch;
import zombie.plz.PLZDisconnectWatch;
import zombie.plz.PLZFixes;
import zombie.plz.PLZNetProbe;
import zombie.plz.PLZPhysicsCoalesce;
import zombie.plz.PLZRelay;
import zombie.plz.PLZStalledConnections;
import zombie.plz.PLZStationClamp;
import zombie.plz.PLZTickRate;
import zombie.plz.PLZVariableSync;
import zombie.plz.PLZVoiceRouting;
import zombie.plz.access.PLZChatServerAccess;

@Mixin(GameServer.class)
abstract class GameServerMixin {
    @Shadow
    public static String serverName;

    @Shadow
    public static UdpEngine udpEngine;

    @Shadow
    @Final
    private static ArrayList<IZomboidPacket> MainLoopNetData2;

    @Unique
    private static long plz$workshopSyncedAt;

    @Unique
    private static UpdateLimit plz$voiceRoutingLimiter;

    @Unique
    private static VarHandle plz$dealProbe;

    @Shadow
    private static void mainLoopDealWithNetData(ZomboidNetData data) {
        throw new AssertionError();
    }

    // PLZ: the launcher reads this over A2S to tell players the server is behind a Workshop update.
    @WrapOperation(method = "main", at = @At(value = "INVOKE", target = "Lzombie/network/GameServerWorkshopItems;Install(Ljava/util/ArrayList;)Z"))
    private static boolean plz$stampWorkshopSync(ArrayList<?> items, Operation<Boolean> original) {
        long started = System.currentTimeMillis() / 1000L;
        boolean installed = original.call(items);
        if (installed) {
            plz$workshopSyncedAt = started;
            SteamGameServer.SetKeyValue("plzws", String.valueOf(plz$workshopSyncedAt));
        }

        return installed;
    }

    // PLZ: after the startup backup, so that backup holds the chunk files as they were.
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lzombie/core/backup/ZipBackup;onVersion()V", shift = At.Shift.AFTER))
    private static void plz$repairChunkCrcs(String[] args, CallbackInfo ci) {
        PLZChunkCrc.repairSaveDir(serverName);
    }

    // PLZ: the loop gate and lockFPS share one knob (PLZTickRate); the default 10 matches vanilla.
    @ModifyArg(method = "main", at = @At(value = "INVOKE", target = "Lzombie/core/utils/UpdateLimit;<init>(J)V"))
    private static long plz$tickInterval(long vanillaMs) {
        plz$voiceRoutingLimiter = new UpdateLimit(PLZVoiceRouting.REFRESH_MS);
        return PLZTickRate.initialTickIntervalMs();
    }

    @ModifyExpressionValue(method = "main", at = @At(value = "NEW", target = "(J)Lzombie/core/utils/UpdateLimit;"))
    private static UpdateLimit plz$registerTickLimiter(UpdateLimit limiter) {
        PLZTickRate.register(limiter);
        return limiter;
    }

    @ModifyArg(method = "main", at = @At(value = "INVOKE", target = "Lzombie/core/PerformanceSettings;setLockFPS(I)V"))
    private static int plz$serverFps(int vanillaFps) {
        return PLZTickRate.serverFps();
    }

    // PLZ: under overload keep the latest vehicle-physics packet per vehicle instead of dropping the whole tail.
    // Vanilla adds (size - n) to countOfDroppedPackets, so returning n + stale makes that line add the stale count.
    @ModifyExpressionValue(
        method = "main",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;size()I", ordinal = 0),
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lzombie/network/GameServer;droppedPackets:I", opcode = Opcodes.PUTSTATIC, ordinal = 0)
        )
    )
    private static int plz$coalescePhysics(int size, @Local(index = 16) int n) {
        if (!PLZFixes.on(PLZFixes.VEHICLE_PHYSICS_COALESCE)) {
            return size;
        }

        boolean[] stale = PLZPhysicsCoalesce.stale(MainLoopNetData2, n);
        int dropped = 0;
        for (int i = n; i < MainLoopNetData2.size(); i++) {
            ZomboidNetData latest = (ZomboidNetData)MainLoopNetData2.get(i);
            if (stale[i - n]) {
                dropped++;
                ZomboidNetDataPool.instance.discard(latest);
            } else {
                try (AbstractPerformanceProfileProbe probe = plz$dealWithNetDataProbe().profile()) {
                    mainLoopDealWithNetData(latest);
                }
            }
        }

        PLZFixes.hit(PLZFixes.VEHICLE_PHYSICS_COALESCE);
        return n + dropped;
    }

    // s_performance is package-private, so only a lookup made from inside GameServer can reach its probe.
    @Unique
    private static PerformanceProfileProbe plz$dealWithNetDataProbe() {
        VarHandle probe = plz$dealProbe;
        if (probe == null) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> perf = lookup.findClass("zombie.network.GameServer$s_performance");
                probe = lookup.findStaticVarHandle(perf, "mainLoopDealWithNetData", PerformanceProfileProbe.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }

            plz$dealProbe = probe;
        }

        return (PerformanceProfileProbe) probe.get();
    }

    @Inject(
        method = "main",
        at = @At(value = "INVOKE", target = "Lzombie/iso/IsoCamera$FrameState;updateUnPausedAccumulator()V", shift = At.Shift.AFTER)
    )
    private static void plz$refreshVoiceRouting(String[] args, CallbackInfo ci) {
        if (PLZFixes.on(PLZFixes.VOICE_ROUTING_SLACK) && plz$voiceRoutingLimiter.Check()) {
            PLZVoiceRouting.refresh();
        }
    }

    @Inject(
        method = "main",
        at = @At(value = "INVOKE", target = "Lzombie/network/statistics/StatisticManager;update(J)V", shift = At.Shift.AFTER)
    )
    private static void plz$probeNet(String[] args, CallbackInfo ci) {
        PLZNetProbe.update(udpEngine.connections);
    }

    @Inject(method = "setupSteamGameServer", at = @At("RETURN"))
    private static void plz$advertiseWorkshopSync(CallbackInfo ci) {
        if (plz$workshopSyncedAt > 0L) {
            SteamGameServer.SetKeyValue("plzws", String.valueOf(plz$workshopSyncedAt));
        }
    }

    // PLZ: once per tick on the server main thread, which is where the reap must run (disconnect is not thread-safe).
    @Inject(method = "launchCommandHandler", at = @At("HEAD"))
    private static void plz$sweepStalledConnections(CallbackInfo ci) {
        PLZStalledConnections.sweep();
    }

    // PLZ: the one dispatch point for RCON, console and staff slash commands; null means not ours.
    @Inject(
        method = "handleServerCommand",
        at = @At(value = "INVOKE", target = "Lzombie/commands/CommandBase;findCommandCls(Ljava/lang/String;)Ljava/lang/Class;"),
        cancellable = true
    )
    private static void plz$handleOwnCommands(
        String input, UdpConnection connection, CallbackInfoReturnable<String> cir, @Local(index = 2) String adminUsername, @Local Role accessLevel
    ) {
        String plzSlots = PLZSlots.handleCommand(input, adminUsername, accessLevel);
        if (plzSlots != null) {
            cir.setReturnValue(plzSlots);
            return;
        }

        String plzRelay = PLZRelay.handleCommand(input, connection != null);
        if (plzRelay != null) {
            cir.setReturnValue(plzRelay);
        }
    }

    // PLZ: the only place the sending connection is known, and the author must be resolved from it.
    @Inject(method = "receivePlayerStartPMChat", at = @At("HEAD"), cancellable = true)
    private static void plz$whisperStartFromConnection(ByteBufferReader bb, UdpConnection connection, short packetType, CallbackInfo ci) {
        if (PLZFixes.on(PLZFixes.PM_CHAT_START)) {
            ((PLZChatServerAccess) ChatServer.getInstance()).plzProcessPlayerStartWhisperChatPacket(bb, connection);
            ci.cancel();
        }
    }

    // Answered here rather than through OnClientCommand: the repair is pure engine state and no Lua listens for it.
    @Inject(
        method = "receiveClientCommand",
        at = @At(value = "FIELD", target = "Lzombie/network/GameServer;ccFilters:Ljava/util/HashMap;", opcode = Opcodes.GETSTATIC),
        cancellable = true
    )
    private static void plz$chatRecoveryCommand(
        ByteBufferReader bb,
        UdpConnection connection,
        short packetType,
        CallbackInfo ci,
        @Local(index = 4) String module,
        @Local(index = 5) String command,
        @Local IsoPlayer player
    ) {
        if (!PLZChatRecovery.MODULE.equals(module)) {
            return;
        }

        if (PLZFixes.on(PLZFixes.CHAT_JOIN_RECOVERY) && PLZChatRecovery.serverMayServe(player.getOnlineID())) {
            ((PLZChatServerAccess) ChatServer.getInstance())
                .plzResendJoins(player.getOnlineID(), PLZChatRecovery.COMMAND_REINIT.equals(command));
        }

        ci.cancel();
    }

    // PLZ: fired first, before the seat, world and player maps are cleared; the server has no Lua disconnect event.
    @Inject(method = "disconnectPlayer(Lzombie/characters/IsoPlayer;Lzombie/network/IConnection;)V", at = @At("HEAD"))
    private static void plz$watchDisconnect(IsoPlayer player, IConnection connection, CallbackInfo ci) {
        PLZDisconnectWatch.onDisconnect(player);
    }

    // PLZ: the relay calls this per observer per update; a change already reaches everyone through VariableSync.
    @Inject(method = "setCustomVariables", at = @At("HEAD"), cancellable = true)
    private static void plz$throttleVariableResend(IsoPlayer p, IConnection c, CallbackInfo ci) {
        if (!PLZVariableSync.shouldSend(c.getConnectedGUID(), p.getOnlineID())) {
            ci.cancel();
        }
    }

    @Inject(
        method = "sendPlayerConnected",
        at = @At(value = "INVOKE", target = "Lzombie/network/GameServer;setCustomVariables(Lzombie/characters/IsoPlayer;Lzombie/network/IConnection;)V")
    )
    private static void plz$forceVariableSync(IsoPlayer p, IConnection c, CallbackInfo ci) {
        PLZVariableSync.force(c.getConnectedGUID(), p.getOnlineID());
    }

    // PLZ: sendPlayerExtraInfo ignores its connection and broadcasts, so once per connection made a join N*N packets.
    @WrapOperation(
        method = "receivePlayerConnect",
        at = @At(value = "INVOKE", target = "Lzombie/network/GameServer;sendPlayerExtraInfo(Lzombie/characters/IsoPlayer;Lzombie/core/raknet/UdpConnection;Z)V")
    )
    private static void plz$oneExtraInfo(
        IsoPlayer player, UdpConnection c, boolean flag, Operation<Void> original, @Share("plzOneExtraInfo") LocalIntRef oneExtraInfo
    ) {
        if (oneExtraInfo.get() == 0) {
            oneExtraInfo.set(PLZFixes.on(PLZFixes.CONNECT_EXTRA_INFO_FANOUT) ? 1 : 2);
        }

        if (oneExtraInfo.get() != 1) {
            original.call(player, c, flag);
        }
    }

    @Inject(method = "receivePlayerConnect", at = @At(value = "INVOKE", target = "Ljava/util/HashMap;values()Ljava/util/Collection;"))
    private static void plz$afterConnectFanout(
        ByteBufferReader bb,
        IConnection connection,
        String username,
        CallbackInfo ci,
        @Local(index = 5) IsoPlayer player,
        @Share("plzOneExtraInfo") LocalIntRef oneExtraInfo,
        @Share("plzVariableSyncOnce") LocalBooleanRef variableSyncOnce
    ) {
        if (oneExtraInfo.get() == 0) {
            oneExtraInfo.set(PLZFixes.on(PLZFixes.CONNECT_EXTRA_INFO_FANOUT) ? 1 : 2);
        }

        if (oneExtraInfo.get() == 1) {
            PLZFixes.hit(PLZFixes.CONNECT_EXTRA_INFO_FANOUT);
            GameServer.sendPlayerExtraInfo(player, null, true);
        }

        // PLZ: sendPlayerConnected already ran setCustomVariables for the same pair, so each variable reached the joiner twice.
        boolean once = PLZFixes.on(PLZFixes.CONNECT_VARIABLE_SYNC_ONCE);
        if (once) {
            PLZFixes.hit(PLZFixes.CONNECT_VARIABLE_SYNC_ONCE);
        }

        variableSyncOnce.set(once);
    }

    @WrapOperation(
        method = "receivePlayerConnect",
        at = @At(value = "INVOKE", target = "Lzombie/network/GameServer;setCustomVariables(Lzombie/characters/IsoPlayer;Lzombie/network/IConnection;)V")
    )
    private static void plz$variableSyncOnce(
        IsoPlayer p, IConnection c, Operation<Void> original, @Share("plzVariableSyncOnce") LocalBooleanRef variableSyncOnce
    ) {
        if (!variableSyncOnce.get()) {
            original.call(p, c);
        }
    }

    // PLZ: fired last, once the character, the online list and the connection are all true; the server has no join hook.
    @Inject(
        method = "receivePlayerConnect",
        at = @At(value = "INVOKE", target = "Lzombie/core/logger/ZLogger;write(Ljava/lang/String;)V", ordinal = 1, shift = At.Shift.AFTER)
    )
    private static void plz$watchConnect(
        ByteBufferReader bb, IConnection connection, String username, CallbackInfo ci, @Local(index = 3) int playerIndex, @Local(index = 5) IsoPlayer player
    ) {
        PLZConnectWatch.onConnect(playerIndex, player);
    }

    // PLZ: drop the per-connection samples, so a reconnecting player inherits none of the old session.
    @Inject(
        method = "disconnect(Lzombie/core/raknet/UdpConnection;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lzombie/network/ConnectionManager;log(Ljava/lang/String;Ljava/lang/String;Lzombie/network/IConnection;)V")
    )
    private static void plz$forgetConnection(UdpConnection connection, String description, CallbackInfo ci) {
        PLZVoiceRouting.forget(connection.getConnectedGUID());
        PLZVariableSync.forget(connection.getConnectedGUID());
        PLZStationClamp.forget(connection.getConnectedGUID());
    }

    // PLZ: the routing table is republished every 3 s, so PLZVoiceRouting re-pushes live positions; bb still carries the true ranges.
    @WrapOperation(
        method = "receiveSyncRadioData",
        at = @At(value = "INVOKE", target = "Lzombie/core/raknet/RakVoice;SetChannelsRouting(JZ[IS)V")
    )
    private static void plz$routeVoice(
        long guid,
        boolean isCanHearAll,
        int[] radioData,
        short radioDataSize,
        Operation<Void> original,
        @Local(argsOnly = true) UdpConnection connection
    ) {
        // PLZ radio stations: only the server-authorised on-air host may transmit on a station frequency.
        PLZStationClamp.clamp(connection, isCanHearAll, radioData, radioDataSize);
        if (PLZFixes.on(PLZFixes.VOICE_ROUTING_SLACK)) {
            PLZFixes.hit(PLZFixes.VOICE_ROUTING_SLACK);
            PLZVoiceRouting.publish(connection, isCanHearAll, radioData, radioDataSize);
        } else {
            original.call(guid, isCanHearAll, radioData, radioDataSize);
        }
    }
}

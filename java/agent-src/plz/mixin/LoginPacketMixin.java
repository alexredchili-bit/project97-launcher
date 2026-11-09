package plz.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.core.logger.LoggerManager;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.core.znet.SteamUtils;
import zombie.debug.DebugType;
import zombie.debug.LogSeverity;
import zombie.network.CoopSlave;
import zombie.network.IConnection;
import zombie.plz.PLZAccounts;
import zombie.plz.PLZJavaGate;
import zombie.plz.PLZQueue;
import zombie.plz.PLZSlots;
import zombie.network.PacketTypes;
import zombie.network.ServerWorldDatabase;
import zombie.network.packets.INetworkPacket;
import zombie.network.packets.connection.LoginPacket;
import zombie.plz.PLZJavaGuard;

@Mixin(LoginPacket.class)
public abstract class LoginPacketMixin {
    @Shadow
    String username;
    @Shadow
    String password;

    @Unique
    private List<String> plz$javaReport;
    @Unique
    private String plz$queueDenial;

    @Inject(method = "parse", at = @At("TAIL"))
    private void plz$readJavaReport(ByteBufferReader b, IConnection connection, CallbackInfo ci) {
        this.plz$javaReport = PLZJavaGuard.read(b);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void plz$writeJavaReport(ByteBufferWriter b, CallbackInfo ci) {
        PLZJavaGuard.write(b);
    }

    @Inject(
        method = "processServer",
        at = @At(value = "FIELD", target = "Lzombie/network/packets/connection/LoginPacket;password:Ljava/lang/String;", opcode = 180, ordinal = 0),
        cancellable = true
    )
    private void plz$applyBinding(PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci) {
        String denial = this.plz$binding(connection);
        if (denial != null) {
            LoggerManager.getLogger("user").write("access denied: user \"" + this.username + "\" reason \"" + denial + "\"");
            INetworkPacket.send(connection, PacketTypes.PacketType.AccessDenied, denial);
            connection.forceDisconnect("access-denied-plz-binding");
            ci.cancel();
        }
    }

    @Unique
    private String plz$binding(UdpConnection connection) {
        if (!SteamUtils.isSteamModeEnabled() || CoopSlave.instance != null || connection.getSteamId() <= 0L) {
            return null;
        }

        String steamID = SteamUtils.convertSteamIDToString(connection.getSteamId());
        List<String> bound = PLZAccounts.boundUsernames(steamID);

        if (!bound.isEmpty()) {
            for (int i = 0; i < bound.size(); i++) {
                if (bound.get(i).equals(this.username)) {
                    String credential = PLZAccounts.credentialFor(steamID);
                    PLZAccounts.adoptCredential(this.username, credential);
                    this.password = credential;
                    return null;
                }
            }
            // An unbound name with a spare slot is a new character, so it takes the same mint path as their first.
            if (bound.size() >= PLZSlots.allowed(steamID)) {
                return "PLZWrongCharacter##" + String.join(", ", bound);
            }
        }

        try {
            if (!ServerWorldDatabase.instance.isSteamIDAllowed(steamID)) {
                return "PLZNotApproved";
            }
        } catch (Exception ex) {
            DebugType.General.printException(ex, LogSeverity.Error);
            return null;
        }

        if (!ServerWorldDatabase.isValidUserName(this.username)) {
            return "InvalidUsername";
        }
        if (ServerWorldDatabase.instance.containsCaseinsensitiveUser(this.username)) {
            return "PLZNameTaken";
        }

        this.password = PLZAccounts.credentialFor(steamID);
        return null;
    }

    @Definition(id = "getPlayerCount", method = "Lzombie/network/GameServer;getPlayerCount()I")
    @Definition(id = "getMaxPlayers", method = "Lzombie/network/ServerOptions;getMaxPlayers()I")
    @Expression("getPlayerCount() >= ?.getMaxPlayers()")
    @ModifyExpressionValue(method = "processServer", at = @At("MIXINEXTRAS:EXPRESSION"), require = 2)
    private boolean plz$admit(boolean serverFull, @Local(argsOnly = true) UdpConnection connection) {
        String denial = PLZQueue.admit(connection);
        this.plz$queueDenial = denial;
        if (denial != null) {
            LoggerManager.getLogger("user").write("access denied: user \"" + this.username + "\" reason \"" + denial + "\"");
        }
        return denial != null;
    }

    @ModifyConstant(method = "processServer", constant = @Constant(stringValue = "ServerFull"), require = 2)
    private String plz$queueReason(String serverFull) {
        return this.plz$queueDenial != null ? this.plz$queueDenial : serverFull;
    }

    @ModifyConstant(method = "processServer", constant = @Constant(stringValue = "access-denied-server-full-cs"))
    private String plz$queueDisconnectCoop(String reason) {
        return "access-denied-plz-queue-cs";
    }

    @ModifyConstant(method = "processServer", constant = @Constant(stringValue = "access-denied-server-full"))
    private String plz$queueDisconnect(String reason) {
        return "access-denied-plz-queue";
    }

    @Inject(
        method = "processServer",
        at = @At(value = "FIELD", target = "Lzombie/network/ServerWorldDatabase$LogonResult;authorized:Z", opcode = 180, ordinal = 0),
        cancellable = true
    )
    private void plz$javaGate(
        PacketTypes.PacketType packetType, UdpConnection connection, CallbackInfo ci, @Local(index = 4) ServerWorldDatabase.LogonResult r
    ) {
        if (!r.authorized) {
            return;
        }
        String denial = PLZJavaGate.check(this.plz$javaReport, r.role, this.username);
        if (denial != null) {
            INetworkPacket.send(connection, PacketTypes.PacketType.AccessDenied, denial);
            connection.forceDisconnect("access-denied-plz-java");
            ci.cancel();
        }
    }
}

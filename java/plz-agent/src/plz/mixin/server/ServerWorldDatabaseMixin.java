package plz.mixin.server;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.core.znet.SteamUtils;
import zombie.plz.PLZAccounts;
import zombie.network.ServerWorldDatabase;

@Mixin(ServerWorldDatabase.class)
abstract class ServerWorldDatabaseMixin {
    // PLZ: vanilla caps a username at 32.
    @ModifyConstant(method = "isValidUserName", constant = @Constant(intValue = 32))
    private static int plz$longerUserNames(int vanillaCap) {
        return 50;
    }

    // PLZ: LoginPacket's binding needs this SteamID's BCrypt credential; hash it now, off the main loop.
    @Inject(
        method = "authClient(J)Lzombie/network/ServerWorldDatabase$LogonResult;",
        at = @At(
            value = "FIELD",
            target = "Lzombie/network/ServerWorldDatabase$LogonResult;authorized:Z",
            opcode = Opcodes.PUTFIELD,
            ordinal = 1,
            shift = At.Shift.AFTER
        )
    )
    private void plz$prefetchCredential(long steamID, CallbackInfoReturnable<ServerWorldDatabase.LogonResult> cir) {
        PLZAccounts.prefetchCredential(SteamUtils.convertSteamIDToString(steamID));
    }
}

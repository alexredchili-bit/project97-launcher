package plz.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.HashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatBase;
import zombie.chat.ChatManager;
import zombie.chat.ChatMessage;
import zombie.chat.ChatTab;
import zombie.chat.defaultChats.ServerChat;
import zombie.core.network.ByteBufferReader;
import zombie.debug.DebugLog;
import zombie.network.GameClient;
import zombie.network.chat.ChatType;
import zombie.plz.PLZChatRecovery;
import zombie.plz.PLZFixes;
import zombie.plz.access.PLZChatManagerAccess;

@Mixin(ChatManager.class)
abstract class ChatManagerMixin implements PLZChatManagerAccess {
    @Shadow
    @Final
    private HashMap<Short, ChatTab> tabs;

    @Shadow
    private ChatTab focusTab;

    @Shadow
    private IsoPlayer player;

    @Shadow
    private boolean singlePlayerMode;

    @Shadow
    private ServerChat serverChat;

    @Inject(method = "init", at = @At("RETURN"))
    private void plz$markJoined(boolean isSinglePlayer, IsoPlayer owner, CallbackInfo ci) {
        PLZChatRecovery.clientJoined();
    }

    @Inject(
        method = "unpackMessage",
        at = @At(value = "INVOKE", target = "Lzombie/chat/ChatBase;unpackMessage(Lzombie/core/network/ByteBufferReader;)Lzombie/chat/ChatMessage;"),
        cancellable = true
    )
    private void plz$rejoinUnknownChat(
        ByteBufferReader bb, CallbackInfoReturnable<ChatMessage> cir, @Local(ordinal = 0) int id, @Local ChatBase chat
    ) {
        if (chat == null && PLZFixes.on(PLZFixes.CHAT_JOIN_RECOVERY)) {
            PLZFixes.hit(PLZFixes.CHAT_JOIN_RECOVERY);
            this.plz$requestRejoin("message for unjoined chat " + id);
            cir.setReturnValue(null);
        }
    }

    @Inject(
        method = "processChatMessagePacket",
        at = @At(value = "INVOKE", target = "Lzombie/chat/ChatMessage;getChat()Lzombie/chat/ChatBase;"),
        cancellable = true
    )
    private void plz$dropUnpackedNothing(ByteBufferReader bb, CallbackInfo ci, @Local ChatMessage msg) {
        if (msg == null) {
            ci.cancel();
        }
    }

    @Inject(
        method = "sendMessageToChat(Ljava/lang/String;Lzombie/network/chat/ChatType;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lzombie/chat/ChatManager;showChatDisabledMessage(Lzombie/network/chat/ChatType;)V")
    )
    private void plz$rejoinMissingChat(String author, ChatType type, String msg, CallbackInfo ci) {
        if (PLZFixes.on(PLZFixes.CHAT_JOIN_RECOVERY)) {
            PLZFixes.hit(PLZFixes.CHAT_JOIN_RECOVERY);
            this.plz$requestRejoin("no " + type + " chat to send to");
        }
    }

    @Inject(method = "showServerChatMessage", at = @At("HEAD"), cancellable = true)
    private void plz$rejoinNoServerChat(String msg, CallbackInfo ci) {
        if ((this.serverChat == null || this.focusTab == null) && PLZFixes.on(PLZFixes.CHAT_JOIN_RECOVERY)) {
            PLZFixes.hit(PLZFixes.CHAT_JOIN_RECOVERY);
            DebugLog.log("PLZ: no server chat to show '" + msg + "' in");
            this.plz$requestRejoin(this.serverChat == null ? "server chat missing" : "no focused tab");
            ci.cancel();
        }
    }

    // Joins only unless the tabs are gone too: a full re-init re-fires OnChatWindowInit and ISChat.initChat wipes the history.
    @Unique
    private void plz$requestRejoin(String reason) {
        if (this.singlePlayerMode || !GameClient.client || GameClient.connection == null || this.player == null) {
            return;
        }

        if (!PLZChatRecovery.clientMayAsk()) {
            return;
        }

        boolean full = this.tabs.isEmpty() || this.focusTab == null;
        DebugLog.log("PLZ: chat join incomplete (" + reason + "), asking the server to re-send" + (full ? " everything" : " the joins"));
        GameClient.instance
            .sendClientCommand(
                this.player, PLZChatRecovery.MODULE, full ? PLZChatRecovery.COMMAND_REINIT : PLZChatRecovery.COMMAND_REJOIN, null
            );
    }

    // Polled, because a client with no tab strip can neither send nor take the inbound recovery branch.
    @Override
    public void plzHealthCheck() {
        if (this.singlePlayerMode || !GameClient.client || GameClient.connection == null || this.player == null) {
            return;
        }

        if (!PLZFixes.on(PLZFixes.CHAT_JOIN_RECOVERY)) {
            return;
        }

        if (!this.tabs.isEmpty() && this.focusTab != null) {
            PLZChatRecovery.clientChatOk();
            return;
        }

        // Only after the check above, so the clock measures unbroken breakage rather than uptime.
        if (!PLZChatRecovery.clientSettled()) {
            return;
        }

        PLZFixes.hit(PLZFixes.CHAT_JOIN_RECOVERY);
        this.plz$requestRejoin(this.tabs.isEmpty() ? "health check: no chat tabs" : "health check: no focused tab");
    }
}

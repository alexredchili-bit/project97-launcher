package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatBase;
import zombie.chat.ChatMessage;
import zombie.chat.ChatSettings;
import zombie.chat.ChatTab;
import zombie.chat.ChatUtility;
import zombie.chat.defaultChats.SayChat;
import zombie.chat.defaultChats.WhisperChat;
import zombie.core.Translator;
import zombie.core.logger.ZLogger;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.core.raknet.UdpConnection;
import zombie.network.GameServer;
import zombie.network.PacketTypes;
import zombie.network.chat.ChatServer;
import zombie.network.chat.ChatType;
import zombie.plz.PLZChatRecovery;
import zombie.plz.PLZFixes;
import zombie.plz.access.PLZChatBaseAccess;
import zombie.plz.access.PLZChatServerAccess;

@Mixin(ChatServer.class)
abstract class ChatServerMixin implements PLZChatServerAccess {
    @Shadow
    @Final
    private static ConcurrentHashMap<Integer, ChatBase> chats;

    @Shadow
    private static ZLogger logger;

    @Shadow
    @Final
    private static HashMap<String, ChatTab> tabs;

    @Shadow
    @Final
    private static HashMap<ChatType, ChatBase> defaultChats;

    @Shadow
    private int getNextChatID() {
        throw new AssertionError();
    }

    @Shadow
    private void sendPlayerNotFoundMessage(UdpConnection connection, String playerName) {
        throw new AssertionError();
    }

    @Shadow
    private void sendInitPlayerChatPacket(UdpConnection connection) {
        throw new AssertionError();
    }

    @Shadow
    public abstract void sendMessageToServerChat(UdpConnection connection, String msg);

    // PLZ: author from the connection, not the packet; WhisperChat.init() on the receiver needs canonical usernames.
    @Override
    public void plzProcessPlayerStartWhisperChatPacket(ByteBufferReader bb, UdpConnection connection) {
        logger.write("Whisper chat starting...", "info");
        if (!ChatUtility.chatStreamEnabled(ChatType.whisper)) {
            logger.write("Message for whisper chat is ignored because whisper chat is disabled by server settings", "info");
            return;
        }

        String authorName = bb.getUTF();
        String destPlayerName = bb.getUTF();
        logger.write("Player '" + authorName + "' attempt to start whispering with '" + destPlayerName + "'", "info");

        IsoPlayer player1 = plz$resolveAuthor(connection, authorName);
        if (player1 == null) {
            logger.write("Whisper author could not be resolved from the sending connection; packet dropped", "error");
            return;
        }

        IsoPlayer player2 = plz$findPlayerIgnoreCase(destPlayerName);
        if (player2 == null) {
            logger.write("Player '" + player1.getUsername() + "' attempt to start whisper dialog with '" + destPlayerName + "' but this player not found!", "info");
            this.sendPlayerNotFoundMessage(connection, destPlayerName);
            return;
        }

        PLZFixes.hit(PLZFixes.PM_CHAT_START);
        WhisperChat newPMChat = new WhisperChat(this.getNextChatID(), tabs.get("main"), player1.getUsername(), player2.getUsername());
        newPMChat.addMember(player1.getOnlineID());
        newPMChat.addMember(player2.getOnlineID());
        chats.put(newPMChat.getID(), newPMChat);
        logger.write("Whisper chat (id = " + newPMChat.getID() + ") between '" + player1.getUsername() + "' and '" + player2.getUsername() + "' started", "info");
    }

    /** PLZ: the author is whoever owns the sending connection, not whoever the packet claims. */
    @Unique
    private static IsoPlayer plz$resolveAuthor(UdpConnection connection, String claimedName) {
        if (connection == null) {
            return null;
        }

        IsoPlayer firstOwned = null;
        for (IsoPlayer player : connection.players) {
            if (player == null) {
                continue;
            }

            if (firstOwned == null) {
                firstOwned = player;
            }

            if (claimedName != null && claimedName.equalsIgnoreCase(player.getUsername())) {
                return player;
            }
        }

        // Split-screen: no name matched, so fall back to the connection's first player.
        return firstOwned;
    }

    /** PLZ: case-insensitive destination lookup, after the exact-match paths vanilla uses. */
    @Unique
    private static IsoPlayer plz$findPlayerIgnoreCase(String name) {
        IsoPlayer exact = GameServer.getPlayerByUserNameForCommand(name);
        if (exact == null) {
            exact = GameServer.getPlayerByUserName(name);
        }

        if (exact != null || name == null) {
            return exact;
        }

        for (IsoPlayer player : GameServer.Players) {
            if (player != null && name.equalsIgnoreCase(player.getUsername())) {
                return player;
            }
        }

        return null;
    }

    // Players already connected keep the old bubble range until they rejoin; the filter is live at once.
    @Override
    public boolean plzSetSayRange(float range) {
        ChatBase say = defaultChats.get(ChatType.say);
        if (say == null || !(range > 0.0F)) {
            return false;
        }

        ChatSettings settings = SayChat.getDefaultSettings();
        settings.setRange(range);
        say.setSettings(settings);
        logger.write("Say chat range set to " + range + " tiles", "info");
        return true;
    }

    // Membership is deliberately not touched: the server's view is the correct one, it is the client's copy that is short.
    @Override
    public boolean plzResendJoins(short playerID, boolean full) {
        UdpConnection connection = ChatUtility.findConnection(playerID);
        if (connection == null) {
            return false;
        }

        if (full) {
            this.sendInitPlayerChatPacket(connection);
        }

        int sent = 0;
        synchronized (chats) {
            for (ChatBase chat : chats.values()) {
                if (((PLZChatBaseAccess) chat).plzHasMember(playerID)) {
                    chat.sendPlayerJoinChatPacket(connection);
                    sent++;
                }
            }
        }

        if (full) {
            // init(false, ..) put the client's stage back to starting; without this it never
            // returns to working and updateClient stops running.
            ByteBufferWriter bb = connection.startPacket();
            PacketTypes.PacketType.PlayerConnectedToChat.doPacket(bb);
            PacketTypes.PacketType.PlayerConnectedToChat.send(connection);
        }

        PLZFixes.hit(PLZFixes.CHAT_JOIN_RECOVERY);
        logger.write("Re-sent " + sent + " chat join(s) to player " + playerID + (full ? " after a full re-init" : ""), "info");
        if (sent > 0) {
            this.sendMessageToServerChat(connection, Translator.getText("UI_PLZ_ChatReconnected"));
        }

        return sent > 0;
    }

    @Inject(
        method = "disconnectPlayer",
        at = @At(value = "INVOKE", target = "Lzombie/core/logger/ZLogger;write(Ljava/lang/String;Ljava/lang/String;)V", ordinal = 1)
    )
    private void plz$forgetRecovery(short playerID, CallbackInfo ci) {
        PLZChatRecovery.forget(playerID);
    }

    @WrapOperation(
        method = "sendMessage(Lzombie/chat/ChatMessage;)V",
        at = @At(value = "INVOKE", target = "Lzombie/chat/ChatBase;sendMessageToChatMembers(Lzombie/chat/ChatMessage;)V")
    )
    private void plz$sayOnSameFloor(ChatBase chat, ChatMessage msg, Operation<Void> original) {
        if (chat.getType() == ChatType.say && PLZFixes.on(PLZFixes.CHAT_SAY_SAME_FLOOR)) {
            this.plz$sendSayOnFloor(chat, msg);
        } else {
            original.call(chat, msg);
        }
    }

    // Vanilla RangeBasedChat measures flat distance only, so /say carried through floors and ceilings.
    @Unique
    private void plz$sendSayOnFloor(ChatBase chat, ChatMessage msg) {
        IsoPlayer author = ChatUtility.findPlayer(msg.getAuthor());
        if (author == null) {
            logger.write("Say author '" + msg.getAuthor() + "' not found; message dropped", "warning");
            return;
        }

        float range = chat.getRange();
        int floor = author.getZi();
        UdpConnection authorConnection = GameServer.getConnectionFromPlayer(author);
        HashSet<UdpConnection> connectionsSent = new HashSet<>();
        for (IsoPlayer player : GameServer.Players) {
            if (player == null || player.getOnlineID() == author.getOnlineID() || player.getZi() != floor) {
                continue;
            }

            short playerID = player.getOnlineID();
            if (((PLZChatBaseAccess) chat).plzHasMember(playerID) && ChatUtility.getDistance(author, player) < range) {
                UdpConnection connection = GameServer.getConnectionFromPlayer(player);
                if (connection != null && connection != authorConnection && connectionsSent.add(connection)) {
                    chat.sendMessageToPlayer(playerID, msg);
                }
            }
        }

        PLZFixes.hit(PLZFixes.CHAT_SAY_SAME_FLOOR);
    }
}

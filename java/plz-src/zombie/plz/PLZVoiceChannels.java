package zombie.plz;

import zombie.core.raknet.VoiceManager;
import zombie.network.GameClient;

public final class PLZVoiceChannels {
    private PLZVoiceChannels() {
    }

    public static void republish() {
        if (GameClient.client && GameClient.connection != null) {
            VoiceManager.instance.UpdateChannelsRoaming(GameClient.connection);
        }
    }
}

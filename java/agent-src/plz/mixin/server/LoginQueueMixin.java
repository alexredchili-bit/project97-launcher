package plz.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import zombie.core.raknet.UdpConnection;
import zombie.network.LoginQueue;
import zombie.plz.PLZLoginQueue;

@Mixin(LoginQueue.class)
public abstract class LoginQueueMixin {
    /**
     * @author PLZ
     * @reason the tiered, multi-slot queue in PLZLoginQueue replaces vanilla's single loading slot
     */
    @Overwrite
    public static void receiveLoginQueueDone(long gameLoadingTime, UdpConnection connection) {
        PLZLoginQueue.receiveLoginQueueDone(gameLoadingTime, connection);
    }

    /**
     * @author PLZ
     * @reason see receiveLoginQueueDone
     */
    @Overwrite
    public static void receiveServerLoginQueueRequest(UdpConnection connection) {
        PLZLoginQueue.receiveServerLoginQueueRequest(connection);
    }

    /**
     * @author PLZ
     * @reason see receiveLoginQueueDone
     */
    @Overwrite
    public static void disconnect(UdpConnection connection) {
        PLZLoginQueue.disconnect(connection);
    }

    /**
     * @author PLZ
     * @reason see receiveLoginQueueDone
     */
    @Overwrite
    public static boolean isInTheQueue(UdpConnection connection) {
        return PLZLoginQueue.isInTheQueue(connection);
    }

    /**
     * @author PLZ
     * @reason see receiveLoginQueueDone
     */
    @Overwrite
    public static void update() {
        PLZLoginQueue.update();
    }

    /**
     * @author PLZ
     * @reason see receiveLoginQueueDone
     */
    @Overwrite
    public static int getCountPlayers() {
        return PLZLoginQueue.getCountPlayers();
    }

    /**
     * @author PLZ
     * @reason see receiveLoginQueueDone
     */
    @Overwrite
    public static String getDescription() {
        return PLZLoginQueue.getDescription();
    }
}

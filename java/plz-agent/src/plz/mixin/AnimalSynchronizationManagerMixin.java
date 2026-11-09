package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.characters.animals.IsoAnimal;
import zombie.core.raknet.UdpConnection;
import zombie.network.IConnection;
import zombie.network.PacketTypes;
import zombie.network.packets.character.AnimalUpdatePacket;
import zombie.network.packets.character.AnimalUpdateReliablePacket;
import zombie.plz.PLZAnimalSync;
import zombie.plz.access.PLZAnimalSynchronizationManagerAccess;
import zombie.popman.animal.AnimalInstanceManager;
import zombie.popman.animal.AnimalSynchronizationManager;

@Mixin(AnimalSynchronizationManager.class)
abstract class AnimalSynchronizationManagerMixin implements PLZAnimalSynchronizationManagerAccess {
    @Unique
    private static final HashSet<Short> plz$gone = new HashSet<>();

    @Shadow
    @Final
    private static HashMap<Long, HashSet<Short>> requests;

    @Shadow
    @Final
    private static HashSet<Short> receivedToSend;

    @Shadow
    @Final
    private static HashSet<Short> deletedToSend;

    @Shadow
    public abstract void sendRequestToServer(IConnection connection);

    @Override
    public void plzRequest(IConnection connection, Collection<Short> onlineIDs) {
        requests.computeIfAbsent(connection.getConnectedGUID(), k -> new HashSet<>()).addAll(onlineIDs);
        this.sendRequestToServer(connection);
    }

    @Inject(method = "sendUpdateToClient", at = @At("HEAD"))
    private void plz$answerGone(
        UdpConnection connection,
        boolean isReliable,
        HashSet<Short> toSendList,
        CallbackInfo ci,
        @Share("plz") LocalBooleanRef plzRef,
        @Share("deletionsSent") LocalBooleanRef deletionsSent,
        @Share("connection") LocalRef<UdpConnection> connectionRef
    ) {
        connectionRef.set(connection);
        boolean plz = PLZAnimalSync.serverOn();
        plzRef.set(plz);
        HashSet<Short> gone = plz$gone;
        gone.clear();
        if (plz) {
            HashSet<Short> asked = requests.computeIfAbsent(connection.getConnectedGUID(), k -> new HashSet<>());
            for (short onlineID : asked) {
                IsoAnimal animal = AnimalInstanceManager.getInstance().get(onlineID);
                if (animal == null
                    || !PLZAnimalSync.isPresentable(animal)
                    || animal.isExistInTheWorld() && !connection.RelevantTo(animal.getX(), animal.getY(), (connection.getRelevantRange() - 2) * 10)) {
                    gone.add(onlineID);
                    PLZAnimalSync.countAnsweredGone();
                }
            }

            asked.removeAll(gone);
            if (!isReliable && toSendList == receivedToSend && (!deletedToSend.isEmpty() || !gone.isEmpty())) {
                plz$sendDeletions(connection, gone);
                deletionsSent.set(true);
            }
        }
    }

    @Unique
    private static boolean plz$unanswerable(Short onlineID) {
        return AnimalInstanceManager.getInstance().get(onlineID) == null || plz$gone.contains(onlineID);
    }

    @WrapOperation(
        method = "sendUpdateToClient",
        at = @At(value = "INVOKE", target = "Ljava/util/HashSet;iterator()Ljava/util/Iterator;", ordinal = 0)
    )
    private Iterator<Short> plz$skipUnanswerable(HashSet<Short> requested, Operation<Iterator<Short>> original, @Share("plz") LocalBooleanRef plz) {
        if (plz.get()) {
            for (Short onlineID : requested) {
                if (plz$unanswerable(onlineID)) {
                    LinkedHashSet<Short> answerable = new LinkedHashSet<>();
                    for (Short id : requested) {
                        if (!plz$unanswerable(id)) {
                            answerable.add(id);
                        }
                    }

                    return answerable.iterator();
                }
            }
        }

        return original.call(requested);
    }

    @WrapOperation(
        method = "sendUpdateToClient",
        at = @At(value = "INVOKE", target = "Ljava/util/HashSet;add(Ljava/lang/Object;)Z", ordinal = 0)
    )
    private boolean plz$refreshAnswer(
        HashSet<Short> requested,
        Object id,
        Operation<Boolean> original,
        @Share("plz") LocalBooleanRef plz,
        @Share("connection") LocalRef<UdpConnection> connection
    ) {
        if (plz.get()) {
            short onlineID = (Short) id;
            IsoAnimal answer = AnimalInstanceManager.getInstance().get(onlineID);
            try {
                answer.getNetworkCharacterAI().setAnimalPacket(connection.get());
                PLZAnimalSync.countAnsweredState();
            } catch (RuntimeException e) {
                PLZAnimalSync.countRefreshFailed(onlineID, e);
            }
        }

        return original.call(requested, id);
    }

    @WrapOperation(
        method = "sendUpdateToClient",
        at = @At(value = "INVOKE", target = "Ljava/util/HashSet;addAll(Ljava/util/Collection;)Z", ordinal = 0)
    )
    private boolean plz$deleteUnlessSent(
        HashSet<Short> deleted,
        Collection<Short> toDelete,
        Operation<Boolean> original,
        @Share("deletionsSent") LocalBooleanRef deletionsSent
    ) {
        boolean changed = false;
        if (!deletionsSent.get()) {
            changed = original.call(deleted, toDelete);
            deleted.addAll(plz$gone);
        }

        plz$gone.clear();
        return changed;
    }

    @Unique
    private static void plz$sendDeletions(UdpConnection connection, HashSet<Short> gone) {
        PacketTypes.PacketType packetType = PacketTypes.PacketType.AnimalUpdateReliable;
        AnimalUpdatePacket packet = (AnimalUpdateReliablePacket) connection.getPacket(packetType);
        packet.getRequested().clear();
        packet.getUpdated().clear();
        packet.getPending().clear();
        packet.getDeleted().clear();
        packet.getDeleted().addAll(deletedToSend);
        packet.getDeleted().addAll(gone);
        packet.sendToClient(packetType, connection);
        PLZAnimalSync.countForcedReliable();
    }
}

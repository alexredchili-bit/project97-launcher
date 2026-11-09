package plz.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.entity.Component;
import zombie.entity.GameEntity;
import zombie.entity.GameEntityNetwork;
import zombie.entity.network.EntityPacketData;
import zombie.network.IConnection;
import zombie.network.PacketTypes;
import zombie.plz.PLZEntitySync;

@Mixin(GameEntityNetwork.class)
abstract class GameEntityNetworkMixin {
    @WrapOperation(
        method = "sendPacketData(Lzombie/entity/network/EntityPacketData;Lzombie/entity/GameEntity;Lzombie/entity/Component;Lzombie/network/IConnection;Z)V",
        at = @At(value = "INVOKE", target = "Lzombie/network/packets/INetworkPacket;sendToAll(Lzombie/network/PacketTypes$PacketType;Lzombie/network/IConnection;[Ljava/lang/Object;)V")
    )
    private static void plz$sendToRelevant(
        PacketTypes.PacketType packetType,
        IConnection connection,
        Object[] values,
        Operation<Void> original,
        @Local(argsOnly = true) EntityPacketData data,
        @Local(argsOnly = true) GameEntity entity,
        @Local(argsOnly = true) Component component
    ) {
        if (!PLZEntitySync.sendToRelevant(connection, data, entity, component)) {
            original.call(packetType, connection, values);
        }
    }
}

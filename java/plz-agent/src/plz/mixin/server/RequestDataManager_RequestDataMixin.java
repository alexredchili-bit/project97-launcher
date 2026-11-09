package plz.mixin.server;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import zombie.network.packets.RequestDataPacket;
import zombie.plz.access.PLZRequestDataAccess;

@Mixin(targets = "zombie.network.RequestDataManager$RequestData")
abstract class RequestDataManager_RequestDataMixin implements PLZRequestDataAccess {
    @Shadow
    @Final
    private RequestDataPacket.RequestID id;

    @Shadow
    @Final
    private long connectionGuid;

    @Shadow
    private long creationTime;

    @Override
    public long plzConnectionGuid() {
        return this.connectionGuid;
    }

    @Override
    public RequestDataPacket.RequestID plzId() {
        return this.id;
    }

    @Override
    public long plzCreationTime() {
        return this.creationTime;
    }
}

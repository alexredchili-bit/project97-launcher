package plz.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import zombie.core.raknet.VoiceManagerData;
import zombie.radio.devices.DeviceData;

@Mixin(VoiceManagerData.RadioData.class)
public interface VoiceManagerData_RadioDataMixin {
    @Accessor("deviceData")
    DeviceData plz$getDeviceData();

    @Accessor("lastReceiveDistance")
    float plz$getLastReceiveDistance();

    @Accessor("lastReceiveDistance")
    void plz$setLastReceiveDistance(float distance);
}

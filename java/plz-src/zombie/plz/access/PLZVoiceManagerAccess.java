package zombie.plz.access;

import zombie.core.raknet.VoiceManagerData;

public interface PLZVoiceManagerAccess {
    long plzIndicatorIsVoice();

    boolean plzServerVOIPEnable();

    float plzMinDistance();

    float plzMaxDistance();

    VoiceManagerData.RadioData plzRadioLink(VoiceManagerData theirs);
}

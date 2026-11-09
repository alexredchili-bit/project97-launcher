package zombie.plz.access;

import zombie.characters.IsoGameCharacter;

public interface PLZIsoAnimalAccess {
    boolean plzIsLedHorse();

    boolean plzHorseShouldMove();

    void plzMoveToHorseTarget(IsoGameCharacter leader);

    long plzGetNetSeenMs();

    void plzSetNetSeenMs(long ms);

    long plzGetNetAskedMs();

    void plzSetNetAskedMs(long ms);

    int plzGetNetAsks();

    void plzSetNetAsks(int asks);
}

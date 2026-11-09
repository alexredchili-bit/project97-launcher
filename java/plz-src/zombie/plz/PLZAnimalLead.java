package zombie.plz;

import zombie.characters.IsoGameCharacter;

public final class PLZAnimalLead {
    private PLZAnimalLead() {
    }

    public static boolean leaderRuns(IsoGameCharacter leader) {
        return leader != null && leader.isPlayerMoving() && (leader.isRunning() || leader.isSprinting());
    }
}

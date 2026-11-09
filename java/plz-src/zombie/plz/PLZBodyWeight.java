package zombie.plz;

import zombie.characters.IsoPlayer;

public final class PLZBodyWeight {
    // 70 is inside vanilla's Underweight band; safe only while Nutrition.applyTraitFromWeight adds no traits.
    public static final double MIN_WEIGHT = 70.0;
    public static final double MAX_WEIGHT = 80.0;
    // Vanilla's 2000 updates is over three minutes at a dedicated server's ~10fps, too slow to strip a legacy trait.
    public static final int TRAIT_INTERVAL = 200;
    // Per game-world second: the full band takes roughly two in-game days to cross.
    public static final double WEIGHT_RATE = 5.8E-5;

    private PLZBodyWeight() {
    }

    public static double clamp(double weight) {
        if (weight < MIN_WEIGHT) {
            return MIN_WEIGHT;
        } else if (weight > MAX_WEIGHT) {
            return MAX_WEIGHT;
        }

        return weight;
    }

    // Calories are deliberately not an input: with Nutrition off nothing decays the reserve, parking everyone at the ceiling.
    public static double target(IsoPlayer player) {
        float condition = PLZDietScore.conditionOf(player);
        return MIN_WEIGHT + (MAX_WEIGHT - MIN_WEIGHT) * condition;
    }
}

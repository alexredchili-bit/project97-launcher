package zombie.plz;

import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import zombie.core.Translator;
import zombie.entity.GameEntity;
import zombie.entity.components.fluids.FluidContainer;
import zombie.entity.components.fluids.FluidInstance;
import zombie.inventory.InventoryItem;

public final class PLZDrinkName {
    public static final String STAMP_KEY = "plzDrink";
    public static final double DEFAULT_TOLERANCE = 0.05;
    private static final double EPSILON = 0.0001;
    private static final int MAX_FLUIDS = 8;

    private PLZDrinkName() {
    }

    public static String nameFor(FluidContainer container, List<FluidInstance> fluids) {
        try {
            if (container == null || fluids == null || fluids.isEmpty()) {
                return null;
            }
            GameEntity owner = container.getOwner();
            if (!(owner instanceof InventoryItem item) || !item.hasModData()) {
                return null;
            }
            if (!(item.getModData().rawget(STAMP_KEY) instanceof KahluaTable stamp)) {
                return null;
            }
            String drink = drinkName(stamp);
            if (drink == null) {
                return null;
            }
            int count = fluids.size();
            if (count > MAX_FLUIDS) {
                return null;
            }
            String[] names = new String[count];
            double[] amounts = new double[count];
            for (int i = 0; i < count; i++) {
                FluidInstance instance = fluids.get(i);
                names[i] = instance.getFluid().getFluidTypeString();
                amounts[i] = instance.getAmount();
            }
            if (!matches(stamp, names, amounts)) {
                return null;
            }
            return Translator.getText("Fluid_HoldingOneType", container.getTranslatedContainerName(), drink);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String drinkName(KahluaTable stamp) {
        if (stamp == null) {
            return null;
        }
        if (stamp.rawget("k") instanceof String key && !key.isEmpty()) {
            return Translator.getText(key);
        }
        if (stamp.rawget("n") instanceof String name && !name.isEmpty()) {
            return name;
        }
        return null;
    }

    public static boolean matches(KahluaTable stamp, String[] names, double[] amounts) {
        if (stamp == null || !(stamp.rawget("r") instanceof KahluaTable ratios)) {
            return false;
        }
        double tolerance = asDouble(stamp.rawget("t"), DEFAULT_TOLERANCE) + EPSILON;
        double total = 0.0;
        for (double amount : amounts) {
            total += amount;
        }
        if (total <= EPSILON) {
            return false;
        }
        int wanted = 0;
        KahluaTableIterator it = ratios.iterator();
        while (it.advance()) {
            if (!(it.getKey() instanceof String fluid)) {
                return false;
            }
            double share = asDouble(it.getValue(), -1.0);
            double have = 0.0;
            for (int i = 0; i < names.length; i++) {
                if (fluid.equals(names[i])) {
                    have += amounts[i] / total;
                }
            }
            if (share < 0.0 || Math.abs(have - share) > tolerance) {
                return false;
            }
            wanted++;
        }
        return wanted > 0 && wanted == names.length;
    }

    public static double asDouble(Object value, double fallback) {
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }
}

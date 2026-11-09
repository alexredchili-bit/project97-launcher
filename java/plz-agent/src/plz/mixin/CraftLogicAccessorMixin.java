package plz.mixin;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import zombie.entity.components.crafting.CraftLogic;
import zombie.entity.components.crafting.recipe.CraftRecipeData;

@Mixin(CraftLogic.class)
public interface CraftLogicAccessorMixin {
    @Invoker("getAllInProgressCraftData")
    ArrayList<CraftRecipeData> plz$getAllInProgressCraftData();
}

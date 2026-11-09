package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import zombie.ZomboidFileSystem;
import zombie.characters.action.ActionGroup;
import zombie.characters.action.ActionState;
import zombie.plz.PLZFixes;

@Mixin(ActionGroup.class)
abstract class ActionGroupMixin {
    // Same unsynchronised-map race as AnimationSet; a group load may reach an anim set, never the reverse, so no deadlock.
    @Unique
    private static final Object plz$GROUP_MAP_LOCK = new Object();

    @Shadow
    private String name;

    @Shadow
    public abstract ActionState getOrCreate(String stateName);

    // Mod action states live under every mod's media/actiongroups, not only the game's.
    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Ljava/io/File;listFiles()[Ljava/io/File;"))
    private File[] plz$loadModStates(File folder, Operation<File[]> original) {
        String[] stateDirs = ZomboidFileSystem.instance.resolveAllDirectories("media/actiongroups/" + this.name, dir -> true, false);

        for (String stateDir : stateDirs) {
            ActionState state = this.getOrCreate(new File(stateDir).getName());
            state.load(stateDir);
        }

        return null;
    }

    @WrapMethod(method = "getActionGroup")
    private static ActionGroup plz$lockGet(String groupName, Operation<ActionGroup> original) {
        if (PLZFixes.on(PLZFixes.ACTION_GROUP_SYNC)) {
            synchronized (plz$GROUP_MAP_LOCK) {
                PLZFixes.hit(PLZFixes.ACTION_GROUP_SYNC);
                return original.call(groupName);
            }
        }

        return original.call(groupName);
    }

    @WrapMethod(method = "reloadAll")
    private static void plz$lockReload(Operation<Void> original) {
        if (PLZFixes.on(PLZFixes.ACTION_GROUP_SYNC)) {
            synchronized (plz$GROUP_MAP_LOCK) {
                original.call();
                return;
            }
        }

        original.call();
    }
}

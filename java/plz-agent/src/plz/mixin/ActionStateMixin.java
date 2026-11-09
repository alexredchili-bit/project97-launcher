package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zombie.ZomboidFileSystem;
import zombie.characters.action.ActionState;

@Mixin(ActionState.class)
abstract class ActionStateMixin {
    // Resolves through the mod-aware file system so a mod's state XMLs load, not only the base folder's.
    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Ljava/io/File;listFiles(Ljava/io/FilenameFilter;)[Ljava/io/File;"))
    private File[] plz$resolveStateFiles(File folder, FilenameFilter filter, Operation<File[]> original, @Local(argsOnly = true) String stateFolderPath) {
        String[] relativeFiles = ZomboidFileSystem.instance
            .resolveAllFiles(stateFolderPath, file -> file.getName().toLowerCase().endsWith(".xml"), false);
        if (relativeFiles == null || relativeFiles.length == 0) {
            return null;
        }

        ArrayList<File> listOfFiles = new ArrayList<>();
        for (String relativeFile : relativeFiles) {
            String absolutePath = ZomboidFileSystem.instance.resolveFileOrGUID(relativeFile);
            if (absolutePath != null) {
                listOfFiles.add(new File(absolutePath));
            }
        }

        return listOfFiles.toArray(new File[0]);
    }
}

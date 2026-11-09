package zombie.plz;

import java.io.File;
import java.util.Locale;
import zombie.GameWindow;
import zombie.ZomboidFileSystem;
import zombie.core.SpriteRenderer;
import zombie.core.skinnedmodel.model.MeshAssetManager;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.population.OutfitManager;
import zombie.debug.DebugType;
import zombie.network.GameServer;
import zombie.plz.access.PLZModelManagerAccess;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.AnimationsMesh;

public final class PLZModelMeshReload {
    private PLZModelMeshReload() {
    }

    // create() binds the body meshes at boot, before the server's mod list exists, so a server-sent body mod stays inert.
    public static void reloadBodyModels(boolean created, Model maleModel, Model femaleModel, ModelMesh animModel) {
        if (!created || GameServer.server) {
            return;
        }

        int reloaded = reloadBodyMesh(maleModel, "skinned/malebody", animModel) + reloadBodyMesh(femaleModel, "skinned/femalebody", animModel);
        if (reloaded > 0) {
            waitForBodyMeshes(maleModel, femaleModel);
        }
    }

    private static int reloadBodyMesh(Model model, String meshName, ModelMesh animModel) {
        if (model == null || model.mesh == null) {
            return 0;
        }

        String resolved = resolveMeshFile(meshName);
        if (resolved == null) {
            DebugType.Animation.error("PLZ: nothing resolves body mesh \"" + meshName + "\"; keeping the one already loaded");
            return 0;
        }

        if (!model.mesh.isFailure() && resolved.equalsIgnoreCase(model.mesh.fullPath)) {
            return 0;
        }

        ModelMesh.MeshAssetParams assetParams = new ModelMesh.MeshAssetParams();
        assetParams.isStatic = false;
        assetParams.animationsMesh = animModel;
        assetParams.postProcess = null;
        DebugType.Animation.println("PLZ: body mesh \"" + meshName + "\" now resolves to " + resolved + "; reloading it");
        MeshAssetManager.instance.reload(model.mesh, assetParams);
        return 1;
    }

    // Same fbx-glb-x order as FileTask_AbstractLoadModel: a body mod ships FemaleBody.fbx beside vanilla's .x, not over it.
    private static String resolveMeshFile(String meshName) {
        String base = "media/models_x/" + meshName.toLowerCase(Locale.ENGLISH);

        for (String extension : new String[]{".fbx", ".glb", ".x"}) {
            String path = ZomboidFileSystem.instance.getString(base + extension);
            if (new File(path).exists()) {
                return path;
            }
        }

        return null;
    }

    private static void waitForBodyMeshes(Model maleModel, Model femaleModel) {
        // On the join's ResetLua the pump runs foreign callbacks with OutfitManager still null, and an NPE there aborts the join.
        if (OutfitManager.instance == null) {
            DebugType.Animation.println(
                "PLZ: body meshes are reloading asynchronously - the engine is still coming up "
                    + "(this is the server-join path), so they are not waited for here");
            return;
        }

        boolean bClient = SpriteRenderer.instance != null;
        long deadline = System.currentTimeMillis() + 30000L;

        while (isLoadingBodyMesh(maleModel) || isLoadingBodyMesh(femaleModel)) {
            // Safety net: anything escaping the foreign callbacks would cost a connection, so give up waiting instead.
            try {
                GameWindow.fileSystem.updateAsyncTransactions();
                if (bClient) {
                    SpriteRenderer.instance.notifyRenderStateQueue();
                }
            } catch (Throwable failure) {
                DebugType.Animation.error(
                    "PLZ: an async task failed while waiting for the player body meshes; leaving "
                        + "them to finish on their own (" + failure + ")");
                return;
            }

            if (System.currentTimeMillis() > deadline) {
                DebugType.Animation.error("PLZ: timed out reloading the player body meshes; bodies stay vanilla until the game is restarted");
                return;
            }

            try {
                Thread.sleep(5L);
            } catch (InterruptedException ignored) {
            }
        }

        if (bodyMeshFailed(maleModel) || bodyMeshFailed(femaleModel)) {
            DebugType.Animation.error("PLZ: a player body mesh failed to reload; bodies stay vanilla until the game is restarted");
        } else {
            DebugType.Animation.println("PLZ: player body meshes reloaded from the active mod list");
        }
    }

    private static boolean isLoadingBodyMesh(Model model) {
        return model != null && model.mesh != null && !model.mesh.isFailure() && !model.mesh.isReady();
    }

    private static boolean bodyMeshFailed(Model model) {
        return model != null && model.mesh != null && model.mesh.isFailure();
    }

    public static void waitForAnimationMeshes(PLZModelManagerAccess modelManager) {
        int requeued = requeueFailedAnimationMeshes();
        boolean bClient = !GameServer.server && SpriteRenderer.instance != null;
        long deadline = System.currentTimeMillis() + 30000L;

        while (modelManager.plzIsLoadingAnimationMeshes()) {
            GameWindow.fileSystem.updateAsyncTransactions();
            if (bClient) {
                SpriteRenderer.instance.notifyRenderStateQueue();
            }

            if (System.currentTimeMillis() > deadline) {
                DebugType.Animation.error("PLZ: timed out waiting for animation meshes; modded animations may be missing until restart");
                return;
            }

            try {
                Thread.sleep(5L);
            } catch (InterruptedException ignored) {
            }
        }

        if (reportFailedAnimationMeshes() == 0) {
            DebugType.Animation
                .println("PLZ: animation meshes ready before loadModAnimations" + (requeued > 0 ? " (" + requeued + " reloaded first)" : ""));
        }
    }

    // A failed mesh is not "loading", so without a retry the wait reports success and that mod's animations never load.
    private static int requeueFailedAnimationMeshes() {
        int requeued = 0;

        for (AnimationsMesh am : ScriptManager.instance.getAllAnimationsMeshes()) {
            ModelMesh mesh = am.modelMesh;
            if (mesh == null || !mesh.isFailure()) {
                continue;
            }

            ModelMesh.MeshAssetParams assetParams = new ModelMesh.MeshAssetParams();
            assetParams.isStatic = false;
            assetParams.animationsMesh = null;
            assetParams.postProcess = am.postProcess;
            DebugType.Animation.error("PLZ: animation mesh \"" + am.meshFile + "\" failed to load; asking for it again");
            MeshAssetManager.instance.reload(mesh, assetParams);
            requeued++;
        }

        return requeued;
    }

    private static int reportFailedAnimationMeshes() {
        int failed = 0;

        for (AnimationsMesh am : ScriptManager.instance.getAllAnimationsMeshes()) {
            if (am.modelMesh != null && am.modelMesh.isFailure()) {
                failed++;
                DebugType.Animation
                    .error(
                        "PLZ: animation mesh \"" + am.meshFile + "\" is still failed after a reload; that mod's animations stay missing until the game is restarted"
                    );
            }
        }

        return failed;
    }
}

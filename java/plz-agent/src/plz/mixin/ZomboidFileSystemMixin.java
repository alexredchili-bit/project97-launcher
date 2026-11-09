package plz.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zombie.DebugFileWatcher;
import zombie.GameWindow;
import zombie.PredicatedFileWatcher;
import zombie.ZomboidFileSystem;
import zombie.core.textures.Texture;
import zombie.debug.DebugType;
import zombie.network.GameServer;
import zombie.plz.PLZAssetRefusals;
import zombie.plz.PLZBodyOverride;
import zombie.plz.PLZModFolderBaseline;
import zombie.plz.PLZPrefixRetry;
import zombie.util.ResettableLazyValue;

@Mixin(ZomboidFileSystem.class)
abstract class ZomboidFileSystemMixin {
    @Shadow
    private volatile List<String> modFolders;

    @Shadow
    private List<String> modFoldersOrder;

    @Shadow
    @Final
    public Map<String, String> activeFileMap;

    @Shadow
    @Final
    private ResettableLazyValue<List<Path>> allowedPrefixes;

    @Shadow
    @Final
    private PredicatedFileWatcher modFileWatcher;

    @Shadow
    @Final
    private Set<String> watchedModFolders;

    @Unique
    private int plz$bestModFolderCount;

    // Insertion-ordered: the undo runs in reverse load order, since two tracked mods can write the same path.
    @Unique
    private LinkedHashMap<String, ArrayList<String[]>> plz$trackedFiles;

    @Unique
    private Boolean plz$bodyOverrideApplied;

    // Every read and write of modFolders holds this; a partial lock loses resets to a walk publishing over them.
    @Unique
    private Object plz$modFoldersLock;

    @Shadow
    public abstract void setModFoldersOrder(String s);

    @Shadow
    public abstract void getStagedItemModsFolders(List<String> out);

    @Shadow
    public abstract void getInstalledItemModsFolders(List<String> out);

    @Shadow
    public abstract String getCacheDirSub(String subPath);

    @Shadow
    public abstract void resetModFolders();

    @Shadow
    private void getAllModFoldersAux(String folder, List<String> out) {
        throw new AssertionError();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$init(CallbackInfo ci) {
        this.plz$trackedFiles = new LinkedHashMap<>();
        this.plz$modFoldersLock = new Object();
    }

    @Inject(method = "Reset", at = @At(value = "INVOKE", target = "Lzombie/modding/ActiveMods;Reset()V"))
    private void plz$resetTracked(CallbackInfo ci) {
        this.plz$trackedFiles.clear();
    }

    @WrapOperation(
        method = "resetModFolders",
        at = @At(value = "FIELD", target = "Lzombie/ZomboidFileSystem;modFolders:Ljava/util/List;", opcode = org.objectweb.asm.Opcodes.PUTFIELD)
    )
    private void plz$lockedReset(ZomboidFileSystem fs, List<String> value, Operation<Void> original) {
        synchronized (this.plz$modFoldersLock) {
            original.call(fs, value);
        }
    }

    // A caller already inside a retry does not start a new retry budget; see PLZPrefixRetry.newSession.
    @Inject(method = "resetModFolders", at = @At("TAIL"))
    private void plz$newRetrySession(CallbackInfo ci) {
        PLZPrefixRetry.newSession();
    }

    /**
     * @author PLZ
     * @reason A private lock instead of the synchronized method, because the checked walk can sleep for seconds and
     *     getString/isKnownFile share this monitor; the walk itself moves to a retrying helper.
     */
    @Overwrite
    public void getAllModFolders(List<String> out) {
        List<String> folders;
        synchronized (this.plz$modFoldersLock) {
            if (this.modFolders == null) {
                this.modFolders = this.plz$walkModFoldersChecked();
            }

            folders = this.modFolders;
        }

        out.clear();
        out.addAll(folders);
    }

    // A short walk is measured against a high-water mark: a raced walk taken on trust costs the player their session.
    @Unique
    private ArrayList<String> plz$walkModFoldersChecked() {
        long[] backoffMs = {250L, 1000L, 3000L};
        ArrayList<String> built = this.plz$walkModFolders();

        // The mark comes from PLZModFolderBaseline so the first walk of a process, the coldest one, is checked too.
        int best = Math.max(this.plz$bestModFolderCount, PLZModFolderBaseline.best());

        for (int attempt = 0; attempt < backoffMs.length && built.size() < best; attempt++) {
            DebugType.Mod
                .warn(
                    "PLZ: mod folder walk returned " + built.size() + ", best known is " + best + "; walking again in "
                        + backoffMs[attempt] + "ms"
                );

            try {
                Thread.sleep(backoffMs[attempt]);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }

            built = this.plz$walkModFolders();
        }

        if (built.size() < best) {
            // An unsubscribe shortens the list for good, so the mark comes down after several launches agree.
            if (PLZModFolderBaseline.recordShort(built.size())) {
                this.plz$bestModFolderCount = built.size();
                DebugType.Mod
                    .warn(
                        "PLZ: mod folder walk has returned " + built.size()
                            + " across several launches; taking it as the new baseline, which is what an unsubscribe looks like."
                    );
            } else {
                this.plz$bestModFolderCount = best;
                DebugType.Mod
                    .error(
                        "PLZ: mod folder walk is still short (" + built.size() + " of " + best
                            + "). Workshop assets under the missing folders will be refused until it is walked again."
                    );
            }
        } else {
            this.plz$bestModFolderCount = built.size();
            PLZModFolderBaseline.recordGood(built.size());
        }

        return built;
    }

    @Unique
    private ArrayList<String> plz$walkModFolders() {
        ArrayList<String> built = new ArrayList<>();
        if (this.modFoldersOrder == null) {
            this.setModFoldersOrder("workshop,steam,mods");
        }

        List<String> modsFolders = new ArrayList<>();

        for (int i = 0; i < this.modFoldersOrder.size(); i++) {
            String s = this.modFoldersOrder.get(i);
            if ("workshop".equals(s)) {
                this.getStagedItemModsFolders(modsFolders);
            }

            if ("steam".equals(s)) {
                this.getInstalledItemModsFolders(modsFolders);
            }

            if ("mods".equals(s)) {
                File file = new File(ZomboidFileSystem.instance.getCacheDirSub("mods"));
                if (!file.exists()) {
                    file.mkdirs();
                }

                modsFolders.add(file.getAbsolutePath());
            }
        }

        for (int j = 0; j < modsFolders.size(); j++) {
            String folder = modsFolders.get(j);
            if (!this.watchedModFolders.contains(folder)) {
                this.watchedModFolders.add(folder);
                DebugFileWatcher.instance.addDirectory(folder);
            }

            this.getAllModFoldersAux(folder, built);
        }

        DebugFileWatcher.instance.add(this.modFileWatcher);
        return built;
    }

    @Inject(method = "loadMod", at = @At(value = "INVOKE", target = "Lzombie/ZomboidFileSystem;getModInfoForDir(Ljava/lang/String;)Lzombie/gameStates/ChooseGameInfo$Mod;"))
    private void plz$decideTracking(String modId, CallbackInfo ci, @Share("plzTrackedMod") LocalRef<String> trackedMod) {
        trackedMod.set(!GameServer.server && PLZBodyOverride.isTracked(modId) ? modId : null);
    }

    @WrapOperation(
        method = "loadMod",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        require = 2,
        allow = 2
    )
    private Object plz$recordTracked(
        Map<String, String> map,
        Object rel,
        Object absPath,
        Operation<Object> original,
        @Share("plzTrackedMod") LocalRef<String> trackedMod
    ) {
        if (trackedMod.get() != null) {
            this.plz$record(trackedMod.get(), (String)rel, this.activeFileMap.get(rel), (String)absPath);
        }

        return original.call(map, rel, absPath);
    }

    @Inject(method = "loadMods(Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", ordinal = 0, shift = At.Shift.AFTER))
    private void plz$clearTracked(List<String> toLoad, CallbackInfo ci) {
        this.plz$trackedFiles.clear();
    }

    @Inject(method = "loadMods(Ljava/util/List;)V", at = @At("TAIL"))
    private void plz$applyBodyOverride(List<String> toLoad, CallbackInfo ci) {
        if (GameServer.server) {
            return;
        }

        boolean enabled = PLZBodyOverride.isEnabled();
        if (!enabled) {
            int undone = this.plz$undoTrackedMods();
            DebugType.Mod.println("PLZ: body mods off by client choice; " + undone + " file override(s) undone");
        }

        // Texture caches are keyed by name and survive ResetLua; setTexturePackLookup rebuilds, onTexturePacksChanged does not.
        if (this.plz$bodyOverrideApplied != null && this.plz$bodyOverrideApplied != enabled) {
            GameWindow.setTexturePackLookup();
            DebugType.Mod.println("PLZ: body override changed to " + enabled + "; texture caches dropped");
        }

        this.plz$bodyOverrideApplied = enabled;
    }

    @Unique
    private void plz$record(String modId, String rel, String before, String after) {
        if (!plz$isRevertable(rel)) {
            return;
        }

        this.plz$trackedFiles.computeIfAbsent(modId, id -> new ArrayList<>()).add(new String[] { rel, before, after });
    }

    // An allowlist: removing anything the MP checksum covers diverges the client total and gets them kicked.
    @Unique
    private static boolean plz$isRevertable(String rel) {
        return rel.startsWith("media/models_x/") || rel.startsWith("media/models/") || rel.startsWith("media/textures/");
    }

    // A path no longer holding the value the mod wrote was overridden later; restoring it would undo that mod too.
    @Unique
    private int plz$undoTrackedMods() {
        ArrayList<String> order = new ArrayList<>(this.plz$trackedFiles.keySet());
        int undone = 0;

        for (int i = order.size() - 1; i >= 0; i--) {
            ArrayList<String[]> records = this.plz$trackedFiles.get(order.get(i));

            for (int n = records.size() - 1; n >= 0; n--) {
                String[] record = records.get(n);
                if (!record[2].equals(this.activeFileMap.get(record[0]))) {
                    continue;
                }

                if (record[1] == null) {
                    this.activeFileMap.remove(record[0]);
                } else {
                    this.activeFileMap.put(record[0], record[1]);
                }

                undone++;
            }
        }

        return undone;
    }

    // Snapshotted once under the lock: this runs on the DebugFileWatcher thread and a reset mid-loop was an NPE.
    @WrapOperation(
        method = "isModFile",
        at = @At(value = "FIELD", target = "Lzombie/ZomboidFileSystem;modFolders:Ljava/util/List;", opcode = org.objectweb.asm.Opcodes.GETFIELD),
        require = 3,
        allow = 3
    )
    private List<String> plz$snapshotModFolders(
        ZomboidFileSystem fs,
        Operation<List<String>> original,
        @Share("plzFolders") LocalRef<List<String>> snapshot,
        @Share("plzFoldersRead") LocalBooleanRef read
    ) {
        if (!read.get()) {
            synchronized (this.plz$modFoldersLock) {
                snapshot.set(original.call(fs));
            }

            read.set(true);
        }

        return snapshot.get();
    }

    @Inject(method = "validatePrefix", at = @At("HEAD"))
    private void plz$noteGeneration(String input, CallbackInfo ci, @Share("plzGeneration") LocalLongRef generation) {
        generation.set(PLZPrefixRetry.generation());
    }

    @Inject(
        method = "validatePrefix",
        at = @At(value = "NEW", target = "java/lang/IllegalArgumentException"),
        cancellable = true
    )
    private void plz$retryRefusal(
        String input,
        CallbackInfo ci,
        @Local(ordinal = 0) Path inputPath,
        @Share("plzGeneration") LocalLongRef generation
    ) {
        if (PLZPrefixRetry.refresh(generation.get(), this::plz$refreshAllowedPrefixes) && this.plz$matchesAllowedPrefix(inputPath)) {
            DebugType.Mod.println("validatePrefix: allowed after re-enumerating mod folders: " + input);
            this.plz$forgetRefusedTextures();
            ci.cancel();
            return;
        }

        // Nothing the player sees names the mod or file, so record it for the client to surface.
        PLZAssetRefusals.record(input);
    }

    @Unique
    private boolean plz$matchesAllowedPrefix(Path inputPath) {
        List<Path> prefixes = this.allowedPrefixes.get();
        int size = prefixes.size();

        for (int i = 0; i < size; i++) {
            if (inputPath.startsWith(prefixes.get(i))) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private void plz$refreshAllowedPrefixes() {
        this.resetModFolders();
        this.allowedPrefixes.get();
    }

    // Texture.nullTextures remembers refusals until onTexturePacksChanged, which the connect path never calls.
    @Unique
    private void plz$forgetRefusedTextures() {
        int dropped = Texture.nullTextures.size();
        if (dropped > 0) {
            Texture.nullTextures.clear();
            DebugType.Mod
                .println(
                    "PLZ: dropped " + dropped + " refused-texture entr" + (dropped == 1 ? "y" : "ies")
                        + " so they retry now the mod folder list is good again"
                );
        }
    }
}

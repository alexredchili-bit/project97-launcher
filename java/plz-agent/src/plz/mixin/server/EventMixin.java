package plz.mixin.server;

import java.util.ArrayList;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.krka.kahlua.integration.LuaCaller;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaClosure;
import zombie.GameProfiler;
import zombie.Lua.Event;
import zombie.Lua.LuaManager;
import zombie.core.logger.ExceptionLogger;
import zombie.debug.DebugOptions;
import zombie.debug.DebugType;
import zombie.plz.PLZEventSkip;
import zombie.plz.PLZLuaProfile;
import zombie.plz.access.PLZEventAccess;

@Mixin(Event.class)
abstract class EventMixin implements PLZEventAccess {
    @Shadow
    @Final
    public ArrayList<LuaClosure> callbacks;

    @Shadow
    public String name;

    // Vanilla rebuilt "Lua - " + name on every callback of every trigger, profiler running or not.
    @Unique
    private String plz$profileKey;

    @Unique
    private long[] plz$nanos;

    @Unique
    private long[] plz$calls;

    @Unique
    private int plz$slots;

    @Unique
    private boolean[] plz$skip;

    @Unique
    private int plz$skipSlots;

    @Unique
    private int plz$skipGen;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void plz$initSlots(String name, int index, CallbackInfo ci) {
        this.plz$slots = -1;
        this.plz$skipSlots = -1;
        this.plz$skipGen = -1;
    }

    /**
     * @author PLZ
     * @reason Hot path: one cached profile key, optional per-callback counters and skip flags, both vanilla branches merged.
     */
    @Overwrite
    public boolean trigger(KahluaTable env, LuaCaller caller, Object[] params) {
        if (this.callbacks.isEmpty()) {
            return false;
        }

        GameProfiler profiler = GameProfiler.getInstance();
        String profileKey = this.plz$profileKey();
        boolean slowChecks = DebugOptions.instance.checks.slowLuaEvents.getValue();
        boolean profiling = PLZLuaProfile.enabled;
        boolean timing = slowChecks || profiling;

        if (profiling) {
            this.plz$syncCounters();
        }

        boolean skipping = PLZEventSkip.enabled;
        if (skipping) {
            this.plz$syncSkips();
        }

        for (int n = 0; n < this.callbacks.size(); n++) {
            // Before the closure is even fetched: a refused callback costs an array read.
            if (skipping && this.plz$skip != null && n < this.plz$skip.length && this.plz$skip[n]) {
                PLZEventSkip.countSkipped();
                continue;
            }

            LuaClosure closure = this.callbacks.get(n);

            try (GameProfiler.ProfileArea area = profiler.profile(profileKey)) {
                if (timing) {
                    long start = System.nanoTime();

                    try {
                        caller.protectedCallVoid(LuaManager.thread, closure, params);
                    } finally {
                        long delta = System.nanoTime() - start;
                        if (profiling && n >= 0 && n < this.plz$slots) {
                            this.plz$nanos[n] += delta;
                            this.plz$calls[n]++;
                        }

                        if (slowChecks) {
                            double delayMS = delta / 1000000.0;
                            if (delayMS > 250.0) {
                                DebugType.Lua.warn("SLOW Lua event callback %s %s %dms", closure.prototype.file, closure, (int)delayMS);
                            }
                        }
                    }
                } else {
                    caller.protectedCallVoid(LuaManager.thread, closure, params);
                }
            } catch (Exception ex) {
                ExceptionLogger.logException(ex);
            }

            if (!this.callbacks.contains(closure)) {
                n--;
            }
        }

        return true;
    }

    @Unique
    private String plz$profileKey() {
        String key = this.plz$profileKey;
        if (key == null) {
            key = "Lua - " + this.name;
            this.plz$profileKey = key;
        }

        return key;
    }

    // A slot is an index into callbacks, so a resized list restarts the counters rather than report another handler's time.
    @Unique
    private void plz$syncCounters() {
        int count = this.callbacks.size();
        if (this.plz$slots == count && this.plz$nanos != null) {
            return;
        }

        this.plz$nanos = new long[count];
        this.plz$calls = new long[count];
        this.plz$slots = count;
        PLZLuaProfile.track((Event) (Object) this);
    }

    // Resolved here so PLZFixes and the file-path match stay off the per-callback path; the generation catches rule changes.
    @Unique
    private void plz$syncSkips() {
        int count = this.callbacks.size();
        int gen = PLZEventSkip.generation();
        if (this.plz$skipSlots == count && this.plz$skipGen == gen && this.plz$skip != null) {
            return;
        }

        boolean[] flags = new boolean[count];
        for (int i = 0; i < count; i++) {
            flags[i] = PLZEventSkip.shouldSkip(this.name, this.callbacks.get(i));
        }

        this.plz$skip = flags;
        this.plz$skipSlots = count;
        this.plz$skipGen = gen;
    }

    @Override
    public long[] plzNanos() {
        return this.plz$nanos;
    }

    @Override
    public long[] plzCalls() {
        return this.plz$calls;
    }

    @Override
    public void plzResetCounters() {
        long[] nanos = this.plz$nanos;
        if (nanos != null) {
            Arrays.fill(nanos, 0L);
        }

        long[] calls = this.plz$calls;
        if (calls != null) {
            Arrays.fill(calls, 0L);
        }
    }
}

"""Executable single-job admission regressions using the two actual assembler mixin bodies.

Run: python -B src/test/python/test_recipe_lock_ae_admission.py -v
No Gradle, Minecraft bootstrap or Mixin transformation. JDK 17+ compiles the actual fields
and handlers (only annotations/imports and shadow initializers are adapted) against small
native-job/inventory/NBT stand-ins. Exact-JAR instruction/descriptor/host-IO contracts live
in test_recipe_lock_ae_adapters.py; the JDK 21 real-dependency compilation lives in
test_recipe_lock_ae_regressions.py. These do not replace server integration tests.
"""

from pathlib import Path
import re
import subprocess
import tempfile
import unittest

from test_recipe_lock_ae_adapters import annotations, source
from test_recipe_lock_ae_regressions import CONTEXT, java_tools


PREFIX = r"""
import java.util.*;
public class AssemblyAdmissionHarness {
    @interface Nullable {}
    interface Operation<T> { T call(Object... arguments); }
    interface IPatternDetails {}
    interface IMolecularAssemblerSupportedPattern extends IPatternDetails { Definition getDefinition(); }
    record Definition(String item) { ItemStack toStack() { return new ItemStack(item, 1); } }
    static final class Plan implements IMolecularAssemblerSupportedPattern {
        final String item;
        Plan(String item) { this.item = item; }
        public Definition getDefinition() { return new Definition(item); }
        public boolean equals(Object other) { return other instanceof Plan plan && item.equals(plan.item); }
        public int hashCode() { return item.hashCode(); }
    }
    static final class KeyCounter { int amount = 1; }
    enum Direction { DOWN }
    enum TickRateModulation { SLEEP, IDLE, SLOWER, FASTER }
    static final class IGridNode {}
    static final class CraftingInput {}
    static final class HolderLookup { static final class Provider {} }
    static final HolderLookup.Provider REGISTRIES = new HolderLookup.Provider();
    static final String MARKER = "molecularmanipulator:admitted_craft";
    static final class CompoundTag {
        final Map<String, Object> values = new HashMap<>();
        void put(String key, CompoundTag value) { values.put(key, value); }
        void putInt(String key, int value) { values.put(key, value); }
        void putString(String key, String value) { values.put(key, value); }
        int getInt(String key) { return (int) values.getOrDefault(key, 0); }
        String getString(String key) { return (String) values.getOrDefault(key, ""); }
        CompoundTag getCompound(String key) { return (CompoundTag) values.getOrDefault(key, new CompoundTag()); }
        boolean contains(String key) { return values.containsKey(key); }
        void remove(String key) { values.remove(key); }
    }
    static final class ItemStack {
        static final ItemStack EMPTY = new ItemStack("", 0);
        final String item;
        final int count;
        ItemStack(String item, int count) { this.item = item; this.count = count; }
        boolean isEmpty() { return count == 0 || item.isEmpty(); }
        static boolean matches(ItemStack left, ItemStack right) {
            return left.isEmpty() && right.isEmpty() || left.count == right.count && left.item.equals(right.item);
        }
        CompoundTag save(HolderLookup.Provider registries) {
            var tag = new CompoundTag(); tag.putString("item", item); tag.putInt("count", count); return tag;
        }
        static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag tag) {
            return new ItemStack(tag.getString("item"), tag.getInt("count"));
        }
        public String toString() { return item + ":" + count; }
    }
    static final class AppEngInternalInventory {
        final ItemStack[] items;
        AppEngInternalInventory(int slots) { items = new ItemStack[slots]; Arrays.fill(items, ItemStack.EMPTY); }
        ItemStack getStackInSlot(int slot) { return items[slot]; }
        void setItemDirect(int slot, ItemStack item) { items[slot] = item; }
        boolean isEmpty() { return Arrays.stream(items).allMatch(ItemStack::isEmpty); }
    }
    static final class Level {
        boolean isClientSide, rules = true, grant = true;
        int checks, ruleScans;
    }
    static class Player {}
    static class BlockEntity {
        Level level = new Level();
        int saves;
        Level getLevel() { return level; }
        void saveChanges() { saves++; }
    }
    static class AEBaseBlockEntity extends BlockEntity {}
    static class MolecularAssemblerBlockEntity extends AEBaseBlockEntity {}
    static final class RecipeLocks {
        static boolean hasRules(Level level) { level.ruleScans++; return level.rules; }
        static boolean allowedPattern(BlockEntity machine, IPatternDetails pattern) {
            machine.level.checks++; return machine.level.grant;
        }
    }
    static final class State {
        final boolean extended;
        boolean matrix, forced, reject, throwAccept, throwAssembly, emptyAssembly, reusable, failDecode, blockedOutput;
        int nativeAccepts, nativeTicks, energy = 1000, progress, assemblies, completed, ejected, trials, cachedOutputs;
        final AppEngInternalInventory grid = new AppEngInternalInventory(10);
        final AppEngInternalInventory patterns = new AppEngInternalInventory(1);
        State(boolean extended) { this.extended = extended; }
    }
    interface Driver {
        State state();
        AEBaseBlockEntity machine();
        IMolecularAssemblerSupportedPattern plan();
        void plan(IMolecularAssemblerSupportedPattern plan);
        ItemStack pattern();
        void pattern(ItemStack pattern);
        boolean awake();
        void awake(boolean awake);
        boolean admitted();
        boolean pending();
        boolean accept(IPatternDetails pattern, KeyCounter[] inputs);
        TickRateModulation tick();
        ItemStack assemble(CraftingInput input);
        CompoundTag writeTag();
        void readTag(CompoundTag tag);
        void recalculate();
        default boolean nativeAccept(Object... args) {
            check(RecipeExecutionContext.actor() == machine(), "Admission scope uses the actual machine");
            var state = state(); state.nativeAccepts++;
            if (state.throwAccept) throw new IllegalStateException("accept failure");
            if (state.reject || !pattern().isEmpty() || !state.grid.isEmpty() || !state.patterns.isEmpty()
                    || !(args[0] instanceof IMolecularAssemblerSupportedPattern)) return false;
            state.forced = true; plan((IMolecularAssemblerSupportedPattern) args[0]);
            state.grid.setItemDirect(0, new ItemStack("material", 1));
            ((KeyCounter[]) args[1])[0].amount = 0;
            awake(true); machine().saveChanges(); return true;
        }
        default TickRateModulation nativeTick() {
            check(RecipeExecutionContext.actor() == machine(), "Tick scope uses the actual machine");
            var state = state(); state.nativeTicks++;
            if (!state.grid.getStackInSlot(9).isEmpty()) {
                if (!state.blockedOutput) { state.grid.setItemDirect(9, ItemStack.EMPTY); state.ejected++; }
                state.progress = 0; updateAwake(); return TickRateModulation.IDLE;
            }
            if (plan() == null || !awake()) return TickRateModulation.SLEEP;
            state.energy -= 25; state.progress += 25;
            if (state.progress >= 100) {
                state.progress = 0;
                var output = assemble(new CraftingInput());
                check(!admitted() && !pending(), "Burn admission before native output/remainder callbacks");
                check(!writeTag().contains(MARKER), "An output callback save cannot copy admission to another round");
                if (!output.isEmpty()) {
                    state.completed++;
                    if (!state.reusable) state.grid.setItemDirect(0, ItemStack.EMPTY);
                    state.grid.setItemDirect(9, output);
                    if (state.extended || state.patterns.isEmpty()) { state.forced = false; plan(null); }
                    updateAwake(); machine().saveChanges();
                } else if (state.extended) {
                    // Exact EAE failure branch clears the plan and returns held ingredients, not consumes them.
                    state.forced = false; plan(null);
                    state.grid.setItemDirect(9, state.grid.getStackInSlot(0));
                    state.grid.setItemDirect(0, ItemStack.EMPTY); updateAwake();
                }
            }
            return TickRateModulation.FASTER;
        }
        default ItemStack nativeAssemble() {
            var state = state(); state.assemblies++;
            check(admitted(), "Actual assembly must start inside the single admitted job");
            if (state.throwAssembly) throw new IllegalStateException("assembly failure");
            if (state.matrix) state.cachedOutputs++; // Matrix returns cached output without calling plan.assemble.
            return state.emptyAssembly ? ItemStack.EMPTY : new ItemStack("result", 1);
        }
        default void nativeRecalculate() {
            var state = state();
            if (state.forced) {
                if (machine().level != null && plan() == null) {
                    if (!pattern().isEmpty() && !state.failDecode) plan(new Plan(pattern().item));
                    pattern(ItemStack.EMPTY);
                    if (plan() == null) state.forced = false;
                }
                return;
            }
            var installed = state.patterns.getStackInSlot(0);
            if (!state.extended && !installed.isEmpty() && machine().level != null && !state.failDecode) {
                if (!ItemStack.matches(installed, pattern())) {
                    plan(new Plan(installed.item)); pattern(installed); state.progress = 0;
                }
            } else { plan(null); pattern(ItemStack.EMPTY); state.progress = 0; }
            updateAwake();
        }
        default void updateAwake() {
            awake(plan() != null && !state().grid.getStackInSlot(0).isEmpty()
                    || !state().grid.getStackInSlot(9).isEmpty());
        }
        default void nativeWrite(CompoundTag tag) {
            var state = state();
            if (state.extended || state.forced) {
                var saved = plan() != null ? plan().getDefinition().toStack() : pattern();
                if (!saved.isEmpty()) { tag.put("myPlan", saved.save(REGISTRIES)); tag.putInt("pushDirection", 0); }
            }
            tag.put("installed", state.patterns.getStackInSlot(0).save(REGISTRIES));
        }
        default void nativeRead(CompoundTag tag) {
            if (!state().matrix) readGrid(tag);
            state().patterns.setItemDirect(0, ItemStack.parseOptional(REGISTRIES, tag.getCompound("installed")));
            state().forced = false; state().progress = 0; pattern(ItemStack.EMPTY); plan(null);
            if (tag.contains("myPlan")) {
                var saved = ItemStack.parseOptional(REGISTRIES, tag.getCompound("myPlan"));
                if (!saved.isEmpty()) { state().forced = true; pattern(saved); }
            }
            recalculate();
        }
        default CompoundTag save() {
            var tag = writeTag();
            // AE's super save and EAE's host serialize this same grid with the original plan/tag.
            for (int slot = 0; slot < 10; slot++) tag.put("slot" + slot, state().grid.getStackInSlot(slot).save(REGISTRIES));
            return tag;
        }
        default void readGrid(CompoundTag tag) {
            for (int slot = 0; slot < 10; slot++)
                state().grid.setItemDirect(slot, ItemStack.parseOptional(REGISTRIES, tag.getCompound("slot" + slot)));
        }
        default void load(CompoundTag tag) {
            readTag(tag);
            // MatrixCrafter.loadTag really restores inputs AFTER CraftingThread.readNBT returns.
            if (state().matrix) { readGrid(tag); recalculate(); }
            updateAwake();
        }
        default void preflight() { state().trials++; /* Native hasMats' assemble is deliberately not wrapped. */ }
        default String contents() {
            return Arrays.toString(state().grid.items) + "/" + state().energy + "/" + state().progress;
        }
    }
"""

BRIDGE = r"""
    public State state() { return fixture; }
    public AEBaseBlockEntity machine() { return MACHINE; }
    public IMolecularAssemblerSupportedPattern plan() { return this.myPlan; }
    public void plan(IMolecularAssemblerSupportedPattern plan) { this.myPlan = plan; }
    public ItemStack pattern() { return this.myPattern; }
    public void pattern(ItemStack pattern) { this.myPattern = pattern; }
    public boolean awake() { return this.isAwake; }
    public void awake(boolean awake) { this.isAwake = awake; }
    public boolean admitted() { return this.recipeLock$admittedPlan != null; }
    public boolean pending() { return !this.recipeLock$restoringAdmission.isEmpty(); }
    public boolean accept(IPatternDetails pattern, KeyCounter[] inputs) {
        return recipeLock$beforeAccept(pattern, inputs, Direction.DOWN, this::nativeAccept);
    }
    public void recalculate() {
        recipeLock$afterPlanChange(args -> { nativeRecalculate(); return null; });
    }
    public void readTag(CompoundTag tag) {
        recipeLock$readAdmission(tag, REGISTRIES, args -> { nativeRead((CompoundTag) args[0]); return null; });
    }
"""

AE_BRIDGE = r"""
    public TickRateModulation tick() { return recipeLock$beforeExecute(null, 1, args -> nativeTick()); }
    public ItemStack assemble(CraftingInput input) {
        return recipeLock$consumeAdmission(this.myPlan, input, getLevel(), args -> nativeAssemble());
    }
    public CompoundTag writeTag() {
        var data = new CompoundTag();
        recipeLock$writeAdmission(data, REGISTRIES, args -> { nativeWrite((CompoundTag) args[0]); return null; });
        return data;
    }
"""

EXTENDED_BRIDGE = r"""
    public TickRateModulation tick() { return recipeLock$beforeCachedExecution(0, 1, args -> nativeTick()); }
    public ItemStack assemble(CraftingInput input) {
        return recipeLock$consumeAdmission(this, input, args -> nativeAssemble());
    }
    public CompoundTag writeTag() {
        return recipeLock$writeAdmission(REGISTRIES, args -> { var data = new CompoundTag(); nativeWrite(data); return data; });
    }
"""

MAIN = r"""
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Driver create(int kind) {
        if (kind == 0) return new AEAssemblerAdapter();
        var driver = new ExtendedAssemblerAdapter(); driver.state().matrix = kind == 2; return driver;
    }
    static KeyCounter[] inputs() { return new KeyCounter[] {new KeyCounter()}; }
    static Plan plan() { return new Plan("encoded:recipe"); }
    static void accept(Driver driver) {
        var inputs = inputs();
        check(driver.accept(plan(), inputs), "Authorized native admission must succeed");
        check(inputs[0].amount == 0 && driver.admitted(), "Only the actual accepted input grid gets admission");
    }
    static void finish(Driver driver) {
        int before = driver.state().completed;
        for (int i = 0; i < 5 && driver.state().completed == before; i++) driver.tick();
        check(driver.state().completed == before + 1 && !driver.admitted(), "Exactly one admitted craft settles");
    }
    static void denyWithoutMutation(Driver driver) {
        var before = driver.contents(); int ticks = driver.state().nativeTicks;
        check(driver.tick() == TickRateModulation.SLOWER, "Unadmitted job must be denied");
        check(before.equals(driver.contents()) && ticks == driver.state().nativeTicks,
                "Denial must not consume inputs, output, energy or progress");
    }
    static void admitted(int kind) {
        var driver = create(kind); accept(driver);
        int scans = driver.machine().level.ruleScans, checks = driver.machine().level.checks;
        driver.machine().level.grant = false; // Engine offline / shared grant revoked / lock reload.
        driver.preflight(); check(driver.admitted(), "hasMats trial assembly cannot spend admission");
        finish(driver);
        check(scans == driver.machine().level.ruleScans && checks == driver.machine().level.checks,
                "Accepted job must not scan live permissions on every tick");
        check(driver.state().grid.getStackInSlot(0).isEmpty(), "Native successful settlement consumes input normally");
        if (kind == 2) check(driver.state().cachedOutputs == 1, "Matrix cached output still spends admission");
        int energy = driver.state().energy;
        driver.state().blockedOutput = true; driver.tick(); driver.tick();
        check(driver.state().completed == 1 && !driver.admitted() && driver.machine().level.checks == checks
                && !driver.state().grid.getStackInSlot(9).isEmpty(),
                "Blocked output keeps retrying ejection without permission scans or another assembly");
        driver.state().blockedOutput = false; driver.tick();
        check(driver.state().ejected == 1 && driver.state().energy == energy,
                "Completed output must eject without checking grants or starting a craft");
        var inputs = inputs();
        check(!driver.accept(plan(), inputs) && inputs[0].amount == 1 && !driver.admitted(),
                "Even the identical cached pattern must be checked anew on the next push/accept");
        check(driver.machine().level.checks == checks + 1, "No permanent pattern permission cache");
    }
    static void isolated(int kind) {
        var accepted = create(kind); var shared = plan();
        check(accepted.accept(shared, inputs()), "First thread accepts the shared pattern");
        var other = create(kind); other.plan(shared); other.state().forced = true;
        other.state().grid.setItemDirect(0, new ItemStack("material", 1)); other.updateAwake();
        other.machine().level.grant = false; denyWithoutMutation(other);
        check(accepted.admitted() && !other.admitted(), "Even the same plan reference cannot share a token across jobs/threads");
        accepted.machine().level.grant = false; finish(accepted);
        check(!other.save().contains(MARKER), "Another thread cannot serialize the first thread's admission");
    }
    static void failed(int kind) {
        var driver = create(kind); driver.state().reject = true;
        check(!driver.accept(plan(), inputs()) && !driver.admitted(), "Native false return cannot mint admission");
        driver.state().reject = false; driver.state().throwAccept = true;
        try { driver.accept(plan(), inputs()); throw new AssertionError("Must propagate acceptance failure"); }
        catch (IllegalStateException expected) {}
        check(!driver.admitted() && !RecipeExecutionContext.active(), "Failed admission must leave no token/scope");
        driver.state().throwAccept = false; accept(driver);
        check(!driver.accept(plan(), inputs()) && driver.admitted(), "Busy rejection must not revoke the existing job");
        driver.machine().level.grant = false;
        check(!driver.accept(plan(), inputs()) && driver.admitted(), "Denied new push must not revoke the existing job");
        finish(driver);
        driver = create(kind); driver.machine().level.isClientSide = true;
        check(!driver.accept(plan(), inputs()) && driver.tick() == TickRateModulation.SLEEP
                && driver.state().nativeAccepts == 0 && !driver.admitted(), "Client cannot accept or advance work");
        driver.machine().level = null;
        check(!driver.accept(plan(), inputs()) && driver.tick() == TickRateModulation.SLEEP,
                "Detached machine cannot accept or advance work");
    }
    static void installed(int ignored) {
        var driver = create(0); driver.state().patterns.setItemDirect(0, plan().getDefinition().toStack());
        driver.recalculate(); driver.tick();
        check(!driver.admitted() && driver.machine().level.checks == 0, "Pattern installation alone is not a job");
        driver.state().grid.setItemDirect(0, new ItemStack("material", 1));
        driver.recalculate(); driver.state().reusable = true; driver.machine().level.grant = false;
        denyWithoutMutation(driver);
        driver.machine().level.grant = true; driver.tick();
        check(driver.admitted(), "Installed-pattern automatic work must check once at real start");
        var originalPlan = driver.plan(); int checks = driver.machine().level.checks;
        driver.machine().level.grant = false; driver.preflight(); finish(driver);
        check(driver.plan() == originalPlan, "Native installed-pattern cache persists after assembly");
        driver.tick(); denyWithoutMutation(driver);
        check(driver.state().completed == 1 && !driver.state().grid.getStackInSlot(0).isEmpty()
                && driver.machine().level.checks == checks + 1,
                "Same plan and reusable input cannot reuse the first craft's token for a second round");
    }
    static void persistence(int kind) {
        var driver = create(kind); driver.machine().level.rules = false; accept(driver); driver.tick();
        var before = driver.contents(); var saved = driver.save();
        check(saved.contains(MARKER) && saved.contains("myPlan") && saved.contains("slot0")
                && before.equals(driver.contents()), "Save single-job marker alongside native plan and exact inputs");
        var restored = create(kind); restored.machine().level.grant = false;
        // Matrix restores per-thread NBT before overwriting its old inventory, including an old output slot.
        if (kind == 2) restored.state().grid.setItemDirect(9, new ItemStack("old-output", 1));
        restored.load(saved);
        check(restored.admitted() && !restored.pending() && restored.plan() != driver.plan()
                && restored.machine().level.checks == 0 && restored.state().progress == 0,
                "Explicit saved job resumes once, not a permission/progress snapshot or raw plan cache");
        finish(restored);
        var completed = restored.save(); check(!completed.contains(MARKER), "Settlement must persist token consumption");
        var eject = create(kind); eject.machine().level.grant = false; eject.load(completed); eject.tick();
        check(eject.state().ejected == 1 && eject.machine().level.checks == 0 && !eject.admitted(),
                "Unmarked saved completed output always ejects");
    }
    static void legacy(int kind) {
        var old = create(kind); accept(old); var saved = old.save(); saved.remove(MARKER);
        var restored = create(kind); restored.machine().level.grant = false; restored.load(saved);
        check(!restored.admitted() && !restored.pending(), "Legacy saved myPlan is not proof of admission");
        denyWithoutMutation(restored);
        restored.machine().level.grant = true; restored.tick(); restored.machine().level.grant = false;
        finish(restored);
        var reused = create(kind); accept(reused); reused.machine().level.grant = false; reused.load(saved);
        check(!reused.admitted(), "Loading legacy data into a live instance must clear the former job token");
        denyWithoutMutation(reused);
    }
    static void replacement(int kind) {
        var driver = create(kind); accept(driver); var saved = driver.save();
        var old = driver.plan(); var equal = new Plan(old.getDefinition().item());
        check(old.equals(equal), "Fixture must exercise equal definitions with distinct plan identities");
        driver.plan(equal); driver.recalculate(); driver.machine().level.grant = false;
        check(!driver.admitted(), "Recalculation cannot transfer admission to an equal replacement recipe/plan");
        denyWithoutMutation(driver);
        saved.put(MARKER, new ItemStack("different:recipe", 1).save(REGISTRIES));
        var restored = create(kind); restored.machine().level.grant = false; restored.load(saved);
        check(!restored.admitted() && !restored.pending(), "Mismatched saved pattern cannot inherit admission");
        denyWithoutMutation(restored);
        // No native saved plan or installed pattern: a marker alone cannot wait for a future installation.
        var orphan = new CompoundTag(); orphan.put(MARKER, plan().getDefinition().toStack().save(REGISTRIES));
        restored.machine().level = null; restored.load(orphan);
        check(!restored.pending(), "Orphan marker cannot authorize a later job");
        restored.machine().level = new Level(); restored.machine().level.grant = false;
        restored.state().forced = true; restored.plan(plan());
        restored.state().grid.setItemDirect(0, new ItemStack("material", 1)); restored.updateAwake();
        denyWithoutMutation(restored);
    }
    static void deferred(int kind) {
        var driver = create(kind); accept(driver); var saved = driver.save();
        var restored = create(kind); restored.machine().level = null; restored.load(saved);
        check(restored.pending() && !restored.admitted(), "Valid saved job waits for native deferred pattern decode");
        var waitingSave = restored.save(); check(waitingSave.contains(MARKER), "Save before level attachment retains that job only");
        restored.machine().level = new Level(); restored.machine().level.grant = false;
        restored.recalculate(); restored.updateAwake();
        check(restored.admitted() && !restored.pending(), "Native decode binds and consumes pending restoration exactly once");
        finish(restored);
        check(restored.machine().level.checks == 0, "Restoration cannot recheck revoked grants for an admitted batch");
        var failed = create(kind); failed.machine().level = null; failed.load(waitingSave);
        failed.state().failDecode = true; failed.machine().level = new Level(); failed.recalculate();
        check(!failed.admitted() && !failed.pending(), "Failed decode cannot leave permission for a later recipe replacement");
        failed.state().failDecode = false; failed.state().forced = true; failed.plan(plan());
        failed.machine().level.grant = false; failed.updateAwake(); denyWithoutMutation(failed);
        var changed = create(kind); changed.machine().level = null; changed.load(waitingSave);
        changed.pattern(new ItemStack("replacement:recipe", 1)); changed.recalculate();
        check(!changed.pending(), "Replacing deferred saved pattern must discard, not copy, the admission marker");
    }
    static void installedPersistence(int ignored) {
        var driver = create(0); driver.state().patterns.setItemDirect(0, plan().getDefinition().toStack());
        driver.state().grid.setItemDirect(0, new ItemStack("material", 1)); driver.recalculate(); driver.tick();
        var saved = driver.save();
        check(saved.contains(MARKER) && !saved.contains("myPlan"),
                "Automatic-job marker must persist with native installed pattern/input IO, not manufacture forced plans");
        var restored = create(0); restored.machine().level = null; restored.load(saved);
        check(restored.pending(), "Installed-pattern deferred load must retain only the marked filled grid");
        restored.machine().level = new Level(); restored.machine().level.grant = false;
        restored.recalculate(); restored.updateAwake(); restored.state().reusable = true;
        finish(restored); restored.tick(); denyWithoutMutation(restored);
    }
    static void assemblyFailure(int kind) {
        var driver = create(kind); accept(driver); driver.state().emptyAssembly = true;
        for (int i = 0; i < 4; i++) driver.tick();
        check(!driver.admitted() && !driver.save().contains(MARKER) && driver.state().completed == 0,
                "Empty actual assembly spends the token too, never a forever-accepted retry loop");
        check(!driver.state().grid.getStackInSlot(kind == 0 ? 0 : 9).isEmpty(), "Failed assembly must retain/return materials");
        if (kind == 0) { driver.machine().level.grant = false; denyWithoutMutation(driver); }
        driver = create(kind); accept(driver); driver.state().throwAssembly = true;
        for (int i = 0; i < 3; i++) driver.tick();
        int saves = driver.machine().saves;
        try { driver.tick(); throw new AssertionError("Must propagate assembly failure"); }
        catch (IllegalStateException expected) {}
        check(!driver.admitted() && !driver.pending() && !RecipeExecutionContext.active()
                && driver.machine().saves > saves && !driver.state().grid.getStackInSlot(0).isEmpty(),
                "Throwing assembly closes scope, persists token burn, and never eats the inputs");
        driver.machine().level.grant = false; denyWithoutMutation(driver);
    }
    public static void main(String[] args) {
        String scenario = args[0];
        for (int kind = 0; kind < 3; kind++) {
            switch (scenario) {
                case "admitted" -> admitted(kind);
                case "failed" -> failed(kind);
                case "isolated" -> isolated(kind);
                case "installed" -> installed(kind);
                case "persistence" -> persistence(kind);
                case "legacy" -> legacy(kind);
                case "replacement" -> replacement(kind);
                case "deferred" -> deferred(kind);
                case "installedPersistence" -> installedPersistence(kind);
                case "assemblyFailure" -> assemblyFailure(kind);
                default -> throw new AssertionError(scenario);
            }
            check(!RecipeExecutionContext.active(), "No execution scope may escape any scenario");
        }
        System.out.println(scenario + " admission regressions passed");
    }
}
"""


def adapter_body(name, target, extended):
    """Keep every production field/handler, not a Python reimplementation of token logic."""
    text = source(name)
    text = text[text.index("public abstract class " + name):]
    for kind in ("WrapMethod", "WrapOperation"):
        for annotation, _ in list(annotations(text, kind)):
            text = text.replace("@" + kind + "(" + annotation + ")", "")
    text = re.sub(r"@(Shadow|Final|Unique|Coerce)\s*", "", text)
    parent = " implements Driver" if extended else " extends MolecularAssemblerBlockEntity implements Driver"
    text = text.replace("public abstract class " + name + " {", "static final class " + target + parent + " {\n"
                        "    private final State fixture = new State(" + str(extended).lower() + ");", 1)
    text = text.replace("private ItemStack myPattern;", "private ItemStack myPattern = ItemStack.EMPTY;")
    text = text.replace("AppEngInternalInventory gridInv;", "AppEngInternalInventory gridInv = fixture.grid;")
    text = text.replace("AppEngInternalInventory patternInv;", "AppEngInternalInventory patternInv = fixture.patterns;")
    text = text.replace("AEBaseBlockEntity host;", "AEBaseBlockEntity host = new AEBaseBlockEntity();")
    bridge = BRIDGE.replace("MACHINE", "this.host" if extended else "this")
    bridge += EXTENDED_BRIDGE if extended else AE_BRIDGE
    return text.rsplit("}", 1)[0] + bridge + "\n}\n"


class ActualAssemblerAdmissionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tools = java_tools(17)
        if cls.tools is None:
            raise unittest.SkipTest("JDK 17+ required for isolated executable single-job checks")
        context = CONTEXT.read_text(encoding="utf-8")
        context = context[context.index("public final class RecipeExecutionContext"):]
        context = context.replace("public final class RecipeExecutionContext", "static final class RecipeExecutionContext", 1)
        java = PREFIX + context + "\n"
        java += adapter_body("RecipeLockAEMolecularAssemblerMixin", "AEAssemblerAdapter", False)
        java += adapter_body("RecipeLockExtendedCraftingThreadMixin", "ExtendedAssemblerAdapter", True)
        java += MAIN
        cls.directory = tempfile.TemporaryDirectory(prefix="recipe-lock-ae-admission-")
        cls.addClassCleanup(cls.directory.cleanup)
        path = Path(cls.directory.name) / "AssemblyAdmissionHarness.java"
        path.write_text(java, encoding="utf-8")
        compiled = subprocess.run([cls.tools[0], "-encoding", "UTF-8", "-proc:none", "-d", cls.directory.name, str(path)],
                                  capture_output=True, text=True, timeout=45)
        if compiled.returncode:
            raise AssertionError(compiled.stdout + compiled.stderr)

    def run_scenario(self, name):
        executed = subprocess.run([self.tools[1], "-cp", self.directory.name, "AssemblyAdmissionHarness", name],
                                  capture_output=True, text=True, timeout=20)
        self.assertEqual(0, executed.returncode, executed.stdout + executed.stderr)
        self.assertIn(name + " admission regressions passed", executed.stdout)

    def test_accepted_job_finishes_after_revocation_without_tick_rechecks_and_next_accept_is_fresh(self):
        self.run_scenario("admitted")

    def test_same_plan_reference_cannot_share_admission_between_machines_or_threads(self):
        self.run_scenario("isolated")

    def test_native_false_exception_busy_denied_and_client_admissions_never_create_or_copy_tokens(self):
        self.run_scenario("failed")

    def test_installed_pattern_checks_actual_start_and_cannot_repeat_with_same_plan_and_reusable_inputs(self):
        self.run_scenario("installed")

    def test_explicit_job_marker_round_trip_and_matrix_inventory_load_order(self):
        self.run_scenario("persistence")

    def test_legacy_save_cannot_authorize_and_live_instance_load_drops_previous_token(self):
        self.run_scenario("legacy")

    def test_equal_plan_replacement_mismatched_nbt_and_orphan_marker_cannot_copy_admission(self):
        self.run_scenario("replacement")

    def test_deferred_level_decode_save_before_attach_and_failed_or_changed_plan_are_one_shot(self):
        self.run_scenario("deferred")

    def test_installed_automatic_job_marker_persists_without_changing_native_forced_plan_tags(self):
        self.run_scenario("installedPersistence")

    def test_empty_or_throwing_actual_assembly_burns_token_without_eating_materials(self):
        self.run_scenario("assemblyFailure")


if __name__ == "__main__":
    unittest.main()

package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** Pattern-isolated input queues and an independent, long-valued output cache. */
public final class MatterPatternBuffer {
    private static final int VERSION = 1;
    private final MatterFabricationPatternAssemblyBlockEntity assembly;
    private final List<QueuedWork> queued = new ArrayList<>();
    // Derived from the queued recipes, never persisted as a second copy of their materials.
    private final Object2LongLinkedOpenHashMap<AEKey> inputs = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongLinkedOpenHashMap<AEKey> refunds = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongLinkedOpenHashMap<AEKey> outputs = new Object2LongLinkedOpenHashMap<>();
    private final MatterFabricationBatch active = new MatterFabricationBatch();
    private final AEKeyTransferScheduler outputTransfer = new AEKeyTransferScheduler();
    private final AEKeyTransferScheduler refundTransfer = new AEKeyTransferScheduler();
    private CompoundTag unavailable;
    private boolean reconcilePatterns = true;
    private boolean refundFirst;

    MatterPatternBuffer(MatterFabricationPatternAssemblyBlockEntity assembly) { this.assembly = assembly; }

    public boolean hasContents() { return !queued.isEmpty() || !refunds.isEmpty() || !outputs.isEmpty() || active.hasWork() || unavailable != null; }
    public boolean hasQueuedInputs() { return !queued.isEmpty() || !refunds.isEmpty(); }
    public boolean isProcessing() { return active.hasWork(); }
    public boolean isUnavailable() { return unavailable != null || active.unavailable(); }
    public int queuedPatterns() { return queued.size(); }
    void patternsChanged() { reconcilePatterns = true; }

    long capacity(IPatternDetails pattern, Map<AEKey, Long> unitInputs) {
        if (isUnavailable() || !assembly.isOperational() || unitInputs.isEmpty()) return 0;
        var controller = assembly.getController();
        var recipe = MatterFabricationBatch.match(controller, pattern, unitInputs, 1);
        if (recipe == null) return 0;
        long limit = Long.MAX_VALUE;
        for (var entry : unitInputs.entrySet()) {
            if (entry.getKey() == null || entry.getValue() <= 0) return 0;
            long stored = Math.addExact(Math.addExact(inputs.getLong(entry.getKey()), refunds.getLong(entry.getKey())), active.storedInputAmount(entry.getKey()));
            limit = Math.min(limit, (Long.MAX_VALUE - stored) / entry.getValue());
        }
        limit = Math.min(limit, MatterFabricationBatch.inputCapacity(recipe.value()));
        // A single AE delivery must also be representable by GenericStack's long amount.
        for (var entry : MatterFabricationBatch.patternOutputs(pattern).entrySet()) {
            if (entry.getKey() == null || entry.getValue() <= 0) return 0;
            limit = Math.min(limit, Long.MAX_VALUE / entry.getValue());
        }
        return limit;
    }

    boolean enqueue(IPatternDetails pattern, Map<AEKey, Long> supplied, long crafts) {
        try {
            if (crafts <= 0 || isUnavailable() || !assembly.isOperational() || supplied.isEmpty()) return false;
            var controller = assembly.getController();
            var candidates = MatterFabricationBatch.candidates(controller, pattern);
            var recipe = MatterFabricationBatch.match(controller, candidates, supplied, crafts);
            if (recipe == null) return false;
            for (var entry : supplied.entrySet()) {
                if (entry.getKey() == null || entry.getValue() <= 0) return false;
                long stored = Math.addExact(Math.addExact(inputs.getLong(entry.getKey()), refunds.getLong(entry.getKey())), active.storedInputAmount(entry.getKey()));
                if (entry.getValue() > Long.MAX_VALUE - stored) return false;
            }
            QueuedWork merge = null;
            for (var work : queued) {
                if (!work.pattern.equals(pattern.getDefinition()) || !work.recipe.equals(recipe.id()) || crafts > Long.MAX_VALUE - work.crafts) continue;
                var existing = MatterFabricationBatch.match(controller, candidates, work.inputs, work.crafts);
                if (existing == null || !existing.id().equals(work.recipe)) continue;
                long total = crafts + work.crafts;
                if (total > MatterFabricationBatch.inputCapacity(recipe.value())) continue;
                merge = work; break;
            }
            if (merge == null) queued.add(new QueuedWork(pattern.getDefinition(), recipe.id(), crafts, supplied));
            else { merge.crafts += crafts; merge(merge.inputs, supplied); }
            supplied.forEach(inputs::addTo);
            changed();
            return true;
        } catch (ArithmeticException error) { return false; }
    }

    /** Called by one controller, at most once per controller tick. */
    MatterFabricationBatch.Update process(MatterFabricationBlockEntity controller) {
        if (isUnavailable() || !assembly.isOperational() || assembly.getController() != controller) return null;
        reconcile();
        MatterFabricationBatch.Update waiting = null;
        if (!active.hasWork()) {
            for (var work : List.copyOf(queued)) {
                var pattern = PatternDetailsHelper.decodePattern(work.pattern.toStack(), controller.getLevel());
                if (pattern == null) continue;
                var recipe = MatterFabricationBatch.match(controller, pattern, work.inputs, work.crafts);
                if (recipe == null || !recipe.id().equals(work.recipe)) continue;
                long count = Math.min(work.crafts, active.capacity(controller, recipe, Map.of()));
                if (count <= 0) { waiting = new MatterFabricationBatch.Update(MatterFabricationBlockEntity.ProcessingState.WAITING_POWER, 0, 0, 0, false); continue; }
                var unitOutputs = MatterFabricationBatch.outputs(recipe.value());
                for (var output : unitOutputs.entrySet()) count = Math.min(count, (Long.MAX_VALUE - outputs.getLong(output.getKey())) / output.getValue());
                if (count <= 0) { waiting = new MatterFabricationBatch.Update(MatterFabricationBlockEntity.ProcessingState.OUTPUT_BLOCKED, 0, 0, 0, false); continue; }
                var supplied = MatterFabricationBatch.takeInputs(recipe.value(), work.inputs, work.crafts, count);
                if (supplied == null) continue;
                if (!active.accept(controller, pattern, supplied, count, scaled(unitOutputs, count))) continue;
                queued.remove(work);
                work.crafts -= count;
                supplied.forEach((item, amount) -> {
                    long left = work.inputs.get(item) - amount;
                    if (left == 0) work.inputs.remove(item); else work.inputs.put(item, left);
                });
                if (work.crafts > 0) queued.add(work);
                supplied.forEach((item, amount) -> subtract(inputs, item, amount));
                changed();
                break;
            }
        }
        if (!active.hasWork()) return waiting;
        var update = active.tick(controller, this::storeOutput);
        changed();
        flushToNetwork();
        return update;
    }

    private long storeOutput(AEKey key, long amount) {
        long inserted = Math.min(amount, Long.MAX_VALUE - outputs.getLong(key));
        if (inserted > 0) outputs.addTo(key, inserted);
        return inserted;
    }

    void serverTick() { reconcile(); flushToNetwork(); }

    private void reconcile() {
        if (!reconcilePatterns || isUnavailable()) return;
        reconcilePatterns = false;
        var definitions = assembly.getLogic().patternDefinitions();
        for (var iterator = queued.iterator(); iterator.hasNext();) {
            var work = iterator.next();
            if (!definitions.contains(work.pattern)) {
                work.inputs.forEach((key, amount) -> {
                    subtract(inputs, key, amount);
                    refunds.addTo(key, amount);
                });
                iterator.remove();
                changed();
            }
        }
    }

    /** Unstarted inputs only; active work retains ownership of already consumed materials. */
    public void refundQueuedInputs() {
        if (isUnavailable()) return;
        inputs.forEach(refunds::addTo);
        queued.clear(); inputs.clear(); changed(); flushToNetwork();
    }

    private void flushToNetwork() {
        var node = assembly.getMainNode();
        if (!node.isActive() || isUnavailable()) return;
        var storage = node.getGrid().getStorageService().getInventory();
        var source = new MachineSource(assembly);
        var budget = AEKeyTransferScheduler.defaultBudget();
        AEKeyTransferScheduler.Inserter insert = (key, amount) -> storage.insert(key, amount, Actionable.MODULATE, source);
        boolean changed;
        // Neither refunds nor finished products can permanently starve the other.
        if (refundFirst) {
            changed = refundTransfer.flush(refunds, budget, insert).changed();
            changed |= outputTransfer.flush(outputs, budget, insert).changed();
        } else {
            changed = outputTransfer.flush(outputs, budget, insert).changed();
            changed |= refundTransfer.flush(refunds, budget, insert).changed();
        }
        refundFirst = !refundFirst;
        if (changed) changed();
    }

    public List<GenericStack> contents(boolean output) {
        var view = new LinkedHashMap<AEKey, Long>();
        if (output) { outputs.forEach(view::put); merge(view, active.storedOutputs()); }
        else { inputs.forEach(view::put); merge(view, refunds); merge(view, active.storedInputs()); }
        return view.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
    }

    void save(CompoundTag parent) {
        if (unavailable != null) { parent.put("fabrication_pattern_buffer", unavailable.copy()); return; }
        var tag = new CompoundTag(); tag.putInt("version", VERSION);
        var jobs = new ListTag();
        queued.forEach(work -> {
            var job = new CompoundTag(); job.put("pattern", GenericStack.writeTag(new GenericStack(work.pattern, 1)));
            job.putString("recipe", work.recipe.toString());
            job.putLong("crafts", work.crafts); job.put("inputs", writeAmounts(work.inputs)); jobs.add(job);
        });
        tag.put("queued", jobs); tag.put("refunds", writeAmounts(refunds)); tag.put("outputs", writeAmounts(outputs));
        if (active.hasWork()) tag.put("active", active.save());
        parent.put("fabrication_pattern_buffer", tag);
    }

    void load(CompoundTag parent) {
        clear();
        if (!parent.contains("fabrication_pattern_buffer", Tag.TAG_COMPOUND)) return;
        var tag = parent.getCompound("fabrication_pattern_buffer");
        try {
            if (tag.getInt("version") != VERSION) throw new IllegalArgumentException("Unsupported buffer version");
            for (var value : tag.getList("queued", Tag.TAG_COMPOUND)) {
                var job = (CompoundTag) value; var definition = GenericStack.readTag(job.getCompound("pattern"));
                if (definition == null || !(definition.what() instanceof AEItemKey pattern)) throw new IllegalArgumentException("Missing buffered pattern");
                long crafts = job.getLong("crafts"); var supplied = readAmounts(job.getList("inputs", Tag.TAG_COMPOUND));
                var recipe = ResourceLocation.tryParse(job.getString("recipe"));
                if (crafts <= 0 || supplied.isEmpty() || recipe == null || recipe.getPath().isEmpty()) throw new IllegalArgumentException("Invalid buffered inputs");
                queued.add(new QueuedWork(pattern, recipe, crafts, supplied));
                supplied.forEach((item, amount) -> inputs.put(item, Math.addExact(inputs.getLong(item), amount)));
            }
            refunds.putAll(readAmounts(tag.getList("refunds", Tag.TAG_COMPOUND)));
            outputs.putAll(readAmounts(tag.getList("outputs", Tag.TAG_COMPOUND)));
            active.load(tag.getCompound("active"));
            contents(false); contents(true); // Validate totals across queued, refunded and active ownership.
        } catch (RuntimeException error) {
            clear(); unavailable = tag.copy();
            MolecularManipulator.LOGGER.warn("Preserving unavailable pattern buffer at {}: {}", assembly.getBlockPos(), error.getMessage());
        }
        reconcilePatterns = true;
    }

    void clear() { queued.clear(); inputs.clear(); refunds.clear(); outputs.clear(); active.clear(); unavailable = null; reconcilePatterns = true; }
    private void changed() { assembly.saveChanges(); }
    private static void subtract(Object2LongLinkedOpenHashMap<AEKey> map, AEKey key, long amount) {
        long left = map.getLong(key) - amount;
        if (left == 0) map.removeLong(key); else if (left > 0) map.put(key, left); else throw new IllegalStateException("Buffered input underflow");
    }
    static Map<AEKey, Long> scaled(Map<AEKey, Long> values, long count) {
        var result = new LinkedHashMap<AEKey, Long>(); values.forEach((key, amount) -> result.put(key, Math.multiplyExact(amount, count))); return result;
    }
    private static void merge(Map<AEKey, Long> target, Map<AEKey, Long> source) { source.forEach((key, amount) -> target.merge(key, amount, Math::addExact)); }
    private static ListTag writeAmounts(Map<AEKey, Long> values) {
        var list = new ListTag(); values.forEach((key, amount) -> list.add(GenericStack.writeTag(new GenericStack(key, amount)))); return list;
    }
    private static Map<AEKey, Long> readAmounts(ListTag values) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var value : values) {
            var stack = GenericStack.readTag((CompoundTag) value);
            if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid buffered resource");
            result.merge(stack.what(), stack.amount(), Math::addExact);
        }
        return result;
    }
    private static final class QueuedWork {
        private final AEItemKey pattern;
        private final ResourceLocation recipe;
        private final Map<AEKey, Long> inputs;
        private long crafts;
        private QueuedWork(AEItemKey pattern, ResourceLocation recipe, long crafts, Map<AEKey, Long> inputs) {
            this.pattern = pattern; this.recipe = recipe; this.crafts = crafts; this.inputs = new LinkedHashMap<>(inputs);
        }
    }
}

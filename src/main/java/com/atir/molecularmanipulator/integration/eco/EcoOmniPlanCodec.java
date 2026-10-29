package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionPlanNbtCodec;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/** ECO owns the execution wire format. This envelope preserves what its CPU codec omits. */
public final class EcoOmniPlanCodec {
    private EcoOmniPlanCodec() {}

    public static CompoundTag write(ECOExecutionPlan plan, HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("eco", ECOExecutionPlanNbtCodec.encode(plan, registries));
        var signature = new CompoundTag();
        signature.put("output", GenericStack.writeTag(registries,
                new GenericStack(plan.signature().finalWhat(), plan.signature().finalAmount())));
        signature.put("used", writeAmounts(big(plan.signature().usedItems()), registries));
        signature.put("emitted", writeAmounts(big(plan.signature().emittedItems()), registries));
        signature.put("missing", writeAmounts(big(plan.signature().missingItems()), registries));
        var counts = new ListTag();
        plan.signature().patternTimes().forEach((identity, count) -> {
            var task = plan.tasks().stream().filter(value -> value.identity().equals(identity)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("ECO signature has no matching task identity"));
            var entry = new CompoundTag();
            entry.putInt("task", task.id());
            entry.putLong("count", count);
            counts.add(entry);
        });
        signature.put("counts", counts);
        tag.put("signature", signature);
        var identities = new ListTag();
        for (var task : plan.tasks()) {
            var entry = new CompoundTag();
            entry.putString("kind", task.identity().kind().name());
            // Object identity cannot survive a world reload. Refuse before submission.
            if (task.identity().kind() == PlanIdentity.Kind.OBJECT) {
                throw new IllegalArgumentException("ECO object-only task identity cannot be persisted");
            }
            entry.put("definition", task.pattern().getDefinition().toTagGeneric(registries));
            if (task.identity().kind() == PlanIdentity.Kind.STRUCTURAL) {
                entry.put("shape", patternShape(task.pattern(), registries));
            }
            identities.add(entry);
        }
        tag.put("identities", identities);
        var schedule = new ListTag();
        for (var phase : plan.schedule().phases()) {
            var entry = new CompoundTag();
            entry.putInt("component", phase.componentId());
            entry.putString("type", phase.type().name());
            entry.put("patterns", writePatterns(phase.patternSet(), registries));
            entry.put("witness", writePatterns(phase.cycleWitness(), registries));
            schedule.add(entry);
        }
        tag.put("schedule", schedule);
        var dependencies = new ListTag();
        for (var dependency : plan.schedule().dependencies()) {
            var entry = new CompoundTag();
            entry.putInt("from", dependency.producerPhase());
            entry.putInt("to", dependency.consumerPhase());
            dependencies.add(entry);
        }
        tag.put("scheduleDependencies", dependencies);
        return tag;
    }

    public static ECOExecutionPlan read(CompoundTag tag, HolderLookup.Provider registries,
            Level level, Map<AEItemKey, IPatternDetails> available) {
        var signatureTag = tag.getCompound("signature");
        var output = GenericStack.readTag(registries, signatureTag.getCompound("output"));
        if (output == null || output.amount() <= 0) throw new IllegalArgumentException("Invalid ECO signature output");
        ECOExecutionPlan decoded;
        try (var scope = EcoOmniPlanDecodeScope.open(level, available)) {
            decoded = ECOExecutionPlanNbtCodec.decode(tag.getCompound("eco"), registries, null, output);
        }
        var identities = tag.getList("identities", Tag.TAG_COMPOUND);
        if (identities.size() != decoded.tasks().size()) throw new IllegalArgumentException("ECO identity vector changed");
        for (var task : decoded.tasks()) {
            var entry = identities.getCompound(task.id());
            if (!task.identity().kind().name().equals(entry.getString("kind"))
                    || !task.pattern().getDefinition().equals(AEKey.fromTagGeneric(registries, entry.getCompound("definition")))) {
                throw new IllegalArgumentException("ECO restored task identity changed");
            }
            if (task.identity().kind() == PlanIdentity.Kind.STRUCTURAL
                    && !entry.getCompound("shape").equals(patternShape(task.pattern(), registries))) {
                throw new IllegalArgumentException("ECO restored structural task identity changed");
            }
        }
        var counts = new LinkedHashMap<PlanIdentity.PatternIdentity, Long>();
        for (var value : tag.getCompound("signature").getList("counts", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var identity = decoded.task(entry.getInt("task")).identity();
            long count = entry.getLong("count");
            if (count <= 0 || counts.putIfAbsent(identity, count) != null) {
                throw new IllegalArgumentException("Invalid ECO signature task count");
            }
        }
        var signature = new PlanIdentity.Signature(output.what(), output.amount(), counts,
                longs(readAmounts(signatureTag.getList("used", Tag.TAG_COMPOUND), registries)),
                longs(readAmounts(signatureTag.getList("emitted", Tag.TAG_COMPOUND), registries)),
                longs(readAmounts(signatureTag.getList("missing", Tag.TAG_COMPOUND), registries)));
        Function<AEItemKey, IPatternDetails> resolve = definition -> {
            var bound = available.get(definition);
            if (bound != null) return bound;
            var pattern = PatternDetailsHelper.decodePattern(definition, level);
            if (pattern == null) throw new IllegalArgumentException("Cannot decode ECO schedule pattern");
            return pattern;
        };
        var phases = new ArrayList<ECOExecutionSchedule.ComponentExecutionPhase>();
        for (var value : tag.getList("schedule", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            phases.add(new ECOExecutionSchedule.ComponentExecutionPhase(entry.getInt("component"),
                    ECOExecutionSchedule.Type.valueOf(entry.getString("type")),
                    new LinkedHashSet<>(readPatterns(entry.getList("patterns", Tag.TAG_COMPOUND), registries, resolve)),
                    readPatterns(entry.getList("witness", Tag.TAG_COMPOUND), registries, resolve)));
        }
        var dependencies = new ArrayList<ECOExecutionSchedule.PhaseDependency>();
        for (var value : tag.getList("scheduleDependencies", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            dependencies.add(new ECOExecutionSchedule.PhaseDependency(entry.getInt("from"), entry.getInt("to")));
        }
        return new ECOExecutionPlan(signature, decoded.mode(), decoded.tasks(), decoded.phases(),
                new ECOExecutionSchedule(List.copyOf(phases), List.copyOf(dependencies)));
    }

    public static ListTag writeAmounts(Map<AEKey, BigInteger> amounts, HolderLookup.Provider registries) {
        var result = new ListTag();
        amounts.forEach((key, amount) -> {
            if (amount.signum() <= 0) throw new IllegalArgumentException("Invalid ECO amount");
            var entry = key.toTagGeneric(registries);
            entry.putString("omniAmount", amount.toString());
            result.add(entry);
        });
        return result;
    }

    public static Map<AEKey, BigInteger> readAmounts(ListTag tags, HolderLookup.Provider registries) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var value : tags) {
            var entry = (CompoundTag) value;
            var key = AEKey.fromTagGeneric(registries, entry);
            var amount = new BigInteger(entry.getString("omniAmount"));
            if (key == null || amount.signum() <= 0 || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid saved ECO amount");
            }
        }
        return result;
    }

    private static CompoundTag patternShape(IPatternDetails pattern, HolderLookup.Provider registries) {
        var shape = new CompoundTag();
        shape.putString("implementation", pattern.getClass().getName());
        var inputs = new ListTag();
        for (var input : pattern.getInputs()) {
            var slot = new CompoundTag();
            slot.putLong("multiplier", input.getMultiplier());
            var alternatives = new ListTag();
            for (var stack : input.getPossibleInputs()) {
                var entry = GenericStack.writeTag(registries, stack);
                var remainder = input.getRemainingKey(stack.what());
                if (remainder != null) entry.put("remainder", remainder.toTagGeneric(registries));
                alternatives.add(entry);
            }
            slot.put("alternatives", alternatives);
            inputs.add(slot);
        }
        shape.put("inputs", inputs);
        var outputs = new ListTag();
        for (var stack : pattern.getOutputs()) outputs.add(GenericStack.writeTag(registries, stack));
        shape.put("outputs", outputs);
        return shape;
    }

    private static Map<AEKey, BigInteger> big(Map<AEKey, Long> values) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        values.forEach((key, count) -> result.put(key, BigInteger.valueOf(count)));
        return result;
    }

    private static Map<AEKey, Long> longs(Map<AEKey, BigInteger> values) {
        var result = new LinkedHashMap<AEKey, Long>();
        values.forEach((key, count) -> result.put(key, count.longValueExact()));
        return result;
    }

    private static ListTag writePatterns(Iterable<IPatternDetails> patterns, HolderLookup.Provider registries) {
        var result = new ListTag();
        for (var pattern : patterns) result.add(pattern.getDefinition().toTagGeneric(registries));
        return result;
    }

    private static List<IPatternDetails> readPatterns(ListTag entries, HolderLookup.Provider registries,
            Function<AEItemKey, IPatternDetails> resolve) {
        var result = new ArrayList<IPatternDetails>();
        for (var value : entries) {
            if (!(AEKey.fromTagGeneric(registries, (CompoundTag) value) instanceof AEItemKey key)) {
                throw new IllegalArgumentException("Invalid ECO schedule definition");
            }
            result.add(resolve.apply(key));
        }
        return List.copyOf(result);
    }
}

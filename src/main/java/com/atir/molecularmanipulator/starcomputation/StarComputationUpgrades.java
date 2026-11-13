package com.atir.molecularmanipulator.starcomputation;

import appeng.api.networking.IGrid;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock;
import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.StarOrreryAutoAssembly;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.blockentity.StarSeaCalculationMainframeBlockEntity;
import com.atir.molecularmanipulator.integration.FtbQuestsProgress;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.Entry;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.Row;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe.Condition;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Live server-side upgrade requirements. No permission unions, virtual networks or saved recipe fallbacks. */
public final class StarComputationUpgrades {
    private static final String PREFIX = "gui.molecularmanipulator.star_computation.upgrade.";

    private StarComputationUpgrades() {}

    public record Rule(ResourceLocation id, StarComputationUpgradeRecipe recipe) {
        public Rule {
            Objects.requireNonNull(id);
            Objects.requireNonNull(recipe);
        }
    }

    public record Evaluation(boolean passed, List<Row> rows) {
        public Evaluation { rows = List.copyOf(rows); }
    }

    /** Query the current RecipeManager every time: same-manager reloads, removals and conflicts take effect immediately. */
    @Nullable
    public static Rule rule(Level level, int targetLevel) {
        return rule(level.getRecipeManager(), targetLevel);
    }

    @Nullable
    static Rule rule(RecipeManager manager, int targetLevel) {
        if (targetLevel < 1 || targetLevel > 9) return null;
        var candidates = rules(manager).getOrDefault(targetLevel, List.of());
        return candidates.size() == 1 && candidates.get(0).recipe().error() == null ? candidates.get(0) : null;
    }

    private static Map<Integer, List<Rule>> rules(@Nullable RecipeManager manager) {
        var result = new LinkedHashMap<Integer, List<Rule>>();
        if (manager != null) for (var candidate : manager.getRecipes()) {
            if (candidate instanceof StarComputationUpgradeRecipe recipe) {
                result.computeIfAbsent(recipe.level(), ignored -> new ArrayList<>()).add(new Rule(recipe.id(), recipe));
            }
        }
        return result;
    }

    public static Evaluation evaluate(StarComputationEngineCoreBlockEntity core, Rule rule) {
        return evaluate(core, rule, null);
    }

    public static Evaluation evaluate(StarComputationEngineCoreBlockEntity core, Rule rule,
            @Nullable ServerPlayer player) {
        String error = rule.recipe().error();
        if (error != null) return invalid(error);
        if (core.getLevel() == null || core.getLevel().isClientSide()) {
            return new Evaluation(false, List.of(new Row(0, false, text("row.server_only"), List.of(text("server_only")))));
        }
        return evaluate(rule.recipe().conditions(), new LiveResolver(core, rule.recipe().level(), rule.recipe().autoBuild(), player));
    }

    public static StarComputationUpgradeCatalog catalog(StarComputationEngineCoreBlockEntity core) {
        return catalog(core, null);
    }

    public static StarComputationUpgradeCatalog catalog(StarComputationEngineCoreBlockEntity core,
            @Nullable ServerPlayer player) {
        var level = core.getLevel();
        if (level == null || level.isClientSide()) return StarComputationUpgradeCatalog.EMPTY;
        var resolvers = new LinkedHashMap<Integer, LiveResolver>();
        var definitions = catalog(level.getRecipeManager(), rule -> {
            var resolver = new LiveResolver(core, rule.recipe().level(), rule.recipe().autoBuild(), player);
            resolvers.put(rule.recipe().level(), resolver);
            return evaluate(rule.recipe().conditions(), resolver);
        });
        var entries = new ArrayList<Entry>(definitions.entries());
        for (int target = 1; target <= 9; target++) {
            var entry = entries.get(target - 1);
            if (target <= 3) {
                var resolver = new LiveResolver(core, target, true, player);
                resolvers.put(target, resolver);
                var plan = resolver.assemblyPlan();
                var targets = new ArrayList<Target>();
                for (var supply : plan.supplies()) if (supply.required() > 0) {
                    targets.add(new Target(anchorTierId(supply.tier()), supply.required(), ""));
                }
                var conditions = new Condition("anchors", List.of(), targets, "all", 1, true, true, "");
                var evaluation = evaluate(conditions, resolver);
                entry = new Entry(target, true, 0, 1, evaluation.passed(), evaluation.rows(), true);
            }
            if (entry.autoBuild()) {
                var plan = resolvers.get(target).assemblyPlan();
                boolean materials = plan.supplies().stream().allMatch(supply ->
                        supply.available() >= supply.required() - supply.placed());
                var rows = new ArrayList<>(entry.rows());
                // Default anchor rows already explain materials. Custom rules can opt in without an anchors leaf.
                if (!materials && !containsAnchorsRow(rows)) rows.add(new Row(0, false,
                        text("row.anchors"), List.of(text("status.anchor_materials"))));
                if (plan.blocked() != null) rows.add(new Row(0, false, text("anchor_space_title"),
                        List.of(text("status." + plan.blocked()))));
                entry = new Entry(target, entry.configured(), entry.energy(), entry.aePerTick(),
                        entry.conditionsMet() && materials && plan.blocked() == null, rows, true);
            }
            entries.set(target - 1, entry);
        }
        return new StarComputationUpgradeCatalog(entries);
    }

    private static boolean containsAnchorsRow(List<Row> rows) {
        return rows.stream().anyMatch(row -> row.text().equals(text("row.anchors")));
    }

    private static String anchorTierId(int tier) {
        return switch (tier) { case 0 -> "reference"; case 1 -> "quantum"; default -> "terminal"; };
    }

    // Shared resolver snapshots last only for one catalog build; neither completion counts nor network state are cached.
    static StarComputationUpgradeCatalog catalog(@Nullable RecipeManager manager, Function<Rule, Evaluation> evaluator) {
        var definitions = rules(manager);
        var entries = new ArrayList<Entry>(9);
        for (int level = 1; level <= 9; level++) {
            if (level <= 3) {
                entries.add(automatic(level));
                continue;
            }
            var candidates = definitions.getOrDefault(level, List.of());
            if (candidates.size() != 1) {
                entries.add(new Entry(level, false, 0, 0, false, List.of(new Row(0, false,
                        text(candidates.isEmpty() ? "row.unconfigured" : "row.conflict", level),
                        List.of(text(candidates.isEmpty() ? "unconfigured" : "conflict", level))))));
                continue;
            }
            var rule = candidates.get(0);
            String error = rule.recipe().error();
            if (error != null) {
                entries.add(new Entry(level, false, 0, 0, false, invalid(error).rows()));
                continue;
            }
            var evaluation = evaluator.apply(rule);
            entries.add(new Entry(level, true, rule.recipe().energy(), rule.recipe().aePerTick(),
                    evaluation.passed(), evaluation.rows(), rule.recipe().autoBuild()));
        }
        return new StarComputationUpgradeCatalog(entries);
    }

    static Entry automatic(int level) {
        return new Entry(level, true, 0, 1, true, List.of(new Row(0, true, text("row.automatic"), List.of(text("automatic")))));
    }

    static Component text(String key, Object... arguments) {
        // Vanilla's component codec collapses literal arguments to strings. Canonicalize before GuiSync compares snapshots.
        var normalized = arguments.clone();
        for (int index = 0; index < normalized.length; index++) {
            if (normalized[index] instanceof Component component && component.getSiblings().isEmpty()
                    && component.getContents() instanceof net.minecraft.network.chat.contents.LiteralContents literal) {
                normalized[index] = literal.text();
            }
        }
        return Component.translatable(PREFIX + key, normalized);
    }

    private static Evaluation invalid(String reason) {
        return new Evaluation(false, List.of(new Row(0, false, text("row.invalid"), List.of(text("detail.invalid")))));
    }

    /** Package-private pure evaluation boundary: tests do not need a world, a running ME grid or optional FTB mods. */
    interface Resolver { LeafResult resolve(Condition condition); }

    record TargetProgress(boolean available, int count, Component name, Component reason, List<Component> details) {
        TargetProgress { details = List.copyOf(details); }
        TargetProgress(boolean available, int count, Component name, Component reason) {
            this(available, count, name, reason, List.of());
        }
        boolean met(Target target) { return available && count >= target.count(); }
        TargetProgress withCount(int value) { return new TargetProgress(available, value, name, reason, details); }
    }

    record LeafResult(boolean passed, List<TargetProgress> targets, @Nullable Component reason) {
        LeafResult { targets = List.copyOf(targets); }
    }

    static Evaluation evaluate(Condition condition, Resolver resolver) {
        String error = condition.error();
        if (error != null) return invalid(error);
        var rows = new ArrayList<Row>();
        boolean passed = evaluate(condition, resolver, 0, rows);
        return new Evaluation(passed, rows);
    }

    private static boolean evaluate(Condition condition, Resolver resolver, int depth, List<Row> rows) {
        int summary = rows.size();
        rows.add(new Row(depth, false, Component.empty()));
        boolean passed;
        Component title;
        var details = new ArrayList<Component>();
        if (condition.group()) {
            var results = new ArrayList<Boolean>();
            // Deliberately do not short-circuit: UI must include every target and every unavailable/failing branch.
            for (var child : condition.children()) results.add(evaluate(child, resolver, depth + 1, rows));
            passed = matches(condition.type(), condition.required(), results);
            title = text("row.group." + condition.type(), condition.required());
            details.add(text("group." + condition.type(), results.stream().filter(Boolean::booleanValue).count(),
                    results.size(), condition.required()));
        } else {
            var result = resolver.resolve(condition);
            if (result.targets().size() != condition.targets().size()) {
                rows.set(summary, new Row(depth, false, text("row.invalid"), List.of(text("detail.invalid"))));
                return false;
            }
            passed = result.passed();
            Component mode = switch (condition.type()) {
                case "research" -> text(condition.sameMachine() ? "research_same" : "research_cross");
                case "machines" -> text(condition.formed() ? "machines_formed" : "machines_active");
                default -> Component.empty();
            };
            int met = 0;
            for (int index = 0; index < condition.targets().size(); index++) {
                var target = condition.targets().get(index);
                var progress = result.targets().get(index);
                boolean targetMet = progress.met(target);
                if (targetMet) met++;
                Component name = named(target.title(), progress.name());
                var targetDetails = new ArrayList<Component>();
                targetDetails.add(text("detail." + condition.type(), progress.name(), progress.count(), target.count()));
                if (!progress.available()) targetDetails.add(progress.reason());
                if (result.reason() != null && !targetDetails.contains(result.reason())) targetDetails.add(result.reason());
                if (!mode.equals(Component.empty())) targetDetails.add(mode);
                targetDetails.addAll(progress.details());
                rows.add(new Row(depth + 1, targetMet, name, targetDetails));
            }
            title = text("row." + condition.type());
            details.add(text("condition." + condition.type(), text("match." + condition.match(), condition.required()),
                    met, condition.targets().size(), mode));
            if (result.reason() != null) details.add(result.reason());
        }
        rows.set(summary, new Row(depth, passed, named(condition.title(), title), details));
        return passed;
    }

    private static Component named(String title, Component actual) {
        // A pack-authored title replaces the automatic name; implementation IDs never form part of player-facing text.
        return title.isEmpty() ? actual : Component.translatable(title);
    }

    static boolean matches(String match, int required, List<Boolean> results) {
        if (results.isEmpty() || required < 1 || required > results.size()) return false;
        long met = results.stream().filter(Boolean::booleanValue).count();
        return switch (match) {
            case "all" -> met == results.size();
            case "any" -> met > 0;
            case "at_least" -> met >= required;
            default -> false;
        };
    }

    static LeafResult leaf(Condition condition, List<TargetProgress> targets) {
        var results = new ArrayList<Boolean>(targets.size());
        for (int index = 0; index < targets.size(); index++) results.add(targets.get(index).met(condition.targets().get(index)));
        return new LeafResult(matches(condition.match(), condition.required(), results), targets, null);
    }

    /** Each inner list is one well's counts, in target order. Cross-well progress is a maximum, NEVER a sum. */
    static LeafResult research(Condition condition, List<TargetProgress> definitions, List<List<Integer>> wells) {
        if (wells.isEmpty()) {
            return new LeafResult(false, definitions, text("no_research_machine"));
        }
        List<TargetProgress> selected = definitions;
        int bestScore = -1;
        boolean passed = false;
        if (condition.sameMachine()) {
            for (var counts : wells) {
                var progress = counts(definitions, counts);
                var candidate = leaf(condition, progress);
                int score = 0;
                for (int index = 0; index < progress.size(); index++) {
                    if (progress.get(index).met(condition.targets().get(index))) score++;
                }
                if (candidate.passed() || score > bestScore) {
                    selected = progress;
                    bestScore = score;
                }
                if (candidate.passed()) { passed = true; break; }
            }
        } else {
            var maxima = new ArrayList<>(Collections.nCopies(definitions.size(), 0));
            for (var counts : wells) for (int index = 0; index < definitions.size(); index++) {
                maxima.set(index, Math.max(maxima.get(index), counts.get(index)));
            }
            selected = counts(definitions, maxima);
            passed = leaf(condition, selected).passed();
        }
        return new LeafResult(passed, selected,
                !passed && condition.sameMachine() ? text("same_machine_failed") : null);
    }

    private static List<TargetProgress> counts(List<TargetProgress> definitions, List<Integer> counts) {
        var result = new ArrayList<TargetProgress>(definitions.size());
        for (int index = 0; index < definitions.size(); index++) result.add(definitions.get(index).withCount(counts.get(index)));
        return result;
    }

    /** Identity is the actual controller BE, not a node, proxy part, coordinate-only key or logical/shared ME storage. */
    record MachineSnapshot(Object identity, Object grid, boolean loaded, boolean active, boolean primary,
                           boolean formed, ResourceLocation block, Set<ResourceLocation> tags) {
        MachineSnapshot { tags = Set.copyOf(tags); }
    }

    static int machineCount(Object grid, List<MachineSnapshot> machines, String target, boolean requireFormed) {
        boolean tag = target.startsWith("#");
        var id = ResourceLocation.tryParse(tag ? target.substring(1) : target);
        if (grid == null || id == null) return 0;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        int count = 0;
        for (var machine : machines) {
            if (machine.grid() != grid || !machine.loaded() || !machine.active() || !machine.primary()
                    || requireFormed && !machine.formed()) continue;
            if ((tag ? machine.tags().contains(id) : machine.block().equals(id)) && seen.add(machine.identity())) count++;
        }
        return count;
    }

    static TargetProgress node(Target target, int unlockMask) {
        for (var node : StarComputationRecipes.NODES) if (node.id().equals(target.id())) {
            return new TargetProgress(true, node.unlocked(unlockMask) ? 1 : 0,
                    text("node." + node.id()), text("incomplete"));
        }
        return unavailable(text("row.unknown_target"), "detail.unknown_target");
    }

    private static final class LiveResolver implements Resolver {
        private final StarComputationEngineCoreBlockEntity core;
        private final Level level;
        @Nullable private final IGrid grid;
        private List<MachineSnapshot> machines;
        private List<MachineSnapshot> formedMachines;
        private Map<ResourceLocation, String> researchTitles;
        private final int targetLevel;
        private final boolean autoBuild;
        @Nullable private final ServerPlayer player;
        private StarOrreryAutoAssembly.Plan assemblyPlan;

        private LiveResolver(StarComputationEngineCoreBlockEntity core, int targetLevel, boolean autoBuild,
                @Nullable ServerPlayer player) {
            this.core = core;
            this.level = core.getLevel();
            this.grid = core.getMainNode().getGrid();
            this.targetLevel = targetLevel;
            this.autoBuild = autoBuild;
            this.player = player;
        }

        private StarOrreryAutoAssembly.Plan assemblyPlan() {
            if (assemblyPlan == null) assemblyPlan = StarOrreryAutoAssembly.plan(core, targetLevel, player);
            return assemblyPlan;
        }

        @Override public LeafResult resolve(Condition condition) {
            if (condition.type().equals("research")) return research(condition);
            return leaf(condition, condition.targets().stream().map(target -> switch (condition.type()) {
                case "ftb_quests" -> quest(target);
                case "anchors" -> anchor(target);
                case "machines" -> machine(target, condition.formed());
                // The owning core's actual mask, never StarComputationNetwork.unlockMask(grid).
                case "nodes" -> node(target, core.getUnlockMask());
                default -> unavailable(text("row.unknown_target"), "detail.unknown_target");
            }).toList());
        }

        private TargetProgress anchor(Target target) {
            int tier = switch (target.id()) { case "reference" -> 0; case "quantum" -> 1; default -> 2; };
            var block = switch (tier) {
                case 0 -> ModContent.REFERENCE_STAR_POSITION_ANCHOR.get();
                case 1 -> ModContent.QUANTUM_STAR_POSITION_ANCHOR.get();
                default -> ModContent.TERMINAL_STAR_POSITION_ANCHOR.get();
            };
            if (!autoBuild || player == null) return new TargetProgress(true, core.getAnchorCount(tier), block.getName(), text("incomplete"));
            var supply = assemblyPlan().supplies().get(tier);
            // Spare inventory cannot satisfy a physical condition the target build will never create.
            // Pre-existing higher rings still count, so custom rules may explicitly require them.
            int count = Math.max(core.getAnchorCount(tier), (int) Math.min(supply.required(),
                    supply.placed() + Math.min(Integer.MAX_VALUE, supply.available())));
            return new TargetProgress(true, count, block.getName(), text("status.anchor_materials"),
                    List.of(text("anchor_materials_hint"), text("anchor_supply", supply.placed(), supply.available(), supply.required()),
                            text(count >= target.count() ? "anchor_ready" : "anchor_missing", Math.max(0, target.count() - count))));
        }

        private TargetProgress quest(Target target) {
            if (level.getServer() == null) return unavailable(text("row.unavailable"), "server_only");
            if (core.getBoundTeam() == null) return unavailable(text("row.unavailable"), "no_team");
            var result = FtbQuestsProgress.check(level.getServer(), core.getBoundTeam(), target.id());
            Component name = result.title().isEmpty() ? text("row.unavailable") : Component.literal(result.title());
            if (!result.available()) return unavailable(name, "detail.ftb_unavailable");
            return new TargetProgress(true, result.completed() ? 1 : 0, name, text("incomplete"));
        }

        private TargetProgress machine(Target target, boolean formed) {
            var blocks = level.registryAccess().registryOrThrow(Registries.BLOCK);
            boolean tag = target.id().startsWith("#");
            var id = new ResourceLocation(tag ? target.id().substring(1) : target.id());
            Component name;
            var nameDetails = new ArrayList<Component>();
            if (tag) {
                var members = blocks.getTag(TagKey.create(Registries.BLOCK, id));
                if (members.isEmpty() || members.get().size() == 0) return unavailable(text("row.unknown_target"), "detail.unknown_target");
                // Tags do not have an intrinsic display name. Send their real member block names, not guessed item IDs.
                name = text("row.machine_options");
                nameDetails.add(text("block_tag_members", ComponentUtils.formatList(members.get().stream().toList(),
                        holder -> holder.value().getName())));
            } else {
                if (!blocks.containsKey(id)) return unavailable(text("row.unknown_target"), "detail.unknown_target");
                name = blocks.get(id).getName();
            }
            if (grid == null) return new TargetProgress(false, 0, name, text("no_grid"), nameDetails);
            return new TargetProgress(true, machineCount(grid, machines(formed), target.id(), formed), name,
                    text("incomplete"), nameDetails);
        }

        private LeafResult research(Condition condition) {
            if (researchTitles == null) {
                researchTitles = new LinkedHashMap<>();
                for (var holder : MatterResearchApi.definitions(level)) researchTitles.put(holder.id(), holder.value().title());
            }
            var definitions = new ArrayList<TargetProgress>();
            for (var target : condition.targets()) {
                var title = researchTitles.get(new ResourceLocation(target.id()));
                definitions.add(title == null ? unavailable(text("row.unavailable"), "research_unavailable")
                        : new TargetProgress(true, 0, Component.translatable(title), text("incomplete")));
            }
            if (grid == null) return new LeafResult(false, definitions, text("no_grid"));
            var counts = new ArrayList<List<Integer>>();
            for (var machine : machines(true)) {
                if (machine.identity() instanceof MatterFabricationBlockEntity well && machine.formed()
                        && !well.isBuilding() && !well.isDismantling()) {
                    counts.add(condition.targets().stream()
                            .map(target -> well.getResearch().completionCount(new ResourceLocation(target.id()))).toList());
                }
            }
            return StarComputationUpgrades.research(condition, definitions, counts);
        }

        private List<MachineSnapshot> machines(boolean checkFormation) {
            if (machines == null) machines = activeMachines();
            if (!checkFormation) return machines; // formed=false does not even call structure APIs.
            if (formedMachines == null) formedMachines = machines.stream().map(machine -> new MachineSnapshot(
                    machine.identity(), machine.grid(), machine.loaded(), machine.active(), machine.primary(),
                    formed((BlockEntity) machine.identity()), machine.block(), machine.tags())).toList();
            return formedMachines;
        }

        private List<MachineSnapshot> activeMachines() {
            var result = new ArrayList<MachineSnapshot>();
            if (grid == null) return result;
            var seen = Collections.newSetFromMap(new IdentityHashMap<BlockEntity, Boolean>());
            for (var node : grid.getNodes()) {
                // Proxy parts are not owners of a machine BE. Several exposed sockets can refer to the very same owner.
                if (!node.isActive() || node.getGrid() != grid || !(node.getOwner() instanceof BlockEntity be)) continue;
                if (be instanceof IGridConnectedBlockEntity host && (host.getMainNode().getNode() != node
                        || !host.getMainNode().isActive() || host.getMainNode().getGrid() != grid)) continue;
                var world = be.getLevel();
                if (world == null || node.getLevel() != world || be.isRemoved() || !world.hasChunkAt(be.getBlockPos())
                        || world.getBlockEntity(be.getBlockPos()) != be || !seen.add(be)) continue;
                var state = be.getBlockState();
                result.add(new MachineSnapshot(be, grid, true, true, true, true,
                        world.registryAccess().registryOrThrow(Registries.BLOCK).getKey(state.getBlock()),
                        state.getTags().map(TagKey::location).collect(Collectors.toSet())));
            }
            return result;
        }
    }

    private static TargetProgress unavailable(Component name, String reason, Object... arguments) {
        return new TargetProgress(false, 0, name, text(reason, arguments));
    }

    /**
     * Native multiblocks use their real formation APIs, not a guessed blockstate property. Third-party controllers
     * have no universal formed contract: for them formed=true means a loaded, active primary BE, just like false.
     * Targets should therefore name controller blocks/tags, not a third-party structure's auxiliary service ports.
     */
    private static boolean formed(BlockEntity be) {
        if (be instanceof StarComputationEngineCoreBlockEntity engine) return engine.isFormed();
        if (be instanceof MatterFabricationBlockEntity well) return well.isStructureFormed();
        if (be instanceof MolecularCenterBlockEntity center) return center.isFormed();
        if (be instanceof OmniComputationCoreBlockEntity computation) return computation.isStructureFormed();
        if (be instanceof AssemblerMatrixMolecularCoreBlockEntity assembler) return assembler.isFormed();
        if (be instanceof SingularityBlockEntity singularity) return singularity.formed();
        if (be instanceof StarSeaCalculationMainframeBlockEntity mainframe) {
            return StarSeaCalculationMainframeBlock.structureComplete(mainframe.getLevel(), mainframe.getBlockPos(),
                    mainframe.getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        }
        return true;
    }
}

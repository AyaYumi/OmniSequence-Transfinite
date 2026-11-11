package com.atir.molecularmanipulator.starcomputation;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Player purchases, never administrative grants. No position/team argument or remote terminal targeting. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class StarResearchCommands {
    private static final double REACH_SQUARED = 8 * 8;
    private static final SimpleCommandExceptionType NO_CORE = new SimpleCommandExceptionType(Component.literal(
            "请看向或靠近（核心中心 8 格内）已绑定自己团队的星算核心；也可看向已成型核心的壳体/观测口。"));
    private static final SimpleCommandExceptionType NOT_OWNED = new SimpleCommandExceptionType(Component.literal(
            "该星算核心未绑定你的当前团队，不能查看或购买本队研究；命令不会自动改绑。"));
    private static final DynamicCommandExceptionType INVALID_NODE = new DynamicCommandExceptionType(
            reason -> Component.literal("无法购买星算研究：" + reason));

    private StarResearchCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("starresearch")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("list").executes(StarResearchCommands::list))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        StarResearchNodes.nodes(context.getSource().getPlayerOrException().serverLevel())
                                                .keySet().stream().sorted(), builder))
                                .executes(StarResearchCommands::unlock))));
    }

    private static StarComputationEngineCoreBlockEntity core(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = player.serverLevel();
        UUID team = FtbTeamOwnership.forPlayer(player);
        if (team == null) throw NOT_OWNED.create();
        var hit = player.pick(8, 1, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && level.hasChunkAt(block.getBlockPos())) {
            var part = block.getBlockPos();
            if (level.getBlockEntity(part) instanceof StarComputationEngineCoreBlockEntity target) {
                return validateTarget(player, team, target);
            }
            var state = level.getBlockState(part);
            if (state.getBlock() instanceof StarComputationEngineCasingBlock
                    || state.getBlock() instanceof StarComputationObservationPortBlock) {
                var target = StarComputationEngineCoreBlockEntity.findCore(level, part);
                if (target != null) return validateTarget(player, team, target);
            }
        }
        // Only inspect block entities of already-loaded chunks, never load a core/chunk through a command.
        StarComputationEngineCoreBlockEntity nearest = null;
        double distance = REACH_SQUARED;
        var origin = player.blockPosition();
        for (int x = (origin.getX() - 8) >> 4; x <= (origin.getX() + 8) >> 4; x++) {
            for (int z = (origin.getZ() - 8) >> 4; z <= (origin.getZ() + 8) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof StarComputationEngineCoreBlockEntity candidate)
                            || candidate.isRemoved() || !team.equals(candidate.getBoundTeam())
                            || !level.mayInteract(player, candidate.getBlockPos())) continue;
                    double candidateDistance = player.distanceToSqr(candidate.getBlockPos().getCenter());
                    if (candidateDistance <= distance) {
                        nearest = candidate;
                        distance = candidateDistance;
                    }
                }
            }
        }
        if (nearest == null) throw NO_CORE.create();
        return validateTarget(player, team, nearest);
    }

    private static StarComputationEngineCoreBlockEntity validateTarget(ServerPlayer player, UUID team,
            StarComputationEngineCoreBlockEntity core) throws CommandSyntaxException {
        if (!team.equals(core.getBoundTeam())) throw NOT_OWNED.create();
        if (core.isRemoved() || player.distanceToSqr(core.getBlockPos().getCenter()) > REACH_SQUARED
                || !player.serverLevel().mayInteract(player, core.getBlockPos())) throw NO_CORE.create();
        core.refreshStructureNow();
        return core;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var core = core(source);
        var catalog = StarResearchNodes.catalog(core.getLevel());
        var unlocked = core.getUnlockedResearchIds();
        int actualLevel = core.getBenefits().level();
        boolean formed = core.isFormed();
        boolean online = core.getMainNode().isActive();
        long information = core.getInfoBalance();
        source.sendSuccess(() -> Component.literal("星算研究 @ " + core.getBlockPos().toShortString()
                + " | 本队永久进度 " + unlocked.size() + " | 实际等级 L" + actualLevel
                + " | 结构 " + (formed ? "已成型" : "未成型")
                + " | ME " + (online ? "在线" : "离线")
                + " | 星光情报 " + information
                + "\n自定义节点通过 /starresearch unlock <id> 购买；现有菜单仍是固定五格。"), false);
        for (var entry : catalog.nodes().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            var id = entry.getKey();
            var recipe = entry.getValue();
            String error = catalog.error(id);
            String state;
            if (unlocked.contains(id)) state = "已解锁（不会重复扣费）" + (error == null ? "" : "；配置无效：" + error);
            else if (error != null) state = "配置无效：" + error;
            else if (actualLevel < recipe.requiredLevel()) state = "等级不足";
            else if (!unlocked.containsAll(recipe.prerequisites())) state = "缺少本队前置：" + missing(recipe, unlocked);
            else if (!formed || !online) state = "核心未成型或 ME 离线";
            else if (information < recipe.informationCost()) state = "情报不足";
            else state = "可购买";
            var line = Component.literal(id.toString()).withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/starresearch unlock " + id)))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                    .append(recipe.title() == null || recipe.title().isBlank()
                            ? Component.literal(id.toString()) : Component.translatable(recipe.title()))
                    .append(Component.literal(" | L" + recipe.requiredLevel() + " | 情报 " + recipe.informationCost()
                            + " | 前置 " + recipe.prerequisites() + " | " + state).withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        }
        for (var id : unlocked.stream().filter(id -> !catalog.nodes().containsKey(id)).sorted().toList()) {
            source.sendSuccess(() -> Component.literal(id + " | 已永久解锁，当前数据包未定义；记录仍保留。")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return catalog.nodes().size();
    }

    private static int unlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();
        var core = core(source);
        var id = ResourceLocationArgument.getId(context, "id");
        var catalog = StarResearchNodes.catalog(core.getLevel());
        String error = catalog.error(id);
        if (error != null) throw INVALID_NODE.create(error);
        var unlocked = core.getUnlockedResearchIds();
        if (unlocked.contains(id)) {
            source.sendSuccess(() -> Component.literal(id + " 已被本队永久解锁，本次未扣费。"), false);
            return 0;
        }
        var recipe = catalog.nodes().get(id);
        if (core.getBenefits().level() < recipe.requiredLevel()) throw INVALID_NODE.create("需要实际等级 L" + recipe.requiredLevel());
        if (!unlocked.containsAll(recipe.prerequisites())) throw INVALID_NODE.create("缺少本队永久前置：" + missing(recipe, unlocked));
        if (!core.tryUnlockResearch(player, id)) throw INVALID_NODE.create(
                "服务器拒绝购买：请确认你有交互权限、核心已成型、ME 在线且有 " + recipe.informationCost() + " 星光情报。");
        source.sendSuccess(() -> Component.literal("本队已永久解锁 " + id + "，消耗 " + recipe.informationCost() + " 星光情报。"), false);
        return 1;
    }

    private static String missing(StarResearchNodeRecipe recipe, Set<ResourceLocation> unlocked) {
        return recipe.prerequisites().stream().filter(id -> !unlocked.contains(id)).distinct()
                .sorted(Comparator.naturalOrder()).map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }
}

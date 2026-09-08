package com.atir.molecularmanipulator.research;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Operator commands act on one controller, independently of player-owned research or grid state. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class MatterResearchCommands {
    private static final SimpleCommandExceptionType NO_CONTROLLER = new SimpleCommandExceptionType(
            text("no_controller"));
    private static final DynamicCommandExceptionType UNKNOWN_RESEARCH = new DynamicCommandExceptionType(
            id -> text("unknown_research", id));

    private MatterResearchCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("matter_research")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("unlock_all")
                        .executes(context -> unlockAll(context, false))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> unlockAll(context, true))))
                .then(Commands.literal("complete")
                        .then(Commands.argument("research", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        MatterResearchApi.definitions(context.getSource().getLevel()).stream().map(holder -> holder.id()), builder))
                                .then(Commands.argument("completed", BoolArgumentType.bool())
                                        .executes(context -> setCompleted(context, false))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> setCompleted(context, true))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("research", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        MatterResearchApi.definitions(context.getSource().getLevel()).stream().map(holder -> holder.id()), builder))
                                .then(Commands.argument("count", LongArgumentType.longArg(0))
                                        .executes(context -> setCount(context, false))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> setCount(context, true)))))));
    }

    private static MatterFabricationBlockEntity controller(CommandContext<CommandSourceStack> context, boolean positioned)
            throws CommandSyntaxException {
        var source = context.getSource();
        net.minecraft.core.BlockPos pos;
        if (positioned) pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        else {
            var hit = source.getPlayerOrException().pick(16, 1, false);
            if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) throw NO_CONTROLLER.create();
            pos = block.getBlockPos();
        }
        if (source.getLevel().getBlockEntity(pos) instanceof MatterFabricationBlockEntity machine) return machine;
        throw NO_CONTROLLER.create();
    }

    private static String researchId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var id = ResourceLocationArgument.getId(context, "research");
        if (MatterResearchApi.definitions(context.getSource().getLevel()).stream().noneMatch(holder -> holder.id().equals(id)))
            throw UNKNOWN_RESEARCH.create(id.toString());
        return id.toString();
    }

    private static int unlockAll(CommandContext<CommandSourceStack> context, boolean positioned) throws CommandSyntaxException {
        var machine = controller(context, positioned);
        int count = MatterResearchApi.unlockAll(machine);
        context.getSource().sendSuccess(() -> text("unlocked_all", position(machine), count), false);
        return count;
    }

    private static int setCompleted(CommandContext<CommandSourceStack> context, boolean positioned) throws CommandSyntaxException {
        return set(context, positioned, BoolArgumentType.getBool(context, "completed") ? Long.MAX_VALUE : 0);
    }

    private static int setCount(CommandContext<CommandSourceStack> context, boolean positioned) throws CommandSyntaxException {
        return set(context, positioned, LongArgumentType.getLong(context, "count"));
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean positioned, long requested) throws CommandSyntaxException {
        var machine = controller(context, positioned);
        String id = researchId(context);
        int count = MatterResearchApi.setCompletionCount(machine, id, requested);
        int max = MatterResearchApi.definitions(machine.getLevel()).stream().filter(holder -> holder.id().toString().equals(id))
                .findFirst().orElseThrow().value().depths().size();
        context.getSource().sendSuccess(() -> text("set", position(machine), id, count, max), false);
        return 1;
    }

    private static String position(MatterFabricationBlockEntity machine) { return machine.getBlockPos().toShortString(); }
    private static Component text(String key, Object... arguments) {
        return Component.translatable("command.molecularmanipulator.research." + key, arguments);
    }
}

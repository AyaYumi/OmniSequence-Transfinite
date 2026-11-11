package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StarResearchCommandsTest {
    @BeforeAll
    static void bootstrap() {
        StarResearchNodeRecipeTest.bootstrap();
    }

    @Test
    void gameBusEventRegistersOnlyPlayerListAndSuggestedIdPurchaseWithoutRemoteOrGrantArguments() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        StarResearchCommands.register(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.DEDICATED, null));
        var root = dispatcher.getRoot().getChild("starresearch");
        assertNotNull(root);
        assertEquals(Set.of("list", "unlock"), root.getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet()));
        var list = root.getChild("list");
        assertNotNull(list.getCommand());
        assertTrue(list.getChildren().isEmpty(), "No coordinate/team targeting");
        var unlock = root.getChild("unlock");
        assertEquals(1, unlock.getChildren().size());
        var id = assertInstanceOf(ArgumentCommandNode.class, unlock.getChild("id"));
        assertInstanceOf(ResourceLocationArgument.class, id.getType());
        assertNotNull(id.getCommand());
        assertNotNull(id.getCustomSuggestions());
        assertTrue(id.getChildren().isEmpty(), "No positional, team or free-grant arguments");
        var console = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null,
                4, "Console", Component.literal("Console"), null, null);
        assertFalse(root.canUse(console), "Even an operator console must not perform player purchases");
    }
}

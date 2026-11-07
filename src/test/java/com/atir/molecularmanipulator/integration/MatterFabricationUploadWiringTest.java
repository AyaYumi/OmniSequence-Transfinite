package com.atir.molecularmanipulator.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Guards the real upload entry point without bootstrapping a server or modifying global registries. */
class MatterFabricationUploadWiringTest {
    private static final String ROOT = "com/atir/molecularmanipulator/";
    private static final String UPLOAD = ROOT + "integration/MatterFabricationPatternUpload";
    private static final String TRANSFER = ROOT + "integration/MatterFabricationUploadTransfer";
    private static final String ASSEMBLY = ROOT + "blockentity/MatterFabricationPatternAssemblyBlockEntity";

    @Test void targetGateNeverReadsCustomNamesAndRenameDoesNotUnregisterTheAssembly() throws IOException {
        var target = calls(method(UPLOAD, "isDestination"));
        assertTrue(target.stream().anyMatch(call -> call.name.equals("getController")));
        assertTrue(target.stream().anyMatch(call -> call.name.equals("getBoundTeam")));
        assertTrue(target.stream().anyMatch(call -> call.owner.equals("java/util/UUID") && call.name.equals("equals")));
        assertTrue(target.stream().anyMatch(call -> call.name.equals("hasChunkAt")));
        assertNoDisplayNameComparison(target);
        assertNoDisplayNameComparison(calls(method(UPLOAD, "register")));

        var rename = calls(method(ASSEMBLY, "rename"));
        assertTrue(rename.stream().anyMatch(call -> call.name.equals("setName")));
        assertTrue(rename.stream().anyMatch(call -> call.name.equals("saveChanges")));
        assertTrue(rename.stream().anyMatch(call -> call.name.equals("updatePatterns")));
        assertTrue(rename.stream().noneMatch(call -> List.of("unregister", "setRemoved", "clearContent",
                "setControllerPos", "clear").contains(call.name)));
    }

    @Test void uploadUsesTheOpenTerminalsNetworkAndPlayerActionSource() throws IOException {
        var calls = calls(method(UPLOAD, "upload"));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("getInventory")
                && call.owner.equals("appeng/helpers/IPatternTerminalMenuHost")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("getActionSource")
                && call.owner.equals("appeng/menu/AEBaseMenu")));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals(TRANSFER) && call.name.equals("<init>")
                && call.desc.contains("Lappeng/api/storage/MEStorage;Lappeng/api/networking/security/IActionSource;")));
        // Destination names belong only in the success message, after insertion has completed.
        assertTrue(index(calls, TRANSFER, "insert") < index(calls, ASSEMBLY, "getDisplayName"));
    }

    @Test void validationAndDuplicateChecksRemainAheadOfBlankConsumption() throws IOException {
        var calls = calls(method(UPLOAD, "upload"));
        assertTrue(index(calls, ROOT + "crafting/MatterFabricationPatternEncoding", "isEncodingMenu")
                < index(calls, ROOT + "crafting/MatterFabricationPatternEncoding", "encode"));
        assertTrue(index(calls, ROOT + "crafting/MatterFabricationPatternEncoding", "encode")
                < index(calls, UPLOAD, "containsPattern"));
        assertTrue(index(calls, UPLOAD, "containsPattern") < index(calls, TRANSFER, "hasBlank"));
        assertTrue(index(calls, TRANSFER, "hasBlank") < index(calls, TRANSFER, "insert"));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("stillValid")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("isValidMenu")));
        assertTrue(calls.stream().anyMatch(call -> call.name.equals("isSpectator")));
    }

    @Test void selectedRecipeInputsUseIngredientValidationNotDisplayedNames() throws IOException {
        var calls = calls(method(ROOT + "crafting/MatterFabricationPatternEncoding", "encode"));
        assertTrue(calls.stream().anyMatch(call -> call.owner.equals("net/minecraft/world/item/crafting/Ingredient")
                && call.name.equals("test")));
        assertNoDisplayNameComparison(calls);
    }

    private static void assertNoDisplayNameComparison(List<MethodInsnNode> calls) {
        for (var call : calls) {
            assertFalse(List.of("getName", "getDisplayName", "getCustomName", "getHoverName", "getTerminalGroup",
                    "getString").contains(call.name), "Display/input name must not be an upload identity: " + call.name);
            assertFalse(call.owner.equals("java/lang/String") && List.of("equals", "equalsIgnoreCase", "contains",
                    "startsWith", "endsWith").contains(call.name), "No name-based destination/input filter");
        }
    }

    private static int index(List<MethodInsnNode> calls, String owner, String name) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).owner.equals(owner) && calls.get(i).name.equals(name)) return i;
        }
        fail("Missing call: " + owner + "." + name);
        return -1;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static MethodNode method(String owner, String name) throws IOException {
        try (var input = MatterFabricationUploadWiringTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(input, owner);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
        }
    }
}

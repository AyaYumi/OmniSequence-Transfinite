package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ForgeMachineRecipeJsonTest {
    @Test
    void ignoresCyclicRuntimeContextWhilePreservingRecipeFields() {
        var context = new RuntimeContext();
        context.owner = context;
        var json = ForgeMachineRecipeJson.encodeFields(new RuntimeRecipe(context)).getAsJsonObject();
        assertEquals(42, json.get("count").getAsInt());
        assertEquals("actual recipe data", json.get("name").getAsString());
        assertFalse(json.has("context"));
        assertFalse(json.has("untypedMixinField"));
    }

    private record RuntimeRecipe(int count, String name, RuntimeContext context, Object untypedMixinField) {
        RuntimeRecipe(RuntimeContext context) { this(42, "actual recipe data", context, context); }
    }
    private static final class RuntimeContext { RuntimeContext owner; }
}

package com.atir.molecularmanipulator.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoCrafterAmountDraftTest {
    @Test
    void editsSurviveRepeatedSyncsUntilTheServerAcknowledgesThem() {
        var draft = new AutoCrafterAmountDraft();
        draft.sync(64);
        draft.edit("128");
        draft.sync(64);
        draft.sync(64);
        assertEquals("128", draft.text());
        assertTrue(draft.isDirty());
        draft.sync(128);
        assertFalse(draft.isDirty());
        draft.sync(256);
        assertEquals("256", draft.text());
    }

    @Test
    void lateAcknowledgementDoesNotEraseANewerEdit() {
        var draft = new AutoCrafterAmountDraft();
        draft.sync(64);
        draft.edit("128");
        draft.edit("256");
        draft.sync(128);
        assertEquals(256, draft.value().orElseThrow());
        assertTrue(draft.isDirty());
    }

    @Test
    void emptyAndOverflowValuesCannotBecomeAnUnlimitedOrMaximumSetting() {
        var draft = new AutoCrafterAmountDraft();
        draft.sync(64);
        for (String invalid : new String[] {"", "9223372036854775808", "9999999999999999999", "-1", "1.5", "１２"}) {
            draft.edit(invalid);
            draft.sync(64);
            assertEquals(invalid, draft.text());
            assertTrue(draft.value().isEmpty(), invalid);
            assertTrue(draft.isDirty());
        }
        draft.edit("0");
        assertEquals(0, draft.value().orElseThrow());
        draft.edit(Long.toString(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, draft.value().orElseThrow());
    }

    @Test
    void independentlyEditedIngredientsSurviveOtherIngredientUpdates() {
        var first = new AutoCrafterAmountDraft();
        var ninth = new AutoCrafterAmountDraft();
        first.sync(10);
        ninth.sync(90);
        first.edit("11");
        ninth.edit("99");
        first.sync(11);
        ninth.sync(90);
        assertFalse(first.isDirty());
        assertEquals("99", ninth.text());
        assertTrue(ninth.isDirty());
    }
}

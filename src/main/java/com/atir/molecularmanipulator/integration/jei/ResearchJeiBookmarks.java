package com.atir.molecularmanipulator.integration.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

/** Small version-tolerant bridge to JEI's internal bookmark list. */
public final class ResearchJeiBookmarks {
    private static volatile IJeiRuntime runtime;
    private ResearchJeiBookmarks() { }

    public static void setRuntime(IJeiRuntime value) { runtime = value; }

    public static int addItems(List<ItemStack> stacks) {
        IJeiRuntime current = runtime;
        if (current == null || stacks == null || stacks.isEmpty()) return 0;
        try {
            var typedClass = mezz.jei.api.ingredients.ITypedIngredient.class;
            Object bookmarkList;
            Method add;
            Object factory = null;
            Method create = null;
            try {
                // New JEI exposes bookmarks through its public runtime manager.
                var runtimeApi = IJeiRuntime.class.getMethod("getBookmarkManager");
                bookmarkList = runtimeApi.invoke(current);
                add = runtimeApi.getReturnType().getMethod("add", typedClass);
            } catch (NoSuchMethodException olderApi) {
                Object overlay = current.getBookmarkOverlay();
                bookmarkList = findField(overlay.getClass(), "bookmarkList").get(overlay);
                try {
                    add = bookmarkList.getClass().getMethod("addIngredientBookmark", typedClass);
                } catch (NoSuchMethodException legacyApi) {
                    factory = findField(bookmarkList.getClass(), "bookmarkFactory").get(bookmarkList);
                    create = factory.getClass().getMethod("create", typedClass);
                    add = bookmarkList.getClass().getMethod("add", Class.forName("mezz.jei.gui.bookmarks.IBookmark"));
                }
            }
            int added = 0, valid = 0;
            for (ItemStack source : stacks) {
                if (source == null || source.isEmpty()) continue;
                var typed = current.getIngredientManager().createTypedIngredient(
                        VanillaTypes.ITEM_STACK, source.copyWithCount(1));
                if (typed.isEmpty()) continue;
                valid++;
                Object bookmark = create == null ? typed.get() : create.invoke(factory, typed.get());
                if ((Boolean) add.invoke(bookmarkList, bookmark)) added++;
            }
            if (valid > 0) showBookmarks(current);
            return added;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Could not bookmark research materials in JEI", failure);
            return 0;
        }
    }

    private static void showBookmarks(IJeiRuntime current) {
        try {
            Object overlay = current.getBookmarkOverlay();
            Object state = findField(overlay.getClass(), "toggleState").get(overlay);
            Class.forName("mezz.jei.common.config.IClientToggleState").getMethod("setBookmarkEnabled", boolean.class)
                    .invoke(state, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // The items are already persisted. Optional overlay display must not undo success.
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Could not show JEI research bookmarks", unavailable);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}

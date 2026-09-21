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
            Object overlay = current.getBookmarkOverlay();
            Field listField = findField(overlay.getClass(), "bookmarkList");
            Object bookmarkList = listField.get(overlay);
            Field factoryField = findField(bookmarkList.getClass(), "bookmarkFactory");
            Object factory = factoryField.get(bookmarkList);
            Method create = factory.getClass().getMethod("create", Class.forName("mezz.jei.api.ingredients.ITypedIngredient"));
            Method add = bookmarkList.getClass().getMethod("add", Class.forName("mezz.jei.gui.bookmarks.IBookmark"));
            int added = 0;
            for (ItemStack source : stacks) {
                if (source == null || source.isEmpty()) continue;
                var typed = current.getIngredientManager().createTypedIngredient(
                        VanillaTypes.ITEM_STACK, source.copyWithCount(1));
                if (typed.isEmpty()) continue;
                Object bookmark = create.invoke(factory, typed.get());
                if ((Boolean) add.invoke(bookmarkList, bookmark)) added++;
            }
            return added;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
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

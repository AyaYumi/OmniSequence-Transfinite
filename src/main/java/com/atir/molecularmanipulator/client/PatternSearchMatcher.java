package com.atir.molecularmanipulator.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Client-only bridge to Just Enough Characters. Keeping the optional class
 * name behind reflection prevents common/server class loading from linking it.
 */
final class PatternSearchMatcher {
    private static Method jecContains = findJecContains();

    private PatternSearchMatcher() {
    }

    static boolean contains(String candidate, CharSequence query) {
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toString().toLowerCase(Locale.ROOT);
        Method method = jecContains;
        if (method != null) {
            try {
                Object result = method.invoke(null, normalizedCandidate, normalizedQuery);
                if (result instanceof Boolean matched) {
                    return matched;
                }
            } catch (ReflectiveOperationException | LinkageError exception) {
                jecContains = null;
            }
        }
        return normalizedCandidate.contains(normalizedQuery);
    }

    private static Method findJecContains() {
        try {
            Class<?> matchClass = Class.forName(
                    "me.towdium.jecharacters.utils.Match",
                    false,
                    PatternSearchMatcher.class.getClassLoader());
            Method method = matchClass.getMethod("contains", String.class, CharSequence.class);
            return Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == boolean.class
                    ? method
                    : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }
}

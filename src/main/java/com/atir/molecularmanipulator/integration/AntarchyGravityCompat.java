package com.atir.molecularmanipulator.integration;

import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;

/** Read Antarchy's public state API without making it a required dependency or changing its gravity. */
public final class AntarchyGravityCompat {
    private AntarchyGravityCompat() {}

    public static boolean controlsGravity(Entity entity) {
        var api = ApiHolder.API;
        if (api == null) return false;
        try {
            return (boolean) api.inverted.invokeExact(entity)
                    || (boolean) api.forced.invokeExact(entity)
                    || (int) api.remaining.invokeExact(entity) > 0;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable exception) {
            RateLimitedLog.warn("Unable to read Antarchy gravity state", exception);
            return false;
        }
    }

    private record Api(MethodHandle inverted, MethodHandle forced, MethodHandle remaining) {}

    private static final class ApiHolder {
        private static final Api API = discover();

        private static Api discover() {
            try {
                var type = Class.forName("com.craisinlord.antarchy.content.gravity.AntarchyGravityApi",
                        false, AntarchyGravityCompat.class.getClassLoader());
                var lookup = MethodHandles.publicLookup();
                var flag = MethodType.methodType(boolean.class, Entity.class);
                return new Api(lookup.findStatic(type, "isGravityInverted", flag),
                        lookup.findStatic(type, "isGravityForced", flag),
                        lookup.findStatic(type, "getTransitionRemaining", MethodType.methodType(int.class, Entity.class)));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException | LinkageError exception) {
                RateLimitedLog.warn("Unable to discover Antarchy gravity API", exception);
                return null;
            }
        }
    }
}

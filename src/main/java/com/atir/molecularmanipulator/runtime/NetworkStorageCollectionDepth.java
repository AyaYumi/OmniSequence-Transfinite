package com.atir.molecularmanipulator.runtime;

/** Tracks nested NetworkStorage collection calls across different instances. */
public final class NetworkStorageCollectionDepth {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private NetworkStorageCollectionDepth() {
    }

    public static Integer current() {
        return DEPTH.get();
    }

    public static boolean isCollecting() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }

    public static void enter(Integer previousDepth) {
        DEPTH.set(previousDepth == null ? 1 : previousDepth + 1);
    }

    public static void exit(Integer previousDepth) {
        if (previousDepth == null) {
            DEPTH.remove();
        } else {
            DEPTH.set(previousDepth);
        }
    }
}

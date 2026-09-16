package com.atir.molecularmanipulator.blockentity;

final class MolecularOutputScheduling {
    private MolecularOutputScheduling() {
    }

    static long scheduleNextTick(long currentReadyTick, long gameTime) {
        long nextTick = gameTime == Long.MAX_VALUE ? Long.MAX_VALUE : gameTime + 1;
        return currentReadyTick == Long.MIN_VALUE || nextTick < currentReadyTick
                ? nextTick
                : currentReadyTick;
    }
}

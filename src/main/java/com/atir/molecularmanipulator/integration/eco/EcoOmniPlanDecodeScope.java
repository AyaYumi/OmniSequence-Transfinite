package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.world.level.Level;

/** Supplies the public ECO NBT codec with an Omni CPU's level and pattern vector. */
public final class EcoOmniPlanDecodeScope implements AutoCloseable {
    private static final ThreadLocal<EcoOmniPlanDecodeScope> CURRENT = new ThreadLocal<>();
    private final EcoOmniPlanDecodeScope previous;
    private final Level level;
    private final Map<AEItemKey, IPatternDetails> patterns;

    private EcoOmniPlanDecodeScope(Level level, Map<AEItemKey, IPatternDetails> patterns) {
        previous = CURRENT.get();
        this.level = level;
        this.patterns = patterns;
        CURRENT.set(this);
    }

    public static EcoOmniPlanDecodeScope open(Level level, Map<AEItemKey, IPatternDetails> patterns) {
        return new EcoOmniPlanDecodeScope(level, patterns);
    }

    public static EcoOmniPlanDecodeScope current() { return CURRENT.get(); }
    public Level level() { return level; }
    public IPatternDetails pattern(AEItemKey definition) { return patterns.get(definition); }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}

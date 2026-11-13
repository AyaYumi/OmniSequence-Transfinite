package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Intake and delivery share one batch timeline; only the final tick creates the converted stack. */
public final class LabCrystallization {
    public static final int INTAKE_START = 72;
    public static final int ABSORBED = 112;
    public static final int DURATION = 160;
    public static final Vec3 INPUT = new Vec3(-2.25, 3.45, 48.25);
    public static final Vec3 OUTPUT = new Vec3(2.25, 3.45, 48.25);
    public static final Vec3 BLACK = new Vec3(-13, 12, 0);
    public static final Vec3 WHITE_OUTLET = new Vec3(13, 12, 5.6);

    private LabCrystallization() {}

    public static ItemStack resultFor(ItemStack input) {
        if (input.is(ModContent.PRIMORDIAL_CRYSTAL_CORE.get())) {
            return new ItemStack(ModContent.STAR_MARROW_SEED.get(), input.getCount());
        }
        return ItemStack.EMPTY;
    }

    public static float portion(float age, int from, int to) { return Mth.clamp((age - from) / (to - from), 0, 1); }
    public static boolean intake(float age) { return age >= INTAKE_START && age < ABSORBED; }
    public static boolean delivery(float age) { return age >= ABSORBED && age < DURATION; }
    public static Vec3 inputPosition(float age) {
        return curve(INPUT, new Vec3(-5, 7, 35), new Vec3(-16, 17, 16), BLACK,
                portion(age, INTAKE_START, ABSORBED));
    }
    public static Vec3 outputPosition(float age) {
        return curve(WHITE_OUTLET, new Vec3(15, 16, 16), new Vec3(4, 7, 34), OUTPUT,
                portion(age, ABSORBED, DURATION));
    }
    private static Vec3 curve(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
        double u = 1 - t;
        return a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(d.scale(t * t * t));
    }
}

package com.atir.molecularmanipulator.client.render;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;

/** AE2 crafting-cube rings; adjacent powered nexuses hide the shared edge. */
public final class NexusFormedLayout {
    public static final float RING = 3.0F;
    public static final float INNER_MIN = 2.99F;
    public static final float INNER_MAX = 13.01F;

    public record Box(float x1, float y1, float z1, float x2, float y2, float z2) {
    }

    public record Stripe(boolean vertical, Box box) {
    }

    private NexusFormedLayout() {
    }

    public static Box inner(Direction side) {
        return inner(side, EnumSet.noneOf(Direction.class));
    }

    public static Box inner(Direction side, EnumSet<Direction> connections) {
        float x1 = connections.contains(Direction.WEST) ? 0 : INNER_MIN;
        float x2 = connections.contains(Direction.EAST) ? 16 : INNER_MAX;
        float y1 = connections.contains(Direction.DOWN) ? 0 : INNER_MIN;
        float y2 = connections.contains(Direction.UP) ? 16 : INNER_MAX;
        float z1 = connections.contains(Direction.NORTH) ? 0 : INNER_MIN;
        float z2 = connections.contains(Direction.SOUTH) ? 16 : INNER_MAX;
        switch (side) {
            case DOWN, UP -> {
                y1 = 0;
                y2 = 16;
            }
            case NORTH, SOUTH -> {
                z1 = 0;
                z2 = 16;
            }
            case WEST, EAST -> {
                x1 = 0;
                x2 = 16;
            }
        }
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    public static List<Box> corners(Direction side) {
        return corners(side, EnumSet.noneOf(Direction.class));
    }

    public static List<Box> corners(Direction side, EnumSet<Direction> connections) {
        var boxes = new ArrayList<Box>(4);
        addCorner(boxes, connections, side, Direction.UP, Direction.EAST, Direction.NORTH);
        addCorner(boxes, connections, side, Direction.UP, Direction.EAST, Direction.SOUTH);
        addCorner(boxes, connections, side, Direction.UP, Direction.WEST, Direction.NORTH);
        addCorner(boxes, connections, side, Direction.UP, Direction.WEST, Direction.SOUTH);
        addCorner(boxes, connections, side, Direction.DOWN, Direction.EAST, Direction.NORTH);
        addCorner(boxes, connections, side, Direction.DOWN, Direction.EAST, Direction.SOUTH);
        addCorner(boxes, connections, side, Direction.DOWN, Direction.WEST, Direction.NORTH);
        addCorner(boxes, connections, side, Direction.DOWN, Direction.WEST, Direction.SOUTH);
        return List.copyOf(boxes);
    }

    public static List<Stripe> stripes(Direction side) {
        return stripes(side, EnumSet.noneOf(Direction.class));
    }

    public static List<Stripe> stripes(Direction side, EnumSet<Direction> connections) {
        var stripes = new ArrayList<Stripe>(4);
        for (var edge : Direction.values()) {
            if (edge == side || edge == side.getOpposite() || connections.contains(edge)) continue;
            float x1 = 0, y1 = 0, z1 = 0, x2 = 16, y2 = 16, z2 = 16;
            switch (edge) {
                case DOWN -> {
                    y1 = 0;
                    y2 = RING;
                }
                case UP -> {
                    y1 = 16 - RING;
                    y2 = 16;
                }
                case WEST -> {
                    x1 = 0;
                    x2 = RING;
                }
                case EAST -> {
                    x1 = 16 - RING;
                    x2 = 16;
                }
                case NORTH -> {
                    z1 = 0;
                    z2 = RING;
                }
                case SOUTH -> {
                    z1 = 16 - RING;
                    z2 = 16;
                }
            }
            var perpendicular = rotateAround(edge, side);
            for (var cap : List.of(perpendicular, perpendicular.getOpposite())) {
                if (connections.contains(cap)) continue;
                switch (cap) {
                    case DOWN -> y1 = RING;
                    case UP -> y2 = 16 - RING;
                    case NORTH -> z1 = RING;
                    case SOUTH -> z2 = 16 - RING;
                    case WEST -> x1 = RING;
                    case EAST -> x2 = 16 - RING;
                    default -> {
                    }
                }
            }
            stripes.add(new Stripe(verticalRing(side, edge), new Box(x1, y1, z1, x2, y2, z2)));
        }
        return List.copyOf(stripes);
    }

    public static boolean verticalRing(Direction side, Direction edge) {
        if (side.getAxis() != Axis.Y
                && (edge == Direction.NORTH || edge == Direction.EAST
                || edge == Direction.WEST || edge == Direction.SOUTH)) {
            return true;
        }
        return side.getAxis() == Axis.Y && (edge == Direction.EAST || edge == Direction.WEST);
    }

    static Direction rotateAround(Direction forward, Direction axis) {
        if (forward.getAxis() == axis.getAxis()) return forward;
        var crossed = forward.getNormal().cross(axis.getNormal());
        return Objects.requireNonNull(Direction.fromDelta(crossed.getX(), crossed.getY(), crossed.getZ()));
    }

    private static void addCorner(List<Box> boxes, EnumSet<Direction> connections, Direction side,
            Direction down, Direction west, Direction north) {
        if (connections.contains(down) || connections.contains(west) || connections.contains(north)) return;
        if (side != down && side != west && side != north) return;
        float x1 = west == Direction.WEST ? 0 : 16 - RING;
        float y1 = down == Direction.DOWN ? 0 : 16 - RING;
        float z1 = north == Direction.NORTH ? 0 : 16 - RING;
        boxes.add(new Box(x1, y1, z1, west == Direction.WEST ? RING : 16,
                down == Direction.DOWN ? RING : 16, north == Direction.NORTH ? RING : 16));
    }
}

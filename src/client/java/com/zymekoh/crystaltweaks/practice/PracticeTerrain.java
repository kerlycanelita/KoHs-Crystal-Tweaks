package com.zymekoh.crystaltweaks.practice;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a practice arena, as pure numbers: gentle hills for the natural worlds, holes and
 * ledges for the hole arena, where the few trees and plants stand.
 *
 * <p>Everything is a function of the world seed and the column, with no state and no Minecraft
 * types. The arena is rebuilt from it at the start of every round, and a deterministic shape is what
 * lets a crater dug last round be filled back in exactly, tree for tree.</p>
 */
public final class PracticeTerrain {
    /** Half the side of the square arena rebuilt each round, in blocks. */
    public static final int RADIUS = 40;
    /** Open ground around the spawn: no trees, no holes, no hills. */
    public static final int OPEN_SPAWN = 9;
    private static final int HOLE_CELL = 4;
    private static final int LEDGE_CELL = 9;
    private static final int TREE_CELL = 8;

    private final long seed;

    public PracticeTerrain(long seed) {
        this.seed = seed;
    }

    /**
     * Height of the ground above the flat base, from -2 to +2: an almost flat meadow. It fades to
     * zero at the arena's edge, where the flat world around it takes over, and near the spawn.
     */
    public int heightOffset(int x, int z) {
        double distance = Math.sqrt((double) x * x + (double) z * z);
        if (distance >= RADIUS) {
            return 0;
        }
        double edge = smooth((RADIUS - distance) / 10.0D);
        double center = smooth((distance - 3.0D) / 6.0D);
        double noise = valueNoise(x, z, 13, 1) + valueNoise(x, z, 6, 2) * 0.45D;
        return (int) Math.round(noise * 1.6D * edge * center);
    }

    /** True where the hole arena has a hole: one block deep with bedrock at the bottom. */
    public boolean hole(int x, int z) {
        if ((long) x * x + (long) z * z < 16L || Math.abs(x) > RADIUS - 1 || Math.abs(z) > RADIUS - 1) {
            return false;
        }
        int cellX = Math.floorDiv(x, HOLE_CELL);
        int cellZ = Math.floorDiv(z, HOLE_CELL);
        if (unit(cellX, cellZ, 11) >= 0.55D) {
            return false;
        }
        // Inside the cell a margin of one block keeps neighbouring holes from merging.
        int originX = cellX * HOLE_CELL + (int) (unit(cellX, cellZ, 12) * 2.0D);
        int originZ = cellZ * HOLE_CELL + (int) (unit(cellX, cellZ, 13) * 2.0D);
        double shape = unit(cellX, cellZ, 14);
        int width = shape < 0.65D ? 1 : shape < 0.80D ? 2 : shape < 0.90D ? 1 : 2;
        int depth = shape < 0.65D ? 1 : shape < 0.80D ? 1 : 2;
        return x >= originX && x < originX + width && z >= originZ && z < originZ + depth;
    }

    /** True where the hole arena has a raised 2x2 step of obsidian to fight from or ledge off. */
    public boolean ledge(int x, int z) {
        if ((long) x * x + (long) z * z < 49L || Math.abs(x) > RADIUS - 2 || Math.abs(z) > RADIUS - 2) {
            return false;
        }
        int cellX = Math.floorDiv(x, LEDGE_CELL);
        int cellZ = Math.floorDiv(z, LEDGE_CELL);
        if (unit(cellX, cellZ, 21) >= 0.22D) {
            return false;
        }
        int originX = cellX * LEDGE_CELL + 2 + (int) (unit(cellX, cellZ, 22) * 4.0D);
        int originZ = cellZ * LEDGE_CELL + 2 + (int) (unit(cellX, cellZ, 23) * 4.0D);
        boolean inside = x >= originX && x < originX + 2 && z >= originZ && z < originZ + 2;
        if (!inside) {
            return false;
        }
        // A step never covers a hole.
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                if (hole(originX + dx, originZ + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Where the trees stand: at most one per 8x8 cell, never near the spawn or the edge, so the
     * middle stays open and the few trees are scenery and cover rather than a forest.
     *
     * @param density chance, per cell, of a tree
     * @return each tree as {x, z, variant}, the variant a number from 0 to 99 for its shape
     */
    public List<int[]> trees(double density) {
        List<int[]> trees = new ArrayList<>();
        if (density <= 0.0D) {
            return trees;
        }
        int cells = RADIUS / TREE_CELL + 1;
        for (int cellX = -cells; cellX <= cells; cellX++) {
            for (int cellZ = -cells; cellZ <= cells; cellZ++) {
                if (unit(cellX, cellZ, 31) >= density) {
                    continue;
                }
                int x = cellX * TREE_CELL + 2 + (int) (unit(cellX, cellZ, 32) * 4.0D);
                int z = cellZ * TREE_CELL + 2 + (int) (unit(cellX, cellZ, 33) * 4.0D);
                double distance = Math.sqrt((double) x * x + (double) z * z);
                if (distance < OPEN_SPAWN + 1 || Math.abs(x) > RADIUS - 4 || Math.abs(z) > RADIUS - 4) {
                    continue;
                }
                trees.add(new int[] {x, z, (int) (unit(cellX, cellZ, 34) * 100.0D)});
            }
        }
        return trees;
    }

    /**
     * Which plant, if any, grows on a column: -1 for none, otherwise an index below {@code kinds}.
     * The spawn's surroundings keep a little more open ground.
     */
    public int plant(int x, int z, double density, int kinds) {
        if (kinds <= 0 || density <= 0.0D) {
            return -1;
        }
        double distance = Math.sqrt((double) x * x + (double) z * z);
        double local = distance < 4.0D ? density * 0.3D : density;
        if (unit(x, z, 41) >= local) {
            return -1;
        }
        return (int) (unit(x, z, 42) * kinds);
    }

    /** A second surface block here and there, like the podzol patches of a taiga. */
    public boolean patch(int x, int z, double share) {
        return valueNoise(x, z, 5, 51) > 1.0D - share * 2.0D;
    }

    /** A fixed number in [0, 1) for a column and purpose. */
    public double unit(int x, int z, int salt) {
        long mixed = this.seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }

    /** Smooth noise in [-1, 1] with features about {@code scale} blocks wide. */
    private double valueNoise(int x, int z, int scale, int salt) {
        int cellX = Math.floorDiv(x, scale);
        int cellZ = Math.floorDiv(z, scale);
        double fractionX = smooth((x - cellX * (double) scale) / scale);
        double fractionZ = smooth((z - cellZ * (double) scale) / scale);
        double corner00 = unit(cellX, cellZ, salt) * 2.0D - 1.0D;
        double corner10 = unit(cellX + 1, cellZ, salt) * 2.0D - 1.0D;
        double corner01 = unit(cellX, cellZ + 1, salt) * 2.0D - 1.0D;
        double corner11 = unit(cellX + 1, cellZ + 1, salt) * 2.0D - 1.0D;
        double top = corner00 + (corner10 - corner00) * fractionX;
        double bottom = corner01 + (corner11 - corner01) * fractionX;
        return top + (bottom - top) * fractionZ;
    }

    private static double smooth(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }
}

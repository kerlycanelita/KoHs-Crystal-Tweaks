package com.zymekoh.crystaltweaks.practice;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.phys.Vec3;

/**
 * Builds the ground of a practice round and puts it back after every one.
 *
 * <p>Three kinds of ground. The netherite flat, where crystals only go on obsidian someone places.
 * The hole arena: an obsidian floor, so a crystal fits anywhere, dotted with one-block holes floored
 * with bedrock and a few raised steps, the terrain of hole fights. And a natural meadow in the chosen
 * biome: almost flat, a few trees, a few plants and no caves at all.</p>
 *
 * <p>Everything is rebuilt from {@link PracticeTerrain}, a pure function of the seed, so craters
 * from the last round are filled back exactly. Only blocks that differ are written, without
 * neighbour updates: about 170 thousand reads for the natural arena, once per round.</p>
 */
public final class PracticeArena {
    static final int RADIUS = PracticeTerrain.RADIUS;
    /** How far above the ground a round's leftovers are cleared: blocks, anchors, glowstone, fire. */
    private static final int CLEAR_HEIGHT = 14;
    /** How deep an explosion's crater is filled back in the natural arena. */
    private static final int FILL_DEPTH = 8;

    private final ServerLevel level;
    private final PracticeSettings.WorldType type;
    private final PracticeSettings.Biome biome;
    private final int base;
    private final PracticeTerrain terrain;
    private final Palette palette;

    PracticeArena(ServerLevel level, PracticeSettings.WorldType type, PracticeSettings.Biome biome, int base, long seed) {
        this.level = level;
        this.type = type;
        this.biome = biome;
        this.base = base;
        this.terrain = new PracticeTerrain(seed);
        this.palette = Palette.of(biome);
    }

    /** Height of the ground's top block at a column, as the arena builds it. */
    int groundY(int x, int z) {
        if (this.type == PracticeSettings.WorldType.NATURAL) {
            return this.base + this.terrain.heightOffset(x, z);
        }
        if (this.type == PracticeSettings.WorldType.HOLES && this.terrain.ledge(x, z)) {
            return this.base + 1;
        }
        return this.base;
    }

    Vec3 spawn() {
        return new Vec3(0.5D, groundY(0, 0) + 1.0D, 0.5D);
    }

    /** True for a column the bot can stand on: in the arena, not in a hole, with room to stand. */
    boolean standable(int x, int z) {
        if (Math.abs(x) > RADIUS - 3 || Math.abs(z) > RADIUS - 3) {
            return false;
        }
        if (this.type == PracticeSettings.WorldType.HOLES && this.terrain.hole(x, z)) {
            return false;
        }
        BlockPos feet = new BlockPos(x, groundY(x, z) + 1, z);
        return this.level.getBlockState(feet).getCollisionShape(this.level, feet).isEmpty()
                && this.level.getBlockState(feet.above()).getCollisionShape(this.level, feet.above()).isEmpty();
    }

    void rebuild() {
        switch (this.type) {
            case FLAT -> rebuildFlat(Blocks.NETHERITE_BLOCK.defaultBlockState());
            case HOLES -> rebuildHoles();
            case NATURAL -> rebuildNatural();
        }
    }

    private void rebuildFlat(BlockState floor) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                set(position.set(x, this.base, z), floor);
                clearAbove(position, x, z, this.base + 1);
            }
        }
    }

    private void rebuildHoles() {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                boolean hole = this.terrain.hole(x, z);
                boolean ledge = !hole && this.terrain.ledge(x, z);
                if (hole) {
                    set(position.set(x, this.base - 1, z), bedrock);
                }
                set(position.set(x, this.base, z), hole ? air : obsidian);
                if (ledge) {
                    set(position.set(x, this.base + 1, z), obsidian);
                }
                clearAbove(position, x, z, this.base + (ledge ? 2 : 1));
            }
        }
    }

    private void rebuildNatural() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int top = groundY(x, z);
                BlockState surface = this.palette.patch != null && this.terrain.patch(x, z, 0.22D)
                        ? this.palette.patch
                        : this.palette.top;
                for (int y = this.base - FILL_DEPTH; y <= this.base + 3; y++) {
                    BlockState state;
                    if (y > top) {
                        state = air;
                    } else if (y == top) {
                        state = surface;
                    } else if (y >= top - 3) {
                        state = this.palette.under;
                    } else {
                        state = this.palette.deep;
                    }
                    set(position.set(x, y, z), state);
                }
                clearAbove(position, x, z, Math.max(top + 1, this.base + 4));
                if (this.palette.cover != null) {
                    set(position.set(x, top + 1, z), this.palette.cover);
                } else {
                    int plant = this.terrain.plant(x, z, this.palette.plantDensity, this.palette.plants.length);
                    if (plant >= 0) {
                        set(position.set(x, top + 1, z), this.palette.plants[plant]);
                    }
                }
            }
        }
        List<int[]> trees = this.terrain.trees(this.palette.treeDensity);
        for (int[] tree : trees) {
            int x = tree[0];
            int z = tree[1];
            growTree(new BlockPos(x, groundY(x, z) + 1, z), tree[2]);
        }
    }

    private void clearAbove(BlockPos.MutableBlockPos position, int x, int z, int from) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = from; y <= this.base + CLEAR_HEIGHT; y++) {
            set(position.set(x, y, z), air);
        }
    }

    private void set(BlockPos position, BlockState state) {
        if (this.level.getBlockState(position) != state) {
            // Clients are told; neighbours are not, so nothing cascades while the arena is rebuilt.
            this.level.setBlock(position, state, 2);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Trees: a handful of simple Vanilla-looking shapes, built block by block so they look the same
    // on every Minecraft version and grow back identically each round.
    // ------------------------------------------------------------------------------------------

    private void growTree(BlockPos root, int variant) {
        switch (this.palette.tree) {
            // A plains tree is now and then a birch, as in Vanilla's plains.
            case OAK -> {
                if (variant < 25) {
                    roundTree(root, Blocks.BIRCH_LOG.defaultBlockState(), leaves(Blocks.BIRCH_LEAVES.defaultBlockState()),
                            5 + variant % 2, 2);
                } else {
                    roundTree(root, Blocks.OAK_LOG.defaultBlockState(), leaves(Blocks.OAK_LEAVES.defaultBlockState()),
                            4 + variant % 2, 2);
                }
            }
            case SPRUCE -> spruce(root, 6 + variant % 3);
            case ACACIA -> acacia(root, variant);
            case CHERRY -> cherry(root, variant);
            case CACTUS -> cactus(root, 1 + variant % 3);
            case NONE -> {
            }
        }
    }

    private static BlockState leaves(BlockState leaves) {
        return leaves.setValue(LeavesBlock.PERSISTENT, true);
    }

    private void roundTree(BlockPos root, BlockState log, BlockState leaves, int height, int radius) {
        for (int y = 0; y < height; y++) {
            set(root.above(y), log);
        }
        int top = height;
        for (int dy = -2; dy <= 1; dy++) {
            int layerRadius = dy <= -1 ? radius : 1;
            for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                    boolean corner = Math.abs(dx) == layerRadius && Math.abs(dz) == layerRadius;
                    if (corner && (dy == 1 || (layerRadius > 1 && ((dx + dz + dy) & 1) == 0))) {
                        continue;
                    }
                    BlockPos leaf = root.offset(dx, top + dy, dz);
                    if (this.level.getBlockState(leaf).isAir()) {
                        set(leaf, leaves);
                    }
                }
            }
        }
    }

    private void spruce(BlockPos root, int height) {
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState leaves = leaves(Blocks.SPRUCE_LEAVES.defaultBlockState());
        for (int y = 0; y < height; y++) {
            set(root.above(y), log);
        }
        set(root.above(height), leaves);
        for (int y = 2; y < height; y++) {
            int fromTop = height - y;
            int radius = fromTop <= 1 ? 1 : (fromTop % 2 == 0 ? 2 : 1);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if ((dx == 0 && dz == 0) || (Math.abs(dx) == radius && Math.abs(dz) == radius && radius > 1)) {
                        continue;
                    }
                    BlockPos leaf = root.offset(dx, y, dz);
                    if (this.level.getBlockState(leaf).isAir()) {
                        set(leaf, leaves);
                    }
                }
            }
        }
    }

    private void acacia(BlockPos root, int variant) {
        BlockState log = Blocks.ACACIA_LOG.defaultBlockState();
        BlockState leaves = leaves(Blocks.ACACIA_LEAVES.defaultBlockState());
        Direction lean = switch (variant % 4) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
        BlockPos top = root;
        for (int y = 0; y < 3; y++) {
            set(top, log);
            top = top.above();
        }
        for (int step = 0; step < 2; step++) {
            top = top.relative(lean);
            set(top, log);
            top = top.above();
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                BlockPos leaf = top.offset(dx, 0, dz);
                if (this.level.getBlockState(leaf).isAir()) {
                    set(leaf, leaves);
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && this.level.getBlockState(leaf.above()).isAir()) {
                    set(leaf.above(), leaves);
                }
            }
        }
    }

    private void cherry(BlockPos root, int variant) {
        BlockState log = Blocks.CHERRY_LOG.defaultBlockState();
        BlockState leaves = leaves(Blocks.CHERRY_LEAVES.defaultBlockState());
        int height = 4 + variant % 2;
        for (int y = 0; y < height; y++) {
            set(root.above(y), log);
        }
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 1 ? 2 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) {
                        continue;
                    }
                    BlockPos leaf = root.offset(dx, height + dy, dz);
                    if (this.level.getBlockState(leaf).isAir()) {
                        set(leaf, leaves);
                    }
                }
            }
        }
    }

    private void cactus(BlockPos root, int height) {
        BlockState cactus = Blocks.CACTUS.defaultBlockState();
        for (int y = 0; y < height; y++) {
            set(root.above(y), cactus);
        }
    }

    /**
     * The layers a new practice world is generated with, {@code layers} in all, the top one at the
     * arena's floor. Outside the arena the world stays exactly this, so it matches the arena's ground.
     */
    public static List<FlatLayerInfo> flatLayers(PracticeSettings.WorldType type, PracticeSettings.Biome biome,
            int layers) {
        List<FlatLayerInfo> list = new ArrayList<>();
        list.add(new FlatLayerInfo(1, Blocks.BEDROCK));
        if (type != PracticeSettings.WorldType.NATURAL) {
            list.add(new FlatLayerInfo(layers - 6, Blocks.STONE));
            list.add(new FlatLayerInfo(4, Blocks.DIRT));
            list.add(new FlatLayerInfo(1, type == PracticeSettings.WorldType.HOLES ? Blocks.OBSIDIAN : Blocks.NETHERITE_BLOCK));
            return list;
        }
        Palette palette = Palette.of(biome);
        list.add(new FlatLayerInfo(layers - 5, palette.deep.getBlock()));
        list.add(new FlatLayerInfo(3, palette.under.getBlock()));
        list.add(new FlatLayerInfo(1, palette.top.getBlock()));
        if (palette.cover != null) {
            list.add(new FlatLayerInfo(1, palette.cover.getBlock()));
        }
        return list;
    }

    // ------------------------------------------------------------------------------------------
    // Palettes
    // ------------------------------------------------------------------------------------------

    private enum Tree { NONE, OAK, SPRUCE, ACACIA, CHERRY, CACTUS }

    private record Palette(BlockState top, BlockState patch, BlockState under, BlockState deep, BlockState cover,
            Tree tree, double treeDensity, BlockState[] plants, double plantDensity) {

        static Palette of(PracticeSettings.Biome biome) {
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            BlockState stone = Blocks.STONE.defaultBlockState();
            BlockState shortGrass = Blocks.SHORT_GRASS.defaultBlockState();
            return switch (biome) {
                case PLAINS -> new Palette(grass, null, dirt, stone, null, Tree.OAK, 0.14D,
                        new BlockState[] {shortGrass, shortGrass, shortGrass, shortGrass, shortGrass, shortGrass,
                                Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
                                Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState(),
                                Blocks.CORNFLOWER.defaultBlockState()}, 0.2D);
                case DESERT -> new Palette(Blocks.SAND.defaultBlockState(), null, Blocks.SAND.defaultBlockState(),
                        Blocks.SANDSTONE.defaultBlockState(), null, Tree.CACTUS, 0.18D,
                        new BlockState[] {Blocks.DEAD_BUSH.defaultBlockState()}, 0.012D);
                case TAIGA -> new Palette(grass, Blocks.PODZOL.defaultBlockState(), dirt, stone, null, Tree.SPRUCE, 0.24D,
                        new BlockState[] {Blocks.FERN.defaultBlockState(), Blocks.FERN.defaultBlockState(), shortGrass},
                        0.14D);
                case SNOWY -> new Palette(grass.setValue(BlockStateProperties.SNOWY, true), null, dirt, stone,
                        Blocks.SNOW.defaultBlockState(), Tree.SPRUCE, 0.06D, new BlockState[0], 0.0D);
                case SAVANNA -> new Palette(grass, Blocks.COARSE_DIRT.defaultBlockState(), dirt, stone, null, Tree.ACACIA,
                        0.1D, new BlockState[] {shortGrass}, 0.26D);
                case CHERRY -> new Palette(grass, null, dirt, stone, null, Tree.CHERRY, 0.16D,
                        new BlockState[] {Blocks.PINK_PETALS.defaultBlockState(), Blocks.PINK_PETALS.defaultBlockState(),
                                shortGrass}, 0.22D);
                case BADLANDS -> new Palette(Blocks.RED_SAND.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
                        Blocks.TERRACOTTA.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), null, Tree.CACTUS,
                        0.05D, new BlockState[] {Blocks.DEAD_BUSH.defaultBlockState()}, 0.016D);
                case END -> new Palette(Blocks.END_STONE.defaultBlockState(), null, Blocks.END_STONE.defaultBlockState(),
                        Blocks.END_STONE.defaultBlockState(), null, Tree.NONE, 0.0D, new BlockState[0], 0.0D);
            };
        }
    }
}

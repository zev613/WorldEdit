/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.function.diff;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Compares the blocks of a clipboard, placed at the coordinates it was copied
 * from, with the blocks currently in an extent (usually the world).
 *
 * <p>Blocks are compared by block state; differences in block entity NBT
 * alone are not reported.</p>
 */
public final class WorldDiff {

    /**
     * Kind of difference, as seen from the clipboard (the "before") to the
     * world (the "after").
     */
    public enum Kind {
        /** Same block state. */
        UNCHANGED,
        /** Both non-air but different. */
        CHANGED,
        /** The clipboard had a block, the world now has air. */
        REMOVED,
        /** The clipboard had air, the world now has a block. */
        ADDED
    }

    /**
     * Receives every difference.
     */
    @FunctionalInterface
    public interface DifferenceConsumer {
        /**
         * Called for each differing position.
         *
         * @param position the position
         * @param kind the kind (never {@link Kind#UNCHANGED})
         * @param expected the clipboard block
         * @param actual the world block
         */
        void accept(BlockVector3 position, Kind kind, BaseBlock expected, BlockState actual);
    }

    /**
     * A recorded difference.
     *
     * @param position the position
     * @param kind the kind
     */
    public record Difference(BlockVector3 position, Kind kind) {
    }

    /**
     * A {@code from -> to} block type pair with a count.
     *
     * @param from the clipboard block type
     * @param to the world block type
     * @param count how often it occurred
     */
    public record TypePair(BlockType from, BlockType to, long count) {
    }

    private static final Set<String> AIR_IDS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private long unchanged;
    private long changed;
    private long removed;
    private long added;
    private @Nullable BlockVector3 min;
    private @Nullable BlockVector3 max;
    private final Map<List<BlockType>, Long> pairs = new HashMap<>();
    private final List<Difference> recorded = new ArrayList<>();
    private boolean recordTruncated;

    private WorldDiff() {
    }

    /**
     * Whether the block is a kind of air. Uses the block id so that it also
     * works without block materials.
     *
     * @param state the block
     * @return true if air
     */
    public static boolean isAir(BlockState state) {
        BlockType type = state.getBlockType();
        if (AIR_IDS.contains(type.id())) {
            return true;
        }
        try {
            BlockMaterial material = type.getMaterial();
            return material != null && material.isAir();
        } catch (RuntimeException _) {
            return false;
        }
    }

    /**
     * Classify a pair of blocks.
     *
     * @param expected the clipboard block
     * @param actual the world block
     * @return the kind of difference
     */
    public static Kind classify(BlockState expected, BlockState actual) {
        if (expected.equals(actual)) {
            return Kind.UNCHANGED;
        }
        boolean expectedAir = isAir(expected);
        boolean actualAir = isAir(actual);
        if (expectedAir && actualAir) {
            return Kind.UNCHANGED;
        }
        if (actualAir) {
            return Kind.REMOVED;
        }
        if (expectedAir) {
            return Kind.ADDED;
        }
        return Kind.CHANGED;
    }

    /**
     * Compute the difference between a clipboard (at its original coordinates)
     * and an extent.
     *
     * @param clipboard the clipboard
     * @param world the extent to compare against
     * @param maxRecorded maximum number of differences to keep in {@link #getRecorded()}
     * @param consumer optional consumer that receives every difference
     * @return the diff
     */
    public static WorldDiff compute(Clipboard clipboard, Extent world, int maxRecorded,
                                    @Nullable DifferenceConsumer consumer) {
        checkNotNull(clipboard);
        checkNotNull(world);
        WorldDiff diff = new WorldDiff();
        for (BlockVector3 pos : clipboard.getRegion()) {
            BaseBlock expected = clipboard.getFullBlock(pos);
            BlockState expectedState = expected.toImmutableState();
            BlockState actual = world.getBlock(pos);
            Kind kind = classify(expectedState, actual);
            if (kind == Kind.UNCHANGED) {
                diff.unchanged++;
                continue;
            }
            switch (kind) {
                case CHANGED -> diff.changed++;
                case REMOVED -> diff.removed++;
                case ADDED -> diff.added++;
                default -> {
                }
            }
            diff.min = diff.min == null ? pos : diff.min.getMinimum(pos);
            diff.max = diff.max == null ? pos : diff.max.getMaximum(pos);
            diff.pairs.merge(List.of(expectedState.getBlockType(), actual.getBlockType()), 1L, Long::sum);
            if (diff.recorded.size() < maxRecorded) {
                diff.recorded.add(new Difference(pos, kind));
            } else {
                diff.recordTruncated = true;
            }
            if (consumer != null) {
                consumer.accept(pos, kind, expected, actual);
            }
        }
        return diff;
    }

    public long getUnchanged() {
        return unchanged;
    }

    public long getChanged() {
        return changed;
    }

    public long getRemoved() {
        return removed;
    }

    public long getAdded() {
        return added;
    }

    public long getTotalDifferences() {
        return changed + removed + added;
    }

    /**
     * Minimum corner of the bounding box of all differences.
     *
     * @return the corner, or null if there is no difference
     */
    public @Nullable BlockVector3 getMinimum() {
        return min;
    }

    /**
     * Maximum corner of the bounding box of all differences.
     *
     * @return the corner, or null if there is no difference
     */
    public @Nullable BlockVector3 getMaximum() {
        return max;
    }

    public List<Difference> getRecorded() {
        return recorded;
    }

    public boolean isRecordTruncated() {
        return recordTruncated;
    }

    /**
     * Get the most frequent {@code clipboard type -> world type} pairs.
     *
     * @param limit how many to return
     * @return the pairs, most frequent first
     */
    public List<TypePair> getTopPairs(int limit) {
        return pairs.entrySet().stream()
            .map(e -> new TypePair(e.getKey().get(0), e.getKey().get(1), e.getValue()))
            .sorted(Comparator.comparingLong(TypePair::count).reversed()
                .thenComparing(p -> p.from().id())
                .thenComparing(p -> p.to().id()))
            .limit(limit)
            .toList();
    }
}

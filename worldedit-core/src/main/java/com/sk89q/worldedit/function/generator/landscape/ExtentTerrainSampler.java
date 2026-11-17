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

package com.sk89q.worldedit.function.generator.landscape;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link TerrainSampler} that reads an {@link Extent}, caching each column.
 *
 * <p>The surface of a column is the highest block between {@code minY} and {@code maxY} that is
 * either ground (a solid block that is not part of a tree) or water. If no such block exists,
 * {@code minY} is reported.</p>
 */
public class ExtentTerrainSampler implements TerrainSampler {

    private final Extent extent;
    private final int minY;
    private final int maxY;
    private final Map<BlockVector2, Integer> cache = new HashMap<>();

    /**
     * Create a new sampler.
     *
     * @param extent the extent to read
     * @param minY the lowest Y to consider
     * @param maxY the highest Y to scan from
     */
    public ExtentTerrainSampler(Extent extent, int minY, int maxY) {
        this.extent = extent;
        this.minY = minY;
        this.maxY = maxY;
    }

    /**
     * Gets whether a block counts as ground: solid, but not leaves, logs or similar plant parts.
     *
     * @param state the block
     * @return true if ground
     */
    public static boolean isGround(BlockState state) {
        BlockType type = state.getBlockType();
        if (!type.getMaterial().isSolid()) {
            return false;
        }
        String id = type.id();
        return !(id.endsWith("_leaves") || id.endsWith("_log") || id.endsWith("_wood")
            || id.endsWith("_stem") || id.endsWith("_hyphae") || id.endsWith("mushroom_block")
            || id.endsWith("_roots") || id.equals("minecraft:bamboo") || id.equals("minecraft:cactus"));
    }

    /**
     * Gets whether a block is water.
     *
     * @param state the block
     * @return true if water
     */
    public static boolean isWater(BlockState state) {
        return state.getBlockType() == BlockTypes.WATER;
    }

    private int encoded(int x, int z) {
        BlockVector2 key = BlockVector2.at(x, z);
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        int result = minY << 1;
        for (int y = maxY; y >= minY; y--) {
            BlockState state = extent.getBlock(BlockVector3.at(x, y, z));
            if (isWater(state)) {
                result = (y << 1) | 1;
                break;
            }
            if (isGround(state)) {
                result = y << 1;
                break;
            }
        }
        cache.put(key, result);
        return result;
    }

    @Override
    public int surfaceY(int x, int z) {
        return encoded(x, z) >> 1;
    }

    @Override
    public boolean isWater(int x, int z) {
        return (encoded(x, z) & 1) != 0;
    }

    /**
     * Gets the highest ground block at or below {@code fromY}, skipping water and anything else.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @param fromY the Y to start at
     * @param limit the lowest Y to check
     * @return the Y of the ground block, or {@code limit - 1} if none was found
     */
    public int groundBelow(int x, int z, int fromY, int limit) {
        for (int y = fromY; y >= limit; y--) {
            if (isGround(extent.getBlock(BlockVector3.at(x, y, z)))) {
                return y;
            }
        }
        return limit - 1;
    }
}

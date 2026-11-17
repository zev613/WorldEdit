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

/**
 * A view of the terrain surface, column by column.
 *
 * <p>Implementations are queried with world X/Z coordinates. They are kept free of any
 * world access so the landscape algorithms can be unit tested with plain arrays.</p>
 */
public interface TerrainSampler {

    /**
     * Gets the Y coordinate of the topmost surface block of a column. Water counts as surface.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @return the Y coordinate of the surface block
     */
    int surfaceY(int x, int z);

    /**
     * Gets whether the topmost surface block of a column is water.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @return true if the surface is water
     */
    boolean isWater(int x, int z);

    /**
     * Creates a sampler backed by arrays indexed {@code [x - originX][z - originZ]}.
     * Columns outside the arrays are reported as very high dry land.
     *
     * @param heights the surface heights
     * @param water the water flags, or null for no water
     * @param originX the X coordinate of index 0
     * @param originZ the Z coordinate of index 0
     * @return a sampler
     */
    static TerrainSampler ofArrays(int[][] heights, boolean[][] water, int originX, int originZ) {
        return new TerrainSampler() {
            private boolean inside(int x, int z) {
                int ix = x - originX;
                int iz = z - originZ;
                return ix >= 0 && iz >= 0 && ix < heights.length && iz < heights[ix].length;
            }

            @Override
            public int surfaceY(int x, int z) {
                if (!inside(x, z)) {
                    return Integer.MAX_VALUE / 4;
                }
                return heights[x - originX][z - originZ];
            }

            @Override
            public boolean isWater(int x, int z) {
                return water != null && inside(x, z) && water[x - originX][z - originZ];
            }
        };
    }
}

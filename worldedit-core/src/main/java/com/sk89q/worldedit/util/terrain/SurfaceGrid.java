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

package com.sk89q.worldedit.util.terrain;

import java.util.Arrays;

/**
 * A rectangular grid of surface heights for XZ columns, used to compute slopes.
 *
 * <p>Index {@code [0][0]} corresponds to the column at ({@code originX}, {@code originZ}).
 * Columns without a surface hold {@link #NO_SURFACE}.</p>
 */
public final class SurfaceGrid {

    /**
     * Marker for a column without any surface block.
     */
    public static final int NO_SURFACE = Integer.MIN_VALUE;

    private final int originX;
    private final int originZ;
    private final int[][] heights;

    /**
     * Create a new grid; all columns start without a surface.
     *
     * @param originX the X coordinate of index 0
     * @param originZ the Z coordinate of index 0
     * @param sizeX the number of columns along X
     * @param sizeZ the number of columns along Z
     */
    public SurfaceGrid(int originX, int originZ, int sizeX, int sizeZ) {
        this.originX = originX;
        this.originZ = originZ;
        this.heights = new int[sizeX][sizeZ];
        for (int[] row : heights) {
            Arrays.fill(row, NO_SURFACE);
        }
    }

    /**
     * Set the surface height of a column.
     *
     * @param x the world X
     * @param z the world Z
     * @param y the surface Y, or {@link #NO_SURFACE}
     */
    public void set(int x, int z, int y) {
        heights[x - originX][z - originZ] = y;
    }

    /**
     * Get the surface height of a column.
     *
     * @param x the world X
     * @param z the world Z
     * @return the surface Y, or {@link #NO_SURFACE} if there is none or the column is outside the grid
     */
    public int get(int x, int z) {
        int ix = x - originX;
        int iz = z - originZ;
        if (ix < 0 || iz < 0 || ix >= heights.length || iz >= heights[ix].length) {
            return NO_SURFACE;
        }
        return heights[ix][iz];
    }

    /**
     * Compute the slope at a column in degrees (0 = flat, approaching 90 = vertical), using
     * the same central-difference gradient as {@link com.sk89q.worldedit.function.mask.SlopeMask}.
     * If a neighbour is missing, a one-sided difference is used; if both are missing along
     * an axis, that axis contributes no slope.
     *
     * @param x the world X
     * @param z the world Z
     * @return the slope in degrees, or 0 if the column has no surface
     */
    public double slopeDegrees(int x, int z) {
        int h = get(x, z);
        if (h == NO_SURFACE) {
            return 0;
        }
        double dx = difference(h, get(x - 1, z), get(x + 1, z));
        double dz = difference(h, get(x, z - 1), get(x, z + 1));
        return Math.toDegrees(Math.atan(Math.hypot(dx, dz)));
    }

    private static double difference(int center, int minus, int plus) {
        boolean hasMinus = minus != NO_SURFACE;
        boolean hasPlus = plus != NO_SURFACE;
        if (hasMinus && hasPlus) {
            return (plus - minus) / 2.0;
        } else if (hasPlus) {
            return plus - center;
        } else if (hasMinus) {
            return center - minus;
        }
        return 0;
    }
}

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

import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.noise.PerlinNoise;

/**
 * Deterministic 2D Perlin noise in [0, 1] for XZ columns.
 */
public final class ColumnNoise {

    /**
     * The fixed seed, so that the same coordinates always give the same value.
     */
    public static final int SEED = 0x5EED;

    private final PerlinNoise noise = new PerlinNoise();
    private final double scale;

    /**
     * Create a new noise source.
     *
     * @param scale the size of noise features in blocks (larger = smoother), must be positive
     */
    public ColumnNoise(double scale) {
        if (!(scale > 0) || Double.isInfinite(scale)) {
            throw new IllegalArgumentException("Noise scale must be a positive number");
        }
        this.scale = scale;
        noise.setSeed(SEED);
    }

    /**
     * Get the noise value of a column.
     *
     * @param x the world X
     * @param z the world Z
     * @return a value in [0, 1]
     */
    public double at(int x, int z) {
        // Offset by half a block so integer coordinates do not land on Perlin lattice points (always 0.5)
        return noise.noise(Vector2.at((x + 0.5) / scale, (z + 0.5) / scale));
    }
}

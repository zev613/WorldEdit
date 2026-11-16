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

package com.sk89q.worldedit.function.pattern;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A pattern that partitions 3D space into organic Voronoi cellular clusters of given scale.
 */
public class VoronoiPattern extends AbstractPattern {

    private final double scale;
    private final List<Pattern> patterns;

    public VoronoiPattern(double scale, List<Pattern> patterns) {
        checkNotNull(patterns, "patterns cannot be null");
        checkArgument(!patterns.isEmpty(), "patterns cannot be empty");
        this.scale = Math.max(1.0, scale);
        this.patterns = patterns;
    }

    @Override
    public BaseBlock applyBlock(BlockVector3 position) {
        if (patterns.size() == 1) {
            return patterns.get(0).applyBlock(position);
        }

        double x = position.x();
        double y = position.y();
        double z = position.z();

        int cellX = (int) Math.floor(x / scale);
        int cellY = (int) Math.floor(y / scale);
        int cellZ = (int) Math.floor(z / scale);

        double minDistSq = Double.MAX_VALUE;
        int closestCellHash = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int cx = cellX + dx;
                    int cy = cellY + dy;
                    int cz = cellZ + dz;

                    double fx = (cx + hashToDouble(cx, cy, cz, 0)) * scale;
                    double fy = (cy + hashToDouble(cx, cy, cz, 1)) * scale;
                    double fz = (cz + hashToDouble(cx, cy, cz, 2)) * scale;

                    double distSq = (x - fx) * (x - fx) + (y - fy) * (y - fy) + (z - fz) * (z - fz);
                    if (distSq < minDistSq) {
                        minDistSq = distSq;
                        closestCellHash = cellHash(cx, cy, cz);
                    }
                }
            }
        }

        int chosenIndex = Math.floorMod(closestCellHash, patterns.size());
        return patterns.get(chosenIndex).applyBlock(position);
    }

    private static int cellHash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 326648991;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static double hashToDouble(int x, int y, int z, int seed) {
        int h = (cellHash(x + seed * 31, y - seed * 17, z + seed * 53) & 0x7fffffff);
        return h / (double) 0x7fffffff;
    }
}

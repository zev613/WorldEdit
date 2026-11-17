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

import com.sk89q.worldedit.math.BlockVector2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rasterizes a band of columns around a polyline of columns.
 */
public final class Corridor {

    /**
     * A column of the band.
     *
     * @param nearestIndex the index of the nearest polyline point
     * @param distance the horizontal distance from the polyline
     */
    public record Column(int nearestIndex, double distance) {
    }

    private Corridor() {
    }

    /**
     * Gets the columns within {@code radius} of the polyline through {@code points}.
     *
     * @param points the {@code {x, z}} points
     * @param radius the maximum distance from the polyline
     * @return the columns, in a deterministic order
     */
    public static Map<BlockVector2, Column> rasterize(List<int[]> points, double radius) {
        Map<BlockVector2, Column> columns = new LinkedHashMap<>();
        if (points.isEmpty()) {
            return columns;
        }
        int reach = (int) Math.ceil(radius);
        int segments = Math.max(1, points.size() - 1);
        for (int i = 0; i < segments; i++) {
            int[] a = points.get(i);
            int[] b = points.get(Math.min(i + 1, points.size() - 1));
            int minX = Math.min(a[0], b[0]) - reach;
            int maxX = Math.max(a[0], b[0]) + reach;
            int minZ = Math.min(a[1], b[1]) - reach;
            int maxZ = Math.max(a[1], b[1]) + reach;
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double lengthSq = dx * dx + dz * dz;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double t = lengthSq == 0 ? 0 : ((x - a[0]) * dx + (z - a[1]) * dz) / lengthSq;
                    t = Math.max(0, Math.min(1, t));
                    double px = a[0] + t * dx - x;
                    double pz = a[1] + t * dz - z;
                    double distance = Math.sqrt(px * px + pz * pz);
                    if (distance > radius) {
                        continue;
                    }
                    int nearest = t <= 0.5 ? i : Math.min(i + 1, points.size() - 1);
                    BlockVector2 key = BlockVector2.at(x, z);
                    Column existing = columns.get(key);
                    if (existing == null || distance < existing.distance() - 1e-9) {
                        columns.put(key, new Column(nearest, distance));
                    }
                }
            }
        }
        return columns;
    }
}

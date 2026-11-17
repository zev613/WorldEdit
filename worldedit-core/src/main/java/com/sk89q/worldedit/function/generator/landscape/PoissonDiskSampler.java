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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Picks points from a set of candidate columns by dart throwing, so that no two picked points
 * are closer than a minimum spacing (a Poisson-disk-like distribution).
 */
public final class PoissonDiskSampler {

    private PoissonDiskSampler() {
    }

    /**
     * Sample points.
     *
     * @param candidates the {@code {x, z}} candidate columns
     * @param count the number of points wanted
     * @param minSpacing the minimum distance between points (0 allows any, but never the same column twice)
     * @param random the random source
     * @param attemptsPerPoint the number of darts thrown per wanted point before giving up
     * @return up to {@code count} points, in the order they were picked
     */
    public static List<int[]> sample(List<int[]> candidates, int count, double minSpacing, Random random,
                                     int attemptsPerPoint) {
        return sample(candidates, count, minSpacing, random, attemptsPerPoint, _ -> true);
    }

    /**
     * Sample points, only accepting candidates that pass a filter. The filter is only consulted
     * for candidates that satisfy the spacing, so it may be expensive.
     *
     * @param candidates the {@code {x, z}} candidate columns
     * @param count the number of points wanted
     * @param minSpacing the minimum distance between points (0 allows any, but never the same column twice)
     * @param random the random source
     * @param attemptsPerPoint the number of darts thrown per wanted point before giving up
     * @param filter the filter
     * @return up to {@code count} points, in the order they were picked
     */
    public static List<int[]> sample(List<int[]> candidates, int count, double minSpacing, Random random,
                                     int attemptsPerPoint, Predicate<int[]> filter) {
        checkArgument(count >= 0, "count must be >= 0");
        checkArgument(minSpacing >= 0, "minSpacing must be >= 0");
        checkArgument(attemptsPerPoint >= 1, "attemptsPerPoint must be >= 1");
        List<int[]> picked = new ArrayList<>();
        if (candidates.isEmpty() || count == 0) {
            return picked;
        }
        double spacing = Math.max(minSpacing, 0.5);
        double spacingSq = spacing * spacing;
        double cellSize = spacing;
        Map<Long, List<int[]>> grid = new HashMap<>();
        long attempts = (long) attemptsPerPoint * count;
        for (long attempt = 0; attempt < attempts && picked.size() < count; attempt++) {
            int[] candidate = candidates.get(random.nextInt(candidates.size()));
            int cellX = (int) Math.floor(candidate[0] / cellSize);
            int cellZ = (int) Math.floor(candidate[1] / cellSize);
            boolean ok = true;
            for (int gx = cellX - 1; gx <= cellX + 1 && ok; gx++) {
                for (int gz = cellZ - 1; gz <= cellZ + 1 && ok; gz++) {
                    List<int[]> cell = grid.get(key(gx, gz));
                    if (cell == null) {
                        continue;
                    }
                    for (int[] other : cell) {
                        double dx = other[0] - candidate[0];
                        double dz = other[1] - candidate[1];
                        if (dx * dx + dz * dz < spacingSq) {
                            ok = false;
                            break;
                        }
                    }
                }
            }
            if (ok && filter.test(candidate)) {
                picked.add(candidate);
                grid.computeIfAbsent(key(cellX, cellZ), _ -> new ArrayList<>()).add(candidate);
            }
        }
        return picked;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}

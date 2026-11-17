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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Traces the course of a river by walking downhill over a terrain surface.
 *
 * <p>At each step the steepest descending neighbor (of the 8 neighbors) is taken; ties are
 * broken in favour of continuing in the current direction. When no neighbor is lower, a
 * bounded breadth-first search over the flat area (again preferring the current direction)
 * looks for the nearest lower column or water. If none is found the river is stuck in a pit.</p>
 */
public final class RiverTracer {

    /**
     * Why the walk stopped.
     */
    public enum StopReason {
        /** Reached existing water; the last point is the water column. */
        WATER,
        /** Reached the maximum length. */
        MAX_LENGTH,
        /** Got stuck in a pit or on a large flat area. */
        PIT
    }

    /**
     * The traced course.
     *
     * @param points the {@code {x, z}} columns, starting with the start column
     * @param reason why the walk stopped
     */
    public record Result(List<int[]> points, StopReason reason) {
    }

    private static final int[][] NEIGHBORS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };

    private final TerrainSampler terrain;
    private final int maxLength;
    private final int maxFlatSearch;

    /**
     * Create a new tracer.
     *
     * @param terrain the terrain
     * @param maxLength the maximum number of columns in the course
     * @param maxFlatSearch the maximum number of columns explored to get off a flat area
     */
    public RiverTracer(TerrainSampler terrain, int maxLength, int maxFlatSearch) {
        checkArgument(maxLength >= 1, "maxLength must be >= 1");
        checkArgument(maxFlatSearch >= 0, "maxFlatSearch must be >= 0");
        this.terrain = terrain;
        this.maxLength = maxLength;
        this.maxFlatSearch = maxFlatSearch;
    }

    /**
     * Trace a river from the given column.
     *
     * @param startX start X
     * @param startZ start Z
     * @return the course
     */
    public Result trace(int startX, int startZ) {
        List<int[]> points = new ArrayList<>();
        Set<BlockVector2> visited = new HashSet<>();
        int[] current = {startX, startZ};
        points.add(current);
        visited.add(BlockVector2.at(startX, startZ));
        int[] direction = {0, 0};

        while (points.size() < maxLength) {
            int cx = current[0];
            int cz = current[1];
            int ch = terrain.surfaceY(cx, cz);

            int[] best = null;
            double bestSlope = 0;
            double bestAlign = 0;
            for (int[] offset : orderedNeighbors(direction)) {
                int nx = cx + offset[0];
                int nz = cz + offset[1];
                if (visited.contains(BlockVector2.at(nx, nz))) {
                    continue;
                }
                if (terrain.isWater(nx, nz)) {
                    points.add(new int[] {nx, nz});
                    return new Result(points, StopReason.WATER);
                }
                int nh = terrain.surfaceY(nx, nz);
                if (nh >= ch) {
                    continue;
                }
                double distance = offset[0] != 0 && offset[1] != 0 ? Math.sqrt(2) : 1;
                double slope = (nh - ch) / distance;
                double align = alignment(direction, offset);
                if (best == null || slope < bestSlope - 1e-9
                    || (Math.abs(slope - bestSlope) <= 1e-9 && align > bestAlign + 1e-9)) {
                    best = offset;
                    bestSlope = slope;
                    bestAlign = align;
                }
            }

            if (best != null) {
                current = new int[] {cx + best[0], cz + best[1]};
                direction = best;
                points.add(current);
                visited.add(BlockVector2.at(current[0], current[1]));
                continue;
            }

            List<int[]> escape = escapeFlat(cx, cz, ch, direction, visited);
            if (escape == null) {
                return new Result(points, StopReason.PIT);
            }
            for (int[] step : escape) {
                if (points.size() >= maxLength) {
                    return new Result(points, StopReason.MAX_LENGTH);
                }
                direction = new int[] {step[0] - current[0], step[1] - current[1]};
                current = step;
                points.add(current);
                visited.add(BlockVector2.at(current[0], current[1]));
                if (terrain.isWater(current[0], current[1])) {
                    return new Result(points, StopReason.WATER);
                }
            }
        }
        return new Result(points, StopReason.MAX_LENGTH);
    }

    /**
     * Breadth-first search across columns no higher than {@code height} for a column that is
     * lower or water.
     *
     * @return the steps after the start column up to and including the found column, or null
     */
    private List<int[]> escapeFlat(int startX, int startZ, int height, int[] direction, Set<BlockVector2> visited) {
        if (maxFlatSearch == 0) {
            return null;
        }
        Map<BlockVector2, BlockVector2> parent = new HashMap<>();
        ArrayDeque<BlockVector2> queue = new ArrayDeque<>();
        BlockVector2 start = BlockVector2.at(startX, startZ);
        parent.put(start, start);
        queue.add(start);
        int explored = 0;
        List<int[]> order = orderedNeighbors(direction);
        while (!queue.isEmpty() && explored < maxFlatSearch) {
            BlockVector2 node = queue.poll();
            explored++;
            for (int[] offset : order) {
                BlockVector2 next = BlockVector2.at(node.x() + offset[0], node.z() + offset[1]);
                if (parent.containsKey(next) || visited.contains(next)) {
                    continue;
                }
                boolean water = terrain.isWater(next.x(), next.z());
                int nh = terrain.surfaceY(next.x(), next.z());
                if (!water && nh > height) {
                    continue;
                }
                parent.put(next, node);
                if (water || nh < height) {
                    List<int[]> steps = new ArrayList<>();
                    BlockVector2 step = next;
                    while (!step.equals(start)) {
                        steps.add(new int[] {step.x(), step.z()});
                        step = parent.get(step);
                    }
                    Collections.reverse(steps);
                    return steps;
                }
                queue.add(next);
            }
        }
        return null;
    }

    private static double alignment(int[] direction, int[] offset) {
        if (direction[0] == 0 && direction[1] == 0) {
            return 0;
        }
        double dot = direction[0] * offset[0] + direction[1] * offset[1];
        return dot / (Math.hypot(direction[0], direction[1]) * Math.hypot(offset[0], offset[1]));
    }

    private static List<int[]> orderedNeighbors(int[] direction) {
        List<int[]> list = new ArrayList<>(Arrays.asList(NEIGHBORS));
        list.sort(Comparator.comparingDouble((int[] o) -> -alignment(direction, o)));
        return list;
    }
}

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A* search over a surface heightmap that prefers gentle, short routes.
 *
 * <p>The search is confined to a rectangular area. The cost of a step is its horizontal length,
 * plus a penalty proportional to the height change, plus a much larger penalty for the part of
 * the height change that exceeds the maximum slope (such steps are only possible by grading
 * the terrain), plus an extra penalty for crossing water (which needs a bridge).</p>
 */
public final class HeightmapPathFinder {

    private static final double SQRT2 = Math.sqrt(2);
    private static final int[][] NEIGHBORS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private final TerrainSampler terrain;
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final double maxSlope;
    private double heightWeight = 1.5;
    private double steepWeight = 12.0;
    private double waterWeight = 2.0;
    private final int[] heightCache;

    /**
     * Create a new path finder.
     *
     * @param terrain the terrain
     * @param minX minimum X of the search area (inclusive)
     * @param minZ minimum Z of the search area (inclusive)
     * @param maxX maximum X of the search area (inclusive)
     * @param maxZ maximum Z of the search area (inclusive)
     * @param maxSlope the maximum rise per block of horizontal distance that does not need grading
     */
    public HeightmapPathFinder(TerrainSampler terrain, int minX, int minZ, int maxX, int maxZ, double maxSlope) {
        checkArgument(maxX >= minX && maxZ >= minZ, "empty search area");
        checkArgument(maxSlope > 0, "maxSlope must be positive");
        this.terrain = terrain;
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = maxX - minX + 1;
        this.sizeZ = maxZ - minZ + 1;
        this.maxSlope = maxSlope;
        this.heightCache = new int[sizeX * sizeZ];
        Arrays.fill(heightCache, UNKNOWN);
    }

    public void setHeightWeight(double heightWeight) {
        this.heightWeight = heightWeight;
    }

    public void setSteepWeight(double steepWeight) {
        this.steepWeight = steepWeight;
    }

    public void setWaterWeight(double waterWeight) {
        this.waterWeight = waterWeight;
    }

    /**
     * The height the path would have on a column: the surface, or one above water.
     *
     * @param terrain the terrain
     * @param x the X coordinate
     * @param z the Z coordinate
     * @return the walking-surface block height
     */
    public static int pathHeight(TerrainSampler terrain, int x, int z) {
        int y = terrain.surfaceY(x, z);
        return terrain.isWater(x, z) ? y + 1 : y;
    }

    private int height(int index) {
        int h = heightCache[index];
        if (h == UNKNOWN) {
            h = pathHeight(terrain, minX + index / sizeZ, minZ + index % sizeZ);
            heightCache[index] = h;
        }
        return h;
    }

    /**
     * Gets the cost of a single step.
     *
     * @param distance the horizontal length of the step
     * @param rise the absolute height change
     * @param water whether the target column is water
     * @return the cost
     */
    double stepCost(double distance, int rise, boolean water) {
        double cost = distance + heightWeight * rise;
        double excess = rise - maxSlope * distance;
        if (excess > 0) {
            cost += steepWeight * excess;
        }
        if (water) {
            cost += waterWeight * distance;
        }
        return cost;
    }

    private static double octile(int dx, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        return Math.max(ax, az) + (SQRT2 - 1) * Math.min(ax, az);
    }

    /**
     * Find a path between two columns.
     *
     * @param startX start X
     * @param startZ start Z
     * @param goalX goal X
     * @param goalZ goal Z
     * @return the list of {@code {x, z}} columns from start to goal (inclusive), or null if none was found
     */
    public List<int[]> findPath(int startX, int startZ, int goalX, int goalZ) {
        checkArgument(inArea(startX, startZ), "start outside search area");
        checkArgument(inArea(goalX, goalZ), "goal outside search area");
        int total = sizeX * sizeZ;
        int start = index(startX, startZ);
        int goal = index(goalX, goalZ);
        double[] gScore = new double[total];
        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        int[] cameFrom = new int[total];
        Arrays.fill(cameFrom, -1);
        boolean[] closed = new boolean[total];

        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        gScore[start] = 0;
        open.add(new double[] {octile(goalX - startX, goalZ - startZ), start});

        while (!open.isEmpty()) {
            double[] entry = open.poll();
            int current = (int) entry[1];
            if (closed[current]) {
                continue;
            }
            if (current == goal) {
                return reconstruct(cameFrom, goal);
            }
            closed[current] = true;
            int cx = current / sizeZ;
            int cz = current % sizeZ;
            int ch = height(current);
            for (int[] offset : NEIGHBORS) {
                int nx = cx + offset[0];
                int nz = cz + offset[1];
                if (nx < 0 || nz < 0 || nx >= sizeX || nz >= sizeZ) {
                    continue;
                }
                int neighbor = nx * sizeZ + nz;
                if (closed[neighbor]) {
                    continue;
                }
                double distance = offset[0] != 0 && offset[1] != 0 ? SQRT2 : 1;
                int rise = Math.abs(height(neighbor) - ch);
                boolean water = terrain.isWater(minX + nx, minZ + nz);
                double tentative = gScore[current] + stepCost(distance, rise, water);
                if (tentative < gScore[neighbor]) {
                    gScore[neighbor] = tentative;
                    cameFrom[neighbor] = current;
                    double f = tentative + octile(goalX - minX - nx, goalZ - minZ - nz);
                    open.add(new double[] {f, neighbor});
                }
            }
        }
        return null;
    }

    private List<int[]> reconstruct(int[] cameFrom, int goal) {
        List<int[]> path = new ArrayList<>();
        int current = goal;
        while (current != -1) {
            path.add(new int[] {minX + current / sizeZ, minZ + current % sizeZ});
            current = cameFrom[current];
        }
        Collections.reverse(path);
        return path;
    }

    private boolean inArea(int x, int z) {
        return x >= minX && z >= minZ && x < minX + sizeX && z < minZ + sizeZ;
    }

    private int index(int x, int z) {
        return (x - minX) * sizeZ + (z - minZ);
    }
}

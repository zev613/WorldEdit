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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Plans the cross-section of a river channel along a traced course.
 *
 * <p>The water level is one block below the bank at the start and never rises downstream. The
 * cross-section is U-shaped (elliptical): full depth on the centre line, shallower towards
 * the banks. Where a bank is lower than the water level, a levee is planned to hold the water.</p>
 */
public final class RiverChannel {

    /**
     * A carved column. From bottom to top: bed blocks from {@code bedBottomY} to {@code bedY},
     * water from {@code bedY + 1} to {@code waterTopY}, air from {@code waterTopY + 1} to
     * {@code clearTopY}.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @param bedBottomY the lowest bed block
     * @param bedY the top bed block
     * @param waterTopY the top water block
     * @param clearTopY the highest block to clear (may be below {@code waterTopY + 1})
     */
    public record ChannelColumn(int x, int z, int bedBottomY, int bedY, int waterTopY, int clearTopY) {
    }

    /**
     * A levee column, filled with bed blocks from {@code fromY} to {@code toY}.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @param fromY the lowest block
     * @param toY the highest block
     */
    public record LeveeColumn(int x, int z, int fromY, int toY) {
    }

    /**
     * The plan.
     *
     * @param channel the carved columns
     * @param levees the levee columns
     */
    public record Plan(List<ChannelColumn> channel, List<LeveeColumn> levees) {
    }

    private RiverChannel() {
    }

    /**
     * Gets the water levels along the course.
     *
     * @param terrain the terrain
     * @param points the course
     * @return the top water block height for each point
     */
    public static int[] waterLevels(TerrainSampler terrain, List<int[]> points) {
        int[] levels = new int[points.size()];
        int level = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            int surface = terrain.surfaceY(p[0], p[1]);
            int candidate = terrain.isWater(p[0], p[1]) ? surface : surface - 1;
            level = Math.min(level, candidate);
            levels[i] = level;
        }
        return levels;
    }

    /**
     * Gets the water depth at a distance from the centre line.
     *
     * @param depth the depth on the centre line
     * @param distance the distance from the centre line
     * @param radius the half width of the channel
     * @return the water depth, at least 1
     */
    public static int depthAt(int depth, double distance, double radius) {
        double f = distance / (radius + 0.5);
        double shape = Math.sqrt(Math.max(0, 1 - f * f));
        return Math.max(1, (int) Math.round(depth * shape));
    }

    /**
     * Plan the channel.
     *
     * @param terrain the terrain
     * @param points the course
     * @param width the channel width
     * @param depth the channel depth on the centre line
     * @param pond whether to widen the end of the course into a pond
     * @return the plan
     */
    public static Plan plan(TerrainSampler terrain, List<int[]> points, int width, int depth, boolean pond) {
        checkArgument(width >= 1, "width must be >= 1");
        checkArgument(depth >= 1, "depth must be >= 1");
        int[] levels = waterLevels(terrain, points);
        double radius = (width - 1) / 2.0 + 0.3;

        // column -> {level, water depth}
        Map<BlockVector2, int[]> carve = new LinkedHashMap<>();
        Map<BlockVector2, Corridor.Column> band = Corridor.rasterize(points, radius);
        for (Map.Entry<BlockVector2, Corridor.Column> entry : band.entrySet()) {
            Corridor.Column column = entry.getValue();
            int level = levels[column.nearestIndex()];
            carve.put(entry.getKey(), new int[] {level, depthAt(depth, column.distance(), radius)});
        }

        double pondRadius = Math.max(radius + 1.5, 2.5);
        if (pond && !points.isEmpty()) {
            int[] end = points.get(points.size() - 1);
            int level = levels[levels.length - 1];
            int reach = (int) Math.ceil(pondRadius);
            for (int x = end[0] - reach; x <= end[0] + reach; x++) {
                for (int z = end[1] - reach; z <= end[1] + reach; z++) {
                    double distance = Math.hypot(x - end[0], z - end[1]);
                    if (distance > pondRadius) {
                        continue;
                    }
                    int wd = depthAt(depth, distance, pondRadius);
                    BlockVector2 key = BlockVector2.at(x, z);
                    int[] existing = carve.get(key);
                    if (existing == null || existing[0] - existing[1] > level - wd) {
                        carve.put(key, new int[] {level, wd});
                    }
                }
            }
        }

        List<ChannelColumn> channel = new ArrayList<>();
        for (Map.Entry<BlockVector2, int[]> entry : carve.entrySet()) {
            BlockVector2 key = entry.getKey();
            if (terrain.isWater(key.x(), key.z())) {
                continue;
            }
            int waterTop = entry.getValue()[0];
            int bed = waterTop - entry.getValue()[1];
            int surface = terrain.surfaceY(key.x(), key.z());
            int bedBottom = Math.min(bed, surface + 1);
            channel.add(new ChannelColumn(key.x(), key.z(), bedBottom, bed, waterTop, Math.max(surface, waterTop)));
        }

        List<LeveeColumn> levees = new ArrayList<>();
        Map<BlockVector2, Corridor.Column> outer = Corridor.rasterize(points, radius + 1.5);
        for (Map.Entry<BlockVector2, Corridor.Column> entry : outer.entrySet()) {
            addLevee(terrain, carve, levees, entry.getKey(), levels[entry.getValue().nearestIndex()]);
        }
        if (pond && !points.isEmpty()) {
            int[] end = points.get(points.size() - 1);
            int level = levels[levels.length - 1];
            double outerRadius = pondRadius + 1.5;
            int reach = (int) Math.ceil(outerRadius);
            for (int x = end[0] - reach; x <= end[0] + reach; x++) {
                for (int z = end[1] - reach; z <= end[1] + reach; z++) {
                    if (Math.hypot(x - end[0], z - end[1]) <= outerRadius) {
                        addLevee(terrain, carve, levees, BlockVector2.at(x, z), level);
                    }
                }
            }
        }
        return new Plan(channel, levees);
    }

    private static void addLevee(TerrainSampler terrain, Map<BlockVector2, int[]> carve, List<LeveeColumn> levees,
                                 BlockVector2 key, int level) {
        if (carve.containsKey(key) || terrain.isWater(key.x(), key.z())) {
            return;
        }
        int surface = terrain.surfaceY(key.x(), key.z());
        if (surface < level) {
            carve.put(key, new int[] {Integer.MIN_VALUE, 0});
            levees.add(new LeveeColumn(key.x(), key.z(), surface + 1, level));
        }
    }
}

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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.sk89q.worldedit.function.generator.landscape.HeightmapPathFinderTest.flat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RiverTest {

    private static void assertConnected(List<int[]> path) {
        for (int i = 1; i < path.size(); i++) {
            int dx = Math.abs(path.get(i)[0] - path.get(i - 1)[0]);
            int dz = Math.abs(path.get(i)[1] - path.get(i - 1)[1]);
            assertTrue(dx <= 1 && dz <= 1 && dx + dz > 0, "steps must be to a neighbouring column");
        }
    }

    @Test
    void followsASlopeDownToWater() {
        int[][] heights = new int[40][9];
        boolean[][] water = new boolean[40][9];
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 9; z++) {
                // valley along z = 4, falling towards +x
                heights[x][z] = 100 - x + Math.abs(z - 4) * 2;
            }
        }
        for (int z = 0; z < 9; z++) {
            water[35][z] = true;
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, water, 0, 0);
        RiverTracer.Result result = new RiverTracer(terrain, 256, 100).trace(0, 4);
        assertEquals(RiverTracer.StopReason.WATER, result.reason());
        assertConnected(result.points());
        assertEquals(35, result.points().getLast()[0]);
        for (int[] p : result.points()) {
            assertEquals(4, p[1], "should stay in the valley");
        }
    }

    @Test
    void stopsAtMaxLength() {
        int[][] heights = new int[100][3];
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 3; z++) {
                heights[x][z] = 200 - x;
            }
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        RiverTracer.Result result = new RiverTracer(terrain, 10, 100).trace(0, 1);
        assertEquals(RiverTracer.StopReason.MAX_LENGTH, result.reason());
        assertEquals(10, result.points().size());
    }

    @Test
    void crossesAFlatInTheCurrentDirection() {
        int[][] heights = new int[30][11];
        for (int x = 0; x < 30; x++) {
            for (int z = 0; z < 11; z++) {
                if (x < 5) {
                    heights[x][z] = 80 - x;
                } else if (x < 20) {
                    heights[x][z] = 75;
                } else {
                    heights[x][z] = 75 - (x - 19);
                }
            }
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        RiverTracer.Result result = new RiverTracer(terrain, 29, 1000).trace(0, 5);
        assertConnected(result.points());
        assertTrue(result.points().getLast()[0] >= 20, "should get across the flat");
        int[] last = result.points().getLast();
        assertTrue(heights[last[0]][last[1]] < 75);
    }

    @Test
    void stopsInAPit() {
        int[][] heights = flat(21, 21, 80);
        for (int x = 0; x < 21; x++) {
            for (int z = 0; z < 21; z++) {
                heights[x][z] = 60 + Math.max(Math.abs(x - 10), Math.abs(z - 10));
            }
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        RiverTracer.Result result = new RiverTracer(terrain, 256, 200).trace(0, 3);
        assertEquals(RiverTracer.StopReason.PIT, result.reason());
        int[] last = result.points().getLast();
        assertEquals(10, last[0]);
        assertEquals(10, last[1]);
    }

    @Test
    void waterLevelNeverRises() {
        int[][] heights = new int[10][1];
        int[] profile = {70, 69, 69, 71, 68, 68, 67, 69, 66, 65};
        for (int x = 0; x < 10; x++) {
            heights[x][0] = profile[x];
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        List<int[]> points = new ArrayList<>();
        for (int x = 0; x < 10; x++) {
            points.add(new int[] {x, 0});
        }
        int[] levels = RiverChannel.waterLevels(terrain, points);
        assertEquals(69, levels[0]);
        for (int i = 1; i < levels.length; i++) {
            assertTrue(levels[i] <= levels[i - 1]);
            assertTrue(levels[i] <= profile[i] - 1);
        }
    }

    @Test
    void channelHasAUShapedCrossSection() {
        assertEquals(3, RiverChannel.depthAt(3, 0, 2.3));
        assertTrue(RiverChannel.depthAt(3, 2, 2.3) < 3);
        assertTrue(RiverChannel.depthAt(3, 2, 2.3) >= 1);

        int[][] heights = flat(20, 20, 70);
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        List<int[]> points = new ArrayList<>();
        for (int x = 2; x < 18; x++) {
            points.add(new int[] {x, 10});
        }
        RiverChannel.Plan plan = RiverChannel.plan(terrain, points, 5, 3, false);
        assertTrue(plan.levees().isEmpty());
        int centreBed = Integer.MAX_VALUE;
        int edgeBed = Integer.MIN_VALUE;
        for (RiverChannel.ChannelColumn column : plan.channel()) {
            assertEquals(69, column.waterTopY());
            assertEquals(70, column.clearTopY());
            assertTrue(column.bedY() < column.waterTopY());
            if (column.x() == 10 && column.z() == 10) {
                centreBed = column.bedY();
            }
            if (column.x() == 10 && column.z() == 12) {
                edgeBed = column.bedY();
            }
            assertTrue(Math.abs(column.z() - 10) <= 2, "channel must be 5 wide");
        }
        assertEquals(66, centreBed);
        assertTrue(edgeBed > centreBed);
    }

    @Test
    void lowBankGetsALevee() {
        int[][] heights = flat(20, 20, 70);
        for (int x = 0; x < 20; x++) {
            heights[x][13] = 65;
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        List<int[]> points = new ArrayList<>();
        for (int x = 2; x < 18; x++) {
            points.add(new int[] {x, 10});
        }
        RiverChannel.Plan plan = RiverChannel.plan(terrain, points, 5, 3, false);
        assertFalse(plan.levees().isEmpty());
        for (RiverChannel.LeveeColumn levee : plan.levees()) {
            assertEquals(13, levee.z());
            assertEquals(66, levee.fromY());
            assertEquals(69, levee.toY());
        }
    }

    @Test
    void pondWidensTheEnd() {
        TerrainSampler terrain = TerrainSampler.ofArrays(flat(30, 30, 70), null, 0, 0);
        List<int[]> points = List.of(new int[] {15, 15}, new int[] {16, 15});
        int without = RiverChannel.plan(terrain, points, 1, 2, false).channel().size();
        int with = RiverChannel.plan(terrain, points, 1, 2, true).channel().size();
        assertTrue(with > without + 8);
    }
}

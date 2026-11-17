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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeightmapPathFinderTest {

    static int[][] flat(int sizeX, int sizeZ, int height) {
        int[][] heights = new int[sizeX][sizeZ];
        for (int[] row : heights) {
            Arrays.fill(row, height);
        }
        return heights;
    }

    private static void assertConnected(List<int[]> path) {
        for (int i = 1; i < path.size(); i++) {
            int dx = Math.abs(path.get(i)[0] - path.get(i - 1)[0]);
            int dz = Math.abs(path.get(i)[1] - path.get(i - 1)[1]);
            assertTrue(dx <= 1 && dz <= 1 && dx + dz > 0, "steps must be to a neighbouring column");
        }
    }

    @Test
    void straightLineOnFlatTerrain() {
        TerrainSampler terrain = TerrainSampler.ofArrays(flat(20, 5, 64), null, 0, 0);
        HeightmapPathFinder finder = new HeightmapPathFinder(terrain, 0, 0, 19, 4, 1);
        List<int[]> path = finder.findPath(0, 2, 19, 2);
        assertNotNull(path);
        assertEquals(20, path.size());
        assertConnected(path);
        for (int[] p : path) {
            assertEquals(2, p[1]);
        }
    }

    @Test
    void worksWithOffsetOrigin() {
        TerrainSampler terrain = TerrainSampler.ofArrays(flat(10, 10, 64), null, -100, 50);
        HeightmapPathFinder finder = new HeightmapPathFinder(terrain, -100, 50, -91, 59, 1);
        List<int[]> path = finder.findPath(-100, 50, -91, 59);
        assertNotNull(path);
        assertEquals(10, path.size());
        assertConnected(path);
        assertEquals(-91, path.getLast()[0]);
        assertEquals(59, path.getLast()[1]);
    }

    @Test
    void goesAroundAWall() {
        int[][] heights = flat(21, 21, 64);
        // A tall wall across x = 10 with a gap at z = 20.
        for (int z = 0; z < 20; z++) {
            heights[10][z] = 90;
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        HeightmapPathFinder finder = new HeightmapPathFinder(terrain, 0, 0, 20, 20, 1);
        List<int[]> path = finder.findPath(0, 10, 20, 10);
        assertNotNull(path);
        assertConnected(path);
        for (int[] p : path) {
            assertEquals(64, heights[p[0]][p[1]], "path should avoid the wall");
        }
        assertEquals(0, path.getFirst()[0]);
        assertEquals(20, path.getLast()[0]);
    }

    @Test
    void prefersRampOverCliff() {
        int[][] heights = flat(30, 30, 64);
        // A cliff of 5 blocks from x = 15, with a ramp at z = 25..29.
        for (int x = 15; x < 30; x++) {
            for (int z = 0; z < 30; z++) {
                heights[x][z] = 69;
            }
        }
        for (int z = 25; z < 30; z++) {
            for (int x = 10; x < 15; x++) {
                heights[x][z] = 64 + (x - 9);
            }
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        HeightmapPathFinder finder = new HeightmapPathFinder(terrain, 0, 0, 29, 29, 1);
        List<int[]> path = finder.findPath(0, 15, 29, 15);
        assertNotNull(path);
        assertConnected(path);
        for (int i = 1; i < path.size(); i++) {
            int[] a = path.get(i - 1);
            int[] b = path.get(i);
            assertTrue(Math.abs(heights[b[0]][b[1]] - heights[a[0]][a[1]]) <= 1, "should not climb the cliff directly");
        }
    }

    @Test
    void steepStepIsUsedWhenThereIsNoAlternative() {
        int[][] heights = flat(10, 1, 64);
        for (int x = 5; x < 10; x++) {
            heights[x][0] = 70;
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, null, 0, 0);
        List<int[]> path = new HeightmapPathFinder(terrain, 0, 0, 9, 0, 1).findPath(0, 0, 9, 0);
        assertNotNull(path);
        assertEquals(10, path.size());
    }

    @Test
    void avoidsWaterWhenADetourIsCheap() {
        int[][] heights = flat(11, 11, 64);
        boolean[][] water = new boolean[11][11];
        for (int z = 0; z < 9; z++) {
            water[5][z] = true;
            heights[5][z] = 63;
        }
        TerrainSampler terrain = TerrainSampler.ofArrays(heights, water, 0, 0);
        assertEquals(64, HeightmapPathFinder.pathHeight(terrain, 5, 0));
        HeightmapPathFinder finder = new HeightmapPathFinder(terrain, 0, 0, 10, 10, 1);
        finder.setWaterWeight(50);
        List<int[]> path = finder.findPath(0, 2, 10, 2);
        assertNotNull(path);
        for (int[] p : path) {
            assertFalse(water[p[0]][p[1]], "should walk around the pond");
        }
    }

    @Test
    void stepCostPenalisesSteepness() {
        TerrainSampler terrain = TerrainSampler.ofArrays(flat(2, 2, 0), null, 0, 0);
        HeightmapPathFinder finder = new HeightmapPathFinder(terrain, 0, 0, 1, 1, 1);
        double level = finder.stepCost(1, 0, false);
        double gentle = finder.stepCost(1, 1, false);
        double steep = finder.stepCost(1, 2, false);
        assertEquals(1.0, level, 1e-9);
        assertTrue(steep - gentle > gentle - level, "rise beyond the maximum slope must cost more");
        assertTrue(finder.stepCost(1, 0, true) > level, "water must cost more");
    }

    @Test
    void singleColumnPath() {
        TerrainSampler terrain = TerrainSampler.ofArrays(flat(3, 3, 0), null, 0, 0);
        List<int[]> path = new HeightmapPathFinder(terrain, 0, 0, 2, 2, 1).findPath(1, 1, 1, 1);
        assertNotNull(path);
        assertEquals(1, path.size());
    }
}

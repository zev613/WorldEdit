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

package com.sk89q.worldedit.util.buildtools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightFillSolverTest {

    /**
     * A floor (y=0, opaque) with spawnable air above it (y=1), more air above.
     */
    private static LightFillSolver floor(int sizeX, int sizeZ) {
        LightFillSolver solver = new LightFillSolver(sizeX, 3, sizeZ);
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                solver.setCell(x, 0, z, LightFillSolver.OPAQUE, 0);
                solver.setSpawnable(x, 1, z, true);
            }
        }
        return solver;
    }

    @Test
    void lightDecreasesByOnePerStepInAir() {
        LightFillSolver solver = new LightFillSolver(20, 1, 1);
        solver.setCell(0, 0, 0, 1, 15);
        solver.computeLight();
        for (int x = 0; x < 16; x++) {
            assertEquals(15 - x, solver.getLight(x, 0, 0), "x=" + x);
        }
        assertEquals(0, solver.getLight(16, 0, 0));
    }

    @Test
    void opaqueBlocksStopLightButOpaqueEmittersShine() {
        LightFillSolver solver = new LightFillSolver(5, 1, 1);
        solver.setCell(0, 0, 0, LightFillSolver.OPAQUE, 15); // glowstone
        solver.setCell(2, 0, 0, LightFillSolver.OPAQUE, 0); // wall
        solver.computeLight();
        assertEquals(15, solver.getLight(0, 0, 0));
        assertEquals(14, solver.getLight(1, 0, 0));
        assertEquals(0, solver.getLight(2, 0, 0));
        assertEquals(0, solver.getLight(3, 0, 0));
    }

    @Test
    void lightGoesAroundWalls() {
        // 3 wide corridor along z with a wall at z=1 except at x=2
        LightFillSolver solver = new LightFillSolver(3, 1, 3);
        solver.setCell(0, 0, 1, LightFillSolver.OPAQUE, 0);
        solver.setCell(1, 0, 1, LightFillSolver.OPAQUE, 0);
        solver.setCell(0, 0, 0, 1, 10);
        solver.computeLight();
        // path: (0,0)->(1,0)->(2,0)->(2,1)->(2,2)->(1,2)->(0,2) = 6 steps
        assertEquals(4, solver.getLight(0, 0, 2));
    }

    @Test
    void attenuationCost() {
        LightFillSolver solver = new LightFillSolver(4, 1, 1);
        solver.setCell(0, 0, 0, 1, 15);
        solver.setCell(1, 0, 0, 2, 0); // e.g. water
        solver.computeLight();
        assertEquals(13, solver.getLight(1, 0, 0));
        assertEquals(12, solver.getLight(2, 0, 0));
    }

    @Test
    void existingLightIsRespected() {
        LightFillSolver solver = floor(9, 9);
        solver.setCell(4, 1, 4, 1, 15);
        solver.setSpawnable(4, 1, 4, false);
        solver.computeLight();
        assertEquals(0, solver.countDark(1));
        assertTrue(solver.placeLights(1, 15, 14, 100).isEmpty());
    }

    @Test
    void placesFewLightsOnOpenFloor() {
        LightFillSolver solver = floor(40, 40);
        solver.computeLight();
        assertEquals(1600, solver.countDark(1));
        List<int[]> placed = solver.placeLights(1, 15, 14, 1000);
        assertEquals(0, solver.countDark(1));
        // Each light covers a Manhattan diamond of radius 14 on the floor (~420 spots).
        assertTrue(placed.size() <= 10, "placed " + placed.size());
        for (int[] pos : placed) {
            assertEquals(1, pos[1]);
        }
        assertFullyLitFromScratch(40, 40, placed, 1, 15);
    }

    @Test
    void higherMinimumAndSmallerSpacingNeedMoreLights() {
        LightFillSolver loose = floor(30, 30);
        loose.computeLight();
        int looseCount = loose.placeLights(1, 15, 14, 1000).size();

        LightFillSolver strict = floor(30, 30);
        strict.computeLight();
        List<int[]> strictPlaced = strict.placeLights(8, 15, 7, 1000);
        assertEquals(0, strict.countDark(8));
        assertTrue(strictPlaced.size() > looseCount, strictPlaced.size() + " vs " + looseCount);
        assertFullyLitFromScratch(30, 30, strictPlaced, 8, 15);
    }

    @Test
    void separateRoomsEachGetALight() {
        // Two rooms separated by an opaque wall at x=5
        LightFillSolver solver = floor(11, 5);
        for (int z = 0; z < 5; z++) {
            for (int y = 1; y < 3; y++) {
                solver.setCell(5, y, z, LightFillSolver.OPAQUE, 0);
            }
            solver.setSpawnable(5, 1, z, false);
        }
        solver.computeLight();
        List<int[]> placed = solver.placeLights(1, 14, 14, 100);
        assertEquals(2, placed.size());
        assertEquals(0, solver.countDark(1));
    }

    @Test
    void respectsMaxLights() {
        LightFillSolver solver = floor(60, 60);
        solver.computeLight();
        assertEquals(1, solver.placeLights(1, 15, 14, 1).size());
        assertTrue(solver.countDark(1) > 0);
    }

    private static void assertFullyLitFromScratch(int sizeX, int sizeZ, List<int[]> placed, int minLight, int level) {
        LightFillSolver check = floor(sizeX, sizeZ);
        for (int[] pos : placed) {
            check.setCell(pos[0], pos[1], pos[2], 1, level);
            check.setSpawnable(pos[0], pos[1], pos[2], false);
        }
        check.computeLight();
        assertEquals(0, check.countDark(minLight));
    }
}

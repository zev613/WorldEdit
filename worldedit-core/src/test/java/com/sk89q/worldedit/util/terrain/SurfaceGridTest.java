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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceGridTest {

    private static SurfaceGrid ramp(double perBlockX) {
        SurfaceGrid grid = new SurfaceGrid(-5, 10, 5, 5);
        for (int x = -5; x < 0; x++) {
            for (int z = 10; z < 15; z++) {
                grid.set(x, z, 64 + (int) Math.round((x + 5) * perBlockX));
            }
        }
        return grid;
    }

    @Test
    void flatIsZero() {
        assertEquals(0, ramp(0).slopeDegrees(-3, 12), 1e-9);
    }

    @Test
    void oneToOneRampIsFortyFiveDegrees() {
        SurfaceGrid grid = ramp(1);
        assertEquals(45, grid.slopeDegrees(-3, 12), 1e-9);
        // edges use one-sided differences
        assertEquals(45, grid.slopeDegrees(-5, 10), 1e-9);
        assertEquals(45, grid.slopeDegrees(-1, 14), 1e-9);
    }

    @Test
    void steepRamp() {
        assertEquals(Math.toDegrees(Math.atan(3)), ramp(3).slopeDegrees(-3, 12), 1e-9);
    }

    @Test
    void diagonalGradientCombinesAxes() {
        SurfaceGrid grid = new SurfaceGrid(0, 0, 3, 3);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                grid.set(x, z, x + z);
            }
        }
        assertEquals(Math.toDegrees(Math.atan(Math.sqrt(2))), grid.slopeDegrees(1, 1), 1e-9);
    }

    @Test
    void missingColumns() {
        SurfaceGrid grid = new SurfaceGrid(0, 0, 3, 1);
        grid.set(1, 0, 70);
        assertEquals(SurfaceGrid.NO_SURFACE, grid.get(0, 0));
        assertEquals(SurfaceGrid.NO_SURFACE, grid.get(99, 99));
        assertEquals(0, grid.slopeDegrees(1, 0), 1e-9);
        assertEquals(0, grid.slopeDegrees(0, 0), 1e-9);
        grid.set(2, 0, 72);
        assertEquals(Math.toDegrees(Math.atan(2)), grid.slopeDegrees(1, 0), 1e-9);
    }

    @Test
    void noiseIsDeterministicAndInRange() {
        ColumnNoise a = new ColumnNoise(16);
        ColumnNoise b = new ColumnNoise(16);
        boolean varies = false;
        double first = a.at(0, 0);
        for (int x = -50; x < 50; x += 7) {
            for (int z = -50; z < 50; z += 5) {
                double value = a.at(x, z);
                assertTrue(value >= 0 && value <= 1, "noise out of range: " + value);
                assertEquals(value, b.at(x, z));
                if (value != first) {
                    varies = true;
                }
            }
        }
        assertTrue(varies);
        assertNotEquals(new ColumnNoise(16).at(3, 7), new ColumnNoise(4).at(3, 7));
        assertThrows(IllegalArgumentException.class, () -> new ColumnNoise(0));
        assertThrows(IllegalArgumentException.class, () -> new ColumnNoise(Double.NaN));
    }
}

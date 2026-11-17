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
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PoissonDiskSamplerTest {

    private static List<int[]> square(int size) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                candidates.add(new int[] {x, z});
            }
        }
        return candidates;
    }

    @Test
    void respectsSpacing() {
        List<int[]> points = PoissonDiskSampler.sample(square(100), 50, 8, new Random(1), 30);
        assertEquals(50, points.size());
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                double d = Math.hypot(points.get(i)[0] - points.get(j)[0], points.get(i)[1] - points.get(j)[1]);
                assertTrue(d >= 8, "points too close: " + d);
            }
        }
    }

    @Test
    void stopsWhenFull() {
        // At most 4 points with spacing 10 fit into an 11x11 square.
        List<int[]> points = PoissonDiskSampler.sample(square(11), 100, 10, new Random(2), 30);
        assertTrue(points.size() <= 4);
        assertTrue(points.size() >= 1);
    }

    @Test
    void isReproducible() {
        List<int[]> a = PoissonDiskSampler.sample(square(50), 20, 5, new Random(99), 30);
        List<int[]> b = PoissonDiskSampler.sample(square(50), 20, 5, new Random(99), 30);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i)[0], b.get(i)[0]);
            assertEquals(a.get(i)[1], b.get(i)[1]);
        }
    }

    @Test
    void zeroSpacingNeverRepeatsAColumn() {
        List<int[]> points = PoissonDiskSampler.sample(square(3), 9, 0, new Random(3), 200);
        assertEquals(9, points.size());
    }

    @Test
    void filterIsApplied() {
        List<int[]> points = PoissonDiskSampler.sample(square(40), 30, 2, new Random(4), 50, c -> c[0] < 10);
        assertTrue(!points.isEmpty());
        for (int[] p : points) {
            assertTrue(p[0] < 10);
        }
    }
}

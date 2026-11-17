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

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathGraderTest {

    private static void assertGraded(int[] graded, int maxStep) {
        for (int i = 1; i < graded.length; i++) {
            assertTrue(Math.abs(graded[i] - graded[i - 1]) <= maxStep,
                "step " + i + " is too steep: " + graded[i - 1] + " -> " + graded[i]);
        }
    }

    @Test
    void flatStaysFlat() {
        int[] raw = {64, 64, 64, 64, 64};
        assertArrayEquals(raw, PathGrader.grade(raw, 2, 1));
    }

    @Test
    void gentleSlopeIsKept() {
        int[] raw = {60, 61, 62, 63, 64, 65};
        assertArrayEquals(raw, PathGrader.grade(raw, 0, 1));
    }

    @Test
    void cliffIsTurnedIntoRamp() {
        int[] raw = {64, 64, 64, 64, 64, 70, 70, 70, 70, 70};
        int[] graded = PathGrader.grade(raw, 0, 1);
        assertGraded(graded, 1);
        // The ramp is centred on the cliff, cutting the top and filling the bottom equally.
        assertTrue(graded[0] >= 64 && graded[9] <= 70);
        assertTrue(graded[4] > 64 && graded[5] < 70);
    }

    @Test
    void spikeIsSmoothed() {
        int[] raw = {64, 64, 64, 80, 64, 64, 64};
        int[] graded = PathGrader.grade(raw, 1, 1);
        assertGraded(graded, 1);
        assertTrue(graded[3] < 70, "the spike should be cut down");
    }

    @Test
    void randomProfilesAreAlwaysGraded() {
        Random random = new Random(42);
        for (int run = 0; run < 200; run++) {
            int[] raw = new int[1 + random.nextInt(60)];
            int h = 64;
            for (int i = 0; i < raw.length; i++) {
                h += random.nextInt(9) - 4;
                raw[i] = h;
            }
            int smooth = random.nextInt(4);
            int maxStep = 1 + random.nextInt(2);
            int[] graded = PathGrader.grade(raw, smooth, maxStep);
            assertEquals(raw.length, graded.length);
            assertGraded(graded, maxStep);
        }
    }

    @Test
    void emptyProfile() {
        assertEquals(0, PathGrader.grade(new int[0], 2, 1).length);
    }
}

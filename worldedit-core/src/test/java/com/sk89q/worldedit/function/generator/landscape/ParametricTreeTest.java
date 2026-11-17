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

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParametricTreeTest {

    @Test
    void straightTrunkIsVertical() {
        ParametricTree.Skeleton skeleton = ParametricTree.generate(new Random(1), 12, 0, 3, 0, 1);
        ParametricTree.Voxels voxels = ParametricTree.rasterize(skeleton, new Random(1));
        for (int y = 0; y < 12; y++) {
            assertEquals(ParametricTree.Axis.Y, voxels.logs().get(BlockVector3.at(0, y, 0)), "trunk block at y=" + y);
        }
        assertEquals(12, voxels.logs().size());
        // only the crown blob
        assertEquals(1, skeleton.blobs().size());
        assertFalse(voxels.leaves().isEmpty());
    }

    @Test
    void thickTrunkIsTwoByTwo() {
        ParametricTree.Skeleton skeleton = ParametricTree.generate(new Random(1), 8, 0, 2, 0, 2);
        ParametricTree.Voxels voxels = ParametricTree.rasterize(skeleton, new Random(1));
        assertEquals(32, voxels.logs().size());
        assertTrue(voxels.logs().containsKey(BlockVector3.at(1, 7, 1)));
    }

    @Test
    void branchesStartInUpperPartAndEndInBlobs() {
        int height = 20;
        ParametricTree.Skeleton skeleton = ParametricTree.generate(new Random(7), height, 6, 3, 0, 1);
        // 4 trunk pieces + 6 branches
        assertEquals(10, skeleton.segments().size());
        assertEquals(7, skeleton.blobs().size());
        for (int i = 4; i < 10; i++) {
            ParametricTree.Segment branch = skeleton.segments().get(i);
            assertTrue(branch.from().y() >= 0.4 * (height - 1) - 1e-6, "branch starts too low");
            assertTrue(branch.to().y() > branch.from().y(), "branches must go up");
            Vector3 blob = skeleton.blobs().get(i - 4).center();
            assertEquals(branch.to(), blob);
            double radius = skeleton.blobs().get(i - 4).radiusXZ();
            assertTrue(radius >= 2.4 - 1e-9 && radius <= 3.6 + 1e-9);
        }
    }

    @Test
    void horizontalBranchesGetHorizontalAxis() {
        ParametricTree.Segment branch = new ParametricTree.Segment(Vector3.at(0, 5, 0), Vector3.at(6, 6, 0), 1);
        ParametricTree.Skeleton skeleton = new ParametricTree.Skeleton(List.of(branch), List.of());
        ParametricTree.Voxels voxels = ParametricTree.rasterize(skeleton, new Random(0));
        assertTrue(voxels.logs().size() >= 6);
        voxels.logs().values().forEach(axis -> assertEquals(ParametricTree.Axis.X, axis));
        assertEquals(ParametricTree.Axis.Z, ParametricTree.Axis.dominant(Vector3.at(0.2, 0.5, -1)));
    }

    @Test
    void leavesNeverOverlapLogs() {
        for (int seed = 0; seed < 20; seed++) {
            Random random = new Random(seed);
            ParametricTree.Skeleton skeleton = ParametricTree.generate(random, 10 + seed % 7, seed % 6, 2 + seed % 3, seed * 3 % 45, 1 + seed % 2);
            ParametricTree.Voxels voxels = ParametricTree.rasterize(skeleton, random);
            for (BlockVector3 leaf : voxels.leaves()) {
                assertFalse(voxels.logs().containsKey(leaf));
            }
        }
    }

    @Test
    void leanMovesTheTop() {
        ParametricTree.Skeleton skeleton = ParametricTree.generate(new Random(3), 16, 0, 3, 30, 1);
        Vector3 top = skeleton.segments().get(3).to();
        double horizontal = Math.hypot(top.x(), top.z());
        assertTrue(horizontal > 2, "a 30 degree lean should displace the top");
        assertTrue(top.y() < 15);
    }

    @Test
    void sameSeedSameTree() {
        ParametricTree.Voxels a = ParametricTree.rasterize(ParametricTree.generate(new Random(5), 14, 5, 3, 10, 1), new Random(6));
        ParametricTree.Voxels b = ParametricTree.rasterize(ParametricTree.generate(new Random(5), 14, 5, 3, 10, 1), new Random(6));
        assertEquals(a.logs(), b.logs());
        assertEquals(a.leaves(), b.leaves());
    }

    @Test
    void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> ParametricTree.generate(new Random(), 1, 0, 3, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ParametricTree.generate(new Random(), 10, 0, 3, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> ParametricTree.generate(new Random(), 10, 0, 3, 90, 1));
    }
}

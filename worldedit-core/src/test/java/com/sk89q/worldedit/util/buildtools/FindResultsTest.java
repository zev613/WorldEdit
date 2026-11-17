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

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindResultsTest {

    @Test
    void keepsNearestSorted() {
        FindResults results = new FindResults(BlockVector3.ZERO, 3, false);
        results.add(BlockVector3.at(10, 0, 0));
        results.add(BlockVector3.at(1, 0, 0));
        results.add(BlockVector3.at(0, 5, 0));
        results.add(BlockVector3.at(0, 0, -2));
        results.add(BlockVector3.at(100, 100, 100));
        assertEquals(5, results.getTotal());
        assertEquals(List.of(BlockVector3.at(1, 0, 0), BlockVector3.at(0, 0, -2), BlockVector3.at(0, 5, 0)),
            results.getNearest());
        assertEquals(25, results.distanceSq(BlockVector3.at(0, 5, 0)));
    }

    @Test
    void manyMatchesKeepBoundedHeap() {
        BlockVector3 origin = BlockVector3.at(50, 50, 50);
        FindResults results = new FindResults(origin, 10, false);
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                results.add(BlockVector3.at(x, 50, z));
            }
        }
        List<BlockVector3> nearest = results.getNearest();
        assertEquals(10, nearest.size());
        assertEquals(origin, nearest.getFirst());
        for (BlockVector3 pt : nearest) {
            assertTrue(results.distanceSq(pt) <= 4, pt.toString());
        }
    }

    @Test
    void chunkCounts() {
        FindResults results = new FindResults(BlockVector3.ZERO, 0, true);
        results.add(BlockVector3.at(0, 0, 0));
        results.add(BlockVector3.at(15, 3, 15));
        results.add(BlockVector3.at(16, 0, 0));
        results.add(BlockVector3.at(-1, 0, 0));
        results.add(BlockVector3.at(-16, 0, -17));
        results.add(BlockVector3.at(-5, 0, 3));
        assertEquals(6, results.getTotal());
        assertTrue(results.getNearest().isEmpty());
        assertEquals(4, results.getChunkCount());
        List<Map.Entry<BlockVector2, Integer>> top = results.getTopChunks(2);
        assertEquals(2, top.size());
        // chunk (-1, 0) and (0, 0) both have 2; ties broken by chunk x
        assertEquals(BlockVector2.at(-1, 0), top.get(0).getKey());
        assertEquals(2, top.get(0).getValue());
        assertEquals(BlockVector2.at(0, 0), top.get(1).getKey());
        assertEquals(4, results.getTopChunks(10).size());
    }
}

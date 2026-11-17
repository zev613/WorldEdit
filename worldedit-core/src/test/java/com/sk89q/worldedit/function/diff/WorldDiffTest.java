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

package com.sk89q.worldedit.function.diff;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldDiffTest extends BaseWorldEditTest {

    static BlockState state(String id) {
        BlockType type = BlockType.REGISTRY.get(id);
        if (type == null) {
            type = new BlockType(id);
            BlockType.REGISTRY.register(id, type);
        }
        return type.getDefaultState();
    }

    private static BlockArrayClipboard filled(CuboidRegion region, BlockState block) throws WorldEditException {
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        for (BlockVector3 pos : region) {
            clipboard.setBlock(pos, block);
        }
        return clipboard;
    }

    @Test
    void classify() {
        BlockState air = state("minecraft:air");
        BlockState caveAir = state("minecraft:cave_air");
        BlockState stone = state("minecraft:stone");
        BlockState dirt = state("minecraft:dirt");
        assertTrue(WorldDiff.isAir(air));
        assertTrue(WorldDiff.isAir(caveAir));
        assertFalse(WorldDiff.isAir(stone));
        assertEquals(WorldDiff.Kind.UNCHANGED, WorldDiff.classify(stone, stone));
        assertEquals(WorldDiff.Kind.UNCHANGED, WorldDiff.classify(air, caveAir));
        assertEquals(WorldDiff.Kind.CHANGED, WorldDiff.classify(stone, dirt));
        assertEquals(WorldDiff.Kind.REMOVED, WorldDiff.classify(stone, air));
        assertEquals(WorldDiff.Kind.ADDED, WorldDiff.classify(air, dirt));
    }

    @Test
    void countsAndBoundingBox() throws WorldEditException {
        BlockState air = state("minecraft:air");
        BlockState stone = state("minecraft:stone");
        BlockState dirt = state("minecraft:dirt");
        BlockState glass = state("minecraft:glass");
        // clipboard copied from (10,60,10)-(13,61,13): 32 blocks of stone, top layer air
        CuboidRegion region = new CuboidRegion(BlockVector3.at(10, 60, 10), BlockVector3.at(13, 61, 13));
        BlockArrayClipboard clipboard = filled(region, stone);
        for (int x = 10; x <= 13; x++) {
            for (int z = 10; z <= 13; z++) {
                clipboard.setBlock(BlockVector3.at(x, 61, z), air);
            }
        }
        // "world" larger than the clipboard, starts as an exact copy
        CuboidRegion worldRegion = new CuboidRegion(BlockVector3.at(0, 50, 0), BlockVector3.at(20, 70, 20));
        BlockArrayClipboard world = filled(worldRegion, air);
        for (BlockVector3 pos : region) {
            world.setBlock(pos, clipboard.getBlock(pos));
        }
        WorldDiff same = WorldDiff.compute(clipboard, world, 100, null);
        assertEquals(32, same.getUnchanged());
        assertEquals(0, same.getTotalDifferences());
        assertNull(same.getMinimum());

        world.setBlock(BlockVector3.at(10, 60, 10), dirt);   // changed
        world.setBlock(BlockVector3.at(11, 60, 10), dirt);   // changed
        world.setBlock(BlockVector3.at(12, 60, 12), air);    // removed
        world.setBlock(BlockVector3.at(13, 61, 13), glass);  // added
        world.setBlock(BlockVector3.at(5, 60, 5), glass);    // outside the clipboard: ignored

        List<BlockVector3> seen = new ArrayList<>();
        WorldDiff diff = WorldDiff.compute(clipboard, world, 2, (pos, _, _, _) -> seen.add(pos));
        assertEquals(28, diff.getUnchanged());
        assertEquals(2, diff.getChanged());
        assertEquals(1, diff.getRemoved());
        assertEquals(1, diff.getAdded());
        assertEquals(4, seen.size());
        assertEquals(BlockVector3.at(10, 60, 10), diff.getMinimum());
        assertEquals(BlockVector3.at(13, 61, 13), diff.getMaximum());
        assertEquals(2, diff.getRecorded().size());
        assertTrue(diff.isRecordTruncated());

        List<WorldDiff.TypePair> pairs = diff.getTopPairs(5);
        assertEquals(3, pairs.size());
        assertEquals("minecraft:stone", pairs.get(0).from().id());
        assertEquals("minecraft:dirt", pairs.get(0).to().id());
        assertEquals(2, pairs.get(0).count());
        assertEquals(1, diff.getTopPairs(1).size());
    }
}

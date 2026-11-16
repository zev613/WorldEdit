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

package com.sk89q.worldedit.function.pattern;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedPatternsTest {

    private static List<BaseBlock> blocks(int count) {
        return IntStream.range(0, count).mapToObj(_ -> mock(BaseBlock.class)).toList();
    }

    private static List<Pattern> patternsFor(List<BaseBlock> blocks) {
        return blocks.stream().map(b -> {
            Pattern p = mock(Pattern.class);
            when(p.applyBlock(any())).thenReturn(b);
            return p;
        }).toList();
    }

    @Test
    void gradientUsesFirstAndLastPaletteEntryAtBounds() {
        List<BaseBlock> blocks = blocks(3);
        GradientPattern pattern = new GradientPattern('y', 0, 30, patternsFor(blocks));
        assertSame(blocks.get(0), pattern.applyBlock(BlockVector3.at(5, -10, 5)));
        assertSame(blocks.get(0), pattern.applyBlock(BlockVector3.at(5, 0, 5)));
        assertSame(blocks.get(2), pattern.applyBlock(BlockVector3.at(5, 30, 5)));
        assertSame(blocks.get(2), pattern.applyBlock(BlockVector3.at(5, 100, 5)));
        // Exactly halfway lands on the middle entry with no dithering
        assertSame(blocks.get(1), pattern.applyBlock(BlockVector3.at(5, 15, 5)));
    }

    @Test
    void gradientRespectsAxis() {
        List<BaseBlock> blocks = blocks(2);
        GradientPattern pattern = new GradientPattern('x', 0, 10, patternsFor(blocks));
        assertSame(blocks.get(0), pattern.applyBlock(BlockVector3.at(0, 999, 0)));
        assertSame(blocks.get(1), pattern.applyBlock(BlockVector3.at(10, -999, 0)));
    }

    @Test
    void voronoiIsDeterministicAndUsesWholePalette() {
        List<BaseBlock> blocks = blocks(4);
        VoronoiPattern pattern = new VoronoiPattern(4, patternsFor(blocks));
        Set<BaseBlock> seen = new HashSet<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                BlockVector3 pos = BlockVector3.at(x, 64, z);
                BaseBlock result = pattern.applyBlock(pos);
                assertSame(result, pattern.applyBlock(pos));
                seen.add(result);
            }
        }
        assertEquals(4, seen.size());
    }

    @Test
    void voronoiCellsAreContiguousBlobs() {
        // Neighbouring blocks should mostly share a cell; a random pattern would match ~25% of the time.
        List<BaseBlock> blocks = blocks(4);
        VoronoiPattern pattern = new VoronoiPattern(8, patternsFor(blocks));
        int same = 0;
        int total = 0;
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                total++;
                if (pattern.applyBlock(BlockVector3.at(x, 0, z)) == pattern.applyBlock(BlockVector3.at(x + 1, 0, z))) {
                    same++;
                }
            }
        }
        assertTrue(same > total * 0.75, "expected contiguous cells, got " + same + "/" + total);
    }
}

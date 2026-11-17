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

import com.sk89q.worldedit.math.BlockVector2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CorridorTest {

    @Test
    void straightBandHasRequestedWidth() {
        List<int[]> points = new ArrayList<>();
        for (int x = 0; x < 10; x++) {
            points.add(new int[] {x, 0});
        }
        Map<BlockVector2, Corridor.Column> band = Corridor.rasterize(points, 1.3);
        for (int x = 0; x < 10; x++) {
            for (int z = -1; z <= 1; z++) {
                Corridor.Column column = band.get(BlockVector2.at(x, z));
                assertNotNull(column);
                assertEquals(x, column.nearestIndex());
                assertEquals(Math.abs(z), column.distance(), 1e-9);
            }
        }
        // 3 wide over 10 points plus one cap column at each end
        assertEquals(30 + 2, band.size());
    }

    @Test
    void singlePoint() {
        Map<BlockVector2, Corridor.Column> band = Corridor.rasterize(List.<int[]>of(new int[] {5, 5}), 1);
        assertEquals(5, band.size());
    }
}

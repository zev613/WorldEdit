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

package com.sk89q.worldedit.session.preview;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.Registry;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EditPreviewTest extends BaseWorldEditTest {

    @BeforeAll
    static void registerBlocks() {
        BlockType.REGISTRY.register("minecraft:air", new BlockType("minecraft:air"));
        BlockType.REGISTRY.register("minecraft:stone", new BlockType("minecraft:stone"));
        BlockType.REGISTRY.register("minecraft:glass", new BlockType("minecraft:glass"));
    }

    @AfterAll
    static void clearBlocks() throws Exception {
        Field map = Registry.class.getDeclaredField("map");
        map.setAccessible(true);
        ((Map<?, ?>) map.get(BlockType.REGISTRY)).clear();
    }

    private static BaseBlock block(String id) {
        return checkNotNull(BlockType.REGISTRY.get(id)).getDefaultState().toBaseBlock();
    }

    @Test
    void captureExtentKeepsChangesOutOfTheWorld() throws WorldEditException {
        BaseBlock stone = block("minecraft:stone");
        BaseBlock glass = block("minecraft:glass");
        Extent world = mock(Extent.class);
        BlockVector3 untouched = BlockVector3.at(5, 5, 5);
        when(world.getFullBlock(untouched)).thenReturn(stone);
        when(world.getBlock(untouched)).thenReturn(stone.toImmutableState());

        EditPreview preview = new EditPreview(mock(World.class));
        PreviewCaptureExtent extent = new PreviewCaptureExtent(world, preview);
        BlockVector3 pos = BlockVector3.at(1, 2, 3);

        assertTrue(extent.setBlock(pos, glass));
        verify(world, never()).setBlock(any(BlockVector3.class), any());
        assertEquals(glass, extent.getFullBlock(pos), "reads must see the previewed block");
        assertEquals(glass.toImmutableState(), extent.getBlock(pos));
        assertEquals(stone, extent.getFullBlock(untouched), "other positions read from the world");
        assertEquals(1, preview.size());
        assertNull(extent.createEntity(null, null));
    }

    @Test
    void showRespectsLimitAndHideRestores() {
        BaseBlock glass = block("minecraft:glass");
        EditPreview preview = new EditPreview(mock(World.class));
        for (int i = 0; i < 10; i++) {
            preview.put(BlockVector3.at(i, 0, 0), glass);
        }
        Player player = mock(Player.class);
        int skipped = preview.show(player, 6);
        assertEquals(4, skipped);
        verify(player, times(6)).sendFakeBlock(any(BlockVector3.class), eq(glass));

        // Showing again doesn't resend what's already shown
        assertEquals(0, preview.show(player, 6));
        verify(player, times(6)).sendFakeBlock(any(BlockVector3.class), eq(glass));

        preview.hide(player);
        verify(player, times(6)).sendFakeBlock(any(BlockVector3.class), isNull());

        // After hiding, showAll brings everything back (within the limit)
        assertEquals(0, preview.showAll(player, 100));
        verify(player, times(16)).sendFakeBlock(any(BlockVector3.class), eq(glass));
    }
}

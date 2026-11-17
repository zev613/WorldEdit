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

package com.sk89q.worldedit.extent.symmetry;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.NullExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SymmetryTest extends BaseWorldEditTest {

    private static Set<BlockVector3> positions(List<SymmetrySettings.Mirror> mirrors) {
        return mirrors.stream().map(SymmetrySettings.Mirror::position).collect(Collectors.toSet());
    }

    @Test
    void mirrorXThroughBlockCentre() {
        SymmetrySettings settings = new SymmetrySettings(true, false, 20, 0, "w");
        List<SymmetrySettings.Mirror> mirrors = settings.mirrorsOf(BlockVector3.at(13, 64, 5));
        assertEquals(1, mirrors.size());
        assertEquals(BlockVector3.at(7, 64, 5), mirrors.getFirst().position());
        assertTrue(mirrors.getFirst().flipX());
        // on the plane: no mirror
        assertTrue(settings.mirrorsOf(BlockVector3.at(10, 64, 5)).isEmpty());
    }

    @Test
    void mirrorBetweenBlocks() {
        // Plane between x=10 and x=11 (e.g. centre of an even-width selection 5..16)
        SymmetrySettings settings = new SymmetrySettings(true, false, 21, 0, "w");
        assertEquals(BlockVector3.at(11, 0, 0), settings.mirrorsOf(BlockVector3.at(10, 0, 0)).getFirst().position());
        assertEquals(BlockVector3.at(5, 0, 0), settings.mirrorsOf(BlockVector3.at(16, 0, 0)).getFirst().position());
    }

    @Test
    void fourWay() {
        SymmetrySettings settings = new SymmetrySettings(true, true, 0, 0, "w");
        assertEquals(Set.of(BlockVector3.at(-3, 1, 4), BlockVector3.at(3, 1, -4), BlockVector3.at(-3, 1, -4)),
            positions(settings.mirrorsOf(BlockVector3.at(3, 1, 4))));
        // on the X plane: only the Z mirror remains, duplicates removed
        assertEquals(Set.of(BlockVector3.at(0, 1, -4)), positions(settings.mirrorsOf(BlockVector3.at(0, 1, 4))));
        // on both planes: nothing
        assertTrue(settings.mirrorsOf(BlockVector3.at(0, 1, 0)).isEmpty());
    }

    @Test
    void mirrorIsInvolution() {
        SymmetrySettings settings = new SymmetrySettings(true, true, 7, -3, "w");
        BlockVector3 original = BlockVector3.at(12, 5, -20);
        for (SymmetrySettings.Mirror mirror : settings.mirrorsOf(original)) {
            assertTrue(positions(settings.mirrorsOf(mirror.position())).contains(original));
        }
    }

    @Test
    void describe() {
        assertEquals("X mirror through block x=10, Z mirror between blocks z=-2 and z=-1 in world",
            new SymmetrySettings(true, true, 20, -3, "world").describe());
    }

    @Test
    void extentWritesMirroredCopiesToDelegateOnly() throws Exception {
        List<BlockVector3> written = new ArrayList<>();
        AbstractDelegateExtent recorder = new AbstractDelegateExtent(new NullExtent()) {
            @Override
            public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) {
                written.add(location);
                return true;
            }
        };
        SymmetryExtent extent = new SymmetryExtent(recorder, new SymmetrySettings(true, true, 0, 0, "w"));
        BlockState state = new BlockType("worldedit:symmetry_test").getDefaultState();
        assertTrue(extent.setBlock(BlockVector3.at(2, 0, 3), state));
        assertEquals(4, written.size());
        assertEquals(BlockVector3.at(2, 0, 3), written.getFirst());
        assertEquals(Set.of(BlockVector3.at(2, 0, 3), BlockVector3.at(-2, 0, 3), BlockVector3.at(2, 0, -3),
            BlockVector3.at(-2, 0, -3)), Set.copyOf(written));
    }

    @Test
    void managerWrapsOnlyBeforeHistoryForPlayersWithSymmetry() {
        SymmetryManager manager = SymmetryManager.getInstance();
        UUID id = UUID.randomUUID();
        Actor player = mock(Actor.class);
        when(player.isPlayer()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(id);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        manager.setSettings(id, new SymmetrySettings(true, false, 0, 0, "world"));
        try {
            NullExtent base = new NullExtent();
            EditSessionEvent history = new EditSessionEvent(world, player, -1, EditSession.Stage.BEFORE_HISTORY);
            history.setExtent(base);
            manager.onEditSession(history);
            assertInstanceOf(SymmetryExtent.class, history.getExtent());

            EditSessionEvent change = new EditSessionEvent(world, player, -1, EditSession.Stage.BEFORE_CHANGE);
            change.setExtent(base);
            manager.onEditSession(change);
            assertSame(base, change.getExtent());

            World other = mock(World.class);
            when(other.getName()).thenReturn("nether");
            EditSessionEvent otherWorld = new EditSessionEvent(other, player, -1, EditSession.Stage.BEFORE_HISTORY);
            otherWorld.setExtent(base);
            manager.onEditSession(otherWorld);
            assertSame(base, otherWorld.getExtent());

            manager.setSettings(id, null);
            EditSessionEvent off = new EditSessionEvent(world, player, -1, EditSession.Stage.BEFORE_HISTORY);
            off.setExtent(base);
            manager.onEditSession(off);
            assertSame(base, off.getExtent());
        } finally {
            manager.setSettings(id, null);
        }
    }
}

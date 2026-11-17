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

package com.sk89q.worldedit.session;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NamedClipboardStoreTest extends BaseWorldEditTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path tempDir;

    @Test
    void sanitize() {
        assertEquals("house", NamedClipboardStore.sanitizeName("House"));
        assertEquals("my_build-2", NamedClipboardStore.sanitizeName(" my_build-2 "));
        assertNull(NamedClipboardStore.sanitizeName(""));
        assertNull(NamedClipboardStore.sanitizeName("../evil"));
        assertNull(NamedClipboardStore.sanitizeName("a/b"));
        assertNull(NamedClipboardStore.sanitizeName("a\\b"));
        assertNull(NamedClipboardStore.sanitizeName("a.schem"));
        assertNull(NamedClipboardStore.sanitizeName(".."));
        assertNull(NamedClipboardStore.sanitizeName("x".repeat(33)));
        assertNull(NamedClipboardStore.sanitizeName(null));
    }

    @Test
    void filesStayInsidePlayerFolder() {
        NamedClipboardStore store = new NamedClipboardStore(tempDir);
        Path file = store.getFile(OWNER, "Castle");
        assertEquals(tempDir.resolve(OWNER.toString()).resolve("castle.schem").toAbsolutePath().normalize(), file);
        assertThrows(IllegalArgumentException.class, () -> store.getFile(OWNER, "../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> store.getFile(OWNER, "/abs"));
    }

    @Test
    void listAndDelete() throws IOException {
        NamedClipboardStore store = new NamedClipboardStore(tempDir);
        assertTrue(store.list(OWNER).isEmpty());
        Path dir = tempDir.resolve(OWNER.toString());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("beta.schem"), "x");
        Files.writeString(dir.resolve("alpha.schem"), "x");
        Files.writeString(dir.resolve("ignored.txt"), "x");
        Files.writeString(dir.resolve("Bad Name.schem"), "x");
        assertEquals(List.of("alpha", "beta"), store.list(OWNER));
        assertTrue(store.exists(OWNER, "ALPHA"));
        assertTrue(store.delete(OWNER, "alpha"));
        assertFalse(store.delete(OWNER, "alpha"));
        assertEquals(List.of("beta"), store.list(OWNER));
        assertTrue(store.list(UUID.randomUUID()).isEmpty());
    }

    private static BlockState state(String id) {
        BlockType type = BlockType.REGISTRY.get(id);
        if (type == null) {
            type = new BlockType(id);
            BlockType.REGISTRY.register(id, type);
        }
        return type.getDefaultState();
    }

    @Test
    void saveLoadRoundTripAndSessionFallback() throws Exception {
        // The schematic writer records the WorldEdit version and platform id.
        Field version = WorldEdit.class.getDeclaredField("version");
        version.setAccessible(true);
        if (version.get(null) == null) {
            version.set(null, "test");
        }
        when(MOCKED_PLATFORM.id()).thenReturn("test");
        when(MOCKED_PLATFORM.getPlatformName()).thenReturn("Test");
        when(MOCKED_PLATFORM.getPlatformVersion()).thenReturn("1");
        BlockState air = state("minecraft:air");
        BlockState stone = state("minecraft:stone");
        CuboidRegion region = new CuboidRegion(BlockVector3.at(100, 64, 100), BlockVector3.at(101, 65, 102));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(BlockVector3.at(100, 64, 100));
        for (BlockVector3 pos : region) {
            clipboard.setBlock(pos, pos.y() == 64 ? stone : air);
        }
        NamedClipboardStore store = new NamedClipboardStore(tempDir);
        store.save(OWNER, "Test", new ClipboardHolder(clipboard));
        assertTrue(Files.isRegularFile(tempDir.resolve(OWNER.toString()).resolve("test.schem")));

        ClipboardHolder loaded = store.load(OWNER, "test");
        assertNotNull(loaded);
        Clipboard read = loaded.getClipboard();
        assertEquals(region.getMinimumPoint(), read.getRegion().getMinimumPoint());
        assertEquals(region.getMaximumPoint(), read.getRegion().getMaximumPoint());
        assertEquals(clipboard.getOrigin(), read.getOrigin());
        for (BlockVector3 pos : region) {
            assertEquals(clipboard.getBlock(pos), read.getBlock(pos), pos.toString());
        }
        assertNull(store.load(OWNER, "missing"));

        LocalSession session = new LocalSession();
        Actor actor = mock(Actor.class);
        when(actor.getUniqueId()).thenReturn(OWNER);
        when(actor.getName()).thenReturn("tester");
        assertThrows(EmptyClipboardException.class, session::getClipboard);
        ClipboardHolder fromDisk = store.getNamedClipboard(session, actor, "TEST");
        assertSame(fromDisk, session.getNamedClipboard("test"));
        assertThrows(EmptyClipboardException.class, () -> store.getNamedClipboard(session, actor, "nope"));
        assertThrows(EmptyClipboardException.class, () -> store.getNamedClipboard(session, actor, "../x"));
    }
}

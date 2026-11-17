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

package com.sk89q.worldedit.history.persistent;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.history.change.BiomeChange3D;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.changeset.ArrayListHistory;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistorySnapshotTest extends BaseWorldEditTest {

    @TempDir
    Path tempDir;

    private static BlockState state(String id) {
        BlockType type = BlockType.REGISTRY.get(id);
        if (type == null) {
            type = new BlockType(id);
            BlockType.REGISTRY.register(id, type);
        }
        return type.getDefaultState();
    }

    private static BiomeType biome(String id) {
        BiomeType type = BiomeType.REGISTRY.get(id);
        if (type == null) {
            type = new BiomeType(id);
            BiomeType.REGISTRY.register(id, type);
        }
        return type;
    }

    /**
     * Resolves block ids from the registry; property-less test blocks only.
     */
    static final HistorySnapshot.Resolver RESOLVER = new HistorySnapshot.Resolver() {
        @Override
        public @Nullable BlockState parseBlock(String id, int dataVersion) {
            BlockType type = BlockType.REGISTRY.get(id);
            return type == null ? null : type.getDefaultState();
        }

        @Override
        public @Nullable BiomeType parseBiome(String id, int dataVersion) {
            return BiomeType.REGISTRY.get(id);
        }
    };

    private static Map<BlockVector3, BaseBlock> applyBlocks(Iterator<Change> it, boolean undo) {
        Map<BlockVector3, BaseBlock> result = new HashMap<>();
        while (it.hasNext()) {
            Change change = it.next();
            if (change instanceof BlockChange blockChange) {
                result.put(blockChange.position(), undo ? blockChange.previous() : blockChange.current());
            }
        }
        return result;
    }

    private static ChangeSet sampleChanges(ChangeSet changes) {
        BaseBlock air = state("minecraft:air").toBaseBlock();
        BaseBlock stone = state("minecraft:stone").toBaseBlock();
        BaseBlock chest = state("minecraft:chest").toBaseBlock(
            LinCompoundTag.builder().putString("CustomName", "loot").build());
        BlockVector3 a = BlockVector3.at(1, 64, -3);
        BlockVector3 b = BlockVector3.at(2, 64, -3);
        changes.add(new BlockChange(a, air, stone));
        changes.add(new BlockChange(b, chest, air));
        changes.add(new BlockChange(a, stone, chest));
        changes.add(new BiomeChange3D(BlockVector3.at(0, 64, 0), biome("minecraft:plains"), biome("minecraft:desert")));
        return changes;
    }

    @Test
    void captureNetEffect() {
        for (ChangeSet changes : List.of(sampleChanges(new BlockOptimizedHistory()), sampleChanges(new ArrayListHistory()))) {
            HistorySnapshot snapshot = HistorySnapshot.capture(changes, "world", 1234, 1000);
            assertFalse(snapshot.barrier());
            assertEquals(2, snapshot.blocks().size());
            assertEquals(1, snapshot.biomes().size());
            // first changed position comes first
            BlockChange first = snapshot.blocks().get(0);
            assertEquals(BlockVector3.at(1, 64, -3), first.position());
            assertEquals("minecraft:air", first.previous().getBlockType().id());
            assertEquals("minecraft:chest", first.current().getBlockType().id());
            assertNotNull(first.current().getNbt());
            BlockChange second = snapshot.blocks().get(1);
            assertNotNull(second.previous().getNbt());
            assertNull(second.current().getNbt());
        }
    }

    @Test
    void largeEditBecomesBarrier() {
        HistorySnapshot snapshot = HistorySnapshot.capture(sampleChanges(new ArrayListHistory()), "world", 1, 2);
        assertTrue(snapshot.barrier());
        assertTrue(snapshot.blocks().isEmpty());
    }

    @Test
    void fileRoundTripPreservesUndoAndRedo() throws IOException {
        ChangeSet original = sampleChanges(new BlockOptimizedHistory());
        HistorySnapshot snapshot = HistorySnapshot.capture(original, "my world", 4321, 1000);
        Path file = tempDir.resolve("edit.weh");
        snapshot.write(file);
        HistorySnapshot read = HistorySnapshot.read(file, RESOLVER);
        assertEquals("my world", read.worldName());
        assertEquals(4321, read.dataVersion());
        assertEquals(snapshot.created(), read.created());
        assertEquals(snapshot.blocks(), read.blocks());
        assertEquals(snapshot.biomes(), read.biomes());

        ChangeSet restored = new BlockOptimizedHistory();
        read.addTo(restored);
        assertEquals(applyBlocks(original.backwardIterator(), true), applyBlocks(restored.backwardIterator(), true));
        assertEquals(applyBlocks(original.forwardIterator(), false), applyBlocks(restored.forwardIterator(), false));
        assertEquals("loot", applyBlocks(restored.forwardIterator(), false)
            .get(BlockVector3.at(1, 64, -3)).getNbt().getTag("CustomName",
                org.enginehub.linbus.tree.LinTagType.stringTag()).value());
    }

    @Test
    void unknownBlockFailsToLoad() throws IOException {
        HistorySnapshot snapshot = HistorySnapshot.capture(sampleChanges(new ArrayListHistory()), "w", 1, 1000);
        Path file = tempDir.resolve("bad.weh");
        snapshot.write(file);
        HistorySnapshot.Resolver noBlocks = new HistorySnapshot.Resolver() {
            @Override
            public @Nullable BlockState parseBlock(String state, int dataVersion) {
                return null;
            }

            @Override
            public @Nullable BiomeType parseBiome(String id, int dataVersion) {
                return null;
            }
        };
        assertThrows(IOException.class, () -> HistorySnapshot.read(file, noBlocks));
        Files.writeString(file, "not gzip");
        assertThrows(IOException.class, () -> HistorySnapshot.read(file, RESOLVER));
    }

    // ---- PersistentHistory file bookkeeping ------------------------------------------------

    private static EditSession mockEdit(World world) {
        EditSession editSession = mock(EditSession.class);
        ChangeSet changes = sampleChanges(new BlockOptimizedHistory());
        when(editSession.getChangeSet()).thenReturn(changes);
        when(editSession.getWorld()).thenReturn(world);
        return editSession;
    }

    private static List<String> names(PersistentHistory history) throws IOException {
        List<String> names = new ArrayList<>();
        for (PersistentHistory.FileInfo info : history.listFiles()) {
            names.add(info.id() + (info.done() ? "" : "*"));
        }
        return names;
    }

    @Test
    void persistentHistoryFiles() throws IOException {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Path dir = tempDir.resolve("history");
        PersistentHistory history = new PersistentHistory(dir, 3, _ -> null, RESOLVER);
        List<EditSession> memory = new ArrayList<>();

        EditSession e1 = mockEdit(world);
        memory.add(e1);
        history.onRemember(e1, memory);
        EditSession e2 = mockEdit(world);
        memory.add(e2);
        history.onRemember(e2, memory);
        history.flush();
        List<String> files = names(history);
        assertEquals(2, files.size());
        assertTrue(Files.exists(dir.resolve(files.get(1) + PersistentHistory.DONE_SUFFIX)));

        // undo e2 in memory -> file marked undone
        history.onUndo(e2);
        history.flush();
        assertEquals(List.of(files.get(0), files.get(1) + "*"), names(history));
        // redo -> done again
        history.onRedo(e2);
        history.onUndo(e2);
        history.flush();
        assertEquals(List.of(files.get(0), files.get(1) + "*"), names(history));

        // a new edit drops the undone one (like the in-memory redo branch)
        memory.remove(e2);
        EditSession e3 = mockEdit(world);
        memory.add(e3);
        history.onRemember(e3, memory);
        history.flush();
        assertEquals(2, names(history).size());
        assertFalse(names(history).contains(files.get(1) + "*"));

        // pruning keeps the newest 3
        for (int i = 0; i < 3; i++) {
            EditSession more = mockEdit(world);
            memory.add(more);
            history.onRemember(more, memory);
        }
        history.flush();
        assertEquals(3, names(history).size());
        assertFalse(names(history).contains(files.get(0)));

        // everything is in memory, so nothing is undoable from disk
        assertFalse(history.hasDiskRedo());

        history.onClear();
        history.flush();
        assertTrue(names(history).isEmpty());
    }

    @Test
    void afterRestartUnmappedFilesAreUsed() throws IOException {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Path dir = tempDir.resolve("history2");
        PersistentHistory before = new PersistentHistory(dir, 10, _ -> null, RESOLVER);
        List<EditSession> memory = new ArrayList<>();
        EditSession e1 = mockEdit(world);
        memory.add(e1);
        before.onRemember(e1, memory);
        EditSession e2 = mockEdit(world);
        memory.add(e2);
        before.onRemember(e2, memory);
        before.onUndo(e2);
        before.flush();

        // "restart": a new instance with an empty in-memory history
        PersistentHistory after = new PersistentHistory(dir, 10, _ -> null, RESOLVER);
        assertTrue(after.hasDiskRedo());
        // the world is not loaded -> nothing is changed
        PersistentHistory.DiskResult result = after.undoFromDisk(null, null, null);
        assertEquals(PersistentHistory.Status.WORLD_MISSING, result.status());
        assertEquals("world", result.worldName());
        assertEquals(2, names(after).size());
    }

    @Test
    void barrierStopsDiskUndo() throws IOException {
        Path dir = tempDir.resolve("history3");
        Files.createDirectories(dir);
        HistorySnapshot.capture(sampleChanges(new ArrayListHistory()), "world", 1, 0)
            .write(dir.resolve("000000000000001-000000" + PersistentHistory.DONE_SUFFIX));
        PersistentHistory history = new PersistentHistory(dir, 10, _ -> null, RESOLVER);
        assertEquals(PersistentHistory.Status.BARRIER, history.undoFromDisk(null, null, null).status());
        PersistentHistory empty = new PersistentHistory(tempDir.resolve("none"), 10, _ -> null, RESOLVER);
        assertEquals(PersistentHistory.Status.NONE, empty.undoFromDisk(null, null, null).status());
    }
}

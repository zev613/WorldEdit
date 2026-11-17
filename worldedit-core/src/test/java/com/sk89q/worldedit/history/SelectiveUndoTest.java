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

package com.sk89q.worldedit.history;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.changeset.ArrayListHistory;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectiveUndoTest extends BaseWorldEditTest {

    private static BaseBlock block(String id) {
        BlockType type = BlockType.REGISTRY.get(id);
        if (type == null) {
            type = new BlockType(id);
            BlockType.REGISTRY.register(id, type);
        }
        return type.getDefaultState().toBaseBlock();
    }

    private static void apply(Map<BlockVector3, BaseBlock> world, Iterator<Change> changes, boolean undo) {
        while (changes.hasNext()) {
            if (changes.next() instanceof BlockChange change) {
                world.put(change.position(), undo ? change.previous() : change.current());
            }
        }
    }

    private static Supplier<ChangeSet> factory(String kind) {
        return kind.equals("optimized") ? BlockOptimizedHistory::new : ArrayListHistory::new;
    }

    @ParameterizedTest
    @ValueSource(strings = { "optimized", "list" })
    void partialUndoStaysConsistent(String kind) {
        BaseBlock air = block("minecraft:air");
        BaseBlock stone = block("minecraft:stone");
        BaseBlock dirt = block("minecraft:dirt");
        BaseBlock gold = block("minecraft:gold_block");
        BlockVector3 a = BlockVector3.at(0, 0, 0);
        BlockVector3 b = BlockVector3.at(1, 0, 0);
        BlockVector3 c = BlockVector3.at(2, 0, 0);

        // Before the edit: a=air, b=air, c=stone.
        Map<BlockVector3, BaseBlock> world = new HashMap<>();
        world.put(a, air);
        world.put(b, air);
        world.put(c, stone);

        // The edit: a air->dirt->gold (touched twice), b air->dirt, c stone->dirt.
        ChangeSet changes = factory(kind).get();
        changes.add(new BlockChange(a, air, dirt));
        changes.add(new BlockChange(b, air, dirt));
        changes.add(new BlockChange(a, dirt, gold));
        changes.add(new BlockChange(c, stone, dirt));
        world.put(a, gold);
        world.put(b, dirt);
        world.put(c, dirt);

        // Selectively undo a and c only.
        Map<BlockVector3, BaseBlock> toRevert = SelectiveUndo.collectUndo(changes, pos -> !pos.equals(b));
        assertEquals(Map.of(a, air, c, stone), toRevert);

        ChangeSet carrier = new ArrayListHistory();
        SelectiveUndo.fillUndoChanges(carrier, toRevert);
        apply(world, carrier.backwardIterator(), true);
        assertEquals(air, world.get(a));
        assertEquals(dirt, world.get(b));
        assertEquals(stone, world.get(c));

        SelectiveUndo.neutralise(changes, toRevert);

        // A redo of the (neutralised) entry must not bring back the reverted part.
        Map<BlockVector3, BaseBlock> redone = new HashMap<>(world);
        apply(redone, changes.forwardIterator(), false);
        assertEquals(air, redone.get(a));
        assertEquals(dirt, redone.get(b));
        assertEquals(stone, redone.get(c));

        // A full undo afterwards reverts the rest and leaves the world as it was before the edit.
        apply(world, changes.backwardIterator(), true);
        assertEquals(air, world.get(a));
        assertEquals(air, world.get(b));
        assertEquals(stone, world.get(c));

        // Redo after the full undo re-applies only the non-reverted part.
        apply(world, changes.forwardIterator(), false);
        assertEquals(air, world.get(a));
        assertEquals(dirt, world.get(b));
        assertEquals(stone, world.get(c));
    }

    @ParameterizedTest
    @ValueSource(strings = { "optimized", "list" })
    void neutraliseWorksWhileNotRecording(String kind) {
        BaseBlock air = block("minecraft:air");
        BaseBlock dirt = block("minecraft:dirt");
        BlockVector3 a = BlockVector3.at(5, 5, 5);
        ChangeSet changes = factory(kind).get();
        changes.add(new BlockChange(a, air, dirt));
        changes.setRecordChanges(false);
        SelectiveUndo.neutralise(changes, Map.of(a, air));
        Map<BlockVector3, BaseBlock> world = new HashMap<>();
        apply(world, changes.forwardIterator(), false);
        assertEquals(air, world.get(a));
        assertEquals(false, changes.isRecordingChanges());
    }
}

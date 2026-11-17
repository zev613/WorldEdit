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

import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Support for undoing only part of an edit.
 *
 * <p>The positions to revert are chosen with a predicate (usually a mask
 * evaluated against the current world). For each chosen position, the block it
 * had before the edit is computed from the change set's undo iterator.</p>
 *
 * <p>After those blocks have been put back, {@link #neutralise} appends a
 * {@code previous -> previous} change for each reverted position to the
 * edit's change set. The reverted part thereby becomes permanent: a later full
 * undo of the entry restores the same block (a no-op at those positions) and
 * a redo also leaves them at their pre-edit state, so the history entry stays
 * consistent with the world. The entry itself stays in the history and is not
 * marked as undone.</p>
 */
public final class SelectiveUndo {

    private SelectiveUndo() {
    }

    /**
     * Compute the blocks that undoing the given change set would put back,
     * restricted to positions accepted by the filter. The filter is evaluated
     * once per position, before anything is changed.
     *
     * @param changeSet the change set
     * @param filter which positions to include
     * @return map from position to the pre-edit block, in undo order
     */
    public static Map<BlockVector3, BaseBlock> collectUndo(ChangeSet changeSet, Predicate<BlockVector3> filter) {
        Map<BlockVector3, Boolean> decisions = new HashMap<>();
        Map<BlockVector3, BaseBlock> result = new LinkedHashMap<>();
        for (Iterator<Change> it = changeSet.backwardIterator(); it.hasNext(); ) {
            Change change = it.next();
            if (!(change instanceof BlockChange blockChange)) {
                continue;
            }
            BlockVector3 pos = blockChange.position();
            if (decisions.computeIfAbsent(pos, filter::test)) {
                // The undo iterator is applied in order: the last value written wins.
                result.remove(pos);
                result.put(pos, blockChange.previous());
            }
        }
        return result;
    }

    /**
     * Build a change set that, when undone, writes the given blocks.
     *
     * @param target the (empty) change set to fill
     * @param blocks the blocks to write on undo
     */
    public static void fillUndoChanges(ChangeSet target, Map<BlockVector3, BaseBlock> blocks) {
        addAll(target, blocks);
    }

    /**
     * Mark the given positions as permanently reverted in a change set by
     * appending a {@code previous -> previous} change for each.
     *
     * @param changeSet the history entry's change set
     * @param reverted the reverted positions and their pre-edit blocks
     */
    public static void neutralise(ChangeSet changeSet, Map<BlockVector3, BaseBlock> reverted) {
        addAll(changeSet, reverted);
    }

    private static void addAll(ChangeSet target, Map<BlockVector3, BaseBlock> blocks) {
        boolean recording = target.isRecordingChanges();
        target.setRecordChanges(true);
        try {
            for (Map.Entry<BlockVector3, BaseBlock> entry : blocks.entrySet()) {
                target.add(new BlockChange(entry.getKey(), entry.getValue(), entry.getValue()));
            }
        } finally {
            target.setRecordChanges(recording);
        }
    }
}

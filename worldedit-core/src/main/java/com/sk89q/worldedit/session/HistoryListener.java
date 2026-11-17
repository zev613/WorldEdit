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

import com.sk89q.worldedit.EditSession;

import java.util.Collection;

/**
 * Observes changes to a {@link com.sk89q.worldedit.LocalSession}'s undo history.
 *
 * <p>All methods are called on the thread that modifies the history.</p>
 */
public interface HistoryListener {

    /**
     * Called after an edit was added to the history.
     *
     * @param editSession the remembered edit
     * @param history the complete in-memory history after the change, oldest first
     */
    void onRemember(EditSession editSession, Collection<EditSession> history);

    /**
     * Called after an edit was undone.
     *
     * @param editSession the edit
     */
    void onUndo(EditSession editSession);

    /**
     * Called after an edit was redone.
     *
     * @param editSession the edit
     */
    void onRedo(EditSession editSession);

    /**
     * Called after the change set of an edit in the history was modified in
     * place (for example by a selective undo).
     *
     * @param editSession the edit
     */
    void onModified(EditSession editSession);

    /**
     * Called after the history was cleared.
     */
    void onClear();
}

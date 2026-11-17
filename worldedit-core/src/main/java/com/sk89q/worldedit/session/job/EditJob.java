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

package com.sk89q.worldedit.session.job;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.world.World;

import javax.annotation.Nullable;

/**
 * An edit whose changes are being applied to the world over several ticks.
 */
public final class EditJob {

    private final int id;
    private final EditSession editSession;
    private final @Nullable Actor actor;
    private final World world;
    private final int blockCount;
    private final long startedNanos = System.nanoTime();
    private @Nullable Operation remaining;
    private long lastProgressMessageNanos = startedNanos;
    private boolean failed;

    EditJob(int id, EditSession editSession, Operation operation, @Nullable Actor actor, World world) {
        this.id = id;
        this.editSession = editSession;
        this.remaining = operation;
        this.actor = actor;
        this.world = world;
        this.blockCount = editSession.getBlockChangeCount();
    }

    public int getId() {
        return id;
    }

    public EditSession getEditSession() {
        return editSession;
    }

    public @Nullable Actor getActor() {
        return actor;
    }

    public World getWorld() {
        return world;
    }

    /**
     * Get the number of blocks this edit changes.
     *
     * @return the number of blocks
     */
    public int getBlockCount() {
        return blockCount;
    }

    /**
     * Get the number of seconds since the job was queued.
     *
     * @return the elapsed time in seconds
     */
    public double getElapsedSeconds() {
        return (System.nanoTime() - startedNanos) / 1_000_000_000.0;
    }

    @Nullable Operation getRemaining() {
        return remaining;
    }

    void setRemaining(@Nullable Operation remaining) {
        this.remaining = remaining;
    }

    void markFailed() {
        failed = true;
    }

    /**
     * Check whether applying this job failed part-way.
     *
     * @return true if it failed
     */
    public boolean isFailed() {
        return failed;
    }

    boolean isProgressMessageDue(long intervalNanos) {
        long now = System.nanoTime();
        if (now - lastProgressMessageNanos >= intervalNanos) {
            lastProgressMessageNanos = now;
            return true;
        }
        return false;
    }
}

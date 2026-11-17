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
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.operation.TimeBudgetRunContext;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * Applies large edits over several server ticks, so that a big edit does not freeze the server
 * (the approach AsyncWorldEdit used).
 *
 * <p>An edit is still computed in full when its command runs; only writing the buffered changes
 * into the world is spread out. Jobs are applied in order. To keep later edits consistent with the
 * world, any queued jobs in a world are finished before a new edit session for that world is
 * created (see {@link #completeJobs(World)}).</p>
 *
 * <p>The platform must call {@link #tick()} once per server tick from the server thread. If it
 * does not, edits are applied immediately as usual.</p>
 */
public final class EditJobQueue {

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private static final long TICKING_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final long PROGRESS_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final WorldEdit worldEdit;
    private final Deque<EditJob> jobs = new ArrayDeque<>();
    private int nextId = 1;
    private long lastTickNanos;
    private boolean ticked;
    private boolean running;

    public EditJobQueue(WorldEdit worldEdit) {
        this.worldEdit = worldEdit;
    }

    /**
     * Check whether the platform is ticking this queue, i.e. whether edits can be deferred.
     *
     * @return true if {@link #tick()} has been called recently
     */
    public synchronized boolean isTicking() {
        return ticked && System.nanoTime() - lastTickNanos < TICKING_TIMEOUT_NANOS;
    }

    /**
     * Check whether the given edit session should be applied over several ticks.
     *
     * @param editSession the edit session
     * @return true if it should be deferred
     */
    public boolean shouldDefer(EditSession editSession) {
        LocalConfiguration config = worldEdit.getConfiguration();
        return config.timeSlicedEdits
            && editSession.getBlockChangeCount() >= Math.max(1, config.timeSlicedEditThreshold)
            && isTicking();
    }

    /**
     * Queue the remaining work of an edit session.
     *
     * @param editSession the edit session
     * @param operation the operation that applies the session's buffered changes
     * @param actor the actor to notify, if any
     * @return the job
     */
    public synchronized EditJob submit(EditSession editSession, Operation operation, @Nullable Actor actor) {
        World world = editSession.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot queue an edit session without a world");
        }
        EditJob job = new EditJob(nextId++, editSession, operation, actor, world);
        jobs.addLast(job);
        notify(job, TextComponent.of(String.format(
            "Applying %,d block changes over the next few ticks (job #%d). Use //jobs to see progress.",
            job.getBlockCount(), job.getId()
        )));
        return job;
    }

    /**
     * Apply queued jobs for up to the configured time budget. Must be called from the server thread.
     */
    public synchronized void tick() {
        lastTickNanos = System.nanoTime();
        ticked = true;
        if (jobs.isEmpty() || running) {
            return;
        }
        int budget = Math.max(1, worldEdit.getConfiguration().timeSlicedEditTickBudget);
        TimeBudgetRunContext context = TimeBudgetRunContext.ofMillis(budget);
        running = true;
        try {
            while (!jobs.isEmpty()) {
                EditJob job = jobs.peekFirst();
                if (!step(job, context)) {
                    if (job.isProgressMessageDue(PROGRESS_INTERVAL_NANOS)) {
                        notify(job, TextComponent.of(String.format(
                            "Job #%d is still being applied (%.0fs so far).", job.getId(), job.getElapsedSeconds()
                        )));
                    }
                    return;
                }
                jobs.pollFirst();
                finished(job);
                if (!context.shouldContinue()) {
                    return;
                }
            }
        } finally {
            running = false;
        }
    }

    /**
     * Run a job until it is done or the context says to stop.
     *
     * @return true if the job is complete (or failed and was abandoned)
     */
    private boolean step(EditJob job, TimeBudgetRunContext context) {
        Operation remaining = job.getRemaining();
        while (remaining != null) {
            try {
                remaining = remaining.resume(context);
            } catch (WorldEditException | RuntimeException e) {
                LOGGER.error("Failed to apply queued edit job #{}", job.getId(), e);
                notify(job, TextComponent.of("Job #" + job.getId() + " failed part-way: " + e.getMessage()
                    + ". Use //undo to revert what was applied."));
                job.setRemaining(null);
                job.markFailed();
                return true;
            }
            job.setRemaining(remaining);
            if (remaining != null && !context.shouldContinue()) {
                return false;
            }
        }
        return true;
    }

    private void finished(EditJob job) {
        if (job.isFailed()) {
            return;
        }
        notify(job, TextComponent.of(String.format(
            "Job #%d finished: %,d blocks applied in %.1fs.", job.getId(), job.getBlockCount(), job.getElapsedSeconds()
        )));
    }

    /**
     * Immediately finish all queued jobs in the given world, or in every world if {@code null}.
     *
     * @param world the world, or null for all worlds
     */
    public synchronized void completeJobs(@Nullable World world) {
        if (jobs.isEmpty() || running) {
            return;
        }
        running = true;
        try {
            Iterator<EditJob> it = jobs.iterator();
            while (it.hasNext()) {
                EditJob job = it.next();
                if (world != null && !job.getWorld().equals(world)) {
                    continue;
                }
                Operation remaining = job.getRemaining();
                if (remaining != null) {
                    try {
                        Operations.complete(remaining);
                    } catch (WorldEditException | RuntimeException e) {
                        LOGGER.error("Failed to apply queued edit job #{}", job.getId(), e);
                        notify(job, TextComponent.of("Job #" + job.getId() + " failed part-way: " + e.getMessage()));
                        job.markFailed();
                    }
                    job.setRemaining(null);
                }
                it.remove();
                finished(job);
            }
        } finally {
            running = false;
        }
    }

    /**
     * Stop applying a job. Changes already applied stay in the world; the edit is still in the
     * owner's history, so {@code //undo} reverts the part that was applied.
     *
     * @param id the job id
     * @return the cancelled job, or null if there was no such job
     */
    public synchronized @Nullable EditJob cancel(int id) {
        Iterator<EditJob> it = jobs.iterator();
        while (it.hasNext()) {
            EditJob job = it.next();
            if (job.getId() == id) {
                Operation remaining = job.getRemaining();
                if (remaining != null) {
                    remaining.cancel();
                    job.setRemaining(null);
                }
                it.remove();
                return job;
            }
        }
        return null;
    }

    /**
     * Get the queued jobs, in the order they will be applied.
     *
     * @return a snapshot of the queued jobs
     */
    public synchronized List<EditJob> getJobs() {
        return List.copyOf(jobs);
    }

    private static void notify(EditJob job, Component message) {
        Actor actor = job.getActor();
        if (actor == null) {
            return;
        }
        try {
            actor.printInfo(message);
        } catch (RuntimeException e) {
            // The actor may have disconnected; the job carries on regardless
            LOGGER.debug("Could not notify actor about edit job #{}", job.getId(), e);
        }
    }
}

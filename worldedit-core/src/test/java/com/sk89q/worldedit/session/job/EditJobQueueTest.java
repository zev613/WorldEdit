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
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.RunContext;
import com.sk89q.worldedit.world.World;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EditJobQueueTest {

    private final LocalConfiguration config = new LocalConfiguration() {
        @Override
        public void load() {
        }
    };
    private final WorldEdit worldEdit = mock(WorldEdit.class);
    private final EditJobQueue queue;

    EditJobQueueTest() {
        when(worldEdit.getConfiguration()).thenReturn(config);
        config.timeSlicedEditThreshold = 100;
        config.timeSlicedEditTickBudget = 5;
        queue = new EditJobQueue(worldEdit);
    }

    /**
     * An operation that needs {@code units} calls to shouldContinue-checked work units to finish.
     */
    private static final class CountingOperation implements Operation {
        private int remaining;
        private int resumes;
        private boolean cancelled;

        CountingOperation(int units) {
            this.remaining = units;
        }

        @Override
        public Operation resume(RunContext run) {
            resumes++;
            while (remaining > 0) {
                if (!run.shouldContinue()) {
                    return this;
                }
                remaining--;
                // Simulate some work so the time budget matters
                long until = System.nanoTime() + 100_000;
                while (System.nanoTime() < until) {
                    Thread.onSpinWait();
                }
            }
            return null;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private EditSession session(World world, int changes) {
        EditSession editSession = mock(EditSession.class);
        when(editSession.getWorld()).thenReturn(world);
        when(editSession.getBlockChangeCount()).thenReturn(changes);
        return editSession;
    }

    @Test
    void onlyDefersLargeEditsOnceTicking() {
        World world = mock(World.class);
        assertFalse(queue.isTicking());
        assertFalse(queue.shouldDefer(session(world, 1000)), "should not defer before the platform ticks");
        queue.tick();
        assertTrue(queue.isTicking());
        assertTrue(queue.shouldDefer(session(world, 1000)));
        assertFalse(queue.shouldDefer(session(world, 99)));
        config.timeSlicedEdits = false;
        assertFalse(queue.shouldDefer(session(world, 1000)));
    }

    @Test
    void spreadsWorkOverTicksAndNotifiesActor() {
        World world = mock(World.class);
        Actor actor = mock(Actor.class);
        // ~1000 units * 0.1ms = ~100ms of work, budget 5ms per tick
        CountingOperation op = new CountingOperation(1000);
        queue.submit(session(world, 1000), op, actor);
        queue.tick();
        assertEquals(1, queue.getJobs().size(), "a single tick should not finish the job");
        assertTrue(op.remaining > 0 && op.remaining < 1000, "some progress should have been made");
        int ticks = 1;
        while (!queue.getJobs().isEmpty() && ticks < 1000) {
            queue.tick();
            ticks++;
        }
        assertTrue(queue.getJobs().isEmpty());
        assertEquals(0, op.remaining);
        assertTrue(ticks > 5, "work should have been spread over several ticks, took " + ticks);
        verify(actor, atLeastOnce()).printInfo(any());
    }

    @Test
    void jobsRunInOrder() {
        World world = mock(World.class);
        CountingOperation first = new CountingOperation(200);
        CountingOperation second = new CountingOperation(1);
        queue.submit(session(world, 1000), first, null);
        queue.submit(session(world, 1000), second, null);
        queue.tick();
        assertEquals(0, second.resumes, "second job must wait for the first");
    }

    @Test
    void completeJobsOnlyFinishesTheGivenWorld() {
        World a = mock(World.class);
        World b = mock(World.class);
        CountingOperation inA = new CountingOperation(50);
        CountingOperation inB = new CountingOperation(50);
        queue.submit(session(a, 1000), inA, null);
        queue.submit(session(b, 1000), inB, null);
        queue.completeJobs(b);
        assertEquals(0, inB.remaining);
        assertEquals(50, inA.remaining);
        assertEquals(1, queue.getJobs().size());
        queue.completeJobs(null);
        assertTrue(queue.getJobs().isEmpty());
        assertEquals(0, inA.remaining);
    }

    @Test
    void cancelStopsTheJob() {
        World world = mock(World.class);
        CountingOperation op = new CountingOperation(50);
        EditJob job = queue.submit(session(world, 1000), op, null);
        assertNotNull(queue.cancel(job.getId()));
        assertTrue(op.cancelled);
        assertTrue(queue.getJobs().isEmpty());
        assertEquals(null, queue.cancel(job.getId()));
    }

    @Test
    void failingJobIsDroppedAndOthersContinue() {
        World world = mock(World.class);
        AtomicBoolean secondRan = new AtomicBoolean();
        queue.submit(session(world, 1000), new Operation() {
            @Override
            public Operation resume(RunContext run) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void cancel() {
            }
        }, mock(Actor.class));
        queue.submit(session(world, 1000), new Operation() {
            @Override
            public Operation resume(RunContext run) {
                secondRan.set(true);
                return null;
            }

            @Override
            public void cancel() {
            }
        }, null);
        for (int i = 0; i < 10 && !queue.getJobs().isEmpty(); i++) {
            queue.tick();
        }
        assertTrue(secondRan.get());
        assertTrue(queue.getJobs().isEmpty());
    }
}

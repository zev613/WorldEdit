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

package com.sk89q.worldedit.function.operation;

/**
 * A {@link RunContext} that allows an operation to run until a deadline,
 * so that long operations can be spread over several server ticks.
 */
public class TimeBudgetRunContext extends RunContext {

    private final long deadlineNanos;

    /**
     * Create a new context that allows running until the given deadline.
     *
     * @param deadlineNanos the deadline, as a {@link System#nanoTime()} value
     */
    public TimeBudgetRunContext(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    /**
     * Create a new context that allows running for the given number of milliseconds from now.
     *
     * @param millis the time budget in milliseconds
     * @return the context
     */
    public static TimeBudgetRunContext ofMillis(long millis) {
        return new TimeBudgetRunContext(System.nanoTime() + millis * 1_000_000L);
    }

    @Override
    public boolean shouldContinue() {
        return System.nanoTime() - deadlineNanos < 0;
    }
}

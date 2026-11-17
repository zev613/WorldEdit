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

package com.sk89q.worldedit.util.buildtools;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Accumulates matches for {@code //find}: the total count, the nearest
 * {@code limit} matches to an origin (bounded heap, so memory stays small),
 * and per-chunk counts.
 */
public final class FindResults {

    private final BlockVector3 origin;
    private final int limit;
    private final boolean trackChunks;
    private final Comparator<BlockVector3> nearestFirst;
    // Max-heap on distance: the head is the farthest of the kept matches
    private final PriorityQueue<BlockVector3> nearest;
    private final Map<BlockVector2, Integer> chunkCounts = new HashMap<>();
    private long total;

    /**
     * Create a new accumulator.
     *
     * @param origin the position distances are measured from
     * @param limit how many of the nearest matches to keep
     * @param trackChunks whether to count matches per chunk
     */
    public FindResults(BlockVector3 origin, int limit, boolean trackChunks) {
        checkArgument(limit >= 0, "limit must be >= 0");
        this.origin = checkNotNull(origin);
        this.limit = limit;
        this.trackChunks = trackChunks;
        this.nearestFirst = Comparator.<BlockVector3>comparingLong(this::distanceSq)
            .thenComparingInt(BlockVector3::y)
            .thenComparingInt(BlockVector3::x)
            .thenComparingInt(BlockVector3::z);
        this.nearest = new PriorityQueue<>(Math.max(1, limit + 1), nearestFirst.reversed());
    }

    /**
     * Squared distance from the origin.
     */
    public long distanceSq(BlockVector3 position) {
        long dx = (long) position.x() - origin.x();
        long dy = (long) position.y() - origin.y();
        long dz = (long) position.z() - origin.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Record a match.
     *
     * @param position the matching position
     */
    public void add(BlockVector3 position) {
        total++;
        if (trackChunks) {
            chunkCounts.merge(BlockVector2.at(position.x() >> 4, position.z() >> 4), 1, Integer::sum);
        }
        if (limit == 0) {
            return;
        }
        if (nearest.size() < limit) {
            nearest.add(position);
        } else if (nearestFirst.compare(position, nearest.peek()) < 0) {
            nearest.poll();
            nearest.add(position);
        }
    }

    public long getTotal() {
        return total;
    }

    /**
     * Get the kept matches, nearest first.
     */
    public List<BlockVector3> getNearest() {
        List<BlockVector3> list = new ArrayList<>(nearest);
        list.sort(nearestFirst);
        return list;
    }

    /**
     * Get the chunks with the most matches, most first (ties: by chunk x, then z).
     *
     * @param count how many chunks to return
     * @return chunk positions (chunk coordinates) with counts
     */
    public List<Map.Entry<BlockVector2, Integer>> getTopChunks(int count) {
        List<Map.Entry<BlockVector2, Integer>> entries = new ArrayList<>(chunkCounts.entrySet());
        entries.sort(Comparator.<Map.Entry<BlockVector2, Integer>>comparingInt(Map.Entry::getValue).reversed()
            .thenComparingInt(e -> e.getKey().x())
            .thenComparingInt(e -> e.getKey().z()));
        return entries.subList(0, Math.min(count, entries.size()));
    }

    public int getChunkCount() {
        return chunkCounts.size();
    }
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A pure, grid based approximation of Minecraft block light used by
 * {@code //lightfill}, plus a greedy light placement algorithm.
 *
 * <p>Each cell has an attenuation cost ({@link #OPAQUE} blocks light
 * completely; otherwise light entering the cell loses {@code cost} levels)
 * and an emission level. Light at a cell is
 * {@code max(emission, max(neighbour light) - cost)}, computed with a
 * bucketed (Dijkstra-like) flood fill from all emitters. Opaque emitters
 * (e.g. glowstone) keep their own level and light their neighbours.</p>
 *
 * <p>Cells are addressed by {@code (x, y, z)} in {@code [0, size)}; anything
 * outside the grid is treated as opaque and dark.</p>
 */
public final class LightFillSolver {

    /** Cost value for cells that block light entirely. */
    public static final int OPAQUE = 0;
    /** Maximum light level. */
    public static final int MAX_LIGHT = 15;

    private static final int MAX_CANDIDATES = 96;

    private static final int[] DX = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 0, 0, 1, -1 };

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] cost;
    private final byte[] emission;
    private final byte[] light;
    private final boolean[] spawnable;
    private final IntQueue[] buckets = new IntQueue[MAX_LIGHT + 1];

    // Scratch space for coverage simulation
    private final byte[] scratchLight;
    private final int[] scratchStamp;
    private int stamp;

    /**
     * Create a new solver for a grid. All cells start transparent (cost 1),
     * non-emitting and not spawnable.
     *
     * @param sizeX size on X
     * @param sizeY size on Y
     * @param sizeZ size on Z
     */
    public LightFillSolver(int sizeX, int sizeY, int sizeZ) {
        checkArgument(sizeX > 0 && sizeY > 0 && sizeZ > 0, "grid must be non-empty");
        long volume = (long) sizeX * sizeY * sizeZ;
        checkArgument(volume <= Integer.MAX_VALUE / 2, "grid too large");
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        int n = (int) volume;
        this.cost = new byte[n];
        Arrays.fill(cost, (byte) 1);
        this.emission = new byte[n];
        this.light = new byte[n];
        this.spawnable = new boolean[n];
        this.scratchLight = new byte[n];
        this.scratchStamp = new int[n];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new IntQueue();
        }
    }

    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    /**
     * Set the properties of a cell.
     *
     * @param x x
     * @param y y
     * @param z z
     * @param attenuation {@link #OPAQUE} or the light lost entering the cell (1 or more)
     * @param emitted the emitted light level (0-15)
     */
    public void setCell(int x, int y, int z, int attenuation, int emitted) {
        int i = index(x, y, z);
        cost[i] = (byte) Math.max(0, Math.min(MAX_LIGHT, attenuation));
        emission[i] = (byte) Math.max(0, Math.min(MAX_LIGHT, emitted));
    }

    /**
     * Mark a cell as a spot where a hostile mob could spawn (and where a
     * light may be placed).
     */
    public void setSpawnable(int x, int y, int z, boolean value) {
        spawnable[index(x, y, z)] = value;
    }

    public boolean isSpawnable(int x, int y, int z) {
        return spawnable[index(x, y, z)];
    }

    public int getLight(int x, int y, int z) {
        return light[index(x, y, z)];
    }

    /**
     * Compute the light field from scratch from all emitters.
     */
    public void computeLight() {
        Arrays.fill(light, (byte) 0);
        for (int i = 0; i < light.length; i++) {
            if (emission[i] > 0) {
                light[i] = emission[i];
                buckets[emission[i]].add(i);
            }
        }
        propagateLight();
    }

    /**
     * Add a light source at a cell and update the light field incrementally.
     * The cell becomes transparent and non-spawnable.
     */
    public void addLight(int x, int y, int z, int level) {
        int i = index(x, y, z);
        cost[i] = 1;
        spawnable[i] = false;
        emission[i] = (byte) Math.max(emission[i], Math.min(MAX_LIGHT, level));
        if (emission[i] > light[i]) {
            light[i] = emission[i];
            buckets[light[i]].add(i);
            propagateLight();
        }
    }

    /**
     * Count the spawnable cells with light below {@code minLight}.
     */
    public int countDark(int minLight) {
        int count = 0;
        for (int i = 0; i < light.length; i++) {
            if (spawnable[i] && light[i] < minLight) {
                count++;
            }
        }
        return count;
    }

    /**
     * Greedily place light sources until every spawnable cell has at least
     * {@code minLight} light, or {@code maxLights} have been placed.
     *
     * <p>Algorithm: take the first dark spawnable cell in scan order
     * (bottom-up) as the seed. Candidate positions are spawnable cells within
     * {@code maxDistance} light-steps of the seed (plus the seed itself; a
     * sample is used if there are many). For each candidate, simulate the
     * light it would add and count how many dark cells it would light; pick
     * the candidate that lights the seed and the most dark cells (ties: the
     * one farthest from the seed). The seed always gets lit, so the loop
     * terminates.</p>
     *
     * @param minLight required light level (1-15)
     * @param level the light level of the placed source
     * @param maxDistance how far (in light steps) from an uncovered spot a
     *     light may be placed; smaller values give denser, more overlapping lights
     * @param maxLights maximum number of lights to place
     * @return placed positions as {@code {x, y, z}} arrays, in order
     */
    public List<int[]> placeLights(int minLight, int level, int maxDistance, int maxLights) {
        checkArgument(minLight >= 1 && minLight <= MAX_LIGHT, "minLight must be 1-15");
        checkArgument(level >= minLight && level <= MAX_LIGHT, "light level must be >= minLight");
        int reach = Math.max(0, Math.min(maxDistance, level - minLight));
        List<int[]> placed = new ArrayList<>();
        int cursor = 0;
        while (placed.size() < maxLights) {
            while (cursor < light.length && !(spawnable[cursor] && light[cursor] < minLight)) {
                cursor++;
            }
            if (cursor >= light.length) {
                break;
            }
            int seed = cursor;
            int best = chooseCandidate(seed, minLight, level, reach);
            int bx = best % sizeX;
            int bz = (best / sizeX) % sizeZ;
            int by = best / (sizeX * sizeZ);
            addLight(bx, by, bz, level);
            placed.add(new int[] { bx, by, bz });
        }
        return placed;
    }

    private int chooseCandidate(int seed, int minLight, int level, int reach) {
        // Gather spawnable cells reachable from the seed within `reach` light steps:
        // flood a pseudo-light of (reach + 1) from the seed; distance = reach + 1 - value.
        List<int[]> reachable = new ArrayList<>(); // {index, distance}
        if (reach > 0) {
            nextStamp();
            raiseScratch(seed, reach + 1);
            propagateScratch(0, -1, null, reachable, reach + 1);
        }
        reachable.removeIf(entry -> entry[0] == seed || !spawnable[entry[0]]);
        // Farthest first, then sample evenly
        reachable.sort((a, b) -> Integer.compare(b[1], a[1]));
        List<int[]> candidates = new ArrayList<>();
        candidates.add(new int[] { seed, 0 });
        int step = Math.max(1, (reachable.size() + MAX_CANDIDATES - 1) / MAX_CANDIDATES);
        for (int k = 0; k < reachable.size(); k += step) {
            candidates.add(reachable.get(k));
        }

        int best = seed;
        long bestScore = Long.MIN_VALUE;
        for (int[] candidate : candidates) {
            int[] counter = new int[2]; // {newly lit dark cells, seed lit}
            nextStamp();
            raiseScratch(candidate[0], level);
            countIfNewlyLit(candidate[0], light[candidate[0]], level, minLight, seed, counter);
            propagateScratch(minLight, seed, counter, null, 0);
            if (counter[1] == 0) {
                continue;
            }
            long score = (long) counter[0] * 1024 + candidate[1];
            if (score > bestScore) {
                bestScore = score;
                best = candidate[0];
            }
        }
        return best;
    }

    private void nextStamp() {
        stamp++;
        if (stamp == Integer.MAX_VALUE) {
            Arrays.fill(scratchStamp, 0);
            stamp = 1;
        }
    }

    private int scratchValue(int i) {
        return scratchStamp[i] == stamp ? scratchLight[i] : 0;
    }

    private void raiseScratch(int i, int value) {
        scratchLight[i] = (byte) value;
        scratchStamp[i] = stamp;
        buckets[value].add(i);
    }

    private void countIfNewlyLit(int i, int previous, int value, int minLight, int seed, int[] counter) {
        if (spawnable[i] && previous < minLight && value >= minLight) {
            counter[0]++;
            if (i == seed) {
                counter[1] = 1;
            }
        }
    }

    /**
     * Propagate queued cells through the scratch field without touching the
     * real light field.
     *
     * <p>Coverage mode ({@code reachable == null}): only raise cells whose
     * real light is lower (the existing light already dominates elsewhere),
     * counting dark spawnable cells that become lit. Reach mode: ignore the
     * real light and record every raised cell with its distance.</p>
     */
    private void propagateScratch(int minLight, int seed, int[] counter, List<int[]> reachable, int start) {
        for (int level = MAX_LIGHT; level >= 1; level--) {
            IntQueue queue = buckets[level];
            while (!queue.isEmpty()) {
                int i = queue.poll();
                if (scratchValue(i) != level) {
                    continue;
                }
                int x = i % sizeX;
                int z = (i / sizeX) % sizeZ;
                int y = i / (sizeX * sizeZ);
                for (int d = 0; d < 6; d++) {
                    int nx = x + DX[d];
                    int ny = y + DY[d];
                    int nz = z + DZ[d];
                    if (!inBounds(nx, ny, nz)) {
                        continue;
                    }
                    int n = index(nx, ny, nz);
                    int c = cost[n];
                    if (c == OPAQUE) {
                        continue;
                    }
                    int value = level - c;
                    int previousScratch = scratchValue(n);
                    if (value <= 0 || value <= previousScratch) {
                        continue;
                    }
                    if (reachable == null) {
                        if (value <= light[n]) {
                            continue;
                        }
                        countIfNewlyLit(n, Math.max(light[n], previousScratch), value, minLight, seed, counter);
                    } else {
                        reachable.add(new int[] { n, start - value });
                    }
                    raiseScratch(n, value);
                }
            }
        }
    }

    /**
     * Propagate queued cells through the real light field.
     */
    private void propagateLight() {
        for (int level = MAX_LIGHT; level >= 1; level--) {
            IntQueue queue = buckets[level];
            while (!queue.isEmpty()) {
                int i = queue.poll();
                if (light[i] != level) {
                    continue;
                }
                int x = i % sizeX;
                int z = (i / sizeX) % sizeZ;
                int y = i / (sizeX * sizeZ);
                for (int d = 0; d < 6; d++) {
                    int nx = x + DX[d];
                    int ny = y + DY[d];
                    int nz = z + DZ[d];
                    if (!inBounds(nx, ny, nz)) {
                        continue;
                    }
                    int n = index(nx, ny, nz);
                    int c = cost[n];
                    if (c == OPAQUE) {
                        continue;
                    }
                    int value = level - c;
                    if (value <= 0 || value <= light[n]) {
                        continue;
                    }
                    light[n] = (byte) value;
                    buckets[value].add(n);
                }
            }
        }
    }

    /**
     * A minimal growable int FIFO queue.
     */
    private static final class IntQueue {
        private int[] data = new int[64];
        private int head;
        private int tail;

        void add(int value) {
            if (tail == data.length) {
                if (head > data.length / 2) {
                    System.arraycopy(data, head, data, 0, tail - head);
                    tail -= head;
                    head = 0;
                } else {
                    data = Arrays.copyOf(data, data.length * 2);
                }
            }
            data[tail++] = value;
        }

        boolean isEmpty() {
            return head == tail;
        }

        int poll() {
            int value = data[head++];
            if (head == tail) {
                head = 0;
                tail = 0;
            }
            return value;
        }
    }
}

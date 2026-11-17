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

package com.sk89q.worldedit.function.generator.landscape;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Generates a parametric tree: a (possibly leaning) trunk, branches from the upper part of the
 * trunk ending in leaf blobs, and a crown blob on top.
 *
 * <p>All coordinates are relative to the base of the trunk.</p>
 */
public final class ParametricTree {

    /**
     * The axis a log block is aligned with.
     */
    public enum Axis {
        X, Y, Z;

        /**
         * Gets the axis of the largest component of a direction.
         *
         * @param direction the direction
         * @return the axis
         */
        public static Axis dominant(Vector3 direction) {
            double ax = Math.abs(direction.x());
            double ay = Math.abs(direction.y());
            double az = Math.abs(direction.z());
            if (ay >= ax && ay >= az) {
                return Y;
            }
            return ax >= az ? X : Z;
        }
    }

    /**
     * A straight piece of wood.
     *
     * @param from the start
     * @param to the end
     * @param thickness 1 for a single block line, 2 for a 2x2 line
     */
    public record Segment(Vector3 from, Vector3 to, int thickness) {
    }

    /**
     * An ellipsoid of leaves.
     *
     * @param center the centre
     * @param radiusXZ the horizontal radius
     * @param radiusY the vertical radius
     */
    public record LeafBlob(Vector3 center, double radiusXZ, double radiusY) {
    }

    /**
     * The tree's skeleton.
     *
     * @param segments the wood segments
     * @param blobs the leaf blobs
     */
    public record Skeleton(List<Segment> segments, List<LeafBlob> blobs) {
    }

    /**
     * The rasterized tree.
     *
     * @param logs the log positions and their axes
     * @param leaves the leaf positions (never overlapping logs)
     */
    public record Voxels(Map<BlockVector3, Axis> logs, Set<BlockVector3> leaves) {
    }

    private static final int TRUNK_PIECES = 4;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private ParametricTree() {
    }

    /**
     * Generate a skeleton.
     *
     * @param random the random source
     * @param height the trunk height (at least 2)
     * @param branches the number of branches (at least 0)
     * @param leafRadius the base leaf blob radius (at least 1)
     * @param leanDegrees the lean of the trunk top from vertical, in degrees (0 to 60)
     * @param thickness the trunk thickness (1 or 2)
     * @return the skeleton
     */
    public static Skeleton generate(Random random, int height, int branches, double leafRadius,
                                    double leanDegrees, int thickness) {
        checkArgument(height >= 2, "height must be >= 2");
        checkArgument(branches >= 0, "branches must be >= 0");
        checkArgument(leafRadius >= 1, "leafRadius must be >= 1");
        checkArgument(leanDegrees >= 0 && leanDegrees <= 60, "lean must be between 0 and 60 degrees");
        checkArgument(thickness == 1 || thickness == 2, "thickness must be 1 or 2");

        List<Segment> segments = new ArrayList<>();
        List<LeafBlob> blobs = new ArrayList<>();

        // Trunk: a few pieces whose tilt grows towards the top, giving a gentle curve.
        double leanAzimuth = random.nextDouble() * Math.PI * 2;
        double leanRad = Math.toRadians(leanDegrees);
        double pieceLength = (height - 1.0) / TRUNK_PIECES;
        List<Vector3> trunk = new ArrayList<>();
        Vector3 point = Vector3.ZERO;
        trunk.add(point);
        for (int i = 0; i < TRUNK_PIECES; i++) {
            double tilt = leanRad * (i + 1) / TRUNK_PIECES;
            Vector3 direction = Vector3.at(
                Math.sin(tilt) * Math.cos(leanAzimuth),
                Math.cos(tilt),
                Math.sin(tilt) * Math.sin(leanAzimuth)
            );
            Vector3 next = point.add(direction.multiply(pieceLength));
            segments.add(new Segment(point, next, thickness));
            point = next;
            trunk.add(point);
        }
        Vector3 top = point;

        // Branches from the upper 60% of the trunk.
        double azimuthOffset = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < branches; i++) {
            double fraction = branches == 1 ? 0.7 : 0.4 + 0.55 * i / (branches - 1);
            fraction = Math.min(0.95, Math.max(0.4, fraction + (random.nextDouble() - 0.5) * 0.08));
            Vector3 origin = alongTrunk(trunk, fraction);
            double azimuth = azimuthOffset + i * GOLDEN_ANGLE + (random.nextDouble() - 0.5) * 0.6;
            double elevation = Math.toRadians(20 + random.nextDouble() * 30);
            double length = Math.max(2, height * (0.45 - 0.2 * (fraction - 0.4) / 0.55) * (0.8 + random.nextDouble() * 0.4));
            Vector3 direction = Vector3.at(
                Math.cos(elevation) * Math.cos(azimuth),
                Math.sin(elevation),
                Math.cos(elevation) * Math.sin(azimuth)
            );
            Vector3 end = origin.add(direction.multiply(length));
            segments.add(new Segment(origin, end, 1));
            double radius = leafRadius * (0.8 + random.nextDouble() * 0.4);
            blobs.add(new LeafBlob(end, radius, radius * 0.75));
        }

        double crown = leafRadius * (1.1 + random.nextDouble() * 0.3);
        blobs.add(new LeafBlob(top, crown, crown * 0.85));
        return new Skeleton(segments, blobs);
    }

    private static Vector3 alongTrunk(List<Vector3> trunk, double fraction) {
        double position = fraction * (trunk.size() - 1);
        int index = Math.min(trunk.size() - 2, (int) Math.floor(position));
        double t = position - index;
        Vector3 a = trunk.get(index);
        Vector3 b = trunk.get(index + 1);
        return a.add(b.subtract(a).multiply(t));
    }

    /**
     * Rasterize a skeleton into blocks.
     *
     * @param skeleton the skeleton
     * @param random the random source, used to roughen leaf blob edges
     * @return the blocks
     */
    public static Voxels rasterize(Skeleton skeleton, Random random) {
        Map<BlockVector3, Axis> logs = new LinkedHashMap<>();
        for (Segment segment : skeleton.segments()) {
            Vector3 delta = segment.to().subtract(segment.from());
            Axis axis = Axis.dominant(delta);
            double length = delta.length();
            int steps = Math.max(1, (int) Math.ceil(length * 3));
            for (int s = 0; s <= steps; s++) {
                Vector3 p = segment.from().add(delta.multiply((double) s / steps));
                BlockVector3 block = BlockVector3.at(Math.floor(p.x() + 0.5), Math.floor(p.y() + 0.5), Math.floor(p.z() + 0.5));
                // keep the first assignment so the trunk keeps its vertical axis where branches start
                logs.putIfAbsent(block, axis);
                if (segment.thickness() == 2) {
                    logs.putIfAbsent(block.add(1, 0, 0), axis);
                    logs.putIfAbsent(block.add(0, 0, 1), axis);
                    logs.putIfAbsent(block.add(1, 0, 1), axis);
                }
            }
        }

        Set<BlockVector3> leaves = new LinkedHashSet<>();
        for (LeafBlob blob : skeleton.blobs()) {
            Vector3 c = blob.center();
            int rx = (int) Math.ceil(blob.radiusXZ());
            int ry = (int) Math.ceil(blob.radiusY());
            int cx = (int) Math.floor(c.x() + 0.5);
            int cy = (int) Math.floor(c.y() + 0.5);
            int cz = (int) Math.floor(c.z() + 0.5);
            for (int x = cx - rx; x <= cx + rx; x++) {
                for (int y = cy - ry; y <= cy + ry; y++) {
                    for (int z = cz - rx; z <= cz + rx; z++) {
                        double nx = (x - c.x()) / (blob.radiusXZ() + 0.5);
                        double ny = (y - c.y()) / (blob.radiusY() + 0.5);
                        double nz = (z - c.z()) / (blob.radiusXZ() + 0.5);
                        double d = nx * nx + ny * ny + nz * nz;
                        if (d > 1) {
                            continue;
                        }
                        // roughen the outer shell a little
                        if (d > 0.7 && random.nextDouble() < 0.25) {
                            continue;
                        }
                        BlockVector3 block = BlockVector3.at(x, y, z);
                        if (!logs.containsKey(block)) {
                            leaves.add(block);
                        }
                    }
                }
            }
        }
        return new Voxels(logs, leaves);
    }
}

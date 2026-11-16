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

package com.sk89q.worldedit.function.pattern;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A pattern that blends smoothly between a list of sub-patterns along an axis with dithering.
 */
public class GradientPattern extends AbstractPattern {

    private final char axis;
    private final double minCoord;
    private final double maxCoord;
    private final List<Pattern> patterns;

    public GradientPattern(char axis, double minCoord, double maxCoord, List<Pattern> patterns) {
        checkNotNull(patterns, "patterns cannot be null");
        checkArgument(!patterns.isEmpty(), "patterns cannot be empty");
        this.axis = Character.toLowerCase(axis);
        if (minCoord >= maxCoord) {
            this.minCoord = minCoord;
            this.maxCoord = minCoord + 16.0;
        } else {
            this.minCoord = minCoord;
            this.maxCoord = maxCoord;
        }
        this.patterns = patterns;
    }

    @Override
    public BaseBlock applyBlock(BlockVector3 position) {
        if (patterns.size() == 1) {
            return patterns.get(0).applyBlock(position);
        }

        double val = switch (axis) {
            case 'x' -> position.x();
            case 'z' -> position.z();
            default -> position.y();
        };

        double t = (val - minCoord) / (maxCoord - minCoord);
        t = Math.max(0.0, Math.min(1.0, t));

        double posInPalette = t * (patterns.size() - 1);
        int index = (int) Math.floor(posInPalette);
        double fraction = posInPalette - index;

        if (index >= patterns.size() - 1) {
            return patterns.get(patterns.size() - 1).applyBlock(position);
        }

        double dither = pseudoRandom(position.x(), position.y(), position.z());
        int chosen = (dither < fraction) ? (index + 1) : index;
        return patterns.get(chosen).applyBlock(position);
    }

    private static double pseudoRandom(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 326648991;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }
}

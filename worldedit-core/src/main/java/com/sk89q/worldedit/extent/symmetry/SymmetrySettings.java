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

package com.sk89q.worldedit.extent.symmetry;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An immutable description of a player's symmetry plane(s).
 *
 * <p>Plane coordinates are stored doubled so that a plane may pass either
 * through the centre of a block ({@code doubledX} even) or along the
 * boundary between two blocks ({@code doubledX} odd, e.g. the centre of a
 * selection with an even width). A block at {@code x} mirrors to
 * {@code doubledX - x}.</p>
 *
 * @param mirrorX whether to mirror across the plane perpendicular to the X axis
 * @param mirrorZ whether to mirror across the plane perpendicular to the Z axis
 * @param doubledX twice the X coordinate of the X plane (in block-centre space)
 * @param doubledZ twice the Z coordinate of the Z plane (in block-centre space)
 * @param worldName the world the planes were set in
 */
public record SymmetrySettings(boolean mirrorX, boolean mirrorZ, int doubledX, int doubledZ, String worldName) {

    /**
     * A mirrored copy of a position.
     *
     * @param position the mirrored position
     * @param flipX whether the X axis was flipped
     * @param flipZ whether the Z axis was flipped
     */
    public record Mirror(BlockVector3 position, boolean flipX, boolean flipZ) {
    }

    /**
     * Compute all mirror images of a position, excluding the position itself
     * and duplicates (which happen for positions on a plane).
     *
     * @param position the original position
     * @return the distinct mirror images
     */
    public List<Mirror> mirrorsOf(BlockVector3 position) {
        List<Mirror> result = new ArrayList<>(3);
        int mx = doubledX - position.x();
        int mz = doubledZ - position.z();
        if (mirrorX) {
            addIfNew(result, position, new Mirror(position.withX(mx), true, false));
        }
        if (mirrorZ) {
            addIfNew(result, position, new Mirror(position.withZ(mz), false, true));
        }
        if (mirrorX && mirrorZ) {
            addIfNew(result, position, new Mirror(BlockVector3.at(mx, position.y(), mz), true, true));
        }
        return result;
    }

    private static void addIfNew(List<Mirror> result, BlockVector3 original, Mirror mirror) {
        if (mirror.position().equals(original)) {
            return;
        }
        for (Mirror existing : result) {
            if (existing.position().equals(mirror.position())) {
                return;
            }
        }
        result.add(mirror);
    }

    /**
     * Describe the active planes for chat output.
     *
     * @return a human readable description
     */
    public String describe() {
        List<String> parts = new ArrayList<>(2);
        if (mirrorX) {
            parts.add("X mirror " + formatPlane("x", doubledX));
        }
        if (mirrorZ) {
            parts.add("Z mirror " + formatPlane("z", doubledZ));
        }
        return String.join(", ", parts) + " in " + worldName;
    }

    private static String formatPlane(String axis, int doubled) {
        int lower = Math.floorDiv(doubled, 2);
        if (Math.floorMod(doubled, 2) == 0) {
            return String.format(Locale.ROOT, "through block %s=%d", axis, lower);
        }
        return String.format(Locale.ROOT, "between blocks %s=%d and %s=%d", axis, lower, axis, lower + 1);
    }
}

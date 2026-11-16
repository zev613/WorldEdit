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

package com.sk89q.worldedit.function.mask;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;


/**
 * A mask that matches blocks based on their surface terrain slope in degrees.
 * Flat ground is 0 degrees, a 1:1 diagonal slope is 45 degrees, and sheer vertical cliffs approach 90 degrees.
 */
public class SlopeMask extends AbstractExtentMask {

    private final double minAngle;
    private final double maxAngle;

    /**
     * Create a new slope mask for a given angle range in degrees [0, 90].
     *
     * @param extent The extent to check against
     * @param minAngle The minimum slope angle in degrees
     * @param maxAngle The maximum slope angle in degrees
     */
    public SlopeMask(Extent extent, double minAngle, double maxAngle) {
        super(extent);
        this.minAngle = Math.max(0.0, Math.min(90.0, minAngle));
        this.maxAngle = Math.max(0.0, Math.min(90.0, maxAngle));
    }

    @Override
    public boolean test(BlockVector3 vector) {
        Extent extent = getExtent();
        BlockState current = extent.getBlock(vector);
        if (current.getBlockType().getMaterial().isAir()) {
            return false;
        }

        int x = vector.x();
        int y = vector.y();
        int z = vector.z();

        int hXPlus = findSurfaceY(extent, x + 1, y, z);
        int hXMinus = findSurfaceY(extent, x - 1, y, z);
        int hZPlus = findSurfaceY(extent, x, y, z + 1);
        int hZMinus = findSurfaceY(extent, x, y, z - 1);

        double dx = (hXPlus - hXMinus) / 2.0;
        double dz = (hZPlus - hZMinus) / 2.0;
        double gradient = Math.hypot(dx, dz);
        double angle = Math.toDegrees(Math.atan(gradient));

        return angle >= minAngle && angle <= maxAngle;
    }

    private static int findSurfaceY(Extent extent, int nx, int baseY, int nz) {
        // Search vertically within [-6, +6] for the highest solid block in the neighboring column
        for (int dy = 6; dy >= -6; dy--) {
            int checkY = baseY + dy;
            BlockState state = extent.getBlock(BlockVector3.at(nx, checkY, nz));
            if (state.getBlockType().getMaterial().isSolid()) {
                BlockState above = extent.getBlock(BlockVector3.at(nx, checkY + 1, nz));
                if (!above.getBlockType().getMaterial().isSolid()) {
                    return checkY;
                }
            }
        }
        return baseY;
    }
}

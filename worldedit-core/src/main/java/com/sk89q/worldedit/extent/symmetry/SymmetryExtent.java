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

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An extent that, for every block (and biome) written through it, also
 * writes a mirrored copy across the configured symmetry plane(s).
 *
 * <p>Mirrored writes are sent straight to the delegate extent, never back
 * through this extent, so they cannot recurse. When installed above the
 * history stage of an {@code EditSession}, both halves are recorded and
 * undone together.</p>
 */
public class SymmetryExtent extends AbstractDelegateExtent {

    private static final AffineTransform FLIP_X = new AffineTransform().scale(-1, 1, 1);
    private static final AffineTransform FLIP_Z = new AffineTransform().scale(1, 1, -1);
    private static final AffineTransform FLIP_XZ = new AffineTransform().scale(-1, 1, -1);

    private final SymmetrySettings settings;

    /**
     * Create a new instance.
     *
     * @param extent the extent to write to
     * @param settings the symmetry settings
     */
    public SymmetryExtent(Extent extent, SymmetrySettings settings) {
        super(extent);
        this.settings = checkNotNull(settings);
    }

    public SymmetrySettings getSettings() {
        return settings;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws WorldEditException {
        boolean changed = getExtent().setBlock(location, block);
        for (SymmetrySettings.Mirror mirror : settings.mirrorsOf(location)) {
            T mirrored = mirrorBlock(block, mirror.flipX(), mirror.flipZ());
            changed |= getExtent().setBlock(mirror.position(), mirrored);
        }
        return changed;
    }

    @Override
    public boolean setBiome(BlockVector3 position, BiomeType biome) {
        boolean changed = getExtent().setBiome(position, biome);
        for (SymmetrySettings.Mirror mirror : settings.mirrorsOf(position)) {
            changed |= getExtent().setBiome(mirror.position(), biome);
        }
        return changed;
    }

    /**
     * Mirror a block's state (facing, stair shape, chest/door sides, ...).
     *
     * @param block the block
     * @param flipX whether X is flipped
     * @param flipZ whether Z is flipped
     * @return the mirrored block
     */
    public static <T extends BlockStateHolder<T>> T mirrorBlock(T block, boolean flipX, boolean flipZ) {
        AffineTransform transform;
        if (flipX && flipZ) {
            transform = FLIP_XZ;
        } else if (flipX) {
            transform = FLIP_X;
        } else if (flipZ) {
            transform = FLIP_Z;
        } else {
            return block;
        }
        T result = BlockTransformExtent.transform(block, transform);
        if (flipX != flipZ) {
            // A single mirror swaps handedness; BlockTransformExtent does not handle door hinges.
            result = swapEnumValue(result, "hinge", "left", "right");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockStateHolder<T>> T swapEnumValue(T block, String propertyName, String first, String second) {
        Property<?> property = block.getBlockType().getPropertyMap().get(propertyName);
        if (property == null) {
            return block;
        }
        Object value = block.getState(property);
        String replacement;
        if (first.equals(value)) {
            replacement = second;
        } else if (second.equals(value)) {
            replacement = first;
        } else {
            return block;
        }
        try {
            return block.with((Property<Object>) property, replacement);
        } catch (IllegalArgumentException _) {
            return block;
        }
    }
}

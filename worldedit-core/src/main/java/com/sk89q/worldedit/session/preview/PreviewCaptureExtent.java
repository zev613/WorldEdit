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

package com.sk89q.worldedit.session.preview;

import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Captures block changes into an {@link EditPreview} instead of writing them to the world.
 * Reads see the previewed blocks, so several previewed edits can build on each other.
 *
 * <p>Only blocks are previewed: entity creation and biome changes are dropped while previewing.</p>
 */
public class PreviewCaptureExtent extends AbstractDelegateExtent {

    private final EditPreview preview;

    public PreviewCaptureExtent(Extent extent, EditPreview preview) {
        super(extent);
        this.preview = checkNotNull(preview);
    }

    @Override
    public BlockState getBlock(BlockVector3 position) {
        BaseBlock previewed = preview.get(position);
        return previewed != null ? previewed.toImmutableState() : super.getBlock(position);
    }

    @Override
    public BaseBlock getFullBlock(BlockVector3 position) {
        BaseBlock previewed = preview.get(position);
        return previewed != null ? previewed : super.getFullBlock(position);
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) {
        preview.put(location, block.toBaseBlock());
        return true;
    }

    @Override
    public @Nullable Entity createEntity(Location location, BaseEntity entity) {
        return null;
    }

    @Override
    public boolean setBiome(BlockVector3 position, BiomeType biome) {
        return false;
    }
}

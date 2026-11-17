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

import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.collection.BlockMap;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Block changes that a player has made in preview mode. They are not in the world;
 * the player sees them as client-side "ghost" blocks until they apply or cancel the preview.
 */
public final class EditPreview {

    private final World world;
    private final BlockMap<BaseBlock> pending = BlockMap.createForBaseBlock();
    private final Set<BlockVector3> notYetShown = new LinkedHashSet<>();
    private final Set<BlockVector3> shown = Collections.newSetFromMap(BlockMap.create());

    public EditPreview(World world) {
        this.world = checkNotNull(world);
    }

    public World getWorld() {
        return world;
    }

    /**
     * Get the previewed block at a position.
     *
     * @param position the position
     * @return the block, or null if the preview doesn't change this position
     */
    public @Nullable BaseBlock get(BlockVector3 position) {
        return pending.get(position);
    }

    /**
     * Record a previewed block change.
     *
     * @param position the position
     * @param block the new block
     */
    public void put(BlockVector3 position, BaseBlock block) {
        pending.put(position, block);
        notYetShown.add(position);
    }

    /**
     * Get the number of previewed block changes.
     *
     * @return the number of changes
     */
    public int size() {
        return pending.size();
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /**
     * Get all previewed changes.
     *
     * @return an unmodifiable view of the changes
     */
    public Map<BlockVector3, BaseBlock> getChanges() {
        return Collections.unmodifiableMap(pending);
    }

    /**
     * Send ghost blocks for changes the player hasn't been shown yet.
     *
     * @param player the player
     * @param maxShown the maximum number of ghost blocks to have on screen
     * @return the number of changes that could not be shown because of the limit
     */
    public int show(Player player, int maxShown) {
        int skipped = 0;
        for (BlockVector3 position : notYetShown) {
            BaseBlock block = pending.get(position);
            if (block == null) {
                continue;
            }
            if (!shown.contains(position) && shown.size() >= maxShown) {
                skipped++;
                continue;
            }
            player.sendFakeBlock(position, block);
            shown.add(position);
        }
        notYetShown.clear();
        return skipped;
    }

    /**
     * Re-send every ghost block, e.g. after the client reloaded the chunks.
     *
     * @param player the player
     * @param maxShown the maximum number of ghost blocks to have on screen
     * @return the number of changes that could not be shown because of the limit
     */
    public int showAll(Player player, int maxShown) {
        notYetShown.addAll(pending.keySet());
        shown.clear();
        return show(player, maxShown);
    }

    /**
     * Remove the ghost blocks, showing the player the real world again.
     *
     * @param player the player
     */
    public void hide(Player player) {
        for (BlockVector3 position : shown) {
            player.sendFakeBlock(position, null);
        }
        shown.clear();
        notYetShown.addAll(pending.keySet());
    }

    /**
     * Forget all previewed changes. Does not touch what the player sees; call {@link #hide(Player)} first.
     */
    public void clear() {
        pending.clear();
        notYetShown.clear();
        shown.clear();
    }
}

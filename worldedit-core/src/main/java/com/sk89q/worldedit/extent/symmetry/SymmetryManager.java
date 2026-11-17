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

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

/**
 * Keeps per-player symmetry settings and installs a {@link SymmetryExtent}
 * into every {@link EditSession} created for a player with symmetry on.
 *
 * <p>The extent is installed at {@link EditSession.Stage#BEFORE_HISTORY},
 * above the change-set extent, so mirrored writes are recorded in history.
 * Undo/redo write below that stage and are therefore never mirrored.</p>
 */
public final class SymmetryManager {

    private static final SymmetryManager INSTANCE = new SymmetryManager();

    private final Map<UUID, SymmetrySettings> settings = new ConcurrentHashMap<>();
    private final AtomicBoolean registered = new AtomicBoolean();

    private SymmetryManager() {
    }

    public static SymmetryManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register the edit session listener with the given event bus, once.
     *
     * @param eventBus the event bus
     */
    public void registerOnce(EventBus eventBus) {
        if (registered.compareAndSet(false, true)) {
            eventBus.register(this);
        }
    }

    @Nullable
    public SymmetrySettings getSettings(UUID player) {
        return settings.get(player);
    }

    /**
     * Set or clear a player's symmetry settings.
     *
     * @param player the player's UUID
     * @param newSettings the settings, or null to turn symmetry off
     */
    public void setSettings(UUID player, @Nullable SymmetrySettings newSettings) {
        if (newSettings == null || (!newSettings.mirrorX() && !newSettings.mirrorZ())) {
            settings.remove(player);
        } else {
            settings.put(player, newSettings);
        }
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_HISTORY || settings.isEmpty()) {
            return;
        }
        Actor actor = event.getActor();
        World world = event.getWorld();
        if (actor == null || !actor.isPlayer() || world == null) {
            return;
        }
        SymmetrySettings playerSettings = settings.get(actor.getUniqueId());
        if (playerSettings == null || !playerSettings.worldName().equals(world.getName())) {
            return;
        }
        event.setExtent(new SymmetryExtent(event.getExtent(), playerSettings));
    }
}

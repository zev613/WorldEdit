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

package com.sk89q.worldedit.command;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.preview.EditPreview;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;

import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Commands for previewing edits as client-side ghost blocks before applying them.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class PreviewCommands {

    private final WorldEdit worldEdit;

    public PreviewCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "/preview",
        desc = "Preview edits as ghost blocks only you can see, then apply or cancel them",
        descFooter = "Actions: on, off, apply, cancel, show (re-send ghost blocks), status. "
            + "With no action, toggles preview mode. Only blocks are previewed: biome and entity changes "
            + "made while previewing are skipped, and entity removal commands still act immediately."
    )
    @CommandPermissions("worldedit.preview")
    public void preview(Player player, LocalSession session,
                        @Arg(desc = "on, off, apply, cancel, show or status", def = "toggle")
                            String action) throws WorldEditException {
        switch (action.toLowerCase(Locale.ROOT)) {
            case "toggle" -> setMode(player, session, !session.isPreviewMode());
            case "on", "enable" -> setMode(player, session, true);
            case "off", "disable" -> setMode(player, session, false);
            case "apply", "confirm" -> apply(player, session);
            case "cancel", "discard", "clear" -> {
                discard(player, session);
                player.printInfo(TextComponent.of("Preview discarded."));
            }
            case "show" -> {
                EditPreview preview = session.getPreview();
                if (preview == null || preview.isEmpty()) {
                    player.printError(TextComponent.of("There is nothing to preview."));
                    return;
                }
                int skipped = preview.showAll(player, worldEdit.getConfiguration().maxPreviewBlocks);
                player.printInfo(TextComponent.of(String.format("Showing %,d previewed changes.", preview.size() - skipped)));
            }
            case "status" -> status(player, session);
            default -> player.printError(TextComponent.of("Unknown action '" + action
                + "'. Use on, off, apply, cancel, show or status."));
        }
    }

    private void setMode(Player player, LocalSession session, boolean on) {
        session.setPreviewMode(on);
        if (on) {
            player.printInfo(TextComponent.of("Preview mode on: your edits are shown as ghost blocks only you can see. "
                + "Use //preview apply to build them, //preview cancel to discard them."));
        } else {
            boolean hadPending = session.getPreview() != null && !session.getPreview().isEmpty();
            discard(player, session);
            player.printInfo(TextComponent.of("Preview mode off: edits apply to the world again."
                + (hadPending ? " Unapplied previewed changes were discarded." : "")));
        }
    }

    private void status(Player player, LocalSession session) {
        EditPreview preview = session.getPreview();
        int pending = preview == null ? 0 : preview.size();
        player.printInfo(TextComponent.of(String.format("Preview mode is %s; %,d previewed changes waiting.",
            session.isPreviewMode() ? "on" : "off", pending)));
    }

    private static void discard(Player player, LocalSession session) {
        EditPreview preview = session.getPreview();
        if (preview != null) {
            preview.hide(player);
            preview.clear();
            session.clearPreview();
        }
    }

    private void apply(Player player, LocalSession session) throws WorldEditException {
        EditPreview preview = session.getPreview();
        if (preview == null || preview.isEmpty()) {
            player.printError(TextComponent.of("There are no previewed changes to apply."));
            return;
        }
        if (!preview.getWorld().equals(player.getWorld())) {
            player.printError(TextComponent.of("The preview is for another world. Go back to that world to apply it, "
                + "or use //preview cancel."));
            return;
        }

        // Build a real (non-preview) edit session for the changes, so they go into history as one edit
        boolean previewMode = session.isPreviewMode();
        session.setPreviewMode(false);
        EditSession editSession;
        try {
            editSession = session.createEditSession(player);
        } finally {
            session.setPreviewMode(previewMode);
        }
        int count = preview.size();
        try {
            for (Map.Entry<BlockVector3, BaseBlock> change : preview.getChanges().entrySet()) {
                editSession.setBlock(change.getKey(), change.getValue());
            }
        } finally {
            session.remember(editSession);
            editSession.closeDeferrable();
            worldEdit.flushBlockBag(player, editSession);
        }
        // The real blocks replace the ghost blocks as they are placed
        preview.clear();
        session.clearPreview();
        player.printInfo(TextComponent.of(String.format("Applied %,d previewed changes. Use //undo to revert them.", count)));
    }
}

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
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.diff.WorldDiff;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.NamedClipboardStore;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;

/**
 * {@code //diff}: compare the world with a clipboard at its original position.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class DiffCommands {

    /**
     * Maximum number of fake blocks sent by {@code //diff -v}.
     */
    public static final int MAX_VISUALISED = 20_000;

    private static final int TOP_PAIRS = 5;

    /**
     * Fake blocks currently shown per player, so that {@code //diff clear} can reset them.
     */
    private static final Map<UUID, List<BlockVector3>> VISUALISED = new ConcurrentHashMap<>();

    @Command(
        name = "/diff",
        desc = "Compare the world with the clipboard at the position it was copied from",
        descFooter =
            """
            Actions:
              (none) or compare: report unchanged/changed/removed/added blocks.
                -s selects the bounding box of the differences, -v shows them to you with
                fake glass (red = removed, lime = added, yellow = changed).
              clear: remove the -v overlay and show the real blocks again (same as -x).
              restore: put the clipboard's blocks back where they differ (undoable),
                optionally only where the -m mask matches. Needs worldedit.diff.restore.
            -k <name> uses a named clipboard instead of the active one.
            """
    )
    @CommandPermissions("worldedit.diff")
    @Logging(REGION)
    public void diff(Actor actor, LocalSession session, EditSession editSession, World world,
                     @Arg(desc = "Action: compare, clear or restore", def = "compare")
                         String action,
                     @ArgFlag(name = 'k', desc = "Compare against this named clipboard", def = "")
                         String clipName,
                     @Switch(name = 's', desc = "Select the bounding box of the differences")
                         boolean select,
                     @Switch(name = 'v', desc = "Visualise the differences with fake blocks (only you see them)")
                         boolean visualise,
                     @Switch(name = 'x', desc = "Clear a previous -v visualisation")
                         boolean clear,
                     @ArgFlag(name = 'm', desc = "restore: only restore positions matching this mask")
                         Mask mask) throws WorldEditException {
        String act = action.toLowerCase(Locale.ROOT);
        if (clear || act.equals("clear")) {
            clearVisualisation(actor, true);
            return;
        }
        switch (act) {
            case "compare", "show" -> compare(actor, session, world, clipName, select, visualise);
            case "restore" -> {
                actor.checkPermission("worldedit.diff.restore");
                restore(actor, session, editSession, clipName, mask);
            }
            default -> actor.printError(TextComponent.of(
                "Unknown action '" + action + "'. Use //diff [compare|clear|restore]."));
        }
    }

    private static ClipboardHolder getHolder(Actor actor, LocalSession session, String clipName)
            throws EmptyClipboardException {
        if (clipName != null && !clipName.isEmpty()) {
            return NamedClipboardStore.getDefault().getNamedClipboard(session, actor, clipName);
        }
        return session.getClipboard();
    }

    private static boolean checkUntransformed(Actor actor, ClipboardHolder holder) {
        if (!holder.getTransform().isIdentity()) {
            actor.printError(TextComponent.of("The clipboard is rotated or flipped. //diff compares the clipboard "
                + "where it was copied from and cannot use a transform; copy it again (or load a fresh copy) first."));
            return false;
        }
        return true;
    }

    private void compare(Actor actor, LocalSession session, World world, String clipName,
                         boolean select, boolean visualise) throws WorldEditException {
        ClipboardHolder holder = getHolder(actor, session, clipName);
        if (!checkUntransformed(actor, holder)) {
            return;
        }
        Clipboard clipboard = holder.getClipboard();
        int record = visualise ? MAX_VISUALISED : 0;
        WorldDiff diff = WorldDiff.compute(clipboard, world, record, null);

        actor.printInfo(TextComponent.of("Compared " + clipboard.getRegion().getVolume() + " blocks at "
            + clipboard.getRegion().getMinimumPoint() + " - " + clipboard.getRegion().getMaximumPoint() + ":"));
        actor.printInfo(TextComponent.of("Unchanged: " + diff.getUnchanged()
            + ", changed: " + diff.getChanged()
            + ", removed (now air): " + diff.getRemoved()
            + ", added (air now filled): " + diff.getAdded()));
        List<WorldDiff.TypePair> pairs = diff.getTopPairs(TOP_PAIRS);
        if (!pairs.isEmpty()) {
            actor.printInfo(TextComponent.of("Top changes (clipboard -> world):"));
            for (WorldDiff.TypePair pair : pairs) {
                String to = pair.from().equals(pair.to()) ? pair.to().id() + " (different state)" : pair.to().id();
                actor.printInfo(TextComponent.of("  " + pair.count() + "x " + pair.from().id() + " -> " + to));
            }
        }

        if (diff.getTotalDifferences() == 0) {
            actor.printInfo(TextComponent.of("No differences."));
            if (visualise) {
                clearVisualisation(actor, false);
            }
            return;
        }

        if (select) {
            BlockVector3 min = diff.getMinimum();
            BlockVector3 max = diff.getMaximum();
            CuboidRegionSelector selector = new CuboidRegionSelector(world, min, max);
            session.setRegionSelector(world, selector);
            selector.learnChanges();
            selector.explainRegionAdjust(actor, session);
            actor.printInfo(TextComponent.of("Selected the differences: " + min + " - " + max));
        }

        if (visualise) {
            if (!(actor instanceof Player player)) {
                actor.printError(TextComponent.of("Only players can visualise differences."));
                return;
            }
            clearVisualisation(actor, false);
            List<BlockVector3> shown = new ArrayList<>(diff.getRecorded().size());
            BlockState removedBlock = glass(BlockTypes.RED_STAINED_GLASS);
            BlockState addedBlock = glass(BlockTypes.LIME_STAINED_GLASS);
            BlockState changedBlock = glass(BlockTypes.YELLOW_STAINED_GLASS);
            for (WorldDiff.Difference difference : diff.getRecorded()) {
                BlockState block = switch (difference.kind()) {
                    case REMOVED -> removedBlock;
                    case ADDED -> addedBlock;
                    default -> changedBlock;
                };
                if (block != null) {
                    player.sendFakeBlock(difference.position(), block);
                    shown.add(difference.position());
                }
            }
            VISUALISED.put(actor.getUniqueId(), shown);
            if (diff.isRecordTruncated()) {
                actor.printInfo(TextComponent.of("Showing the first " + MAX_VISUALISED + " of "
                    + diff.getTotalDifferences() + " differences."));
            }
            actor.print(TextComponent.of("Red = removed, lime = added, yellow = changed. Use //diff clear to hide.",
                TextColor.GRAY));
        }
    }

    private static BlockState glass(BlockType type) {
        return type == null ? null : type.getDefaultState();
    }

    private static void clearVisualisation(Actor actor, boolean report) {
        List<BlockVector3> shown = VISUALISED.remove(actor.getUniqueId());
        if (shown == null || shown.isEmpty()) {
            if (report) {
                actor.printInfo(TextComponent.of("No //diff visualisation to clear."));
            }
            return;
        }
        if (actor instanceof Player player) {
            for (BlockVector3 pos : shown) {
                player.sendFakeBlock(pos, null);
            }
        }
        if (report) {
            actor.printInfo(TextComponent.of("Cleared " + shown.size() + " visualised blocks."));
        }
    }

    private void restore(Actor actor, LocalSession session, EditSession editSession, String clipName,
                         Mask mask) throws WorldEditException {
        ClipboardHolder holder = getHolder(actor, session, clipName);
        if (!checkUntransformed(actor, holder)) {
            return;
        }
        Clipboard clipboard = holder.getClipboard();
        List<BlockVector3> positions = new ArrayList<>();
        List<BaseBlock> blocks = new ArrayList<>();
        // Evaluate the mask against the current world before changing anything.
        WorldDiff diff = WorldDiff.compute(clipboard, editSession, 0, (pos, _, expected, _) -> {
            if (mask == null || mask.test(pos)) {
                positions.add(pos);
                blocks.add(expected);
            }
        });
        if (diff.getTotalDifferences() == 0) {
            actor.printInfo(TextComponent.of("No differences; nothing to restore."));
            return;
        }
        int changed = 0;
        for (int i = 0; i < positions.size(); i++) {
            if (editSession.setBlock(positions.get(i), blocks.get(i))) {
                changed++;
            }
        }
        actor.printInfo(TextComponent.of("Restored " + changed + " of " + diff.getTotalDifferences()
            + " differing blocks from the clipboard" + (mask != null ? " (mask applied)" : "") + ". Use //undo to revert."));
    }
}

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

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.NamedClipboardStore;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Implementation of {@code //clipboards} (named clipboards with disk persistence).
 */
final class NamedClipboardsHelper {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    private NamedClipboardsHelper() {
    }

    static void run(Actor actor, LocalSession session, String action, String rawName) throws WorldEditException {
        NamedClipboardStore store = NamedClipboardStore.getDefault();
        UUID owner = actor.getUniqueId();
        String act = action.toLowerCase(Locale.ROOT);
        String name = rawName == null ? "" : rawName.trim();
        switch (act) {
            case "save" -> {
                checkCommandArgument(!name.isEmpty(), "Please specify a clipboard name to save to: //clipboards save <name>");
                String key = requireName(name);
                ClipboardHolder holder = session.getClipboard();
                session.setNamedClipboard(key, holder);
                try {
                    store.save(owner, key, holder);
                    actor.printInfo(TextComponent.of("Current clipboard saved as '" + key + "' (in memory and on disk)."));
                    if (!holder.getTransform().isIdentity()) {
                        actor.printInfo(TextComponent.of("Note: the clipboard's rotation/flip was applied to the saved file."));
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to save named clipboard '" + key + "' for " + actor.getName(), e);
                    actor.printError(TextComponent.of("Clipboard saved as '" + key
                        + "' in memory only; writing it to disk failed: " + e.getMessage()));
                }
            }
            case "load" -> {
                checkCommandArgument(!name.isEmpty(), "Please specify a clipboard name to load: //clipboards load <name>");
                ClipboardHolder holder;
                try {
                    holder = store.getNamedClipboard(session, actor, name);
                } catch (EmptyClipboardException _) {
                    actor.printError(TextComponent.of("No clipboard named '" + name + "' in memory or on disk."));
                    return;
                }
                session.setClipboard(holder);
                actor.printInfo(TextComponent.of("Clipboard '" + name + "' loaded as active clipboard."));
            }
            case "delete", "remove" -> {
                checkCommandArgument(!name.isEmpty(), "Please specify a clipboard name to delete: //clipboards delete <name>");
                boolean inMemory = session.getNamedClipboards().containsKey(name.toLowerCase(Locale.ROOT));
                session.setNamedClipboard(name, null);
                boolean onDisk = false;
                if (NamedClipboardStore.sanitizeName(name) != null) {
                    try {
                        onDisk = store.delete(owner, name);
                    } catch (IOException e) {
                        actor.printError(TextComponent.of("Failed to delete saved clipboard file: " + e.getMessage()));
                        return;
                    }
                }
                if (inMemory || onDisk) {
                    actor.printInfo(TextComponent.of("Clipboard '" + name + "' deleted"
                        + (onDisk ? " (including its saved file)." : " (it was only in memory).")));
                } else {
                    actor.printError(TextComponent.of("No clipboard named '" + name + "' in memory or on disk."));
                }
            }
            case "list" -> {
                List<String> memory = new ArrayList<>(session.getNamedClipboards().keySet());
                memory.sort(null);
                List<String> disk = store.list(owner);
                if (memory.isEmpty() && disk.isEmpty()) {
                    actor.printInfo(TextComponent.of("No named clipboards in this session or on disk."));
                    return;
                }
                actor.printInfo(TextComponent.of("In memory (" + memory.size() + "): "
                    + (memory.isEmpty() ? "-" : String.join(", ", memory))));
                actor.printInfo(TextComponent.of("Saved on disk (" + disk.size() + "): "
                    + (disk.isEmpty() ? "-" : String.join(", ", disk))));
            }
            default -> actor.printError(TextComponent.of(
                "Unknown action '" + action + "'. Use: //clipboards [list|save|load|delete] <name>"));
        }
    }

    private static String requireName(String name) {
        String key = NamedClipboardStore.sanitizeName(name);
        checkCommandArgument(key != null,
            "Invalid clipboard name '" + name + "': use 1-32 letters, digits, '_' or '-'.");
        return key;
    }
}

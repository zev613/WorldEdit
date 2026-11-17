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

package com.sk89q.worldedit.cli.prune;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

/**
 * The {@code prune} sub-command of the WorldEdit CLI.
 *
 * <pre>
 * java -jar worldedit-cli.jar prune &lt;dimension folder&gt; [options]
 * </pre>
 */
public final class PruneCommand {

    public static final String NAME = "prune";
    public static final long DEFAULT_MIN_INHABITED = 1200;

    private PruneCommand() {
    }

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption("t", "min-inhabited", true,
            "Remove chunks whose InhabitedTime (ticks) is below this. Default 1200 (1 minute).");
        options.addOption("c", "center", true, "Centre of the protected area as x,z in blocks. Default 0,0.");
        options.addOption("r", "radius", true, "Protected radius in blocks around the centre. Default 0 (none).");
        options.addOption(null, "apply", false, "Actually modify the region files (default is a dry run).");
        options.addOption(null, "backup", true, "Copy every affected region file to this folder first.");
        options.addOption(null, "no-backup", false, "Confirm that --apply should run without a backup.");
        options.addOption("h", "help", false, "Show this help.");
        return options;
    }

    private static void printHelp(PrintStream out) {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        new HelpFormatter().printHelp(writer, 100, "prune <world or dimension folder> [options]",
            "Remove rarely visited chunks from a world that is NOT currently loaded by a server.", buildOptions(),
            2, 2, "The folder must contain a region/ folder (e.g. world, world/DIM-1).");
        writer.flush();
    }

    /**
     * Run the prune command.
     *
     * @param args arguments after {@code prune}
     * @param out output stream for messages
     * @return the process exit code
     */
    public static int run(String[] args, PrintStream out) {
        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(buildOptions(), args);
        } catch (ParseException e) {
            out.println("Error: " + e.getMessage());
            printHelp(out);
            return 2;
        }
        if (cmd.hasOption("help")) {
            printHelp(out);
            return 0;
        }
        List<String> rest = cmd.getArgList();
        if (rest.size() != 1) {
            out.println("Error: exactly one world/dimension folder must be given.");
            printHelp(out);
            return 2;
        }
        ChunkPruner.Options options;
        try {
            options = parseOptions(cmd, Paths.get(rest.get(0)));
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
            return 2;
        }

        Path parentDir = options.dimensionDir().toAbsolutePath().getParent();
        if (isLocked(options.dimensionDir().resolve("session.lock"))
            || (parentDir != null && isLocked(parentDir.resolve("session.lock")))) {
            if (options.apply()) {
                out.println("Error: the world appears to be loaded by a running server (session.lock is held). "
                    + "Stop the server first.");
                return 3;
            }
            out.println("Warning: the world appears to be loaded by a running server; results may be stale.");
        }

        out.println(String.format(Locale.ROOT,
            "%s chunks in %s with InhabitedTime < %d ticks%s.",
            options.apply() ? "Pruning" : "Dry run: looking for",
            options.dimensionDir(), options.minInhabitedTicks(),
            options.protectRadius() > 0
                ? " outside radius " + options.protectRadius() + " of " + options.centerX() + "," + options.centerZ()
                : ""));
        ChunkPruner.Result result;
        try {
            result = new ChunkPruner(options, out).run();
        } catch (IOException e) {
            out.println("Error: " + e.getMessage());
            return 1;
        }
        out.println(String.format(Locale.ROOT,
            "Scanned %d chunks in %d region files. %s %d chunks (%d region files rewritten, %d deleted). "
                + "%d low-activity chunks protected, %d unreadable chunks kept. %s %s.",
            result.chunksScanned(), result.regionFiles(),
            options.apply() ? "Removed" : "Would remove",
            result.chunksRemoved(), result.regionsModified(), result.regionsDeleted(),
            result.chunksProtected(), result.chunksUnreadable(),
            options.apply() ? "Freed" : "Would free about",
            formatBytes(result.bytesSaved())));
        if (!options.apply() && result.chunksRemoved() > 0) {
            out.println("Nothing was changed. Re-run with --apply and --backup <dir> (or --no-backup) to prune.");
        }
        return 0;
    }

    static ChunkPruner.Options parseOptions(CommandLine cmd, Path folder) {
        Path dimension = folder;
        if (!Files.isDirectory(dimension.resolve("region"))
            && dimension.getFileName() != null && dimension.getFileName().toString().equals("region")
            && dimension.getParent() != null) {
            dimension = dimension.getParent();
        }
        if (!Files.isDirectory(dimension.resolve("region"))) {
            throw new IllegalArgumentException("No region folder found in " + folder);
        }
        long minInhabited = DEFAULT_MIN_INHABITED;
        if (cmd.hasOption("min-inhabited")) {
            minInhabited = parseLong(cmd.getOptionValue("min-inhabited"), "min-inhabited");
            if (minInhabited < 0) {
                throw new IllegalArgumentException("--min-inhabited must not be negative");
            }
        }
        int centerX = 0;
        int centerZ = 0;
        if (cmd.hasOption("center")) {
            String[] parts = cmd.getOptionValue("center").split(",", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("--center must be given as x,z");
            }
            centerX = (int) parseLong(parts[0].trim(), "center x");
            centerZ = (int) parseLong(parts[1].trim(), "center z");
        }
        int radius = 0;
        if (cmd.hasOption("radius")) {
            radius = (int) parseLong(cmd.getOptionValue("radius"), "radius");
            if (radius < 0) {
                throw new IllegalArgumentException("--radius must not be negative");
            }
        }
        boolean apply = cmd.hasOption("apply");
        Path backup = cmd.hasOption("backup") ? Paths.get(cmd.getOptionValue("backup")) : null;
        boolean noBackup = cmd.hasOption("no-backup");
        if (backup != null && noBackup) {
            throw new IllegalArgumentException("--backup and --no-backup cannot be combined");
        }
        if (apply && backup == null && !noBackup) {
            throw new IllegalArgumentException("--apply requires either --backup <dir> or --no-backup");
        }
        if (backup != null) {
            Path absBackup = backup.toAbsolutePath().normalize();
            Path absRegion = dimension.resolve("region").toAbsolutePath().normalize();
            if (absBackup.startsWith(absRegion)) {
                throw new IllegalArgumentException("The backup folder must not be inside the region folder");
            }
        }
        return new ChunkPruner.Options(dimension, minInhabited, centerX, centerZ, radius, apply, backup);
    }

    private static long parseLong(String value, String what) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Invalid number for " + what + ": " + value);
        }
    }

    private static boolean isLocked(Path sessionLock) {
        if (!Files.isRegularFile(sessionLock)) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(sessionLock, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                return true;
            }
            lock.release();
            return false;
        } catch (OverlappingFileLockException _) {
            return true;
        } catch (IOException _) {
            return false;
        }
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024));
    }
}

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

import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinNumberTag;
import org.enginehub.linbus.tree.LinTag;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline pruning of rarely visited chunks from an Anvil world.
 *
 * <p>A chunk is removed if its {@code InhabitedTime} is lower than the
 * threshold and it lies completely outside the protected radius. The same
 * chunks are also removed from the {@code entities/} and {@code poi/} region
 * folders when those exist, so that stale entities or POIs do not reappear in
 * the regenerated chunk.</p>
 */
public final class ChunkPruner {

    /**
     * Options for a prune run.
     *
     * @param dimensionDir the dimension folder that contains {@code region/}
     * @param minInhabitedTicks chunks with an inhabited time strictly below this are removed
     * @param centerX protected area centre X (blocks)
     * @param centerZ protected area centre Z (blocks)
     * @param protectRadius protected radius in blocks, 0 for none
     * @param apply whether to actually modify files
     * @param backupDir where to copy affected files before modifying them, or null for no backup
     */
    public record Options(Path dimensionDir, long minInhabitedTicks, int centerX, int centerZ,
                          int protectRadius, boolean apply, Path backupDir) {
    }

    /**
     * Summary of a prune run.
     *
     * @param regionFiles number of region files scanned
     * @param chunksScanned number of chunks present
     * @param chunksRemoved number of chunks that are (or would be) removed
     * @param chunksProtected number of low-activity chunks kept because of the protected radius
     * @param chunksUnreadable number of chunks that could not be read (always kept)
     * @param regionsModified number of region files that are (or would be) rewritten
     * @param regionsDeleted number of region files that are (or would be) deleted entirely
     * @param bytesSaved bytes freed (estimated for a dry run)
     */
    public record Result(int regionFiles, long chunksScanned, long chunksRemoved, long chunksProtected,
                         long chunksUnreadable, int regionsModified, int regionsDeleted, long bytesSaved) {
    }

    private static final String[] COMPANION_DIRS = { "entities", "poi" };

    private final Options options;
    private final PrintStream log;

    public ChunkPruner(Options options, PrintStream log) {
        this.options = options;
        this.log = log;
    }

    /**
     * Read the inhabited time of a chunk. Supports both the modern layout
     * (fields at the root) and the pre-1.18 layout (fields under {@code Level}).
     *
     * @param chunk the chunk root tag
     * @return the inhabited time in ticks, 0 if missing
     */
    public static long getInhabitedTime(LinCompoundTag chunk) {
        LinTag<?> tag = chunk.value().get("InhabitedTime");
        if (tag == null) {
            LinTag<?> level = chunk.value().get("Level");
            if (level instanceof LinCompoundTag levelCompound) {
                tag = levelCompound.value().get("InhabitedTime");
            }
        }
        if (tag instanceof LinNumberTag<?> number) {
            return number.value().longValue();
        }
        return 0;
    }

    /**
     * Whether a chunk touches the protected circle.
     *
     * @param chunkX chunk X
     * @param chunkZ chunk Z
     * @param centerX centre X in blocks
     * @param centerZ centre Z in blocks
     * @param radius radius in blocks, 0 or less for none
     * @return true if any part of the chunk is within the radius
     */
    public static boolean isProtected(int chunkX, int chunkZ, int centerX, int centerZ, int radius) {
        if (radius <= 0) {
            return false;
        }
        long minX = (long) chunkX << 4;
        long minZ = (long) chunkZ << 4;
        long nearestX = Math.max(minX, Math.min(centerX, minX + 15));
        long nearestZ = Math.max(minZ, Math.min(centerZ, minZ + 15));
        long dx = nearestX - centerX;
        long dz = nearestZ - centerZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /**
     * Run the prune.
     *
     * @return the result
     * @throws IOException on I/O failure
     */
    public Result run() throws IOException {
        Path regionDir = options.dimensionDir().resolve("region");
        if (!Files.isDirectory(regionDir)) {
            throw new IOException("No region folder found at " + regionDir);
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.mca")) {
            for (Path p : stream) {
                if (AnvilRegionFile.parseRegionCoordinates(p.getFileName().toString()) != null) {
                    files.add(p);
                }
            }
        }
        files.sort(null);

        long scanned = 0;
        long removed = 0;
        long protectedCount = 0;
        long unreadable = 0;
        int modified = 0;
        int deleted = 0;
        long saved = 0;

        for (Path file : files) {
            AnvilRegionFile region;
            try {
                region = AnvilRegionFile.read(file);
            } catch (IOException e) {
                log.println("Skipping unreadable region file " + file.getFileName() + ": " + e.getMessage());
                continue;
            }
            if (!region.hasValidHeader()) {
                log.println("Skipping region file with incomplete header: " + file.getFileName());
                continue;
            }
            boolean[] keep = new boolean[AnvilRegionFile.CHUNKS];
            int present = 0;
            int toRemove = 0;
            long removedSectorsBytes = 0;
            for (int i = 0; i < AnvilRegionFile.CHUNKS; i++) {
                if (!region.hasChunk(i)) {
                    continue;
                }
                present++;
                keep[i] = true;
                long inhabited;
                try {
                    inhabited = getInhabitedTime(region.readChunkNbt(i));
                } catch (IOException | RuntimeException e) {
                    unreadable++;
                    log.println("  Keeping unreadable chunk " + region.getChunkX(i) + "," + region.getChunkZ(i)
                        + " in " + file.getFileName() + ": " + e.getMessage());
                    continue;
                }
                if (inhabited >= options.minInhabitedTicks()) {
                    continue;
                }
                if (isProtected(region.getChunkX(i), region.getChunkZ(i),
                    options.centerX(), options.centerZ(), options.protectRadius())) {
                    protectedCount++;
                    continue;
                }
                keep[i] = false;
                toRemove++;
                removedSectorsBytes += (long) region.getSectorCount(i) * AnvilRegionFile.SECTOR_BYTES;
                if (region.isExternal(i) && Files.exists(region.getExternalChunkPath(i))) {
                    removedSectorsBytes += Files.size(region.getExternalChunkPath(i));
                }
            }
            scanned += present;
            if (toRemove == 0) {
                continue;
            }
            removed += toRemove;
            boolean empties = toRemove == present;
            if (empties) {
                deleted++;
            } else {
                modified++;
            }
            if (!options.apply()) {
                saved += empties ? region.getFileSize() : removedSectorsBytes;
                log.println(String.format(Locale.ROOT, "%s: would remove %d of %d chunks%s",
                    file.getFileName(), toRemove, present, empties ? " (file would be deleted)" : ""));
                continue;
            }
            saved += applyToRegion(region, keep, true);
            for (String companion : COMPANION_DIRS) {
                Path companionFile = options.dimensionDir().resolve(companion).resolve(file.getFileName());
                if (Files.isRegularFile(companionFile)) {
                    try {
                        AnvilRegionFile companionRegion = AnvilRegionFile.read(companionFile);
                        if (companionRegion.hasValidHeader()) {
                            saved += applyToRegion(companionRegion, keep, false);
                        }
                    } catch (IOException e) {
                        log.println("  Could not update " + companion + "/" + file.getFileName() + ": " + e.getMessage());
                    }
                }
            }
            log.println(String.format(Locale.ROOT, "%s: removed %d of %d chunks%s",
                file.getFileName(), toRemove, present, empties ? " (file deleted)" : ""));
        }
        return new Result(files.size(), scanned, removed, protectedCount, unreadable, modified, deleted, saved);
    }

    private long applyToRegion(AnvilRegionFile region, boolean[] keep, boolean primary) throws IOException {
        Path file = region.getPath();
        boolean anyRemoved = false;
        List<Path> externals = new ArrayList<>();
        for (int i = 0; i < AnvilRegionFile.CHUNKS; i++) {
            if (!keep[i] && region.hasChunk(i)) {
                anyRemoved = true;
                if (region.isExternal(i)) {
                    externals.add(region.getExternalChunkPath(i));
                }
            }
        }
        if (!anyRemoved) {
            return 0;
        }
        if (options.backupDir() != null) {
            Path relative = options.dimensionDir().relativize(file);
            Path target = options.backupDir().resolve(relative);
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            for (Path external : externals) {
                if (Files.exists(external)) {
                    Path externalTarget = options.backupDir().resolve(options.dimensionDir().relativize(external));
                    Files.copy(external, externalTarget, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        long before = region.getFileSize();
        long after;
        byte[] compacted = region.buildCompacted(keep);
        if (compacted == null) {
            Files.delete(file);
            after = 0;
        } else {
            Path temp = file.resolveSibling(file.getFileName() + ".prune-tmp");
            Files.write(temp, compacted);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException _) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            after = compacted.length;
        }
        long externalBytes = 0;
        for (Path external : externals) {
            if (Files.exists(external)) {
                externalBytes += Files.size(external);
                Files.delete(external);
            }
        }
        if (!primary && compacted == null) {
            log.println("  Deleted empty " + file.getParent().getFileName() + "/" + file.getFileName());
        }
        return before - after + externalBytes;
    }
}

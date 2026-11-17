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

import org.enginehub.linbus.stream.LinBinaryIO;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinRootEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkPrunerTest {

    @TempDir
    Path tempDir;

    private static byte[] chunkPayload(int x, int z, long inhabited, boolean gzip) throws IOException {
        LinCompoundTag tag = LinCompoundTag.builder()
            .putInt("xPos", x)
            .putInt("zPos", z)
            .putLong("InhabitedTime", inhabited)
            .putString("Status", "minecraft:full")
            .build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OutputStream compressed = gzip ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes);
        try (DataOutputStream out = new DataOutputStream(compressed)) {
            LinBinaryIO.write(out, new LinRootEntry("", tag));
        }
        return bytes.toByteArray();
    }

    /**
     * Write a region file containing the given chunks (index to inhabited time).
     */
    private static void writeRegion(Path file, int regionX, int regionZ, Map<Integer, Long> chunks) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(2 * AnvilRegionFile.SECTOR_BYTES);
        int sector = 2;
        for (Map.Entry<Integer, Long> entry : new TreeMap<>(chunks).entrySet()) {
            int index = entry.getKey();
            int cx = regionX * 32 + (index & 31);
            int cz = regionZ * 32 + (index >> 5);
            byte[] payload = chunkPayload(cx, cz, entry.getValue(), index % 2 == 0);
            int length = payload.length + 1;
            int sectors = (length + 4 + AnvilRegionFile.SECTOR_BYTES - 1) / AnvilRegionFile.SECTOR_BYTES;
            ByteBuffer chunk = ByteBuffer.allocate(sectors * AnvilRegionFile.SECTOR_BYTES);
            chunk.putInt(length);
            chunk.put((byte) (index % 2 == 0 ? AnvilRegionFile.COMPRESSION_GZIP : AnvilRegionFile.COMPRESSION_ZLIB));
            chunk.put(payload);
            body.write(chunk.array());
            header.putInt(index * 4, (sector << 8) | sectors);
            header.putInt(AnvilRegionFile.SECTOR_BYTES + index * 4, 1000 + index);
            sector += sectors;
        }
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(header.array());
            out.write(body.toByteArray());
        }
    }

    private Path setUpWorld() throws IOException {
        Path world = tempDir.resolve("world");
        // r.0.0: chunk 0 (0,0) busy, chunk 1 (1,0) idle, chunk 33 (1,1) idle, chunk 40 (8,1) busy
        writeRegion(world.resolve("region/r.0.0.mca"), 0, 0, Map.of(0, 50_000L, 1, 10L, 33, 0L, 40, 1200L));
        // r.-1.0: all idle -> should be deleted entirely
        writeRegion(world.resolve("region/r.-1.0.mca"), -1, 0, Map.of(0, 5L, 32, 100L));
        // entities companion for r.0.0 with the same chunks
        writeRegion(world.resolve("entities/r.0.0.mca"), 0, 0, Map.of(0, 0L, 1, 0L));
        return world;
    }

    private static PrintStream quiet() {
        return new PrintStream(OutputStream.nullOutputStream());
    }

    @Test
    void readsInhabitedTime() throws IOException {
        Path world = setUpWorld();
        AnvilRegionFile region = AnvilRegionFile.read(world.resolve("region/r.0.0.mca"));
        assertTrue(region.hasChunk(0));
        assertFalse(region.hasChunk(2));
        assertEquals(50_000L, ChunkPruner.getInhabitedTime(region.readChunkNbt(0)));
        assertEquals(10L, ChunkPruner.getInhabitedTime(region.readChunkNbt(1)));
        assertEquals(1, region.getChunkX(33));
        assertEquals(1, region.getChunkZ(33));
    }

    @Test
    void legacyLevelLayout() {
        LinCompoundTag legacy = LinCompoundTag.builder()
            .put("Level", LinCompoundTag.builder().putLong("InhabitedTime", 77L).build())
            .build();
        assertEquals(77L, ChunkPruner.getInhabitedTime(legacy));
        assertEquals(0L, ChunkPruner.getInhabitedTime(LinCompoundTag.builder().build()));
    }

    @Test
    void protectedRadius() {
        assertFalse(ChunkPruner.isProtected(0, 0, 0, 0, 0));
        assertTrue(ChunkPruner.isProtected(0, 0, 0, 0, 1));
        // chunk 1 starts at x=16
        assertTrue(ChunkPruner.isProtected(1, 0, 0, 0, 16));
        assertFalse(ChunkPruner.isProtected(1, 0, 0, 0, 15));
        assertTrue(ChunkPruner.isProtected(-1, 0, 0, 0, 1));
        assertFalse(ChunkPruner.isProtected(10, 10, 0, 0, 100));
    }

    @Test
    void dryRunChangesNothing() throws IOException {
        Path world = setUpWorld();
        byte[] before = Files.readAllBytes(world.resolve("region/r.0.0.mca"));
        ChunkPruner.Result result = new ChunkPruner(
            new ChunkPruner.Options(world, 1200, 0, 0, 0, false, null), quiet()).run();
        assertEquals(2, result.regionFiles());
        assertEquals(6, result.chunksScanned());
        assertEquals(4, result.chunksRemoved());
        assertEquals(1, result.regionsModified());
        assertEquals(1, result.regionsDeleted());
        assertTrue(result.bytesSaved() > 0);
        assertTrue(Files.exists(world.resolve("region/r.-1.0.mca")));
        assertEquals(ByteBuffer.wrap(before), ByteBuffer.wrap(Files.readAllBytes(world.resolve("region/r.0.0.mca"))));
    }

    @Test
    void applyWithBackupAndRadius() throws IOException {
        Path world = setUpWorld();
        Path backup = tempDir.resolve("backup");
        long sizeBefore = Files.size(world.resolve("region/r.0.0.mca"));
        // protect chunk (1,0) via a radius of 20 blocks around 0,0; (1,1) is at distance sqrt(16^2+16^2)=22.6
        ChunkPruner.Result result = new ChunkPruner(
            new ChunkPruner.Options(world, 1200, 0, 0, 20, true, backup), quiet()).run();
        assertEquals(3, result.chunksRemoved());
        assertEquals(1, result.chunksProtected());
        assertEquals(1, result.regionsDeleted());

        assertFalse(Files.exists(world.resolve("region/r.-1.0.mca")));
        assertTrue(Files.exists(backup.resolve("region/r.-1.0.mca")));
        assertEquals(sizeBefore, Files.size(backup.resolve("region/r.0.0.mca")));
        // the entities file only holds kept chunks here, so it is untouched and not backed up
        assertFalse(Files.exists(backup.resolve("entities/r.0.0.mca")));

        AnvilRegionFile region = AnvilRegionFile.read(world.resolve("region/r.0.0.mca"));
        assertTrue(region.hasChunk(0));
        assertTrue(region.hasChunk(1));
        assertFalse(region.hasChunk(33));
        assertTrue(region.hasChunk(40));
        // data of kept chunks still readable after compaction
        assertEquals(50_000L, ChunkPruner.getInhabitedTime(region.readChunkNbt(0)));
        assertEquals(10L, ChunkPruner.getInhabitedTime(region.readChunkNbt(1)));
        assertEquals(1200L, ChunkPruner.getInhabitedTime(region.readChunkNbt(40)));
        assertTrue(region.getFileSize() < sizeBefore);

        // nothing else to prune now except the protected chunk
        ChunkPruner.Result second = new ChunkPruner(
            new ChunkPruner.Options(world, 1200, 0, 0, 20, false, null), quiet()).run();
        assertEquals(0, second.chunksRemoved());
    }

    @Test
    void applyAlsoPrunesCompanionFolders() throws IOException {
        Path world = setUpWorld();
        new ChunkPruner(new ChunkPruner.Options(world, 1200, 0, 0, 0, true, null), quiet()).run();
        AnvilRegionFile entities = AnvilRegionFile.read(world.resolve("entities/r.0.0.mca"));
        assertTrue(entities.hasChunk(0));
        assertFalse(entities.hasChunk(1));
    }

    @Test
    void commandRequiresBackupChoice() throws IOException {
        Path world = setUpWorld();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
        int code = PruneCommand.run(new String[] { world.toString(), "--apply" }, out);
        assertNotEquals(0, code);
        assertTrue(Files.exists(world.resolve("region/r.-1.0.mca")));

        code = PruneCommand.run(new String[] { world.toString(), "--min-inhabited", "1200" }, out);
        assertEquals(0, code);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Would remove 4 chunks"));

        code = PruneCommand.run(new String[] { world.toString(), "--apply", "--no-backup", "-r", "0" }, out);
        assertEquals(0, code);
        assertFalse(Files.exists(world.resolve("region/r.-1.0.mca")));
    }

    @Test
    void regionNameParsing() {
        int[] coords = AnvilRegionFile.parseRegionCoordinates("r.-3.12.mca");
        assertEquals(-3, coords[0]);
        assertEquals(12, coords[1]);
        assertEquals(null, AnvilRegionFile.parseRegionCoordinates("r.1.mca"));
        assertEquals(null, AnvilRegionFile.parseRegionCoordinates("c.1.2.mcc"));
    }
}

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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * A minimal, self-contained reader and rewriter for Anvil region files
 * ({@code r.X.Z.mca}).
 *
 * <p>The whole file is read into memory, which is fine for region files
 * (at most a few tens of megabytes). Supported chunk compressions are GZip (1),
 * zlib (2) and uncompressed (3), including chunks stored in external
 * {@code c.X.Z.mcc} files. LZ4 (4) chunks cannot be inspected.</p>
 */
public final class AnvilRegionFile {

    public static final int SECTOR_BYTES = 4096;
    public static final int CHUNKS = 1024;
    public static final int COMPRESSION_GZIP = 1;
    public static final int COMPRESSION_ZLIB = 2;
    public static final int COMPRESSION_NONE = 3;
    public static final int COMPRESSION_LZ4 = 4;
    public static final int EXTERNAL_FLAG = 128;

    private static final Pattern NAME_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");

    private final Path path;
    private final int regionX;
    private final int regionZ;
    private final byte[] data;
    private final int[] locations = new int[CHUNKS];
    private final int[] timestamps = new int[CHUNKS];

    private AnvilRegionFile(Path path, int regionX, int regionZ, byte[] data) {
        this.path = path;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.data = data;
        if (data.length >= 2 * SECTOR_BYTES) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            for (int i = 0; i < CHUNKS; i++) {
                locations[i] = buffer.getInt(i * 4);
                timestamps[i] = buffer.getInt(SECTOR_BYTES + i * 4);
            }
        }
    }

    /**
     * Parse the region coordinates from a file name such as {@code r.-1.2.mca}.
     *
     * @param fileName the file name
     * @return {@code [x, z]}, or {@code null} if the name is not a region file name
     */
    public static int[] parseRegionCoordinates(String fileName) {
        Matcher matcher = NAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new int[] { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) };
        } catch (NumberFormatException _) {
            return null;
        }
    }

    /**
     * Read a region file.
     *
     * @param path the file
     * @return the region file
     * @throws IOException on I/O error or if the file name is not a region file name
     */
    public static AnvilRegionFile read(Path path) throws IOException {
        int[] coords = parseRegionCoordinates(path.getFileName().toString());
        if (coords == null) {
            throw new IOException("Not a region file name: " + path.getFileName());
        }
        return new AnvilRegionFile(path, coords[0], coords[1], Files.readAllBytes(path));
    }

    public Path getPath() {
        return path;
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public long getFileSize() {
        return data.length;
    }

    /**
     * Whether the header is complete. Files shorter than 8 KiB are treated
     * as empty / corrupt.
     *
     * @return true if the file has a full header
     */
    public boolean hasValidHeader() {
        return data.length >= 2 * SECTOR_BYTES;
    }

    public boolean hasChunk(int index) {
        return locations[index] != 0;
    }

    public int getChunkX(int index) {
        return regionX * 32 + (index & 31);
    }

    public int getChunkZ(int index) {
        return regionZ * 32 + (index >> 5);
    }

    public int getSectorCount(int index) {
        return locations[index] & 0xFF;
    }

    private int getSectorOffset(int index) {
        return locations[index] >>> 8;
    }

    private boolean sectorsInBounds(int index) {
        long start = (long) getSectorOffset(index) * SECTOR_BYTES;
        return getSectorOffset(index) >= 2 && start + 5 <= data.length;
    }

    /**
     * Path of the external chunk file used when a chunk is too large to
     * fit in the region file.
     *
     * @param index the chunk index
     * @return the path of the {@code c.X.Z.mcc} file
     */
    public Path getExternalChunkPath(int index) {
        return path.resolveSibling("c." + getChunkX(index) + "." + getChunkZ(index) + ".mcc");
    }

    /**
     * Whether the chunk payload is stored in an external {@code .mcc} file.
     *
     * @param index the chunk index
     * @return true if external
     */
    public boolean isExternal(int index) {
        if (!hasChunk(index) || !sectorsInBounds(index)) {
            return false;
        }
        int start = getSectorOffset(index) * SECTOR_BYTES;
        return (data[start + 4] & EXTERNAL_FLAG) != 0;
    }

    /**
     * Read and decompress the NBT of a chunk.
     *
     * @param index the chunk index (0 to 1023)
     * @return the root compound tag
     * @throws IOException if the chunk cannot be read or uses an unsupported compression
     */
    public LinCompoundTag readChunkNbt(int index) throws IOException {
        if (!hasChunk(index)) {
            throw new IOException("Chunk " + index + " is not present");
        }
        if (!sectorsInBounds(index)) {
            throw new IOException("Chunk " + index + " points outside of the file");
        }
        int start = getSectorOffset(index) * SECTOR_BYTES;
        int length = ByteBuffer.wrap(data).getInt(start);
        int type = data[start + 4] & 0xFF;
        byte[] payload;
        if ((type & EXTERNAL_FLAG) != 0) {
            type &= ~EXTERNAL_FLAG;
            payload = Files.readAllBytes(getExternalChunkPath(index));
        } else {
            if (length < 1 || (long) start + 4 + length > data.length) {
                throw new IOException("Chunk " + index + " has an invalid length of " + length);
            }
            payload = new byte[length - 1];
            System.arraycopy(data, start + 5, payload, 0, length - 1);
        }
        InputStream in = new ByteArrayInputStream(payload);
        in = switch (type) {
            case COMPRESSION_GZIP -> new GZIPInputStream(in);
            case COMPRESSION_ZLIB -> new InflaterInputStream(in);
            case COMPRESSION_NONE -> in;
            default -> throw new IOException("Chunk " + index + " uses unsupported compression type " + type);
        };
        try (DataInputStream dataIn = new DataInputStream(in)) {
            return LinBinaryIO.readUsing(dataIn, LinRootEntry::readFrom).value();
        }
    }

    /**
     * Build the bytes of a compacted copy of this region file that only
     * contains the chunks for which {@code keep[index]} is true. Location
     * and timestamp entries of removed chunks are zeroed and the sectors of
     * the kept chunks are packed together.
     *
     * @param keep which chunks to keep
     * @return the new file contents, or {@code null} if no chunk is kept
     */
    public byte[] buildCompacted(boolean[] keep) {
        int totalSectors = 2;
        boolean any = false;
        for (int i = 0; i < CHUNKS; i++) {
            if (keep[i] && hasChunk(i)) {
                totalSectors += getSectorCount(i);
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        byte[] out = new byte[totalSectors * SECTOR_BYTES];
        ByteBuffer buffer = ByteBuffer.wrap(out);
        int nextSector = 2;
        for (int i = 0; i < CHUNKS; i++) {
            if (!keep[i] || !hasChunk(i)) {
                continue;
            }
            int count = getSectorCount(i);
            int srcStart = getSectorOffset(i) * SECTOR_BYTES;
            int copyLength = Math.max(0, Math.min(count * SECTOR_BYTES, data.length - srcStart));
            System.arraycopy(data, srcStart, out, nextSector * SECTOR_BYTES, copyLength);
            buffer.putInt(i * 4, (nextSector << 8) | count);
            buffer.putInt(SECTOR_BYTES + i * 4, timestamps[i]);
            nextSector += count;
        }
        return out;
    }
}

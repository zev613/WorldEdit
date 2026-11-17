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

package com.sk89q.worldedit.history.persistent;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.history.change.BiomeChange3D;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.DataFixer;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import org.enginehub.linbus.stream.LinBinaryIO;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinListTag;
import org.enginehub.linbus.tree.LinRootEntry;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A serialisable snapshot of the net effect of one edit: for every changed
 * position the block before the edit and the block after it (including block
 * entity NBT), plus 3D biome changes. Entity changes are not captured.
 *
 * @param worldName the name of the world the edit was made in
 * @param created creation time (epoch millis)
 * @param dataVersion the Minecraft data version the blocks were saved with, -1 if unknown
 * @param barrier true if the edit was too large to be saved; it then holds no changes and
 *     marks a point past which the persisted history cannot be undone
 * @param skippedChanges number of changes that were not captured (entities, 2D biomes)
 * @param blocks block changes, in the order the positions were first changed
 * @param biomes biome changes
 */
public record HistorySnapshot(String worldName, long created, int dataVersion, boolean barrier,
                              int skippedChanges, List<BlockChange> blocks, List<BiomeChange3D> biomes) {

    public static final int FORMAT_VERSION = 1;

    /**
     * Capture the net effect of a change set.
     *
     * @param changeSet the change set
     * @param worldName the world name
     * @param dataVersion the data version
     * @param maxChanges if the change set is larger than this, a barrier snapshot is returned
     * @return the snapshot
     */
    public static HistorySnapshot capture(ChangeSet changeSet, String worldName, int dataVersion, int maxChanges) {
        long now = System.currentTimeMillis();
        if (changeSet.size() > maxChanges) {
            return new HistorySnapshot(worldName, now, dataVersion, true, changeSet.size(), List.of(), List.of());
        }
        // Undo applies the backward iterator in order, so the last value seen per position is
        // the state before the edit. Redo applies the forward iterator, last value wins too.
        LinkedHashMap<BlockVector3, BaseBlock> previous = new LinkedHashMap<>();
        Map<BlockVector3, BiomeType> biomePrevious = new LinkedHashMap<>();
        int skipped = 0;
        for (Iterator<Change> it = changeSet.backwardIterator(); it.hasNext(); ) {
            Change change = it.next();
            // remove + put moves the position to the end, so the final order is the reverse of first change
            if (change instanceof BlockChange blockChange) {
                previous.remove(blockChange.position());
                previous.put(blockChange.position(), blockChange.previous());
            } else if (change instanceof BiomeChange3D biomeChange) {
                biomePrevious.remove(biomeChange.position());
                biomePrevious.put(biomeChange.position(), biomeChange.previous());
            } else {
                skipped++;
            }
        }
        Map<BlockVector3, BaseBlock> current = new HashMap<>();
        Map<BlockVector3, BiomeType> biomeCurrent = new LinkedHashMap<>();
        for (Iterator<Change> it = changeSet.forwardIterator(); it.hasNext(); ) {
            Change change = it.next();
            if (change instanceof BlockChange blockChange) {
                current.put(blockChange.position(), blockChange.current());
            } else if (change instanceof BiomeChange3D biomeChange) {
                biomeCurrent.put(biomeChange.position(), biomeChange.current());
            }
        }
        // The backward iterator visits positions in reverse order of first change.
        List<BlockChange> blocks = new ArrayList<>(previous.size());
        for (Map.Entry<BlockVector3, BaseBlock> entry : previous.entrySet()) {
            BaseBlock after = current.getOrDefault(entry.getKey(), entry.getValue());
            blocks.add(new BlockChange(entry.getKey(), entry.getValue(), after));
        }
        Collections.reverse(blocks);
        List<BiomeChange3D> biomes = new ArrayList<>(biomePrevious.size());
        for (Map.Entry<BlockVector3, BiomeType> entry : biomePrevious.entrySet()) {
            BiomeType after = biomeCurrent.getOrDefault(entry.getKey(), entry.getValue());
            biomes.add(new BiomeChange3D(entry.getKey(), entry.getValue(), after));
        }
        return new HistorySnapshot(worldName, now, dataVersion, false, skipped, blocks, biomes);
    }

    /**
     * Add the changes of this snapshot to a change set, so that undoing or
     * redoing that change set has the same effect as the original edit.
     *
     * @param target the change set to fill
     */
    public void addTo(ChangeSet target) {
        boolean recording = target.isRecordingChanges();
        target.setRecordChanges(true);
        try {
            for (BlockChange change : blocks) {
                target.add(change);
            }
            for (BiomeChange3D change : biomes) {
                target.add(change);
            }
        } finally {
            target.setRecordChanges(recording);
        }
    }

    /**
     * Serialise to NBT.
     *
     * @return the tag
     */
    public LinCompoundTag toNbt() {
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        int[] positions = new int[blocks.size() * 3];
        int[] before = new int[blocks.size()];
        int[] after = new int[blocks.size()];
        LinListTag.Builder<LinCompoundTag> blockEntities = LinListTag.builder(LinTagType.compoundTag());
        for (int i = 0; i < blocks.size(); i++) {
            BlockChange change = blocks.get(i);
            positions[i * 3] = change.position().x();
            positions[i * 3 + 1] = change.position().y();
            positions[i * 3 + 2] = change.position().z();
            before[i] = paletteIndex.computeIfAbsent(change.previous().toImmutableState().getAsString(),
                _ -> paletteIndex.size());
            after[i] = paletteIndex.computeIfAbsent(change.current().toImmutableState().getAsString(),
                _ -> paletteIndex.size());
            addNbt(blockEntities, i, false, change.previous());
            addNbt(blockEntities, i, true, change.current());
        }
        Map<String, Integer> biomePaletteIndex = new LinkedHashMap<>();
        int[] biomePositions = new int[biomes.size() * 3];
        int[] biomeBefore = new int[biomes.size()];
        int[] biomeAfter = new int[biomes.size()];
        for (int i = 0; i < biomes.size(); i++) {
            BiomeChange3D change = biomes.get(i);
            biomePositions[i * 3] = change.position().x();
            biomePositions[i * 3 + 1] = change.position().y();
            biomePositions[i * 3 + 2] = change.position().z();
            biomeBefore[i] = biomePaletteIndex.computeIfAbsent(change.previous().id(), _ -> biomePaletteIndex.size());
            biomeAfter[i] = biomePaletteIndex.computeIfAbsent(change.current().id(), _ -> biomePaletteIndex.size());
        }
        return LinCompoundTag.builder()
            .putInt("Version", FORMAT_VERSION)
            .putInt("DataVersion", dataVersion)
            .putString("World", worldName)
            .putLong("Created", created)
            .putByte("Barrier", (byte) (barrier ? 1 : 0))
            .putInt("Skipped", skippedChanges)
            .put("Palette", stringList(paletteIndex))
            .putIntArray("Positions", positions)
            .putIntArray("Previous", before)
            .putIntArray("Current", after)
            .put("BlockEntities", blockEntities.build())
            .put("BiomePalette", stringList(biomePaletteIndex))
            .putIntArray("BiomePositions", biomePositions)
            .putIntArray("BiomePrevious", biomeBefore)
            .putIntArray("BiomeCurrent", biomeAfter)
            .build();
    }

    private static void addNbt(LinListTag.Builder<LinCompoundTag> list, int index, boolean current, BaseBlock block) {
        LinCompoundTag nbt = block.getNbt();
        if (nbt != null) {
            list.add(LinCompoundTag.builder()
                .putInt("Index", index)
                .putByte("Current", (byte) (current ? 1 : 0))
                .put("Nbt", nbt)
                .build());
        }
    }

    private static LinListTag<LinStringTag> stringList(Map<String, Integer> palette) {
        LinListTag.Builder<LinStringTag> builder = LinListTag.builder(LinTagType.stringTag());
        // LinkedHashMap preserves insertion order, which is index order.
        for (String key : palette.keySet()) {
            builder.add(LinStringTag.of(key));
        }
        return builder.build();
    }

    /**
     * Resolves block state strings, biome ids and block entity data when
     * reading a snapshot.
     */
    public interface Resolver {
        /**
         * Parse a block state string.
         *
         * @param state the string, e.g. {@code minecraft:oak_stairs[facing=north]}
         * @param dataVersion the data version it was saved with
         * @return the state, or null if unknown
         */
        @Nullable BlockState parseBlock(String state, int dataVersion);

        /**
         * Look up a biome.
         *
         * @param id the id
         * @param dataVersion the data version it was saved with
         * @return the biome, or null if unknown
         */
        @Nullable BiomeType parseBiome(String id, int dataVersion);

        /**
         * Update block entity data.
         *
         * @param nbt the data
         * @param dataVersion the data version it was saved with
         * @return the updated data
         */
        default LinCompoundTag fixBlockEntity(LinCompoundTag nbt, int dataVersion) {
            return nbt;
        }
    }

    /**
     * A resolver that uses WorldEdit's block parser and the platform's data
     * fixer when the data version differs from the running one.
     *
     * @return the resolver
     */
    public static Resolver platformResolver() {
        ParserContext context = new ParserContext();
        context.setRestricted(false);
        context.setTryLegacy(false);
        context.setPreferringWildcard(false);
        return new Resolver() {
            private @Nullable DataFixer fixer(int dataVersion) {
                try {
                    Platform platform = WorldEdit.getInstance().getPlatformManager()
                        .queryCapability(Capability.WORLD_EDITING);
                    if (dataVersion <= 0 || dataVersion >= platform.getDataVersion()) {
                        return null;
                    }
                    return platform.getDataFixer();
                } catch (RuntimeException _) {
                    return null;
                }
            }

            @Override
            public @Nullable BlockState parseBlock(String state, int dataVersion) {
                DataFixer fixer = fixer(dataVersion);
                String fixed = fixer == null ? state : fixer.fixUp(DataFixer.FixTypes.BLOCK_STATE, state, dataVersion);
                try {
                    return WorldEdit.getInstance().getBlockFactory().parseFromInput(fixed, context).toImmutableState();
                } catch (InputParseException _) {
                    return null;
                }
            }

            @Override
            public @Nullable BiomeType parseBiome(String id, int dataVersion) {
                DataFixer fixer = fixer(dataVersion);
                String fixed = fixer == null ? id : fixer.fixUp(DataFixer.FixTypes.BIOME, id, dataVersion);
                return BiomeType.REGISTRY.get(fixed);
            }

            @Override
            public LinCompoundTag fixBlockEntity(LinCompoundTag nbt, int dataVersion) {
                DataFixer fixer = fixer(dataVersion);
                return fixer == null ? nbt : fixer.fixUp(DataFixer.FixTypes.BLOCK_ENTITY, nbt, dataVersion);
            }
        };
    }

    /**
     * Read a snapshot from NBT.
     *
     * @param tag the tag
     * @param resolver resolves block states and biomes
     * @return the snapshot
     * @throws IOException if the data is invalid or a block cannot be resolved
     */
    public static HistorySnapshot fromNbt(LinCompoundTag tag, Resolver resolver) throws IOException {
        try {
            int version = tag.getTag("Version", LinTagType.intTag()).valueAsInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported history format version " + version);
            }
            int dataVersion = tag.getTag("DataVersion", LinTagType.intTag()).valueAsInt();
            String world = tag.getTag("World", LinTagType.stringTag()).value();
            long created = tag.getTag("Created", LinTagType.longTag()).valueAsLong();
            boolean barrier = tag.getTag("Barrier", LinTagType.byteTag()).valueAsByte() != 0;
            int skipped = tag.getTag("Skipped", LinTagType.intTag()).valueAsInt();

            List<LinStringTag> paletteTags = tag.getListTag("Palette", LinTagType.stringTag()).value();
            BlockState[] palette = new BlockState[paletteTags.size()];
            for (int i = 0; i < palette.length; i++) {
                String state = paletteTags.get(i).value();
                palette[i] = resolver.parseBlock(state, dataVersion);
                if (palette[i] == null) {
                    throw new IOException("Unknown block state in history: " + state);
                }
            }
            int[] positions = tag.getTag("Positions", LinTagType.intArrayTag()).value();
            int[] before = tag.getTag("Previous", LinTagType.intArrayTag()).value();
            int[] after = tag.getTag("Current", LinTagType.intArrayTag()).value();
            if (positions.length != before.length * 3 || before.length != after.length) {
                throw new IOException("Inconsistent block arrays in history file");
            }
            Map<Integer, LinCompoundTag> nbtBefore = new HashMap<>();
            Map<Integer, LinCompoundTag> nbtAfter = new HashMap<>();
            for (LinCompoundTag entry : tag.getListTag("BlockEntities", LinTagType.compoundTag()).value()) {
                int index = entry.getTag("Index", LinTagType.intTag()).valueAsInt();
                boolean current = entry.getTag("Current", LinTagType.byteTag()).valueAsByte() != 0;
                LinCompoundTag nbt = resolver.fixBlockEntity(entry.getTag("Nbt", LinTagType.compoundTag()), dataVersion);
                (current ? nbtAfter : nbtBefore).put(index, nbt);
            }
            List<BlockChange> blocks = new ArrayList<>(before.length);
            for (int i = 0; i < before.length; i++) {
                BlockVector3 pos = BlockVector3.at(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
                blocks.add(new BlockChange(pos,
                    toBaseBlock(palette[before[i]], nbtBefore.get(i)),
                    toBaseBlock(palette[after[i]], nbtAfter.get(i))));
            }

            List<LinStringTag> biomePaletteTags = tag.getListTag("BiomePalette", LinTagType.stringTag()).value();
            BiomeType[] biomePalette = new BiomeType[biomePaletteTags.size()];
            for (int i = 0; i < biomePalette.length; i++) {
                biomePalette[i] = resolver.parseBiome(biomePaletteTags.get(i).value(), dataVersion);
            }
            int[] biomePositions = tag.getTag("BiomePositions", LinTagType.intArrayTag()).value();
            int[] biomeBefore = tag.getTag("BiomePrevious", LinTagType.intArrayTag()).value();
            int[] biomeAfter = tag.getTag("BiomeCurrent", LinTagType.intArrayTag()).value();
            if (biomePositions.length != biomeBefore.length * 3 || biomeBefore.length != biomeAfter.length) {
                throw new IOException("Inconsistent biome arrays in history file");
            }
            List<BiomeChange3D> biomes = new ArrayList<>(biomeBefore.length);
            for (int i = 0; i < biomeBefore.length; i++) {
                BiomeType from = biomePalette[biomeBefore[i]];
                BiomeType to = biomePalette[biomeAfter[i]];
                if (from == null || to == null) {
                    continue;
                }
                BlockVector3 pos = BlockVector3.at(biomePositions[i * 3], biomePositions[i * 3 + 1], biomePositions[i * 3 + 2]);
                biomes.add(new BiomeChange3D(pos, from, to));
            }
            return new HistorySnapshot(world, created, dataVersion, barrier, skipped, blocks, biomes);
        } catch (IllegalStateException | IndexOutOfBoundsException | NullPointerException
                 | NoSuchElementException | ClassCastException e) {
            throw new IOException("Invalid history data: " + e.getMessage(), e);
        }
    }

    private static BaseBlock toBaseBlock(BlockState state, @Nullable LinCompoundTag nbt) {
        return nbt == null ? state.toBaseBlock() : state.toBaseBlock(nbt);
    }

    /**
     * Write to a GZip-compressed NBT file.
     *
     * @param file the file
     * @throws IOException on I/O error
     */
    public void write(Path file) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file));
             DataOutputStream data = new DataOutputStream(new GZIPOutputStream(out))) {
            LinBinaryIO.write(data, new LinRootEntry("", toNbt()));
        }
    }

    /**
     * Read from a GZip-compressed NBT file.
     *
     * @param file the file
     * @param resolver resolves block states and biomes
     * @return the snapshot
     * @throws IOException on I/O error or invalid data
     */
    public static HistorySnapshot read(Path file, Resolver resolver) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file));
             DataInputStream data = new DataInputStream(new GZIPInputStream(in))) {
            return fromNbt(LinBinaryIO.readUsing(data, LinRootEntry::readFrom).value(), resolver);
        }
    }
}

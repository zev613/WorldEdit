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

package com.sk89q.worldedit.session.job;

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.reorder.ChunkBatchingExtent;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.RunContext;
import com.sk89q.worldedit.function.operation.SetBlockMap;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.Registry;
import com.sk89q.worldedit.util.collection.BlockMap;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The buffered-apply operations must be resumable, so they can be spread over several ticks.
 */
class ResumableCommitTest extends BaseWorldEditTest {

    @BeforeAll
    static void registerBlocks() {
        BlockType.REGISTRY.register("minecraft:air", new BlockType("minecraft:air"));
        BlockType.REGISTRY.register("minecraft:stone", new BlockType("minecraft:stone"));
    }

    @AfterAll
    static void clearBlocks() throws Exception {
        Field map = Registry.class.getDeclaredField("map");
        map.setAccessible(true);
        ((Map<?, ?>) map.get(BlockType.REGISTRY)).clear();
    }

    /**
     * Allows {@code checks} calls to shouldContinue per resume.
     */
    private static final class LimitedRunContext extends RunContext {
        private final int checks;
        private int used;

        LimitedRunContext(int checks) {
            this.checks = checks;
        }

        @Override
        public boolean shouldContinue() {
            return used++ < checks;
        }
    }

    private static Extent recordingExtent(List<BlockVector3> written) throws WorldEditException {
        Extent extent = mock(Extent.class);
        when(extent.setBlock(any(BlockVector3.class), any())).thenAnswer(inv -> {
            written.add(inv.getArgument(0));
            return true;
        });
        return extent;
    }

    private static int runInSlices(Operation op) throws WorldEditException {
        int resumes = 0;
        while (op != null) {
            op = op.resume(new LimitedRunContext(1));
            resumes++;
            assertTrue(resumes < 10_000, "operation never finished");
        }
        return resumes;
    }

    @Test
    void chunkBatchingCommitIsResumable() throws WorldEditException {
        BaseBlock stone = checkNotNull(BlockType.REGISTRY.get("minecraft:stone")).getDefaultState().toBaseBlock();
        List<BlockVector3> sliced = new ArrayList<>();
        ChunkBatchingExtent batching = new ChunkBatchingExtent(recordingExtent(sliced), true);
        List<BlockVector3> uninterrupted = new ArrayList<>();
        ChunkBatchingExtent reference = new ChunkBatchingExtent(recordingExtent(uninterrupted), true);
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                batching.setBlock(BlockVector3.at(x * 3, 64, z * 3), stone);
                reference.setBlock(BlockVector3.at(x * 3, 64, z * 3), stone);
            }
        }
        Operation op = batching.commit();
        assertNotNull(op);
        int resumes = runInSlices(op);
        assertTrue(resumes > 1, "1600 blocks should take several slices");
        Operation refOp = reference.commit();
        while (refOp != null) {
            refOp = refOp.resume(new RunContext());
        }
        assertEquals(uninterrupted, sliced, "slicing must not change which blocks are set or their order");
        assertEquals(1600, sliced.size());
    }

    @Test
    void setBlockMapIsResumable() throws WorldEditException {
        BaseBlock stone = checkNotNull(BlockType.REGISTRY.get("minecraft:stone")).getDefaultState().toBaseBlock();
        BlockMap<BaseBlock> blocks = BlockMap.createForBaseBlock();
        for (int i = 0; i < 1000; i++) {
            blocks.put(BlockVector3.at(i, 0, 0), stone);
        }
        List<BlockVector3> written = new ArrayList<>();
        int resumes = runInSlices(new SetBlockMap(recordingExtent(written), blocks));
        assertTrue(resumes > 1);
        assertEquals(1000, written.size());
        assertEquals(1000, written.stream().distinct().count());
    }
}

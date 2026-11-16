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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.InputExtent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.GroundFunction;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.function.RegionMaskingFilter;
import com.sk89q.worldedit.function.block.ApplySideEffect;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.generator.FloraGenerator;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.NoiseFilter2D;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.util.ChangeLimitingFilter;
import com.sk89q.worldedit.function.visitor.LayerVisitor;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.internal.annotation.Offset;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.internal.util.TransformUtil;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.convolution.GaussianKernel;
import com.sk89q.worldedit.math.convolution.HeightMap;
import com.sk89q.worldedit.math.convolution.HeightMapFilter;
import com.sk89q.worldedit.math.convolution.SnowHeightMap;
import com.sk89q.worldedit.math.noise.RandomNoise;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.formatting.component.TextUtils;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.generation.TreeType;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.List;

import static com.sk89q.worldedit.command.util.Logging.LogMode.ALL;
import static com.sk89q.worldedit.command.util.Logging.LogMode.ORIENTATION_REGION;
import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;
import static com.sk89q.worldedit.regions.Regions.asFlatRegion;
import static com.sk89q.worldedit.regions.Regions.maximumBlockY;
import static com.sk89q.worldedit.regions.Regions.minimumBlockY;

/**
 * Commands that operate on regions.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class RegionCommands {

    /**
     * Create a new instance.
     */
    public RegionCommands() {
    }

    @Command(
        name = "/set",
        desc = "Sets all the blocks in the region"
    )
    @CommandPermissions("worldedit.region.set")
    @Logging(REGION)
    public int set(Actor actor, EditSession editSession,
                   @Selection Region region,
                   @Arg(desc = "The pattern of blocks to set")
                       Pattern pattern) {
        RegionFunction set = new BlockReplace(editSession, pattern);
        RegionVisitor visitor = new RegionVisitor(region, set);

        Operations.completeBlindly(visitor);
        List<Component> messages = Lists.newArrayList(visitor.getStatusMessages());
        if (messages.isEmpty()) {
            actor.printInfo(TranslatableComponent.of("worldedit.set.done"));
        } else {
            actor.printInfo(TranslatableComponent.of("worldedit.set.done.verbose", TextUtils.join(messages, TextComponent.of(", "))));
        }

        return visitor.getAffected();
    }

    @Command(
        name = "/line",
        desc = "Draws line segments between cuboid selection corners or convex polyhedral selection vertices",
        descFooter = "Can only be used with a cuboid selection or a convex polyhedral selection"
    )
    @CommandPermissions("worldedit.region.line")
    @Logging(REGION)
    public int line(Actor actor, EditSession editSession,
                    @Selection Region region,
                    @Arg(desc = "The pattern of blocks to place")
                        Pattern pattern,
                    @Arg(desc = "The thickness of the line", def = "0")
                        double thickness,
                    @Switch(name = 'h', desc = "Generate only a shell")
                        boolean shell) throws WorldEditException {
        if (!((region instanceof CuboidRegion) || (region instanceof ConvexPolyhedralRegion))) {
            actor.printError(TranslatableComponent.of("worldedit.line.invalid-type"));
            return 0;
        }
        checkCommandArgument(thickness >= 0, "Thickness must be >= 0");
        WorldEdit.getInstance().checkMaxRadius(thickness);

        List<BlockVector3> vectors;

        if (region instanceof CuboidRegion cuboidRegion) {
            vectors = ImmutableList.of(cuboidRegion.getPos1(), cuboidRegion.getPos2());
        } else {
            ConvexPolyhedralRegion convexRegion = (ConvexPolyhedralRegion) region;
            vectors = ImmutableList.copyOf(convexRegion.getVertices());
        }

        int blocksChanged = editSession.drawLine(pattern, vectors, thickness, !shell);

        actor.printInfo(TranslatableComponent.of("worldedit.line.changed", TextComponent.of(blocksChanged)));
        return blocksChanged;
    }

    @Command(
        name = "/catenary",
        aliases = { "/cat" },
        desc = "Draws a hanging catenary curve (rope/chain) between selection points",
        descFooter = "Can only be used with a cuboid selection or a convex polyhedral selection"
    )
    @CommandPermissions("worldedit.region.catenary")
    @Logging(REGION)
    public int catenary(Actor actor, EditSession editSession,
                        @Selection Region region,
                        @Arg(desc = "The pattern of blocks to place")
                            Pattern pattern,
                        @Arg(desc = "The sag (droop depth) in blocks (0 for auto)", def = "0")
                            double sag,
                        @Arg(desc = "The thickness of the curve", def = "0")
                            double thickness,
                        @Switch(name = 'h', desc = "Generate only a shell")
                            boolean shell) throws WorldEditException {
        if (!((region instanceof CuboidRegion) || (region instanceof ConvexPolyhedralRegion))) {
            actor.printError(TranslatableComponent.of("worldedit.line.invalid-type"));
            return 0;
        }
        checkCommandArgument(thickness >= 0, "Thickness must be >= 0");
        checkCommandArgument(sag >= 0, "Sag must be >= 0");
        WorldEdit.getInstance().checkMaxRadius(thickness);

        List<BlockVector3> vectors;

        if (region instanceof CuboidRegion cuboidRegion) {
            vectors = ImmutableList.of(cuboidRegion.getPos1(), cuboidRegion.getPos2());
        } else {
            ConvexPolyhedralRegion convexRegion = (ConvexPolyhedralRegion) region;
            vectors = ImmutableList.copyOf(convexRegion.getVertices());
        }

        int blocksChanged = editSession.drawCatenary(pattern, vectors, sag, thickness, !shell);

        actor.printInfo(TranslatableComponent.of("worldedit.line.changed", TextComponent.of(blocksChanged)));
        return blocksChanged;
    }

    @Command(
        name = "/curve",
        desc = "Draws a spline through selected points",
        descFooter = "Can only be used with a convex polyhedral selection"
    )
    @CommandPermissions("worldedit.region.curve")
    @Logging(REGION)
    public int curve(Actor actor, EditSession editSession,
                     @Selection Region region,
                     @Arg(desc = "The pattern of blocks to place")
                         Pattern pattern,
                     @Arg(desc = "The thickness of the curve", def = "0")
                         double thickness,
                     @Switch(name = 'h', desc = "Generate only a shell")
                         boolean shell) throws WorldEditException {
        if (!(region instanceof ConvexPolyhedralRegion cpregion)) {
            actor.printError(TranslatableComponent.of("worldedit.curve.invalid-type"));
            return 0;
        }
        checkCommandArgument(thickness >= 0, "Thickness must be >= 0");
        WorldEdit.getInstance().checkMaxRadius(thickness);

        List<BlockVector3> vectors = new ArrayList<>(cpregion.getVertices());

        int blocksChanged = editSession.drawSpline(pattern, vectors, 0, 0, 0, 10, thickness, !shell);

        actor.printInfo(TranslatableComponent.of("worldedit.curve.changed", TextComponent.of(blocksChanged)));
        return blocksChanged;
    }

    @Command(
        name = "/replace",
        aliases = { "/re", "/rep" },
        desc = "Replace all blocks in the selection with another"
    )
    @CommandPermissions("worldedit.region.replace")
    @Logging(REGION)
    public int replace(Actor actor, EditSession editSession, @Selection Region region,
                       @Arg(desc = "The mask representing blocks to replace", def = "")
                           Mask from,
                       @Arg(desc = "The pattern of blocks to replace with")
                           Pattern to) throws WorldEditException {
        if (from == null) {
            from = new ExistingBlockMask(editSession);
        }
        int affected = editSession.replaceBlocks(region, from, to);
        actor.printInfo(TranslatableComponent.of("worldedit.replace.replaced", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/overlay",
        desc = "Set a block on top of blocks in the region"
    )
    @CommandPermissions("worldedit.region.overlay")
    @Logging(REGION)
    public int overlay(Actor actor, EditSession editSession, @Selection Region region,
                       @Arg(desc = "The pattern of blocks to overlay")
                           Pattern pattern) throws WorldEditException {
        int affected = editSession.overlayCuboidBlocks(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.overlay.overlaid", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/center",
        aliases = { "/middle" },
        desc = "Set the center block(s)"
    )
    @Logging(REGION)
    @CommandPermissions("worldedit.region.center")
    public int center(Actor actor, EditSession editSession, @Selection Region region,
                      @Arg(desc = "The pattern of blocks to set")
                          Pattern pattern) throws WorldEditException {
        int affected = editSession.center(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.center.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/naturalize",
        desc = "3 layers of dirt on top then rock below"
    )
    @CommandPermissions("worldedit.region.naturalize")
    @Logging(REGION)
    public int naturalize(Actor actor, EditSession editSession, @Selection Region region) throws WorldEditException {
        int affected = editSession.naturalizeCuboidBlocks(region);
        actor.printInfo(TranslatableComponent.of("worldedit.naturalize.naturalized", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/walls",
        desc = "Build the four sides of the selection"
    )
    @CommandPermissions("worldedit.region.walls")
    @Logging(REGION)
    public int walls(Actor actor, EditSession editSession, @Selection Region region,
                     @Arg(desc = "The pattern of blocks to set")
                         Pattern pattern) throws WorldEditException {
        int affected = editSession.makeWalls(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.walls.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/faces",
        aliases = { "/outline" },
        desc = "Build the walls, ceiling, and floor of a selection"
    )
    @CommandPermissions("worldedit.region.faces")
    @Logging(REGION)
    public int faces(Actor actor, EditSession editSession, @Selection Region region,
                     @Arg(desc = "The pattern of blocks to set")
                         Pattern pattern) throws WorldEditException {
        int affected = editSession.makeFaces(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.faces.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/smooth",
        desc = "Smooth the elevation in the selection",
        descFooter = "Example: '//smooth 1 grass_block,dirt,stone' would only smooth natural surface terrain."
    )
    @CommandPermissions("worldedit.region.smooth")
    @Logging(REGION)
    public int smooth(Actor actor, EditSession editSession, @Selection Region region,
                      @Arg(desc = "# of iterations to perform", def = "1")
                          int iterations,
                      @Arg(desc = "The mask of blocks to use as the height map", def = "")
                          Mask mask) throws WorldEditException {
        HeightMap heightMap = new HeightMap(editSession, region, mask);
        HeightMapFilter filter = new HeightMapFilter(new GaussianKernel(5, 1.0));
        int affected = heightMap.applyFilter(filter, iterations);
        actor.printInfo(TranslatableComponent.of("worldedit.smooth.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/snowsmooth",
        desc = "Smooth the elevation in the selection with snow layers",
        descFooter = "Example: '//snowsmooth 1 -m snow_block,snow' would only smooth snow terrain."
    )
    @CommandPermissions("worldedit.region.snowsmooth")
    @Logging(REGION)
    public int snowSmooth(Actor actor, EditSession editSession, @Selection Region region,
                          @Arg(desc = "# of iterations to perform", def = "1")
                              int iterations,
                          @ArgFlag(name = 'l', desc = "Set the amount of snow blocks under the snow", def = "1")
                              int snowBlockCount,
                          @ArgFlag(name = 'm', desc = "The mask of blocks to use as the height map")
                              Mask mask) throws WorldEditException {
        SnowHeightMap heightMap = new SnowHeightMap(editSession, region, mask);
        HeightMapFilter filter = new HeightMapFilter(new GaussianKernel(5, 1.0));
        float[] changed = heightMap.applyFilter(filter, iterations);
        int affected = heightMap.applyChanges(changed, snowBlockCount);
        actor.printInfo(TranslatableComponent.of("worldedit.snowsmooth.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/strata",
        aliases = { "/layers" },
        desc = "Generates geological sedimentary rock strata through the selection",
        descFooter = "Layers the selection with palettes tilted at a dip angle and azimuth direction"
    )
    @CommandPermissions("worldedit.region.strata")
    @Logging(REGION)
    public int strata(Actor actor, World world, EditSession editSession, LocalSession session,
                      @Selection Region region,
                      @Arg(desc = "Comma-separated list of patterns for each layer")
                          String patternListStr,
                      @Arg(desc = "Thickness of each layer in blocks", def = "4")
                          double layerThickness,
                      @Arg(desc = "Dip angle in degrees (tilt from horizontal, 0-90)", def = "15")
                          double dipDegrees,
                      @Arg(desc = "Azimuth angle in degrees (0 = North, 90 = East)", def = "45")
                          double azimuthDegrees,
                      @Arg(desc = "Waviness factor for layer boundaries", def = "0.0")
                          double noiseFactor) throws WorldEditException {
        checkCommandArgument(layerThickness > 0, "Layer thickness must be > 0");

        Iterable<String> rawPatterns = com.google.common.base.Splitter.on(',').split(patternListStr);
        List<Pattern> patterns = new ArrayList<>();
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(editSession);
        for (String raw : rawPatterns) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(WorldEdit.getInstance().getPatternFactory().parseFromInput(trimmed, context));
            }
        }
        checkCommandArgument(!patterns.isEmpty(), "At least one pattern must be specified");

        double dipRad = Math.toRadians(dipDegrees);
        double azRad = Math.toRadians(azimuthDegrees);
        double ux = Math.sin(dipRad) * Math.sin(azRad);
        double uy = Math.cos(dipRad);
        double uz = Math.sin(dipRad) * Math.cos(azRad);

        int affected = 0;
        for (BlockVector3 pt : region) {
            double d = pt.x() * ux + pt.y() * uy + pt.z() * uz;
            if (noiseFactor != 0.0) {
                d += noiseFactor * 3.0 * Math.sin(pt.x() * 0.15) * Math.cos(pt.z() * 0.15);
            }
            int layerIdx = (int) Math.floor(d / layerThickness);
            int patIdx = Math.floorMod(layerIdx, patterns.size());
            if (editSession.setBlock(pt, patterns.get(patIdx))) {
                affected++;
            }
        }

        actor.printInfo(TextComponent.of(affected + " blocks changed."));
        return affected;
    }

    @Command(
        name = "/erode",
        desc = "Simulates terrain erosion (thermal talus rockfall) over the selection"
    )
    @CommandPermissions("worldedit.region.erode")
    @Logging(REGION)
    public int erode(Actor actor, EditSession editSession,
                     @Selection Region region,
                     @Arg(desc = "Erosion mode: thermal (talus/rockfall)", def = "thermal")
                         String mode,
                     @Arg(desc = "Number of iterations to perform", def = "1")
                         int iterations) throws WorldEditException {
        checkCommandArgument(iterations > 0, "Iterations must be > 0");
        int totalChanges = 0;

        int minX = region.getMinimumPoint().x();
        int maxX = region.getMaximumPoint().x();
        int minY = region.getMinimumPoint().y();
        int maxY = region.getMaximumPoint().y();
        int minZ = region.getMinimumPoint().z();
        int maxZ = region.getMaximumPoint().z();

        for (int iter = 0; iter < Math.min(iterations, 10); iter++) {
            List<BlockVector3> toRemove = new ArrayList<>();
            List<BlockVector3> toAdd = new ArrayList<>();
            List<BlockState> addedStates = new ArrayList<>();

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int topY = Integer.MIN_VALUE;
                    BlockState topState = null;
                    for (int y = maxY; y >= minY; y--) {
                        BlockVector3 pt = BlockVector3.at(x, y, z);
                        if (!region.contains(pt)) continue;
                        BlockState state = editSession.getBlock(pt);
                        if (state.getBlockType().getMaterial().isSolid()) {
                            topY = y;
                            topState = state;
                            break;
                        }
                    }
                    if (topY == Integer.MIN_VALUE) continue;

                    int[][] offsets = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
                    int lowestNeighborY = topY;
                    int lowestNx = x;
                    int lowestNz = z;

                    for (int[] off : offsets) {
                        int nx = x + off[0];
                        int nz = z + off[1];
                        int nTopY = minY - 1;
                        for (int y = maxY; y >= minY; y--) {
                            BlockVector3 npt = BlockVector3.at(nx, y, nz);
                            if (!region.contains(npt)) continue;
                            if (editSession.getBlock(npt).getBlockType().getMaterial().isSolid()) {
                                nTopY = y;
                                break;
                            }
                        }
                        if (nTopY < lowestNeighborY) {
                            lowestNeighborY = nTopY;
                            lowestNx = nx;
                            lowestNz = nz;
                        }
                    }

                    if (topY - lowestNeighborY >= 2 && lowestNeighborY >= minY) {
                        toRemove.add(BlockVector3.at(x, topY, z));
                        toAdd.add(BlockVector3.at(lowestNx, lowestNeighborY + 1, lowestNz));
                        addedStates.add(topState);
                    }
                }
            }

            for (BlockVector3 rem : toRemove) {
                if (editSession.setBlock(rem, BlockTypes.AIR.getDefaultState())) {
                    totalChanges++;
                }
            }
            for (int i = 0; i < toAdd.size(); i++) {
                if (editSession.setBlock(toAdd.get(i), addedStates.get(i))) {
                    totalChanges++;
                }
            }
        }

        actor.printInfo(TextComponent.of(totalChanges + " blocks changed."));
        return totalChanges;
    }

    @Command(
        name = "/weather",
        desc = "Simulates aging, weathering, and patina on blocks in the selection"
    )
    @CommandPermissions("worldedit.region.weather")
    @Logging(REGION)
    public int weather(Actor actor, EditSession editSession,
                       @Selection Region region,
                       @Arg(desc = "Severity of weathering (0.0 to 1.0)", def = "0.5")
                           double severity,
                       @Switch(name = 'm', desc = "Add moss to surfaces/materials")
                           boolean moss,
                       @Switch(name = 'c', desc = "Advance copper oxidation")
                           boolean copper) throws WorldEditException {
        checkCommandArgument(severity >= 0.0 && severity <= 1.0, "Severity must be between 0.0 and 1.0");
        int changed = 0;

        for (BlockVector3 pt : region) {
            BlockState state = editSession.getBlock(pt);
            BlockType type = state.getBlockType();
            String id = type.id();
            String targetId = null;

            double roll = pseudoRandom(pt.x(), pt.y(), pt.z());

            if (id.equals("minecraft:stone_bricks")) {
                if (roll < severity * 0.35) {
                    targetId = "minecraft:cracked_stone_bricks";
                } else if (moss && roll < severity * 0.7) {
                    targetId = "minecraft:mossy_stone_bricks";
                }
            } else if (id.equals("minecraft:deepslate_bricks") && roll < severity * 0.4) {
                targetId = "minecraft:cracked_deepslate_bricks";
            } else if (id.equals("minecraft:deepslate_tiles") && roll < severity * 0.4) {
                targetId = "minecraft:cracked_deepslate_tiles";
            } else if (id.equals("minecraft:polished_blackstone_bricks") && roll < severity * 0.4) {
                targetId = "minecraft:cracked_polished_blackstone_bricks";
            } else if (moss) {
                if (id.equals("minecraft:cobblestone") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone";
                } else if (id.equals("minecraft:cobblestone_stairs") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone_stairs";
                } else if (id.equals("minecraft:cobblestone_slab") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone_slab";
                } else if (id.equals("minecraft:cobblestone_wall") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone_wall";
                } else if (id.equals("minecraft:stone_brick_stairs") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_stone_brick_stairs";
                } else if (id.equals("minecraft:stone_brick_slab") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_stone_brick_slab";
                } else if (id.equals("minecraft:stone_brick_wall") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_stone_brick_wall";
                }
            }

            if (copper || targetId == null) {
                if (id.contains("copper") && !id.contains("waxed")) {
                    String[] stages = { "", "exposed_", "weathered_", "oxidized_" };
                    for (int s = 0; s < stages.length - 1; s++) {
                        String currentPrefix = "minecraft:" + stages[s];
                        String nextPrefix = "minecraft:" + stages[s + 1];
                        if (stages[s].isEmpty()) {
                            if (id.equals("minecraft:copper_block") && roll < severity) {
                                targetId = "minecraft:exposed_copper";
                                break;
                            } else if (id.startsWith("minecraft:cut_copper") && roll < severity) {
                                targetId = id.replace("minecraft:cut_copper", "minecraft:exposed_cut_copper");
                                break;
                            } else if (id.startsWith("minecraft:copper_") && roll < severity) {
                                targetId = id.replace("minecraft:copper_", "minecraft:exposed_copper_");
                                break;
                            }
                        } else if (id.startsWith(currentPrefix) && roll < severity) {
                            targetId = id.replace(currentPrefix, nextPrefix);
                            break;
                        }
                    }
                }
            }

            if (targetId != null) {
                BlockType targetType = BlockTypes.get(targetId);
                if (targetType != null) {
                    BlockState newState = transferBlockProperties(state, targetType);
                    if (editSession.setBlock(pt, newState)) {
                        changed++;
                    }
                }
            }
        }

        actor.printInfo(TextComponent.of(changed + " blocks weathered."));
        return changed;
    }

    private static BlockState transferBlockProperties(BlockState original, BlockType newType) {
        BlockState result = newType.getDefaultState();
        for (com.sk89q.worldedit.registry.state.Property<?> prop : original.getStates().keySet()) {
            com.sk89q.worldedit.registry.state.Property<?> targetProp = newType.getProperty(prop.name());
            if (targetProp != null) {
                result = applyPropertyUnchecked(result, targetProp, original.getState(prop));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <V> BlockState applyPropertyUnchecked(BlockState state, com.sk89q.worldedit.registry.state.Property<V> prop, Object value) {
        try {
            return state.with(prop, (V) value);
        } catch (Exception expected) {
            // Property not compatible with target block state, return original
            return state;
        }
    }

    private static double pseudoRandom(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 326648991;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7fffffff) / (double) 0x7fffffff;
    }

    @Command(
        name = "/move",
        desc = "Move the contents of the selection"
    )
    @CommandPermissions("worldedit.region.move")
    @Logging(ORIENTATION_REGION)
    public int move(Actor actor, World world, EditSession editSession, LocalSession session,
                    @Selection Region region,
                    @Arg(desc = "number of times to apply the offset", def = "1")
                        int multiplier,
                    @Arg(desc = "The offset to move", def = Offset.FORWARD)
                    @Offset
                        BlockVector3 offset,
                    @Arg(desc = "The pattern of blocks to leave", def = "air")
                        Pattern replace,
                    @Switch(name = 's', desc = "Shift the selection to the target location")
                        boolean moveSelection,
                    @Switch(name = 'a', desc = "Ignore air blocks")
                        boolean ignoreAirBlocks,
                    @Switch(name = 'e', desc = "Also copy entities")
                        boolean copyEntities,
                    @Switch(name = 'b', desc = "Also copy biomes")
                        boolean copyBiomes,
                    @ArgFlag(name = 'm', desc = "Set the include mask, non-matching blocks become air")
                        Mask mask) throws WorldEditException {
        checkCommandArgument(multiplier >= 1, "Multiplier must be >= 1");

        Mask combinedMask;
        if (ignoreAirBlocks) {
            if (mask == null) {
                combinedMask = new ExistingBlockMask(editSession);
            } else {
                combinedMask = new MaskIntersection(mask, new ExistingBlockMask(editSession));
            }
        } else {
            combinedMask = mask;
        }

        int affected = editSession.moveRegion(region, offset, multiplier, copyEntities, copyBiomes, combinedMask, replace);

        if (moveSelection) {
            try {
                region.shift(offset.multiply(multiplier));

                session.getRegionSelector(world).learnChanges();
                session.getRegionSelector(world).explainRegionAdjust(actor, session);
            } catch (RegionOperationException e) {
                actor.printError(e.getRichMessage());
            }
        }

        actor.printInfo(TranslatableComponent.of("worldedit.move.moved", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/stack",
        desc = "Repeat the contents of the selection"
    )
    @CommandPermissions("worldedit.region.stack")
    @Logging(ORIENTATION_REGION)
    public int stack(Actor actor, World world, EditSession editSession, LocalSession session,
                     @Selection Region region,
                     @Arg(desc = "# of copies to stack", def = "1")
                         int count,
                     @Arg(desc = "How far to move the contents each stack", def = Offset.FORWARD)
                     @Offset
                         BlockVector3 offset,
                     @Switch(name = 's', desc = "Shift the selection to the last stacked copy")
                         boolean moveSelection,
                     @Switch(name = 'a', desc = "Ignore air blocks")
                         boolean ignoreAirBlocks,
                     @Switch(name = 'e', desc = "Also copy entities")
                         boolean copyEntities,
                     @Switch(name = 'b', desc = "Also copy biomes")
                         boolean copyBiomes,
                     @Switch(name = 'r', desc = "Use block units")
                        boolean blockUnits,
                     @ArgFlag(name = 'm', desc = "Set the include mask, non-matching blocks become air")
                         Mask mask) throws WorldEditException {
        checkCommandArgument(count >= 1, "Count must be >= 1");

        Mask combinedMask;
        if (ignoreAirBlocks) {
            if (mask == null) {
                combinedMask = new ExistingBlockMask(editSession);
            } else {
                combinedMask = new MaskIntersection(mask, new ExistingBlockMask(editSession));
            }
        } else {
            combinedMask = mask;
        }

        int affected;
        if (blockUnits) {
            affected = editSession.stackRegionBlockUnits(region, offset, count, copyEntities, copyBiomes, combinedMask);
        } else {
            affected = editSession.stackCuboidRegion(region, offset, count, copyEntities, copyBiomes, combinedMask);
        }

        if (moveSelection) {
            try {
                final BlockVector3 size = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);

                final BlockVector3 shiftSize = blockUnits ? offset : offset.multiply(size);
                final BlockVector3 shiftVector = shiftSize.multiply(count);
                region.shift(shiftVector);

                session.getRegionSelector(world).learnChanges();
                session.getRegionSelector(world).explainRegionAdjust(actor, session);
            } catch (RegionOperationException e) {
                actor.printError(e.getRichMessage());
            }
        }

        actor.printInfo(TranslatableComponent.of("worldedit.stack.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/regen",
        desc = "Regenerates the contents of the selection"
    )
    @CommandPermissions("worldedit.regen")
    @Logging(REGION)
    void regenerate(Actor actor, World world, LocalSession session, EditSession editSession,
                    @Selection Region region,
                    @Arg(desc = "The seed to regenerate with, otherwise uses world seed", def = "")
                        Long seed,
                    @Switch(name = 'b', desc = "Regenerate biomes as well")
                        boolean regenBiomes,
                    @Switch(name = 'c', desc = "Regenerate to the clipboard")
                        boolean toClipboard) throws WorldEditException {
        Mask mask = session.getMask();
        boolean success;
        try {
            session.setMask(null);
            RegenOptions options = RegenOptions.builder()
                .seed(seed)
                .regenBiomes(regenBiomes)
                .build();
            Extent outputExtent = editSession;
            BlockArrayClipboard clipboard = null;
            if (toClipboard) {
                clipboard = new BlockArrayClipboard(region);
                clipboard.setOrigin(session.getPlacementPosition(actor));
                outputExtent = clipboard;
            }
            success = world.regenerate(region, outputExtent, options);
            if (success && toClipboard) {
                session.setClipboard(new ClipboardHolder(clipboard));
            }
        } finally {
            session.setMask(mask);
        }
        if (success) {
            actor.printInfo(TranslatableComponent.of("worldedit.regen.regenerated"));
        } else {
            actor.printError(TranslatableComponent.of("worldedit.regen.failed"));
        }
    }

    @Command(
        name = "/deform",
        desc = "Deforms a selected region with an expression",
        descFooter =
            """
            The expression is executed for each block and is expected
            to modify the variables x, y and z to point to a new block
            to fetch. For details, see https://ehub.to/we/expr
            """
    )
    @CommandPermissions("worldedit.region.deform")
    @Logging(ALL)
    public int deform(Actor actor, LocalSession session, EditSession editSession,
                      @Selection Region region,
                      @Arg(desc = "The expression to use", variable = true)
                          List<String> expression,
                      @Switch(name = 'r', desc = "Use the game's coordinate origin")
                          boolean useRawCoords,
                      @Switch(name = 'o', desc = "Use the placement's coordinate origin")
                          boolean offsetPlacement,
                      @Switch(name = 'c', desc = "Use the selection's center as origin")
                          boolean offsetCenter,
                      @Switch(name = 'l', desc = "Fetch from the clipboard instead of the world")
                          boolean useClipboard) throws WorldEditException {
        final Transform targetTransform = TransformUtil.createTransformForExpressionCommand(actor, session, region, useRawCoords, offsetPlacement, offsetCenter);

        final InputExtent sourceExtent;
        final Transform sourceTransform;
        if (useClipboard) {
            final Clipboard clipboard = session.getClipboard().getClipboard();
            sourceExtent = clipboard;

            final Vector3 clipboardMin = clipboard.getMinimumPoint().toVector3();
            final Vector3 clipboardMax = clipboard.getMaximumPoint().toVector3();

            sourceTransform = TransformUtil.createTransformForExpressionCommand(useRawCoords, offsetPlacement, offsetCenter, clipboardMin, clipboardMax, clipboardMin);
        } else {
            sourceExtent = editSession.getWorld();
            sourceTransform = targetTransform;
        }

        try {
            final int affected = editSession.deformRegion(region, targetTransform, String.join(" ", expression), session.getTimeout(), sourceExtent, sourceTransform);
            if (actor instanceof Player player) {
                player.findFreePosition();
            }
            actor.printInfo(TranslatableComponent.of("worldedit.deform.deformed", TextComponent.of(affected)));
            return affected;
        } catch (ExpressionException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
    }

    @Command(
        name = "/hollow",
        desc = "Hollows out the object contained in this selection",
        descFooter = "Thickness is measured in manhattan distance."
    )
    @CommandPermissions("worldedit.region.hollow")
    @Logging(REGION)
    public int hollow(Actor actor, EditSession editSession,
                      @Selection Region region,
                      @Arg(desc = "Thickness of the shell to leave", def = "0")
                          int thickness,
                      @Arg(desc = "The pattern of blocks to replace the hollowed area with", def = "air")
                          Pattern pattern) throws WorldEditException {
        checkCommandArgument(thickness >= 0, "Thickness must be >= 0");

        int affected = editSession.hollowOutRegion(region, thickness, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.hollow.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/forest",
        desc = "Make a forest within the region"
    )
    @CommandPermissions("worldedit.region.forest")
    @Logging(REGION)
    public int forest(Actor actor, EditSession editSession, @Selection Region region,
                      @Arg(desc = "The type of tree to place", def = "tree")
                          TreeType type,
                      @Arg(desc = "The density of the forest", def = "5")
                          double density) throws WorldEditException {
        checkCommandArgument(0 <= density && density <= 100, "Density must be in [0, 100]");
        int affected = editSession.makeForest(region, density / 100, type);
        actor.printInfo(TranslatableComponent.of("worldedit.forest.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/flora",
        desc = "Make flora within the region"
    )
    @CommandPermissions("worldedit.region.flora")
    @Logging(REGION)
    public int flora(Actor actor, EditSession editSession, @Selection Region region,
                     @Arg(desc = "The density of the forest", def = "5")
                         double density) throws WorldEditException {
        checkCommandArgument(0 <= density && density <= 100, "Density must be in [0, 100]");
        density = density / 100;
        FloraGenerator generator = new FloraGenerator(editSession);
        GroundFunction ground = new GroundFunction(new ExistingBlockMask(editSession), generator);
        LayerVisitor visitor = new LayerVisitor(asFlatRegion(region), minimumBlockY(region), maximumBlockY(region), ground);
        visitor.setMask(new NoiseFilter2D(new RandomNoise(), density));
        Operations.completeLegacy(visitor);

        int affected = ground.getAffected();
        actor.printInfo(TranslatableComponent.of("worldedit.flora.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/update",
        desc = "Apply side effects to your selection"
    )
    @CommandPermissions("worldedit.update")
    void update(Actor actor, LocalSession session, World injectedWorld,
                @Arg(desc = "The side effects", def = "")
                SideEffectSet sideEffectSet) throws WorldEditException {
        if (sideEffectSet == null) {
            // Use defaults if none supplied.
            sideEffectSet = SideEffectSet.defaults();
        }
        RegionFunction apply = new ApplySideEffect(injectedWorld, sideEffectSet);
        if (session.getMask() != null) {
            apply = new RegionMaskingFilter(session.getMask(), apply);
        }
        if (session.getBlockChangeLimit() >= 0) {
            // Only apply limits if one is set.
            apply = new ChangeLimitingFilter(session.getBlockChangeLimit(), apply);
        }

        RegionVisitor visitor = new RegionVisitor(session.getSelection(injectedWorld), apply);
        Operations.complete(visitor);

        actor.printInfo(TranslatableComponent.of("worldedit.update"));
    }
}

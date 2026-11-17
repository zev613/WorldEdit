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
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.terrain.ColumnNoise;
import com.sk89q.worldedit.util.terrain.SurfaceGrid;
import com.sk89q.worldedit.util.terrain.TextureRules;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;

import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Rule-based surface painting commands.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class TerrainPaintCommands {

    /**
     * Create a new instance.
     */
    public TerrainPaintCommands() {
    }

    @Command(
        name = "/texture",
        aliases = { "/terrainpaint" },
        desc = "Paints the surface of the selection using height, slope and noise rules",
        descFooter =
            """
            Rules are comma-separated, each pattern@condition&condition... or a bare pattern as fallback.
            Conditions: y>N, y<N, slope>D, slope<D (degrees), noise>F, noise<F (0-1); >= and <= also work.
            The first matching rule wins; columns matching no rule are left alone.
            Example: //texture snow_block@y>140,stone@slope>45,coarse_dirt@noise>0.7&slope>25,grass_block\
            """
    )
    @CommandPermissions("worldedit.region.texture")
    @Logging(REGION)
    public int texture(Actor actor, World world, EditSession editSession, LocalSession session,
                       @Selection Region region,
                       @Arg(desc = "Comma-separated texture rules")
                           String rules,
                       @ArgFlag(name = 'd', desc = "Number of blocks from the surface down to replace", def = "1")
                           int depth,
                       @ArgFlag(name = 's', desc = "Noise scale in blocks (larger = smoother)", def = "16")
                           double scale) throws WorldEditException {
        checkCommandArgument(depth >= 1, "Depth must be >= 1");
        checkCommandArgument(scale > 0 && !Double.isInfinite(scale), "Noise scale must be > 0");

        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(editSession);
        context.setSession(session);
        TextureRules<Pattern> textureRules = TextureRules.parse(rules,
            input -> WorldEdit.getInstance().getPatternFactory().parseFromInput(input, context));

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int sizeX = max.x() - min.x() + 1;
        int sizeZ = max.z() - min.z() + 1;

        // Slope heights cover a one-block border so the selection edges get a proper gradient.
        SurfaceGrid slopeHeights = new SurfaceGrid(min.x() - 1, min.z() - 1, sizeX + 2, sizeZ + 2);
        SurfaceGrid surfaces = new SurfaceGrid(min.x(), min.z(), sizeX, sizeZ);
        for (int x = min.x() - 1; x <= max.x() + 1; x++) {
            for (int z = min.z() - 1; z <= max.z() + 1; z++) {
                boolean inside = x >= min.x() && x <= max.x() && z >= min.z() && z <= max.z();
                int top = SurfaceGrid.NO_SURFACE;
                for (int y = max.y(); y >= min.y(); y--) {
                    BlockVector3 pt = BlockVector3.at(x, y, z);
                    if (!isSolid(editSession, pt)) {
                        continue;
                    }
                    if (!inside) {
                        // Border column: topmost solid block in the selection's Y range
                        top = y;
                        break;
                    }
                    if (region.contains(pt)) {
                        surfaces.set(x, z, y);
                        top = y;
                        break;
                    }
                }
                slopeHeights.set(x, z, top);
            }
        }

        ColumnNoise noise = textureRules.uses(TextureRules.Variable.NOISE) ? new ColumnNoise(scale) : null;
        int changed = 0;
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                int surfaceY = surfaces.get(x, z);
                if (surfaceY == SurfaceGrid.NO_SURFACE) {
                    continue;
                }
                double slope = slopeHeights.slopeDegrees(x, z);
                double noiseValue = noise == null ? 0 : noise.at(x, z);
                Pattern pattern = textureRules.select(surfaceY, slope, noiseValue);
                if (pattern == null) {
                    continue;
                }
                for (int y = surfaceY; y > surfaceY - depth && y >= min.y(); y--) {
                    BlockVector3 pt = BlockVector3.at(x, y, z);
                    if (!region.contains(pt) || editSession.getBlock(pt).getBlockType().getMaterial().isAir()) {
                        continue;
                    }
                    if (editSession.setBlock(pt, pattern)) {
                        changed++;
                    }
                }
            }
        }

        actor.printInfo(TextComponent.of(changed + " surface blocks textured."));
        return changed;
    }

    private static boolean isSolid(Extent extent, BlockVector3 pt) {
        BlockState state = extent.getBlock(pt);
        return state.getBlockType().getMaterial().isSolid();
    }
}

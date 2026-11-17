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
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.terrain.HeightmapImages;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;
import org.enginehub.piston.exception.StopExecutionException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Commands for importing and exporting heightmap images. Registered as {@code //heightmap}.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class HeightmapCommands {

    /**
     * The folder (relative to WorldEdit's working directory) that heightmap images live in.
     */
    public static final String HEIGHTMAP_DIR = "heightmaps";

    /**
     * The largest supported image / selection side length in pixels / blocks.
     */
    public static final int MAX_SIDE = 4096;

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public HeightmapCommands(WorldEdit worldEdit) {
        this.worldEdit = worldEdit;
    }

    private File getHeightmapDir() {
        return worldEdit.getWorkingDirectoryPath(HEIGHTMAP_DIR).toFile();
    }

    @Command(
        name = "export",
        desc = "Exports the surface of the selection as a 16-bit grayscale PNG heightmap",
        descFooter =
            """
            One pixel per column (image x = world X, image y = world Z, origin at the minimum corner).
            Pixel value = round((surfaceY - worldMinY) / (worldMaxY - worldMinY) * 65535).
            With -c, writes an 8-bit RGB map coloured by the top block instead.\
            """
    )
    @CommandPermissions("worldedit.heightmap.export")
    public void export(Actor actor, World world,
                       @Selection Region region,
                       @Arg(desc = "File name (saved in the heightmaps folder, .png added if missing)")
                           String filename,
                       @Switch(name = 'c', desc = "Write a colour map of the top blocks instead of heights")
                           boolean colour,
                       @Switch(name = 'f', desc = "Overwrite an existing file")
                           boolean overwrite) throws WorldEditException {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int sizeX = max.x() - min.x() + 1;
        int sizeZ = max.z() - min.z() + 1;
        checkCommandArgument(sizeX <= MAX_SIDE && sizeZ <= MAX_SIDE,
            "Selection is too large to export (max " + MAX_SIDE + "x" + MAX_SIDE + " columns)");

        File dir = getHeightmapDir();
        File file = worldEdit.getSafeSaveFile(actor, dir, filename, "png");
        if (file.exists() && !overwrite) {
            actor.printError(TextComponent.of("That heightmap already exists. Use -f to overwrite it."));
            return;
        }

        int worldMinY = world.getMinY();
        int worldMaxY = world.getMaxY();
        int[][] values = new int[sizeX][sizeZ];
        int columns = 0;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int x = min.x() + dx;
                int z = min.z() + dz;
                if (colour) {
                    values[dx][dz] = topColour(world, region, x, z, min.y(), max.y());
                    columns++;
                    continue;
                }
                int surface = surfaceY(world, region, x, z, min.y(), max.y());
                if (surface != Integer.MIN_VALUE) {
                    values[dx][dz] = HeightmapImages.heightToPixel(surface, worldMinY, worldMaxY);
                    columns++;
                }
            }
        }

        BufferedImage image = colour ? HeightmapImages.createRgb(values) : HeightmapImages.createGray16(values);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new StopExecutionException(TextComponent.of("Could not create the heightmaps folder."));
        }
        try {
            if (!ImageIO.write(image, "png", file)) {
                throw new StopExecutionException(TextComponent.of("No PNG writer is available."));
            }
        } catch (IOException e) {
            throw new StopExecutionException(TextComponent.of("Failed to write heightmap: " + e.getMessage()));
        }

        if (colour) {
            actor.printInfo(TextComponent.of("Exported a " + sizeX + "x" + sizeZ + " colour map to " + file.getName() + "."));
        } else {
            actor.printInfo(TextComponent.of("Exported a " + sizeX + "x" + sizeZ + " heightmap to " + file.getName()
                + " (" + columns + " columns with a surface). Black (0) = Y " + worldMinY
                + ", white (65535) = Y " + worldMaxY + "."));
            actor.printInfo(TextComponent.of("To re-import at the same heights, select from Y " + worldMinY
                + " and use maxHeight " + (worldMaxY - worldMinY) + "."));
        }
    }

    @Command(
        name = "import",
        desc = "Builds terrain in the selection from a grayscale heightmap image",
        descFooter =
            """
            The image is stretched (bilinear) to the selection's X/Z size. A pixel with brightness b (0-1)
            fills its column from the selection's bottom up to bottomY + round(b * maxHeight),
            capped at the selection's top. Above that is cleared to air unless -k is given.\
            """
    )
    @CommandPermissions("worldedit.heightmap.import")
    @Logging(REGION)
    public int importHeightmap(Actor actor, EditSession editSession,
                               @Selection Region region,
                               @Arg(desc = "File name in the heightmaps folder (.png added if missing)")
                                   String filename,
                               @Arg(desc = "The pattern to fill the terrain with")
                                   Pattern pattern,
                               @Arg(desc = "Blocks above the selection bottom for a white pixel "
                                   + "(default: selection height - 1)", def = "")
                                   Integer maxHeight,
                               @ArgFlag(name = 't', desc = "Pattern for the top block of each column")
                                   Pattern topPattern,
                               @Switch(name = 'k', desc = "Keep existing blocks above the imported surface")
                                   boolean keepAbove) throws WorldEditException {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int sizeX = max.x() - min.x() + 1;
        int sizeZ = max.z() - min.z() + 1;
        int height = maxHeight == null ? max.y() - min.y() : maxHeight;
        checkCommandArgument(height >= 0, "maxHeight must be >= 0");

        File file = worldEdit.getSafeOpenFile(actor, getHeightmapDir(), filename, "png");
        if (!file.isFile()) {
            actor.printError(TextComponent.of("Heightmap " + file.getName() + " does not exist."));
            return 0;
        }
        BufferedImage image = readImage(file);

        double[][] values = HeightmapImages.resampleBilinear(HeightmapImages.readNormalized(image), sizeX, sizeZ);
        BlockState air = BlockTypes.AIR.getDefaultState();
        Pattern top = topPattern == null ? pattern : topPattern;
        int changed = 0;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                int surface = Math.min(max.y(), HeightmapImages.normalizedToHeight(values[dx][dz], min.y(), height));
                int topY = keepAbove ? surface : max.y();
                for (int y = min.y(); y <= topY; y++) {
                    BlockVector3 pt = BlockVector3.at(min.x() + dx, y, min.z() + dz);
                    if (!region.contains(pt)) {
                        continue;
                    }
                    boolean set;
                    if (y < surface) {
                        set = editSession.setBlock(pt, pattern);
                    } else if (y == surface) {
                        set = editSession.setBlock(pt, top);
                    } else {
                        set = editSession.setBlock(pt, air);
                    }
                    if (set) {
                        changed++;
                    }
                }
            }
        }

        actor.printInfo(TextComponent.of("Imported " + image.getWidth() + "x" + image.getHeight() + " heightmap onto "
            + sizeX + "x" + sizeZ + " columns (" + changed + " blocks changed)."));
        return changed;
    }

    private static BufferedImage readImage(File file) throws WorldEditException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                throw new StopExecutionException(TextComponent.of("Could not open " + file.getName() + "."));
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new StopExecutionException(TextComponent.of(file.getName() + " is not a supported image."));
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkCommandArgument(width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE,
                    "Heightmap image must be between 1x1 and " + MAX_SIDE + "x" + MAX_SIDE + " pixels");
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new StopExecutionException(TextComponent.of("Failed to read heightmap: " + e.getMessage()));
        }
    }

    /**
     * Find the topmost solid block of a column inside the region.
     */
    private static int surfaceY(World world, Region region, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            BlockVector3 pt = BlockVector3.at(x, y, z);
            if (region.contains(pt) && world.getBlock(pt).getBlockType().getMaterial().isSolid()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Find the map colour of the topmost non-air block of a column inside the region (black if none).
     */
    private static int topColour(World world, Region region, int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            BlockVector3 pt = BlockVector3.at(x, y, z);
            if (!region.contains(pt)) {
                continue;
            }
            BlockState state = world.getBlock(pt);
            if (!state.getBlockType().getMaterial().isAir()) {
                return HeightmapImages.colourOf(state.getBlockType().id());
            }
        }
        return 0;
    }
}

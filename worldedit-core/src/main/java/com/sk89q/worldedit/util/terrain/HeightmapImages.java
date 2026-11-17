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

package com.sk89q.worldedit.util.terrain;

import com.google.common.collect.ImmutableMap;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Map;

/**
 * Pure helpers for converting between heights and heightmap images.
 *
 * <p>Image X corresponds to world X and image Y to world Z, with pixel (0, 0) at the
 * selection's minimum X/Z corner.</p>
 *
 * <p>Export mapping: a 16-bit pixel value {@code v} encodes the surface Y as
 * {@code v = round((y - minY) / (maxY - minY) * 65535)}, where minY/maxY are the world's
 * build limits. Import reads a normalized value {@code n} in [0, 1] and places the surface
 * at {@code baseY + round(n * maxHeight)}.</p>
 */
public final class HeightmapImages {

    /**
     * The largest 16-bit sample value.
     */
    public static final int MAX_16 = 0xFFFF;

    /**
     * The colour used for blocks that have no entry in the colour table.
     */
    public static final int FALLBACK_RGB = 0x808080;

    /**
     * Approximate map colours for common terrain blocks (keyed by block id).
     */
    private static final Map<String, Integer> TERRAIN_COLOURS = ImmutableMap.<String, Integer>builder()
        .put("minecraft:grass_block", 0x7FB238)
        .put("minecraft:dirt", 0x976D4D)
        .put("minecraft:coarse_dirt", 0x976D4D)
        .put("minecraft:rooted_dirt", 0x976D4D)
        .put("minecraft:podzol", 0x815631)
        .put("minecraft:mycelium", 0x7F3FB2)
        .put("minecraft:dirt_path", 0x976D4D)
        .put("minecraft:farmland", 0x976D4D)
        .put("minecraft:mud", 0x575C5C)
        .put("minecraft:sand", 0xF7E9A3)
        .put("minecraft:sandstone", 0xF7E9A3)
        .put("minecraft:red_sand", 0xD87F33)
        .put("minecraft:red_sandstone", 0xD87F33)
        .put("minecraft:gravel", 0x707070)
        .put("minecraft:clay", 0xA4A8B8)
        .put("minecraft:stone", 0x707070)
        .put("minecraft:cobblestone", 0x707070)
        .put("minecraft:andesite", 0x707070)
        .put("minecraft:diorite", 0xFFFCF5)
        .put("minecraft:granite", 0x976D4D)
        .put("minecraft:deepslate", 0x646464)
        .put("minecraft:tuff", 0x393E3F)
        .put("minecraft:calcite", 0xD1B1A1)
        .put("minecraft:terracotta", 0xD87F33)
        .put("minecraft:snow", 0xFFFFFF)
        .put("minecraft:snow_block", 0xFFFFFF)
        .put("minecraft:powder_snow", 0xFFFFFF)
        .put("minecraft:ice", 0xA0A0FF)
        .put("minecraft:packed_ice", 0xA0A0FF)
        .put("minecraft:blue_ice", 0xA0A0FF)
        .put("minecraft:water", 0x4040FF)
        .put("minecraft:lava", 0xFF0000)
        .put("minecraft:netherrack", 0x700200)
        .put("minecraft:end_stone", 0xF7E9A3)
        .put("minecraft:oak_leaves", 0x007C00)
        .put("minecraft:birch_leaves", 0x007C00)
        .put("minecraft:spruce_leaves", 0x007C00)
        .put("minecraft:jungle_leaves", 0x007C00)
        .put("minecraft:acacia_leaves", 0x007C00)
        .put("minecraft:dark_oak_leaves", 0x007C00)
        .put("minecraft:mangrove_leaves", 0x007C00)
        .put("minecraft:moss_block", 0x667F33)
        .build();

    private HeightmapImages() {
    }

    /**
     * Map a world Y to a 16-bit pixel value.
     *
     * @param y the Y coordinate
     * @param minY the Y mapped to 0
     * @param maxY the Y mapped to 65535
     * @return the pixel value, clamped to [0, 65535]
     */
    public static int heightToPixel(int y, int minY, int maxY) {
        if (maxY <= minY) {
            return 0;
        }
        double n = (y - (double) minY) / (maxY - (double) minY);
        return (int) Math.round(clamp01(n) * MAX_16);
    }

    /**
     * Map a 16-bit pixel value back to a world Y (the inverse of {@link #heightToPixel}).
     *
     * @param pixel the pixel value
     * @param minY the Y mapped to 0
     * @param maxY the Y mapped to 65535
     * @return the Y coordinate
     */
    public static int pixelToHeight(int pixel, int minY, int maxY) {
        return normalizedToHeight(pixel / (double) MAX_16, minY, maxY - minY);
    }

    /**
     * Map a normalized value to a Y coordinate.
     *
     * @param value the value in [0, 1] (clamped)
     * @param baseY the Y for value 0
     * @param maxHeight the number of blocks above baseY for value 1
     * @return the Y coordinate
     */
    public static int normalizedToHeight(double value, int baseY, int maxHeight) {
        return baseY + (int) Math.round(clamp01(value) * maxHeight);
    }

    /**
     * Create a 16-bit grayscale image from pixel values.
     *
     * @param pixels values in [0, 65535], indexed {@code [x][z]}
     * @return the image
     */
    public static BufferedImage createGray16(int[][] pixels) {
        int width = pixels.length;
        int height = width == 0 ? 0 : pixels[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster raster = image.getRaster();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                raster.setSample(x, z, 0, pixels[x][z]);
            }
        }
        return image;
    }

    /**
     * Create an 8-bit RGB image from colours.
     *
     * @param rgb colours as 0xRRGGBB, indexed {@code [x][z]}
     * @return the image
     */
    public static BufferedImage createRgb(int[][] rgb) {
        int width = rgb.length;
        int height = width == 0 ? 0 : rgb[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                image.setRGB(x, z, rgb[x][z]);
            }
        }
        return image;
    }

    /**
     * Read an image as normalized values in [0, 1]. Grayscale images (8 or 16 bit) use their
     * sample value directly, anything else is converted to luminance (Rec. 709).
     *
     * @param image the image
     * @return the values indexed {@code [x][y]}
     */
    public static double[][] readNormalized(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] values = new double[width][height];
        ColorModel colorModel = image.getColorModel();
        Raster raster = image.getRaster();
        boolean directGray = !(colorModel instanceof IndexColorModel)
            && colorModel.getColorSpace().getType() == ColorSpace.TYPE_GRAY;
        if (directGray) {
            int bits = raster.getSampleModel().getSampleSize(0);
            double max = (1L << bits) - 1;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    values[x][y] = raster.getSample(x, y, 0) / max;
                }
            }
        } else {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    values[x][y] = luminance(image.getRGB(x, y));
                }
            }
        }
        return values;
    }

    /**
     * Compute the luminance of an RGB colour.
     *
     * @param rgb the colour as 0xRRGGBB (alpha ignored)
     * @return the luminance in [0, 1]
     */
    public static double luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    }

    /**
     * Resample a grid to a new size using bilinear interpolation. Corner samples map
     * onto corner samples, so resampling to the same size is the identity.
     *
     * @param source the source values indexed {@code [x][y]}, at least 1x1
     * @param width the target width
     * @param height the target height
     * @return the resampled values indexed {@code [x][y]}
     */
    public static double[][] resampleBilinear(double[][] source, int width, int height) {
        int srcWidth = source.length;
        int srcHeight = source[0].length;
        double[][] result = new double[width][height];
        for (int x = 0; x < width; x++) {
            double sx = sourceCoordinate(x, width, srcWidth);
            int x0 = (int) Math.floor(sx);
            int x1 = Math.min(x0 + 1, srcWidth - 1);
            double fx = sx - x0;
            for (int y = 0; y < height; y++) {
                double sy = sourceCoordinate(y, height, srcHeight);
                int y0 = (int) Math.floor(sy);
                int y1 = Math.min(y0 + 1, srcHeight - 1);
                double fy = sy - y0;
                double top = source[x0][y0] * (1 - fx) + source[x1][y0] * fx;
                double bottom = source[x0][y1] * (1 - fx) + source[x1][y1] * fx;
                result[x][y] = top * (1 - fy) + bottom * fy;
            }
        }
        return result;
    }

    private static double sourceCoordinate(int target, int targetSize, int sourceSize) {
        if (sourceSize == 1) {
            return 0;
        }
        if (targetSize == 1) {
            return (sourceSize - 1) / 2.0;
        }
        return target * (sourceSize - 1) / (double) (targetSize - 1);
    }

    /**
     * Get the map colour for a block id.
     *
     * @param blockId the block id, e.g. {@code minecraft:stone}
     * @return the colour as 0xRRGGBB, or {@link #FALLBACK_RGB} if unknown
     */
    public static int colourOf(String blockId) {
        return TERRAIN_COLOURS.getOrDefault(blockId, FALLBACK_RGB);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}

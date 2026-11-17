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

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeightmapImagesTest {

    @Test
    void heightPixelMappingRoundTrips() {
        int minY = -64;
        int maxY = 319;
        assertEquals(0, HeightmapImages.heightToPixel(minY, minY, maxY));
        assertEquals(HeightmapImages.MAX_16, HeightmapImages.heightToPixel(maxY, minY, maxY));
        assertEquals(0, HeightmapImages.heightToPixel(-100, minY, maxY));
        assertEquals(HeightmapImages.MAX_16, HeightmapImages.heightToPixel(400, minY, maxY));
        for (int y = minY; y <= maxY; y++) {
            int pixel = HeightmapImages.heightToPixel(y, minY, maxY);
            assertEquals(y, HeightmapImages.pixelToHeight(pixel, minY, maxY));
        }
    }

    @Test
    void normalizedToHeight() {
        assertEquals(10, HeightmapImages.normalizedToHeight(0, 10, 50));
        assertEquals(60, HeightmapImages.normalizedToHeight(1, 10, 50));
        assertEquals(35, HeightmapImages.normalizedToHeight(0.5, 10, 50));
        assertEquals(60, HeightmapImages.normalizedToHeight(2, 10, 50));
        assertEquals(10, HeightmapImages.normalizedToHeight(Double.NaN, 10, 50));
    }

    @Test
    void sixteenBitPngRoundTrip() throws IOException {
        int[][] pixels = {{0, 1000, 65535}, {12345, 54321, 7}};
        BufferedImage written = HeightmapImages.createGray16(pixels);
        assertEquals(BufferedImage.TYPE_USHORT_GRAY, written.getType());
        BufferedImage read = pngRoundTrip(written);
        assertEquals(2, read.getWidth());
        assertEquals(3, read.getHeight());
        double[][] values = HeightmapImages.readNormalized(read);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 3; z++) {
                assertEquals(pixels[x][z], (int) Math.round(values[x][z] * HeightmapImages.MAX_16));
            }
        }
    }

    @Test
    void eightBitGrayAndRgbAreNormalized() throws IOException {
        BufferedImage gray = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        gray.getRaster().setSample(0, 0, 0, 255);
        gray.getRaster().setSample(1, 0, 0, 51);
        double[][] grayValues = HeightmapImages.readNormalized(pngRoundTrip(gray));
        assertEquals(1.0, grayValues[0][0], 1e-9);
        assertEquals(0.2, grayValues[1][0], 1e-9);

        BufferedImage rgb = HeightmapImages.createRgb(new int[][] {{0xFFFFFF}, {0x000000}, {0x00FF00}});
        double[][] rgbValues = HeightmapImages.readNormalized(pngRoundTrip(rgb));
        assertEquals(1.0, rgbValues[0][0], 1e-9);
        assertEquals(0.0, rgbValues[1][0], 1e-9);
        assertEquals(0.7152, rgbValues[2][0], 1e-9);
    }

    @Test
    void resampleSameSizeIsIdentity() {
        double[][] source = {{0, 0.25, 0.5}, {1, 0.75, 0.1}};
        double[][] result = HeightmapImages.resampleBilinear(source, 2, 3);
        for (int x = 0; x < 2; x++) {
            assertArrayEquals(source[x], result[x], 1e-12);
        }
    }

    @Test
    void resampleUpscalesLinearly() {
        double[][] source = {{0, 0}, {1, 1}};
        double[][] result = HeightmapImages.resampleBilinear(source, 5, 3);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                assertEquals(x / 4.0, result[x][y], 1e-12);
            }
        }
    }

    @Test
    void resampleDownscalesAndHandlesSinglePixels() {
        double[][] source = {{0}, {0.5}, {1}};
        double[][] down = HeightmapImages.resampleBilinear(source, 2, 1);
        assertEquals(0, down[0][0], 1e-12);
        assertEquals(1, down[1][0], 1e-12);
        double[][] single = HeightmapImages.resampleBilinear(source, 1, 1);
        assertEquals(0.5, single[0][0], 1e-12);
        double[][] fromOne = HeightmapImages.resampleBilinear(new double[][] {{0.3}}, 4, 4);
        assertEquals(0.3, fromOne[3][3], 1e-12);
        double[][] bilinear = HeightmapImages.resampleBilinear(new double[][] {{0, 1}, {1, 2}}, 3, 3);
        assertEquals(1, bilinear[1][1], 1e-12);
        assertEquals(1.5, bilinear[2][1], 1e-12);
    }

    @Test
    void colourTableHasFallback() {
        assertEquals(0x7FB238, HeightmapImages.colourOf("minecraft:grass_block"));
        assertEquals(HeightmapImages.FALLBACK_RGB, HeightmapImages.colourOf("minecraft:oak_planks"));
        assertTrue(HeightmapImages.luminance(0x808080) > 0.49);
    }

    private static BufferedImage pngRoundTrip(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", out));
        return ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
    }
}

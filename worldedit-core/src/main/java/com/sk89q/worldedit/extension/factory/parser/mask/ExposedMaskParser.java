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

package com.sk89q.worldedit.extension.factory.parser.mask;

import com.google.common.collect.ImmutableSet;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.mask.OffsetsMask;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Parser for {@code #exposed}, {@code #surface}, or {@code #exposed[dir]} (e.g. up, down, north, south, east, west).
 */
public class ExposedMaskParser extends InputParser<Mask> {

    public ExposedMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (input.isEmpty() || "#".startsWith(input)) {
            return Stream.of("#exposed", "#surface");
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if ("#exposed".startsWith(lower) || "#surface".startsWith(lower)) {
            return Stream.of("#exposed", "#exposed[up]", "#exposed[down]", "#exposed[north]", "#exposed[south]", "#exposed[east]", "#exposed[west]", "#surface");
        }
        if (lower.startsWith("#exposed[") || lower.startsWith("#surface[")) {
            return Stream.of("#exposed[up]", "#exposed[down]", "#exposed[north]", "#exposed[south]", "#exposed[east]", "#exposed[west]", "#exposed[horizontal]")
                .filter(s -> s.startsWith(lower));
        }
        return Stream.empty();
    }

    @Override
    public Mask parseFromInput(String input, ParserContext context) throws InputParseException {
        String lower = input.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("#exposed") && !lower.startsWith("#surface")) {
            return null;
        }

        Mask airMask = Masks.negate(new ExistingBlockMask(context.requireExtent()));
        if (lower.equals("#exposed") || lower.equals("#surface")) {
            return OffsetsMask.builder(airMask).excludeSelf(true).build();
        }

        int open = input.indexOf('[');
        int close = input.lastIndexOf(']');
        if (open < 0 || close != input.length() - 1) {
            // Not something we understand, e.g. "#exposedfoo"; let other parsers have a go
            return null;
        }

        String dirStr = input.substring(open + 1, close).trim().toLowerCase(Locale.ROOT);
        return switch (dirStr) {
            case "up", "top", "sky" -> OffsetsMask.single(airMask, BlockVector3.at(0, 1, 0));
            case "down", "bottom", "floor" -> OffsetsMask.single(airMask, BlockVector3.at(0, -1, 0));
            case "north" -> OffsetsMask.single(airMask, BlockVector3.at(0, 0, -1));
            case "south" -> OffsetsMask.single(airMask, BlockVector3.at(0, 0, 1));
            case "east" -> OffsetsMask.single(airMask, BlockVector3.at(1, 0, 0));
            case "west" -> OffsetsMask.single(airMask, BlockVector3.at(-1, 0, 0));
            case "horizontal", "sides" -> OffsetsMask.builder(airMask)
                .offsets(ImmutableSet.of(
                    BlockVector3.at(0, 0, -1),
                    BlockVector3.at(0, 0, 1),
                    BlockVector3.at(1, 0, 0),
                    BlockVector3.at(-1, 0, 0)
                ))
                .excludeSelf(true)
                .build();
            default -> throw new InputParseException(TextComponent.of(
                "Unknown direction for exposed mask (expected up, down, north, south, east, west or horizontal): " + dirStr));
        };
    }
}

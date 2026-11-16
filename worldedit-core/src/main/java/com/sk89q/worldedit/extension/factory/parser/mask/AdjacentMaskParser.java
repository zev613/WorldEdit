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

import com.google.common.base.Splitter;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.OffsetsMask;
import com.sk89q.worldedit.internal.registry.InputParser;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Parser for {@code #adjacent[mask]}, {@code #adjacent[count,mask]}, or {@code #adjacent[min,max,mask]}.
 */
public class AdjacentMaskParser extends InputParser<Mask> {

    public AdjacentMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (input.isEmpty() || "#".startsWith(input)) {
            return Stream.of("#adjacent", "#near");
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if ("#adjacent".startsWith(lower) || "#near".startsWith(lower)) {
            return Stream.of("#adjacent", "#adjacent[1,water]", "#adjacent[2,stone]", "#adjacent[3,4,stone]");
        }
        return Stream.empty();
    }

    @Override
    public Mask parseFromInput(String input, ParserContext context) throws InputParseException {
        String lower = input.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("#adjacent") && !lower.startsWith("#near")) {
            return null;
        }

        if (lower.equals("#adjacent") || lower.equals("#near")) {
            return OffsetsMask.adjacent(new ExistingBlockMask(context.requireExtent()));
        }

        int open = input.indexOf('[');
        int close = input.lastIndexOf(']');
        if (open < 0 || close <= open) {
            return OffsetsMask.adjacent(new ExistingBlockMask(context.requireExtent()));
        }

        String argsStr = input.substring(open + 1, close).trim();
        List<String> parts = Splitter.on(',').trimResults().splitToList(argsStr);

        int minMatches = 1;
        int maxMatches = Integer.MAX_VALUE;
        String maskStr;

        if (parts.size() == 1) {
            maskStr = parts.get(0);
        } else if (parts.size() == 2) {
            try {
                int count = Integer.parseInt(parts.get(0));
                minMatches = count;
                maxMatches = count;
            } catch (NumberFormatException expected) {
                // If first arg isn't a number, treat entire string as mask
            }
            maskStr = parts.get(1);
        } else {
            try {
                minMatches = Integer.parseInt(parts.get(0));
                maxMatches = Integer.parseInt(parts.get(1));
            } catch (NumberFormatException expected) {
                // Ignore parse failure
            }
            maskStr = parts.get(2);
        }

        Mask subMask = worldEdit.getMaskFactory().parseFromInput(maskStr, context);
        return OffsetsMask.builder(subMask)
            .minMatches(minMatches)
            .maxMatches(maxMatches)
            .excludeSelf(true)
            .build();
    }
}

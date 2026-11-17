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

import com.google.common.base.CharMatcher;
import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.OffsetsMask;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

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
        if (!lower.equals("#adjacent") && !lower.equals("#near")
            && !lower.startsWith("#adjacent[") && !lower.startsWith("#near[")) {
            return null;
        }

        if (lower.equals("#adjacent") || lower.equals("#near")) {
            return OffsetsMask.adjacent(new ExistingBlockMask(context.requireExtent()));
        }

        int open = input.indexOf('[');
        int close = open < 0 ? -1 : StringUtil.findMatchingCloseBracket(input, open);
        if (open < 0 || close != input.length() - 1) {
            throw new InputParseException(TextComponent.of(
                "Invalid adjacent mask, expected #adjacent[mask], #adjacent[count,mask] or #adjacent[min,max,mask]: " + input));
        }

        String argsStr = input.substring(open + 1, close).trim();
        List<String> parts = StringUtil.splitOutsideBrackets(argsStr, CharMatcher.is(','));

        int minMatches = 1;
        int maxMatches = Integer.MAX_VALUE;
        String maskStr;

        try {
            if (parts.size() == 1) {
                maskStr = parts.get(0).trim();
            } else if (parts.size() == 2) {
                minMatches = Integer.parseInt(parts.get(0).trim());
                maxMatches = minMatches;
                maskStr = parts.get(1).trim();
            } else if (parts.size() == 3) {
                minMatches = Integer.parseInt(parts.get(0).trim());
                maxMatches = Integer.parseInt(parts.get(1).trim());
                maskStr = parts.get(2).trim();
            } else {
                throw new InputParseException(TextComponent.of("Too many arguments for adjacent mask: " + input));
            }
        } catch (NumberFormatException e) {
            throw new InputParseException(TextComponent.of("Invalid neighbor count in adjacent mask: " + input), e);
        }
        if (minMatches < 0 || maxMatches < minMatches || minMatches > 6) {
            throw new InputParseException(TextComponent.of("Adjacent neighbor counts must satisfy 0 <= min <= max, min <= 6: " + input));
        }

        Mask subMask = worldEdit.getMaskFactory().parseFromInput(maskStr, context);
        return OffsetsMask.builder(subMask)
            .minMatches(minMatches)
            .maxMatches(maxMatches)
            .excludeSelf(true)
            .build();
    }
}

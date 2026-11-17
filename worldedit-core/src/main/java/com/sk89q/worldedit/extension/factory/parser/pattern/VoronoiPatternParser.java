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

package com.sk89q.worldedit.extension.factory.parser.pattern;

import com.google.common.base.CharMatcher;
import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.VoronoiPattern;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Parser for {@code #voronoi[scale][palette]} or {@code #voronoi[palette]}.
 */
public class VoronoiPatternParser extends InputParser<Pattern> {

    public VoronoiPatternParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (input.isEmpty() || "#".startsWith(input)) {
            return Stream.of("#voronoi");
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if ("#voronoi".startsWith(lower) || "#cells".startsWith(lower)) {
            return Stream.of("#voronoi[5][stone,cobblestone]", "#voronoi[stone,cobblestone]");
        }
        return Stream.empty();
    }

    @Override
    public Pattern parseFromInput(String input, ParserContext context) throws InputParseException {
        String lower = input.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("#voronoi[") && !lower.startsWith("#cells[")) {
            return null;
        }

        List<String> groups = GradientPatternParser.bracketGroups(input, input.indexOf('['));
        if (groups == null || groups.isEmpty() || groups.size() > 2) {
            throw new InputParseException(TextComponent.of(
                "Invalid voronoi pattern, expected #voronoi[scale][palette] or #voronoi[palette]: " + input));
        }

        double scale = 5.0;
        String paletteString;
        if (groups.size() == 2) {
            paletteString = groups.get(1).trim();
            try {
                scale = Double.parseDouble(groups.get(0).trim());
            } catch (NumberFormatException e) {
                throw new InputParseException(TextComponent.of("Invalid voronoi cell scale: " + groups.get(0)), e);
            }
        } else {
            paletteString = groups.get(0).trim();
        }

        List<Pattern> patterns = new ArrayList<>();
        for (String token : StringUtil.splitOutsideBrackets(paletteString, CharMatcher.is(','))) {
            token = token.trim();
            if (!token.isEmpty()) {
                patterns.add(worldEdit.getPatternFactory().parseFromInput(token, context));
            }
        }

        if (patterns.isEmpty()) {
            throw new InputParseException(TextComponent.of("Empty palette for voronoi pattern: " + input));
        }

        return new VoronoiPattern(scale, patterns);
    }
}

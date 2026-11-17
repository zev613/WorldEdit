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
import com.google.common.base.Splitter;
import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.GradientPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Parser for {@code #gradient[axis][palette]} or {@code #gradient[palette]}.
 */
public class GradientPatternParser extends InputParser<Pattern> {

    public GradientPatternParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (input.isEmpty() || "#".startsWith(input)) {
            return Stream.of("#gradient");
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if ("#gradient".startsWith(lower) || "#grad".startsWith(lower)) {
            return Stream.of("#gradient[y][stone,cobblestone]", "#gradient[stone,cobblestone]");
        }
        return Stream.empty();
    }

    @Override
    public Pattern parseFromInput(String input, ParserContext context) throws InputParseException {
        String lower = input.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("#gradient[") && !lower.startsWith("#grad[")) {
            return null;
        }

        // Match #gradient[axis(,min,max)][palette] or #gradient[palette]
        List<String> groups = bracketGroups(input, input.indexOf('['));
        if (groups == null || groups.isEmpty() || groups.size() > 2) {
            throw new InputParseException(TextComponent.of(
                "Invalid gradient pattern, expected #gradient[axis,min,max][palette] or #gradient[palette]: " + input));
        }

        char axis = 'y';
        double minCoord = 0;
        double maxCoord = 64;
        boolean hasExplicitBounds = false;
        String paletteString;

        if (groups.size() == 2) {
            paletteString = groups.get(1).trim();

            List<String> configParts = Splitter.on(',').trimResults().splitToList(groups.get(0));
            String firstPart = configParts.get(0);
            if (firstPart.equalsIgnoreCase("x") || firstPart.equalsIgnoreCase("y") || firstPart.equalsIgnoreCase("z")) {
                axis = Character.toLowerCase(firstPart.charAt(0));
            } else if (!firstPart.isEmpty()) {
                throw new InputParseException(TextComponent.of("Invalid gradient axis (expected x, y or z): " + firstPart));
            }
            if (configParts.size() == 3) {
                try {
                    minCoord = Double.parseDouble(configParts.get(1));
                    maxCoord = Double.parseDouble(configParts.get(2));
                    hasExplicitBounds = true;
                } catch (NumberFormatException e) {
                    throw new InputParseException(TextComponent.of("Invalid gradient bounds: " + groups.get(0)), e);
                }
            } else if (configParts.size() != 1) {
                throw new InputParseException(TextComponent.of(
                    "Gradient settings must be [axis] or [axis,min,max]: " + groups.get(0)));
            }
        } else {
            paletteString = groups.get(0).trim();
        }

        if (!hasExplicitBounds) {
            // Attempt to derive from selection if available
            LocalSession session = context.getSession();
            if (session != null) {
                try {
                    Region region = session.getSelection();
                    switch (Character.toLowerCase(axis)) {
                        case 'x' -> {
                            minCoord = region.getMinimumPoint().x();
                            maxCoord = region.getMaximumPoint().x();
                        }
                        case 'z' -> {
                            minCoord = region.getMinimumPoint().z();
                            maxCoord = region.getMaximumPoint().z();
                        }
                        default -> {
                            minCoord = region.getMinimumPoint().y();
                            maxCoord = region.getMaximumPoint().y();
                        }
                    }
                } catch (IncompleteRegionException _) {
                    // Region incomplete or not selected, fall back to default bounds
                }
            }
        }

        List<Pattern> patterns = new ArrayList<>();
        for (String token : StringUtil.splitOutsideBrackets(paletteString, CharMatcher.is(','))) {
            token = token.trim();
            if (!token.isEmpty()) {
                patterns.add(worldEdit.getPatternFactory().parseFromInput(token, context));
            }
        }

        if (patterns.isEmpty()) {
            throw new InputParseException(TextComponent.of("Empty palette for gradient pattern: " + input));
        }

        return new GradientPattern(axis, minCoord, maxCoord, patterns);
    }

    /**
     * Splits input of the form {@code [a][b]...} starting at {@code from} into the contents of each
     * top-level bracket group, respecting nested brackets (e.g. block states).
     *
     * @return the group contents, or {@code null} if the input is malformed
     */
    @Nullable
    static List<String> bracketGroups(String input, int from) {
        List<String> groups = new ArrayList<>();
        int i = from;
        while (i < input.length()) {
            if (input.charAt(i) != '[') {
                return null;
            }
            int close = StringUtil.findMatchingCloseBracket(input, i);
            if (close < 0) {
                return null;
            }
            groups.add(input.substring(i + 1, close));
            i = close + 1;
        }
        return groups;
    }
}

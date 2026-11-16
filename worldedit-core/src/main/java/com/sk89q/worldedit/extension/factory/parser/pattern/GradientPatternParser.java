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

import com.google.common.base.Splitter;
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

        // Match #gradient[axis][palette] or #gradient[palette]
        int firstOpen = input.indexOf('[');
        int firstClose = input.indexOf(']', firstOpen);
        if (firstClose < 0) {
            return null;
        }

        char axis = 'y';
        double minCoord = 0;
        double maxCoord = 64;
        boolean hasExplicitBounds = false;

        String firstBracket = input.substring(firstOpen + 1, firstClose).trim();
        String paletteString;

        int secondOpen = input.indexOf('[', firstClose);
        if (secondOpen >= 0 && input.endsWith("]")) {
            // Format is #gradient[config][palette]
            paletteString = input.substring(secondOpen + 1, input.length() - 1).trim();

            List<String> configParts = Splitter.on(',').trimResults().splitToList(firstBracket);
            if (!configParts.isEmpty()) {
                String firstPart = configParts.get(0);
                if (firstPart.equalsIgnoreCase("x") || firstPart.equalsIgnoreCase("y") || firstPart.equalsIgnoreCase("z")) {
                    axis = firstPart.charAt(0);
                }
                if (configParts.size() >= 3) {
                    try {
                        minCoord = Double.parseDouble(configParts.get(1));
                        maxCoord = Double.parseDouble(configParts.get(2));
                        hasExplicitBounds = true;
                    } catch (NumberFormatException expected) {
                        // Keep default bounds if coordinates cannot be parsed
                    }
                }
            }
        } else {
            // Format is #gradient[palette]
            paletteString = firstBracket;
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
                } catch (IncompleteRegionException expected) {
                    // Region incomplete or not selected, fall back to default bounds
                }
            }
        }

        List<Pattern> patterns = new ArrayList<>();
        for (String token : Splitter.on(',').trimResults().split(paletteString)) {
            if (!token.isEmpty()) {
                patterns.add(worldEdit.getPatternFactory().parseFromInput(token, context));
            }
        }

        if (patterns.isEmpty()) {
            throw new InputParseException(TextComponent.of("Empty palette for gradient pattern: " + input));
        }

        return new GradientPattern(axis, minCoord, maxCoord, patterns);
    }
}

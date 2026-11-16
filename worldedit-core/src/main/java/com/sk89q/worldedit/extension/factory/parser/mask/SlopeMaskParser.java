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

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.SlopeMask;
import com.sk89q.worldedit.internal.registry.InputParser;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Parser for the {@code #slope} and {@code #slope[min,max]} mask syntax.
 */
public class SlopeMaskParser extends InputParser<Mask> {

    public SlopeMaskParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (input.isEmpty() || "#".startsWith(input)) {
            return Stream.of("#slope");
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if ("#slope".startsWith(lower)) {
            return Stream.of("#slope", "#slope[0,30]", "#slope[30,60]", "#slope[45,90]");
        }
        if (lower.startsWith("#slope[")) {
            return Stream.of("#slope[0,30]", "#slope[30,60]", "#slope[45,90]").filter(s -> s.startsWith(lower));
        }
        return Stream.empty();
    }

    @Override
    public Mask parseFromInput(String input, ParserContext context) throws InputParseException {
        String lower = input.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("#slope")) {
            return null;
        }

        double min = 35.0;
        double max = 90.0;

        if (lower.equals("#slope")) {
            return new SlopeMask(context.requireExtent(), min, max);
        }

        if (lower.startsWith("#slope[") && lower.endsWith("]")) {
            String inner = lower.substring(7, lower.length() - 1).trim();
            if (inner.contains(",")) {
                String[] parts = inner.split(",", 2);
                try {
                    min = Double.parseDouble(parts[0].trim());
                    max = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    throw new InputParseException(com.sk89q.worldedit.util.formatting.text.TextComponent.of("Invalid slope angle range: " + inner), e);
                }
            } else if (!inner.isEmpty()) {
                try {
                    min = Double.parseDouble(inner);
                    max = 90.0;
                } catch (NumberFormatException e) {
                    throw new InputParseException(com.sk89q.worldedit.util.formatting.text.TextComponent.of("Invalid slope angle: " + inner), e);
                }
            }
            return new SlopeMask(context.requireExtent(), min, max);
        }

        return null;
    }
}

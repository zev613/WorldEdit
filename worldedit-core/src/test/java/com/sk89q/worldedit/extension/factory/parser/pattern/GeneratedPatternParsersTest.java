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

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.PatternFactory;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.GradientPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.VoronoiPattern;
import com.sk89q.worldedit.util.translation.TranslationManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedPatternParsersTest extends BaseWorldEditTest {

    @BeforeAll
    static void stubTranslations() {
        // Building an InputParseException renders its message, which needs a translation manager
        TranslationManager translationManager = mock(TranslationManager.class);
        when(translationManager.convertText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(MOCKED_PLATFORM.getTranslationManager()).thenReturn(translationManager);
    }

    private final WorldEdit worldEdit = mock(WorldEdit.class);
    private final PatternFactory patternFactory = mock(PatternFactory.class);
    private final ParserContext context = new ParserContext();
    private final List<String> parsedTokens = new ArrayList<>();

    GeneratedPatternParsersTest() throws InputParseException {
        when(worldEdit.getPatternFactory()).thenReturn(patternFactory);
        when(patternFactory.parseFromInput(anyString(), any())).thenAnswer(inv -> {
            parsedTokens.add(inv.getArgument(0));
            return mock(Pattern.class);
        });
    }

    @Test
    void bracketGroupsRespectNesting() {
        assertEquals(List.of("y", "stone,oak_stairs[facing=north,half=top]"),
            GradientPatternParser.bracketGroups("[y][stone,oak_stairs[facing=north,half=top]]", 0));
        assertEquals(List.of("a"), GradientPatternParser.bracketGroups("[a]", 0));
        assertNull(GradientPatternParser.bracketGroups("[a", 0));
        assertNull(GradientPatternParser.bracketGroups("[a]x", 0));
    }

    @Test
    void gradientPassesBlockStatesThroughIntact() throws InputParseException {
        GradientPatternParser parser = new GradientPatternParser(worldEdit);
        Pattern result = parser.parseFromInput("#gradient[x,0,10][stone, oak_stairs[facing=north,half=top]]", context);
        assertInstanceOf(GradientPattern.class, result);
        assertEquals(List.of("stone", "oak_stairs[facing=north,half=top]"), parsedTokens);
    }

    @Test
    void gradientPaletteOnlyForm() throws InputParseException {
        GradientPatternParser parser = new GradientPatternParser(worldEdit);
        assertInstanceOf(GradientPattern.class, parser.parseFromInput("#grad[stone,dirt,grass_block]", context));
        assertEquals(List.of("stone", "dirt", "grass_block"), parsedTokens);
    }

    @Test
    void gradientRejectsBadInput() {
        GradientPatternParser parser = new GradientPatternParser(worldEdit);
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#gradient[q][stone]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#gradient[y,a,b][stone]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#gradient[stone", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#gradient[]", context));
    }

    @Test
    void gradientIgnoresUnrelatedInput() throws InputParseException {
        assertNull(new GradientPatternParser(worldEdit).parseFromInput("stone", context));
    }

    @Test
    void voronoiParses() throws InputParseException {
        VoronoiPatternParser parser = new VoronoiPatternParser(worldEdit);
        assertInstanceOf(VoronoiPattern.class, parser.parseFromInput("#voronoi[6][stone,andesite[]]", context));
        assertEquals(List.of("stone", "andesite[]"), parsedTokens);
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#voronoi[big][stone]", context));
        assertNull(parser.parseFromInput("#clipboard", context));
    }
}

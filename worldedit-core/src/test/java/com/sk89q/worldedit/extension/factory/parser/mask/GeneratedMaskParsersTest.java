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

import com.sk89q.worldedit.BaseWorldEditTest;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.MaskFactory;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.OffsetsMask;
import com.sk89q.worldedit.function.mask.SlopeMask;
import com.sk89q.worldedit.util.translation.TranslationManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedMaskParsersTest extends BaseWorldEditTest {

    @BeforeAll
    static void stubTranslations() {
        // Building an InputParseException renders its message, which needs a translation manager
        TranslationManager translationManager = mock(TranslationManager.class);
        when(translationManager.convertText(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(MOCKED_PLATFORM.getTranslationManager()).thenReturn(translationManager);
    }

    private final WorldEdit worldEdit = mock(WorldEdit.class);
    private final MaskFactory maskFactory = mock(MaskFactory.class);
    private final ParserContext context = new ParserContext();

    GeneratedMaskParsersTest() throws InputParseException {
        when(worldEdit.getMaskFactory()).thenReturn(maskFactory);
        when(maskFactory.parseFromInput(anyString(), any())).thenReturn(mock(Mask.class));
        context.setExtent(mock(Extent.class));
    }

    @Test
    void exposedDirections() throws InputParseException {
        ExposedMaskParser parser = new ExposedMaskParser(worldEdit);
        for (String input : new String[] {"#exposed", "#surface", "#exposed[up]", "#exposed[WEST]", "#surface[horizontal]"}) {
            assertNotNull(parser.parseFromInput(input, context), input);
        }
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#exposed[sideways]", context));
        assertNull(parser.parseFromInput("#existing", context));
    }

    @Test
    void adjacentForms() throws InputParseException {
        AdjacentMaskParser parser = new AdjacentMaskParser(worldEdit);
        assertInstanceOf(OffsetsMask.class, parser.parseFromInput("#adjacent", context));
        assertInstanceOf(OffsetsMask.class, parser.parseFromInput("#adjacent[water]", context));
        assertInstanceOf(OffsetsMask.class, parser.parseFromInput("#near[2,stone]", context));
        assertInstanceOf(OffsetsMask.class, parser.parseFromInput("#adjacent[1,3,oak_stairs[facing=north,half=top]]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#adjacent[x,y,stone]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#adjacent[3,1,stone]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#adjacent[stone", context));
        assertNull(parser.parseFromInput("#adjacentish", context));
    }

    @Test
    void slopeForms() throws InputParseException {
        SlopeMaskParser parser = new SlopeMaskParser(worldEdit);
        assertInstanceOf(SlopeMask.class, parser.parseFromInput("#slope", context));
        assertInstanceOf(SlopeMask.class, parser.parseFromInput("#slope[20,40]", context));
        assertInstanceOf(SlopeMask.class, parser.parseFromInput("#slope[45]", context));
        assertThrows(InputParseException.class, () -> parser.parseFromInput("#slope[steep]", context));
    }
}

package io.github.mightguy.microserivce.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mightguy.microserivce.model.SuggestionResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests the dictionary backed suggestion service.
 *
 * @author MighTguY
 * @since 6.7
 */
class SuggestionServiceTest {

    private static SuggestionService service;

    @BeforeAll
    static void loadDictionary() throws Exception {
        service = new SuggestionService();
    }

    @Test
    void correctsATypo() throws Exception {
        final SuggestionResponse response = service.suggest("Rechnug", 5);
        assertFalse(response.exact());
        assertNotNull(response.correction());
        assertEquals("rechnung", response.correction().term());
        assertTrue(response.dictionarySize() > 0);
    }

    @Test
    void acceptsAnExactTermIgnoringCase() throws Exception {
        final SuggestionResponse response = service.suggest("Haus", 5);
        assertTrue(response.exact());
        assertEquals(0.0, response.suggestions().get(0).distance(), 0.001);
    }

    @Test
    void returnsNothingForAnEmptyQuery() throws Exception {
        final SuggestionResponse response = service.suggest("   ", 5);
        assertTrue(response.suggestions().isEmpty());
        assertTrue(response.correction() == null);
    }
}

package io.github.mightguy.microserivce.controller;

import io.github.mightguy.microserivce.model.SuggestionResponse;
import io.github.mightguy.microserivce.service.SuggestionService;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller backing the small search front end.
 *
 * @author MighTguY
 * @since 6.7
 */
@RestController
@Tag(name = "Suggestions")
@RequestMapping("/api")
public class SuggestController {

    private final SuggestionService suggestionService;

    /**
     * Creates the controller.
     *
     * @param theSuggestionService the suggestion service, must not be {@code null}
     */
    public SuggestController(final SuggestionService theSuggestionService) {
        this.suggestionService = theSuggestionService;
    }

    /**
     * Returns spelling suggestions for the given query.
     *
     * @param query the user query
     * @param limit the maximum number of suggestions
     * @return the suggestions plus a possible correction
     * @throws SpellCheckException if the lookup fails
     */
    @Operation(summary = "Spelling suggestions",
            description = "Returns dictionary suggestions and a did-you-mean correction for a query")
    @GetMapping("/suggest")
    public SuggestionResponse suggest(
            @RequestParam(name = "q", defaultValue = "") final String query,
            @RequestParam(name = "k", defaultValue = "10") final int limit) throws SpellCheckException {
        return suggestionService.suggest(query, limit);
    }
}

package io.github.mightguy.microserivce.model;

import java.util.List;

/**
 * Response of the suggestion endpoint.
 *
 * @param query the normalized query the response belongs to
 * @param exact whether the query itself is part of the dictionary
 * @param correction the best correction, or {@code null} when the query already matches exactly
 * @param suggestions suggestions ordered by distance and frequency
 * @param micros the lookup duration in microseconds
 * @param dictionarySize the number of terms in the loaded dictionary
 * @author MighTguY
 * @since 6.7
 */
public record SuggestionResponse(
        String query,
        boolean exact,
        Suggestion correction,
        List<Suggestion> suggestions,
        long micros,
        int dictionarySize) {
}

package io.github.mightguy.microserivce.model;

/**
 * A single spelling suggestion returned by the suggestion endpoint.
 *
 * @param term the suggested dictionary term, never {@code null}
 * @param distance the edit distance between the query and the term
 * @param frequency the corpus frequency of the term
 * @author MighTguY
 * @since 6.7
 */
public record Suggestion(String term, double distance, double frequency) {
}

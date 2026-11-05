
package io.github.mightguy.spellcheck.symspell.common;

/**
 * Dictionary Item class which holds the term, frequency and the edit distance.
 *
 * @param term      the dictionary term
 * @param frequency the frequency of the term
 * @param distance  the edit distance of the term
  *
 * @author MighTguY
 * @since 6.7
 */
public record DictionaryItem(String term, Double frequency, Double distance) {
}

package io.github.mightguy.spellcheck.symspell.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Folds terms to ASCII so that accented input and dictionary terms compare consistently.
 *
 * <p>German umlauts and the sharp s are expanded to their conventional ASCII forms ({@code ä} to
 * {@code ae}, {@code ß} to {@code ss}) before remaining diacritics are removed. This keeps the
 * delete index small and lets a lookup with unaccented input match accented dictionary terms.
 *
 * @author MighTguY
 * @since 6.7
 */
public final class StringNormalizer {

    /** Extra capacity reserved when a German digraph is expanded. */
    private static final int EXPANSION_PADDING = 4;

    private StringNormalizer() {
    }

    /**
     * Folds the given term to ASCII.
     *
     * <p>The method is allocation-light: it only builds a new string when the term actually changes.
     *
     * @param term the term to fold, must not be {@code null}
     * @return the ASCII form of the term, or the same instance if no character had to be replaced
     */
    public static String toAscii(final String term) {
        final String expanded = expandGerman(term);
        final String decomposed = Normalizer.normalize(expanded, Normalizer.Form.NFKD);
        final StringBuilder result = new StringBuilder(decomposed.length());
        boolean changed = !expanded.equals(term);
        for (int i = 0; i < decomposed.length(); i++) {
            final char current = decomposed.charAt(i);
            if (isCombiningMark(current)) {
                changed = true;
                continue;
            }
            result.append(current);
        }
        return changed ? result.toString() : term;
    }

    /**
     * Expands German umlauts and the sharp s to their ASCII digraphs.
     *
     * @param term the term to expand
     * @return the expanded term, or the same instance if nothing was replaced
     */
    private static String expandGerman(final String term) {
        StringBuilder builder = null;
        for (int i = 0; i < term.length(); i++) {
            final char current = term.charAt(i);
            final String replacement = germanReplacement(current);
            if (replacement == null) {
                if (builder != null) {
                    builder.append(current);
                }
                continue;
            }
            if (builder == null) {
                builder = new StringBuilder(term.length() + EXPANSION_PADDING);
                builder.append(term, 0, i);
            }
            builder.append(replacement);
        }
        return builder == null ? term : builder.toString();
    }

    /**
     * Returns the ASCII replacement for a German character.
     *
     * @param current the character to map
     * @return the replacement, or {@code null} if the character is not a German special case
     */
    private static String germanReplacement(final char current) {
        switch (current) {
            case 'ä':
                return "ae";
            case 'ö':
                return "oe";
            case 'ü':
                return "ue";
            case 'Ä':
                return "Ae";
            case 'Ö':
                return "Oe";
            case 'Ü':
                return "Ue";
            case 'ß':
                return "ss";
            default:
                return null;
        }
    }

    /**
     * Checks whether a character is a combining mark produced by Unicode decomposition.
     *
     * @param current the character to check
     * @return true for combining marks
     */
    private static boolean isCombiningMark(final char current) {
        return Character.getType(current) == Character.NON_SPACING_MARK
                || Character.getType(current) == Character.COMBINING_SPACING_MARK
                || Character.getType(current) == Character.ENCLOSING_MARK;
    }

    /**
     * Folds a term to lowercase ASCII in one step.
     *
     * @param term the term to fold
     * @return the lower-case ASCII form
     */
    public static String toLowerAscii(final String term) {
        return toAscii(term).toLowerCase(Locale.ROOT);
    }
}

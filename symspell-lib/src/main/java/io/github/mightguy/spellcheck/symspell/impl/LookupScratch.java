package io.github.mightguy.spellcheck.symspell.impl;

/**
 * Per thread scratch state for a single lookup: the deduplication sets for generated candidates and
 * accepted suggestions plus the reusable buffers of the string free fast path. Keeping everything in
 * one object means the hot path needs only a single {@link ThreadLocal} lookup.
 *
 * @author MighTguY
 * @since 6.7
 */
final class LookupScratch {
    private static final int INITIAL_CANDIDATE_CAPACITY = 64;

    private static final int INITIAL_PREFIX_CAPACITY = 32;

    /**
     * Candidate deduplication of the generic, string based lookup path.
     */
    final StringHashSet consideredDeletes = new StringHashSet();

    /**
     * Suggestion deduplication of the generic, string based lookup path.
     */
    final StringHashSet consideredSuggestions = new StringHashSet();

    /**
     * Suggestion deduplication of the in-memory fast path, keyed by term id.
     */
    final IntHashSet consideredSuggestionIds = new IntHashSet();

    /**
     * Breadth first queue of delete masks of the in-memory fast path.
     */
    int[] candidateMasks = new int[INITIAL_CANDIDATE_CAPACITY];

    /**
     * Characters of the (possibly trimmed) phrase prefix.
     */
    char[] prefixChars = new char[INITIAL_PREFIX_CAPACITY];

    /**
     * Destination buffer for the candidate currently being verified.
     */
    char[] candidateChars = new char[INITIAL_PREFIX_CAPACITY];

    void clear() {
        consideredDeletes.clear();
        consideredSuggestions.clear();
        consideredSuggestionIds.clear();
    }
}

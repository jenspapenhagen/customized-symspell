package io.github.mightguy.spellcheck.symspell.impl;

/**
 * Per thread scratch state for a single lookup: the two deduplication sets for generated
 * candidates and accepted suggestions. Keeping both in one object means the hot path needs only a
 * single {@link ThreadLocal} lookup.
 */
final class LookupScratch {

    final StringHashSet consideredDeletes = new StringHashSet();

    final StringHashSet consideredSuggestions = new StringHashSet();

    void clear() {
        consideredDeletes.clear();
        consideredSuggestions.clear();
    }
}

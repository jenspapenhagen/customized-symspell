
package io.github.mightguy.spellcheck.symspell.impl;

import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.api.SpellChecker;
import io.github.mightguy.spellcheck.symspell.api.StringDistance;
import io.github.mightguy.spellcheck.symspell.common.Composition;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SpellHelper;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Verbosity;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckExceptionCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/**
 * SymSpell implementation of the Symmetric Delete spelling correction algorithm.
 *
 * <p>Dictionary deletes are generated once when entries are added; lookups therefore only need to
 * enumerate deletes of the input term and verify candidates with the configured distance metric.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SymSpellCheck extends SpellChecker {
    private static final long MAGIC_1024908267229 = 1024908267229L;
    private static final int MAGIC_32 = 32;
    private static final double MAGIC_0_01 = 0.01;
    private static final double MAGIC_10_0 = 10.0;


    //N equals the sum of all counts c in the dictionary only if the dictionary is complete,
    // but not if the dictionary is truncated or filtered
    private static Long nMax = MAGIC_1024908267229;

    /**
     * Reusable deduplication tables for the lookup hot path. They are per thread, because a
     * {@link SymSpellCheck} instance is shared between threads and the tables are only valid for the
     * duration of a single lookup.
     */
    private static final ThreadLocal<LookupScratch> LOOKUP_SCRATCH =
            ThreadLocal.withInitial(LookupScratch::new);

    /**
     * Creates a SymSpell checker.
     *
     * @param dataHolder the dictionary holder, must not be {@code null}
     * @param stringDistance the distance metric used for verification, must not be {@code null}
     * @param spellCheckSettings the settings of this checker, must not be {@code null}
     */
    public SymSpellCheck(final DataHolder dataHolder,
            final StringDistance stringDistance,
            final SpellCheckSettings spellCheckSettings) {
        super(dataHolder, stringDistance, spellCheckSettings);
    }


    /**
     * supports compound aware automatic spelling correction of multi-word input strings with three
     * cases 1. mistakenly inserted space into a correct word led to two incorrect terms 2. mistakenly
     * omitted space between two correct words led to one incorrect combined term 3. multiple
     * independent input terms with/without spelling errors Find suggested spellings for a multi-word
     * input string (supports word splitting/merging).
     *
     * @param thePhrase the string being spell checked
     * @param maxEditDistance the maximum edit distance between input and suggested words
     * @param tokenizeOnWhiteSpace whether the input is split into words before correction
     * @return the suggested spellings for the input string, never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    @Override
    public List<SuggestionItem> lookupCompound(final String thePhrase, final double maxEditDistance,
            final boolean tokenizeOnWhiteSpace)
            throws SpellCheckException {

        String phrase = thePhrase;

        if (maxEditDistance > spellCheckSettings.getMaxEditDistance()) {
            throw new SpellCheckException(SpellCheckExceptionCode.LOOKUP_ERROR,
                    "max Edit distance should be less than  global Max i.e" + spellCheckSettings
                            .getMaxEditDistance());
        }
        if ((phrase == null || phrase.isEmpty())) {
            throw new SpellCheckException(SpellCheckExceptionCode.LOOKUP_ERROR,
                    "Invalid input of string");
        }
        if (spellCheckSettings.isLowerCaseTerms()) {
            phrase = phrase.toLowerCase();
        }
        final String[] items;
        if (tokenizeOnWhiteSpace) {
            items = SpellHelper.tokenizeOnWhiteSpace(phrase);
        } else {
            items = new String[]{phrase};
        }
        List<SuggestionItem> suggestions = new ArrayList<>();
        final List<SuggestionItem> suggestionParts = new ArrayList<>();
        boolean isLastCombi = false;

        /*
            Early exit when in exclusion list
         */
        final String exclusionItem = dataHolder.getExclusionItem(phrase);
        if (exclusionItem != null && !exclusionItem.isEmpty()) {
            return SpellHelper.earlyExit(suggestions, exclusionItem, maxEditDistance, false);
        }

        for (int i = 0; i < items.length; i++) {
            //Normal suggestions
            suggestions = lookup(items[i], Verbosity.TOP, maxEditDistance);

            //combi check, always before split
            if (i > 0 && !isLastCombi
                    && lookupCombineWords(items[i], items[i - 1], suggestions, suggestionParts,
                    maxEditDistance)) {
                isLastCombi = true;
                continue;
            }

            isLastCombi = false;

            if ((suggestions != null && !suggestions.isEmpty()) && (suggestions.get(0).getDistance() == 0
                    || items[i].length() == 1)) {
                //choose best suggestion
                suggestionParts.add(suggestions.get(0));
            } else {
                lookupSplitWords(suggestionParts, suggestions, items[i], maxEditDistance);
            }


        }

        String joinedTerm = "";
        double joinedCount = Double.MAX_VALUE;
        for (SuggestionItem si : suggestionParts) {
            joinedTerm = joinedTerm.concat(si.getTerm()).concat(" ");
            joinedCount = Math.min(joinedCount, si.getCount());
        }
        joinedTerm = joinedTerm.trim();
        final double dist = stringDistance.getDistance(
                joinedTerm, phrase, Math.pow(2, 31) - 1);

        final SuggestionItem suggestionItem = new SuggestionItem(joinedTerm, dist, joinedCount);
        return Collections.singletonList(suggestionItem);
    }

    /**
     * supports compound aware automatic spelling correction of multi-word input strings with.
     * mistakenly omitted space between two correct words led to one incorrect combined term
     *
     * @param token           The string being spell checked.
     * @param previousToken   The string previousToken being spell checked.
     * @param maxEditDistance The maximum edit distance between input and suggested words.
     * @param suggestions     Suggestions items List
     * @param suggestionParts Partial suggestions list.
     */
    private boolean lookupCombineWords(final String token, final String previousToken,
            final List<SuggestionItem> suggestions,
            final List<SuggestionItem> suggestionParts, final double maxEditDistance) throws SpellCheckException {
        final List<SuggestionItem> suggestionsCombi = lookup(previousToken + token, Verbosity.TOP,
                maxEditDistance);
        if ((suggestionsCombi == null || suggestionsCombi.isEmpty())) {
            return false;
        }
        final SuggestionItem best1 = suggestionParts.get(suggestionParts.size() - 1);
        final SuggestionItem best2;
        if ((suggestions != null && !suggestions.isEmpty())) {
            best2 = suggestions.get(0);
        } else {
            best2 = new SuggestionItem(token,
                    maxEditDistance + 1, 0);
        }

        final double editDistance = stringDistance
                .getDistance(best1.getTerm().concat(" ").concat(best2.getTerm()),
                        previousToken.concat(" ").concat(token), maxEditDistance);

        if (editDistance >= 0 && suggestionsCombi.get(0).getDistance() < editDistance) {
            suggestionsCombi.get(0).setDistance(suggestionsCombi.get(0).getDistance() + 1);
            suggestionParts.remove(suggestionParts.size() - 1);
            suggestionParts.add(suggestionsCombi.get(0));
            return true;
        }
        return false;
    }


    /**
     * supports compound aware automatic spelling correction of multi-word input strings with.
     * mistakenly inserted space into a correct word led to two incorrect terms
     *
     * @param suggestions     Suggestions items List
     * @param maxEditDistance The maximum edit distance between input and suggested words.
     * @param suggestionParts Partial suggestions list.
     */
    private void lookupSplitWords(final List<SuggestionItem> suggestionParts,
            final List<SuggestionItem> suggestions, final String word, final double maxEditDistance)
            throws SpellCheckException {

        //if no perfect suggestion, split word into pairs
        SuggestionItem suggestionSplitBest = null;
        if ((suggestions != null && !suggestions.isEmpty())) {
            suggestionSplitBest = suggestions.get(0);
        }

        if (word.length() <= 1) {
            suggestionParts.add(new SuggestionItem(word, maxEditDistance + 1, 0));
            return;
        }

        for (int j = 1; j < word.length(); j++) {
            final String part1 = word.substring(0, j);
            final String part2 = word.substring(j);

            final List<SuggestionItem> suggestions1 = lookup(part1, Verbosity.TOP,
                    maxEditDistance);

            if (SpellHelper.continueConditionIfHeadIsSame(suggestions, suggestions1)) {
                continue;
            }

            final List<SuggestionItem> suggestions2 = lookup(part2, Verbosity.TOP, maxEditDistance);

            if (SpellHelper.continueConditionIfHeadIsSame(suggestions, suggestions2)) {
                continue;
            }

            final String split = suggestions1.get(0).getTerm() + " " + suggestions2.get(0).getTerm();
            double splitDistance = stringDistance.getDistance(word, split, maxEditDistance);
            double count;

            if (splitDistance < 0) {
                splitDistance = maxEditDistance + 1;
            }

            if (suggestionSplitBest != null) {
                if (splitDistance > suggestionSplitBest.getDistance()) {
                    continue;
                }
                if (splitDistance < suggestionSplitBest.getDistance()) {
                    suggestionSplitBest = null;
                }
            }

            final Double bigramFreq = dataHolder.getItemFrequencyBiGram(split);

            //if bigram exists in bigram dictionary
            if (bigramFreq != null) {
                count = bigramFreq;

                if ((suggestions != null && !suggestions.isEmpty())) {
                    if ((suggestions1.get(0).getTerm() + suggestions2.get(0).getTerm()).equals(word)) {
                        //make count bigger than count of single term correction
                        count = Math.max(count, suggestions.get(0).getCount() + 2);
                    } else if ((suggestions1.get(0).getTerm().equals(suggestions.get(0).getTerm()))
                            || (suggestions2.get(0).getTerm().equals(suggestions.get(0).getTerm()))) {
                        //make count bigger than count of single term correction
                        count = Math.max(count, suggestions.get(0).getCount() + 1);
                    }
                } else if ((suggestions1.get(0).getTerm() + suggestions2.get(0).getTerm()).equals(word)) {
                    count = Math.max(count,
                            Math.max(suggestions1.get(0).getCount(), suggestions2.get(0).getCount()));
                }
            } else {
                count = Math.min(spellCheckSettings.getBigramCountMin(),
                        (suggestions1.get(0).getCount() / nMax
                               * suggestions2.get(0).getCount()));
            }

            final SuggestionItem suggestionSplit = new SuggestionItem(split, splitDistance, count);

            if ((suggestionSplitBest == null) || (suggestionSplit.getCount() > suggestionSplitBest
                    .getCount())) {
                suggestionSplitBest = suggestionSplit;
            }

        }

        if (suggestionSplitBest != null) {
            suggestionParts.add(suggestionSplitBest);
        } else {
            suggestionParts.add(new SuggestionItem(word, maxEditDistance + 1, 0));
        }

    }

    /**
     * @param thePhrase the word being spell checked
     * @param verbosity controls the quantity/closeness of the returned suggestions
     * @param theMaxEditDistance the maximum edit distance between phrase and suggested words
     * @return the matching suggestions, never {@code null}
     * @throws SpellCheckException if the lookup fails
     */
    @Override
    public List<SuggestionItem> lookup(final String thePhrase, final Verbosity verbosity,
            final double theMaxEditDistance)
            throws SpellCheckException {

        String phrase = thePhrase;
        double maxEditDistance = theMaxEditDistance;

        if (maxEditDistance <= 0) {
            maxEditDistance = spellCheckSettings.getMaxEditDistance();
        }

        if (maxEditDistance > spellCheckSettings.getMaxEditDistance()) {
            throw new SpellCheckException(SpellCheckExceptionCode.LOOKUP_ERROR,
                    "max Edit distance should be less than  global Max i.e" + spellCheckSettings
                            .getMaxEditDistance());
        }

        final int phraseLen = phrase.length();
        final int prefixLength = spellCheckSettings.getPrefixLength();
        if (spellCheckSettings.isLowerCaseTerms()) {
            phrase = phrase.toLowerCase();
        }
        double suggestionCount = 0;
        final LookupScratch scratch = LOOKUP_SCRATCH.get();
        scratch.clear();
        final StringHashSet consideredDeletes = scratch.consideredDeletes;
        final StringHashSet consideredSuggestions = scratch.consideredSuggestions;
        final List<SuggestionItem> suggestionItems = new ArrayList<>(
                spellCheckSettings.getTopK());

        /*
            Early exit when in exclusion list
         */
        final String exclusionItem = dataHolder.getExclusionItem(phrase);
        if (exclusionItem != null && !exclusionItem.isEmpty()) {
            return SpellHelper.earlyExit(suggestionItems, exclusionItem, maxEditDistance, false);
        }

        /*
        Early exit when word is too big
         */
        if ((phraseLen - maxEditDistance) > spellCheckSettings.getMaxLength()) {
            return SpellHelper.earlyExit(suggestionItems, phrase, maxEditDistance,
                    spellCheckSettings.isIgnoreUnknown());
        }

        final Double frequency = dataHolder.getItemFrequency(phrase);

        if (frequency != null) {
            suggestionCount = frequency;
            suggestionItems.add(new SuggestionItem(phrase, 0, suggestionCount));

            if (verbosity != Verbosity.ALL) {
                return SpellHelper.earlyExit(suggestionItems, phrase, maxEditDistance,
                        spellCheckSettings.isIgnoreUnknown());
            }
        }

        consideredSuggestions.add(phrase);

        double maxEditDistance2 = maxEditDistance;
        final int phrasePrefixLen;
        final Queue<String> candidates = new ArrayDeque<>();

        if (phraseLen > prefixLength) {
            phrasePrefixLen = prefixLength;
            candidates.add(phrase.substring(0, phrasePrefixLen));
        } else {
            phrasePrefixLen = phraseLen;
            candidates.add(phrase);
        }

        String[] terms = null;
        double[] frequencies = null;
        if (dataHolder instanceof InMemoryDataHolder) {
            final InMemoryDataHolder inMemory = (InMemoryDataHolder) dataHolder;
            terms = inMemory.getTerms();
            frequencies = inMemory.getFrequencies();
        }

        while (!candidates.isEmpty()) {
            final String candidate = candidates.remove();
            final int candidateLen = candidate.length();
            final int lenDiff = phrasePrefixLen - candidateLen;
            /*
            early termination: if candidate distance is already higher than suggestion distance,
            than there are no better suggestions to be expected
             */
            if (lenDiff > maxEditDistance2) {
                if (verbosity == Verbosity.ALL) {
                    continue;
                }
                break;
            }


            /*
            read candidate entry from dictionary
             */
            final int[] deleteIds;
            if (dataHolder instanceof InMemoryDataHolder) {
                final InMemoryDataHolder inMem = (InMemoryDataHolder) dataHolder;
                final long storedKey = inMem.getHashFunction().hash(candidate) + 1L;
                final LongIntMap idx = inMem.getDeleteIndex();
                final int mask = idx.getMask();
                int slot = ((int) (storedKey >>> MAGIC_32) ^ (int) storedKey) & mask;
                int bucket = -1;
                while (true) {
                    final long cur = idx.getKey(slot);
                    if (cur == 0L) {
                        break;
                    }
                    if (cur == storedKey) {
                        bucket = idx.getValue(slot);
                        break;
                    }
                    slot = (slot + 1) & mask;
                }
                final int[][] buckets = inMem.getDeleteBuckets();
                deleteIds = (bucket < 0) ? null : buckets[bucket];
            } else {
                deleteIds = dataHolder.getDeleteIds(candidate);
            }
            if (deleteIds != null && deleteIds.length > 0) {
                for (int deleteId : deleteIds) {
                    final String suggestion = (terms != null) ? terms[deleteId] : dataHolder.getTerm(deleteId);
                    if (
                            filterOnEquivalance(suggestion, phrase, candidate, maxEditDistance2)
                                    ||
                                    filterOnPrefixLen(suggestion.length(), prefixLength,
                                            phrasePrefixLen, candidate.length(), maxEditDistance2)) {
                        continue;
                    }
                    /*
                        True Damerau-Levenshtein Edit Distance: adjust
                                        distance, if both distances>0
                                        We allow simultaneous edits (deletes) of
                                        max_edit_distance on on both the dictionary and
                                        the phrase term. For replaces and adjacent
                                        transposes the resulting edit distance stays
                                        <= max_edit_distance. For inserts and deletes the
                                        resulting edit distance might exceed
                                        max_edit_distance. To prevent suggestions of a
                                        higher edit distance, we need to calculate the
                                        resulting edit distance, if there are
                                        simultaneous edits on both sides.
                                        Example: (bank==bnak and bank==bink, but
                                        bank!=kanb and bank!=xban and bank!=baxn for
                                        max_edit_distance=1). Two deletes on each side of
                                        a pair makes them all equal, but the first two
                                        pairs have edit distance=1, the others edit
                                        distance=2.
                   */
                    double distance = 0;
                    int minDistance = 0;

                    if (candidateLen == 0) {
                        /*
                            suggestions which have no common chars with
                                                phrase (phrase_len<=max_edit_distance &&
                                                suggestion_len<=max_edit_distance)
                       */
                        distance = Math.max(phraseLen, suggestion.length());
                        if (distance > maxEditDistance2 || !consideredSuggestions.add(suggestion)) {
                            continue;
                        }
                    } else if (suggestion.length() == 1) {
                        distance = phrase.indexOf(suggestion.charAt(0)) < 0 ? phraseLen : phraseLen - 1;
                        if (distance > maxEditDistance2 || !consideredSuggestions.add(suggestion)) {
                            continue;
                        }
                    } else {

                        /*
                            handles the shortcircuit of min_distance assignment when first boolean expression
                            evaluates to False
                         */

                        minDistance = getMinDistanceOnPrefixbasis(maxEditDistance, candidate,
                                phrase, suggestion);

                        if (isDistanceCalculationRequired(phrase, maxEditDistance, minDistance, suggestion,
                                candidate)) {
                            continue;
                        } else {
                            if (verbosity != Verbosity.ALL
                                    && !deleteInSuggestionPrefix(candidate, candidateLen,
                                    suggestion, suggestion.length()) || !consideredSuggestions
                                    .add(suggestion)) {
                                continue;
                            }
                            distance = stringDistance.getDistance(phrase, suggestion, maxEditDistance2);
                            if (distance < 0) {
                                continue;
                            }
                        }
                    }

                    if (SpellHelper.isLessOrEqualDouble(distance, maxEditDistance2, MAGIC_0_01)) {
                        suggestionCount = (frequencies != null)
                                ? frequencies[deleteId]
                                : dataHolder.getTermFrequency(deleteId);
                        final SuggestionItem si = new SuggestionItem(suggestion, distance, suggestionCount);
                        if ((suggestionItems != null && !suggestionItems.isEmpty())) {
                            if (verbosity == Verbosity.CLOSEST && distance < maxEditDistance2) {
                                suggestionItems.clear();
                            } else if (verbosity == Verbosity.TOP) {
                                if (SpellHelper.isLessDouble(distance, maxEditDistance2, MAGIC_0_01)
                                        || suggestionCount > suggestionItems.get(0).getCount()) {
                                    maxEditDistance2 = distance;
                                    suggestionItems.set(0, si);
                                }
                                continue;
                            }
                        }

                        if (verbosity != Verbosity.ALL) {
                            maxEditDistance2 = distance;
                        }
                        suggestionItems.add(si);
                    }
                }
            }

            if (lenDiff < maxEditDistance && candidateLen <= prefixLength) {
                if (verbosity != Verbosity.ALL && lenDiff >= maxEditDistance2) {
                    continue;
                }

                final char[] deleteBuffer = new char[Math.max(candidateLen - 1, 0)];
                for (int i = 0; i < candidateLen; i++) {
                    candidate.getChars(0, i, deleteBuffer, 0);
                    candidate.getChars(i + 1, candidateLen, deleteBuffer, i);
                    final String delete = new String(deleteBuffer, 0, candidateLen - 1);
                    if (consideredDeletes.add(delete)) {
                        candidates.add(delete);
                    }
                }

            }
        }

        return suggestionItems;
    }


    private int getMinDistanceOnPrefixbasis(final double maxEditDistance, final String candidate, final String
            phrase,
            final String suggestion) {
        if (Double.compare(spellCheckSettings.getPrefixLength() - maxEditDistance,
                candidate.length()) == 0) {
            return
                    Math.min(phrase.length(), suggestion.length()) - spellCheckSettings.getPrefixLength();
        } else {
            return 0;
        }
    }

    private boolean filterOnPrefixLen(final int theSuggestionLen, final int prefixLen, final int phrasePrefixLen,
            final int candidateLen,
            final double maxEditDistance2) {
        final int suggestionLen = theSuggestionLen;
        final int suggestionPrefixLen = Math.min(suggestionLen, prefixLen);
        return (suggestionPrefixLen > phrasePrefixLen
                && (suggestionPrefixLen - candidateLen) > maxEditDistance2);
    }

    private boolean filterOnEquivalance(final String delete, final String phrase, final String candidate,
            final double maxEditDistance2) {

        final int deleteLen = delete.length();
        final int phraseLen = phrase.length();
        return (Math.abs(deleteLen - phraseLen) > maxEditDistance2)
                || (deleteLen < candidate.length())
                || (deleteLen == candidate.length() && !delete.equals(candidate))
                || (deleteLen == phraseLen && delete.equals(phrase));
    }

    /**
     * Check whether all delete chars are present in the suggestion prefix in correct order, otherwise.
     * this is just a hash collision
     */
    private boolean deleteInSuggestionPrefix(final String delete, final int deleteLen, final String suggestion,
            final int theSuggestionLen) {
        int suggestionLen = theSuggestionLen;
        if (deleteLen == 0) {
            return true;
        }
        if (spellCheckSettings.getPrefixLength() < suggestionLen) {
            suggestionLen = spellCheckSettings.getPrefixLength();
        }

        int j = 0;
        for (int i = 0; i < deleteLen; i++) {
            final char delChar = delete.charAt(i);
            while (j < suggestionLen && delChar != suggestion.charAt(j)) {
                j++;
            }
            if (j == suggestionLen) {
                return false;
            }
        }
        return true;
    }

    private boolean isDistanceCalculationRequired(final String phrase, final double maxEditDistance, final int min,
            final String suggestion, final String candidate) {
        if (Double.compare(spellCheckSettings.getPrefixLength() - maxEditDistance,
                candidate.length()) == 0 && min > 1) {
            final int trailingLength = min - 1;
            final int phraseFrom = phrase.length() - trailingLength;
            final int suggestionFrom = suggestion.length() - trailingLength;
            if (phraseFrom >= 0 && suggestionFrom >= 0
                    && !sameTrailingCharacters(phrase, phraseFrom, suggestion, suggestionFrom,
                    trailingLength)) {
                return true;
            }
        }

        if (min > 0) {
            final int phraseLast = phrase.length() - min;
            final int suggestionLast = suggestion.length() - min;
            return phrase.charAt(phraseLast) != suggestion.charAt(suggestionLast)
                    && (phraseLast > 0 && suggestionLast > 0)
                    && (phrase.charAt(phraseLast - 1) != suggestion.charAt(suggestionLast)
                            || phrase.charAt(phraseLast) != suggestion.charAt(suggestionLast - 1));
        }
        return false;
    }

    /**
     * Allocation free replacement for {@code substring(x).equals(substring(y))} on the hot path.
     */
    private static boolean sameTrailingCharacters(final String phrase, final int phraseFrom, final String suggestion,
            final int suggestionFrom, final int length) {
        for (int i = 0; i < length; i++) {
            if (phrase.charAt(phraseFrom + i) != suggestion.charAt(suggestionFrom + i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * word_segmentation` divides a string into words by inserting missing spaces at the appropriate
     * positions misspelled words are corrected and do not affect segmentation existing spaces are
     * allowed and considered for optimum segmentation
     * <p>
     * `word_segmentation` uses a novel approach *without* recursion.
     * https://medium.com/@wolfgarbe/fast-word-segmentation-for-noisy-text-2c2c41f9e8da
     * While each string of length n can be segmented in 2^n−1 possible compositions
     * https://en.wikipedia.org/wiki/Composition_(combinatorics) `word_segmentation` has a linear
     * runtime O(n) to find the optimum composition
     * <p>
     * Find suggested spellings for a multi-word input string (supports word splitting/merging).
     *
     * @param phrase                    The string being spell checked.
     * @param maxSegmentationWordLength The maximum word length
     * @param maxEditDistance           The maximum edit distance
     * @return The word segmented string
     */
    public Composition wordBreakSegmentation(final String thePhrase, final int maxSegmentationWordLength,
            final double maxEditDistance) throws SpellCheckException {

        String phrase = thePhrase;

        /*
        number of all words in the corpus used to generate the
                frequency dictionary. This is used to calculate the word
                occurrence probability p from word counts c : p=c/nMax. nMax equals
                the sum of all counts c in the dictionary only if the
                dictionary is complete, but not if the dictionary is
                truncated or filtered
         */
        if (phrase.isEmpty()) {
            return new Composition();
        }

        // v6.7.0 - Normalize ligatures (ﬁ → fi) and remove hyphens (syllabification artifacts)
        phrase = java.text.Normalizer.normalize(phrase, java.text.Normalizer.Form.NFKC)
                .replace("-", "");  // Remove hyphen-minus character (U+002D)

        // v6.7.0 - Preserve original phrase for case detection before lowercasing
        final String originalPhrase = phrase;

        if (spellCheckSettings.isLowerCaseTerms()) {
            phrase = phrase.toLowerCase();
        }

        /*
            Early exit when in exclusion list
         */
        final String exclusionItem = dataHolder.getExclusionItem(phrase);
        if (exclusionItem != null && !exclusionItem.isEmpty()) {
            return new Composition(phrase, exclusionItem, 0, 0);
        }

        final int arraySize = Math.min(maxSegmentationWordLength, phrase.length());
        final Composition[] compositions = new Composition[arraySize];
        for (int i = 0; i < arraySize; i++) {
            compositions[i] = new Composition();
        }
        int circularIndex = -1;
        //outer loop (column): all possible part start positions
        for (int j = 0; j < phrase.length(); j++) {
            //inner loop (row): all possible part lengths (from start position): part can't be bigger
            // than longest word in dictionary (other than long unknown word)
            final int imax = Math.min(phrase.length() - j, maxSegmentationWordLength);
            for (int i = 1; i <= imax; i++) {
                //get top spelling correction/ed for part
                String part = phrase.substring(j, j + i);
                // v6.7.0 - Get original part for case detection
                String originalPart = originalPhrase.substring(j, j + i);
                int separatorLength = 0;
                int topEd = 0;
                final double topProbabilityLog;
                String topResult;
                if (Character.isWhitespace(part.charAt(0))) {
                    //remove space for levensthein calculation
                    part = part.substring(1);
                    originalPart = originalPart.substring(1);
                } else {
                    //add ed+1: space did not exist, had to be inserted
                    separatorLength = 1;
                }

                //remove space from part1, add number of removed spaces to topEd
                topEd += part.length();
                //remove space
                part = part
                        .replace(" ", "");
                originalPart = originalPart.replace(" ", "");
                //add number of removed spaces to ed
                topEd -= part.length();

                // v6.7.0 - Lookup against lowercase for dictionary matching
                final List<SuggestionItem> results = this.lookup(part.toLowerCase(), Verbosity.TOP,
                        maxEditDistance);
                if ((results != null && !results.isEmpty())) {
                    topResult = results.get(0).getTerm();

                    // v6.7.0 - Preserve uppercase on first character from original part
                    // v6.7.2 - Safety check for length > 0
                    if (originalPart.length() > 0 && Character.isUpperCase(originalPart.charAt(0))) {
                        final char[] resultChars = topResult.toCharArray();
                        if (resultChars.length > 0) {
                            resultChars[0] = Character.toUpperCase(resultChars[0]);
                            topResult = new String(resultChars);
                        }
                    }

                    topEd += results.get(0).getDistance();
                    //Naive Bayes Rule
                    //we assume the word probabilities of two words to be independent
                    //therefore the resulting probability of the word combination is the product of the
                    // two word probabilities

                    //instead of computing the product of probabilities we are computing the sum of the
                    // logarithm of probabilities
                    //because the probabilities of words are about 10^-10, the product of many such small
                    // numbers could exceed (underflow) the floating number range and become zero
                    //log(ab)=log(a)+log(b)
                    topProbabilityLog = Math.log10(results.get(0).getCount() / nMax);
                } else {
                    topResult = part;
                    //default, if word not found
                    //otherwise long input text would win as long unknown word (with ed=edmax+1 ), although
                    // there there should many spaces inserted
                    topEd += part.length();
                    topProbabilityLog = Math.log10(MAGIC_10_0 / (nMax * Math.pow(MAGIC_10_0, part.length())));
                }
                final int destinationIndex = ((i + circularIndex) % arraySize);

                //set values in first loop
                if (j == 0) {
                    compositions[destinationIndex].setSegmentedString(part);
                    compositions[destinationIndex].setCorrectedString(topResult);
                    compositions[destinationIndex].setDistanceSum(topEd);
                    compositions[destinationIndex].setLogProbSum(topProbabilityLog);
                } else if ((i == maxSegmentationWordLength)
                        //replace values if better probabilityLogSum, if same edit distance OR one
                        // space difference
                        || (((compositions[circularIndex].getDistanceSum() + topEd
                        == compositions[destinationIndex].getDistanceSum()) || (
                        compositions[circularIndex].getDistanceSum() + separatorLength + topEd
                                == compositions[destinationIndex].getDistanceSum())) && (
                        compositions[destinationIndex].getLogProbSum()
                                < compositions[circularIndex].getLogProbSum() + topProbabilityLog))
                        //replace values if smaller edit distance
                        || (compositions[circularIndex].getDistanceSum() + separatorLength + topEd
                        < compositions[destinationIndex].getDistanceSum())) {

                    // v6.7.0 - Keep punctuation or apostrophe adjacent to previous word (no space)
                    final boolean isPunctuation = (topResult.length() == 1
                            && Character.getType(topResult.charAt(0)) >= Character.DASH_PUNCTUATION
                            && Character.getType(topResult.charAt(0)) <= Character.OTHER_PUNCTUATION);
                    final boolean isApostrophe = (topResult.length() == 2 && topResult.startsWith("'"));

                    if (isPunctuation || isApostrophe) {
                        // No space before punctuation/apostrophe
                        compositions[destinationIndex].setSegmentedString(
                                compositions[circularIndex].getSegmentedString() + part);
                        compositions[destinationIndex].setCorrectedString(
                                compositions[circularIndex].getCorrectedString() + topResult);
                    } else {
                        // Normal case: add space
                        compositions[destinationIndex].setSegmentedString(
                                compositions[circularIndex].getSegmentedString() + " " + part);
                        compositions[destinationIndex].setCorrectedString(
                                compositions[circularIndex].getCorrectedString() + " " + topResult);
                    }

                    compositions[destinationIndex].setDistanceSum(
                            compositions[circularIndex].getDistanceSum() + topEd);
                    compositions[destinationIndex].setLogProbSum(
                            compositions[circularIndex].getLogProbSum() + topProbabilityLog);
                }
            }
            circularIndex++;
            if (circularIndex >= arraySize) {
                circularIndex = 0;
            }
        }
        return compositions[circularIndex];
    }


}

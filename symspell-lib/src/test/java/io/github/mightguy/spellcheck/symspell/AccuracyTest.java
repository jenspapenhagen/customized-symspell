package io.github.mightguy.spellcheck.symspell;

import io.github.mightguy.spellcheck.symspell.api.CharDistance;
import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.api.SpellChecker;
import io.github.mightguy.spellcheck.symspell.api.StringDistance;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.QwertyDistance;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Test;

public class AccuracyTest {

    private static String fullTestData = "full_test.txt";
    // very verbose!!
    private static boolean printFailures = false;

    private static boolean acceptSecondHitAsSuccess = false;

    public void run(SpellChecker spellChecker) throws IOException, SpellCheckException {

        URL queryResourceUrl = this.getClass().getClassLoader().getResource(fullTestData);
        List<String> lines = readLines(queryResourceUrl);

        Map<String, String> tpCandidates = new HashMap<>();
        Map<String, String> fpCandidates = new HashMap<>();

        // index
        long indexStart = System.nanoTime();
        int indexCount = 0;
        for (String line : lines) {
            // 0 = correct word
            // 1 = true if this is a desired match,
            // false if this is a false-positive match
            // 2 = comma separated list of similar word
            String[] fields = line.split(":", 3);
            if (fields.length < 3) {
                continue;
            }
            boolean match = Boolean.parseBoolean(fields[1]);
            if (match) {
                appendToList(tpCandidates, fields);
            } else {
                if (fields[1].equals(fields[0])) {
                    System.out.println("WRONG: " + fields[1] + "," + fields[0] + ",false");
                }
                appendToList(fpCandidates, fields);
            }

            spellChecker.getDataHolder().addItem(new DictionaryItem(fields[0], 1d, 0d));
            indexCount++;
        }

        long indexTime = (System.nanoTime() - indexStart) / 1_000_000;

        long searchStart = System.nanoTime();

        // for each spellTestSetEntry do all searches
        int success = 0;
        int fail = 0;
        int truePositives = 0;
        int trueNegatives = 0;
        int falsePositives = 0;
        int falseNegatives = 0;
        int count = 0;

        for (Entry<String, String> candidate : tpCandidates.entrySet()) {
            List<SuggestionItem> results = spellChecker.lookupCompound(candidate.getKey());
            Collections.sort(results);
            // first or second match count as success
            if (isMatch(candidate, results)) {
                success++;
                truePositives++;
            } else {
                if (printFailures) {
                    System.out.println(
                            count + ": '" + candidate.getValue() + "' not found by search for " + candidate
                                    .getKey());
                    if (results.size() > 0) {
                        System.out.println("found '" + results.get(0)
                                + (results.size() > 1 ? "' and '" + results.get(1) : "")
                                + "' instead");
                    }
                    System.out.println();
                }
                fail++;
                falseNegatives++;
            }
            count++;
        }

        for (Entry<String, String> candidate : fpCandidates.entrySet()) {
            List<SuggestionItem> results = spellChecker.lookupCompound(candidate.getKey());
            Collections.sort(results);
            // first or second match count as success
            if (isMatch(candidate, results) && !candidate.getKey().equals(results.get(0))) {
                fail++;
                falsePositives++;
                if (printFailures) {
                    System.out
                            .println("false-positive: found '" + results.get(0) + "' by search for '" + candidate
                                    .getKey() + "'");
                    if (results.size() > 1 && acceptSecondHitAsSuccess) {
                        System.out.println("              + found '" + results.get(1) + "' as well'");
                    }
                    System.out.println();
                }
            } else {
                success++;
                trueNegatives++;
            }
            count++;
        }

        long searchTime = (System.nanoTime() - searchStart) / 1_000_000;

        System.out.println("indexed " + indexCount + " words in " + indexTime + "ms");
        System.out.println(count + " searches");
        System.out.println(searchTime + "ms => "
                + String.format("%1$.3f searches/ms", ((double) count / searchTime)));
        System.out.println();
        System.out.println(
                success + " success / accuracy => " + String.format("%.2f%%", (100.0 * success / count)));
        System.out.println(truePositives + " true-positives");
        System.out.println(trueNegatives + " true-negatives (?)");
        System.out.println();
        System.out.println(fail + " fail => " + String.format("%.2f%%", (100.0 * fail / count)));
        System.out.println(falseNegatives + " false-negatives");
        System.out.println(falsePositives + " false-positives");
        System.out.println();


    }

    private static List<String> readLines(URL url) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank()).toList();
        }
    }

    private void appendToList(Map<String, String> tpCandidates, String[] fields) {
        String targetWord = fields[0];
        String[] variants = fields[2].split(",");
        for (String variant : variants) {
            tpCandidates.put(variant, targetWord);
        }
    }

    private static boolean isMatch(Entry<String, String> candidate, List<SuggestionItem> results) {
        return (results.size() > 0 && results.get(0).getTerm().trim().equals(candidate.getValue()))
                || (results.size() > 0 && results.get(0).getTerm().trim().equals(candidate.getKey()))
                || (acceptSecondHitAsSuccess
                && results.size() > 1
                && results.get(1).getTerm().equals(candidate.getValue()));
    }

    @Test
    public  void testAccuracy() throws IOException, SpellCheckException {

        AccuracyTest accuracyTest = new AccuracyTest();

        System.out.println("=========  Basic =============================");
        //Basic
        SpellCheckSettings spellCheckSettings = SpellCheckSettings.builder()
                .countThreshold(0)
                .prefixLength(40)
                .maxEditDistance(2.0d).build();

        DataHolder dataHolder = new InMemoryDataHolder(spellCheckSettings,
                new Utf16HashFunction());

        SpellChecker spellChecker = new SymSpellCheck(dataHolder,
                accuracyTest.getStringDistance(spellCheckSettings, null),
                spellCheckSettings);
        accuracyTest.run(spellChecker);
        System.out.println("==================================================");

        //Weighted
        System.out.println("=========  Weighted =============================");
        spellCheckSettings = SpellCheckSettings.builder()
                .deletionWeight(1.01f)
                .insertionWeight(0.9f)
                .replaceWeight(0.7f)
                .transpositionWeight(1.0f)
                .countThreshold(0)
                .prefixLength(40)
                .maxEditDistance(2.0d).build();

        dataHolder = new InMemoryDataHolder(spellCheckSettings,
                new Utf16HashFunction());
        SpellChecker weightedSpellChecker = new SymSpellCheck(dataHolder,
                accuracyTest.getStringDistance(spellCheckSettings, null),
                spellCheckSettings);
        accuracyTest.run(weightedSpellChecker);
        System.out.println("==================================================");


        //Qwerty
        System.out.println("=========  Qwerty =============================");
        spellCheckSettings = SpellCheckSettings.builder()
                .countThreshold(0)
                .prefixLength(40)
                .maxEditDistance(2.0d).build();
        dataHolder = new InMemoryDataHolder(spellCheckSettings,
                new Utf16HashFunction());
        SpellChecker keyboardSpellChecker = new SymSpellCheck(dataHolder,
                accuracyTest.getStringDistance(spellCheckSettings, new QwertyDistance()),
                spellCheckSettings);
        accuracyTest.run(keyboardSpellChecker);
        System.out.println("==================================================");

        //QwertyWeighted
        System.out.println("=========  QwertyWeighted =============================");
        spellCheckSettings = SpellCheckSettings.builder()
                .deletionWeight(1.01f)
                .insertionWeight(0.9f)
                .replaceWeight(0.7f)
                .transpositionWeight(1.0f)
                .countThreshold(0)
                .prefixLength(40)
                .maxEditDistance(2.0d).build();
        dataHolder = new InMemoryDataHolder(spellCheckSettings,
                new Utf16HashFunction());
        SpellChecker keyboardWeightedSpellChecker = new SymSpellCheck(dataHolder,
                accuracyTest.getStringDistance(spellCheckSettings, new QwertyDistance()),
                spellCheckSettings);
        accuracyTest.run(keyboardWeightedSpellChecker);
        System.out.println("==================================================");
    }

    private StringDistance getStringDistance(SpellCheckSettings spellCheckSettings,
            CharDistance charDistance) {
        return new WeightedDamerauLevenshteinDistance(spellCheckSettings.getDeletionWeight(),
                spellCheckSettings.getInsertionWeight(),
                spellCheckSettings.getReplaceWeight(),
                spellCheckSettings.getTranspositionWeight(), charDistance);
    }

}

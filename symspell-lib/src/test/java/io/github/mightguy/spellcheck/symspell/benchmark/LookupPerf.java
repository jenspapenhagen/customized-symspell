package io.github.mightguy.spellcheck.symspell.benchmark;

import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.api.SpellChecker;
import io.github.mightguy.spellcheck.symspell.api.StringDistance;
import io.github.mightguy.spellcheck.symspell.common.DictionaryReader;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.Verbosity;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain (non-JUnit) throughput benchmark for the lookup hot path.
 * Run with: java -cp ... LookupPerf [dictionary] [queries]
 */
public final class LookupPerf {

    private LookupPerf() {
    }

    public static void main(String[] args) throws Exception {
        String dictionary = args.length > 0 ? args[0] : "frequency_dictionary_en_82_765.txt";
        String queriesFile = args.length > 1 ? args[1] : "noisy_query_en_1000.txt";
        int rounds = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int warmupQueries = args.length > 3 ? Integer.parseInt(args[3]) : 1000;

        List<String> queries = readWords(Paths.get(resource(queriesFile).getFile()));
        SpellCheckSettings settings = SpellCheckSettings.builder().maxEditDistance(2).build();

        long buildStart = System.nanoTime();
        DataHolder dataHolder = new InMemoryDataHolder(settings, new Utf16HashFunction());
        for (DictionaryItem item : DictionaryReader.read(resource(dictionary).openStream())) {
            dataHolder.addItem(item);
        }
        long buildMillis = (System.nanoTime() - buildStart) / 1_000_000;
        StringDistance distance = new WeightedDamerauLevenshteinDistance(
                settings.getDeletionWeight(), settings.getInsertionWeight(), settings.getReplaceWeight(),
                settings.getTranspositionWeight(), null);
        SpellChecker checker = new SymSpellCheck(dataHolder, distance, settings);

        // warmup
        for (int round = 0; round < Math.max(1, warmupQueries / queries.size()); round++) {
            for (Verbosity verbosity : Verbosity.values()) {
                for (String query : queries) {
                    checker.lookup(query, verbosity, 2);
                }
            }
        }

        long total = 0;
        for (Verbosity verbosity : Verbosity.values()) {
            long best = Long.MAX_VALUE;
            for (int round = 0; round < rounds; round++) {
                long start = System.nanoTime();
                for (String query : queries) {
                    total += checker.lookup(query, verbosity, 2).size();
                }
                best = Math.min(best, System.nanoTime() - start);
            }
            double nanosPerQuery = (double) best / queries.size();
            System.out.printf("%-8s %9.3f us/query  %,12.0f queries/s%n",
                    verbosity, nanosPerQuery / 1000.0, 1_000_000_000.0 / nanosPerQuery);
        }
        System.out.printf("dictionary=%d words build=%d ms results=%d%n",
                dataHolder.getSize(), buildMillis, total);
        System.out.printf("heap used=%d MB%n", usedHeapMb());
    }

    private static long usedHeapMb() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private static URL resource(String name) {
        URL url = LookupPerf.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new IllegalArgumentException("missing test resource: " + name);
        }
        return url;
    }

    private static List<String> readWords(Path path) throws IOException {

        List<String> words = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                words.add(parts[0]);
            }
        }
        return words;
    }
}

package io.github.mightguy.symspell.solr.component;

import io.github.mightguy.spellcheck.symspell.api.CharDistance;
import io.github.mightguy.spellcheck.symspell.api.DataHolder;
import io.github.mightguy.spellcheck.symspell.api.SpellChecker;
import io.github.mightguy.spellcheck.symspell.api.StringDistance;
import io.github.mightguy.spellcheck.symspell.common.DictionaryItem;
import io.github.mightguy.spellcheck.symspell.common.SpellCheckSettings;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.spellcheck.symspell.common.Utf16HashFunction;
import io.github.mightguy.spellcheck.symspell.common.Verbosity;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import io.github.mightguy.spellcheck.symspell.exception.SpellCheckException;
import io.github.mightguy.spellcheck.symspell.impl.InMemoryDataHolder;
import io.github.mightguy.spellcheck.symspell.impl.SymSpellCheck;
import io.github.mightguy.symspell.solr.eventlistner.CustomSpellCheckListner;
import io.github.mightguy.symspell.solr.utils.Constants;
import io.github.mightguy.symspell.solr.utils.SearchRequestUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solr search component that holds the dictionary and contributes spelling suggestions to a query.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SpellcheckComponent extends SearchComponent implements SolrCoreAware {

    private static final Logger log = LoggerFactory.getLogger(SpellcheckComponent.class);
    private static final int DEFAULT_THRESHOLD = 15;
    private static final int DEFAULT_PREFIX_LENGTH = 7;
    private static final int DEFAULT_COUNT_THRESHOLD = 10;

    /**
     * Creates the component. Solr calls {@link #init(NamedList)} during startup.
     */
    public SpellcheckComponent() {
        // configured in init()
    }

    private NamedList initParams;
    private SpellChecker spellChecker;

    /**
     * Returns the spell checker backing this component.
     *
     * @return the spell checker, or {@code null} before initialization
     */
    public SpellChecker getSpellChecker() {
        return spellChecker;
    }
    private CustomSpellCheckListner customSpellCheckListner;
    private int threshold = 0;

    /**
     * Name under which the component is registered in solrconfig.xml.
     */
    public static final String COMPONENT_NAME = "custom_spellcheck";

    @Override
    public void init(final NamedList args) {
        super.init(args);
        this.initParams = args;
    }

    @Override
    public void prepare(final ResponseBuilder rb) throws IOException {
        final SolrParams params = rb.req.getParams();
        threshold = params.getInt(Constants.SPELLCHECK_THRESHOLD, DEFAULT_THRESHOLD);
        if (!params.getBool(COMPONENT_NAME, false)) {
            return;
        }
        try {
            if (params.getBool(Constants.SPELLCHECK_BUILD, false)) {
                customSpellCheckListner.reload(rb.req.getSearcher(), spellChecker);
                rb.rsp.add("command", "build");
            }
        } catch (SpellCheckException ex) {
            log.error("Unable to build spellcheck indexes");
            throw new IOException(ex);
        }

    }

    @Override
    public void process(final ResponseBuilder rb) throws IOException {
        if (!rb.req.getParams().getBool(Constants.SPELLCHECK_ENABLE, true) || SearchRequestUtil
                .resultGreaterThanThreshold(rb.rsp, threshold)) {
            log.debug("Spellcheck is disbaled either by query or result  greater than threshold [{}]",
                    threshold);
            return;
        }
        final SolrParams params = rb.req.getParams();
        final String q = params.get(Constants.SPELLCHECK_Q, params.get(CommonParams.Q));
        final boolean sow = params.getBool(Constants.SPELLCHECK_SOW, true);
        final List<SuggestionItem> suggestions;
        try {
            if (sow) {
                suggestions = spellChecker.lookupCompound(q);
            } else {
                suggestions = spellChecker.lookupCompound(q, 2, false);
            }
            if (suggestions != null && !suggestions.isEmpty()) {
                addToResponse(rb, suggestions);
            }
        } catch (SpellCheckException ex) {
            log.error("exception occured while looking for spelling suggestions");
            throw new IOException(ex);
        }

    }


    private void addToResponse(final ResponseBuilder rb, final List<SuggestionItem> suggestions) {
        rb.rsp.add("spell_suggestions", toNamedList(suggestions));
    }

    private NamedList toNamedList(final List<SuggestionItem> suggestionItems) {
        final NamedList result = new NamedList();
        if (suggestionItems == null || suggestionItems.isEmpty()) {
            return result;
        }
        final Map<String, String> suggestions = suggestionItems.parallelStream().collect(
                Collectors.toMap(SuggestionItem::getTerm,
                        s -> s.getCount() + "," + s.getDistance() + "," + s.getScore()));

        result.add("spellcheck", suggestions);
        result.add("correctlySpelled", suggestionItems.get(0).getDistance() == 0);
        return result;
    }

    @Override
    public String getDescription() {
        return "SymSpell based Spellchecker Component";
    }

    @Override
    public void inform(final SolrCore core) {
        if (initParams == null) {
            return;
        }

        log.info("Initializing spell checkers");

        if (initParams.getName(0).equals("spellcheckers")) {
            final Object cfg = initParams.getVal(0);
            if (cfg instanceof NamedList) {
                addSpellChecker(core, (NamedList) cfg);
            } else if (cfg instanceof Map) {
                addSpellChecker(core, new NamedList((Map) cfg));
            } else if (cfg instanceof List) {
                for (Object o : (List) cfg) {
                    if (o instanceof Map) {
                        addSpellChecker(core, new NamedList((Map) o));
                    }
                }
            }
        }

        log.info("Spell checker  Initialization completed");
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    private StringDistance getStringDistance(final NamedList spellchecker,
            final SpellCheckSettings spellCheckSettings, final SolrCore core) {

        final String chardistanceClassname = SearchRequestUtil
                .getFromNamedList(spellchecker, "chardistance_classname", null);

        CharDistance charDistance = null;
        if (chardistanceClassname != null) {
            charDistance = SearchRequestUtil
                    .getClassFromLoader(chardistanceClassname, core.getResourceLoader(), CharDistance.class,
                            new String[0], toObjectArr());
        }

        return new WeightedDamerauLevenshteinDistance(spellCheckSettings.getDeletionWeight(),
                spellCheckSettings.getInsertionWeight(), spellCheckSettings.getReplaceWeight(),
                spellCheckSettings.getTranspositionWeight(), charDistance);
    }

    private void addSpellChecker(final SolrCore core, final NamedList spellcheckerNL) {

        final SpellCheckSettings spellCheckSettings = SpellCheckSettings.builder()
                .deletionWeight(SearchRequestUtil.getFromNamedList(spellcheckerNL, "deleteionWeight", 1.0f))
                .insertionWeight(
                        SearchRequestUtil.getFromNamedList(spellcheckerNL, "insertionWeight", 1.0f))
                .replaceWeight(SearchRequestUtil.getFromNamedList(spellcheckerNL, "replaceWeight", 1.0f))
                .transpositionWeight(
                        SearchRequestUtil.getFromNamedList(spellcheckerNL, "transpositionWeight", 1.0f))
                .maxEditDistance(
                        SearchRequestUtil.getFromNamedList(spellcheckerNL, "maxEditDistance", 2.0d))
                .prefixLength(SearchRequestUtil.getFromNamedList(spellcheckerNL, "prefixLength", DEFAULT_PREFIX_LENGTH))
                .verbosity(Verbosity.valueOf(
                        SearchRequestUtil
                                .getFromNamedList(spellcheckerNL, "verbosity", Verbosity.ALL.name())))
                .countThreshold(
                        SearchRequestUtil.getFromNamedList(spellcheckerNL, "countThreshold", DEFAULT_COUNT_THRESHOLD))
                .doKeySplit(
                        SearchRequestUtil.getFromNamedList(spellcheckerNL, "createBigram", true))
                .keySplitRegex(
                        SearchRequestUtil.getFromNamedList(spellcheckerNL, "bigramSplitRegex", "\\s+"))
                .build();

        final StringDistance stringDistance = getStringDistance(spellcheckerNL, spellCheckSettings, core);

        final DataHolder dataHolder = new InMemoryDataHolder(spellCheckSettings, new Utf16HashFunction());

        spellChecker = new SymSpellCheck(dataHolder, stringDistance, spellCheckSettings);

        final String[] fieldList = SearchRequestUtil.getFromNamedList(spellcheckerNL, "field_names", "")
                .split("\\s+");

        // Register event listeners for this SpellChecker
        customSpellCheckListner = new CustomSpellCheckListner(core, spellChecker, fieldList);
        core.registerFirstSearcherListener(customSpellCheckListner);

        final String unigramsFile = SearchRequestUtil.getFromNamedList(spellcheckerNL, "unigrams_file", null);
        final String bigramsFile = SearchRequestUtil.getFromNamedList(spellcheckerNL, "bigrams_file", null);
        final String exclusionsFile = SearchRequestUtil
                .getFromNamedList(spellcheckerNL, "exclusions_file", null);
        final String exclustionnFileSeperator = SearchRequestUtil
                .getFromNamedList(spellcheckerNL, "exclusions_file_sp", "\\s+");
        loadDefault(unigramsFile, bigramsFile, exclusionsFile, spellChecker, core,
                exclustionnFileSeperator);
        final boolean buildOnCommit = Boolean.parseBoolean((String) spellcheckerNL.get("buildOnCommit"));
        final boolean buildOnOptimize = Boolean.parseBoolean((String) spellcheckerNL.get("buildOnOptimize"));
        if (buildOnCommit || buildOnOptimize) {
            log.info("Registering newSearcher listener for spellChecker");
            core.registerNewSearcherListener(
                    new CustomSpellCheckListner(core, spellChecker, fieldList));
        }

    }

    private void loadDefault(final String unigramsFile, final String bigramsFile, final String exclusionsFile,
            final SpellChecker theSpellChecker,
            final SolrCore core, final String exclusionListSperatorRegex) {
        try {
            if (unigramsFile != null && !unigramsFile.isEmpty()) {
                loadUniGramFile(core.getResourceLoader().openResource(unigramsFile),
                        theSpellChecker.getDataHolder());
            }

            if (bigramsFile != null && !bigramsFile.isEmpty()) {
                loadBiGramFile(core.getResourceLoader().openResource(bigramsFile),
                        theSpellChecker.getDataHolder());
            }

            if (exclusionsFile != null && !exclusionsFile.isEmpty()) {
                loadExclusions(core.getResourceLoader().openResource(exclusionsFile),
                        theSpellChecker.getDataHolder(), exclusionListSperatorRegex);
            }

        } catch (SpellCheckException | IOException ex) {
            log.error("Error occured while loading default Configs for Spellcheck");
        }
    }

    private void loadUniGramFile(final InputStream inputStream, final DataHolder dataHolder)
            throws IOException, SpellCheckException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] arr = line.split("\\s+");
                dataHolder.addItem(new DictionaryItem(arr[0], Double.parseDouble(arr[1]), -1.0));
            }
        }
    }

    private void loadBiGramFile(final InputStream inputStream, final DataHolder dataHolder)
            throws IOException, SpellCheckException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] arr = line.split("\\s+");
                dataHolder
                        .addItem(new DictionaryItem(arr[0] + " " + arr[1], Double.parseDouble(arr[2]), -1.0));
            }
        }
    }

    private void loadExclusions(final InputStream inputStream, final DataHolder dataHolder, final String seperatorRegex)
            throws IOException, SpellCheckException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] arr = line.split(seperatorRegex);
                if (arr.length == 2) {
                    dataHolder.addExclusionItem(arr[0], arr[1]);
                }
            }
        }
    }

    private Object[] toObjectArr(final Object... args) {
        return args;
    }

}

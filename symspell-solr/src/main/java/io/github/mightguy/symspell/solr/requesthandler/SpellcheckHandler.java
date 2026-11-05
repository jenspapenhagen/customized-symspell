/*
 * Copyright (c) 2018 Walmart Co. All rights reserved.
 */

package io.github.mightguy.symspell.solr.requesthandler;

import io.github.mightguy.spellcheck.symspell.api.SpellChecker;
import io.github.mightguy.spellcheck.symspell.common.Composition;
import io.github.mightguy.spellcheck.symspell.common.SuggestionItem;
import io.github.mightguy.symspell.solr.component.SpellcheckComponent;
import io.github.mightguy.symspell.solr.utils.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

/**
 * Solr request handler that exposes single term and compound suggestions.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SpellcheckHandler extends SearchHandler {

    /**
     * Creates the handler. The component and checker are resolved lazily on the first request.
     */
    public SpellcheckHandler() {
        // resolved lazily in initSpellCheck()
    }

    SpellcheckComponent spellCheckComponent;
    SpellChecker spellChecker;


    @Override
    public void handleRequestBody(final SolrQueryRequest req, final SolrQueryResponse rsp) throws Exception {

        if (spellChecker == null) {
            initSpellCheck();
        }
        final String word = req.getParams().get(Constants.SPELLCHECK_Q, "");
        rsp.add("lookup_compound", createFeature(spellChecker.lookupCompound(word)));
        rsp.add("lookup", createFeature(spellChecker.lookup(word)));
        rsp.add("lookup_wordbreak", createFeatureComp(spellChecker.wordBreakSegmentation(word)));
        if (req.getParams().getBool(Constants.SPELLCHECK_DATALOAD_UNIGRAM, false)) {
            rsp.add("UNIGRAM", spellChecker.getDataHolder().getItemFrequency(word));
        }
        if (req.getParams().getBool(Constants.SPELLCHECK_DATALOAD_BIGRAM, false)) {
            rsp.add("BIGRAM", spellChecker.getDataHolder().getItemFrequencyBiGram(word));
        }
    }

    private Object createFeatureComp(final Composition composition) {
        final Map<String, Object> dataInfo = new HashMap<>();
        dataInfo.put("CorrectedString", composition.getCorrectedString());
        dataInfo.put("DistanceSum", composition.getDistanceSum());
        dataInfo.put("ProbSum", composition.getLogProbSum());
        dataInfo.put("SegmentedString", composition.getSegmentedString());
        return dataInfo;
    }

    private Object createFeature(final List<SuggestionItem> items) {
        final List<Map<String, Object>> datas = new ArrayList<>();
        for (SuggestionItem suggestionItem : items) {
            final Map<String, Object> itemInfo = new HashMap<>();
            itemInfo.put("term", suggestionItem.getTerm().trim());
            itemInfo.put("count", suggestionItem.getCount());
            itemInfo.put("distance", suggestionItem.getDistance());
            itemInfo.put("score", suggestionItem.getScore());
            datas.add(itemInfo);
        }
        return datas;

    }

    private void initSpellCheck() {
        final List<SearchComponent> components = getComponents();
        SearchComponent spellComponent = null;
        for (SearchComponent searchComponent : components) {
            if (searchComponent instanceof SpellcheckComponent) {
                spellComponent = searchComponent;
                break;
            }
        }
        if (spellComponent == null) {
            throw new SolrException(ErrorCode.BAD_REQUEST, "Component Should be SpellCheck");
        }
        spellCheckComponent = (SpellcheckComponent) spellComponent;
        spellChecker = spellCheckComponent.getSpellChecker();
    }

    @Override
    public String getDescription() {
        return "Spellcheck based request handler";
    }
}

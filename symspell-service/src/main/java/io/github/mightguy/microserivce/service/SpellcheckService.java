package io.github.mightguy.microserivce.service;

import io.github.mightguy.microserivce.config.SpellcheckConfiguration;
import io.github.mightguy.microserivce.model.Response;
import io.github.mightguy.spellcheck.symspell.api.CharDistance;
import io.github.mightguy.spellcheck.symspell.api.StringDistance;
import io.github.mightguy.spellcheck.symspell.common.QwertyDistance;
import io.github.mightguy.spellcheck.symspell.common.QwertzDistance;
import io.github.mightguy.spellcheck.symspell.common.WeightedDamerauLevenshteinDistance;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
/**
 * Service layer that resolves the configured distance metric and answers edit distance requests.
 *
 * @author MighTguY
 * @since 6.7
 */
public class SpellcheckService {

    StringDistance stringDistance;
    SpellcheckConfiguration configuration;
    QwertyDistance qwertyDistance = new QwertyDistance();
    QwertzDistance qwertzDistance = new QwertzDistance();


    /**
     *
     * @param configuration
     */
    @Autowired
    /**
     * Creates the service and resolves the configured distance metric.
     *
     * @param theConfiguration the external configuration, must not be {@code null}
     */
    public SpellcheckService(final SpellcheckConfiguration theConfiguration) {
        this.configuration = theConfiguration;
        stringDistance = new WeightedDamerauLevenshteinDistance(theConfiguration.getDeletionWeight(),
                theConfiguration.getInsertionWeight(), theConfiguration.getReplaceWeight(),
                theConfiguration.getTranspositionWeight(),
                getCharDistanceObject(theConfiguration.getCharDistance()));
    }

    /**
     * Computes the edit distance and wraps it in a response.
     *
     * @param source the source word
     * @param target the target word
     * @param maxED the maximum distance to compute
     * @return the response containing both words and the distance
     */
    public Response getEditDistance(final String source, final String target, final double maxED) {
        final double distance = stringDistance.getDistance(source, target, maxED);
        return generateResponse(source, target, distance);
    }

    /**
     *
     * @param source
     * @param target
     * @return
     */
    public Response getEditDistance(final String source, final String target) {
        final double distance = stringDistance.getDistance(source, target);
        return generateResponse(source, target, distance);
    }

    /**
     *
     * @param source
     * @param target
     * @param distance
     * @return
     */
    public Response generateResponse(final String source, final String target, final double distance) {
        final Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("Source", source);
        responseMap.put("Target", target);
        responseMap.put("Edit Distance", distance);
        responseMap.put("Configurtion", configuration.toString());
        return new Response(HttpStatus.OK, "Sucess", responseMap);
    }

    private CharDistance getCharDistanceObject(final String algorithm) {
        if ("QWERTY".equals(algorithm)) {
            return qwertyDistance;
        }
        if ("QWERTZ".equals(algorithm)) {
            return qwertzDistance;
        }
        return null;
    }


}

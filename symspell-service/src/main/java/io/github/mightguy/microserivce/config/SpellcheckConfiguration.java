package io.github.mightguy.microserivce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * External configuration for the distance weights used by the spellcheck service.
 *
 * @author MighTguY
 * @since 6.7
 */
@Configuration
@ConfigurationProperties(prefix = "spellcheck")
public class SpellcheckConfiguration {

    /**
     * Creates the configuration. Spring Boot binds the properties from the environment.
     */
    public SpellcheckConfiguration() {
        // configured by Spring
    }

    private double deletionWeight;
    private double insertionWeight;
    private double replaceWeight;
    private double transpositionWeight;
    private String charDistance;

    /**
     * Returns the deletion cost.
     *
     * @return the deletion cost
     */
    public double getDeletionWeight() {
        return deletionWeight;
    }

    /**
     * Sets the deletion cost.
     *
     * @param theDeletionWeight the deletion cost
     */
    public void setDeletionWeight(final double theDeletionWeight) {
        this.deletionWeight = theDeletionWeight;
    }

    /**
     * Returns the insertion cost.
     *
     * @return the insertion cost
     */
    public double getInsertionWeight() {
        return insertionWeight;
    }

    /**
     * Sets the insertion cost.
     *
     * @param theInsertionWeight the insertion cost
     */
    public void setInsertionWeight(final double theInsertionWeight) {
        this.insertionWeight = theInsertionWeight;
    }

    /**
     * Returns the replacement cost.
     *
     * @return the replacement cost
     */
    public double getReplaceWeight() {
        return replaceWeight;
    }

    /**
     * Sets the replacement cost.
     *
     * @param theReplaceWeight the replacement cost
     */
    public void setReplaceWeight(final double theReplaceWeight) {
        this.replaceWeight = theReplaceWeight;
    }

    /**
     * Returns the transposition cost.
     *
     * @return the transposition cost
     */
    public double getTranspositionWeight() {
        return transpositionWeight;
    }

    /**
     * Sets the transposition cost.
     *
     * @param theTranspositionWeight the transposition cost
     */
    public void setTranspositionWeight(final double theTranspositionWeight) {
        this.transpositionWeight = theTranspositionWeight;
    }

    /**
     * Returns the configured per-character distance algorithm name.
     *
     * @return the algorithm name, or {@code null} if not configured
     */
    public String getCharDistance() {
        return charDistance;
    }

    /**
     * Sets the per-character distance algorithm name.
     *
     * @param theCharDistance the algorithm name, for example {@code QWERTY}
     */
    public void setCharDistance(final String theCharDistance) {
        this.charDistance = theCharDistance;
    }
}
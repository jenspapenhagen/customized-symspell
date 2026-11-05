package io.github.mightguy.symspell.solr.utils;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.response.BasicResultContext;
import org.apache.solr.response.SolrQueryResponse;

/**
 * Helpers for reading component configuration and query results.
 *
 * @author MighTguY
 * @since 6.7
 */
public final class SearchRequestUtil {

    private SearchRequestUtil() {
    }

    /**
     * Instantiates a class through the Solr resource loader.
     *
     * @param className the fully qualified class name
     * @param loader the resource loader of the core
     * @param abstractClass the expected super type
     * @param subPackages additional packages to search
     * @param args constructor arguments
     * @param <T> the expected type
     * @return the created instance, never {@code null}
     */
    public static <T> T getClassFromLoader(final String className, final SolrResourceLoader loader,
            final Class abstractClass, final String[] subPackages, final Object[] args) {
        final Object obj = loader.newInstance(className, abstractClass, subPackages, new Class[0], args);
        if (obj == null) {
            throw new FatalException("Can't load spell checker: " + className);
        }
        return (T) obj;
    }

    /**
     * Reads a typed value from a named list.
     *
     * @param namedList the configuration list
     * @param key the parameter name
     * @param def the value to return when the key is absent
     * @param <T> the expected type
     * @return the configured value or {@code def}
     */
    public static <T> T getFromNamedList(final NamedList namedList, final String key, final T def) {
        T val = (T) namedList.get(key);
        if (val == null) {
            val = def;
        }
        return val;
    }

    /**
     * Checks whether the result has at least the configured number of matches.
     *
     * @param rsp the query response
     * @param spellCheckThreshold the minimum number of matches
     * @return {@code true} if the result reaches the threshold
     */
    public static boolean resultGreaterThanThreshold(final SolrQueryResponse rsp,
            final long spellCheckThreshold) {
        return !resultLessThanThreshold(rsp, spellCheckThreshold);
    }

    /**
     * Checks whether the result has fewer matches than the configured threshold.
     *
     * @param rsp the query response
     * @param spellCheckThreshold the minimum number of matches
     * @return {@code true} if the result is below the threshold
     */
    public static boolean resultLessThanThreshold(final SolrQueryResponse rsp, final long spellCheckThreshold) {
        if (null == rsp.getResponse()) {
            return true;
        }
        return ((BasicResultContext) rsp.getResponse()).getDocList().matches() < spellCheckThreshold;
    }


}

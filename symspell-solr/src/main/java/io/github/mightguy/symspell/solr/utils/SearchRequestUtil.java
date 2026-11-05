package io.github.mightguy.symspell.solr.utils;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.response.BasicResultContext;
import org.apache.solr.response.SolrQueryResponse;

public final class SearchRequestUtil {

    private SearchRequestUtil() {
    }

    /**
     * Get Class  from class loader.
     * @param className
     * @param loader
     * @param abstractClass
     * @param subPackages
     * @param args
     * @param <T>
     * @return
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
     * Get value of  Type T from named list.
     * @param namedList
     * @param key
     * @param def
     * @param <T>
     * @return
     */
    public static <T> T getFromNamedList(final NamedList namedList, final String key, final T def) {
        T val = (T) namedList.get(key);
        if (val == null) {
            val = def;
        }
        return val;
    }

    /**
     * Check if the result greater than the spellcheck threshold.
     * @param rsp
     * @param spellCheckThreshold
     * @return
     */
    public static boolean resultGreaterThanThreshold(final SolrQueryResponse rsp,
            final long spellCheckThreshold) {
        return !resultLessThanThreshold(rsp, spellCheckThreshold);
    }

    /**
     * Check if the result lesser than the spellcheck threshold.
     * @param rsp
     * @param spellCheckThreshold
     * @return
     */
    public static boolean resultLessThanThreshold(final SolrQueryResponse rsp, final long spellCheckThreshold) {
        if (null == rsp.getResponse()) {
            return true;
        }
        return ((BasicResultContext) rsp.getResponse()).getDocList().matches() < spellCheckThreshold;
    }


}

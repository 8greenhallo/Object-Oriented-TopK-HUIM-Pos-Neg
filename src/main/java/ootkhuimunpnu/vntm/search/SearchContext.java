package ootkhuimunpnu.vntm.search;

import java.util.List;

import ootkhuimunpnu.vntm.utility.PULList;

/**
 * Carries the context for a single level of the itemset search recursion.
 *
 * <p>Groups the prefix utility list, the set of viable extensions, and the
 * current recursion depth into a single object. This avoids passing many
 * parameters through recursive calls and makes it easier to serialise search
 * state for breadth-first queues or future variant.
 *
 * @author Meg
 * @version 5.0
 */
public class SearchContext {

    /** Prefix utility list representing the current itemset X. */
    private final PULList prefix;

    /** Extension utility lists to be appended to the prefix. */
    private final List<PULList> extensions;

    /**
     * Current recursion depth (1-based: depth 1 = single items,
     * depth 2 = 2-itemsets, …).
     */
    private final int depth;

    /**
     * Constructs a SearchContext.
     *
     * @param prefix     current prefix utility list
     * @param extensions viable extension lists
     * @param depth      current depth in the search tree
     */
    public SearchContext(PULList prefix, List<PULList> extensions, int depth) {
        this.prefix     = prefix;
        this.extensions = extensions;
        this.depth      = depth;
    }

    /** @return prefix utility list */
    public PULList getPrefix() { return prefix; }

    /** @return list of extension utility lists */
    public List<PULList> getExtensions() { return extensions; }

    /** @return current search depth */
    public int getDepth() { return depth; }

    @Override
    public String toString() {
        return String.format("SearchContext{depth=%d, prefix=%s, extensions=%d}",
                depth, prefix.getItemset(), extensions.size());
    }
}
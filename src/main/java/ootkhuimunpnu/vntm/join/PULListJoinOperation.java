package ootkhuimunpnu.vntm.join;

import ootkhuimunpnu.vntm.utility.IUtilityList;
import ootkhuimunpnu.vntm.utility.PULList;

/**
 * Backwards-compatibility shim for code that previously depended on
 * {@code PULListJoinOperation} as a concrete class.
 *
 * <p>This class now delegates entirely to {@link SortedMergeJoin}, which
 * implements the {@link IJoinStrategy} interface.  New code should depend
 * on {@link IJoinStrategy} directly and receive a concrete implementation
 * (e.g., {@link SortedMergeJoin}, {@link EarlyTerminationJoin},
 * {@link ProbabilityFilteredJoin}) via constructor injection.
 *
 * <p>This shim exists only to prevent breaking call sites that were written
 * before the interface was extracted; it will be removed in a future version.
 *
 * @author Meg
 * @version 5.0
 * @deprecated Inject {@link IJoinStrategy} directly instead.
 */
@Deprecated
public class PULListJoinOperation implements IJoinStrategy {

    private final SortedMergeJoin delegate = new SortedMergeJoin();

    /** Constructs the shim (delegates to {@link SortedMergeJoin}). */
    public PULListJoinOperation() {}

    /**
     * {@inheritDoc}
     *
     * @deprecated Use {@link SortedMergeJoin} directly.
     */
    @Override
    @Deprecated
    public PULList join(IUtilityList ul1, IUtilityList ul2, double threshold) {
        return delegate.join(ul1, ul2, threshold);
    }
}
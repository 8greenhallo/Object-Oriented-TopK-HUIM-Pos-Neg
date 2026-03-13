package ootkhuimunpnu.vntm.search;

import ootkhuimunpnu.vntm.config.SearchConfig;
import ootkhuimunpnu.vntm.join.IJoinStrategy;

/**
 * Factory class responsible for instantiating the search strategy
 * based on the provided configuration.
 *
 * <p>Applying the Factory Pattern helps adhere to the Open/Closed Principle (OCP)
 * and Separation of Concerns (SoC) within the architecture.
 *
 * @author Meg
 * @version 5.0
 */
public class SearchStrategyFactory {

    /**
     * Creates and returns the corresponding ISearchStrategy instance.
     *
     * @param config       The search configuration (contains the strategy type).
     * @param joinStrategy The join strategy to be injected into the search algorithm.
     * @return An instance of ISearchStrategy (BestFirstSearch, DepthFirstSearch, or BreadthFirstSearch).
     * @throws IllegalArgumentException if the configuration is null or the strategy type is invalid.
     */
    public static ISearchStrategy createStrategy(SearchConfig config, IJoinStrategy joinStrategy) {
        if (config == null) {
            throw new IllegalArgumentException("SearchConfig cannot be null.");
        }

        // Convert to uppercase and trim to avoid case-sensitivity or whitespace issues from the config file.
        // Assuming config.getStrategyType() returns an Enum or a String.
        String strategyType = String.valueOf(config.getStrategyType()).trim().toUpperCase();

        switch (strategyType) {
            case "BEST_FIRST":
            case "BEFS":
            case "BESTFIRSTSEARCH":
                return new BestFirstSearch(joinStrategy);

            case "DFS":
            case "DEPTH_FIRST":
            case "DEPTHFIRSTSEARCH":
                return new DepthFirstSearch(joinStrategy);

            case "BFS":
            case "BREADTH_FIRST":
            case "BREADTHFIRSTSEARCH":
                return new BreadthFirstSearch(joinStrategy);

            default:
                // In a research environment, it is better to throw an exception 
                // rather than silently falling back to a default strategy. 
                // This ensures the researcher knows their config file is incorrect.
                throw new IllegalArgumentException(
                    "Search strategy not found: [" + strategyType + "]. " +
                    "Currently supported types are: BEST_FIRST, DFS, BFS."
                );
        }
    }
}
package ootkhuimunpnu.vntm.io;

import java.io.*;
import java.util.List;

import ootkhuimunpnu.vntm.db.Itemset;

/**
 * Writes mining results to CSV and plain-text output files.
 *
 * @author Meg
 * @version 5.0
 */
public class ResultWriter {

    private final String outputDirectory;

    /**
     * @param outputDirectory directory where result files will be written
     */
    public ResultWriter(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Writes results to a CSV file.
     *
     * @param filename output filename (relative to outputDirectory)
     * @param results  top-K itemsets
     * @throws IOException on write failure
     */
    public void writeCsv(String filename, List<Itemset> results) throws IOException {
        try (PrintWriter w = new PrintWriter(outputDirectory + "/" + filename)) {
            w.println("rank,items,expected_utility,existential_probability,size");
            int rank = 1;
            for (Itemset is : results) {
                w.printf("%d,\"%s\",%.6f,%.6f,%d%n",
                        rank++, is.getItems(),
                        is.getExpectedUtility(),
                        is.getExistentialProbability(),
                        is.size());
            }
        }
    }

    /**
     * Prints results to stdout in a human-readable format.
     *
     * @param results top-K itemsets
     * @param k       number of top itemsets requested
     */
    public void printToConsole(List<Itemset> results, int k) {
        System.out.println("\n=== Top-" + k + " PHUIs ===");
        int rank = 1;
        for (Itemset is : results) {
            System.out.println(is.toOutputString(rank++));
        }
        System.out.println("Total found: " + results.size());
    }
}
package org.aksw.twig.automaton.data;

import org.aksw.twig.files.FileHandler;
import org.aksw.twig.model.Twitter7ModelWrapper;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class calculates the probability of a word being succeeded by another one. Probabilities are calculated by frequency distribution.
 * The word that will be succeeded by another word will be denoted as "predecessor" from now on.
 * The word that is succeeding another word will be denoted as "successor" from now on.<br><br>
 *
 * A word following "" (the empty word) denotes the word starting a sentence.<br>
 * A word followed by "" (the empty word) denotes the word ending a sentence.<br><br>
 *
 * By invocation of {@link #alterFrequency(String, String, long)} you alter the frequency distribution of words being followed by one another.<br>
 * By invocation of {@link #getChance(String, String)} you get the transition chance between two words.<br><br>
 *
 * For example: After invocation of: <br>
 *     <pre>
 *         {@code matrix.alterFrequency("a", "b", 3);}
 *         {@code matrix.alterFrequency("a", "c", 2);}
 *     </pre>
 * {@code matrix.getChance("a", "b");} will return {@code 0.6} whereas {@code matrix.getChance("a", "c");} will return {@code 0.4};
 */
public class WordMatrix implements Serializable {

    private static final long serialVersionUID = 2104488071228760278L;

    private Map<String, MutablePair<Long, Map<String, Long>>> matrix = new HashMap<>();

    /**
     * Alters the frequency distribution: You add {@code count} more occurences of the word {@code predecessor} being followed by {@code successor}.
     * @param predecessor Predecessor to add.
     * @param successor Successor to add.
     * @param count Count to alter the frequency distribution by.
     */
    public void alterFrequency(String predecessor, String successor, long count) {
        MutablePair<Long, Map<String, Long>> mapping = matrix.computeIfAbsent(predecessor, key -> new MutablePair<>(0L, new HashMap<>()));

        mapping.setLeft(mapping.getLeft() + count);
        Map<String, Long> columns = mapping.getRight();
        Long val = columns.get(successor);
        columns.put(successor, val == null ? count : val + count);
    }

    /**
     * Adds all iterable elements as pairs of predecessors and successors to the frequency distribution. Every {@link Pair} will be processed by: {@code alterFrequency(pair.getLeft(), pair.getRight(), 1);}.
     * @param iterable Pairs of succeeding words to add to the frequency distribution.
     */
    public void putAll(Iterable<Pair<String, String>> iterable) {
        iterable.forEach(pair -> alterFrequency(pair.getLeft(), pair.getRight(), 1));
    }

    /**
     * Iterates over given {@link Model} looking for statements with {@link Twitter7ModelWrapper#TWEET_CONTENT_PROPERTY_NAME} predicate.
     * Once a sufficient statement is found all words from the literal will be added to the frequency distribution.
     * @param model Model to add statements from.
     */
    public void addModel(Model model) {
        model.listStatements().forEachRemaining(statement -> {
            if (statement.getPredicate().getLocalName().equals(Twitter7ModelWrapper.TWEET_CONTENT_PROPERTY_NAME)) {
                String tweet = statement.getObject().asLiteral().getString();
                putAll(new TweetSplitter(tweet));
            }
        });
    }

    /**
     * Merges the frequency distribution of given {@link WordMatrix} into this.
     * @param wordMatrix Matrix to merge.
     */
    public void merge(WordMatrix wordMatrix) {
        wordMatrix.matrix.entrySet().forEach(
                entry -> {
                    String predecessor = entry.getKey();
                    entry.getValue().getRight().entrySet().forEach(
                            mappedEntry -> {
                                alterFrequency(predecessor, mappedEntry.getKey(), mappedEntry.getValue());
                            }
                    );
                }
        );
    }

    /**
     * Returns the probability that {@code predecessor} will be followed by {@code successor}.
     * @param predecessor Predecessor.
     * @param successor Successor.
     * @return Chance.
     * @throws IllegalArgumentException Thrown if there is no mapping for the {@code predecessor}.
     */
    public double getChance(String predecessor, String successor) throws IllegalArgumentException {
        MutablePair<Long, Map<String, Long>> mapping = matrix.get(predecessor);
        if (mapping == null) {
            throw new IllegalArgumentException("No mapping found.");
        }

        return (double) mapping.getRight().get(successor) / (double) mapping.getLeft();
    }

    /**
     * Returns the set of all predecessors that can be queried as first argument in {@link #getChance(String, String)} or {@link #getMappings(String)}.
     * @return Set of predecessors.
     */
    public Set<String> getPredecessors() {
        return matrix.keySet();
    }

    /**
     * Get all words that can be successor to the {@code predecessor}. Those successors are mapped to their chance of succeeding.
     * @param predecessor Predecessor.
     * @return Map of successor to succeeding chance.
     */
    public Map<String, Double> getMappings(String predecessor) throws IllegalArgumentException {
        MutablePair<Long, Map<String, Long>> mapping = matrix.get(predecessor);
        if (mapping == null) {
            throw new IllegalArgumentException("No mapping found.");
        }

        long size = mapping.getLeft();
        return mapping.getRight().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> (double) entry.getValue() / (double) size
                ));
    }
}

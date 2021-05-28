package eu.cqse.breakroulette;

import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A history-aware match generator for 1:1 coffee breaks. Finds pairings of people that haven't had contact recently,
 * writes them out and stores them in a history file.
 * <p>
 * Simply run the {@link #main(String[])} method to use this.
 */
public class BreakRoulette {

    /** A text file that contains the name or email address of exactly one participant per line. */
    public static final String CANDIDATE_POOL_FILE = "pool.txt";

    /** Storage file for old matches (so we don't repeat history by mistake). */
    public static final String PREVIOUS_PAIRS_FILE = "previous-pairs.csv";

    /** Main entry point. Run this for quick results. */
    public static void main(String[] args) throws IOException {
        List<String> matchedPairs = generateMatches();
        matchedPairs.forEach(System.out::println);
        saveToFile(matchedPairs);
    }

    /**
     * Computes and returns a list of matches, where a match is a string pairing two people into a single match group.
     * Addidionally, the result will contain one group of three people iff the number of candidates (given in {@link
     * #CANDIDATE_POOL_FILE} is odd.
     */
    private static List<String> generateMatches() throws IOException {
        List<String> allParticipants = readFile(CANDIDATE_POOL_FILE);
        List<String> previousPairFileContent = readFile(PREVIOUS_PAIRS_FILE);

        String leftOverParticipant = null;
        if (allParticipants.size() % 2 != 0) {
            leftOverParticipant = findLeftOverParticipant(allParticipants, previousPairFileContent);
            // Ensure an even number of participants, so we can find a perfect match
            allParticipants.remove(leftOverParticipant);
        }
        // The history we want to consider
        List<Pair> previousPairs = readPreviousPairs(previousPairFileContent);

        // Pick the set of actual matches from the potential pairs that best respects the given history. We assume that
        // the computation happens once per week (but running this in different intervals will work as well).
        Set<Pair> matchesForCurrentWeek = findMatches(allParticipants, previousPairs);

        List<String> matchesAsStrings = new ArrayList<>();
        if (leftOverParticipant != null) {
            Pair triple = filterBestLeftOverMatch(leftOverParticipant, matchesForCurrentWeek, previousPairs);
            matchesAsStrings.add(leftOverParticipant + ", " + triple);
            matchesForCurrentWeek.remove(triple);
        }
        for (Pair pair : matchesForCurrentWeek) {
            matchesAsStrings.add(pair.toString());
        }
        return matchesAsStrings;
    }

    private static Pair filterBestLeftOverMatch(String leftOver, Set<Pair> thisWeek, List<Pair> previousPairs) {
        HashSet<Pair> bestLeftOverMatch = new HashSet<>(thisWeek);
        // Iteration order is newest first
        for (int i = previousPairs.size() - 1; i >= 0; i--) {
            if (bestLeftOverMatch.size() == 1) {
                break;
            }
            Pair pair = previousPairs.get(i);
            if (pair.contains(leftOver)) {
                String partner = pair.getPartner(leftOver);
                bestLeftOverMatch.removeIf(match -> match.contains(partner));
            }
        }
        return bestLeftOverMatch.iterator().next();
    }

    /**
     * If an odd number of participants take part this week, we need to form one group of three people. This method
     * returns a participant that hasn't recently participated in such a group, so we can put him in one later on.
     */
    private static String findLeftOverParticipant(List<String> allParticipants, List<String> previousPairFileContent) {
        // We are only interested in three-way groups ("triples")
        List<String> previousTriplesContent =
                previousPairFileContent.stream().filter(line -> line.chars().filter(c -> c == ',').count() > 1)
                        .collect(Collectors.toList());
        // Starting out, everybody could potentially become the "leftover" participant
        Set<String> leftOverCandidates = new HashSet<>(allParticipants);
        // This explodes all triples into pairs of two
        List<Pair> previousTriples = readPreviousPairs(previousTriplesContent);
        // Then we linearize them into single participants, ordered by recency of their taking part in a triple
        List<String> linearizedTriples =
                previousTriples.stream().map(pair -> List.of(pair.left, pair.right)).flatMap(List::stream)
                        .collect(Collectors.toList());
        // From the pool of potential leftovers, throw out people who recently participated in a triple until only one
        // is left (or we run out of history to consider)
        for (int i = linearizedTriples.size() - 1; i >= 0; i--) {
            if (leftOverCandidates.size() == 1) {
                break;
            }
            leftOverCandidates.remove(linearizedTriples.get(i));
        }
        return leftOverCandidates.iterator().next();
    }

    private static void saveToFile(List<String> matches) throws IOException {
        List<String> content = Files.readAllLines(Path.of(PREVIOUS_PAIRS_FILE));
        content.add("");
        content.addAll(matches);
        Files.write(Path.of(PREVIOUS_PAIRS_FILE), content);
    }

    private static List<Pair> readPreviousPairs(List<String> previousPairLines) {
        List<Pair> previousPairs = new ArrayList<>();
        for (String previousPairLine : previousPairLines) {
            String[] split = previousPairLine.split(",");
            if (split.length < 2) {
                throw new IllegalArgumentException(
                        "Encountered unexpected line (should only contain comma-separated identifiers): " +
                                previousPairLine);
            }
            previousPairs.add(new Pair(split[0].trim(), split[1].trim()));
            // Explode groups with three participants into pairs of two
            if (split.length > 2) {
                previousPairs.add(new Pair(split[0].trim(), split[2].trim()));
                previousPairs.add(new Pair(split[1].trim(), split[2].trim()));
            }
        }
        return previousPairs;
    }

    /**
     * Returns the set of all potential pairs between all given participants (i.e., all two-combinations of the
     * participant list).
     */
    private static Set<Pair> computePotentialPairs(List<String> allParticipants) {
        Set<Set<String>> combinationsOfTwo = Sets.combinations(new HashSet<>(allParticipants), 2);
        Set<Pair> potentialPairs = new HashSet<>();
        for (Set<String> potentialPair : combinationsOfTwo) {
            potentialPairs.add(new Pair(potentialPair));
        }
        return potentialPairs;
    }

    /**
     * Given the set of participants and the history of previous matches, computes and returns the matches that are
     * fairest with regard to the history (i.e., tries to avoid pairing people who were recently paired).
     */
    private static Set<Pair> findMatches(List<String> allParticipants, List<Pair> previousPairs) {
        // All possible pairings between all of the participants
        Set<Pair> potentialPairs = computePotentialPairs(allParticipants);
        // How many meetings a week may contain
        int meetingsInAWeek = (allParticipants.size() / 2) + 2;
        int cutoffIndex = computePreviousPairsCutoffIndex(allParticipants, previousPairs, meetingsInAWeek);
        previousPairs = previousPairs.subList(cutoffIndex, previousPairs.size());
        // We shall now try to find a good set of matches. If we don't find one, cut off some of the older history
        // and try again until it works out.
        Optional<Set<Pair>> foundMatches;
        while (previousPairs.size() - 1 > meetingsInAWeek) {
            HashSet<Pair> potentialPairsWithExcludedHistory = new HashSet<>(potentialPairs);
            // This is where the magic happens: Exclude all pairs we have already seen in the considered chunk of
            // history from the list of possible pairs, so the only remaining possible matches are guaranteed fresh
            potentialPairsWithExcludedHistory.removeAll(previousPairs);
            // This performs the actual work of finding matches, up until now we have just done some
            // book-keeping of how much history we want to consider
            foundMatches = findMatches(potentialPairsWithExcludedHistory, new HashSet<>());
            if (foundMatches.isPresent()) {
                return foundMatches.get();
            }
            previousPairs = previousPairs.subList(meetingsInAWeek, previousPairs.size());
        }
        throw new IllegalStateException("Could not find any matches!");
    }

    /** Returns how much history to consider in the following computations. */
    private static int computePreviousPairsCutoffIndex(List<String> allParticipants, List<Pair> previousPairs,
                                                       int meetingsInAWeek) {
        // How many weeks we can hope to go without repeating any meeting
        int maxWeeksWithoutRepeatedMeetings = allParticipants.size() - 1;
        // The "+ 2" below accounts for additional three-way meetings that are modelled as three distinct two-way meetings
        int historyEntriesToConsider = (meetingsInAWeek + 2) * maxWeeksWithoutRepeatedMeetings;
        return Math.max(0, previousPairs.size() - historyEntriesToConsider);
    }

    /**
     * Given a set of potential pairs that still need to be matched up and a set of pairs that have already been
     * matched, try to add matches to the list of found matches until we no longer have potentials to distribute.
     * <p>
     * Performs backtracking if it turns out that we painted ourselves into a corner by removing too many potential
     * matches.
     */
    private static Optional<Set<Pair>> findMatches(Set<Pair> potentialPairs, Set<Pair> foundSoFar) {
        if (potentialPairs.isEmpty()) {
            return Optional.of(foundSoFar);
        }
        for (Pair potentialPair : potentialPairs) {
            Set<Pair> remainingPotentialPairs = removeParticipants(potentialPair, potentialPairs);
            // Check if we arrived at some impossible to continue combination by mistake
            if (!isDifferenceExactlyTwoParticipants(remainingPotentialPairs, potentialPairs)) {
                continue;
            }
            HashSet<Pair> added = new HashSet<>(foundSoFar);
            added.add(potentialPair);
            // Find matches recursively
            Optional<Set<Pair>> foundMatch = findMatches(remainingPotentialPairs, added);
            if (foundMatch.isPresent()) {
                return foundMatch;
            }
        }
        return Optional.empty();
    }

    /**
     * Check if the difference between two sets of pairs is exactly two participants (i.e., one pair). We need this in
     * sitations where the only remaining potential matches are [(A, B), (A, C), (B, C), (B, D)] and we try to match (B,
     * C) by mistake. Since both B and C are now no longer available for matching, we also have to remove (A, B) and (A,
     * C) from the potential matches, which means that A no longer exists in the list of potential matches. In that
     * case, we need to backtrack and match (A, C) and (B, D).
     */
    private static boolean isDifferenceExactlyTwoParticipants(Set<Pair> newPairs, Set<Pair> oldPairs) {
        Set<String> newParticipants = getParticipants(newPairs);
        Set<String> oldParticipants = getParticipants(oldPairs);
        return Math.abs(newParticipants.size() - oldParticipants.size()) == 2;
    }

    /** Returns a new set of potential pairs, where no potential pair contains any participant of the pair to remove. */
    private static Set<Pair> removeParticipants(Pair pairToRemove, Set<Pair> potentialPairs) {
        HashSet<Pair> copy = new HashSet<>(potentialPairs);
        copy.remove(pairToRemove);
        copy.removeIf(entry -> containsParticipant(entry, pairToRemove));
        return copy;
    }

    /** Checks if a given reference pair contains any participant of the candidate pair. */
    private static boolean containsParticipant(Pair candidate, Pair reference) {
        return reference.contains(candidate.left) || reference.contains(candidate.right);
    }

    /** Returns a set of all participants included in the given set of potential pairs. */
    private static Set<String> getParticipants(Set<Pair> potentialPairs) {
        Set<String> participants = new HashSet<>();
        for (Pair pair : potentialPairs) {
            participants.add(pair.left);
            participants.add(pair.right);
        }
        return participants;
    }

    /**
     * Reads the given file and returns its contents as as list of lines, trimming away excess whitespace and performing
     * normalization by converting everything to lower case.
     */
    private static List<String> readFile(String filename) throws IOException {
        return Files.readAllLines(Path.of(filename)).stream().map(String::trim).filter(line -> !line.isBlank())
                .map(String::toLowerCase).collect(Collectors.toList());
    }
}

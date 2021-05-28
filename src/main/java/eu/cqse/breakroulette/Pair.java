package eu.cqse.breakroulette;

import java.util.Set;

/**
 * Simple data class modelling a pair of participants where order is irrelevant, i.e. Pair(A, B) is the same as Pair(B,
 * A).
 */
public class Pair {

    public final String left;
    public final String right;

    public Pair(String left, String right) {
        if (left.compareTo(right) < 0) {
            this.left = left;
            this.right = right;
        } else {
            this.left = right;
            this.right = left;
        }
    }

    /** Converts the given set containing exactly two strings to a pair containing these strings as participants. */
    public Pair(Set<String> setOfTwo) {
        this(setOfTwo.toArray(new String[0])[0], setOfTwo.toArray(new String[0])[1]);
    }

    /** Returns whether this set contains the given participant. */
    public boolean contains(String participant) {
        return participant.equals(left) || participant.equals(right);
    }

    @Override
    public String toString() {
        return left + ", " + right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pair pair = (Pair) o;
        return left.equals(pair.left) && right.equals(pair.right);
    }

    @Override
    public int hashCode() {
        int result = left.hashCode();
        result = 31 * result + right.hashCode();
        return result;
    }

    public String getPartner(String reference) {
        if (left.equals(reference)) {
            return right;
        }
        return left;
    }
}

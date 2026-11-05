package it.infog.psiche.spelling;

/**
 * How many letters must change to turn one word into another: a letter added, removed or replaced, or two letters
 * side by side swapped, as typing slips do. Only small distances matter for suggestions, so a pair further apart than
 * asked is told as such without working out by how much.
 */
final class EditDistance {

    private EditDistance() {
    }

    /** The edits between {@code a} and {@code b}, or {@code max + 1} if they need more than {@code max}. */
    static int between(String a, String b, int max) {

        if (Math.abs(a.length() - b.length()) > max)
            return max + 1;

        //the distances between a's first i letters and b's first j, a row at a time, keeping the two rows before
        int[] twoBefore = new int[b.length() + 1];
        int[] before = new int[b.length() + 1];
        int[] row = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++)
            before[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            row[0] = i;
            int smallest = row[0];
            for (int j = 1; j <= b.length(); j++) {
                int replace = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                row[j] = Math.min(Math.min(before[j] + 1, row[j - 1] + 1), before[j - 1] + replace);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    row[j] = Math.min(row[j], twoBefore[j - 2] + 1);
                smallest = Math.min(smallest, row[j]);
            }
            // Every way on goes through this row, so none can come back under the limit.
            if (smallest > max)
                return max + 1;

            int[] oldest = twoBefore;
            twoBefore = before;
            before = row;
            row = oldest;
        }

        return Math.min(before[b.length()], max + 1);
    }
}

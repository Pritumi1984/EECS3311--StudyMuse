package com.studymuse.domain;

/** How well the student remembered a flashcard (F06). Quality is the SM-2 grade 0-5. */
public enum Rating {
    AGAIN(1, 0.0), HARD(3, 0.5), GOOD(4, 1.0), EASY(5, 1.0);

    private final int quality;
    private final double correctness;

    Rating(int quality, double correctness) {
        this.quality = quality;
        this.correctness = correctness;
    }

    /** SM-2 quality grade used by the scheduler. */
    public int getQuality() {
        return quality;
    }

    /** 0 = forgot, 0.5 = struggled, 1 = remembered; used for topic accuracy (F09). */
    public double getCorrectness() {
        return correctness;
    }

    /** Accepts 1-4 (Again, Hard, Good, Easy) or the name. */
    public static Rating parse(String text) {
        String t = text == null ? "" : text.trim().toUpperCase();
        switch (t) {
            case "1": return AGAIN;
            case "2": return HARD;
            case "3": return GOOD;
            case "4": return EASY;
            default:
                try {
                    return valueOf(t);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Rating must be 1-4 or again/hard/good/easy (got '" + text + "').");
                }
        }
    }
}

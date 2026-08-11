package com.Tbence132545.Melodigram.model;

/**
 * Maps MIDI pitches onto staff positions and durations onto note values.
 *
 * <p>Positions are expressed as <em>diatonic steps</em>: one step per line-or-space, so
 * consecutive steps alternate between sitting on a line and sitting in a space. Middle C is
 * step 28, which places it one ledger line below the treble staff and one above the bass —
 * exactly where it belongs on a grand staff.
 */
public final class StaffNotation {

    /** Step of middle C (C4). */
    public static final int MIDDLE_C_STEP = 28;
    /** Bottom line of the treble staff, E4. */
    public static final int TREBLE_BOTTOM_STEP = 30;
    /** Bottom line of the bass staff, G2. */
    public static final int BASS_BOTTOM_STEP = 18;
    /** A staff spans four gaps between its five lines. */
    public static final int STAFF_SPAN_STEPS = 8;

    /** Which letter each pitch class is spelled with, spelling black keys as sharps. */
    private static final int[] DIATONIC_INDEX = {0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6};
    private static final boolean[] NEEDS_SHARP = {
            false, true, false, true, false, false, true, false, true, false, true, false
    };

    public enum NoteValue {
        WHOLE(false, false),
        HALF(true, false),
        QUARTER(true, true),
        EIGHTH(true, true),
        SIXTEENTH(true, true);

        private final boolean stemmed;
        private final boolean filled;

        NoteValue(boolean stemmed, boolean filled) {
            this.stemmed = stemmed;
            this.filled = filled;
        }

        public boolean hasStem() {
            return stemmed;
        }

        public boolean isFilled() {
            return filled;
        }

        /** Number of flags on the stem; beaming is not attempted. */
        public int flagCount() {
            return switch (this) {
                case EIGHTH -> 1;
                case SIXTEENTH -> 2;
                default -> 0;
            };
        }
    }

    private StaffNotation() {
    }

    public static int diatonicStep(int midiNote) {
        int octave = Math.floorDiv(midiNote, 12) - 1;
        return octave * 7 + DIATONIC_INDEX[Math.floorMod(midiNote, 12)];
    }

    public static boolean needsSharp(int midiNote) {
        return NEEDS_SHARP[Math.floorMod(midiNote, 12)];
    }

    /**
     * Classifies a sounded duration against the beat. Played durations fall short of their
     * written value because keys are released early, so the thresholds sit well below the
     * nominal ratios rather than at them.
     */
    public static NoteValue noteValue(long durationMillis, double quarterMillis) {
        if (quarterMillis <= 0) {
            return NoteValue.QUARTER;
        }
        double beats = durationMillis / quarterMillis;
        if (beats >= 3.2) return NoteValue.WHOLE;
        if (beats >= 1.6) return NoteValue.HALF;
        if (beats >= 0.7) return NoteValue.QUARTER;
        if (beats >= 0.32) return NoteValue.EIGHTH;
        return NoteValue.SIXTEENTH;
    }

    /** Stems point away from the middle line so they stay inside the staff. */
    public static boolean stemPointsUp(int step, int staffBottomStep) {
        return step < staffBottomStep + STAFF_SPAN_STEPS / 2;
    }

    /**
     * Works out which noteheads of a chord must be pushed sideways to stay readable.
     *
     * <p>Two notes one step apart overlap by half a notehead and merge into a single blob, hiding
     * which line each is sitting on. Engraving moves the upper of the pair to the other side of
     * the stem. A displaced note does not itself displace the next one, so a run of adjacent
     * notes zig-zags instead of marching steadily off to the side.
     *
     * @param ascendingSteps the chord's staff positions, lowest first
     * @return one flag per note, in the same order
     */
    public static boolean[] displacedNoteheads(int[] ascendingSteps) {
        boolean[] displaced = new boolean[ascendingSteps.length];
        for (int i = 1; i < ascendingSteps.length; i++) {
            displaced[i] = (ascendingSteps[i] - ascendingSteps[i - 1] == 1) && !displaced[i - 1];
        }
        return displaced;
    }
}

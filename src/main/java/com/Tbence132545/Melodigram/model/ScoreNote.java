package com.Tbence132545.Melodigram.model;

/**
 * One sounded note of the loaded piece.
 *
 * <p>The falling-note view and the sheet view share these instances, so assigning a hand in one
 * is immediately reflected in the other. Confined to the event dispatch thread.
 */
public class ScoreNote {

    private final int midiNote;
    private final long onMillis;
    private final long offMillis;
    private final boolean blackKey;
    private Hand hand;

    public ScoreNote(int midiNote, long onMillis, long offMillis, boolean blackKey) {
        this.midiNote = midiNote;
        this.onMillis = onMillis;
        this.offMillis = offMillis;
        this.blackKey = blackKey;
    }

    public int midiNote() {
        return midiNote;
    }

    public long onMillis() {
        return onMillis;
    }

    public long offMillis() {
        return offMillis;
    }

    public boolean isBlackKey() {
        return blackKey;
    }

    /** The assigned hand, or null when the piece has no assignment for this note. */
    public Hand hand() {
        return hand;
    }

    public void setHand(Hand hand) {
        this.hand = hand;
    }

    public boolean isSoundingAt(long millis) {
        return millis >= onMillis && millis < offMillis;
    }
}

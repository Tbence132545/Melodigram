package com.Tbence132545.Melodigram.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandSplitterTest {

    @Test
    void splitsAWideChordBetweenTheHands() {
        // A low octave under a high triad is beyond any one hand's reach.
        List<ScoreNote> notes = chord(0, 36, 48, 72, 76, 79);
        HandSplitter.assignHands(notes);

        assertEquals(Hand.LEFT, handOf(notes, 36));
        assertEquals(Hand.LEFT, handOf(notes, 48));
        assertEquals(Hand.RIGHT, handOf(notes, 72));
        assertEquals(Hand.RIGHT, handOf(notes, 79));
    }

    @Test
    void keepsAReachableChordInOneHand() {
        // A close triad low down belongs to the left hand alone.
        List<ScoreNote> notes = chord(0, 45, 48, 52);
        HandSplitter.assignHands(notes);

        for (ScoreNote note : notes) {
            assertEquals(Hand.LEFT, note.hand());
        }
    }

    @Test
    void keepsBothHandsWithinReachWhereThatIsPossible() {
        List<ScoreNote> notes = chord(0, 40, 45, 52, 67, 72, 79);
        HandSplitter.assignHands(notes);

        assertTrue(spanOf(notes, Hand.LEFT) <= 14, "left hand span");
        assertTrue(spanOf(notes, Hand.RIGHT) <= 14, "right hand span");
    }

    @Test
    void stillSharesOutAChordNoPairOfHandsCouldHold() {
        // Six notes across five octaves cannot be held by two hands at all — a pianist rolls it.
        // Reach is therefore a preference, not a guarantee, and the notes must still be assigned.
        List<ScoreNote> notes = chord(0, 28, 40, 55, 67, 79, 91);
        HandSplitter.assignHands(notes);

        assertTrue(notes.stream().anyMatch(n -> n.hand() == Hand.LEFT), "left hand used");
        assertTrue(notes.stream().anyMatch(n -> n.hand() == Hand.RIGHT), "right hand used");
        assertTrue(notes.stream().allMatch(n -> n.hand() != null));
    }

    @Test
    void theLowerNotesAlwaysGoToTheLeftHand() {
        List<ScoreNote> notes = chord(0, 30, 34, 70, 74);
        HandSplitter.assignHands(notes);

        int highestLeft = notes.stream().filter(n -> n.hand() == Hand.LEFT)
                .mapToInt(ScoreNote::midiNote).max().orElse(Integer.MIN_VALUE);
        int lowestRight = notes.stream().filter(n -> n.hand() == Hand.RIGHT)
                .mapToInt(ScoreNote::midiNote).min().orElse(Integer.MAX_VALUE);
        assertTrue(highestLeft < lowestRight, "the hands must not cross");
    }

    @Test
    void followsAMelodyThatMovesIntoTheOtherRegister() {
        // A right-hand line descending well below middle C, with no left hand playing, stays in
        // one hand rather than flipping when it crosses an arbitrary pitch.
        List<ScoreNote> notes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            notes.add(new ScoreNote(74 - i, i * 400L, i * 400L + 300, false));
        }
        HandSplitter.assignHands(notes);

        long right = notes.stream().filter(n -> n.hand() == Hand.RIGHT).count();
        assertTrue(right >= 9, "expected the line to stay in the right hand, got " + right + "/12");
    }

    @Test
    void assignsEveryNote() {
        List<ScoreNote> notes = chord(0, 40, 52, 64, 76);
        HandSplitter.assignHands(notes);
        assertTrue(notes.stream().allMatch(note -> note.hand() != null));
    }

    private static List<ScoreNote> chord(long onset, int... midiNotes) {
        List<ScoreNote> notes = new ArrayList<>();
        for (int midiNote : midiNotes) {
            notes.add(new ScoreNote(midiNote, onset, onset + 500, false));
        }
        return notes;
    }

    private static Hand handOf(List<ScoreNote> notes, int midiNote) {
        return notes.stream().filter(n -> n.midiNote() == midiNote).findFirst().orElseThrow().hand();
    }

    private static int spanOf(List<ScoreNote> notes, Hand hand) {
        List<Integer> pitches = notes.stream().filter(n -> n.hand() == hand)
                .map(ScoreNote::midiNote).sorted().toList();
        return pitches.isEmpty() ? 0 : pitches.get(pitches.size() - 1) - pitches.get(0);
    }
}

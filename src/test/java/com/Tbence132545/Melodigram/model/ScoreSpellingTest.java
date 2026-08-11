package com.Tbence132545.Melodigram.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreSpellingTest {

    private static final int F_SHARP_4 = 66;
    private static final int F_NATURAL_4 = 65;
    private static final int C4 = 60;
    private static final List<Long> TWO_SECOND_MEASURES = List.of(0L, 2000L, 4000L);

    @Test
    void notesInTheKeySignatureNeedNoAccidental() {
        // D major already sharpens every F, so an F sharp is printed bare.
        List<ScoreSpelling.SpelledNote> spelled = ScoreSpelling.spell(
                List.of(note(F_SHARP_4, 0)), new KeySignature(2), TWO_SECOND_MEASURES);

        assertFalse(spelled.get(0).drawAccidental());
    }

    @Test
    void notesOutsideTheKeySignatureDoNeedOne() {
        // In D major an F natural contradicts the signature, so it must be marked.
        List<ScoreSpelling.SpelledNote> spelled = ScoreSpelling.spell(
                List.of(note(F_NATURAL_4, 0)), new KeySignature(2), TWO_SECOND_MEASURES);

        assertTrue(spelled.get(0).drawAccidental());
        assertEquals(0, spelled.get(0).accidental(), "printed as a natural");
    }

    @Test
    void anAccidentalHoldsForTheRestOfItsMeasure() {
        List<ScoreSpelling.SpelledNote> spelled = ScoreSpelling.spell(
                List.of(note(F_SHARP_4, 100), note(F_SHARP_4, 500), note(F_SHARP_4, 900)),
                new KeySignature(0), TWO_SECOND_MEASURES);

        assertTrue(spelled.get(0).drawAccidental(), "first one is marked");
        assertFalse(spelled.get(1).drawAccidental(), "repeats within the measure are not");
        assertFalse(spelled.get(2).drawAccidental());
    }

    @Test
    void aBarlineCancelsAccidentalsBackToTheKey() {
        List<ScoreSpelling.SpelledNote> spelled = ScoreSpelling.spell(
                List.of(note(F_SHARP_4, 100), note(F_SHARP_4, 2100)),
                new KeySignature(0), TWO_SECOND_MEASURES);

        assertTrue(spelled.get(0).drawAccidental());
        assertTrue(spelled.get(1).drawAccidental(), "the next measure starts clean");
    }

    @Test
    void returningToTheKeyWithinAMeasureIsMarkedNatural() {
        List<ScoreSpelling.SpelledNote> spelled = ScoreSpelling.spell(
                List.of(note(F_SHARP_4, 100), note(F_NATURAL_4, 500)),
                new KeySignature(0), TWO_SECOND_MEASURES);

        assertTrue(spelled.get(0).drawAccidental());
        assertTrue(spelled.get(1).drawAccidental(), "cancelling the sharp needs a natural");
        assertEquals(0, spelled.get(1).accidental());
    }

    @Test
    void theSamePitchInAnotherOctaveIsMarkedSeparately() {
        List<ScoreSpelling.SpelledNote> spelled = ScoreSpelling.spell(
                List.of(note(F_SHARP_4, 100), note(F_SHARP_4 + 12, 500)),
                new KeySignature(0), TWO_SECOND_MEASURES);

        assertTrue(spelled.get(0).drawAccidental());
        assertTrue(spelled.get(1).drawAccidental(), "an accidental does not carry across octaves");
    }

    @Test
    void naturalNotesInAPlainKeyAreNeverMarked() {
        List<ScoreSpelling.SpelledNote> spelled = ScoreSpelling.spell(
                List.of(note(C4, 0), note(C4, 500)), new KeySignature(0), TWO_SECOND_MEASURES);

        assertFalse(spelled.get(0).drawAccidental());
        assertFalse(spelled.get(1).drawAccidental());
    }

    private static ScoreNote note(int midiNote, long onMillis) {
        return new ScoreNote(midiNote, onMillis, onMillis + 200, false);
    }
}

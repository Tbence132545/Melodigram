package com.Tbence132545.Melodigram.model;

import com.Tbence132545.Melodigram.model.StaffNotation.NoteValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffNotationTest {

    private static final int MIDDLE_C = 60;
    private static final int E4 = 64;   // bottom line of the treble staff
    private static final int F5 = 77;   // top line of the treble staff
    private static final int G2 = 43;   // bottom line of the bass staff
    private static final int A3 = 57;   // top line of the bass staff

    @Test
    void placesStaffReferencePitchesOnTheirLines() {
        assertEquals(StaffNotation.MIDDLE_C_STEP, StaffNotation.diatonicStep(MIDDLE_C));
        assertEquals(StaffNotation.TREBLE_BOTTOM_STEP, StaffNotation.diatonicStep(E4));
        assertEquals(StaffNotation.BASS_BOTTOM_STEP, StaffNotation.diatonicStep(G2));
    }

    @Test
    void staffSpansExactlyEightStepsBetweenOuterLines() {
        assertEquals(StaffNotation.TREBLE_BOTTOM_STEP + StaffNotation.STAFF_SPAN_STEPS,
                StaffNotation.diatonicStep(F5));
        assertEquals(StaffNotation.BASS_BOTTOM_STEP + StaffNotation.STAFF_SPAN_STEPS,
                StaffNotation.diatonicStep(A3));
    }

    @Test
    void middleCSitsOneLedgerLineOutsideEitherStaff() {
        int middleC = StaffNotation.diatonicStep(MIDDLE_C);
        assertEquals(StaffNotation.TREBLE_BOTTOM_STEP - 2, middleC, "one ledger below the treble staff");
        assertEquals(StaffNotation.BASS_BOTTOM_STEP + StaffNotation.STAFF_SPAN_STEPS + 2, middleC,
                "one ledger above the bass staff");
    }

    @Test
    void sharpsShareTheStepOfTheNaturalBelowThem() {
        assertEquals(StaffNotation.diatonicStep(MIDDLE_C), StaffNotation.diatonicStep(MIDDLE_C + 1));
        assertFalse(StaffNotation.needsSharp(MIDDLE_C));
        assertTrue(StaffNotation.needsSharp(MIDDLE_C + 1));
    }

    @Test
    void eachOctaveIsSevenSteps() {
        assertEquals(StaffNotation.diatonicStep(MIDDLE_C) + 7, StaffNotation.diatonicStep(MIDDLE_C + 12));
        assertEquals(StaffNotation.diatonicStep(MIDDLE_C) - 7, StaffNotation.diatonicStep(MIDDLE_C - 12));
    }

    @Test
    void classifiesDurationsAgainstTheBeat() {
        double quarter = 500;
        assertEquals(NoteValue.WHOLE, StaffNotation.noteValue(2000, quarter));
        assertEquals(NoteValue.HALF, StaffNotation.noteValue(1000, quarter));
        assertEquals(NoteValue.QUARTER, StaffNotation.noteValue(500, quarter));
        assertEquals(NoteValue.EIGHTH, StaffNotation.noteValue(250, quarter));
        assertEquals(NoteValue.SIXTEENTH, StaffNotation.noteValue(125, quarter));
    }

    @Test
    void toleratesNotesReleasedEarly() {
        double quarter = 500;
        // A crotchet held for 80% of its value must still be written as a crotchet.
        assertEquals(NoteValue.QUARTER, StaffNotation.noteValue(400, quarter));
        assertEquals(NoteValue.HALF, StaffNotation.noteValue(850, quarter));
    }

    @Test
    void onlyStemlessValueIsTheWholeNote() {
        assertFalse(NoteValue.WHOLE.hasStem());
        assertFalse(NoteValue.WHOLE.isFilled());
        assertTrue(NoteValue.HALF.hasStem());
        assertFalse(NoteValue.HALF.isFilled());
        assertTrue(NoteValue.QUARTER.isFilled());
        assertEquals(0, NoteValue.QUARTER.flagCount());
        assertEquals(1, NoteValue.EIGHTH.flagCount());
        assertEquals(2, NoteValue.SIXTEENTH.flagCount());
    }

    @Test
    void chordsSpacedByThirdsNeedNoDisplacement() {
        // Notes two steps apart sit line-space-line and never overlap.
        assertArrayEquals(new boolean[]{false, false, false},
                StaffNotation.displacedNoteheads(new int[]{30, 32, 34}));
    }

    @Test
    void aSecondPushesTheUpperNoteAside() {
        assertArrayEquals(new boolean[]{false, true},
                StaffNotation.displacedNoteheads(new int[]{30, 31}));
    }

    @Test
    void aRunOfAdjacentNotesZigZagsRatherThanDrifting() {
        // Every other note is displaced, so the cluster stays within one notehead of the stem.
        assertArrayEquals(new boolean[]{false, true, false, true},
                StaffNotation.displacedNoteheads(new int[]{30, 31, 32, 33}));
    }

    @Test
    void displacementResetsAfterAGap() {
        assertArrayEquals(new boolean[]{false, true, false, false},
                StaffNotation.displacedNoteheads(new int[]{30, 31, 34, 36}));
    }

    @Test
    void aSingleNoteIsNeverDisplaced() {
        assertArrayEquals(new boolean[]{false}, StaffNotation.displacedNoteheads(new int[]{30}));
        assertArrayEquals(new boolean[]{}, StaffNotation.displacedNoteheads(new int[]{}));
    }

    @Test
    void stemsPointAwayFromTheMiddleLine() {
        int bottom = StaffNotation.TREBLE_BOTTOM_STEP;
        assertTrue(StaffNotation.stemPointsUp(bottom, bottom), "low notes get an upward stem");
        assertFalse(StaffNotation.stemPointsUp(bottom + StaffNotation.STAFF_SPAN_STEPS, bottom),
                "high notes get a downward stem");
    }
}

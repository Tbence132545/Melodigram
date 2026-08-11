package com.Tbence132545.Melodigram.model;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeySignatureTest {

    private static final int KEY_SIGNATURE_META = 0x59;
    private static final int C4 = 60;
    private static final int A_SHARP_4 = 70; // also B flat 4
    private static final int F_SHARP_4 = 66;

    @Test
    void spellsBlackKeysAsFlatsInAFlatKey() {
        KeySignature twoFlats = new KeySignature(-2); // B flat major
        KeySignature.Spelled spelled = twoFlats.spell(A_SHARP_4);

        // B flat is written on the B line and carries the key's flat, not a sharp on the A line.
        assertEquals(StaffNotation.diatonicStep(71), spelled.step(), "written as a B, not an A");
        assertEquals(-1, spelled.alteration());
    }

    @Test
    void spellsBlackKeysAsSharpsInASharpKey() {
        KeySignature twoSharps = new KeySignature(2); // D major
        KeySignature.Spelled spelled = twoSharps.spell(F_SHARP_4);

        assertEquals(StaffNotation.diatonicStep(65), spelled.step(), "written as an F");
        assertEquals(1, spelled.alteration());
    }

    @Test
    void naturalNotesKeepTheirOwnStepAndNoAlteration() {
        KeySignature cMajor = new KeySignature(0);
        KeySignature.Spelled spelled = cMajor.spell(C4);

        assertEquals(StaffNotation.MIDDLE_C_STEP, spelled.step());
        assertEquals(0, spelled.alteration());
    }

    @Test
    void notesBelongingToTheKeyCarryTheKeysOwnAlteration() {
        KeySignature fiveSharps = new KeySignature(5); // B major: F C G D A sharp
        // Every one of these is in the key, so each is spelled with the key's sharp and will
        // need no printed accidental.
        for (int midiNote : new int[]{66, 61, 68, 63, 70}) {
            assertEquals(1, fiveSharps.spell(midiNote).alteration(),
                    "pitch " + midiNote + " belongs to B major");
        }
    }

    @Test
    void keepsTheSpelledOctaveWhenTheLetterCrossesTheOctaveBoundary() {
        KeySignature sevenSharps = new KeySignature(7); // C sharp major, includes B sharp
        // MIDI 60 sounds as C4 but is written as B sharp 3, on the B line below middle C.
        KeySignature.Spelled spelled = sevenSharps.spell(60);
        assertEquals(1, spelled.alteration());
        assertEquals(StaffNotation.MIDDLE_C_STEP - 1, spelled.step());
    }

    @Test
    void readsTheDeclaredKeySignature() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track track = sequence.createTrack();
        track.add(keySignatureEvent(-3));
        track.add(noteOn(C4));

        assertEquals(-3, KeySignature.of(sequence).accidentals());
        assertTrue(KeySignature.of(sequence).usesFlats());
    }

    @Test
    void infersTheKeyFromThePitchesWhenNoneIsDeclared() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track track = sequence.createTrack();
        // A D major scale: D E F# G A B C#
        for (int midiNote : new int[]{62, 64, 66, 67, 69, 71, 73, 74}) {
            track.add(noteOn(midiNote));
        }

        assertEquals(2, KeySignature.of(sequence).accidentals());
    }

    @Test
    void prefersTheSimplerSignatureWhenSeveralFitEqually() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track track = sequence.createTrack();
        for (int midiNote : new int[]{60, 62, 64, 65, 67, 69, 71}) { // C major scale
            track.add(noteOn(midiNote));
        }

        assertEquals(0, KeySignature.of(sequence).accidentals());
    }

    @Test
    void writesKeySignatureAccidentalsOnTheConventionalLines() {
        KeySignature oneSharp = new KeySignature(1);
        // The single sharp of G major sits on the top line of the treble staff, F5.
        assertEquals(StaffNotation.diatonicStep(77), oneSharp.trebleSymbolSteps()[0]);
        // And two octaves lower on the bass staff, F3.
        assertEquals(StaffNotation.diatonicStep(53), oneSharp.bassSymbolSteps()[0]);

        KeySignature oneFlat = new KeySignature(-1);
        // The single flat of F major sits on the middle line of the treble staff, B4.
        assertEquals(StaffNotation.diatonicStep(71), oneFlat.trebleSymbolSteps()[0]);
    }

    private static MidiEvent noteOn(int midiNote) throws InvalidMidiDataException {
        return new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, midiNote, 100), 0);
    }

    private static MidiEvent keySignatureEvent(int accidentals) throws InvalidMidiDataException {
        byte[] data = {(byte) accidentals, 0};
        return new MidiEvent(new MetaMessage(KEY_SIGNATURE_META, data, data.length), 0);
    }
}

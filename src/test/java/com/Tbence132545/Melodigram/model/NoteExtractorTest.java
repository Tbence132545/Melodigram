package com.Tbence132545.Melodigram.model;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteExtractorTest {

    private static final int TICKS_PER_QUARTER = 480;
    private static final int MIDDLE_C = 60;
    private static final int E_ABOVE_MIDDLE_C = 64;

    @Test
    void pairsEachNoteOnWithItsNoteOff() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(noteOn(0, MIDDLE_C, 100, 0));
        track.add(noteOff(0, MIDDLE_C, TICKS_PER_QUARTER));

        List<NoteExtractor.Note> notes = NoteExtractor.extractNotes(sequence);

        assertEquals(List.of(new NoteExtractor.Note(MIDDLE_C, 0, 500)), notes);
    }

    @Test
    void treatsZeroVelocityNoteOnAsARelease() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(noteOn(0, MIDDLE_C, 100, 0));
        track.add(noteOn(0, MIDDLE_C, 0, TICKS_PER_QUARTER));

        List<NoteExtractor.Note> notes = NoteExtractor.extractNotes(sequence);

        assertEquals(List.of(new NoteExtractor.Note(MIDDLE_C, 0, 500)), notes);
    }

    @Test
    void keepsChannelsSeparateWhenTheSamePitchOverlaps() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        // Channel 0 holds middle C for a whole note while channel 1 plays it as a quarter.
        track.add(noteOn(0, MIDDLE_C, 100, 0));
        track.add(noteOn(1, MIDDLE_C, 100, TICKS_PER_QUARTER));
        track.add(noteOff(1, MIDDLE_C, 2 * TICKS_PER_QUARTER));
        track.add(noteOff(0, MIDDLE_C, 4 * TICKS_PER_QUARTER));

        List<NoteExtractor.Note> notes = NoteExtractor.extractNotes(sequence);

        assertEquals(
                List.of(new NoteExtractor.Note(MIDDLE_C, 0, 2000), new NoteExtractor.Note(MIDDLE_C, 500, 1000)),
                notes,
                "each channel's note-off must close that channel's own note-on");
    }

    @Test
    void restrikingASoundingPitchEndsThePreviousNote() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        // Two strikes but only one release, which is how many files notate a repeated note.
        track.add(noteOn(0, MIDDLE_C, 100, 0));
        track.add(noteOn(0, MIDDLE_C, 100, TICKS_PER_QUARTER));
        track.add(noteOff(0, MIDDLE_C, 2 * TICKS_PER_QUARTER));

        List<NoteExtractor.Note> notes = NoteExtractor.extractNotes(sequence);

        assertEquals(
                List.of(new NoteExtractor.Note(MIDDLE_C, 0, 500), new NoteExtractor.Note(MIDDLE_C, 500, 1000)),
                notes,
                "the second strike must end the first note rather than drop it or sustain it to the end");
    }

    @Test
    void collapsesDuplicateNoteOnsAtTheSameInstant() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        // A doubled part: every event written twice at the same tick.
        track.add(noteOn(0, MIDDLE_C, 100, 0));
        track.add(noteOn(0, MIDDLE_C, 100, 0));
        track.add(noteOff(0, MIDDLE_C, TICKS_PER_QUARTER));
        track.add(noteOff(0, MIDDLE_C, TICKS_PER_QUARTER));

        List<NoteExtractor.Note> notes = NoteExtractor.extractNotes(sequence);

        assertEquals(List.of(new NoteExtractor.Note(MIDDLE_C, 0, 500)), notes,
                "duplicated events must not become a zero-length, invisible note");
    }

    @Test
    void discardsNotesReleasedAtTheInstantTheyAreStruck() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(noteOn(0, MIDDLE_C, 100, TICKS_PER_QUARTER));
        track.add(noteOff(0, MIDDLE_C, TICKS_PER_QUARTER));

        assertEquals(List.of(), NoteExtractor.extractNotes(sequence));
    }

    @Test
    void sustainsNotesThatNeverReceiveANoteOff() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(noteOn(0, MIDDLE_C, 100, 0));
        track.add(noteOn(0, E_ABOVE_MIDDLE_C, 100, TICKS_PER_QUARTER));
        track.add(noteOff(0, E_ABOVE_MIDDLE_C, 2 * TICKS_PER_QUARTER));

        List<NoteExtractor.Note> notes = NoteExtractor.extractNotes(sequence);

        assertEquals(2, notes.size(), "the unterminated note must not be dropped");
        assertEquals(new NoteExtractor.Note(MIDDLE_C, 0, 1000), notes.get(0));
    }

    @Test
    void returnsNotesOrderedByOnset() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track firstTrack = sequence.createTrack();
        Track secondTrack = sequence.createTrack();
        firstTrack.add(noteOn(0, MIDDLE_C, 100, 2 * TICKS_PER_QUARTER));
        firstTrack.add(noteOff(0, MIDDLE_C, 3 * TICKS_PER_QUARTER));
        secondTrack.add(noteOn(1, E_ABOVE_MIDDLE_C, 100, 0));
        secondTrack.add(noteOff(1, E_ABOVE_MIDDLE_C, TICKS_PER_QUARTER));

        List<NoteExtractor.Note> notes = NoteExtractor.extractNotes(sequence);

        assertEquals(List.of(0L, 1000L), notes.stream().map(NoteExtractor.Note::onMillis).toList());
    }

    private static MidiEvent noteOn(int channel, int note, int velocity, long tick) throws InvalidMidiDataException {
        return new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, channel, note, velocity), tick);
    }

    private static MidiEvent noteOff(int channel, int note, long tick) throws InvalidMidiDataException {
        return new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, channel, note, 0), tick);
    }
}

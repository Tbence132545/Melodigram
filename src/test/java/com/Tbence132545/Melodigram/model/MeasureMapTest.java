package com.Tbence132545.Melodigram.model;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasureMapTest {

    private static final int TIME_SIGNATURE = 0x58;
    private static final int TICKS_PER_QUARTER = 480;

    @Test
    void assumesFourFourWhenNoTimeSignatureIsGiven() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(note(8 * TICKS_PER_QUARTER));

        List<Long> measures = new MeasureMap(sequence).measureStartMillis();

        // At the default 120bpm a 4/4 measure lasts 2s, and the piece runs 4s.
        assertEquals(List.of(0L, 2000L, 4000L), measures);
    }

    @Test
    void usesTheDeclaredTimeSignature() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(timeSignature(0, 3, 4));
        track.add(note(6 * TICKS_PER_QUARTER));

        List<Long> measures = new MeasureMap(sequence).measureStartMillis();

        // Three crotchets per measure is 1.5s at 120bpm.
        assertEquals(List.of(0L, 1500L, 3000L), measures);
    }

    @Test
    void followsAMidPieceTimeSignatureChange() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(timeSignature(0, 4, 4));
        track.add(timeSignature(8 * TICKS_PER_QUARTER, 2, 4));
        track.add(note(12 * TICKS_PER_QUARTER));

        List<Long> measures = new MeasureMap(sequence).measureStartMillis();

        assertTrue(measures.containsAll(List.of(0L, 2000L, 4000L)), "4/4 measures every 2s");
        assertTrue(measures.contains(5000L), "2/4 measures every 1s after the change");
    }

    @Test
    void producesNoBarlinesForSmpteTiming() throws Exception {
        Sequence sequence = new Sequence(Sequence.SMPTE_25, 40);
        sequence.createTrack().add(note(1000));

        assertTrue(new MeasureMap(sequence).measureStartMillis().isEmpty());
    }

    private static MidiEvent note(long tick) throws InvalidMidiDataException {
        return new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), tick);
    }

    private static MidiEvent timeSignature(long tick, int numerator, int denominator)
            throws InvalidMidiDataException {
        int denominatorPower = Integer.numberOfTrailingZeros(denominator);
        byte[] data = {(byte) numerator, (byte) denominatorPower, 24, 8};
        return new MidiEvent(new MetaMessage(TIME_SIGNATURE, data, data.length), tick);
    }
}

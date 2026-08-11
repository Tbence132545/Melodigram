package com.Tbence132545.Melodigram.model;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MidiTimelineTest {

    private static final int SET_TEMPO = 0x51;
    private static final int TICKS_PER_QUARTER = 480;
    private static final int MICROS_PER_QUARTER_AT_120_BPM = 500_000;
    private static final int MICROS_PER_QUARTER_AT_60_BPM = 1_000_000;

    @Test
    void usesDefaultTempoWhenSequenceDeclaresNone() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        sequence.createTrack();

        MidiTimeline timeline = new MidiTimeline(sequence);

        assertEquals(0, timeline.toMillis(0));
        assertEquals(500, timeline.toMillis(TICKS_PER_QUARTER));
        assertEquals(1000, timeline.toMillis(2 * TICKS_PER_QUARTER));
    }

    @Test
    void appliesTempoChangeOnlyFromItsOwnTickOnwards() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(tempoEvent(TICKS_PER_QUARTER, MICROS_PER_QUARTER_AT_60_BPM));

        MidiTimeline timeline = new MidiTimeline(sequence);

        assertEquals(500, timeline.toMillis(TICKS_PER_QUARTER), "ticks before the change keep the default tempo");
        assertEquals(1500, timeline.toMillis(2 * TICKS_PER_QUARTER), "the slower tempo doubles the next quarter");
    }

    @Test
    void tempoChangeAtTickZeroReplacesTheDefault() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track track = sequence.createTrack();
        track.add(tempoEvent(0, MICROS_PER_QUARTER_AT_60_BPM));

        MidiTimeline timeline = new MidiTimeline(sequence);

        assertEquals(1000, timeline.toMillis(TICKS_PER_QUARTER));
    }

    @Test
    void mergesTempoChangesAcrossTracksInTickOrder() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
        Track conductorTrack = sequence.createTrack();
        Track laterTrack = sequence.createTrack();
        laterTrack.add(tempoEvent(2 * TICKS_PER_QUARTER, MICROS_PER_QUARTER_AT_120_BPM));
        conductorTrack.add(tempoEvent(TICKS_PER_QUARTER, MICROS_PER_QUARTER_AT_60_BPM));

        MidiTimeline timeline = new MidiTimeline(sequence);

        assertEquals(500, timeline.toMillis(TICKS_PER_QUARTER));
        assertEquals(1500, timeline.toMillis(2 * TICKS_PER_QUARTER));
        assertEquals(2000, timeline.toMillis(3 * TICKS_PER_QUARTER), "the second change restores the faster tempo");
    }

    private static MidiEvent tempoEvent(long tick, int microsPerQuarter) throws InvalidMidiDataException {
        byte[] data = {
                (byte) ((microsPerQuarter >> 16) & 0xFF),
                (byte) ((microsPerQuarter >> 8) & 0xFF),
                (byte) (microsPerQuarter & 0xFF)
        };
        return new MidiEvent(new MetaMessage(SET_TEMPO, data, data.length), tick);
    }
}

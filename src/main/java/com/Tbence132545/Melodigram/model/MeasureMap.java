package com.Tbence132545.Melodigram.model;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works out where the barlines fall, by walking the sequence's time signatures and converting
 * each measure boundary to milliseconds through the tempo map.
 */
public final class MeasureMap {

    private static final int TIME_SIGNATURE = 0x58;
    private static final int DEFAULT_NUMERATOR = 4;
    private static final int DEFAULT_DENOMINATOR = 4;

    /** Guards against a malformed signature producing millions of barlines. */
    private static final int MAX_MEASURES = 20_000;

    private final List<Long> measureStartMillis = new ArrayList<>();
    private final List<Long> beatStartMillis = new ArrayList<>();
    private int numerator = DEFAULT_NUMERATOR;
    private int denominator = DEFAULT_DENOMINATOR;

    private record TimeSignature(long tick, int numerator, int denominator) {
    }

    public MeasureMap(Sequence sequence) {
        if (sequence.getDivisionType() != Sequence.PPQ) {
            // Barlines need a tick-per-quarter relationship, which SMPTE timing does not give.
            return;
        }
        MidiTimeline timeline = new MidiTimeline(sequence);
        int resolution = Math.max(1, sequence.getResolution());
        long lastTick = lastTick(sequence);

        List<TimeSignature> signatures = collectTimeSignatures(sequence);
        if (signatures.isEmpty() || signatures.get(0).tick() > 0) {
            signatures.add(0, new TimeSignature(0, DEFAULT_NUMERATOR, DEFAULT_DENOMINATOR));
        }

        long tick = 0;
        for (int i = 0; i < signatures.size() && tick <= lastTick; i++) {
            TimeSignature signature = signatures.get(i);
            boolean isLastSignature = (i + 1 == signatures.size());
            long segmentEnd = isLastSignature ? lastTick : signatures.get(i + 1).tick();
            long ticksPerMeasure = (long) signature.numerator() * resolution * 4 / signature.denominator();
            if (ticksPerMeasure <= 0) {
                continue;
            }

            tick = Math.max(tick, signature.tick());
            long ticksPerBeat = Math.max(1, ticksPerMeasure / signature.numerator());
            // Only the final signature runs through its last barline; earlier ones stop short of
            // the change, because the new signature starts a bar of its own there. Striding past
            // it with the old measure length would knock every later barline out of place.
            while (tick < segmentEnd && measureStartMillis.size() < MAX_MEASURES) {
                measureStartMillis.add(timeline.toMillis(tick));
                addBeatsOfMeasure(timeline, tick, ticksPerBeat, signature.numerator());
                tick += ticksPerMeasure;
            }
            if (isLastSignature && tick == segmentEnd && measureStartMillis.size() < MAX_MEASURES) {
                measureStartMillis.add(timeline.toMillis(tick));
                addBeatsOfMeasure(timeline, tick, ticksPerBeat, signature.numerator());
            }
            tick = segmentEnd;
            this.numerator = signature.numerator();
            this.denominator = signature.denominator();
        }
    }

    /** Measure start times in milliseconds, ascending. Empty when barlines cannot be derived. */
    public List<Long> measureStartMillis() {
        return measureStartMillis;
    }

    /**
     * Beat start times in milliseconds, ascending. Notes are beamed together only within a beat,
     * which is what stops a beam running across the whole measure.
     */
    public List<Long> beatStartMillis() {
        return beatStartMillis;
    }

    public int numerator() {
        return numerator;
    }

    public int denominator() {
        return denominator;
    }

    private void addBeatsOfMeasure(MidiTimeline timeline, long measureTick, long ticksPerBeat, int beats) {
        for (int beat = 0; beat < beats; beat++) {
            beatStartMillis.add(timeline.toMillis(measureTick + beat * ticksPerBeat));
        }
    }

    private static List<TimeSignature> collectTimeSignatures(Sequence sequence) {
        List<TimeSignature> signatures = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage() instanceof MetaMessage meta && meta.getType() == TIME_SIGNATURE) {
                    byte[] data = meta.getData();
                    if (data.length < 2 || data[1] < 0 || data[1] > 6) {
                        continue;
                    }
                    int numerator = data[0] & 0xFF;
                    int denominator = 1 << data[1];
                    if (numerator > 0) {
                        signatures.add(new TimeSignature(event.getTick(), numerator, denominator));
                    }
                }
            }
        }
        signatures.sort(Comparator.comparingLong(TimeSignature::tick));
        return signatures;
    }

    private static long lastTick(Sequence sequence) {
        long last = 0;
        for (Track track : sequence.getTracks()) {
            last = Math.max(last, track.ticks());
        }
        return last;
    }
}

package com.Tbence132545.Melodigram.model;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Converts MIDI ticks to wall-clock milliseconds by replaying the sequence's tempo map.
 *
 * <p>The arithmetic matters: the obvious alternative is to seek a real {@code Sequencer} to
 * every event and read its microsecond position, which costs one seek per event and makes
 * loading a large file take seconds.
 */
public final class MidiTimeline {

    private static final int SET_TEMPO = 0x51;
    private static final long DEFAULT_MICROS_PER_QUARTER = 500_000; // 120 BPM, the MIDI default

    /** From {@code tick} onwards the sequence runs at {@code microsPerTick}, having reached {@code micros}. */
    private record TempoSegment(long tick, long micros, double microsPerTick) {
    }

    private final List<TempoSegment> segments = new ArrayList<>();
    private final double resolution;
    private final boolean isPpq;

    public MidiTimeline(Sequence sequence) {
        double resolution = Math.max(1, sequence.getResolution());
        this.resolution = resolution;
        this.isPpq = sequence.getDivisionType() == Sequence.PPQ;

        if (sequence.getDivisionType() != Sequence.PPQ) {
            // SMPTE timing: ticks are tied to a fixed frame rate, so tempo changes do not
            // affect how long a tick lasts and a single segment covers the whole sequence.
            segments.add(new TempoSegment(0, 0, 1_000_000.0 / (sequence.getDivisionType() * resolution)));
            return;
        }

        segments.add(new TempoSegment(0, 0, DEFAULT_MICROS_PER_QUARTER / resolution));

        long micros = 0;
        long previousTick = 0;
        double microsPerTick = DEFAULT_MICROS_PER_QUARTER / resolution;
        for (long[] change : collectTempoChanges(sequence)) {
            long tick = change[0];
            micros += Math.round((tick - previousTick) * microsPerTick);
            previousTick = tick;
            microsPerTick = change[1] / resolution;
            segments.add(new TempoSegment(tick, micros, microsPerTick));
        }
    }

    public long toMillis(long tick) {
        TempoSegment segment = segmentAt(tick);
        double micros = segment.micros() + (tick - segment.tick()) * segment.microsPerTick();
        return Math.round(micros / 1000.0);
    }

    /**
     * How long a quarter note lasts at the given point in the piece, in milliseconds. Used to
     * decide whether a sounded note is written as a quaver, a crotchet and so on.
     */
    public double millisPerQuarterAtMillis(long millis) {
        if (!isPpq) {
            return DEFAULT_MICROS_PER_QUARTER / 1000.0;
        }
        return segmentAtMillis(millis).microsPerTick() * resolution / 1000.0;
    }

    /** @return the last segment beginning at or before {@code millis}. */
    private TempoSegment segmentAtMillis(long millis) {
        long micros = millis * 1000;
        int low = 0;
        int high = segments.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (segments.get(mid).micros() <= micros) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return segments.get(low);
    }

    /** @return {@code {tick, microsecondsPerQuarterNote}} pairs from every track, in tick order. */
    private static List<long[]> collectTempoChanges(Sequence sequence) {
        List<long[]> changes = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (!(event.getMessage() instanceof MetaMessage meta) || meta.getType() != SET_TEMPO) {
                    continue;
                }
                byte[] data = meta.getData();
                if (data.length < 3) {
                    continue;
                }
                long microsPerQuarter = ((data[0] & 0xFFL) << 16) | ((data[1] & 0xFFL) << 8) | (data[2] & 0xFFL);
                if (microsPerQuarter > 0) {
                    changes.add(new long[]{event.getTick(), microsPerQuarter});
                }
            }
        }
        changes.sort(Comparator.comparingLong(change -> change[0]));
        return changes;
    }

    /** @return the last segment starting at or before {@code tick}. */
    private TempoSegment segmentAt(long tick) {
        int low = 0;
        int high = segments.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (segments.get(mid).tick() <= tick) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return segments.get(low);
    }
}

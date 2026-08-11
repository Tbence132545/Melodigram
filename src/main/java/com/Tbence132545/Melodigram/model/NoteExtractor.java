package com.Tbence132545.Melodigram.model;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens a {@link Sequence} into the timed notes the animation draws.
 *
 * <p>Every note returned has a positive duration, so each one is something the player can
 * actually see and play. Events that would produce no audible note — a pitch struck twice at
 * the same instant, or released at the instant it was struck — are discarded rather than
 * turned into zero-height bars.
 */
public final class NoteExtractor {

    public record Note(int midiNote, long onMillis, long offMillis) {
    }

    private NoteExtractor() {
    }

    public static List<Note> extractNotes(Sequence sequence) {
        MidiTimeline timeline = new MidiTimeline(sequence);
        List<Note> notes = new ArrayList<>();

        for (Track track : sequence.getTracks()) {
            // Pending onsets are keyed by channel as well as pitch: the same pitch sounding on
            // two channels at once would otherwise pair a note-on with the wrong note-off.
            Map<Integer, Long> pendingOnsets = new HashMap<>();
            long trackEndMillis = 0;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (!(event.getMessage() instanceof ShortMessage message)) {
                    continue;
                }
                long millis = timeline.toMillis(event.getTick());
                trackEndMillis = Math.max(trackEndMillis, millis);
                int key = message.getChannel() << 8 | message.getData1();

                if (MidiMessages.isNoteOn(message)) {
                    // Striking a pitch that is already sounding retriggers it: a key cannot be
                    // pressed twice without being released, so the previous note ends here.
                    // Two note-ons at the same instant are instead a duplicated event, which
                    // some files use to double a part; collapse them into the one note.
                    Long retriggered = pendingOnsets.put(key, millis);
                    if (retriggered != null && retriggered < millis) {
                        notes.add(new Note(message.getData1(), retriggered, millis));
                    }
                } else if (MidiMessages.isNoteOff(message)) {
                    Long onset = pendingOnsets.remove(key);
                    if (onset != null && onset < millis) {
                        notes.add(new Note(message.getData1(), onset, millis));
                    }
                }
            }
            closeDanglingNotes(notes, pendingOnsets, trackEndMillis);
        }

        notes.sort(Comparator.comparingLong(Note::onMillis));
        return notes;
    }

    /**
     * Sustains note-ons still held at the end of their track. Dropping them instead would
     * silently omit those notes from the animation.
     */
    private static void closeDanglingNotes(List<Note> notes, Map<Integer, Long> pendingOnsets, long trackEndMillis) {
        pendingOnsets.forEach((key, onset) ->
                notes.add(new Note(key & 0xFF, onset, Math.max(onset, trackEndMillis))));
    }
}

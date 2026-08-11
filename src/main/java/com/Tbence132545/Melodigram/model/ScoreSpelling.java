package com.Tbence132545.Melodigram.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides where each note sits on the staff and whether it needs a printed accidental.
 *
 * <p>Applies the two rules that keep a score readable: a note already covered by the key
 * signature carries no accidental, and an accidental holds for the rest of its measure rather
 * than being repeated on every note it touches.
 */
public final class ScoreSpelling {

    /** @param accidental only meaningful when {@code drawAccidental} is set. */
    public record SpelledNote(int step, int accidental, boolean drawAccidental) {
    }

    private ScoreSpelling() {
    }

    /**
     * @param notes              in onset order
     * @param measureStartMillis barlines, which reset the accidentals in force; may be empty
     * @return one entry per note, in the same order
     */
    public static List<SpelledNote> spell(List<ScoreNote> notes, KeySignature key,
                                          List<Long> measureStartMillis) {
        List<SpelledNote> spelled = new ArrayList<>(notes.size());
        Map<Integer, Integer> alterationInForce = new HashMap<>();
        int measureIndex = 0;

        for (ScoreNote note : notes) {
            int noteMeasure = measureAt(note.onMillis(), measureStartMillis, measureIndex);
            if (noteMeasure != measureIndex || measureStartMillis.isEmpty()) {
                // A barline cancels every accidental back to the key signature.
                alterationInForce.clear();
                measureIndex = noteMeasure;
            }

            KeySignature.Spelled position = key.spell(note.midiNote());
            int letter = Math.floorMod(position.step(), 7);
            int octave = Math.floorDiv(position.step(), 7);
            int voiceKey = octave * 7 + letter;

            int current = alterationInForce.getOrDefault(voiceKey, key.alterationForLetter(letter));
            boolean draw = position.alteration() != current;
            if (draw) {
                alterationInForce.put(voiceKey, position.alteration());
            }
            spelled.add(new SpelledNote(position.step(), position.alteration(), draw));
        }
        return spelled;
    }

    /** Walks forward from the last measure rather than searching, since notes arrive in order. */
    private static int measureAt(long millis, List<Long> measureStartMillis, int from) {
        int index = Math.max(0, Math.min(from, measureStartMillis.size() - 1));
        while (index + 1 < measureStartMillis.size() && measureStartMillis.get(index + 1) <= millis) {
            index++;
        }
        return index;
    }
}

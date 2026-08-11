package com.Tbence132545.Melodigram.model;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/**
 * The key a piece is written in, and how each pitch is spelled within it.
 *
 * <p>Spelling matters as much as pitch: in a two-flat key the black key between A and B is a
 * B flat, not an A sharp. Writing every black key as a sharp is both wrong on the page and far
 * noisier, because notes that belong to the key signature end up carrying their own accidental.
 */
public final class KeySignature {

    private static final int KEY_SIGNATURE_META = 0x59;
    private static final int MAX_ACCIDENTALS = 7;

    /** Pitch class of each natural letter, C through B. */
    private static final int[] LETTER_PITCH_CLASS = {0, 2, 4, 5, 7, 9, 11};
    /** Letters take sharps in the order F C G D A E B. */
    private static final int[] SHARP_ORDER = {3, 0, 4, 1, 5, 2, 6};
    /** Letters take flats in the order B E A D G C F. */
    private static final int[] FLAT_ORDER = {6, 2, 5, 1, 4, 0, 3};

    /** A pitch written on the staff: which line/space, and the accidental it carries. */
    public record Spelled(int step, int alteration) {
    }

    /** Positive for sharp keys, negative for flat keys, zero for C major / A minor. */
    private final int accidentals;
    private final int[] letterAlteration = new int[7];

    public KeySignature(int accidentals) {
        this.accidentals = Math.max(-MAX_ACCIDENTALS, Math.min(MAX_ACCIDENTALS, accidentals));
        int[] order = this.accidentals >= 0 ? SHARP_ORDER : FLAT_ORDER;
        int step = this.accidentals >= 0 ? 1 : -1;
        for (int i = 0; i < Math.abs(this.accidentals); i++) {
            letterAlteration[order[i]] = step;
        }
    }

    public int accidentals() {
        return accidentals;
    }

    public boolean usesFlats() {
        return accidentals < 0;
    }

    /** The alteration the key signature already applies to a letter, so it needs no accidental. */
    public int alterationForLetter(int letterIndex) {
        return letterAlteration[letterIndex];
    }

    /** Reads the declared key signature, falling back to inferring one from the notes played. */
    public static KeySignature of(Sequence sequence) {
        Integer declared = declaredAccidentals(sequence);
        return new KeySignature(declared != null ? declared : inferAccidentals(sequence));
    }

    private static Integer declaredAccidentals(Sequence sequence) {
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage() instanceof MetaMessage meta
                        && meta.getType() == KEY_SIGNATURE_META
                        && meta.getData().length >= 1) {
                    return (int) meta.getData()[0]; // already signed
                }
            }
        }
        return null;
    }

    /**
     * Picks the key signature that leaves the fewest notes needing an accidental, preferring the
     * simpler signature when two fit equally well.
     */
    private static int inferAccidentals(Sequence sequence) {
        int[] pitchClassCounts = new int[12];
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof ShortMessage message
                        && MidiMessages.isNoteOn(message)) {
                    pitchClassCounts[Math.floorMod(message.getData1(), 12)]++;
                }
            }
        }

        int bestAccidentals = 0;
        long bestCost = Long.MAX_VALUE;
        for (int candidate = -MAX_ACCIDENTALS; candidate <= MAX_ACCIDENTALS; candidate++) {
            boolean[] inKey = new KeySignature(candidate).diatonicPitchClasses();
            long cost = 0;
            for (int pitchClass = 0; pitchClass < 12; pitchClass++) {
                if (!inKey[pitchClass]) {
                    cost += pitchClassCounts[pitchClass];
                }
            }
            if (cost < bestCost || (cost == bestCost && Math.abs(candidate) < Math.abs(bestAccidentals))) {
                bestCost = cost;
                bestAccidentals = candidate;
            }
        }
        return bestAccidentals;
    }

    private boolean[] diatonicPitchClasses() {
        boolean[] inKey = new boolean[12];
        for (int letter = 0; letter < 7; letter++) {
            inKey[Math.floorMod(LETTER_PITCH_CLASS[letter] + letterAlteration[letter], 12)] = true;
        }
        return inKey;
    }

    /**
     * Spells a MIDI pitch as a staff position plus the accidental it actually carries. Notes that
     * belong to the key come back with the key's own alteration, which the caller can compare
     * against to decide whether an accidental needs printing.
     */
    public Spelled spell(int midiNote) {
        int pitchClass = Math.floorMod(midiNote, 12);
        int preferredDirection = usesFlats() ? -1 : 1;
        int bestLetter = -1;
        int bestAlteration = 0;
        int bestScore = Integer.MAX_VALUE;

        for (int letter = 0; letter < 7; letter++) {
            int base = LETTER_PITCH_CLASS[letter] + letterAlteration[letter];
            int offset = Math.floorMod(pitchClass - base + 6, 12) - 6;
            if (offset == 0) {
                return spelledAt(midiNote, letter, letterAlteration[letter]);
            }
            if (Math.abs(offset) != 1) {
                continue;
            }
            int alteration = letterAlteration[letter] + offset;
            if (Math.abs(alteration) > 2) {
                continue;
            }
            // Prefer the plainest note on the page: cancelling a key accidental back to a natural
            // beats inventing a sharp on the neighbouring letter. Only genuine ties fall back to
            // the key's own habit of sharpening or flattening.
            int score = Math.abs(alteration) * 2 + (offset == preferredDirection ? 0 : 1);
            if (score < bestScore) {
                bestScore = score;
                bestLetter = letter;
                bestAlteration = alteration;
            }
        }

        if (bestLetter < 0) {
            // No letter is within a semitone, which a 12-tone pitch class cannot really produce;
            // fall back to a natural spelling so a pitch is never dropped.
            int letter = nearestLetterBelow(pitchClass);
            return spelledAt(midiNote, letter, Math.floorMod(pitchClass - LETTER_PITCH_CLASS[letter] + 6, 12) - 6);
        }
        return spelledAt(midiNote, bestLetter, bestAlteration);
    }

    private static int nearestLetterBelow(int pitchClass) {
        int best = 0;
        for (int letter = 0; letter < 7; letter++) {
            if (LETTER_PITCH_CLASS[letter] <= pitchClass) {
                best = letter;
            }
        }
        return best;
    }

    private static Spelled spelledAt(int midiNote, int letter, int alteration) {
        // The letter's own natural pitch fixes which octave it is written in, so that B sharp
        // stays on the B line of the octave below its sounding pitch.
        int naturalMidi = midiNote - alteration;
        int octave = Math.floorDiv(naturalMidi, 12) - 1;
        return new Spelled(octave * 7 + letter, alteration);
    }

    /** Staff steps at which the key signature's accidentals are written on the treble staff. */
    public int[] trebleSymbolSteps() {
        int[] sharpSteps = {38, 35, 39, 36, 33, 37, 34}; // F5 C5 G5 D5 A4 E5 B4
        int[] flatSteps = {34, 37, 33, 36, 32, 35, 31};  // B4 E5 A4 D5 G4 C5 F4
        int[] source = usesFlats() ? flatSteps : sharpSteps;
        int[] steps = new int[Math.abs(accidentals)];
        System.arraycopy(source, 0, steps, 0, steps.length);
        return steps;
    }

    /** The same symbols sit two octaves lower on the bass staff. */
    public int[] bassSymbolSteps() {
        int[] steps = trebleSymbolSteps();
        for (int i = 0; i < steps.length; i++) {
            steps[i] -= 14;
        }
        return steps;
    }
}

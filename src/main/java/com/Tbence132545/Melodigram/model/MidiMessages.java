package com.Tbence132545.Melodigram.model;

import javax.sound.midi.ShortMessage;

/**
 * Note on/off classification, shared by the sequencer playback, the live MIDI input
 * and the note preprocessor.
 */
public final class MidiMessages {

    private MidiMessages() {
    }

    public static boolean isNoteOn(ShortMessage message) {
        return message.getCommand() == ShortMessage.NOTE_ON && message.getData2() > 0;
    }

    /** A NOTE_ON with zero velocity is the conventional encoding of a note release. */
    public static boolean isNoteOff(ShortMessage message) {
        return message.getCommand() == ShortMessage.NOTE_OFF
                || (message.getCommand() == ShortMessage.NOTE_ON && message.getData2() == 0);
    }
}

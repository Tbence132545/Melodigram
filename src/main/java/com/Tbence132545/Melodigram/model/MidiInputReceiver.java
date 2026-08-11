package com.Tbence132545.Melodigram.model;

import com.Tbence132545.Melodigram.controller.PlaybackController;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

/**
 * Translates messages from the user's MIDI keyboard into note events for the controller.
 * Invoked on the MIDI device's own thread, never on the EDT.
 */
public class MidiInputReceiver implements Receiver {

    private final PlaybackController controller;

    public MidiInputReceiver(PlaybackController controller) {
        this.controller = controller;
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!(message instanceof ShortMessage shortMessage)) {
            return;
        }
        if (MidiMessages.isNoteOn(shortMessage)) {
            controller.onExternalNoteOn(shortMessage.getData1());
        } else if (MidiMessages.isNoteOff(shortMessage)) {
            controller.onExternalNoteOff(shortMessage.getData1());
        }
    }

    @Override
    public void close() {
    }
}

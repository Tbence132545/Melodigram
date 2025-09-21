
package com.Tbence132545.Melodigram.model;

import com.Tbence132545.Melodigram.view.PianoWindow;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import com.Tbence132545.Melodigram.controller.PlaybackController;
import javax.swing.*;
import java.util.List;
import java.util.Set;

public class MidiInputReceiver implements Receiver {

    private final PlaybackController controller;
    private final PianoWindow pianoWindow;
    private final List<Integer> currentlyPressedNotes;
    private final Set<Integer> notesPressedInChordAttempt;

    public MidiInputReceiver(PlaybackController controller) {
        this.controller = controller;
        this.pianoWindow = controller.getPianoWindow();
        this.currentlyPressedNotes = controller.getCurrentlyPressedNotes();
        this.notesPressedInChordAttempt = controller.getNotesPressedInChordAttempt();
    }

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!controller.isPracticeMode || !(message instanceof ShortMessage sm)) return;

        int command = sm.getCommand();
        int note = sm.getData1();
        int velocity = sm.getData2();

        if (command == ShortMessage.NOTE_ON && velocity > 0) {
            synchronized (currentlyPressedNotes) {
                if (!currentlyPressedNotes.contains(note)) {
                    currentlyPressedNotes.add(note);
                    notesPressedInChordAttempt.add(note);
                    SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(note));
                }
            }
        } else if (command == ShortMessage.NOTE_OFF || (command == ShortMessage.NOTE_ON && velocity == 0)) {
            synchronized (currentlyPressedNotes) {
                currentlyPressedNotes.remove((Integer) note);
                SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(note));
            }
        }
    }

    @Override
    public void close() {
    }
}
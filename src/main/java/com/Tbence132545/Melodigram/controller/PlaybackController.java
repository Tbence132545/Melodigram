package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.model.HandAssignmentService; // Correct
import com.Tbence132545.Melodigram.model.MidiInputReceiver;
import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.view.AnimationPanel;
import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.PianoWindow;
import com.Tbence132545.Melodigram.view.SeekBar;

import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.Timer;
import java.util.*;

public class PlaybackController {

    private final HandAssignmentService assignmentService;

    private static final int TARGET_FPS = 60;
    private static final int TIMER_DELAY_MS = 1000 / TARGET_FPS;
    private static final long STARTUP_DELAY_MS = 3000;

    private final MidiPlayer midiPlayer;
    private final PianoWindow pianoWindow;
    private final AnimationPanel animationPanel;
    private final SeekBar seekBar;
    private final Timer sharedTimer;

    private long startTime;
    private long lastTickTime;
    private boolean playbackStarted = false;
    private boolean animationPaused = false;
    public boolean isPracticeMode = false;
    private boolean isEditingMode= false;
    private boolean wasPlayingBeforeDrag = false;
    private ListWindow.MidiFileActionListener.HandMode practiceHandMode = ListWindow.MidiFileActionListener.HandMode.BOTH;
    private MidiDevice midiInputDevice;
    private final List<Integer> currentlyPressedNotes = new ArrayList<>();
    private final List<Integer> awaitedNotes = new ArrayList<>();
    private final Set<Integer> notesPressedInChordAttempt = new HashSet<>();
    private boolean isNotationOn = false;

    public PlaybackController(MidiPlayer midiPlayer, PianoWindow pianoWindow) {
        this.assignmentService = new HandAssignmentService(); // Instantiated here
        this.midiPlayer = midiPlayer;
        this.pianoWindow = pianoWindow;
        this.animationPanel = pianoWindow.getAnimationPanel();
        this.seekBar = new SeekBar(midiPlayer.getSequencer());

        preprocessNotes(midiPlayer.getSequencer().getSequence());
        animationPanel.setTotalDurationMillis(midiPlayer.getSequencer().getMicrosecondLength() / 1000);
        pianoWindow.addSeekBar(seekBar);
        setupEventListeners();
        this.sharedTimer = new Timer(TIMER_DELAY_MS, e -> onTimerTick());
        initializePlayback();
    }

    private void initializePlayback() {
        startTime = System.currentTimeMillis();
        lastTickTime = startTime;
        sharedTimer.start();
    }

    private void setupEventListeners() {
        midiPlayer.setNoteOnListener(this::onNoteOn);
        midiPlayer.setNoteOffListener(this::onNoteOff);
        pianoWindow.setPlayButtonListener(e -> togglePlayback());
        pianoWindow.setForwardButtonListener(e -> seekAndPreserveState(midiPlayer.getSequencer().getMicrosecondPosition() + 10_000_000));
        pianoWindow.setBackwardButtonListener(e -> seekAndPreserveState(midiPlayer.getSequencer().getMicrosecondPosition() - 10_000_000));
        pianoWindow.setSaveButtonListener(e -> handleSave());
        pianoWindow.setToggleNotationListener( e -> handleToggleNotation());
        seekBar.setSeekListener(this::seekAndPreserveState);
        animationPanel.setOnDragStart(this::handleDragStart);
        animationPanel.setOnTimeChange(this::handleDragChange);
        animationPanel.setOnDragEnd(this::handleDragEnd);
    }
    private void handleToggleNotation() {
        isNotationOn = !isNotationOn;
        animationPanel.setNotationEnabled(isNotationOn);
    }
    private void onTimerTick() {
        long now = System.currentTimeMillis();
        long delta = now - lastTickTime;
        lastTickTime = now;
        if (!playbackStarted) {
            handleInitialStartup(now);
            return;
        }
        if (animationPaused) {
            return;
        }
        if (isPracticeMode) {
            handlePracticeModeTick(delta);
        } else {
            handlePlaybackModeTick(delta);
        }
        seekBar.updateProgress();
    }

    private void handleInitialStartup(long now) {
        if (midiPlayer.getSequencer().getMicrosecondLength() > 0 && now - startTime > STARTUP_DELAY_MS) {
            pianoWindow.disableButtons(false);
            if (isEditingMode) {
                seekBar.setUserInteractionEnabled(false);
            }
            if (!isPracticeMode && !isEditingMode) {
                midiPlayer.play();
            }
            playbackStarted = true;
        } else {
            pianoWindow.disableButtons(true);
        }
    }

    private void handlePlaybackModeTick(long delta) {
        animationPanel.tick(delta);
        if (midiPlayer.isPlaying()) {
            long newTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
            animationPanel.updatePlaybackTime(newTimeMillis);
        }
    }

    private void handlePracticeModeTick(long delta) {
        boolean chordIsSatisfied = false;
        if (!awaitedNotes.isEmpty()) {
            Set<Integer> currentlyHeldSet = new HashSet<>(currentlyPressedNotes);
            Set<Integer> expectedSet = new HashSet<>(awaitedNotes);
            if (notesPressedInChordAttempt.containsAll(expectedSet) && currentlyHeldSet.equals(expectedSet)) {
                chordIsSatisfied = true;
            } else {
                SwingUtilities.invokeLater(() -> {
                    for (int note : awaitedNotes) {
                        synchronized (currentlyPressedNotes) {
                            if (!currentlyPressedNotes.contains(note)) pianoWindow.highlightNote(note);
                        }
                    }
                });
                return;
            }
        }
        long prevTime = animationPanel.getCurrentTimeMillis();
        animationPanel.tick(delta);
        long nextTime = animationPanel.getCurrentTimeMillis();
        List<Integer> onsets = animationPanel.getNotesStartingBetween(
                (prevTime == 0) ? -1 : prevTime,
                nextTime,
                this.practiceHandMode
        );
        if (!onsets.isEmpty()) {
            awaitedNotes.clear();
            awaitedNotes.addAll(onsets);
            notesPressedInChordAttempt.clear();
            SwingUtilities.invokeLater(() -> {
                pianoWindow.releaseAllKeys();
                awaitedNotes.forEach(pianoWindow::highlightNote);
            });
        } else if (chordIsSatisfied) {
            awaitedNotes.clear();
        }
    }

    private void handleDragStart() {
        wasPlayingBeforeDrag = midiPlayer.isPlaying();
        if (wasPlayingBeforeDrag) {
            midiPlayer.stop();
        }
        animationPaused = true;
    }

    private void handleDragChange(long newTimeMillis) {
        updateSequencerPosition(newTimeMillis * 1000);
    }

    private void handleDragEnd() {
        if (isEditingMode) {
            animationPaused = true;
        } else if (isPracticeMode) {
            animationPaused = false;
        } else {
            animationPaused = !wasPlayingBeforeDrag;
            if (wasPlayingBeforeDrag) {
                midiPlayer.play();
            }
        }
        wasPlayingBeforeDrag = false;
    }

    private void seekAndPreserveState(long newMicroseconds) {
        if (isPracticeMode || isEditingMode) {
            updateSequencerPosition(newMicroseconds);
            return;
        }
        boolean wasPlaying = midiPlayer.isPlaying();
        if (wasPlaying) midiPlayer.stop();
        updateSequencerPosition(newMicroseconds);
        if (wasPlaying) midiPlayer.play();
    }

    private void updateSequencerPosition(long newMicroseconds) {
        long clampedMicroseconds = Math.max(0, Math.min(newMicroseconds, midiPlayer.getSequencer().getMicrosecondLength()));
        midiPlayer.getSequencer().setMicrosecondPosition(clampedMicroseconds);
        animationPanel.updatePlaybackTime(clampedMicroseconds / 1000);
        resetPracticeState();
        lastTickTime = System.currentTimeMillis();
        seekBar.updateProgress();
    }

    void togglePlayback() {
        if(!isPracticeMode && !isEditingMode){
            if (midiPlayer.isPlaying()) {
                midiPlayer.stop();
                animationPaused = true;
                pianoWindow.setPlayButtonIcon(false);
            } else {
                midiPlayer.play();
                animationPaused = false;
                lastTickTime = System.currentTimeMillis();
                pianoWindow.setPlayButtonIcon(true);
            }
        }
    }

    public void setEditingMode(boolean enabled) {
        this.isEditingMode = enabled;
        animationPanel.setHandAssignmentMode(enabled);
        pianoWindow.setEditingMode(enabled);
        seekBar.setUserInteractionEnabled(!enabled);
        if (enabled) {
            if (midiPlayer.isPlaying()) {
                midiPlayer.stop();
            }
            animationPaused = true;
            pianoWindow.setPlayButtonIcon(false);
            updateSequencerPosition(0);
            pianoWindow.repaint();
        }
    }
    public void setPracticeMode(boolean enabled, ListWindow.MidiFileActionListener.HandMode mode) {
        this.isPracticeMode = enabled;
        this.practiceHandMode = mode;
        animationPanel.setPracticeFilterMode(mode);
        pianoWindow.disableButtons(enabled);
        if (enabled) {
            midiPlayer.stop();
            resetPracticeState();
        }
    }

    public void setMidiInputDevice(MidiDevice device) {
        try {
            if (midiInputDevice != null && midiInputDevice.isOpen()) midiInputDevice.close();
            midiInputDevice = device;
            if (!device.isOpen()) device.open();
            device.getTransmitter().setReceiver(new MidiInputReceiver(this));
        } catch (MidiUnavailableException e) {
            e.printStackTrace();
        }
    }

    private void handleSave() {
        if (!isEditingMode) return;

        List<AnimationPanel.HandAssignment> items = animationPanel.getAssignedNotes();
        if (items.isEmpty()) {
            JOptionPane.showMessageDialog(pianoWindow, "No hand assignments to save.", "Nothing to save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Sequence seq = midiPlayer.getSequencer().getSequence();
        boolean success = assignmentService.saveAssignments(seq, items);

        if (success) {
            JOptionPane.showMessageDialog(pianoWindow, "Saved hand assignments successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(pianoWindow, "Failed to save assignments.", "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void preprocessNotes(Sequence sequence) {
        try (Sequencer tempSequencer = MidiSystem.getSequencer(false)) {
            tempSequencer.open();
            tempSequencer.setSequence(sequence);
            Map<Integer, List<Long>> activeNotes = new HashMap<>();
            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);
                    MidiMessage msg = event.getMessage();
                    if (msg instanceof ShortMessage sm) {
                        int cmd = sm.getCommand();
                        int note = sm.getData1();
                        tempSequencer.setTickPosition(event.getTick());
                        long timeMillis = tempSequencer.getMicrosecondPosition() / 1000;
                        if (cmd == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            activeNotes.computeIfAbsent(note, k -> new ArrayList<>()).add(timeMillis);
                        } else if (cmd == ShortMessage.NOTE_OFF || (cmd == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            List<Long> onTimes = activeNotes.get(note);
                            if (onTimes != null && !onTimes.isEmpty()) {
                                long onTime = onTimes.remove(0);
                                animationPanel.addFallingNote(note, onTime, timeMillis, pianoWindow.isBlackKey(note));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Optional<List<AnimationPanel.HandAssignment>> assignments = assignmentService.loadAssignments(sequence);
        assignments.ifPresent(animationPanel::applyHandAssignments);
    }


    public PianoWindow getPianoWindow() {
        return this.pianoWindow;
    }

    public List<Integer> getCurrentlyPressedNotes() {
        return this.currentlyPressedNotes;
    }

    public Set<Integer> getNotesPressedInChordAttempt() {
        return this.notesPressedInChordAttempt;
    }

    private void onNoteOn(int midiNote) {
        if (isPracticeMode || isEditingMode) {
            return;
        }
        long playerTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
        animationPanel.updatePlaybackTime(playerTimeMillis);
        SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(midiNote));
    }

    private void onNoteOff(int midiNote) {
        if (isPracticeMode || isEditingMode) {
            return;
        }
        SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(midiNote));
    }

    private void resetPracticeState() {
        synchronized (currentlyPressedNotes) {
            currentlyPressedNotes.clear();
        }
        awaitedNotes.clear();
        notesPressedInChordAttempt.clear();
        SwingUtilities.invokeLater(pianoWindow::releaseAllKeys);
    }
}
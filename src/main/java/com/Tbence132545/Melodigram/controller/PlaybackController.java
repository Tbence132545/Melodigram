package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.model.HandAssignmentService;
import com.Tbence132545.Melodigram.model.KeySignature;
import com.Tbence132545.Melodigram.model.MeasureMap;
import com.Tbence132545.Melodigram.model.MidiInputReceiver;
import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.model.MidiTimeline;
import com.Tbence132545.Melodigram.model.NoteExtractor;
import com.Tbence132545.Melodigram.model.ScoreNote;
import com.Tbence132545.Melodigram.view.AnimationPanel;
import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.PianoWindow;
import com.Tbence132545.Melodigram.view.SeekBar;
import com.Tbence132545.Melodigram.view.SheetMusicPanel;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Drives one open piece: it advances the animation clock, keeps the sequencer in step with it,
 * and in practice mode gates that clock on the user playing the right notes.
 *
 * <p>Everything here runs on the EDT except {@link #onExternalNoteOn}/{@link #onExternalNoteOff},
 * which arrive on the MIDI input thread and touch only the guarded practice state.
 */
public class PlaybackController {

    private static final int TARGET_FPS = 60;
    private static final int TIMER_DELAY_MS = 1000 / TARGET_FPS;

    /** Grace period before playback begins, so the first notes are on screen when sound starts. */
    private static final long STARTUP_DELAY_MS = 3000;

    private static final long SEEK_STEP_MICROS = 10_000_000;

    private final HandAssignmentService assignmentService = new HandAssignmentService();
    private final MidiPlayer midiPlayer;
    private final PianoWindow pianoWindow;
    private final AnimationPanel animationPanel;
    private final SheetMusicPanel sheetMusicPanel;
    private final SeekBar seekBar;
    private final Timer sharedTimer;

    private long startTime;
    private long lastTickTime;
    private boolean playbackStarted = false;
    private boolean animationPaused = false;
    private boolean isEditingMode = false;
    private boolean wasPlayingBeforeDrag = false;
    private ListWindow.MidiFileActionListener.HandMode practiceHandMode = ListWindow.MidiFileActionListener.HandMode.BOTH;
    private MidiDevice midiInputDevice;
    private double playbackSpeed = 1.0;

    /** Read from the MIDI input thread, written on the EDT. */
    private volatile boolean isPracticeMode = false;

    /** Guards the two sets below, which the MIDI input thread writes and the timer reads. */
    private final Object practiceLock = new Object();
    private final Set<Integer> currentlyPressedNotes = new HashSet<>();
    private final Set<Integer> notesPressedInChordAttempt = new HashSet<>();

    /** The chord the animation is currently waiting on. EDT only. */
    private final List<Integer> awaitedNotes = new ArrayList<>();

    public PlaybackController(MidiPlayer midiPlayer, PianoWindow pianoWindow) {
        this.midiPlayer = midiPlayer;
        this.pianoWindow = pianoWindow;
        this.animationPanel = pianoWindow.getAnimationPanel();
        this.sheetMusicPanel = pianoWindow.getSheetMusicPanel();
        this.seekBar = new SeekBar(midiPlayer.getSequencer());

        loadNotes(midiPlayer.getSequencer().getSequence());
        animationPanel.setTotalDurationMillis(midiPlayer.getSequencer().getMicrosecondLength() / 1000);
        pianoWindow.addSeekBar(seekBar);
        setupEventListeners();

        this.sharedTimer = new Timer(TIMER_DELAY_MS, e -> onTimerTick());
        startTime = System.currentTimeMillis();
        lastTickTime = startTime;
        sharedTimer.start();
    }

    /** Stops the animation clock and releases the audio and MIDI input devices. */
    public void dispose() {
        sharedTimer.stop();
        midiPlayer.stop();
        closeMidiInputDevice();
        midiPlayer.close();
    }

    private void setupEventListeners() {
        midiPlayer.setNoteOnListener(this::onNoteOn);
        midiPlayer.setNoteOffListener(this::onNoteOff);
        pianoWindow.setPlayButtonListener(e -> togglePlayback());
        pianoWindow.setForwardButtonListener(e -> seekBy(SEEK_STEP_MICROS));
        pianoWindow.setBackwardButtonListener(e -> seekBy(-SEEK_STEP_MICROS));
        pianoWindow.setSaveButtonListener(e -> handleSave());
        pianoWindow.setNotationToggleListener(animationPanel::setNotationEnabled);
        pianoWindow.setSpeedChangeListener(this::setPlaybackSpeed);
        seekBar.setSeekListener(this::seekAndPreserveState);
        animationPanel.setOnHandAssigned(sheetMusicPanel::repaint);
        animationPanel.setOnDragStart(this::handleDragStart);
        animationPanel.setOnTimeChange(this::handleDragChange);
        animationPanel.setOnDragEnd(this::handleDragEnd);
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
        boolean sequenceLoaded = midiPlayer.getSequencer().getMicrosecondLength() > 0;
        if (!sequenceLoaded || now - startTime <= STARTUP_DELAY_MS) {
            pianoWindow.disableButtons(true);
            return;
        }
        // Practice mode drives the transport from the keyboard, so its controls stay disabled.
        pianoWindow.disableButtons(isPracticeMode);
        if (isEditingMode) {
            seekBar.setEnabled(false);
        }
        if (!isPracticeMode && !isEditingMode) {
            midiPlayer.play();
        }
        playbackStarted = true;
    }

    /**
     * Sets how fast the piece plays, 1.0 being its written tempo. The sequencer handles the
     * audio; the animation clock is scaled to match so the notes and the sound stay together
     * in practice mode, where no sequencer position is available to sync against.
     */
    private void setPlaybackSpeed(double speed) {
        this.playbackSpeed = speed;
        midiPlayer.setTempoFactor((float) speed);
    }

    private long scaleToPlaybackSpeed(long deltaMillis) {
        return Math.round(deltaMillis * playbackSpeed);
    }

    /** Advances the animation clock; the sheet view follows the same clock. */
    private void advanceAnimation(long deltaMillis) {
        animationPanel.tick(deltaMillis);
        sheetMusicPanel.setCurrentTimeMillis(animationPanel.getCurrentTimeMillis());
    }

    private void setAnimationTime(long millis) {
        animationPanel.updatePlaybackTime(millis);
        sheetMusicPanel.setCurrentTimeMillis(millis);
    }

    private void handlePlaybackModeTick(long delta) {
        advanceAnimation(scaleToPlaybackSpeed(delta));
        if (midiPlayer.isPlaying()) {
            setAnimationTime(midiPlayer.getSequencer().getMicrosecondPosition() / 1000);
        }
    }

    private void handlePracticeModeTick(long delta) {
        if (!awaitedNotes.isEmpty() && !consumeSatisfiedChord()) {
            return;
        }

        long previousTime = animationPanel.getCurrentTimeMillis();
        advanceAnimation(scaleToPlaybackSpeed(delta));
        List<Integer> onsets = animationPanel.getNotesStartingBetween(
                (previousTime == 0) ? -1 : previousTime,
                animationPanel.getCurrentTimeMillis(),
                practiceHandMode);

        if (!onsets.isEmpty()) {
            awaitOnsets(onsets);
        }
    }

    /**
     * @return true when the awaited chord has been played and the animation may advance;
     *         false while still waiting, having re-highlighted the notes yet to be pressed.
     */
    private boolean consumeSatisfiedChord() {
        Set<Integer> expected = new HashSet<>(awaitedNotes);
        Set<Integer> stillMissing = new LinkedHashSet<>();
        boolean satisfied;

        synchronized (practiceLock) {
            // Every note must have been struck during this attempt and all of them must still be
            // held, so releasing one key part-way through does not count as playing the chord.
            satisfied = notesPressedInChordAttempt.containsAll(expected) && currentlyPressedNotes.equals(expected);
            if (!satisfied) {
                for (int note : awaitedNotes) {
                    if (!currentlyPressedNotes.contains(note)) {
                        stillMissing.add(note);
                    }
                }
            }
        }

        if (!satisfied) {
            SwingUtilities.invokeLater(() -> stillMissing.forEach(pianoWindow::highlightNote));
            return false;
        }
        awaitedNotes.clear();
        return true;
    }

    private void awaitOnsets(List<Integer> onsets) {
        awaitedNotes.clear();
        awaitedNotes.addAll(onsets);
        synchronized (practiceLock) {
            notesPressedInChordAttempt.clear();
        }
        List<Integer> toHighlight = List.copyOf(onsets);
        SwingUtilities.invokeLater(() -> {
            pianoWindow.releaseAllKeys();
            toHighlight.forEach(pianoWindow::highlightNote);
        });
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

    private void seekBy(long deltaMicroseconds) {
        seekAndPreserveState(midiPlayer.getSequencer().getMicrosecondPosition() + deltaMicroseconds);
    }

    private void seekAndPreserveState(long newMicroseconds) {
        if (isPracticeMode || isEditingMode) {
            updateSequencerPosition(newMicroseconds);
            return;
        }
        boolean wasPlaying = midiPlayer.isPlaying();
        if (wasPlaying) {
            midiPlayer.stop();
        }
        updateSequencerPosition(newMicroseconds);
        if (wasPlaying) {
            midiPlayer.play();
        }
    }

    private void updateSequencerPosition(long newMicroseconds) {
        long clamped = Math.max(0, Math.min(newMicroseconds, midiPlayer.getSequencer().getMicrosecondLength()));
        midiPlayer.getSequencer().setMicrosecondPosition(clamped);
        setAnimationTime(clamped / 1000);
        resetPracticeState();
        lastTickTime = System.currentTimeMillis();
        seekBar.updateProgress();
    }

    void togglePlayback() {
        if (isPracticeMode || isEditingMode) {
            return;
        }
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

    public void setEditingMode(boolean enabled) {
        this.isEditingMode = enabled;
        animationPanel.setHandAssignmentMode(enabled);
        pianoWindow.setEditingMode(enabled);
        seekBar.setEnabled(!enabled);
        if (enabled) {
            midiPlayer.stop();
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

    public void setMidiInputDevice(MidiDevice device) throws MidiUnavailableException {
        closeMidiInputDevice();
        midiInputDevice = device;
        if (!device.isOpen()) {
            device.open();
        }
        device.getTransmitter().setReceiver(new MidiInputReceiver(this));
    }

    private void closeMidiInputDevice() {
        if (midiInputDevice != null && midiInputDevice.isOpen()) {
            midiInputDevice.close();
        }
        midiInputDevice = null;
    }

    /** Called on the MIDI input thread when the user presses a key. */
    public void onExternalNoteOn(int midiNote) {
        if (!isPracticeMode) {
            return;
        }
        synchronized (practiceLock) {
            if (!currentlyPressedNotes.add(midiNote)) {
                return;
            }
            notesPressedInChordAttempt.add(midiNote);
        }
        SwingUtilities.invokeLater(() -> pianoWindow.highlightNote(midiNote));
    }

    /** Called on the MIDI input thread when the user releases a key. */
    public void onExternalNoteOff(int midiNote) {
        if (!isPracticeMode) {
            return;
        }
        synchronized (practiceLock) {
            currentlyPressedNotes.remove(midiNote);
        }
        SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(midiNote));
    }

    private void handleSave() {
        if (!isEditingMode) {
            return;
        }
        List<AnimationPanel.HandAssignment> items = animationPanel.getAssignedNotes();
        if (items.isEmpty()) {
            JOptionPane.showMessageDialog(pianoWindow, "No hand assignments to save.", "Nothing to save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean success = assignmentService.saveAssignments(midiPlayer.getSequencer().getSequence(), items);
        if (success) {
            JOptionPane.showMessageDialog(pianoWindow, "Saved hand assignments successfully.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(pianoWindow, "Failed to save assignments.", "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Both views draw the same {@link ScoreNote} instances, so assigning a hand in the
     * falling-note view immediately re-colours the staff as well.
     */
    private void loadNotes(Sequence sequence) {
        List<ScoreNote> scoreNotes = new ArrayList<>();
        for (NoteExtractor.Note note : NoteExtractor.extractNotes(sequence)) {
            scoreNotes.add(new ScoreNote(note.midiNote(), note.onMillis(), note.offMillis(),
                    pianoWindow.isBlackKey(note.midiNote())));
        }
        animationPanel.setNotes(scoreNotes);

        Optional<List<AnimationPanel.HandAssignment>> assignments = assignmentService.loadAssignments(sequence);
        assignments.ifPresent(animationPanel::applyHandAssignments);

        sheetMusicPanel.setScore(scoreNotes, new MeasureMap(sequence), new MidiTimeline(sequence),
                KeySignature.of(sequence));
    }

    private void onNoteOn(int midiNote) {
        if (isPracticeMode || isEditingMode) {
            return;
        }
        long playerTimeMillis = midiPlayer.getSequencer().getMicrosecondPosition() / 1000;
        SwingUtilities.invokeLater(() -> {
            setAnimationTime(playerTimeMillis);
            pianoWindow.highlightNote(midiNote);
        });
    }

    private void onNoteOff(int midiNote) {
        if (isPracticeMode || isEditingMode) {
            return;
        }
        SwingUtilities.invokeLater(() -> pianoWindow.releaseNote(midiNote));
    }

    private void resetPracticeState() {
        synchronized (practiceLock) {
            currentlyPressedNotes.clear();
            notesPressedInChordAttempt.clear();
        }
        awaitedNotes.clear();
        SwingUtilities.invokeLater(pianoWindow::releaseAllKeys);
    }
}

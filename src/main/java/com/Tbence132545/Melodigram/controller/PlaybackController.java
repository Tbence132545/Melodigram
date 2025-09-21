package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.model.MidiFileService;
import com.Tbence132545.Melodigram.model.MidiInputReceiver;
import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.view.AnimationPanel;
import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.PianoWindow;
import com.Tbence132545.Melodigram.view.SeekBar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.Timer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class PlaybackController {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static class HandAssignmentFile {
        String midiHash;
        List<AnimationPanel.HandAssignment> assignments;
    }

    private static final int TARGET_FPS = 60;
    private static final int TIMER_DELAY_MS = 1000 / TARGET_FPS;
    private static final long STARTUP_DELAY_MS = 3000; //This is used to delay the start of the animation upon loading the pianoWindow- I found it was neccessary to not have any delays between animation and sound
    private static final Path ASSIGNMENTS_DIR;

    static {
        ASSIGNMENTS_DIR = getStandardApplicationDataDirectory().resolve("assignments");
    }

    private static Path getStandardApplicationDataDirectory() {
        String appName = "Melodigram";
        String os = System.getProperty("os.name").toLowerCase();
        Path baseDir;
        if (os.contains("win")) {
            baseDir = Paths.get(System.getenv("APPDATA"));
        } else if (os.contains("mac")) {
            baseDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support");
        } else {
            baseDir = Paths.get(System.getProperty("user.home"), "." + appName);
        }
        return baseDir.resolve(appName);
    }

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
    public PianoWindow getPianoWindow() {return this.pianoWindow;}

    public List<Integer> getCurrentlyPressedNotes() {
        return this.currentlyPressedNotes;
    }

    public Set<Integer> getNotesPressedInChordAttempt() {
        return this.notesPressedInChordAttempt;
    }

    public PlaybackController(MidiPlayer midiPlayer, PianoWindow pianoWindow) {
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
        seekBar.setSeekListener(this::seekAndPreserveState);
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

    //Event Handling Methods
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
                pianoWindow.setPlayButtonText("▶");
            } else {
                midiPlayer.play();
                animationPaused = false;
                lastTickTime = System.currentTimeMillis();
                pianoWindow.setPlayButtonText("||");
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
            pianoWindow.setPlayButtonText("▶");
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
        saveAssignments();
    }

    private static String computeSequenceHash(Sequence sequence) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent ev = track.get(i);
                    updateDigestWithLong(md, ev.getTick());
                    MidiMessage msg = ev.getMessage();
                    byte[] raw = msg.getMessage();
                    md.update(raw, 0, msg.getLength());
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 unavailable", e);
        }
    }

    private static void updateDigestWithLong(MessageDigest md, long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) (v & 0xFF);
            v >>= 8;
        }
        md.update(b);
    }

    private Path getAssignmentFilePath(Sequence sequence) {
        String hash = computeSequenceHash(sequence);
        return ASSIGNMENTS_DIR.resolve(hash + ".json");
    }

    private void saveAssignments() {
        List<AnimationPanel.HandAssignment> items = animationPanel.getAssignedNotes();
        if (items.isEmpty()) {
            JOptionPane.showMessageDialog(pianoWindow, "No hand assignments to save.", "Nothing to save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Sequence seq = midiPlayer.getSequencer().getSequence();
        String hash = computeSequenceHash(seq);
        Path file = getAssignmentFilePath(seq);

        HandAssignmentFile data = new HandAssignmentFile();
        data.midiHash = hash;
        data.assignments = items;

        try {
            Files.createDirectories(ASSIGNMENTS_DIR);
            String json = gson.toJson(data); // Convert object to JSON string
            Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            JOptionPane.showMessageDialog(pianoWindow, "Saved hand assignments to:\n" + file.toAbsolutePath(), "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(pianoWindow, "Failed to save assignments:\n" + e.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadAssignmentsIfPresent(Sequence sequence) {
        Path file = getAssignmentFilePath(sequence);
        if (!Files.exists(file)) {
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            HandAssignmentFile data = gson.fromJson(content, HandAssignmentFile.class);

            if (data != null && data.assignments != null) {
                animationPanel.applyHandAssignments(data.assignments);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static boolean assignmentFileExistsFor(String midiFileName) {
        try {
            MidiFileService service = new MidiFileService();
            MidiFileService.MidiData midiData = service.loadMidiData(midiFileName);
            String hash = computeSequenceHash(midiData.sequence());
            Path path = ASSIGNMENTS_DIR.resolve(hash + ".json");
            return Files.exists(path);
        } catch (Exception e) {
            e.printStackTrace(); // log for debugging
            return false;
        }
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
        loadAssignmentsIfPresent(sequence);
    }
}
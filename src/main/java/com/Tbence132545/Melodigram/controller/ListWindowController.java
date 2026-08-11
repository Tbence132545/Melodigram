package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.model.MidiFileService;
import com.Tbence132545.Melodigram.model.MidiInputSelector;
import com.Tbence132545.Melodigram.model.MidiPlayer;
import com.Tbence132545.Melodigram.model.Settings;
import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.MainWindow;
import com.Tbence132545.Melodigram.view.PianoWindow;
import com.Tbence132545.Melodigram.view.ViewMode;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ListWindowController implements ListWindow.MidiFileActionListener {

    private final ListWindow view;
    private final MidiFileService midiFileService;

    public ListWindowController(ListWindow view) {
        this.view = view;
        this.midiFileService = new MidiFileService();
        setupEventListeners();
        loadAndDisplayMidiFiles();
    }

    private void setupEventListeners() {
        view.setBackButtonListener(e -> handleBackButton());
        view.setImportButtonListener(e -> handleImportButton());
    }

    private void loadAndDisplayMidiFiles() {
        String[] fileNames = midiFileService.getAllMidiFileNames().toArray(new String[0]);
        view.setMidiFileList(fileNames, this);
    }

    private void handleBackButton() {
        view.dispose();
        MainWindow mainWin = new MainWindow();
        new MainWindowController(mainWin).openMainWindow();
    }

    private void handleImportButton() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a MIDI file to import");
        fileChooser.setFileFilter(new FileNameExtensionFilter("MIDI Files", "mid", "midi"));

        if (fileChooser.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            try {
                midiFileService.importMidiFile(fileChooser.getSelectedFile());
                JOptionPane.showMessageDialog(view, "File imported successfully!");
                loadAndDisplayMidiFiles();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(view, "Could not import file: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void onAssignHandsClicked(String midiFilename) {
        openPianoWindow(midiFilename, OpenMode.EDIT, null, null);
    }

    @Override
    public void onWatchAndListenClicked(String midiFilename) {
        openPianoWindow(midiFilename, OpenMode.WATCH, null, null);
    }

    @Override
    public void onPracticeClicked(String midiFilename, HandMode mode) {
        // A device chosen in settings means not asking again every time.
        MidiDevice.Info preferred = preferredInputDevice();
        if (preferred != null) {
            openPianoWindow(midiFilename, OpenMode.PRACTICE, mode, preferred);
            return;
        }
        MidiInputSelector selector = new MidiInputSelector(view, deviceInfo -> {
            if (deviceInfo != null) {
                openPianoWindow(midiFilename, OpenMode.PRACTICE, mode, deviceInfo);
            }
        });
        selector.setVisible(true);
    }

    /** @return the input named in settings if it is still connected, otherwise null. */
    private MidiDevice.Info preferredInputDevice() {
        String preferred = Settings.load().getMidiInputDeviceName();
        if (preferred == null || preferred.isBlank()) {
            return null;
        }
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            if (!info.getName().equals(preferred)) {
                continue;
            }
            try {
                if (MidiSystem.getMidiDevice(info).getMaxTransmitters() != 0) {
                    return info;
                }
            } catch (MidiUnavailableException ignored) {
                // Fall through and ask, rather than failing on a device we cannot open.
            }
        }
        return null;
    }

    /** How a piece is being opened, which is separate from which view it shows. */
    private enum OpenMode {
        WATCH("Error Opening Piano View"),
        EDIT("Error Opening Editor"),
        PRACTICE("Error Initializing Practice");

        private final String errorTitle;

        OpenMode(String errorTitle) {
            this.errorTitle = errorTitle;
        }
    }

    private void openPianoWindow(String midiFileName, OpenMode mode, HandMode hand, MidiDevice.Info deviceInfo) {
        MidiFileService.MidiData midiData = null;
        PlaybackController playbackController = null;
        try {
            midiData = midiFileService.loadMidiData(midiFileName);
            int[] range = MidiPlayer.extractNoteRange(midiData.sequence());
            PianoWindow pianoWindow = new PianoWindow(range[0], range[1]);
            pianoWindow.setViewMode(configuredViewMode());
            playbackController = new PlaybackController(midiData.player(), pianoWindow);

            if (mode == OpenMode.EDIT) {
                playbackController.setEditingMode(true);
            } else if (mode == OpenMode.PRACTICE) {
                playbackController.setPracticeMode(true, hand);
                playbackController.setMidiInputDevice(MidiSystem.getMidiDevice(deviceInfo));
            }

            PlaybackController openController = playbackController;
            pianoWindow.setBackButtonListener(e -> {
                openController.dispose();
                pianoWindow.dispose();
                view.setVisible(true);
            });

            pianoWindow.setVisible(true);
            view.setVisible(false);

        } catch (Exception e) {
            // Without this the sequencer, synthesizer and input device stay open for the life
            // of the process every time opening a piece fails.
            releaseResources(playbackController, midiData);
            JOptionPane.showMessageDialog(view, mode.errorTitle + ":\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static ViewMode configuredViewMode() {
        try {
            return ViewMode.valueOf(Settings.load().getDefaultViewMode());
        } catch (IllegalArgumentException e) {
            return ViewMode.FALLING;
        }
    }

    private static void releaseResources(PlaybackController controller, MidiFileService.MidiData midiData) {
        if (controller != null) {
            controller.dispose();
        } else if (midiData != null) {
            midiData.player().close();
        }
    }
}
package com.Tbence132545.Melodigram.view;

import com.Tbence132545.Melodigram.model.Settings;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Preferences: which MIDI keyboard to practise with, which SoundFont to play through, and which
 * view a piece opens in. Changes take effect the next time a piece is opened.
 */
public class SettingsWindow extends JDialog {

    private static final String ASK_EVERY_TIME = "Ask every time";
    private static final String BUNDLED_SOUNDFONT = "Bundled grand piano";

    private final Settings settings;
    private final JComboBox<String> inputDeviceBox = Theme.createComboBox();
    private final JComboBox<String> viewModeBox = Theme.createComboBox();
    private final JLabel soundfontLabel = new JLabel();
    private final List<String> inputDeviceNames = new ArrayList<>();

    private String soundfontPath;

    public SettingsWindow(Frame owner, Settings settings) {
        super(owner, "Settings", true);
        this.settings = settings;
        this.soundfontPath = settings.getSoundfontPath();

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(26, 30, 22, 30));

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setOpaque(false);
        fields.add(buildInputDeviceRow());
        fields.add(Box.createVerticalStrut(18));
        fields.add(buildSoundfontRow());
        fields.add(Box.createVerticalStrut(18));
        fields.add(buildViewModeRow());

        content.add(header(), BorderLayout.NORTH);
        content.add(fields, BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setMinimumSize(new Dimension(460, getHeight()));
        setLocationRelativeTo(owner);
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel title = new JLabel("Settings");
        title.setFont(Theme.font(Font.BOLD, 22));
        title.setForeground(Theme.TEXT_PRIMARY);
        panel.add(title, BorderLayout.WEST);
        return panel;
    }

    private JPanel buildInputDeviceRow() {
        inputDeviceBox.addItem(ASK_EVERY_TIME);
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                if (MidiSystem.getMidiDevice(info).getMaxTransmitters() != 0) {
                    inputDeviceNames.add(info.getName());
                    inputDeviceBox.addItem(info.getName());
                }
            } catch (MidiUnavailableException ignored) {
                // A device the system will not open is not one we can practise with.
            }
        }
        String saved = settings.getMidiInputDeviceName();
        inputDeviceBox.setSelectedItem(saved != null && inputDeviceNames.contains(saved) ? saved : ASK_EVERY_TIME);

        return row("MIDI keyboard",
                inputDeviceNames.isEmpty()
                        ? "No MIDI inputs are connected right now"
                        : "Used for practice instead of asking each time",
                inputDeviceBox);
    }

    private JPanel buildSoundfontRow() {
        soundfontLabel.setFont(Theme.font(Font.PLAIN, 13));
        soundfontLabel.setForeground(Theme.TEXT_MUTED);
        updateSoundfontLabel();

        JButton choose = Theme.createControlButton("Choose…", null, new Dimension(104, 34));
        choose.addActionListener(e -> chooseSoundfont());
        JButton reset = Theme.createControlButton("Use bundled", null, new Dimension(120, 34));
        reset.addActionListener(e -> {
            soundfontPath = null;
            updateSoundfontLabel();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(choose);
        buttons.add(reset);

        JPanel value = new JPanel();
        value.setLayout(new BoxLayout(value, BoxLayout.Y_AXIS));
        value.setOpaque(false);
        soundfontLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.add(soundfontLabel);
        value.add(Box.createVerticalStrut(8));
        value.add(buttons);

        return row("Instrument sound", "A .sf2 SoundFont to play through", value);
    }

    private JPanel buildViewModeRow() {
        for (ViewMode mode : ViewMode.values()) {
            viewModeBox.addItem(mode.label());
        }
        viewModeBox.setSelectedItem(currentViewMode().label());
        return row("Opens showing", "Which view a piece starts in", viewModeBox);
    }

    private ViewMode currentViewMode() {
        try {
            return ViewMode.valueOf(settings.getDefaultViewMode());
        } catch (IllegalArgumentException e) {
            return ViewMode.FALLING;
        }
    }

    private JPanel row(String labelText, String hintText, Component field) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        JLabel label = new JLabel(labelText);
        label.setFont(Theme.font(Font.BOLD, 14));
        label.setForeground(Theme.TEXT_PRIMARY);

        JLabel hint = new JLabel(hintText);
        hint.setFont(Theme.font(Font.PLAIN, 12));
        hint.setForeground(Theme.TEXT_MUTED);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(label, BorderLayout.NORTH);
        heading.add(hint, BorderLayout.SOUTH);

        panel.add(heading, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildButtons() {
        JButton cancel = Theme.createControlButton("Cancel", null, new Dimension(96, 38));
        cancel.addActionListener(e -> dispose());
        JButton save = Theme.createAccentButton("Save", new Dimension(110, 38));
        save.addActionListener(e -> {
            applyToSettings();
            settings.save();
            dispose();
        });

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setOpaque(false);
        panel.add(cancel);
        panel.add(save);
        return panel;
    }

    private void applyToSettings() {
        Object device = inputDeviceBox.getSelectedItem();
        settings.setMidiInputDeviceName(ASK_EVERY_TIME.equals(device) ? null : (String) device);
        settings.setSoundfontPath(soundfontPath);
        for (ViewMode mode : ViewMode.values()) {
            if (mode.label().equals(viewModeBox.getSelectedItem())) {
                settings.setDefaultViewMode(mode.name());
            }
        }
    }

    private void chooseSoundfont() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select a SoundFont");
        chooser.setFileFilter(new FileNameExtensionFilter("SoundFont files", "sf2"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            soundfontPath = chosen.getAbsolutePath();
            updateSoundfontLabel();
        }
    }

    private void updateSoundfontLabel() {
        if (soundfontPath == null) {
            soundfontLabel.setText(BUNDLED_SOUNDFONT);
            return;
        }
        Path path = Paths.get(soundfontPath);
        soundfontLabel.setText(path.getFileName().toString());
        soundfontLabel.setToolTipText(soundfontPath);
    }
}

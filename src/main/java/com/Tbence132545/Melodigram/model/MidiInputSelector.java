package com.Tbence132545.Melodigram.model;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MidiInputSelector extends JDialog {
    private final JComboBox<String> deviceComboBox;
    private final List<MidiDevice.Info> inputDevices = new ArrayList<>();
    private final Consumer<MidiDevice.Info> onDeviceSelected;

    public MidiInputSelector(JFrame parent, Consumer<MidiDevice.Info> onDeviceSelected) {
        super(parent, "Select MIDI Input Device", true);
        this.onDeviceSelected = onDeviceSelected;

        setSize(400, 120);
        setLayout(new FlowLayout());
        setLocationRelativeTo(parent);

        deviceComboBox = new JComboBox<>();
        loadMidiInputDevices();

        add(new JLabel("MIDI Input Devices:"));
        add(deviceComboBox);

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            int index = deviceComboBox.getSelectedIndex();
            if (index >= 0 && index < inputDevices.size()) {
                // Hide before invoking the callback: it opens the piano window, and a modal
                // dialog still on screen would block that window from being interacted with.
                setVisible(false);
                if (onDeviceSelected != null) {
                    onDeviceSelected.accept(inputDevices.get(index));
                }
            }
        });
        add(okBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> setVisible(false));
        add(cancelBtn);
    }
    private void loadMidiInputDevices() {
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        for (MidiDevice.Info info : infos) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                if (device.getMaxTransmitters() != 0) { // input device
                    inputDevices.add(info);
                    deviceComboBox.addItem(info.getName());
                }
            } catch (MidiUnavailableException ignored) {}
        }
        if (inputDevices.isEmpty()) {
            deviceComboBox.addItem("No MIDI input devices found");
            deviceComboBox.setEnabled(false);
        }
    }
}

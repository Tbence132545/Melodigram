package com.Tbence132545.Melodigram.view;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.DoubleConsumer;

/**
 * Steps playback through a set of preset speeds so a piece can be practised slowly.
 *
 * <p>Presets rather than a free slider: the useful values are few, and they stay repeatable
 * between sessions in a way that dragging to "about 0.7" does not.
 */
public class SpeedControl extends JPanel {

    private static final double[] SPEEDS = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    private static final int NORMAL_SPEED_INDEX = 3;

    private final JButton slowerButton;
    private final JButton fasterButton;
    private final JLabel valueLabel;

    private int speedIndex = NORMAL_SPEED_INDEX;
    private DoubleConsumer speedChangeListener;

    public SpeedControl() {
        super(new GridBagLayout());
        setOpaque(false);

        Dimension stepSize = new Dimension(34, 34);
        slowerButton = Theme.createControlButton("−", null, stepSize);
        slowerButton.setToolTipText("Slower");
        fasterButton = Theme.createControlButton("+", null, stepSize);
        fasterButton.setToolTipText("Faster");

        valueLabel = new JLabel("", SwingConstants.CENTER);
        valueLabel.setFont(Theme.font(Font.BOLD, 13));
        valueLabel.setForeground(Theme.TEXT_PRIMARY);
        valueLabel.setPreferredSize(new Dimension(52, 34));
        valueLabel.setToolTipText("Playback speed");

        slowerButton.addActionListener(e -> shiftSpeed(-1));
        fasterButton.addActionListener(e -> shiftSpeed(1));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 1, 0, 1);
        gbc.gridx = 0;
        add(slowerButton, gbc);
        gbc.gridx = 1;
        add(valueLabel, gbc);
        gbc.gridx = 2;
        add(fasterButton, gbc);

        updateDisplay();
    }

    /** Notified with the new factor, where 1.0 is the piece's written tempo. */
    public void setSpeedChangeListener(DoubleConsumer listener) {
        this.speedChangeListener = listener;
    }

    public double getSpeed() {
        return SPEEDS[speedIndex];
    }

    private void shiftSpeed(int direction) {
        int next = Math.max(0, Math.min(SPEEDS.length - 1, speedIndex + direction));
        if (next == speedIndex) {
            return;
        }
        speedIndex = next;
        updateDisplay();
        if (speedChangeListener != null) {
            speedChangeListener.accept(getSpeed());
        }
    }

    private void updateDisplay() {
        valueLabel.setText(format(getSpeed()) + "×");
        valueLabel.setForeground(speedIndex == NORMAL_SPEED_INDEX ? Theme.TEXT_MUTED : Theme.ACCENT_HOVER);
        slowerButton.setEnabled(speedIndex > 0);
        fasterButton.setEnabled(speedIndex < SPEEDS.length - 1);
    }

    private static String format(double speed) {
        return (speed == Math.rint(speed))
                ? String.valueOf((int) speed)
                : String.valueOf(speed);
    }
}

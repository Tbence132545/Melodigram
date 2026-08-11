package com.Tbence132545.Melodigram.view;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;

public class MainWindow extends JFrame {

    private static final Dimension PRIMARY_BUTTON_SIZE = new Dimension(280, 62);
    private static final Dimension SECONDARY_BUTTON_SIZE = new Dimension(134, 46);

    private final JButton playButton = Theme.createAccentButton("Play", PRIMARY_BUTTON_SIZE);
    private final JButton settingsButton = Theme.createControlButton("Settings", null, SECONDARY_BUTTON_SIZE);
    private final JButton quitButton = Theme.createControlButton("Quit", null, SECONDARY_BUTTON_SIZE);

    public MainWindow() {
        setTitle("Melodigram");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(820, 640));
        setLayout(new BorderLayout());

        JPanel background = createBackgroundPanel();
        background.add(createCentreColumn(), centredConstraints());
        add(background, BorderLayout.CENTER);
    }

    /** A soft vertical wash instead of flat black, so the page has some depth. */
    private JPanel createBackgroundPanel() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(26, 27, 32),
                        0, getHeight(), Theme.BACKGROUND));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        panel.setBackground(Theme.BACKGROUND);
        return panel;
    }

    private JPanel createCentreColumn() {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);

        Wordmark wordmark = new Wordmark("MELODIGRAM");
        wordmark.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(wordmark);
        column.add(Box.createVerticalStrut(38));

        playButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(playButton);
        column.add(Box.createVerticalStrut(14));

        // Settings and Quit read as secondary, so Play is clearly the way in.
        JPanel secondary = new JPanel();
        secondary.setLayout(new BoxLayout(secondary, BoxLayout.X_AXIS));
        secondary.setOpaque(false);
        secondary.setAlignmentX(Component.CENTER_ALIGNMENT);
        secondary.add(settingsButton);
        secondary.add(Box.createHorizontalStrut(12));
        secondary.add(quitButton);
        column.add(secondary);

        column.add(Box.createVerticalStrut(30));
        column.add(createHint());
        return column;
    }

    private JLabel createHint() {
        JLabel hint = new JLabel("Connect a MIDI keyboard to practise along", SwingConstants.CENTER);
        hint.setFont(Theme.font(Font.PLAIN, 13));
        hint.setForeground(new Color(112, 115, 124));
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        return hint;
    }

    private static GridBagConstraints centredConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        return gbc;
    }

    public void addPlayButtonListener(ActionListener listener) {
        playButton.addActionListener(listener);
    }

    public void addSettingsButtonListener(ActionListener listener) {
        settingsButton.addActionListener(listener);
    }

    public void addQuitButtonListener(ActionListener listener) {
        quitButton.addActionListener(listener);
    }
}

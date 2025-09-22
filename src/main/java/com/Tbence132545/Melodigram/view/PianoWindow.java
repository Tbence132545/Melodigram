// java
package com.Tbence132545.Melodigram.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class PianoWindow extends JFrame {

    private static final Font CONTROL_BUTTON_FONT = new Font("SansSerif", Font.BOLD, 18);
    private static final Font PIANO_LABEL_FONT = new Font("SansSerif", Font.BOLD, 14);
    private static final Color COLOR_CONTROL_PANEL_BG = new Color(45, 45, 45);
    private static final Color COLOR_CONTROL_BUTTON_BG = new Color(60, 60, 60);
    private static final Color COLOR_CONTROL_BUTTON_HOVER = new Color(80, 80, 80);
    private static final Color COLOR_WHITE_KEY_HIGHLIGHT = new Color(255, 200, 100);
    private static final Color COLOR_BLACK_KEY_HIGHLIGHT = Color.RED;

    private enum KeyType {
        WHITE, BLACK;
        private static final KeyType[] MIDI_PATTERN = {WHITE, BLACK, WHITE, BLACK, WHITE, WHITE, BLACK, WHITE, BLACK, WHITE, BLACK, WHITE};
        public static KeyType fromMidiNote(int midiNote) {
            return MIDI_PATTERN[midiNote % 12];
        }
    }

    private final JLayeredPane pianoPanel;
    private final AnimationPanel animationPanel;
    private final JButton playButton;
    private final JButton backButton;
    private final JButton backwardButton;
    private final JButton forwardButton;
    private final JButton saveButton;
    private SeekBar seekBar;

    private final Map<Integer, JButton> noteToKeyButton = new HashMap<>();
    private final int lowestNote;
    private final int highestNote;
    private int whiteKeyWidth = 50;
    private int blackKeyWidth = 30;
    private static final int WHITE_KEY_HEIGHT = 150;
    private static final int BLACK_KEY_HEIGHT = 100;

    public PianoWindow(int lowestNote, int highestNote) {
        this.lowestNote = Math.max(lowestNote, 0);
        this.highestNote = Math.min(highestNote, 127);

        initializeFrame();

        JPanel controlPanel = createControlPanel(this.playButton = new JButton("||"),
                this.backButton = new JButton("←"),
                this.backwardButton = new JButton("<<"),
                this.forwardButton = new JButton(">>"),
                this.saveButton = new JButton("Save"));

        this.pianoPanel = createPianoPanel();
        this.animationPanel = new AnimationPanel(this::getKeyInfo, this.lowestNote, this.highestNote);

        JPanel pianoWithLine = createPianoWithLinePanel();

        add(controlPanel, BorderLayout.NORTH);
        add(animationPanel, BorderLayout.CENTER);
        add(pianoWithLine, BorderLayout.SOUTH);

        setupComponentListeners();
        updatePianoKeys();
    }

    private void initializeFrame() {
        setTitle("Piano Visualization");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(new Color(230, 230, 230));
    }
    private JPanel createControlPanel(JButton play, JButton back, JButton backward, JButton forward, JButton save) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(COLOR_CONTROL_PANEL_BG);

        Dimension buttonSize = new Dimension(50, 50);
        MouseAdapter hoverEffect = createHoverEffect();

        styleControlButton(back, buttonSize, hoverEffect);
        styleControlButton(backward, buttonSize, hoverEffect);
        styleControlButton(play, buttonSize, hoverEffect);
        styleControlButton(forward, buttonSize, hoverEffect);

        styleControlButton(save, new Dimension(80, 50), hoverEffect);
        save.setVisible(false);

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0;
        gbc.gridheight = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(back, gbc);

        JPanel centerButtonPanel = new JPanel(new GridBagLayout());
        centerButtonPanel.setOpaque(false);
        GridBagConstraints cbc = new GridBagConstraints();
        cbc.fill = GridBagConstraints.VERTICAL;
        centerButtonPanel.add(backward, cbc);
        centerButtonPanel.add(play, cbc);
        centerButtonPanel.add(forward, cbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel.add(centerButtonPanel, gbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridheight = 2;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(save, gbc);

        return panel;
    }

    private JLayeredPane createPianoPanel() {
        JLayeredPane panel = new JLayeredPane();
        panel.setPreferredSize(new Dimension(800, WHITE_KEY_HEIGHT));
        return panel;
    }

    private JPanel createPianoWithLinePanel() {
        JPanel redLinePanel = new JPanel();
        redLinePanel.setBackground(Color.RED);
        redLinePanel.setPreferredSize(new Dimension(0, 3));

        JPanel pianoWithLine = new JPanel(new BorderLayout());
        pianoWithLine.add(redLinePanel, BorderLayout.NORTH);
        pianoWithLine.add(pianoPanel, BorderLayout.CENTER);
        return pianoWithLine;
    }

    private void setupComponentListeners() {
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                updatePianoKeys();
            }
        });
    }


    private void updatePianoKeys() {
        pianoPanel.removeAll();
        noteToKeyButton.clear();

        int whiteKeyCount = countWhiteKeys();
        if (whiteKeyCount == 0) return;

        int panelWidth = pianoPanel.getWidth() > 0 ? pianoPanel.getWidth() : getWidth();
        whiteKeyWidth = panelWidth / whiteKeyCount;
        blackKeyWidth = (int) (whiteKeyWidth * 0.6);
        //I'm drawing the middle C notes' notation based on the range
        int middleCNote = findMiddleCNote();

        int whiteKeyIndex = 0;
        for (int i = lowestNote; i <= highestNote; i++) {
            KeyType keyType = KeyType.fromMidiNote(i);
            JButton keyButton = createKeyButton(keyType);

            if (keyType == KeyType.WHITE) {
                addWhiteKey(keyButton, whiteKeyIndex, i == middleCNote,i);
                whiteKeyIndex++;
            } else {
                addBlackKey(keyButton, whiteKeyIndex);
            }
            noteToKeyButton.put(i, keyButton);
        }

        pianoPanel.revalidate();
        pianoPanel.repaint();
    }

    private int countWhiteKeys() {
        int count = 0;
        for (int i = lowestNote; i <= highestNote; i++) {
            if (KeyType.fromMidiNote(i) == KeyType.WHITE) {
                count++;
            }
        }
        return count;
    }

    private int findMiddleCNote() {
        int midNote = (lowestNote + highestNote) / 2;
        int closestC = -1;
        int minDiff = Integer.MAX_VALUE;
        for (int i = lowestNote; i <= highestNote; i++) {
            if (i % 12 == 0) { // C notes are multiples of 12
                int diff = Math.abs(i - midNote);
                if (diff < minDiff) {
                    minDiff = diff;
                    closestC = i;
                }
            }
        }
        return closestC;
    }

    private void addWhiteKey(JButton keyButton, int whiteKeyIndex, boolean isMiddleC, int midiNote) {
        keyButton.setBounds(whiteKeyIndex * whiteKeyWidth, 0, whiteKeyWidth, WHITE_KEY_HEIGHT);

        if (isMiddleC) {
            int octave = (midiNote / 12) - 1;
            JLabel label = new JLabel("C" + octave, SwingConstants.CENTER);
            label.setFont(PIANO_LABEL_FONT);
            label.setOpaque(false);
            label.setForeground(Color.DARK_GRAY);

            keyButton.setLayout(new BorderLayout());
            keyButton.add(label, BorderLayout.SOUTH);
        }

        pianoPanel.add(keyButton, JLayeredPane.DEFAULT_LAYER);
    }

    private void addBlackKey(JButton keyButton, int precedingWhiteKeyIndex) {
        int x = (precedingWhiteKeyIndex - 1) * whiteKeyWidth + (whiteKeyWidth - blackKeyWidth / 2);
        keyButton.setBounds(x, 0, blackKeyWidth, BLACK_KEY_HEIGHT);
        pianoPanel.add(keyButton, JLayeredPane.PALETTE_LAYER);
    }


    public void highlightNote(int midiNote) {
        setKeyColor(midiNote, true);
    }

    public void releaseNote(int midiNote) {
        setKeyColor(midiNote, false);
    }

    public void releaseAllKeys() {
        noteToKeyButton.keySet().forEach(this::releaseNote);
        pianoPanel.repaint();
    }

    public void addSeekBar(JComponent seekBarComponent) {
        this.seekBar = (SeekBar) seekBarComponent;
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 5, 5, 5);
        // Add to the control panel, not the frame
        ((JPanel)getContentPane().getComponent(0)).add(seekBarComponent, gbc);
    }
    public void setEditingMode(boolean isEditing){
        saveButton.setVisible(isEditing);
    }
    public void disableButtons(boolean shouldDisable) {
        playButton.setEnabled(!shouldDisable);
        backwardButton.setEnabled(!shouldDisable);
        forwardButton.setEnabled(!shouldDisable);
        if (seekBar != null) {
            seekBar.setEnabled(!shouldDisable);
            seekBar.setUserInteractionEnabled(!shouldDisable);
        }
    }

    public void setPlayButtonText(String text) {
        playButton.setText(text);
    }

    public AnimationPanel getAnimationPanel() {
        return animationPanel;
    }

    public record KeyInfo(boolean isBlack, int x, int width) {}

    public KeyInfo getKeyInfo(int midiNote) {
        JButton key = noteToKeyButton.get(midiNote);
        if (key == null) return null;
        boolean isBlack = (KeyType.fromMidiNote(midiNote) == KeyType.BLACK);
        return new KeyInfo(isBlack, key.getX(), key.getWidth());
    }

    public boolean isBlackKey(int midiNote) {
        return KeyType.fromMidiNote(midiNote) == KeyType.BLACK;
    }

    public void setPlayButtonListener(ActionListener listener) { playButton.addActionListener(listener); }
    public void setBackButtonListener(ActionListener listener) { backButton.addActionListener(listener); }
    public void setBackwardButtonListener(ActionListener listener) { backwardButton.addActionListener(listener); }
    public void setForwardButtonListener(ActionListener listener) { forwardButton.addActionListener(listener); }
    public void setSaveButtonListener(ActionListener listener) { saveButton.addActionListener(listener); }


    private void styleControlButton(JButton button, Dimension size, MouseAdapter hoverEffect) {
        button.setPreferredSize(null);
        button.setFont(CONTROL_BUTTON_FONT);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setForeground(Color.WHITE);
        button.setBackground(COLOR_CONTROL_BUTTON_BG);
        button.addMouseListener(hoverEffect);
    }

    private MouseAdapter createHoverEffect() {
        return new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                e.getComponent().setBackground(COLOR_CONTROL_BUTTON_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                e.getComponent().setBackground(COLOR_CONTROL_BUTTON_BG);
            }
        };
    }

    private JButton createKeyButton(KeyType keyType) {
        JButton keyButton = new JButton();
        keyButton.setFocusable(false);
        keyButton.setOpaque(true);
        keyButton.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        keyButton.setBackground(keyType == KeyType.WHITE ? Color.WHITE : Color.BLACK);
        return keyButton;
    }


    private void setKeyColor(int midiNote, boolean isHighlighted) {
        JButton key = noteToKeyButton.get(midiNote);
        if (key == null) return;

        KeyType keyType = KeyType.fromMidiNote(midiNote);
        if (isHighlighted) {

            Color assignedColor = animationPanel.getAssignedHighlightColor(midiNote);

            if (assignedColor != null) {
                key.setBackground(assignedColor);
            } else {
                key.setBackground(keyType == KeyType.WHITE ? COLOR_WHITE_KEY_HIGHLIGHT : COLOR_BLACK_KEY_HIGHLIGHT);
            }

        } else {
            key.setBackground(keyType == KeyType.WHITE ? Color.WHITE : Color.BLACK);
        }
        key.repaint();
    }
}
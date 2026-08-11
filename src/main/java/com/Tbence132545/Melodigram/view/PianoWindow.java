package com.Tbence132545.Melodigram.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class PianoWindow extends JFrame {

    private static final Font PIANO_LABEL_FONT = Theme.font(Font.BOLD, 13);
    private static final Color COLOR_WHITE_KEY = new Color(248, 248, 250);
    private static final Color COLOR_BLACK_KEY = new Color(22, 22, 26);
    private static final Color COLOR_WHITE_KEY_HIGHLIGHT = new Color(255, 196, 92);
    private static final Color COLOR_BLACK_KEY_HIGHLIGHT = new Color(224, 76, 76);
    private static final Color COLOR_KEY_BORDER = new Color(40, 40, 46);

    private static final Dimension TRANSPORT_BUTTON_SIZE = new Dimension(46, 46);
    private static final int PLAYHEAD_LINE_HEIGHT = 3;

    private final ImageIcon playIcon = loadIcon("play.png", 22);
    private final ImageIcon pauseIcon = loadIcon("pause.png", 22);
    private final ImageIcon backIcon = loadIcon("back.png", 22);
    private final ImageIcon backwardIcon = loadIcon("backward.png", 22);
    private final ImageIcon forwardIcon = loadIcon("forward.png", 22);

    private enum KeyType {
        WHITE, BLACK;
        private static final KeyType[] MIDI_PATTERN = {WHITE, BLACK, WHITE, BLACK, WHITE, WHITE, BLACK, WHITE, BLACK, WHITE, BLACK, WHITE};
        public static KeyType fromMidiNote(int midiNote) {
            return MIDI_PATTERN[midiNote % 12];
        }
    }

    private static final int SHEET_STRIP_HEIGHT = 285;

    private final JPanel controlPanel; // Field for direct access
    private JPanel scoreArea;
    private final JLayeredPane pianoPanel;
    private final AnimationPanel animationPanel;
    private final SheetMusicPanel sheetMusicPanel = new SheetMusicPanel();
    private final ViewModeControl viewModeControl = new ViewModeControl();
    private ViewMode viewMode = ViewMode.FALLING;
    private final JButton playButton;
    private final JButton backButton;
    private final JButton backwardButton;
    private final JButton forwardButton;
    private final JButton saveButton;
    private SeekBar seekBar;
    private final JButton toggleNotation;
    private final SpeedControl speedControl = new SpeedControl();
    private Consumer<Boolean> notationToggleListener;

    private final Map<Integer, JButton> noteToKeyButton = new HashMap<>();
    private final int lowestNote;
    private final int highestNote;
    private int whiteKeyWidth = 50;
    private int blackKeyWidth = 30;
    private static final int WHITE_KEY_HEIGHT = 150;
    private static final int BLACK_KEY_HEIGHT = 100;
    private boolean isNotationEnabled = false;

    public PianoWindow(int lowestNote, int highestNote) {
        this.lowestNote = Math.max(lowestNote, 0);
        this.highestNote = Math.min(highestNote, 127);

        initializeFrame();
        this.toggleNotation = createNotationToggle();

        this.controlPanel = createControlPanel(
                this.playButton = Theme.createControlButton(null, pauseIcon, TRANSPORT_BUTTON_SIZE),
                this.backButton = Theme.createControlButton(null, backIcon, TRANSPORT_BUTTON_SIZE),
                this.backwardButton = Theme.createControlButton(null, backwardIcon, TRANSPORT_BUTTON_SIZE),
                this.forwardButton = Theme.createControlButton(null, forwardIcon, TRANSPORT_BUTTON_SIZE),
                this.saveButton = Theme.createControlButton("Save", null, new Dimension(78, 34))
        );
        playButton.setToolTipText("Play / pause");
        backButton.setToolTipText("Back to the file list");
        backwardButton.setToolTipText("Back 10 seconds");
        forwardButton.setToolTipText("Forward 10 seconds");

        this.pianoPanel = createPianoPanel();
        this.animationPanel = new AnimationPanel(this::getKeyInfo, this.lowestNote, this.highestNote);

        JPanel pianoWithLine = createPianoWithLinePanel();

        add(controlPanel, BorderLayout.NORTH);
        add(createScoreArea(), BorderLayout.CENTER);
        add(pianoWithLine, BorderLayout.SOUTH);

        viewModeControl.setViewModeListener(this::setViewMode);
        applyViewMode();
        setupComponentListeners();
        updatePianoKeys();
    }

    private JPanel createScoreArea() {
        scoreArea = new JPanel(new BorderLayout());
        scoreArea.setBackground(Theme.BACKGROUND);
        return scoreArea;
    }

    public void setViewMode(ViewMode mode) {
        this.viewMode = mode;
        viewModeControl.setSelected(mode);
        applyViewMode();
    }

    /**
     * With both views on, the sheet is a fixed strip above the falling notes so they still flow
     * down into the keyboard. On its own it takes the whole area, so the staff is as large as
     * possible.
     */
    private void applyViewMode() {
        scoreArea.removeAll();
        if (viewMode.showsSheetMusic() && viewMode.showsFallingNotes()) {
            sheetMusicPanel.setPreferredSize(new Dimension(0, SHEET_STRIP_HEIGHT));
            scoreArea.add(sheetMusicPanel, BorderLayout.NORTH);
            scoreArea.add(animationPanel, BorderLayout.CENTER);
        } else if (viewMode.showsSheetMusic()) {
            scoreArea.add(sheetMusicPanel, BorderLayout.CENTER);
        } else {
            scoreArea.add(animationPanel, BorderLayout.CENTER);
        }
        scoreArea.revalidate();
        scoreArea.repaint();
    }

    public SheetMusicPanel getSheetMusicPanel() {
        return sheetMusicPanel;
    }

    private void initializeFrame() {
        setTitle("Melodigram");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(Theme.BACKGROUND);
    }

    /**
     * Lays the transport out in three columns. The side columns carry all the weight and
     * declare no preferred width, so the spare space splits evenly between them and the
     * transport cluster lands on the true centre of the window. Sizing the side columns to
     * their contents instead — as this did before — pushed the cluster off-centre by however
     * much the two groups of buttons differed in width.
     */
    private JPanel createControlPanel(JButton play, JButton back, JButton backward, JButton forward, JButton save) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        save.setVisible(false);

        JPanel leftGroup = createSideGroup(FlowLayout.LEFT);
        leftGroup.add(back);
        leftGroup.add(viewModeControl);

        JPanel centerGroup = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerGroup.setOpaque(false);
        centerGroup.add(backward);
        centerGroup.add(play);
        centerGroup.add(forward);

        JPanel rightGroup = createSideGroup(FlowLayout.RIGHT);
        rightGroup.add(speedControl);
        rightGroup.add(save);
        rightGroup.add(toggleNotation);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        panel.add(leftGroup, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(centerGroup, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(rightGroup, gbc);

        return panel;
    }

    /** A side column that claims no width of its own, so both sides stay symmetric. */
    private JPanel createSideGroup(int flowAlignment) {
        JPanel group = new JPanel(new FlowLayout(flowAlignment, 8, 0)) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, super.getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, super.getMinimumSize().height);
            }
        };
        group.setOpaque(false);
        return group;
    }

    private JLayeredPane createPianoPanel() {
        JLayeredPane panel = new JLayeredPane();
        panel.setPreferredSize(new Dimension(800, WHITE_KEY_HEIGHT));
        return panel;
    }

    private JButton createNotationToggle() {
        JButton notationButton = Theme.createControlButton("Notation", null, new Dimension(104, 34), () -> isNotationEnabled);
        notationButton.setToolTipText("Show note names on the falling notes");
        notationButton.addActionListener(e -> {
            isNotationEnabled = !isNotationEnabled;
            notationButton.repaint();
            if (notationToggleListener != null) {
                notationToggleListener.accept(isNotationEnabled);
            }
        });
        return notationButton;
    }

    private JPanel createPianoWithLinePanel() {
        JPanel playheadLine = new JPanel();
        playheadLine.setBackground(Theme.ACCENT);
        playheadLine.setPreferredSize(new Dimension(0, PLAYHEAD_LINE_HEIGHT));

        JPanel pianoWithLine = new JPanel(new BorderLayout());
        pianoWithLine.setBackground(Theme.BACKGROUND);
        pianoWithLine.add(playheadLine, BorderLayout.NORTH);
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
    private ImageIcon loadIcon(String path, int size) {
        java.net.URL url = getClass().getResource("/images/" + path);
        if (url == null) {
            throw new IllegalArgumentException("Icon not found: " + path);
        }
        Image img = new ImageIcon(url).getImage();
        Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /** Notified with the new state whenever the user toggles the notation overlay. */
    public void setNotationToggleListener(Consumer<Boolean> listener) {
        this.notationToggleListener = listener;
    }

    /**
     * The set of keys never changes, so resizing only repositions them. Rebuilding the buttons
     * on every resize event would also discard whichever keys are currently highlighted.
     */
    private void updatePianoKeys() {
        int whiteKeyCount = countWhiteKeys();
        if (whiteKeyCount == 0) return;

        if (noteToKeyButton.isEmpty()) {
            createKeyButtons();
        }
        layoutKeys(whiteKeyCount);
    }

    private void createKeyButtons() {
        int middleCNote = findMiddleCNote();
        for (int midiNote = lowestNote; midiNote <= highestNote; midiNote++) {
            KeyType keyType = KeyType.fromMidiNote(midiNote);
            JButton keyButton = createKeyButton(keyType);

            if (keyType == KeyType.WHITE) {
                if (midiNote == middleCNote) {
                    addMiddleCLabel(keyButton, midiNote);
                }
                pianoPanel.add(keyButton, JLayeredPane.DEFAULT_LAYER);
            } else {
                pianoPanel.add(keyButton, JLayeredPane.PALETTE_LAYER);
            }
            noteToKeyButton.put(midiNote, keyButton);
        }
    }

    private void layoutKeys(int whiteKeyCount) {
        int panelWidth = pianoPanel.getWidth() > 0 ? pianoPanel.getWidth() : getWidth();
        whiteKeyWidth = panelWidth / whiteKeyCount;
        blackKeyWidth = (int) (whiteKeyWidth * 0.6);

        int whiteKeyIndex = 0;
        for (int midiNote = lowestNote; midiNote <= highestNote; midiNote++) {
            JButton keyButton = noteToKeyButton.get(midiNote);
            if (KeyType.fromMidiNote(midiNote) == KeyType.WHITE) {
                keyButton.setBounds(whiteKeyIndex * whiteKeyWidth, 0, whiteKeyWidth, WHITE_KEY_HEIGHT);
                whiteKeyIndex++;
            } else {
                // A black key straddles the boundary between the white keys either side of it.
                int x = whiteKeyIndex * whiteKeyWidth - blackKeyWidth / 2;
                keyButton.setBounds(x, 0, blackKeyWidth, BLACK_KEY_HEIGHT);
            }
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

    private void addMiddleCLabel(JButton keyButton, int midiNote) {
        int octave = (midiNote / 12) - 1;
        JLabel label = new JLabel("C" + octave, SwingConstants.CENTER);
        label.setFont(PIANO_LABEL_FONT);
        label.setOpaque(false);
        label.setForeground(new Color(120, 122, 130));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        keyButton.setLayout(new BorderLayout());
        keyButton.add(label, BorderLayout.SOUTH);
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

    public void addSeekBar(SeekBar seekBarComponent) {
        this.seekBar = seekBarComponent;

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 2, 0, 2);

        this.controlPanel.add(seekBarComponent, gbc);
    }

    /** Notified with the new playback speed, where 1.0 is the piece's written tempo. */
    public void setSpeedChangeListener(DoubleConsumer listener) {
        speedControl.setSpeedChangeListener(listener);
    }

    public void setEditingMode(boolean isEditing) {
        saveButton.setVisible(isEditing);
    }

    public void disableButtons(boolean shouldDisable) {
        playButton.setEnabled(!shouldDisable);
        backwardButton.setEnabled(!shouldDisable);
        forwardButton.setEnabled(!shouldDisable);
        if (seekBar != null) {
            seekBar.setEnabled(!shouldDisable);
        }
    }

    public void setPlayButtonIcon(boolean isPlaying) {
        playButton.setIcon(isPlaying ? pauseIcon : playIcon);
    }

    public AnimationPanel getAnimationPanel() {
        return animationPanel;
    }

    public record KeyInfo(boolean isBlack, int x, int width) {
    }

    public KeyInfo getKeyInfo(int midiNote) {
        JButton key = noteToKeyButton.get(midiNote);
        if (key == null) return null;
        boolean isBlack = (KeyType.fromMidiNote(midiNote) == KeyType.BLACK);
        return new KeyInfo(isBlack, key.getX(), key.getWidth());
    }

    public boolean isBlackKey(int midiNote) {
        return KeyType.fromMidiNote(midiNote) == KeyType.BLACK;
    }

    public void setPlayButtonListener(ActionListener listener) {
        playButton.addActionListener(listener);
    }

    public void setBackButtonListener(ActionListener listener) {
        backButton.addActionListener(listener);
    }

    public void setBackwardButtonListener(ActionListener listener) {
        backwardButton.addActionListener(listener);
    }

    public void setForwardButtonListener(ActionListener listener) {
        forwardButton.addActionListener(listener);
    }

    public void setSaveButtonListener(ActionListener listener) {
        saveButton.addActionListener(listener);
    }

    /**
     * Keys are painted rather than tinted with {@code setBackground}: look-and-feels that draw
     * their own button bezel would otherwise ignore the highlight colour entirely.
     */
    private JButton createKeyButton(KeyType keyType) {
        JButton keyButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(COLOR_KEY_BORDER);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        keyButton.setFocusable(false);
        keyButton.setContentAreaFilled(false);
        keyButton.setBorderPainted(false);
        keyButton.setOpaque(false);
        keyButton.setBackground(keyType == KeyType.WHITE ? COLOR_WHITE_KEY : COLOR_BLACK_KEY);
        return keyButton;
    }

    private void setKeyColor(int midiNote, boolean isHighlighted) {
        JButton key = noteToKeyButton.get(midiNote);
        if (key == null) return;

        KeyType keyType = KeyType.fromMidiNote(midiNote);
        if (isHighlighted) {
            Color assignedColor = animationPanel.getAssignedHighlightColor(midiNote);
            key.setBackground(assignedColor != null
                    ? assignedColor
                    : (keyType == KeyType.WHITE ? COLOR_WHITE_KEY_HIGHLIGHT : COLOR_BLACK_KEY_HIGHLIGHT));
        } else {
            key.setBackground(keyType == KeyType.WHITE ? COLOR_WHITE_KEY : COLOR_BLACK_KEY);
        }
        key.repaint();
    }
}
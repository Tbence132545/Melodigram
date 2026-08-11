package com.Tbence132545.Melodigram.view;

import com.Tbence132545.Melodigram.model.Hand;
import com.Tbence132545.Melodigram.model.ScoreNote;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

public class AnimationPanel extends JPanel {

    /** The persisted form of an assignment; kept as a String because it is written to JSON. */
    public static class HandAssignment {
        public final int midiNote;
        public final long on;
        public final long off;
        public final String hand;

        public HandAssignment(int midiNote, long on, long off, String hand) {
            this.midiNote = midiNote;
            this.on = on;
            this.off = off;
            this.hand = hand;
        }
    }

    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /** How much music is on screen at once; also fixes how fast the notes travel. */
    private static final long NOTE_FALL_DURATION_MS = 2000;

    /** Shortest note the eye can still pick out, whatever its real duration. */
    private static final int MIN_NOTE_HEIGHT_PX = 12;

    /** Scrubbing sensitivity, kept independent of note length so dragging feels unchanged. */
    private static final double DRAG_PIXELS_PER_MILLISECOND = 0.1;

    private static final long ASSIGNMENT_MATCH_TOLERANCE_MS = 5;
    private static final int NOTE_CORNER_RADIUS = 10;
    private static final Color COLOR_GRID_LINE = new Color(78, 80, 88, 130);
    private static final Color COLOR_BLACK_NOTE = new Color(226, 96, 96, 225);
    private static final Color COLOR_WHITE_NOTE = new Color(240, 190, 74, 225);
    private static final Color COLOR_LEFT_WHITE = new Color(135, 206, 250, 220); // Light Sky Blue
    private static final Color COLOR_LEFT_BLACK = new Color(25, 25, 112, 220);   // Midnight Blue
    private static final Color COLOR_RIGHT_WHITE = new Color(250, 128, 114, 220); // Salmon
    private static final Color COLOR_RIGHT_BLACK = new Color(178, 34, 34, 220);  // Firebrick


    private static final Font NOTE_TEXT_FONT = Theme.font(Font.BOLD, 14);
    private static final Color NOTE_TEXT_COLOR = new Color(28, 24, 20);

    /** Shared with the sheet view, so a hand assigned here shows up there too. EDT only. */
    private List<ScoreNote> notes = Collections.emptyList();
    private final Function<Integer, PianoWindow.KeyInfo> keyInfoProvider;
    private long currentTimeMillis = 0;
    private long totalDurationMillis = 0;
    private final int lowestNote;
    private final int highestNote;
    private boolean isHandAssignmentEnabled = false;
    private boolean isNotationEnabled = false;
    private ListWindow.MidiFileActionListener.HandMode practiceFilterMode = ListWindow.MidiFileActionListener.HandMode.BOTH;


    private Runnable onDragStart;
    private LongConsumer onTimeChange;
    private Runnable onDragEnd;
    private Runnable onHandAssigned;

    public AnimationPanel(Function<Integer, PianoWindow.KeyInfo> keyInfoProvider, int lowestNote, int highestNote) {
        this.keyInfoProvider = keyInfoProvider;
        this.lowestNote = lowestNote;
        this.highestNote = highestNote;

        setBackground(Color.BLACK);

        MouseInteractionHandler mouseHandler = new MouseInteractionHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void setNotes(List<ScoreNote> notes) {
        this.notes = notes;
        repaint();
    }

    public void setPracticeFilterMode(ListWindow.MidiFileActionListener.HandMode mode) {
        this.practiceFilterMode = mode;
        repaint();
    }

    public void setNotationEnabled(boolean enabled) {
        this.isNotationEnabled = enabled;
        repaint();
    }

    public List<HandAssignment> getAssignedNotes() {
        return notes.stream()
                .filter(note -> note.hand() != null)
                .map(note -> new HandAssignment(
                        note.midiNote(), note.onMillis(), note.offMillis(), note.hand().name()))
                .collect(Collectors.toList());
    }

    public void applyHandAssignments(List<HandAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return;

        for (HandAssignment assignment : assignments) {
            Hand hand = parseHand(assignment.hand);
            if (hand == null) continue;
            for (ScoreNote note : notes) {
                if (note.midiNote() == assignment.midiNote
                        && Math.abs(note.onMillis() - assignment.on) <= ASSIGNMENT_MATCH_TOLERANCE_MS
                        && Math.abs(note.offMillis() - assignment.off) <= ASSIGNMENT_MATCH_TOLERANCE_MS) {
                    note.setHand(hand);
                }
            }
        }
        repaint();
    }

    private static Hand parseHand(String name) {
        try {
            return Hand.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    public void setHandAssignmentMode(boolean enabled) {
        this.isHandAssignmentEnabled = enabled;
        repaint();
    }

    public void setTotalDurationMillis(long totalDurationMillis) {
        this.totalDurationMillis = Math.max(0, totalDurationMillis);
    }

    public void setOnDragStart(Runnable onDragStart) { this.onDragStart = onDragStart; }
    public void setOnTimeChange(LongConsumer onTimeChange) { this.onTimeChange = onTimeChange; }
    public void setOnDragEnd(Runnable onDragEnd) { this.onDragEnd = onDragEnd; }

    /** Notified after a click assigns a hand, so other views can refresh. */
    public void setOnHandAssigned(Runnable onHandAssigned) { this.onHandAssigned = onHandAssigned; }

    public long getCurrentTimeMillis() {
        return currentTimeMillis;
    }

    /**
     * Pixels of travel per millisecond of music. Derived from the panel height so the visible
     * area always covers {@link #NOTE_FALL_DURATION_MS} of the piece, at any window size.
     */
    private static double scrollSpeed(int panelHeight) {
        return (double) Math.max(1, panelHeight) / NOTE_FALL_DURATION_MS;
    }

    public void tick(long deltaMillis) {
        currentTimeMillis += deltaMillis;
        repaint();
    }

    public void updatePlaybackTime(long timeMillis) {
        this.currentTimeMillis = timeMillis;
        repaint();
    }

    public List<Integer> getNotesStartingBetween(long startMs, long endMs, ListWindow.MidiFileActionListener.HandMode handMode) {
        List<Integer> onsets = new ArrayList<>();
        if (endMs < startMs) return onsets;

        for (ScoreNote note : notes) {
            boolean isWithinTime = note.onMillis() > startMs && note.onMillis() <= endMs;
            if (isWithinTime && matchesHandFilter(note, handMode)) {
                onsets.add(note.midiNote());
            }
        }
        return onsets;
    }

    public static String midiToNoteName(int midiNumber) {
        if (midiNumber < 0 || midiNumber > 127) {
            return "";
        }
        return NOTE_NAMES[midiNumber % 12] + ((midiNumber / 12) - 1);
    }

    public Color getAssignedHighlightColor(int midiNote) {
        long now = currentTimeMillis;
        for (int i = notes.size() - 1; i >= 0; i--) {
            ScoreNote note = notes.get(i);
            if (note.midiNote() == midiNote && note.isSoundingAt(now) && note.hand() != null) {
                Color color = colorForHand(note.hand(), note.isBlackKey());
                return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGridLines(g2d);

        int panelHeight = getHeight();
        for (ScoreNote note : notes) {
            Rectangle bounds = noteBounds(note, panelHeight);
            if (bounds != null) {
                drawNote(g2d, note, bounds);
            }
        }
    }


    private void drawGridLines(Graphics2D g2d) {
        g2d.setColor(COLOR_GRID_LINE);
        for (int midiNote = lowestNote; midiNote <= highestNote; midiNote++) {
            if (midiNote % 12 == 0) { // Draw a line at the start of every C key
                PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(midiNote);
                if (keyInfo != null && !keyInfo.isBlack()) {
                    g2d.drawLine(keyInfo.x(), 0, keyInfo.x(), getHeight());
                }
            }
        }
    }

    /**
     * Where a note sits right now, or null when it is filtered out or off screen. Drawing and
     * hit-testing both go through this, so a click always lands on what is actually visible.
     */
    private Rectangle noteBounds(ScoreNote note, int panelHeight) {
        if (!shouldBeDrawnForPractice(note)) {
            return null;
        }
        PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(note.midiNote());
        if (keyInfo == null) {
            return null;
        }
        double pixelsPerMillisecond = scrollSpeed(panelHeight);
        int noteHeight = noteHeight(note, pixelsPerMillisecond);
        // The bottom edge reaches the keyboard exactly at onMillis, which is the moment the
        // key should be pressed.
        int bottomY = (int) Math.round(panelHeight + (currentTimeMillis - note.onMillis()) * pixelsPerMillisecond);
        int topY = bottomY - noteHeight;
        if (bottomY <= 0 || topY >= panelHeight) {
            return null;
        }
        return new Rectangle(keyInfo.x(), topY, keyInfo.width(), noteHeight);
    }

    /**
     * A note is as long as the distance it travels while sounding, so its length reads directly
     * as how long the key is held. Very short notes get a floor so they stay visible; that
     * stretches their tail slightly past the release, but the bottom edge — the part that tells
     * you when to press — stays exact.
     */
    private static int noteHeight(ScoreNote note, double pixelsPerMillisecond) {
        int scaled = (int) Math.round((note.offMillis() - note.onMillis()) * pixelsPerMillisecond);
        return Math.max(MIN_NOTE_HEIGHT_PX, scaled);
    }

    private boolean shouldBeDrawnForPractice(ScoreNote note) {
        return matchesHandFilter(note, practiceFilterMode);
    }

    private static boolean matchesHandFilter(ScoreNote note, ListWindow.MidiFileActionListener.HandMode handMode) {
        return switch (handMode) {
            case LEFT -> note.hand() == Hand.LEFT;
            case RIGHT -> note.hand() == Hand.RIGHT;
            case BOTH -> true;
        };
    }

    private void drawNote(Graphics2D g, ScoreNote note, Rectangle bounds) {
        // Keep the corners from swallowing the shortest notes entirely.
        int radius = Math.min(NOTE_CORNER_RADIUS, bounds.height / 2);
        Color body = noteColor(note);
        g.setColor(body);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, radius, radius);
        // A brighter edge separates notes that touch or overlap on the same key.
        g.setColor(body.brighter());
        g.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, radius, radius);

        String label = noteLabel(note);
        if (!label.isEmpty()) {
            drawNoteLabel(g, label, bounds);
        }
    }

    private Color noteColor(ScoreNote note) {
        if (note.hand() != null) {
            return colorForHand(note.hand(), note.isBlackKey());
        }
        return note.isBlackKey() ? COLOR_BLACK_NOTE : COLOR_WHITE_NOTE;
    }

    private static Color colorForHand(Hand hand, boolean isBlackKey) {
        if (hand == Hand.LEFT) {
            return isBlackKey ? COLOR_LEFT_BLACK : COLOR_LEFT_WHITE;
        }
        return isBlackKey ? COLOR_RIGHT_BLACK : COLOR_RIGHT_WHITE;
    }

    /** Note names take precedence over the hand marker when notation is switched on. */
    private String noteLabel(ScoreNote note) {
        if (isNotationEnabled) {
            return midiToNoteName(note.midiNote());
        }
        if (isHandAssignmentEnabled && note.hand() != null) {
            return (note.hand() == Hand.LEFT) ? "L" : "R";
        }
        return "";
    }

    private void drawNoteLabel(Graphics2D g, String text, Rectangle bounds) {
        g.setFont(NOTE_TEXT_FONT);
        g.setColor(NOTE_TEXT_COLOR);
        FontMetrics metrics = g.getFontMetrics();
        int textX = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
        g.drawString(text, textX, Theme.centeredBaseline(metrics, bounds.y, bounds.height));
    }

    /** Assigns {@code hand} to the topmost note drawn under {@code point}, if any. */
    private void assignHandAt(Point point, Hand hand) {
        int panelHeight = getHeight();
        for (int i = notes.size() - 1; i >= 0; i--) {
            ScoreNote note = notes.get(i);
            Rectangle bounds = noteBounds(note, panelHeight);
            if (bounds != null && bounds.contains(point)) {
                note.setHand(hand);
                repaint();
                if (onHandAssigned != null) {
                    onHandAssigned.run();
                }
                return;
            }
        }
    }

    /**
     * Handles both gestures the panel supports, because they start identically: a press that
     * moves scrubs the timeline, a press that does not is a click that assigns a hand. Splitting
     * these across two listeners made every scrub in hand-assignment mode also relabel whichever
     * note happened to sit under the starting point.
     */
    private class MouseInteractionHandler extends MouseAdapter {
        private static final int DRAG_THRESHOLD_PX = 4;

        private boolean isDragging = false;
        private boolean movedBeyondThreshold = false;
        private Hand pressedHand = null;
        private Point pressPoint = new Point();
        private long pressTime = 0;

        @Override
        public void mousePressed(MouseEvent e) {
            isDragging = true;
            movedBeyondThreshold = false;
            // The button masks are cleared by the time the release arrives, so record it now.
            pressedHand = handForButton(e);
            pressPoint = e.getPoint();
            pressTime = currentTimeMillis;
            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            if (onDragStart != null) onDragStart.run();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!isDragging) return;

            int dy = e.getY() - pressPoint.y;
            if (Math.abs(dy) > DRAG_THRESHOLD_PX) {
                movedBeyondThreshold = true;
            }
            long newTime = pressTime - (long) (dy / DRAG_PIXELS_PER_MILLISECOND);
            newTime = Math.max(0, Math.min(newTime, totalDurationMillis));

            updatePlaybackTime(newTime);
            if (onTimeChange != null) onTimeChange.accept(newTime);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!isDragging) return;
            isDragging = false;
            setCursor(Cursor.getDefaultCursor());
            if (!movedBeyondThreshold && isHandAssignmentEnabled && pressedHand != null) {
                assignHandAt(pressPoint, pressedHand);
            }
            if (onDragEnd != null) onDragEnd.run();
        }

        /** @return the hand the pressed button assigns, or null for any other button. */
        private Hand handForButton(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                return Hand.RIGHT;
            }
            return SwingUtilities.isLeftMouseButton(e) ? Hand.LEFT : null;
        }
    }
}

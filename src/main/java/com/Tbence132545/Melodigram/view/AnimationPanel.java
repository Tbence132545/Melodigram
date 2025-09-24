package com.Tbence132545.Melodigram.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

public class AnimationPanel extends JPanel {
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
    private static final double PIXELS_PER_MILLISECOND = 0.1;
    private static final long NOTE_FALL_DURATION_MS = 2000;
    private static final int NOTE_CORNER_RADIUS = 10;
    private static final Color COLOR_GRID_LINE = new Color(100, 100, 100, 150);
    private static final Color COLOR_BLACK_NOTE = new Color(255, 100, 100, 180);
    private static final Color COLOR_WHITE_NOTE = new Color(255, 215, 0, 180);
    private static final Color COLOR_LEFT_WHITE = new Color(135, 206, 250, 220); // Light Sky Blue
    private static final Color COLOR_LEFT_BLACK = new Color(25, 25, 112, 220);   // Midnight Blue
    private static final Color COLOR_RIGHT_WHITE = new Color(250, 128, 114, 220); // Salmon
    private static final Color COLOR_RIGHT_BLACK = new Color(178, 34, 34, 220);  // Firebrick

    private static final Font NOTE_TEXT_FONT = new Font("SansSerif", Font.BOLD, 16);
    private static final Color NOTE_TEXT_COLOR = Color.WHITE;

    private final List<FallingNote> notes = new CopyOnWriteArrayList<>();
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

    public AnimationPanel(Function<Integer, PianoWindow.KeyInfo> keyInfoProvider, int lowestNote, int highestNote) {
        this.keyInfoProvider = keyInfoProvider;
        this.lowestNote = lowestNote;
        this.highestNote = highestNote;

        setBackground(Color.BLACK);

        TimelineDragHandler dragHandler = new TimelineDragHandler();
        addMouseListener(dragHandler);
        addMouseMotionListener(dragHandler);

        NoteClickHandler clickHandler = new NoteClickHandler();
        addMouseListener(clickHandler);
    }

    public void setPracticeFilterMode(ListWindow.MidiFileActionListener.HandMode mode) {
        this.practiceFilterMode = mode;
        repaint();
    }
    public void setNotationEnabled(boolean enabled) {
        this.isNotationEnabled = enabled;
    }
    public List<HandAssignment> getAssignedNotes() {
        return notes.stream()
                .filter(note -> note.hand != null)
                .map(note -> new HandAssignment(
                        note.midiNote,
                        note.noteOnTime,
                        note.noteOffTime,
                        note.hand.name()
                ))
                .collect(Collectors.toList());
    }

    public void applyHandAssignments(List<HandAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return;
        final long TOL_MS = 5;

        for (HandAssignment a : assignments) {
            for (FallingNote n : notes) {
                if (n.midiNote == a.midiNote
                        && Math.abs(n.noteOnTime - a.on) <= TOL_MS
                        && Math.abs(n.noteOffTime - a.off) <= TOL_MS) {
                    try {
                        n.setHand(FallingNote.Hands.valueOf(a.hand));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        repaint();
    }

    public void setHandAssignmentMode(boolean enabled) {
        this.isHandAssignmentEnabled = enabled;
    }

    public void setTotalDurationMillis(long totalDurationMillis) {
        this.totalDurationMillis = Math.max(0, totalDurationMillis);
    }

    public void setOnDragStart(Runnable onDragStart) { this.onDragStart = onDragStart; }
    public void setOnTimeChange(LongConsumer onTimeChange) { this.onTimeChange = onTimeChange; }
    public void setOnDragEnd(Runnable onDragEnd) { this.onDragEnd = onDragEnd; }

    public long getCurrentTimeMillis() {
        return currentTimeMillis;
    }

    public void tick(long deltaMillis) {
        currentTimeMillis += deltaMillis;
        repaint();
    }

    public void updatePlaybackTime(long timeMillis) {
        this.currentTimeMillis = timeMillis;
    }

    public void addFallingNote(int midiNote, long noteOnTime, long noteOffTime, boolean isBlackKey) {
        notes.add(new FallingNote(midiNote, noteOnTime, noteOffTime, isBlackKey));
    }

    public List<Integer> getNotesStartingBetween(long startMs, long endMs, ListWindow.MidiFileActionListener.HandMode handMode) {
        List<Integer> onsets = new ArrayList<>();
        if (endMs < startMs) return onsets;

        for (FallingNote note : notes) {
            boolean isWithinTime = note.noteOnTime > startMs && note.noteOnTime <= endMs;
            if (isWithinTime && note.matchesHandFilter(handMode)) {
                onsets.add(note.midiNote);
            }
        }
        return onsets;
    }
    public static String midiToNoteName(int midiNumber) {
        if (midiNumber < 0 || midiNumber > 127) {
            return "";
        }

        int noteIndex = midiNumber % 12;
        int octave = (midiNumber / 12) - 1;

        String noteName = NOTE_NAMES[noteIndex];

        return noteName + octave;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGridLines(g2d);

        for (FallingNote note : notes) {
            note.draw(g2d, currentTimeMillis, getHeight());
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

    public Color getAssignedHighlightColor(int midiNote) {
        long t = getCurrentTimeMillis();
        for (int i = notes.size() - 1; i >= 0; i--) {
            FallingNote n = notes.get(i);
            if (n.midiNote == midiNote && t >= n.noteOnTime && t < n.noteOffTime && n.hand != null) {
                Color semiTransparentColor = colorForHighlight(n);
                return new Color(semiTransparentColor.getRed(), semiTransparentColor.getGreen(), semiTransparentColor.getBlue(), 255);
            }
        }
        return null;
    }

    private Color colorForHighlight(FallingNote n) {
        boolean isLeft = (n.hand == FallingNote.Hands.LEFT);
        boolean isBlack = n.isBlackKey;
        return isLeft ? (isBlack ? COLOR_LEFT_BLACK : COLOR_LEFT_WHITE)
                : (isBlack ? COLOR_RIGHT_BLACK : COLOR_RIGHT_WHITE);
    }

    private class NoteClickHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (!isHandAssignmentEnabled) {
                return;
            }

            for (int i = notes.size() - 1; i >= 0; i--) {
                FallingNote note = notes.get(i);
                if (note.getBounds().contains(e.getPoint())) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        note.setHand(FallingNote.Hands.RIGHT);
                    } else if (SwingUtilities.isLeftMouseButton(e)) {
                        note.setHand(FallingNote.Hands.LEFT);
                    }
                    repaint();
                    return;
                }
            }
        }
    }

    private class TimelineDragHandler extends MouseAdapter {
        private boolean isDragging = false;
        private int pressY = 0;
        private long pressTime = 0;

        @Override
        public void mousePressed(MouseEvent e) {
            isDragging = true;
            pressY = e.getY();
            pressTime = currentTimeMillis;
            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            if (onDragStart != null) onDragStart.run();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!isDragging) return;

            int dy = e.getY() - pressY;
            long timeDelta = (long) (dy / PIXELS_PER_MILLISECOND);
            long newTime = pressTime - timeDelta;

            newTime = Math.max(0, Math.min(newTime, totalDurationMillis));

            updatePlaybackTime(newTime);
            repaint();
            if (onTimeChange != null) onTimeChange.accept(newTime);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!isDragging) return;
            isDragging = false;
            setCursor(Cursor.getDefaultCursor());
            if (onDragEnd != null) onDragEnd.run();
        }
    }

    private class FallingNote {
        private enum Hands {
            LEFT, RIGHT
        }
        private final int midiNote;
        private final long noteOnTime;
        private final long noteOffTime;
        private final boolean isBlackKey;
        private Hands hand = null;
        private final Rectangle bounds = new Rectangle();

        public FallingNote(int midiNote, long on, long off, boolean isBlackKey) {
            this.midiNote = midiNote;
            this.noteOnTime = on;
            this.noteOffTime = off;
            this.isBlackKey = isBlackKey;
        }

        public void setHand(Hands hand) { this.hand = hand; }
        public Rectangle getBounds() { return this.bounds; }

        public boolean matchesHandFilter(ListWindow.MidiFileActionListener.HandMode handMode) {
            switch (handMode) {
                case LEFT:  return this.hand == Hands.LEFT;
                case RIGHT: return this.hand == Hands.RIGHT;
                case BOTH:
                default:    return true;
            }
        }

        void draw(Graphics2D g, long currentMillis, int panelHeight) {
            if (!shouldBeDrawnForPractice()) {
                bounds.setBounds(0, 0, 0, 0);
                return;
            }

            if (!isVisibleOnScreen(currentMillis)) {
                bounds.setBounds(0, 0, 0, 0);
                return;
            }

            PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(midiNote);
            if (keyInfo == null) return;

            int noteHeight = (int) ((noteOffTime - noteOnTime) * PIXELS_PER_MILLISECOND);
            int topY = calculateTopY(currentMillis, noteHeight, panelHeight);
            bounds.setBounds(keyInfo.x(), topY, keyInfo.width(), noteHeight);

            if (topY < panelHeight && (topY + noteHeight) > 0) {
                drawNoteBody(g, noteHeight);
                String text = "";
                if (isHandAssignmentEnabled && hand != null) {
                    text= (hand == Hands.LEFT) ? "L" : "R";
                }
                if(isNotationEnabled) {
                    text = midiToNoteName(midiNote);
                }
                drawHandText(g, text);
            }
        }

        private boolean shouldBeDrawnForPractice() {
            if (practiceFilterMode == ListWindow.MidiFileActionListener.HandMode.LEFT && hand != Hands.LEFT) {
                return false;
            }
            if (practiceFilterMode == ListWindow.MidiFileActionListener.HandMode.RIGHT && hand != Hands.RIGHT) {
                return false;
            }
            return true;
        }

        private boolean isVisibleOnScreen(long currentMillis) {
            long fallStartTime = noteOnTime - NOTE_FALL_DURATION_MS;
            return currentMillis >= fallStartTime && currentMillis <= noteOffTime;
        }

        private int calculateTopY(long currentMillis, int noteHeight, int panelHeight) {
            long fallStartTime = noteOnTime - NOTE_FALL_DURATION_MS;
            int bottomY = (currentMillis < noteOnTime)
                    ? calculateFallingY(currentMillis, fallStartTime, noteHeight, panelHeight)
                    : calculateSinkingY(currentMillis, noteHeight, panelHeight);
            return bottomY - noteHeight;
        }

        private Color determineNoteColor() {
            if (hand == Hands.LEFT) {
                return isBlackKey ? COLOR_LEFT_BLACK : COLOR_LEFT_WHITE;
            } else if (hand == Hands.RIGHT) {
                return isBlackKey ? COLOR_RIGHT_BLACK : COLOR_RIGHT_WHITE;
            } else {
                return isBlackKey ? COLOR_BLACK_NOTE : COLOR_WHITE_NOTE;
            }
        }

        private void drawNoteBody(Graphics2D g, int noteHeight) {
            g.setColor(determineNoteColor());
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, noteHeight, NOTE_CORNER_RADIUS, NOTE_CORNER_RADIUS);
        }

        private void drawHandText(Graphics2D g, String text) {
            g.setFont(NOTE_TEXT_FONT);
            g.setColor(NOTE_TEXT_COLOR);
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent();
            int textX = bounds.x + (bounds.width - textWidth) / 2;
            int textY = bounds.y + (bounds.height + textHeight) / 2;
            g.drawString(text, textX, textY);
        }

        private int calculateFallingY(long currentMillis, long fallStartTime, int noteHeight, int panelHeight) {
            double progress = (double) (currentMillis - fallStartTime) / NOTE_FALL_DURATION_MS;
            int startY = -noteHeight;
            int endY = panelHeight;
            return (int) (startY + progress * (endY - startY));
        }

        private int calculateSinkingY(long currentMillis, int noteHeight, int panelHeight) {
            long noteDuration = noteOffTime - noteOnTime;
            if (noteDuration <= 0) return panelHeight;

            double progress = (double) (currentMillis - noteOnTime) / noteDuration;
            int startY = panelHeight;
            int endY = panelHeight + noteHeight;
            return (int) (startY + progress * (endY - startY));
        }
    }
}
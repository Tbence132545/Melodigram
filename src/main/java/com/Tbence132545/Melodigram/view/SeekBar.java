package com.Tbence132545.Melodigram.view;

import javax.sound.midi.Sequencer;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class SeekBar extends JComponent {

    private static final int TRACK_HEIGHT = 6;
    private static final int THUMB_RADIUS = 7;
    private static final int TIME_LABEL_WIDTH = 52;
    private static final int TIME_LABEL_GAP = 10;

    private static final Color COLOR_TRACK = new Color(58, 60, 67);
    private static final Color COLOR_TRACK_DISABLED = new Color(44, 45, 50);
    private static final Color COLOR_FILL_DISABLED = new Color(92, 78, 78);

    private double progress = 0.0;
    private boolean dragging = false;
    private boolean hovering = false;
    private long durationMicros = 1;
    private final Sequencer sequencer;
    private SeekListener seekListener;

    public interface SeekListener {
        void onSeek(long newMicroseconds);
    }

    public SeekBar(Sequencer sequencer) {
        this.sequencer = sequencer;
        setPreferredSize(new Dimension(600, 28));
        setFont(Theme.font(Font.PLAIN, 12));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isEnabled()) return;
                dragging = true;
                progress = progressAt(e.getX());
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (hovering != isEnabled()) {
                    hovering = isEnabled();
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                dragging = true;
                progress = progressAt(e.getX());
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isEnabled()) return;
                if (sequencer != null && sequencer.getSequence() != null) {
                    long newTime = (long) (progress * durationMicros);
                    try {
                        sequencer.setMicrosecondPosition(newTime);
                    } catch (Exception ignored) {
                        // A sequencer that rejects the position simply stays where it is.
                    }
                    if (seekListener != null) {
                        seekListener.onSeek(newTime);
                    }
                }
                dragging = false;
                repaint();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                repaint();
            }
        });

        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setSeekListener(SeekListener listener) {
        this.seekListener = listener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            dragging = false;
            hovering = false;
        }
        repaint();
    }

    public void updateProgress() {
        if (dragging || sequencer == null || sequencer.getSequence() == null) {
            return;
        }
        // A sequence with no measurable length would otherwise make progress NaN.
        durationMicros = Math.max(1, sequencer.getMicrosecondLength());
        progress = clamp((double) sequencer.getMicrosecondPosition() / durationMicros);
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (!isEnabled()) return null;
        return formatMicros((long) (progressAt(e.getX()) * durationMicros));
    }

    /** The track is inset by the time labels on either side. */
    private int trackLeft() {
        return TIME_LABEL_WIDTH + TIME_LABEL_GAP;
    }

    private int trackWidth() {
        return Math.max(1, getWidth() - 2 * (TIME_LABEL_WIDTH + TIME_LABEL_GAP));
    }

    private double progressAt(int mouseX) {
        return clamp((double) (mouseX - trackLeft()) / trackWidth());
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private String formatMicros(long micros) {
        long totalSeconds = Math.max(0, micros) / 1_000_000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean enabled = isEnabled();
        int trackLeft = trackLeft();
        int trackWidth = trackWidth();
        int centerY = getHeight() / 2;
        int trackY = centerY - TRACK_HEIGHT / 2;
        int filledWidth = (int) Math.round(trackWidth * progress);

        g2.setColor(enabled ? COLOR_TRACK : COLOR_TRACK_DISABLED);
        g2.fillRoundRect(trackLeft, trackY, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        g2.setColor(enabled ? Theme.ACCENT : COLOR_FILL_DISABLED);
        g2.fillRoundRect(trackLeft, trackY, filledWidth, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        // The handle only appears on hover or while scrubbing, so the bar stays calm at rest.
        if (enabled && (hovering || dragging)) {
            int thumbX = trackLeft + filledWidth;
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.fillOval(thumbX - THUMB_RADIUS, centerY - THUMB_RADIUS, THUMB_RADIUS * 2, THUMB_RADIUS * 2);
        }

        drawTimeLabels(g2, trackLeft, trackWidth, centerY, enabled);
        g2.dispose();
    }

    private void drawTimeLabels(Graphics2D g2, int trackLeft, int trackWidth, int centerY, boolean enabled) {
        g2.setFont(getFont());
        g2.setColor(enabled ? Theme.TEXT_MUTED : Theme.TEXT_DISABLED);
        FontMetrics metrics = g2.getFontMetrics();
        int baseline = centerY - metrics.getHeight() / 2 + metrics.getAscent();

        String elapsed = formatMicros((long) (progress * durationMicros));
        String total = formatMicros(durationMicros);
        g2.drawString(elapsed, trackLeft - TIME_LABEL_GAP - metrics.stringWidth(elapsed), baseline);
        g2.drawString(total, trackLeft + trackWidth + TIME_LABEL_GAP, baseline);
    }
}

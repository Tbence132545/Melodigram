package com.Tbence132545.Melodigram.view;

import javax.sound.midi.Sequencer;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

public class SeekBar extends JComponent {
    private double progress = 0.0;
    private boolean dragging = false;
    private long durationMicros = 1;
    private final Sequencer sequencer;
    private SeekListener seekListener;
    private volatile boolean userInteractionEnabled = true;

    public interface SeekListener {
        void onSeek(long newMicroseconds);
    }

    public SeekBar(Sequencer sequencer) {
        this.sequencer = sequencer;

        setPreferredSize(new Dimension(600, 50));

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isInteractionAllowed()) return;
                dragging = true;
                progress = clamp((double) e.getX() / getWidth());
                repaint();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isInteractionAllowed()) return;
                dragging = true;
                progress = clamp((double) e.getX() / getWidth());
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isInteractionAllowed()) return;
                if (sequencer != null && sequencer.getSequence() != null) {
                    long newTime = (long) (progress * durationMicros);
                    try {
                        sequencer.setMicrosecondPosition(newTime);
                    } catch (Exception ignored) {
                    }
                    if (seekListener != null) {
                        seekListener.onSeek(newTime);
                    }
                }
                dragging = false;
                repaint();
            }
        });

        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setSeekListener(SeekListener listener) {
        this.seekListener = listener;
    }

    public void setUserInteractionEnabled(boolean enabled) {
        this.userInteractionEnabled = enabled;
        super.setEnabled(enabled);
        if (!enabled) {
            dragging = false;
        }
        repaint();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            dragging = false;
        }
        repaint();
    }

    public void updateProgress() {
        if (!dragging && sequencer != null && sequencer.getSequence() != null) {
            durationMicros = sequencer.getMicrosecondLength();
            long currentMicros = sequencer.getMicrosecondPosition();
            progress = clamp((double) currentMicros / durationMicros);
            repaint();
        }
    }

    private boolean isInteractionAllowed() {
        return isEnabled() && userInteractionEnabled;
    }

    private double clamp(double val) {
        return Math.max(0, Math.min(1, val));
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (!isInteractionAllowed()) return null;
        double hoverRatio = (double) e.getX() / Math.max(1, getWidth());
        long hoverMicros = (long) (hoverRatio * durationMicros);
        return formatMicros(hoverMicros);
    }

    private String formatMicros(long micros) {
        long seconds = micros / 1_000_000;
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());

        int barHeight = (int) (height * 0.60);
        if (barHeight < 6) barHeight = 6;
        int barY = height - barHeight;

        boolean disabled = !isEnabled() || !userInteractionEnabled;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg = disabled ? new Color(80, 80, 80) : Color.DARK_GRAY;
        Color fg = disabled ? new Color(140, 100, 100) : new Color(255, 102, 102);
        Color thumb = disabled ? new Color(200, 200, 200, 140) : Color.WHITE;

        g2.setColor(bg);
        g2.fillRoundRect(0, barY, width, barHeight, 4, 4);

        int filledWidth = (int) (width * progress);
        g2.setColor(fg);
        g2.fillRoundRect(0, barY, filledWidth, barHeight, 4, 4);

        g2.setColor(thumb);
        g2.fillRect(filledWidth - 1, barY - (barHeight / 2), 2, barHeight * 2);

        g2.dispose();
    }
}
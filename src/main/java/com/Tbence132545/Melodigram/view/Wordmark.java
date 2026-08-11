package com.Tbence132545.Melodigram.view;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * The Melodigram mark, drawn rather than loaded from an image.
 *
 * <p>Keeps the original idea — a play triangle cut out of piano keys — but as vectors, so it
 * stays sharp at any size, carries no baked-in background to show as a box against the window,
 * and takes its colours from the theme.
 */
public class Wordmark extends JComponent {

    private static final int MARK_SIZE = 132;
    private static final int GAP_BELOW_MARK = 26;
    private static final int TITLE_SIZE = 40;
    private static final int WHITE_KEY_COUNT = 7;

    private static final Color KEY_WHITE = new Color(244, 245, 248);
    private static final Color KEY_BLACK = new Color(18, 18, 22);
    private static final Color TITLE = new Color(248, 248, 252);

    private final String title;

    public Wordmark(String title) {
        this.title = title;
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(460, MARK_SIZE + GAP_BELOW_MARK + TITLE_SIZE + 12);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int centreX = getWidth() / 2;
        drawMark(g2, centreX, 0);
        drawText(g2, centreX, MARK_SIZE + GAP_BELOW_MARK);

        g2.dispose();
    }

    /** A play triangle whose face is made of piano keys. */
    private void drawMark(Graphics2D g2, int centreX, int top) {
        int size = MARK_SIZE;
        int left = centreX - size / 2;

        Path2D triangle = new Path2D.Double();
        triangle.moveTo(left, top);
        triangle.lineTo(left + size, top + size / 2.0);
        triangle.lineTo(left, top + size);
        triangle.closePath();

        Shape previousClip = g2.getClip();
        g2.clip(triangle);

        double keyWidth = size / (double) WHITE_KEY_COUNT;
        for (int i = 0; i < WHITE_KEY_COUNT; i++) {
            g2.setColor(KEY_WHITE);
            g2.fill(new Rectangle2D.Double(left + i * keyWidth + keyWidth * 0.08, top,
                    keyWidth * 0.84, size));
        }
        // Black keys follow the real pattern: none between E–F and B–C.
        boolean[] hasBlackKeyAfter = {true, true, false, true, true, true, false};
        for (int i = 0; i < WHITE_KEY_COUNT; i++) {
            if (!hasBlackKeyAfter[i]) {
                continue;
            }
            g2.setColor(KEY_BLACK);
            g2.fill(new Rectangle2D.Double(left + (i + 1) * keyWidth - keyWidth * 0.26, top,
                    keyWidth * 0.52, size * 0.58));
        }

        g2.setClip(previousClip);

        g2.setColor(Theme.ACCENT);
        g2.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(triangle);
    }

    private void drawText(Graphics2D g2, int centreX, int top) {
        g2.setFont(Theme.font(Font.BOLD, TITLE_SIZE));
        g2.setColor(TITLE);
        int titleWidth = letterSpacedWidth(g2, title, 6);
        drawLetterSpaced(g2, title, centreX - titleWidth / 2, top + g2.getFontMetrics().getAscent(), 6);
    }

    /** Wide letter spacing is what gives the mark its typographic feel. */
    private void drawLetterSpaced(Graphics2D g2, String text, int x, int baseline, int spacing) {
        FontMetrics metrics = g2.getFontMetrics();
        int cursor = x;
        for (char c : text.toCharArray()) {
            g2.drawString(String.valueOf(c), cursor, baseline);
            cursor += metrics.charWidth(c) + spacing;
        }
    }

    private int letterSpacedWidth(Graphics2D g2, String text, int spacing) {
        FontMetrics metrics = g2.getFontMetrics();
        int width = 0;
        for (char c : text.toCharArray()) {
            width += metrics.charWidth(c) + spacing;
        }
        return Math.max(0, width - spacing);
    }
}

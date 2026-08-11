package com.Tbence132545.Melodigram.view;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The shared palette, fonts and control styling.
 *
 * <p>Buttons are painted here rather than configured through {@code setBackground}, because
 * several look-and-feels — the macOS one in particular — ignore the background of a standard
 * button and draw their own native bezel instead, which left the controls looking mismatched.
 */
public final class Theme {

    public static final Color BACKGROUND = new Color(14, 14, 16);
    public static final Color SURFACE = new Color(28, 29, 33);
    public static final Color SURFACE_RAISED = new Color(48, 50, 56);
    public static final Color SURFACE_HOVER = new Color(66, 69, 77);
    public static final Color SURFACE_PRESSED = new Color(38, 40, 45);
    public static final Color ACCENT = new Color(206, 66, 66);
    public static final Color ACCENT_HOVER = new Color(226, 88, 88);
    public static final Color ACCENT_PRESSED = new Color(170, 48, 48);
    public static final Color TEXT_PRIMARY = new Color(242, 242, 246);
    public static final Color TEXT_MUTED = new Color(150, 153, 162);
    public static final Color TEXT_DISABLED = new Color(96, 99, 106);

    public static final int CONTROL_RADIUS = 10;
    public static final int PILL_RADIUS = 16;

    private static final String[] PREFERRED_FAMILIES = {
            "Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Roboto", "DejaVu Sans"
    };
    private static final String FAMILY = resolveFamily();

    private Theme() {
    }

    public static Font font(int style, int size) {
        return new Font(FAMILY, style, size);
    }

    /** Baseline that vertically centres one line of text in a box of {@code height}. */
    public static int centeredBaseline(FontMetrics metrics, int top, int height) {
        return top + (height - metrics.getHeight()) / 2 + metrics.getAscent();
    }

    public static void drawCenteredString(Graphics2D g, String text, int width, int height) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, (width - metrics.stringWidth(text)) / 2, centeredBaseline(metrics, 0, height));
    }

    /** A flat dark control, used for the transport bar and its neighbours. */
    public static JButton createControlButton(String text, Icon icon, Dimension size) {
        return createControlButton(text, icon, size, () -> false);
    }

    /**
     * @param active reports whether the button should render in its "on" colour, for controls
     *               such as the notation toggle that stay lit while enabled.
     */
    public static JButton createControlButton(String text, Icon icon, Dimension size, BooleanSupplier active) {
        JButton button = new JButton(text, icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(controlBackground(this, active.getAsBoolean()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), CONTROL_RADIUS, CONTROL_RADIUS);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setForeground(TEXT_PRIMARY);
        button.setFont(font(Font.BOLD, 13));
        applyCommonButtonSettings(button, size);
        return button;
    }

    /** The large rounded accent button used on the menu and file list. */
    public static JButton createAccentButton(String text, Dimension size) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentBackground(this));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), PILL_RADIUS, PILL_RADIUS);
                g2.setColor(isEnabled() ? TEXT_PRIMARY : TEXT_DISABLED);
                g2.setFont(getFont());
                drawCenteredString(g2, getText(), getWidth(), getHeight());
                g2.dispose();
            }
        };
        button.setFont(font(Font.BOLD, 16));
        applyCommonButtonSettings(button, size);
        return button;
    }

    /**
     * A dropdown that honours the dark theme. The look-and-feel's own combo box paints a native
     * light control and ignores the colours set on it, so a basic delegate is installed and
     * painted here instead.
     */
    public static JComboBox<String> createComboBox() {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(TEXT_MUTED);
                        int midX = getWidth() / 2;
                        int midY = getHeight() / 2;
                        g2.fillPolygon(new int[]{midX - 4, midX + 4, midX},
                                new int[]{midY - 2, midY - 2, midY + 3}, 3);
                        g2.dispose();
                    }
                };
                arrow.setBorderPainted(false);
                arrow.setContentAreaFilled(false);
                arrow.setFocusPainted(false);
                return arrow;
            }
        });
        comboBox.setFont(font(Font.PLAIN, 13));
        comboBox.setForeground(TEXT_PRIMARY);
        comboBox.setBackground(SURFACE_RAISED);
        comboBox.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 8));
        comboBox.setFocusable(false);
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setBackground(selected ? SURFACE_HOVER : SURFACE_RAISED);
                setForeground(TEXT_PRIMARY);
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                setFont(font(Font.PLAIN, 13));
                return this;
            }
        });
        return comboBox;
    }

    private static void applyCommonButtonSettings(JButton button, Dimension size) {
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        // Several look-and-feels reserve a wide default margin, which ate enough of these
        // fixed-width controls to truncate their labels to an ellipsis.
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder());
        // BoxLayout positions children by this, and stacks them off-centre without it.
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (size != null) {
            button.setPreferredSize(size);
            button.setMinimumSize(size);
            button.setMaximumSize(size);
        }
    }

    private static Color controlBackground(AbstractButton button, boolean active) {
        if (!button.isEnabled()) {
            return SURFACE;
        }
        if (active) {
            return button.getModel().isRollover() ? ACCENT_HOVER : ACCENT;
        }
        if (button.getModel().isPressed()) {
            return SURFACE_PRESSED;
        }
        return button.getModel().isRollover() ? SURFACE_HOVER : SURFACE_RAISED;
    }

    private static Color accentBackground(AbstractButton button) {
        if (!button.isEnabled()) {
            return SURFACE_RAISED;
        }
        if (button.getModel().isPressed()) {
            return ACCENT_PRESSED;
        }
        return button.getModel().isRollover() ? ACCENT_HOVER : ACCENT;
    }

    private static String resolveFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : PREFERRED_FAMILIES) {
            if (available.contains(family)) {
                return family;
            }
        }
        return Font.SANS_SERIF;
    }
}

package com.Tbence132545.Melodigram.view;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** A segmented control for choosing between the falling notes, the sheet music, or both. */
public class ViewModeControl extends JPanel {

    private static final Dimension BUTTON_SIZE = new Dimension(62, 34);

    private final Map<ViewMode, JButton> buttons = new EnumMap<>(ViewMode.class);
    private ViewMode selected = ViewMode.FALLING;
    private Consumer<ViewMode> viewModeListener;

    public ViewModeControl() {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setOpaque(false);

        for (ViewMode mode : ViewMode.values()) {
            JButton button = Theme.createControlButton(mode.label(), null, BUTTON_SIZE, () -> selected == mode);
            button.setFont(Theme.font(Font.BOLD, 12));
            button.setToolTipText(tooltipFor(mode));
            button.addActionListener(e -> select(mode));
            buttons.put(mode, button);
            add(button);
        }
    }

    public void setViewModeListener(Consumer<ViewMode> listener) {
        this.viewModeListener = listener;
    }

    public ViewMode getSelected() {
        return selected;
    }

    /** Reflects a mode chosen elsewhere, without firing the listener back. */
    public void setSelected(ViewMode mode) {
        selected = mode;
        buttons.values().forEach(JButton::repaint);
    }

    private void select(ViewMode mode) {
        if (mode == selected) {
            return;
        }
        setSelected(mode);
        if (viewModeListener != null) {
            viewModeListener.accept(mode);
        }
    }

    private static String tooltipFor(ViewMode mode) {
        return switch (mode) {
            case FALLING -> "Falling notes only";
            case SHEET -> "Sheet music only";
            case BOTH -> "Sheet music above the falling notes";
        };
    }
}

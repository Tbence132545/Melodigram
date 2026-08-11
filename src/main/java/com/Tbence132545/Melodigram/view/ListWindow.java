package com.Tbence132545.Melodigram.view;

import com.Tbence132545.Melodigram.model.HandAssignmentService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class ListWindow extends JFrame {

    public interface MidiFileActionListener {
        enum HandMode { LEFT, RIGHT, BOTH }
        void onWatchAndListenClicked(String midiFilename);
        void onPracticeClicked(String midiFilename, HandMode mode);
        void onAssignHandsClicked(String midiFilename);
    }

    private final JPanel contentPanel;
    private JButton backButton;
    private JButton importButton;

    public ListWindow() {
        setTitle("Melodigram");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(Theme.BACKGROUND);

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        mainPanel.setBackground(Theme.BACKGROUND);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(Theme.BACKGROUND);
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        JLabel label = new JLabel("Select a piece");
        label.setFont(Theme.font(Font.BOLD, 26));
        label.setForeground(Theme.TEXT_PRIMARY);
        topPanel.add(label, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(Theme.BACKGROUND);

        importButton = Theme.createAccentButton("Import MIDI", new Dimension(170, 44));
        backButton = Theme.createAccentButton("Back to Menu", new Dimension(180, 44));
        buttonPanel.add(importButton);
        buttonPanel.add(backButton);
        topPanel.add(buttonPanel, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(Theme.BACKGROUND);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Theme.BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        add(mainPanel);
    }

    public void setImportButtonListener(ActionListener listener) {
        this.importButton.addActionListener(listener);
    }

    public void setBackButtonListener(ActionListener listener) {
        this.backButton.addActionListener(listener);
    }

    public void setMidiFileList(String[] fileNames, MidiFileActionListener listener) {
        contentPanel.removeAll();
        if (fileNames != null && fileNames.length > 0) {
            for (String name : fileNames) {
                contentPanel.add(new CollapsiblePanel(name, listener));
            }
        } else {
            JLabel emptyLabel = new JLabel("No MIDI files found. Use “Import MIDI” to add one.");
            emptyLabel.setFont(Theme.font(Font.PLAIN, 16));
            emptyLabel.setForeground(Theme.TEXT_MUTED);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(emptyLabel);
        }
        contentPanel.add(Box.createVerticalGlue());
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private static class CollapsiblePanel extends JPanel {
        private final JButton titleButton;
        private final JPanel cardsPanel;
        private final CardLayout cardLayout;

        private static final String MAIN_ACTIONS = "MAIN_ACTIONS";
        private static final String PRACTICE_OPTIONS = "PRACTICE_OPTIONS";

        public CollapsiblePanel(String title, MidiFileActionListener listener) {
            super(new BorderLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setBackground(Theme.BACKGROUND);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

            titleButton = createTitleButton(title);

            cardLayout = new CardLayout();
            cardsPanel = new JPanel(cardLayout);
            cardsPanel.setOpaque(false);
            cardsPanel.setVisible(false); // start collapsed

            JPanel mainActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            mainActionsPanel.setOpaque(false);

            JButton listenButton = createCardButton("Listen and watch", e -> listener.onWatchAndListenClicked(title));
            JButton practiceButton = createCardButton("Practice", null); // listener added below
            JButton assignHandsButton = createCardButton("Assign Hands", e -> listener.onAssignHandsClicked(title));

            mainActionsPanel.add(listenButton);
            mainActionsPanel.add(practiceButton);
            mainActionsPanel.add(assignHandsButton);

            JPanel practiceOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            practiceOptionsPanel.setOpaque(false);

            practiceButton.addActionListener(e -> {
                boolean hasAssignments = HandAssignmentService.assignmentFileExistsFor(title);
                if (hasAssignments) {
                    JButton left = createCardButton("Just Left Hand", ev -> listener.onPracticeClicked(title, MidiFileActionListener.HandMode.LEFT));
                    JButton right = createCardButton("Just Right Hand", ev -> listener.onPracticeClicked(title, MidiFileActionListener.HandMode.RIGHT));
                    JButton both = createCardButton("Both Hands", ev -> listener.onPracticeClicked(title, MidiFileActionListener.HandMode.BOTH));
                    JButton back = createCardButton("<- Back", ev -> {
                        cardLayout.show(cardsPanel, MAIN_ACTIONS);
                        updatePanelHeight();
                    });
                    practiceOptionsPanel.removeAll();
                    practiceOptionsPanel.add(left);
                    practiceOptionsPanel.add(right);
                    practiceOptionsPanel.add(both);
                    practiceOptionsPanel.add(back);

                    cardsPanel.add(practiceOptionsPanel, PRACTICE_OPTIONS);
                    cardLayout.show(cardsPanel, PRACTICE_OPTIONS);
                } else {
                    listener.onPracticeClicked(title, MidiFileActionListener.HandMode.BOTH);
                }
                updatePanelHeight();
            });
            cardsPanel.add(mainActionsPanel, MAIN_ACTIONS);
            titleButton.addActionListener(e -> toggleVisibility());

            add(titleButton, BorderLayout.NORTH);
            add(cardsPanel, BorderLayout.CENTER);
            updatePanelHeight();
        }

        /** A full-width row that expands to reveal the actions for its piece. */
        private JButton createTitleButton(String title) {
            JButton button = new JButton(title) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean open = cardsPanel != null && cardsPanel.isVisible();
                    Color background = getModel().isRollover() ? Theme.SURFACE_HOVER
                            : (open ? Theme.SURFACE_RAISED : Theme.SURFACE);
                    g2.setColor(background);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.CONTROL_RADIUS, Theme.CONTROL_RADIUS);
                    if (open) {
                        g2.setColor(Theme.ACCENT);
                        g2.fillRoundRect(0, 0, 4, getHeight(), 4, 4);
                    }
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFont(Theme.font(Font.BOLD, 16));
            button.setForeground(Theme.TEXT_PRIMARY);
            button.setFocusPainted(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setOpaque(false);
            button.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            return button;
        }

        /** Neutral rather than accent: these are peer actions, and a page of red buttons
         *  leaves nothing to mark the primary ones in the header. */
        private JButton createCardButton(String text, ActionListener listener) {
            JButton button = Theme.createControlButton(text, null,
                    new Dimension(Math.max(140, text.length() * 11), 40));
            button.setFont(Theme.font(Font.PLAIN, 14));
            if (listener != null) button.addActionListener(listener);
            return button;
        }

        private void toggleVisibility() {
            cardsPanel.setVisible(!cardsPanel.isVisible());
            updatePanelHeight();
            revalidate();
            repaint();
        }

        private void updatePanelHeight() {
            int contentHeight = 0;
            for (Component comp : cardsPanel.getComponents()) {
                if (comp.isVisible()) contentHeight = comp.getPreferredSize().height;
            }
            int height = titleButton.getPreferredSize().height + contentHeight;
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        }
    }
}

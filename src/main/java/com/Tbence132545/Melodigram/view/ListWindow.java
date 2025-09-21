package com.Tbence132545.Melodigram.view;

import com.Tbence132545.Melodigram.controller.PlaybackController;
import com.Tbence132545.Melodigram.model.HandAssignmentService;
import com.Tbence132545.Melodigram.model.MidiFileService;

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
        setTitle("Choose a MIDI File");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        mainPanel.setBackground(Color.BLACK);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(Color.BLACK);

        JLabel label = new JLabel("Select a MIDI File:");
        label.setFont(new Font("Segoe UI", Font.BOLD, 24));
        label.setForeground(Color.WHITE);
        topPanel.add(label, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.BLACK);

        importButton = createModernButton("Import MIDI");
        backButton = createModernButton("<- Back to Main Menu");
        buttonPanel.add(importButton);
        buttonPanel.add(backButton);
        topPanel.add(buttonPanel, BorderLayout.EAST);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Scrollable content
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(Color.BLACK);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.BLACK);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        add(mainPanel);
    }

    private JButton createModernButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color baseColor = new Color(200, 60, 60);
                Color hoverColor = new Color(170, 40, 40);
                g2.setColor(getModel().isRollover() ? hoverColor : baseColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), textX, textY);
                g2.dispose();
            }
        };
        button.setFont(new Font("Segoe UI", Font.BOLD, 16));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(200, 45));
        return button;
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
            JLabel emptyLabel = new JLabel("No MIDI files found or error loading folder.");
            emptyLabel.setForeground(Color.WHITE);
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
            setBackground(Color.BLACK);

            // Title button
            titleButton = new JButton(title);
            titleButton.setHorizontalAlignment(SwingConstants.LEFT);
            titleButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
            titleButton.setFocusPainted(false);
            titleButton.setContentAreaFilled(false);
            titleButton.setOpaque(true);
            titleButton.setBackground(new Color(40, 40, 40));
            titleButton.setForeground(Color.WHITE);
            titleButton.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
            titleButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            titleButton.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent evt) { titleButton.setBackground(new Color(200, 60, 60)); }
                @Override
                public void mouseExited(java.awt.event.MouseEvent evt) { titleButton.setBackground(new Color(40, 40, 40)); }
            });

            cardLayout = new CardLayout();
            cardsPanel = new JPanel(cardLayout);
            cardsPanel.setOpaque(false);
            cardsPanel.setVisible(false); // start collapsed

            JPanel mainActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            mainActionsPanel.setOpaque(false);

            JButton listenButton = createCardButton("Listen and watch", e -> listener.onWatchAndListenClicked("midi/" + title));
            JButton practiceButton = createCardButton("Practice", null); // listener added below
            JButton assignHandsButton = createCardButton("Assign Hands", e -> listener.onAssignHandsClicked("midi/" + title));

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

        private JButton createCardButton(String text, ActionListener listener) {
            JButton button = new JButton(text);
            button.setFocusPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(true);
            button.setBackground(new Color(180, 40, 40));
            button.setForeground(Color.WHITE);
            button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            if (listener != null) button.addActionListener(listener);
            button.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent evt) { button.setBackground(new Color(220, 60, 60)); }
                @Override public void mouseExited(java.awt.event.MouseEvent evt) { button.setBackground(new Color(180, 40, 40)); }
            });
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

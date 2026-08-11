package com.Tbence132545.Melodigram;

import com.Tbence132545.Melodigram.controller.MainWindowController;
import com.Tbence132545.Melodigram.view.MainWindow;

import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        // Swing components must only be created and touched on the event dispatch thread.
        SwingUtilities.invokeLater(() -> new MainWindowController(new MainWindow()).openMainWindow());
    }
}

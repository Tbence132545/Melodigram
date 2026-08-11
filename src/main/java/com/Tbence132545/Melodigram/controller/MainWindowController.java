package com.Tbence132545.Melodigram.controller;

import com.Tbence132545.Melodigram.view.ListWindow;
import com.Tbence132545.Melodigram.view.MainWindow;
import com.Tbence132545.Melodigram.model.Settings;
import com.Tbence132545.Melodigram.view.SettingsWindow;

public class MainWindowController {
    private final MainWindow view;

    public MainWindowController(MainWindow view) {
        this.view = view;
        this.view.addPlayButtonListener(e -> openListWindow());
        this.view.addSettingsButtonListener(e -> openSettingsWindow());
        this.view.addQuitButtonListener(e -> exitProgram());
    }
    public void openMainWindow() {
        this.view.setVisible(true);
    }

    private void openListWindow() {
        this.view.dispose();
        ListWindow listView = new ListWindow();
        new ListWindowController(listView);
        listView.setVisible(true);
    }

    private void openSettingsWindow() {
        new SettingsWindow(view, Settings.load()).setVisible(true);
    }

    private void exitProgram() {
        System.exit(0);
    }
}


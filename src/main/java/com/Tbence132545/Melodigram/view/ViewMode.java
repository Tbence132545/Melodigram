package com.Tbence132545.Melodigram.view;

/** Which representation of the piece the piano window shows. */
public enum ViewMode {
    FALLING("Notes"),
    SHEET("Sheet"),
    BOTH("Both");

    private final String label;

    ViewMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean showsFallingNotes() {
        return this != SHEET;
    }

    public boolean showsSheetMusic() {
        return this != FALLING;
    }
}

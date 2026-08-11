package com.Tbence132545.Melodigram.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SettingsTest {

    @Test
    void defaultsAreUsableWithoutAnySavedFile() {
        Settings settings = new Settings();

        assertNull(settings.getMidiInputDeviceName(), "ask which keyboard to use");
        assertNull(settings.getSoundfontPath(), "play through the bundled piano");
        assertEquals("FALLING", settings.getDefaultViewMode());
    }

    @Test
    void anAbsentViewModeFallsBackRatherThanReturningNull() {
        // A settings file written by an older version may not carry this field at all.
        Settings settings = new Settings();
        settings.setDefaultViewMode(null);

        assertEquals("FALLING", settings.getDefaultViewMode());
    }

    @Test
    void keepsWhatItIsGiven() {
        Settings settings = new Settings();
        settings.setMidiInputDeviceName("Digital Piano");
        settings.setSoundfontPath("/tmp/grand.sf2");
        settings.setDefaultViewMode("BOTH");

        assertEquals("Digital Piano", settings.getMidiInputDeviceName());
        assertEquals("/tmp/grand.sf2", settings.getSoundfontPath());
        assertEquals("BOTH", settings.getDefaultViewMode());
    }

    @Test
    void theSettingsFileSitsBesideTheHandAssignments() {
        assertEquals(AppPaths.dataDirectory(), AppPaths.settingsFile().getParent());
        assertEquals(AppPaths.dataDirectory(), AppPaths.assignmentsDirectory().getParent());
        assertEquals("settings.json", AppPaths.settingsFile().getFileName().toString());
    }
}

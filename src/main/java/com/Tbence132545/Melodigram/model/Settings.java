package com.Tbence132545.Melodigram.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * User preferences, stored as JSON next to the hand assignments.
 *
 * <p>Every field has a usable default, and a missing or unreadable file simply means defaults —
 * settings are never important enough to stop the application starting.
 */
public class Settings {

    /** Name of the MIDI input to use for practice; null means ask each time. */
    private String midiInputDeviceName;

    /** Path to a SoundFont to play through; null means the bundled piano. */
    private String soundfontPath;

    /** Which view the piano window opens in: FALLING, SHEET or BOTH. */
    private String defaultViewMode = "FALLING";

    public String getMidiInputDeviceName() {
        return midiInputDeviceName;
    }

    public void setMidiInputDeviceName(String midiInputDeviceName) {
        this.midiInputDeviceName = midiInputDeviceName;
    }

    public String getSoundfontPath() {
        return soundfontPath;
    }

    public void setSoundfontPath(String soundfontPath) {
        this.soundfontPath = soundfontPath;
    }

    public String getDefaultViewMode() {
        return defaultViewMode == null ? "FALLING" : defaultViewMode;
    }

    public void setDefaultViewMode(String defaultViewMode) {
        this.defaultViewMode = defaultViewMode;
    }

    public static Settings load() {
        Path file = AppPaths.settingsFile();
        if (!Files.exists(file)) {
            return new Settings();
        }
        try {
            Settings loaded = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), Settings.class);
            return loaded == null ? new Settings() : loaded;
        } catch (IOException | JsonParseException e) {
            System.err.println("Could not read settings, using defaults: " + e.getMessage());
            return new Settings();
        }
    }

    public boolean save() {
        try {
            Files.createDirectories(AppPaths.dataDirectory());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(this);
            Files.writeString(AppPaths.settingsFile(), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("Could not save settings: " + e.getMessage());
            return false;
        }
    }
}

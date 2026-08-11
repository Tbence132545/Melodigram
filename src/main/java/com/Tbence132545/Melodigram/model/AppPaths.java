package com.Tbence132545.Melodigram.model;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Where the application keeps the files it owns, following each platform's convention. */
public final class AppPaths {

    private static final String APP_NAME = "Melodigram";

    private AppPaths() {
    }

    public static Path dataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home");
        Path baseDir;
        if (os.contains("win")) {
            // APPDATA is not guaranteed to be set; without a fallback this throws inside a
            // static initializer and takes the whole application down at class-load time.
            String appData = System.getenv("APPDATA");
            baseDir = (appData == null || appData.isBlank())
                    ? Paths.get(userHome, "AppData", "Roaming")
                    : Paths.get(appData);
        } else if (os.contains("mac")) {
            baseDir = Paths.get(userHome, "Library", "Application Support");
        } else {
            baseDir = Paths.get(userHome, "." + APP_NAME);
        }
        return baseDir.resolve(APP_NAME);
    }

    public static Path assignmentsDirectory() {
        return dataDirectory().resolve("assignments");
    }

    public static Path settingsFile() {
        return dataDirectory().resolve("settings.json");
    }
}

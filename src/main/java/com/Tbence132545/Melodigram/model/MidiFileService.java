// java
package com.Tbence132545.Melodigram.model;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A service class to handle all MIDI file loading, discovery, and importing.
 */
public class MidiFileService {

    public record MidiData(MidiPlayer player, Sequence sequence) {}

    private static final String INTERNAL_MIDI_DIR = "midi/";
    private final Path externalMidiDir;

    public MidiFileService() {
        this.externalMidiDir = Paths.get(System.getProperty("user.home"), ".Melodigram", "midi");
    }


    public void importMidiFile(File sourceFile) throws IOException {
        Files.createDirectories(externalMidiDir);
        Path destinationPath = externalMidiDir.resolve(sourceFile.getName());
        Files.copy(sourceFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }


    /**
     * Opens a player already loaded with the named file. The caller owns the returned player
     * and must close it. Use {@link #loadSequence} instead when only the notes are needed:
     * constructing a player also opens a synthesizer and loads the soundfont.
     */
    public MidiData loadMidiData(String midiFileName) throws Exception {
        Sequence sequence = loadSequence(midiFileName);
        MidiPlayer midiPlayer = new MidiPlayer();
        try {
            midiPlayer.setSequence(sequence);
        } catch (Exception e) {
            midiPlayer.close();
            throw e;
        }
        return new MidiData(midiPlayer, sequence);
    }

    /** Reads a MIDI file, preferring a user-imported copy over the bundled resource. */
    public Sequence loadSequence(String midiFileName) throws IOException, InvalidMidiDataException {
        Path externalFile = externalMidiDir.resolve(midiFileName);
        if (Files.exists(externalFile)) {
            return MidiSystem.getSequence(externalFile.toFile());
        }
        String resourcePath = INTERNAL_MIDI_DIR + midiFileName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new FileNotFoundException("Cannot find resource: " + resourcePath);
            return MidiSystem.getSequence(new BufferedInputStream(is));
        }
    }

    public List<String> getAllMidiFileNames() {
        Set<String> allFiles = new HashSet<>(listInternalMidiResources());
        allFiles.addAll(listExternalMidiFiles());

        List<String> sortedList = new ArrayList<>(allFiles);
        Collections.sort(sortedList);
        return sortedList;
    }

    private List<String> listExternalMidiFiles() {
        if (!Files.exists(externalMidiDir) || !Files.isDirectory(externalMidiDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.walk(externalMidiDir, 1)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase().endsWith(".mid") || name.toLowerCase().endsWith(".midi"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error reading external MIDI folder: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> listInternalMidiResources() {
        try {
            URL url = getClass().getClassLoader().getResource(INTERNAL_MIDI_DIR);
            if (url == null) return Collections.emptyList();

            if ("jar".equals(url.getProtocol())) {
                JarURLConnection conn = (JarURLConnection) url.openConnection();
                try (JarFile jar = conn.getJarFile()) {
                    return jar.stream()
                            .map(JarEntry::getName)
                            .filter(name -> name.startsWith(INTERNAL_MIDI_DIR) && !name.endsWith("/"))
                            .filter(name -> name.toLowerCase().endsWith(".mid") || name.toLowerCase().endsWith(".midi"))
                            .map(name -> name.substring(INTERNAL_MIDI_DIR.length()))
                            .collect(Collectors.toList());
                }
            } else {
                try (Stream<Path> stream = Files.list(Paths.get(url.toURI()))) {
                    return stream.filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .filter(n -> n.toLowerCase().endsWith(".mid") || n.toLowerCase().endsWith(".midi"))
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            System.err.println("Could not list internal MIDI resources: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
// src/main/java/com/Tbence132545/Melodigram/services/HandAssignmentService.java
package com.Tbence132545.Melodigram.model;

import com.Tbence132545.Melodigram.view.AnimationPanel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

public class HandAssignmentService {

    private static final Path ASSIGNMENTS_DIR = AppPaths.assignmentsDirectory();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Optional<List<AnimationPanel.HandAssignment>> loadAssignments(Sequence sequence) {
        Path file = assignmentFilePath(computeSequenceHash(sequence));
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            HandAssignmentFile data = gson.fromJson(content, HandAssignmentFile.class);
            return (data != null && data.getAssignment() != null)
                    ? Optional.of(data.getAssignment())
                    : Optional.empty();
        } catch (IOException | JsonParseException e) {
            System.err.println("Could not read hand assignments from " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public boolean saveAssignments(Sequence sequence, List<AnimationPanel.HandAssignment> assignments) {
        String hash = computeSequenceHash(sequence);
        HandAssignmentFile data = new HandAssignmentFile(hash, assignments);
        try {
            Files.createDirectories(ASSIGNMENTS_DIR);
            Files.writeString(assignmentFilePath(hash), gson.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("Could not save hand assignments: " + e.getMessage());
            return false;
        }
    }

    public static boolean assignmentFileExistsFor(String midiFileName) {
        try {
            Sequence sequence = new MidiFileService().loadSequence(midiFileName);
            return Files.exists(assignmentFilePath(computeSequenceHash(sequence)));
        } catch (Exception e) {
            System.err.println("Could not look up hand assignments for " + midiFileName + ": " + e.getMessage());
            return false;
        }
    }

    private static Path assignmentFilePath(String sequenceHash) {
        return ASSIGNMENTS_DIR.resolve(sequenceHash + ".json");
    }

    private static String computeSequenceHash(Sequence sequence) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent ev = track.get(i);
                    updateDigestWithLong(md, ev.getTick());
                    MidiMessage msg = ev.getMessage();
                    md.update(msg.getMessage(), 0, msg.getLength());
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 unavailable", e);
        }
    }

    private static void updateDigestWithLong(MessageDigest md, long v) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) (v & 0xFF);
            v >>= 8;
        }
        md.update(b);
    }

}

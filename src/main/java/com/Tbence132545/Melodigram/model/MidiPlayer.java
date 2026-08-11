package com.Tbence132545.Melodigram.model;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.function.IntConsumer;

/**
 * Owns the sequencer/synthesizer pair used to play one piece.
 *
 * <p>Callers must {@link #close()} the player when they are finished with it: both devices hold
 * native audio resources and the custom soundbank stays resident until the synthesizer closes.
 */
public class MidiPlayer implements AutoCloseable {

    private static final String SOUNDFONT_RESOURCE = "soundfonts/steinway.sf2";
    private static final int END_OF_TRACK = 47;
    private static final int ACOUSTIC_GRAND_PIANO = 0;

    private final Sequencer sequencer;
    private final Synthesizer synthesizer;

    // Assigned on the EDT, read on the sequencer's own playback thread.
    private volatile IntConsumer noteOnListener;
    private volatile IntConsumer noteOffListener;

    public MidiPlayer() throws MidiUnavailableException {
        sequencer = MidiSystem.getSequencer(false);
        synthesizer = MidiSystem.getSynthesizer();
        try {
            sequencer.open();
            synthesizer.open();
            loadSoundfont();
            sequencer.getTransmitter().setReceiver(new NoteObservingReceiver(synthesizer.getReceiver()));
            sequencer.addMetaEventListener(meta -> {
                if (meta.getType() == END_OF_TRACK) {
                    sequencer.stop();
                }
            });
        } catch (MidiUnavailableException e) {
            close();
            throw e;
        }
    }

    public void setSequence(Sequence sequence) throws InvalidMidiDataException {
        sequencer.setSequence(sequence);
    }

    public static int[] extractNoteRange(Sequence sequence) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiMessage message = track.get(i).getMessage();
                if (message instanceof ShortMessage shortMessage
                        && (MidiMessages.isNoteOn(shortMessage) || MidiMessages.isNoteOff(shortMessage))) {
                    lowest = Math.min(lowest, shortMessage.getData1());
                    highest = Math.max(highest, shortMessage.getData1());
                }
            }
        }

        if (lowest > highest) {
            return new int[]{60, 72};
        }
        return new int[]{lowest, highest};
    }

    public void play() {
        sequencer.start();
    }

    public void stop() {
        if (sequencer.isOpen()) {
            sequencer.stop();
        }
    }

    public boolean isPlaying() {
        return sequencer.isOpen() && sequencer.isRunning();
    }

    /**
     * Scales playback rate without altering pitch, where 1.0 is the sequence's written tempo.
     * The sequencer keeps reporting its position in sequence time, so note positions in the
     * animation stay valid at any speed.
     */
    public void setTempoFactor(float factor) {
        if (sequencer.isOpen()) {
            sequencer.setTempoFactor(factor);
        }
    }

    public void setNoteOnListener(IntConsumer listener) {
        this.noteOnListener = listener;
    }

    public void setNoteOffListener(IntConsumer listener) {
        this.noteOffListener = listener;
    }

    public Sequencer getSequencer() {
        return this.sequencer;
    }

    @Override
    public void close() {
        if (sequencer.isOpen()) {
            sequencer.close();
        }
        if (synthesizer.isOpen()) {
            synthesizer.close();
        }
    }

    /** Failing to load a soundfont is not fatal; the default soundbank still plays. */
    private void loadSoundfont() {
        try {
            Soundbank soundbank = readConfiguredSoundbank();
            if (soundbank == null) {
                return;
            }
            if (!synthesizer.isSoundbankSupported(soundbank)) {
                System.err.println("SoundFont not supported by this synthesizer, using default soundbank.");
                return;
            }
            Soundbank defaultSoundbank = synthesizer.getDefaultSoundbank();
            if (defaultSoundbank != null) {
                synthesizer.unloadAllInstruments(defaultSoundbank);
            }
            synthesizer.loadAllInstruments(soundbank);
            // Every channel, not just the first: multi-track files spread the piano part around.
            for (MidiChannel channel : synthesizer.getChannels()) {
                if (channel != null) {
                    channel.programChange(ACOUSTIC_GRAND_PIANO);
                }
            }
        } catch (IOException | InvalidMidiDataException e) {
            System.err.println("Could not load SoundFont, using default soundbank: " + e.getMessage());
        }
    }

    /** The SoundFont chosen in settings, else the bundled one. */
    private Soundbank readConfiguredSoundbank() throws IOException, InvalidMidiDataException {
        String configured = Settings.load().getSoundfontPath();
        if (configured != null && !configured.isBlank()) {
            File file = new File(configured);
            if (file.isFile()) {
                return MidiSystem.getSoundbank(file);
            }
            System.err.println("Configured SoundFont is missing, using the bundled one: " + configured);
        }
        URL bundled = getClass().getClassLoader().getResource(SOUNDFONT_RESOURCE);
        if (bundled == null) {
            System.err.println("SoundFont not found in resources: " + SOUNDFONT_RESOURCE);
            return null;
        }
        return MidiSystem.getSoundbank(bundled);
    }

    /** Forwards everything to the synthesizer, reporting note on/off to the UI on the way through. */
    private final class NoteObservingReceiver implements Receiver {
        private final Receiver synthReceiver;

        private NoteObservingReceiver(Receiver synthReceiver) {
            this.synthReceiver = synthReceiver;
        }

        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (message instanceof ShortMessage shortMessage) {
                if (MidiMessages.isNoteOn(shortMessage)) {
                    notify(noteOnListener, shortMessage);
                } else if (MidiMessages.isNoteOff(shortMessage)) {
                    notify(noteOffListener, shortMessage);
                }
            }
            synthReceiver.send(message, timeStamp);
        }

        private void notify(IntConsumer listener, ShortMessage message) {
            if (listener != null) {
                listener.accept(message.getData1());
            }
        }

        @Override
        public void close() {
            synthReceiver.close();
        }
    }
}

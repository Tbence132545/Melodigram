package com.Tbence132545.Melodigram.model;

import javax.sound.midi.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.function.IntConsumer;
public class MidiPlayer {
    private Sequencer sequencer;
    private IntConsumer noteOnListener;
    private IntConsumer noteOffListener;

    public MidiPlayer() {
        try {
            sequencer = MidiSystem.getSequencer(false);
            sequencer.open();
            Synthesizer synth = MidiSystem.getSynthesizer();
            synth.open();
            try {
                java.net.URL sf2Url = getClass().getClassLoader().getResource("soundfonts/steinway.sf2");
                if (sf2Url == null) {
                    throw new FileNotFoundException("SoundFont not found in resources: soundfonts/soundfont1.sf2");
                }

                Soundbank sb = MidiSystem.getSoundbank(sf2Url);
                if (synth.isSoundbankSupported(sb)) {
                    synth.unloadAllInstruments(synth.getDefaultSoundbank());
                    synth.loadAllInstruments(sb);
                    synth.getChannels()[0].programChange(0);
                } else {
                    System.out.println("Soundbank not supported: " + sf2Url);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Falling back to default soundbank.");
            }

            Transmitter transmitter = sequencer.getTransmitter();
            Receiver synthReceiver = synth.getReceiver();

            transmitter.setReceiver(new Receiver() {
                public void send(MidiMessage message, long timeStamp) {
                    if (message instanceof ShortMessage sm) {
                        if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            if (noteOnListener != null) noteOnListener.accept(sm.getData1());
                        } else if (sm.getCommand() == ShortMessage.NOTE_OFF ||
                                (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                            if (noteOffListener != null) noteOffListener.accept(sm.getData1());
                        }
                    }
                    synthReceiver.send(message, timeStamp);
                }
                public void close() {}
            });

            sequencer.addMetaEventListener(meta -> {
                if (meta.getType() == 47) {
                    sequencer.stop();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void loadMidiFromFile(String filePath) {
        try {
            Sequence sequence = MidiSystem.getSequence(new File(filePath));
            sequencer.setSequence(sequence);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadMidiFromResources(String fileName) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(fileName);
            if (is == null) throw new FileNotFoundException("MIDI file not found: " + fileName);

            Sequence sequence = MidiSystem.getSequence(is);
            sequencer.setSequence(sequence);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static int[] extractNoteRange(Sequence sequence) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();
                if (msg instanceof ShortMessage sm) {
                    int cmd = sm.getCommand();
                    int note = sm.getData1();
                    if ((cmd == ShortMessage.NOTE_ON && sm.getData2() > 0) || cmd == ShortMessage.NOTE_OFF) {
                        lowest = Math.min(lowest, note);
                        highest = Math.max(highest, note);
                    }
                }
            }
        }

        if (lowest == Integer.MAX_VALUE || highest == Integer.MIN_VALUE) {
            lowest = 60;
            highest = 72;
        }

        return new int[]{lowest, highest};
    }

    public void play() {
            sequencer.start();
    }
    public boolean isPlaying(){
        return sequencer.isRunning();
    }
    public void stop() {
        sequencer.stop();
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
}

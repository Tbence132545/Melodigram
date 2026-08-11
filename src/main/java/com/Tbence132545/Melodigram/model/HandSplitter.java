package com.Tbence132545.Melodigram.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Guesses which hand plays each note, for files that do not separate the hands themselves.
 *
 * <p>Assigning a piece by hand is impractical past a few hundred notes, and most MIDI files put
 * both hands on one track, so there is nothing to read the split off. The guess here is made
 * from what a pianist physically can do: notes struck together are cut into a lower and an upper
 * group, choosing the cut that keeps each hand within its reach and closest to where that hand
 * already was.
 */
public final class HandSplitter {

    /** A hand can comfortably span about a tenth. */
    private static final int MAX_SPAN_SEMITONES = 14;
    /** And has five fingers. */
    private static final int MAX_NOTES_PER_HAND = 5;

    /** Notes struck this close together are one gesture, shared between the hands. */
    private static final long CHORD_MS = 30;

    private static final double SPAN_PENALTY = 10.0;
    private static final double FINGER_PENALTY = 8.0;
    private static final double IDLE_HAND_PENALTY = 1.5;

    private static final double CENTRE_SMOOTHING = 0.4;
    private static final int INITIAL_LEFT_CENTRE = 48;   // C3
    private static final int INITIAL_RIGHT_CENTRE = 72;  // C5

    private HandSplitter() {
    }

    /** Assigns a hand to every note in place. Notes need not be sorted. */
    public static void assignHands(List<ScoreNote> notes) {
        List<ScoreNote> ordered = new ArrayList<>(notes);
        ordered.sort(Comparator.comparingLong(ScoreNote::onMillis).thenComparingInt(ScoreNote::midiNote));

        double leftCentre = INITIAL_LEFT_CENTRE;
        double rightCentre = INITIAL_RIGHT_CENTRE;

        int start = 0;
        while (start < ordered.size()) {
            int end = start;
            long onset = ordered.get(start).onMillis();
            while (end < ordered.size() && ordered.get(end).onMillis() - onset <= CHORD_MS) {
                end++;
            }
            List<ScoreNote> group = ordered.subList(start, end);
            group.sort(Comparator.comparingInt(ScoreNote::midiNote));

            int split = bestSplit(group, leftCentre, rightCentre);
            for (int i = 0; i < group.size(); i++) {
                group.get(i).setHand(i < split ? Hand.LEFT : Hand.RIGHT);
            }
            leftCentre = movedCentre(leftCentre, group.subList(0, split));
            rightCentre = movedCentre(rightCentre, group.subList(split, group.size()));
            start = end;
        }
    }

    /**
     * @return how many of the group's notes go to the left hand. Zero and the full size are both
     *         valid answers, which is how a passage for one hand alone falls out of the same
     *         search rather than needing a case of its own.
     */
    private static int bestSplit(List<ScoreNote> group, double leftCentre, double rightCentre) {
        int bestSplit = 0;
        double bestCost = Double.MAX_VALUE;
        for (int split = 0; split <= group.size(); split++) {
            double cost = handCost(group.subList(0, split), leftCentre)
                    + handCost(group.subList(split, group.size()), rightCentre);
            if (cost < bestCost) {
                bestCost = cost;
                bestSplit = split;
            }
        }
        return bestSplit;
    }

    private static double handCost(List<ScoreNote> hand, double centre) {
        if (hand.isEmpty()) {
            // Leaving a hand out is normal, but not free: it stops one hand being handed
            // everything when the other is a slightly better fit for every single note.
            return IDLE_HAND_PENALTY;
        }
        int span = hand.get(hand.size() - 1).midiNote() - hand.get(0).midiNote();
        double cost = Math.max(0, span - MAX_SPAN_SEMITONES) * SPAN_PENALTY
                + Math.max(0, hand.size() - MAX_NOTES_PER_HAND) * FINGER_PENALTY;
        return cost + Math.abs(centroid(hand) - centre);
    }

    private static double centroid(List<ScoreNote> hand) {
        return hand.stream().mapToInt(ScoreNote::midiNote).average().orElse(0);
    }

    private static double movedCentre(double centre, List<ScoreNote> hand) {
        if (hand.isEmpty()) {
            return centre;
        }
        return centre * (1 - CENTRE_SMOOTHING) + centroid(hand) * CENTRE_SMOOTHING;
    }
}

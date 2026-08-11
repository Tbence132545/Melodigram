package com.Tbence132545.Melodigram.view;

import com.Tbence132545.Melodigram.model.Hand;
import com.Tbence132545.Melodigram.model.KeySignature;
import com.Tbence132545.Melodigram.model.MeasureMap;
import com.Tbence132545.Melodigram.model.MidiTimeline;
import com.Tbence132545.Melodigram.model.ScoreNote;
import com.Tbence132545.Melodigram.model.ScoreSpelling;
import com.Tbence132545.Melodigram.model.StaffNotation;
import com.Tbence132545.Melodigram.model.StaffNotation.NoteValue;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A grand staff that scrolls past a fixed playhead, with notes lighting up as they sound.
 *
 * <p>Spacing is proportional to time rather than engraved: a note's horizontal position is
 * where it falls in the piece, so the staff stays locked to playback and to the falling-note
 * view beside it. Note values still pick the head, stem and flags, so it reads as notation
 * rather than as a piano roll.
 */
public class SheetMusicPanel extends JPanel {

    /**
     * Milliseconds of music visible across the staff. Kept short deliberately: a longer window
     * packs the notes closer than a notehead is wide, which is what makes a busy passage
     * illegible.
     */
    private static final long WINDOW_MS = 2500;

    /**
     * Onsets within this of each other are treated as one chord. Sequenced files rarely strike a
     * chord on exactly the same tick — the notes are spread by a few milliseconds to sound human
     * — and drawing those as separate chords stacks them on top of one another.
     */
    private static final long CHORD_GROUPING_MS = 30;

    /** Fraction of the music area kept behind the playhead, so just-played notes stay visible. */
    private static final double PLAYHEAD_FRACTION = 0.28;

    private static final int CLEF_AREA_WIDTH = 88;
    /** Treble staff (4 gaps) + spacing between staves (5) + bass staff (4). */
    private static final int TOTAL_STAFF_GAPS = 13;
    /**
     * The staff is sized off this rather than the raw height, reserving the extra gaps as
     * headroom above and below so ledger-line notes are not clipped by the panel edge.
     */
    private static final int LAYOUT_GAPS_WITH_HEADROOM = 20;
    /** Staves stop growing past this, so a tall window centres one readable system
     *  instead of one enormous one. */
    private static final int MAX_STAFF_GAP = 19;
    private static final int MIN_STAFF_GAP = 5;
    private static final int MAX_LEDGER_LINES = 6;

    // Proportions taken from an engraved reference score, measured in staff spaces.
    private static final float STEM_THICKNESS_RATIO = 0.13f;
    private static final float BARLINE_THICKNESS_RATIO = 0.16f;
    private static final float LEDGER_THICKNESS_RATIO = 0.11f;
    private static final float STAFF_LINE_THICKNESS_RATIO = 0.08f;
    private static final float BEAM_THICKNESS_RATIO = 0.5f;
    private static final float STEM_LENGTH_GAPS = 3.3f;

    private static final Color COLOR_PAPER = new Color(20, 21, 24);
    private static final Color COLOR_STAFF = new Color(150, 155, 166);
    private static final Color COLOR_BARLINE = new Color(96, 100, 110);
    private static final Color COLOR_NOTE = new Color(226, 228, 234);
    private static final Color COLOR_LEFT_HAND = new Color(135, 206, 250);
    private static final Color COLOR_RIGHT_HAND = new Color(250, 150, 138);
    private static final Color COLOR_PLAYHEAD = new Color(206, 66, 66);
    private static final Color COLOR_SOUNDING = new Color(255, 214, 92);

    private static final int TREBLE_CLEF = 0x1D11E;
    private static final int BASS_CLEF = 0x1D122;

    private List<ScoreNote> notes = Collections.emptyList();
    private List<Long> measureStartMillis = Collections.emptyList();
    private List<Long> beatStartMillis = Collections.emptyList();
    private MidiTimeline timeline;
    private KeySignature keySignature = new KeySignature(0);
    private List<ScoreSpelling.SpelledNote> spelling = Collections.emptyList();
    private long currentTimeMillis = 0;
    private long longestNoteMillis = 0;
    private String timeSignature = "";

    public SheetMusicPanel() {
        setBackground(COLOR_PAPER);
        setPreferredSize(new Dimension(800, 250));
    }

    /** @param notes shared with the falling-note view, so hand assignments stay in step. */
    public void setScore(List<ScoreNote> notes, MeasureMap measures, MidiTimeline timeline,
                         KeySignature keySignature) {
        this.notes = notes;
        this.measureStartMillis = measures.measureStartMillis();
        this.beatStartMillis = measures.beatStartMillis();
        this.timeSignature = measures.numerator() + "/" + measures.denominator();
        this.timeline = timeline;
        this.keySignature = keySignature;
        // Spelling depends only on pitch and position in the piece, so it is worked out once
        // here rather than on every frame.
        this.spelling = ScoreSpelling.spell(notes, keySignature, this.measureStartMillis);
        this.longestNoteMillis = notes.stream()
                .mapToLong(note -> note.offMillis() - note.onMillis())
                .max()
                .orElse(0);
        repaint();
    }

    /**
     * Index of the first note that could still be on screen at {@code windowStart}.
     *
     * <p>Notes are ordered by onset, so this is a binary search — but a note that began long ago
     * may still be sounding, so the search target is backed off by the longest note in the piece
     * rather than landing exactly on the window.
     */
    private int firstPossiblyVisibleIndex(long windowStart) {
        long target = windowStart - longestNoteMillis;
        int low = 0;
        int high = notes.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (notes.get(mid).onMillis() < target) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public void setCurrentTimeMillis(long millis) {
        if (millis != currentTimeMillis) {
            this.currentTimeMillis = millis;
            repaint();
        }
    }

    private int staffGap() {
        int fitted = getHeight() / LAYOUT_GAPS_WITH_HEADROOM;
        return Math.max(MIN_STAFF_GAP, Math.min(MAX_STAFF_GAP, fitted));
    }

    /** Y of the top line of the treble staff, with the whole system centred vertically. */
    private int systemTopY() {
        return Math.max(staffGap(), (getHeight() - TOTAL_STAFF_GAPS * staffGap()) / 2);
    }

    private int trebleBottomLineY() {
        return systemTopY() + 4 * staffGap();
    }

    private int bassBottomLineY() {
        return systemTopY() + TOTAL_STAFF_GAPS * staffGap();
    }

    /** The clef block grows to fit however many accidentals the key signature carries. */
    private int clefAreaWidth() {
        return CLEF_AREA_WIDTH + Math.abs(keySignature.accidentals()) * keySignatureStepWidth()
                + Math.round(staffGap() * 2.4f);
    }

    private int keySignatureStepWidth() {
        return Math.round(staffGap() * 0.95f);
    }

    private int musicAreaWidth() {
        return Math.max(1, getWidth() - clefAreaWidth());
    }

    private double pixelsPerMillisecond() {
        return (double) musicAreaWidth() / WINDOW_MS;
    }

    private int playheadX() {
        return clefAreaWidth() + (int) (musicAreaWidth() * PLAYHEAD_FRACTION);
    }

    private int xForMillis(long millis) {
        return playheadX() + (int) Math.round((millis - currentTimeMillis) * pixelsPerMillisecond());
    }

    /** Notes above middle C go on the treble staff unless a hand assignment says otherwise. */
    private boolean useTrebleStaff(ScoreNote note) {
        Hand hand = note.hand();
        if (hand != null) {
            return hand == Hand.RIGHT;
        }
        return note.midiNote() >= 60;
    }

    private int yForStep(int step, boolean treble) {
        int halfGap = staffGap() / 2;
        int bottomLineY = treble ? trebleBottomLineY() : bassBottomLineY();
        int bottomStep = treble ? StaffNotation.TREBLE_BOTTOM_STEP : StaffNotation.BASS_BOTTOM_STEP;
        return bottomLineY - (step - bottomStep) * halfGap;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        drawBarlines(g2);
        drawStaves(g2);
        drawNotes(g2);
        drawPlayhead(g2);
        drawClefArea(g2);

        g2.dispose();
    }

    private void drawStaves(Graphics2D g2) {
        int gap = staffGap();
        g2.setColor(COLOR_STAFF);
        g2.setStroke(new BasicStroke(Math.max(1f, gap * STAFF_LINE_THICKNESS_RATIO)));
        for (int line = 0; line < 5; line++) {
            int trebleY = trebleBottomLineY() - line * gap;
            int bassY = bassBottomLineY() - line * gap;
            g2.drawLine(0, trebleY, getWidth(), trebleY);
            g2.drawLine(0, bassY, getWidth(), bassY);
        }
    }

    private void drawBarlines(Graphics2D g2) {
        if (measureStartMillis.isEmpty()) {
            return;
        }
        g2.setColor(COLOR_BARLINE);
        g2.setStroke(new BasicStroke(Math.max(1.4f, staffGap() * BARLINE_THICKNESS_RATIO)));
        int top = trebleBottomLineY() - 4 * staffGap();
        int bottom = bassBottomLineY();
        for (long measureStart : measureStartMillis) {
            int x = xForMillis(measureStart);
            if (x < clefAreaWidth() - 2) continue;
            if (x > getWidth()) break;
            g2.drawLine(x, top, x, bottom);
        }
    }

    /** The clef and key signature stay put while the music scrolls underneath them. */
    private void drawClefArea(Graphics2D g2) {
        int width = clefAreaWidth();
        g2.setColor(COLOR_PAPER);
        g2.fillRect(0, 0, width, getHeight());

        int gap = staffGap();
        g2.setColor(COLOR_STAFF);
        g2.setStroke(new BasicStroke(1f));
        for (int line = 0; line < 5; line++) {
            g2.drawLine(0, trebleBottomLineY() - line * gap, width, trebleBottomLineY() - line * gap);
            g2.drawLine(0, bassBottomLineY() - line * gap, width, bassBottomLineY() - line * gap);
        }

        // Brace joining the two staves.
        g2.setStroke(new BasicStroke(2.5f));
        int braceTop = trebleBottomLineY() - 4 * gap;
        g2.drawLine(2, braceTop, 2, bassBottomLineY());

        drawClef(g2, TREBLE_CLEF, "G", trebleBottomLineY(), gap, true);
        drawClef(g2, BASS_CLEF, "F", bassBottomLineY(), gap, false);
        drawKeySignature(g2, gap);
        drawTimeSignature(g2, gap);

        g2.setColor(COLOR_STAFF);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(width, braceTop, width, bassBottomLineY());
    }

    /** Writing the key's accidentals once here is what keeps them off the notes themselves. */
    private void drawKeySignature(Graphics2D g2, int gap) {
        if (keySignature.accidentals() == 0) {
            return;
        }
        g2.setColor(COLOR_NOTE);
        String sign = keySignature.usesFlats() ? "♭" : "♯";
        int stepWidth = keySignatureStepWidth();
        int firstX = CLEF_AREA_WIDTH - Math.round(gap * 0.4f);

        drawKeySignatureRow(g2, sign, keySignature.trebleSymbolSteps(), true, firstX, stepWidth, gap);
        drawKeySignatureRow(g2, sign, keySignature.bassSymbolSteps(), false, firstX, stepWidth, gap);
    }

    /** Stacked numerals, sitting just inside the staff on both staves. */
    private void drawTimeSignature(Graphics2D g2, int gap) {
        if (timeSignature.isEmpty()) {
            return;
        }
        String[] parts = timeSignature.split("/");
        if (parts.length != 2) {
            return;
        }
        g2.setColor(COLOR_NOTE);
        g2.setFont(Theme.font(Font.BOLD, Math.round(gap * 2.1f)));
        FontMetrics metrics = g2.getFontMetrics();
        int x = clefAreaWidth() - Math.round(gap * 1.7f);
        for (boolean treble : new boolean[]{true, false}) {
            int middleY = yForStep((treble ? StaffNotation.TREBLE_BOTTOM_STEP
                    : StaffNotation.BASS_BOTTOM_STEP) + StaffNotation.STAFF_SPAN_STEPS / 2, treble);
            g2.drawString(parts[0], x - metrics.stringWidth(parts[0]) / 2,
                    middleY - Math.round(gap * 0.15f));
            g2.drawString(parts[1], x - metrics.stringWidth(parts[1]) / 2,
                    middleY + Math.round(gap * 1.95f));
        }
    }

    private void drawKeySignatureRow(Graphics2D g2, String sign, int[] steps, boolean treble,
                                     int firstX, int stepWidth, int gap) {
        Font font = Theme.font(Font.PLAIN, Math.round(gap * 2.6f));
        if (!font.canDisplay(sign.charAt(0))) {
            return;
        }
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        for (int i = 0; i < steps.length; i++) {
            int x = firstX + i * stepWidth;
            int y = yForStep(steps[i], treble);
            g2.drawString(sign, x - metrics.stringWidth(sign) / 2, y + Math.round(gap * 0.75f));
        }
    }

    /**
     * Draws the clef from the Unicode musical symbol where the platform has the glyph, since
     * hand-drawing a convincing treble clef is far worse than the real character. Falls back to
     * the clef letter so the staff is still identifiable elsewhere.
     */
    private void drawClef(Graphics2D g2, int codePoint, String fallback, int bottomLineY, int gap, boolean treble) {
        g2.setColor(COLOR_NOTE);
        int staffHeight = 4 * gap;
        Font glyphFont = Theme.font(Font.PLAIN, Math.round(staffHeight * (treble ? 2.05f : 1.35f)));

        if (glyphFont.canDisplay(codePoint)) {
            String glyph = new String(Character.toChars(codePoint));
            g2.setFont(glyphFont);
            FontMetrics metrics = g2.getFontMetrics();
            // The treble glyph curls below its baseline; align each clef to its reference line.
            int baseline = treble ? bottomLineY + Math.round(gap * 1.05f) : bottomLineY - Math.round(gap * 2.25f);
            g2.drawString(glyph, 16, baseline);
            if (metrics.stringWidth(glyph) > 0) {
                return;
            }
        }
        g2.setFont(Theme.font(Font.BOLD, staffHeight));
        g2.drawString(fallback, 20, bottomLineY - gap);
    }

    private void drawPlayhead(Graphics2D g2) {
        int x = playheadX();
        g2.setColor(COLOR_PLAYHEAD);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(x, 0, x, getHeight());
    }

    private void drawNotes(Graphics2D g2) {
        if (notes.isEmpty()) {
            return;
        }
        long windowStart = currentTimeMillis - (long) (WINDOW_MS * PLAYHEAD_FRACTION) - 500;
        long windowEnd = currentTimeMillis + WINDOW_MS;

        List<ScoreNote> visible = new ArrayList<>();
        List<Integer> visibleIndices = new ArrayList<>();
        for (int i = firstPossiblyVisibleIndex(windowStart); i < notes.size(); i++) {
            ScoreNote note = notes.get(i);
            if (note.onMillis() > windowEnd) {
                break; // notes are ordered by onset
            }
            if (note.offMillis() >= windowStart) {
                visible.add(note);
                visibleIndices.add(i);
            }
        }

        for (BeamGroup group : beamGroups(placeChords(visible, visibleIndices))) {
            drawBeamGroup(g2, group);
        }
    }

    /** Consecutive chords on one staff, inside one beat, joined under a beam. */
    private record BeamGroup(List<Chord> chords, boolean stemUp) {
    }

    /**
     * Collects chords into beams the way an engraver would: only within a single beat, only
     * quavers and shorter, and only where the chords actually run on from one another. A lone
     * chord becomes its own group and keeps its flag.
     */
    private List<BeamGroup> beamGroups(List<Chord> chords) {
        List<BeamGroup> groups = new ArrayList<>();
        for (boolean treble : new boolean[]{true, false}) {
            List<Chord> onStaff = chords.stream().filter(chord -> chord.treble() == treble).toList();
            int i = 0;
            while (i < onStaff.size()) {
                int end = i + 1;
                if (isBeamable(onStaff.get(i))) {
                    while (end < onStaff.size()
                            && isBeamable(onStaff.get(end))
                            && beatIndexAt(onStaff.get(end).onset()) == beatIndexAt(onStaff.get(i).onset())) {
                        end++;
                    }
                }
                List<Chord> run = onStaff.subList(i, end);
                groups.add(new BeamGroup(run, majorityStemDirection(run)));
                i = end;
            }
        }
        return groups;
    }

    private boolean isBeamable(Chord chord) {
        return chord.value().flagCount() > 0;
    }

    /** One beam means one stem direction for every chord under it. */
    private boolean majorityStemDirection(List<Chord> chords) {
        int up = 0;
        for (Chord chord : chords) {
            if (chord.stemUp()) {
                up++;
            }
        }
        return up * 2 >= chords.size();
    }

    /** @return which beat of the piece a moment falls in, or -1 when beats are unknown. */
    private int beatIndexAt(long millis) {
        if (beatStartMillis.isEmpty()) {
            return -1;
        }
        int low = 0;
        int high = beatStartMillis.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (beatStartMillis.get(mid) <= millis) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /** A note with its resolved staff position and any sideways nudge needed to stay legible. */
    private record Placement(ScoreNote note, ScoreSpelling.SpelledNote spelled, int headOffsetSteps,
                             int accidentalSlot) {
    }

    /** The notes of one staff struck together, which share a single stem. */
    private record Chord(boolean treble, long onset, List<Placement> placements, NoteValue value,
                         boolean stemUp) {
    }

    /** Splits the visible notes into chords sharing an onset and a staff, and lays each one out. */
    private List<Chord> placeChords(List<ScoreNote> visible, List<Integer> indices) {
        List<Chord> chords = new ArrayList<>();
        int start = 0;
        while (start < visible.size()) {
            long onset = visible.get(start).onMillis();
            int end = start;
            while (end < visible.size() && visible.get(end).onMillis() - onset <= CHORD_GROUPING_MS) {
                end++;
            }
            for (boolean treble : new boolean[]{true, false}) {
                Chord chord = placeChordOnStaff(visible, indices, start, end, treble);
                if (chord != null) {
                    chords.add(chord);
                }
            }
            start = end;
        }
        return chords;
    }

    /**
     * Lays out one chord on one staff.
     *
     * <p>Two notes a single step apart overlap by half a notehead and merge into a blob, which is
     * what makes it impossible to tell which line each is sitting on. Engraving solves this by
     * pushing the upper of the pair sideways by one notehead, and that is what happens here. The
     * displaced note does not itself displace the next one, so a run of adjacent notes zig-zags
     * rather than marching ever further to the right.
     */
    private Chord placeChordOnStaff(List<ScoreNote> visible, List<Integer> indices,
                                    int start, int end, boolean treble) {
        record Entry(ScoreNote note, ScoreSpelling.SpelledNote spelled) {
        }
        List<Entry> onStaff = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ScoreNote note = visible.get(i);
            if (useTrebleStaff(note) == treble) {
                onStaff.add(new Entry(note, spellingFor(indices.get(i), note)));
            }
        }
        if (onStaff.isEmpty()) {
            return null;
        }
        onStaff.sort(Comparator.comparingInt(entry -> entry.spelled().step()));

        int[] steps = onStaff.stream().mapToInt(entry -> entry.spelled().step()).toArray();
        boolean[] displaced = StaffNotation.displacedNoteheads(steps);

        List<Placement> placements = new ArrayList<>();
        int accidentalSlot = 0;
        long longestDuration = -1;
        NoteValue chordValue = NoteValue.QUARTER;

        for (int i = 0; i < onStaff.size(); i++) {
            Entry entry = onStaff.get(i);
            placements.add(new Placement(entry.note(), entry.spelled(), displaced[i] ? 1 : 0,
                    entry.spelled().drawAccidental() ? accidentalSlot++ : 0));

            // The chord is written as its longest note; the shorter ones inside it are voices
            // this renderer does not separate.
            long duration = entry.note().offMillis() - entry.note().onMillis();
            if (duration > longestDuration) {
                longestDuration = duration;
                chordValue = StaffNotation.noteValue(duration, quarterMillisAt(entry.note().onMillis()));
            }
        }

        int bottomStep = treble ? StaffNotation.TREBLE_BOTTOM_STEP : StaffNotation.BASS_BOTTOM_STEP;
        return new Chord(treble, visible.get(start).onMillis(), placements, chordValue,
                stemDirectionFor(placements, bottomStep));
    }

    /** The note furthest from the middle line decides which way the whole chord's stem points. */
    private static boolean stemDirectionFor(List<Placement> placements, int bottomStep) {
        int middleStep = bottomStep + StaffNotation.STAFF_SPAN_STEPS / 2;
        int lowest = placements.get(0).spelled().step();
        int highest = placements.get(placements.size() - 1).spelled().step();
        return (middleStep - lowest) >= (highest - middleStep);
    }

    private void drawBeamGroup(Graphics2D g2, BeamGroup group) {
        List<Chord> chords = group.chords();
        int gap = staffGap();

        // Everything under one beam shares a direction, so the group's stems are recomputed
        // rather than using each chord's own preference.
        boolean stemUp = group.stemUp();
        boolean beamed = chords.size() > 1 && isBeamable(chords.get(0));

        if (beamed) {
            drawBeamedRun(g2, chords, stemUp, gap);
        } else {
            for (Chord chord : chords) {
                drawSingleChord(g2, chord, chord.stemUp(), gap);
            }
        }
    }

    /** A chord on its own: stem in its own preferred direction, with a flag if it needs one. */
    private void drawSingleChord(Graphics2D g2, Chord chord, boolean stemUp, int gap) {
        int stemX = xForMillis(chord.onset());
        if (isOffScreen(stemX)) {
            return;
        }
        boolean sounding = isSounding(chord);
        if (chord.value().hasStem()) {
            int tipY = stemTipY(chord, stemUp, gap);
            drawStemLine(g2, chord, stemX, tipY, stemUp, gap, sounding);
            drawFlags(g2, stemX + stemOffset(stemUp, gap), tipY, gap, chord.value().flagCount(), stemUp);
        }
        drawChordHeads(g2, chord, stemX, stemUp, gap);
    }

    /**
     * Draws a run of chords under a shared beam. The beam is a straight line between the first
     * and last stem tips, and every stem is then lengthened or shortened to meet it — which is
     * what makes a run of quavers read as one gesture instead of a row of loose noteheads.
     */
    private void drawBeamedRun(Graphics2D g2, List<Chord> chords, boolean stemUp, int gap) {
        int firstX = xForMillis(chords.get(0).onset());
        int lastX = xForMillis(chords.get(chords.size() - 1).onset());
        if (lastX < clefAreaWidth() - 80 || firstX > getWidth() + 80) {
            return;
        }

        int stemDx = stemOffset(stemUp, gap);
        int firstTip = stemTipY(chords.get(0), stemUp, gap);
        int lastTip = stemTipY(chords.get(chords.size() - 1), stemUp, gap);

        // Keep the beam from tilting more steeply than an engraver would allow.
        int maxSlope = Math.round(gap * 1.6f);
        int midY = (firstTip + lastTip) / 2;
        int halfRise = Math.max(-maxSlope, Math.min(maxSlope, (lastTip - firstTip) / 2));
        int beamStartY = midY - halfRise;
        int beamEndY = midY + halfRise;

        // No stem may end up shorter than the beam allows, so push the whole beam clear of the
        // most extreme notehead in the run.
        int shift = 0;
        for (Chord chord : chords) {
            int x = xForMillis(chord.onset());
            int beamY = interpolate(x, firstX, beamStartY, lastX, beamEndY);
            int required = stemTipY(chord, stemUp, gap);
            shift = stemUp ? Math.min(shift, required - beamY) : Math.max(shift, required - beamY);
        }
        beamStartY += shift;
        beamEndY += shift;

        boolean anySounding = chords.stream().anyMatch(this::isSounding);
        Color color = noteColor(chords.get(0).placements().get(0).note(), anySounding);

        for (Chord chord : chords) {
            int x = xForMillis(chord.onset());
            int beamY = interpolate(x, firstX, beamStartY, lastX, beamEndY);
            g2.setColor(noteColor(chord.placements().get(0).note(), isSounding(chord)));
            g2.setStroke(new BasicStroke(Math.max(1.4f, gap * STEM_THICKNESS_RATIO)));
            g2.drawLine(x + stemDx, outerNoteY(chord, stemUp), x + stemDx, beamY);
        }

        drawBeams(g2, firstX + stemDx, beamStartY, lastX + stemDx, beamEndY,
                maxFlagCount(chords), stemUp, gap, color);

        for (Chord chord : chords) {
            drawChordHeads(g2, chord, xForMillis(chord.onset()), stemUp, gap);
        }
    }

    /** Secondary beams sit inside the first, towards the noteheads. */
    private void drawBeams(Graphics2D g2, int x1, int y1, int x2, int y2, int count,
                           boolean stemUp, int gap, Color color) {
        float thickness = gap * BEAM_THICKNESS_RATIO;
        float spacing = gap * 0.42f;
        g2.setColor(color);
        for (int beam = 0; beam < count; beam++) {
            float offset = beam * spacing * (stemUp ? 1 : -1);
            GeneralPath path = new GeneralPath();
            path.moveTo(x1, y1 + offset);
            path.lineTo(x2, y2 + offset);
            path.lineTo(x2, y2 + offset + thickness);
            path.lineTo(x1, y1 + offset + thickness);
            path.closePath();
            g2.fill(path);
        }
    }

    private static int interpolate(int x, int x1, int y1, int x2, int y2) {
        if (x2 == x1) {
            return y1;
        }
        return y1 + Math.round((float) (y2 - y1) * (x - x1) / (x2 - x1));
    }

    private int maxFlagCount(List<Chord> chords) {
        int max = 1;
        for (Chord chord : chords) {
            max = Math.max(max, chord.value().flagCount());
        }
        return max;
    }

    private boolean isSounding(Chord chord) {
        return chord.placements().stream()
                .anyMatch(placement -> placement.note().isSoundingAt(currentTimeMillis));
    }

    private boolean isOffScreen(int x) {
        return x < clefAreaWidth() - 60 || x > getWidth() + 60;
    }

    private int stemOffset(boolean stemUp, int gap) {
        return stemUp ? noteHeadWidth(gap) / 2 : -noteHeadWidth(gap) / 2;
    }

    /** Y of the notehead the stem grows from: the lowest for an up stem, the highest for a down. */
    private int outerNoteY(Chord chord, boolean stemUp) {
        List<Placement> placements = chord.placements();
        int lowest = yForStep(placements.get(0).spelled().step(), chord.treble());
        int highest = yForStep(placements.get(placements.size() - 1).spelled().step(), chord.treble());
        return stemUp ? lowest : highest;
    }

    private int stemTipY(Chord chord, boolean stemUp, int gap) {
        List<Placement> placements = chord.placements();
        int lowest = yForStep(placements.get(0).spelled().step(), chord.treble());
        int highest = yForStep(placements.get(placements.size() - 1).spelled().step(), chord.treble());
        int length = Math.round(gap * STEM_LENGTH_GAPS);
        return stemUp ? highest - length : lowest + length;
    }

    private void drawStemLine(Graphics2D g2, Chord chord, int stemX, int tipY, boolean stemUp,
                              int gap, boolean sounding) {
        g2.setColor(noteColor(chord.placements().get(0).note(), sounding));
        g2.setStroke(new BasicStroke(Math.max(1.4f, gap * STEM_THICKNESS_RATIO)));
        g2.drawLine(stemX + stemOffset(stemUp, gap), outerNoteY(chord, stemUp),
                stemX + stemOffset(stemUp, gap), tipY);
    }

    private void drawChordHeads(Graphics2D g2, Chord chord, int stemX, boolean stemUp, int gap) {
        if (isOffScreen(stemX)) {
            return;
        }
        for (Placement placement : chord.placements()) {
            drawNoteHeadWithMarkings(g2, chord, placement, stemX, stemUp, gap);
        }
    }

    private void drawNoteHeadWithMarkings(Graphics2D g2, Chord chord, Placement placement,
                                          int stemX, boolean stemUp, int gap) {
        ScoreNote note = placement.note();
        int step = placement.spelled().step();
        int headX = stemX + placement.headOffsetSteps() * noteHeadWidth(gap) * (stemUp ? 1 : -1);
        int y = yForStep(step, chord.treble());
        boolean sounding = note.isSoundingAt(currentTimeMillis);
        Color color = noteColor(note, sounding);

        g2.setColor(color);
        drawLedgerLines(g2, headX, step, chord.treble(), gap);

        if (sounding) {
            // A soft halo makes the sounding note findable at a glance.
            g2.setColor(new Color(COLOR_SOUNDING.getRed(), COLOR_SOUNDING.getGreen(),
                    COLOR_SOUNDING.getBlue(), 70));
            g2.fillOval(headX - gap, y - gap, gap * 2, gap * 2);
        }

        drawNoteHead(g2, headX, y, gap, chord.value().isFilled(), color);

        if (placement.spelled().drawAccidental()) {
            g2.setColor(color);
            int offset = Math.round(gap * (1.6f + placement.accidentalSlot() * 1.15f));
            drawAccidental(g2, placement.spelled().accidental(), headX - offset, y, gap);
        }
    }

    private ScoreSpelling.SpelledNote spellingFor(int index, ScoreNote note) {
        if (index < spelling.size()) {
            return spelling.get(index);
        }
        KeySignature.Spelled fallback = keySignature.spell(note.midiNote());
        return new ScoreSpelling.SpelledNote(fallback.step(), fallback.alteration(), false);
    }

    /**
     * Decided once for the whole visible window rather than per note, so flags do not flicker
     * on and off as the music scrolls. Judged on how far apart the notes actually are: without
     * beaming, a dense run turns into a wall of overlapping hooks that hides the noteheads.
     */
    private boolean hasRoomForFlags(List<ScoreNote> visible) {
        if (visible.size() < 3) {
            return true;
        }
        List<Long> gaps = new ArrayList<>();
        long previousOnset = visible.get(0).onMillis();
        for (ScoreNote note : visible) {
            if (note.onMillis() > previousOnset) {
                gaps.add(note.onMillis() - previousOnset);
                previousOnset = note.onMillis();
            }
        }
        if (gaps.isEmpty()) {
            return true;
        }
        Collections.sort(gaps);
        double medianGapPx = gaps.get(gaps.size() / 2) * pixelsPerMillisecond();
        return medianGapPx >= staffGap() * 2.6;
    }

    private double quarterMillisAt(long millis) {
        return timeline == null ? 500 : timeline.millisPerQuarterAtMillis(millis);
    }

    private Color noteColor(ScoreNote note, boolean sounding) {
        if (sounding) {
            return COLOR_SOUNDING;
        }
        Hand hand = note.hand();
        if (hand == Hand.LEFT) return COLOR_LEFT_HAND;
        if (hand == Hand.RIGHT) return COLOR_RIGHT_HAND;
        return COLOR_NOTE;
    }

    private int noteHeadWidth(int gap) {
        return Math.round(gap * 1.18f);
    }

    /** Noteheads are ellipses tilted the way a broad-nibbed pen would draw them. */
    private void drawNoteHead(Graphics2D g2, int x, int y, int gap, boolean filled, Color color) {
        double width = noteHeadWidth(gap);
        double height = gap * 0.98;
        Shape head = AffineTransform.getRotateInstance(Math.toRadians(-20), x, y)
                .createTransformedShape(new Ellipse2D.Double(x - width / 2, y - height / 2, width, height));

        // A rim in the paper colour keeps heads that still overlap from fusing into one blob.
        g2.setColor(COLOR_PAPER);
        g2.setStroke(new BasicStroke(Math.max(1.8f, gap * 0.18f)));
        g2.draw(head);

        g2.setColor(color);
        if (filled) {
            g2.fill(head);
        } else {
            g2.setStroke(new BasicStroke(Math.max(1.6f, gap * 0.17f)));
            g2.draw(head);
        }
    }

    /** Flags hang off the free end of the chord's single stem. */
    private void drawFlags(Graphics2D g2, int stemX, int stemEndY, int gap, int flagCount, boolean up) {
        for (int flag = 0; flag < flagCount; flag++) {
            int flagY = stemEndY + (up ? flag * Math.round(gap * 0.75f) : -flag * Math.round(gap * 0.75f));
            GeneralPath path = new GeneralPath();
            path.moveTo(stemX, flagY);
            int direction = up ? 1 : -1;
            path.quadTo(stemX + gap * 1.3, flagY + direction * gap * 0.5,
                    stemX + gap * 0.85, flagY + direction * gap * 1.6);
            path.quadTo(stemX + gap * 1.25, flagY + direction * gap * 0.55,
                    stemX, flagY + direction * gap * 0.55);
            path.closePath();
            g2.fill(path);
        }
    }

    private void drawAccidental(Graphics2D g2, int alteration, int x, int y, int gap) {
        String sign = accidentalSign(alteration);
        if (sign.isEmpty()) {
            return;
        }
        Font font = Theme.font(Font.PLAIN, Math.round(gap * 2.6f));
        if (!font.canDisplay(sign.charAt(0))) {
            return;
        }
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(sign, x - metrics.stringWidth(sign) / 2, y + Math.round(gap * 0.75f));
    }

    private static String accidentalSign(int alteration) {
        return switch (alteration) {
            case 2 -> "♯♯";
            case 1 -> "♯";
            case 0 -> "♮";
            case -1 -> "♭";
            case -2 -> "♭♭";
            default -> "";
        };
    }

    /** Short lines extending the staff for notes that sit outside it. */
    private void drawLedgerLines(Graphics2D g2, int x, int step, boolean treble, int gap) {
        int bottomStep = treble ? StaffNotation.TREBLE_BOTTOM_STEP : StaffNotation.BASS_BOTTOM_STEP;
        int topStep = bottomStep + StaffNotation.STAFF_SPAN_STEPS;
        int halfWidth = Math.round(gap * 1.05f);
        g2.setStroke(new BasicStroke(Math.max(1.2f, gap * LEDGER_THICKNESS_RATIO)));

        if (step > topStep) {
            for (int s = topStep + 2, drawn = 0; s <= step && drawn < MAX_LEDGER_LINES; s += 2, drawn++) {
                int y = yForStep(s, treble);
                g2.drawLine(x - halfWidth, y, x + halfWidth, y);
            }
        } else if (step < bottomStep) {
            for (int s = bottomStep - 2, drawn = 0; s >= step && drawn < MAX_LEDGER_LINES; s -= 2, drawn++) {
                int y = yForStep(s, treble);
                g2.drawLine(x - halfWidth, y, x + halfWidth, y);
            }
        }
    }
}

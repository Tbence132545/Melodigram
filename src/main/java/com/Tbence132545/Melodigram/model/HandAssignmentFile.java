package com.Tbence132545.Melodigram.model;
import com.Tbence132545.Melodigram.view.AnimationPanel;
import java.util.List;
public class HandAssignmentFile {
    String midiHash;
    List<AnimationPanel.HandAssignment> assignment;
    public HandAssignmentFile(String midiHash, List<AnimationPanel.HandAssignment> assignment) {
        this.midiHash = midiHash;
        this.assignment = assignment;
    }
    public String getMidiHash() {
        return midiHash;
    }
    public List<AnimationPanel.HandAssignment> getAssignment() {
        return assignment;
    }
}

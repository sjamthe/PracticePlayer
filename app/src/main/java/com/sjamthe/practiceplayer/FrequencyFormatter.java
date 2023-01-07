package com.sjamthe.practiceplayer;

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.ValueFormatter;

public class FrequencyFormatter extends ValueFormatter {
    int rootNote;
    int rootOctave;
    public FrequencyFormatter(String rootNote, String rootOctave) {
        this.rootNote = Integer.parseInt(rootNote);
        this.rootOctave = Integer.parseInt(rootOctave);
    }

    @Override
    public String getAxisLabel(float value, AxisBase axis) {
        int octave = FrequencyAnalyzer.centToOctave(value);
        int note = FrequencyAnalyzer.centToNote(value);
        String noteString = "";
        if(note == rootNote) {
            if(octave == rootOctave) {
                noteString = "Sa";
            } else if (octave == rootOctave + 1) {
                noteString = "Sa'";
            }
        }
        //String noteString = FrequencyAnalyzer.NOTES[note];
        //return noteString + Integer.toString(octave);
        return noteString;
    }
}

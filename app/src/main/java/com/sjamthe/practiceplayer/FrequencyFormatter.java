package com.sjamthe.practiceplayer;

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;

public class FrequencyFormatter extends ValueFormatter {

    public FrequencyFormatter() {
    }

    @Override
    public String getAxisLabel(float value, AxisBase axis) {
        int octave = FrequencyAnalyzer.centToOctave(value);
        int note = FrequencyAnalyzer.centToNote(value);
        String noteString = FrequencyAnalyzer.NOTES[note];
        return noteString + Integer.toString(octave);
    }
}

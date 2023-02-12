package com.sjamthe.practiceplayer;

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.ValueFormatter;

public class SwarFormatter extends ValueFormatter {

    int rootNote;
    String thaat;
    String[] thaatSwars = new String[]{"","","","","","","","","","","","","S'"};

    public SwarFormatter(String rootNote, String thaat) {
        this.rootNote = Integer.parseInt(rootNote);
        this.thaat = thaat;
        // Remove swaars that don't exist in this thaat.
        int[] thaatNotes = FrequencyAnalyzer.getThaatMap().get(this.thaat);
        int note = 0;
        thaatSwars[note] = FrequencyAnalyzer.SWARAS[note];
        for (int gap: thaatNotes) {
            note += gap/100; // gap is in cents
            if(note < FrequencyAnalyzer.SWARAS.length)
                thaatSwars[note] = FrequencyAnalyzer.SWARAS[note];
        }
    }

    @Override
    public String getAxisLabel(float value, AxisBase axis) {
        int note = (int) value;
        String swar = "";
        if (note >= 0 && note < 12) {
            swar = thaatSwars[note];
        }
        return swar;
    }
}

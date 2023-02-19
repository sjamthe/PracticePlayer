package com.sjamthe.practiceplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class DataManager {

    private final Activity activity;
    private final Context applicationContext;
    private Uri fileUri;

    public DataManager(Activity instance, Context applicationContext) {
        this.activity = instance;
        this.applicationContext = applicationContext;
    }

    void createFileInDirectory(Uri directoryUri) {
        try {
            String fileName = "SwarPracticeData.csv";
            fileUri = DocumentsContract.createDocument(applicationContext.getContentResolver(),
                    directoryUri, "text/plain", fileName);
            Log.d("DATA_MANAGER", "File created: " + fileUri.toString());
            writeToFile();
        } catch (FileNotFoundException e) {
            Toast.makeText(activity, "Error creating file", Toast.LENGTH_SHORT).show();
        }
    }

    void writeToFile() {
        try {
            ParcelFileDescriptor pfd = applicationContext.getContentResolver().openFileDescriptor(fileUri, "w");
            FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
            fileOutputStream.write("Hello, World!".getBytes());
            fileOutputStream.close();
            pfd.close();
        } catch (IOException e) {
            Toast.makeText(activity, "Error writing to file", Toast.LENGTH_SHORT).show();
        }
    }
}

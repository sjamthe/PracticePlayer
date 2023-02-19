package com.sjamthe.practiceplayer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.io.FileOutputStream;
import java.io.IOException;

public class NoteSettingsActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> createFileLauncher;
    private SettingsFragment settingsFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        settingsFragment = new SettingsFragment();
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, settingsFragment)
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        /*
        Preference uriPreference = settingsFragment.findPreference("uri_preference");
        uriPreference.setOnPreferenceClickListener(
                preference -> {
                        createFile();
                        return true;
                        });

         */

        // code to launch createDirectory intent
        createFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            Uri treeUri = data.getData();
                            if (treeUri == null) {
                                Toast.makeText(NoteSettingsActivity.this,
                                        "Error selecting file", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            getContentResolver().takePersistableUriPermission(treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            //createFileInDirectory(treeUri);
                            // Save the URI in settings
                            writeToFile(treeUri);
                        }
                    }
                });
        //
    }

    @Override
    public void onStart() {
        super.onStart();
        Uri uri = getSettingsUri();
        Preference uriPreference =  settingsFragment.findPreference("uri_preference");
        uriPreference.setSummary(uri.getAuthority());
        uriPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                       @Override
                       public boolean onPreferenceClick(Preference preference) {
                           createFile();
                           return true;
                       };
                   });
    }

    // Supports back button from Settings
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.note_preferences, rootKey);
        }

       // TextView uriText = findViewById(R.id.uriText);
    }

    private void createFile() {
        String fileName = "swargyan.csv";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        // Add flags to grant read permission and persistable permission
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        createFileLauncher.launch(intent);
    }
    /*
    private void createFileInDirectory(Uri directoryUri) {
        try {
            String fileName = "example.txt";
            // Add the "tree/" segment to the URI
            String documentTreeId = DocumentsContract.getTreeDocumentId(directoryUri);
            Uri treeUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentTreeId);
            Uri fileUri = DocumentsContract.createDocument(getContentResolver(), treeUri, "text/plain", fileName);
            Log.d("TEST", "File created: " + fileUri.toString());
            writeToFile(fileUri);
        } catch (FileNotFoundException e) {
            Toast.makeText(NoteSettingsActivity.this, "Error creating file", Toast.LENGTH_SHORT).show();
        }
    }
*/

    private void writeToFile(Uri fileUri) {
        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(fileUri, "w");
            FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
            fileOutputStream.write(fileUri.getAuthority().getBytes());
            fileOutputStream.close();
            pfd.close();
            saveSettingsUri(fileUri);
        } catch (IOException e) {
            Toast.makeText(NoteSettingsActivity.this, "Error writing to file", Toast.LENGTH_SHORT).show();
        }
    }

    void saveSettingsUri(Uri uri) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Store the Uri as a string in SharedPreferences
        sharedPreferences.edit().putString("uri_preference", uri.toString()).apply();
        PreferenceScreen preferenceScreen = settingsFragment.getPreferenceScreen();
        Preference uriPreference = preferenceScreen.findPreference("uri_preference");
        uriPreference.setSummary(uri.getAuthority());
    }

    Uri getSettingsUri() {
        // Get the SharedPreferences object
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Get the stored Uri as a string
        String uriString = sharedPreferences.getString("uri_preference", "");
        // Convert the string back to a Uri object
        return Uri.parse(uriString);
    }
}
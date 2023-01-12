package com.sjamthe.practiceplayer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class SwarPracticeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.swar_practice);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
           /*TODO: problem with this implementation as every time we click we start a new
           FullscreenActivity without aborting first one. onSupportNavigateUp finish dint work
           case R.id.scale:
                Intent i = new Intent(this, FullscreenActivity.class);
                startActivity(i);
                return true;*/
            case R.id.settings:
                Intent i = new Intent(this, NoteSettingsActivity.class);
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
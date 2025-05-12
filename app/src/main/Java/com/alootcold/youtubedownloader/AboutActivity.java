package com.alootcold.youtubedownloader;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.alootcold.youtubedownloader.util.MusicPlayer;

public class AboutActivity extends AppCompatActivity {

    private MusicPlayer musicPlayer;
    private Button easterEggButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化MusicPlayer
        musicPlayer = MusicPlayer.getInstance();

        // 设置版本号
        TextView versionTextView = findViewById(R.id.versionTextView);
        versionTextView.setText(String.format(getString(R.string.version), BuildConfig.VERSION_NAME));

        // 彩蛋按钮
        easterEggButton = findViewById(R.id.easterEggButton);
        easterEggButton.setOnClickListener(v -> {
            if (musicPlayer.isPlaying()) {
                musicPlayer.stopMusic();
            } else {
                musicPlayer.playRandomMusic(this);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (musicPlayer.isPlaying()) {
            musicPlayer.stopMusic();
        }
    }
} 
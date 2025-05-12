package com.alootcold.youtubedownloader.util;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;

import com.alootcold.youtubedownloader.R;

import java.util.Random;

public class MusicPlayer {
    private static final String TAG = "MusicPlayer";
    private static MusicPlayer instance;

    private MediaPlayer mediaPlayer;
    private int[] musicResources = {
            R.raw.lunisolar,
            R.raw.cage_ntv,
            R.raw.hope_wings,
            R.raw.mottai
    };

    private MusicPlayer() {
        // 私有构造方法
    }

    public static synchronized MusicPlayer getInstance() {
        if (instance == null) {
            instance = new MusicPlayer();
        }
        return instance;
    }

    public void playRandomMusic(Context context) {
        // 如果已经在播放，先停止
        stopMusic();

        // 随机选择一首音乐
        int randomIndex = new Random().nextInt(musicResources.length);
        int musicResId = musicResources[randomIndex];

        try {
            mediaPlayer = MediaPlayer.create(context, musicResId);
            mediaPlayer.setOnCompletionListener(mp -> stopMusic());
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Error playing music", e);
        }
    }

    public void stopMusic() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
} 
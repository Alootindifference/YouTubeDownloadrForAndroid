package com.alootcold.youtubedownloader;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alootcold.youtubedownloader.fragment.HistoryFragment;
import com.alootcold.youtubedownloader.model.DownloadItem;
import com.alootcold.youtubedownloader.util.PreferenceManager;
import com.alootcold.youtubedownloader.adapter.HistoryAdapter;

import java.util.List;

import android.os.AsyncTask;
import android.util.Log;
import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 设置工具栏
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.historyRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        // 异步获取下载历史，避免主线程阻塞
        findViewById(R.id.loadingProgressBar).setVisibility(View.VISIBLE);
        findViewById(R.id.historyRecyclerView).setVisibility(View.GONE);
        findViewById(R.id.emptyView).setVisibility(View.GONE);
        
        new Thread(() -> {
            try {
                // 获取下载历史
                preferenceManager = new PreferenceManager(this);
                List<DownloadItem> historyItems = preferenceManager.getDownloadHistory();
                
                // 修复缺失的缩略图
                fixMissingThumbnails(historyItems);
                
                // 在UI线程更新视图
                runOnUiThread(() -> {
                    findViewById(R.id.loadingProgressBar).setVisibility(View.GONE);
                    
                    if (historyItems.isEmpty()) {
                        findViewById(R.id.emptyView).setVisibility(View.VISIBLE);
                    } else {
                        findViewById(R.id.historyRecyclerView).setVisibility(View.VISIBLE);
                        // 刷新适配器
                        adapter.updateHistoryItems(historyItems);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading history", e);
                runOnUiThread(() -> {
                    findViewById(R.id.loadingProgressBar).setVisibility(View.GONE);
                    findViewById(R.id.emptyView).setVisibility(View.VISIBLE);
                    Toast.makeText(HistoryActivity.this, "加载历史记录失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    /**
     * 修复历史记录中缺失的缩略图
     */
    private void fixMissingThumbnails(List<DownloadItem> items) {
        boolean hasUpdated = false;
        
        for (DownloadItem item : items) {
            // 如果缺少缩略图，尝试使用默认YouTube缩略图
            if (item.getThumbnailUrl() == null || item.getThumbnailUrl().isEmpty()) {
                try {
                    String videoUrl = item.getUrl();
                    if (videoUrl != null && !videoUrl.isEmpty()) {
                        String youtubeId = extractYouTubeId(videoUrl);
                        
                        if (youtubeId != null && !youtubeId.isEmpty()) {
                            // 使用YouTube默认缩略图URL
                            String thumbnailUrl = "https://img.youtube.com/vi/" + youtubeId + "/0.jpg";
                            item.setThumbnailUrl(thumbnailUrl);
                            Log.d(TAG, "Fixed missing thumbnail: " + thumbnailUrl);
                            hasUpdated = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fixing thumbnail", e);
                }
            }
        }
        
        // 如果有更新，保存到偏好设置
        if (hasUpdated) {
            preferenceManager.saveDownloadHistory(items);
        }
    }
    
    /**
     * 从YouTube URL中提取视频ID
     */
    private String extractYouTubeId(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
            return null;
        }
        
        String videoId = null;
        
        // 标准YouTube URL格式：https://www.youtube.com/watch?v=VIDEO_ID
        if (youtubeUrl.contains("youtube.com/watch")) {
            try {
                android.net.Uri uri = android.net.Uri.parse(youtubeUrl);
                videoId = uri.getQueryParameter("v");
            } catch (Exception e) {
                Log.e(TAG, "Error parsing YouTube URL", e);
            }
        } 
        // 短链接格式：https://youtu.be/VIDEO_ID
        else if (youtubeUrl.contains("youtu.be/")) {
            try {
                String[] parts = youtubeUrl.split("youtu\\.be/");
                if (parts.length > 1) {
                    videoId = parts[1];
                    // 移除URL可能的参数
                    int questionMarkPos = videoId.indexOf('?');
                    if (questionMarkPos != -1) {
                        videoId = videoId.substring(0, questionMarkPos);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing YouTube short URL", e);
            }
        }
        
        return videoId;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 
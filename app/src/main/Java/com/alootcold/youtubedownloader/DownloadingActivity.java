package com.alootcold.youtubedownloader;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alootcold.youtubedownloader.adapter.DownloadingAdapter;
import com.alootcold.youtubedownloader.model.DownloadItem;
import com.alootcold.youtubedownloader.service.DownloadService;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

public class DownloadingActivity extends AppCompatActivity implements DownloadingAdapter.DownloadItemListener {

    private static final String TAG = "DownloadingActivity";
    
    private RecyclerView recyclerView;
    private DownloadingAdapter adapter;
    private TextView emptyView;
    
    private DownloadService downloadService;
    private boolean bound = false;
    
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) service;
            downloadService = binder.getService();
            bound = true;
            updateDownloadList();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };
    
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (DownloadService.ACTION_DOWNLOAD_PROGRESS.equals(action)) {
                String downloadId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                int progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0);
                String eta = intent.getStringExtra(DownloadService.EXTRA_ETA);
                
                if (adapter != null) {
                    adapter.updateDownloadProgress(downloadId, progress, eta);
                }
            } else if (DownloadService.ACTION_DOWNLOAD_COMPLETE.equals(action) ||
                     DownloadService.ACTION_DOWNLOAD_FAILED.equals(action) ||
                     DownloadService.ACTION_DOWNLOAD_CANCELED.equals(action)) {
                updateDownloadList();
            } else if (DownloadService.ACTION_DOWNLOAD_PAUSED.equals(action)) {
                String downloadId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                if (adapter != null) {
                    adapter.updateDownloadPauseState(downloadId, true);
                }
            } else if (DownloadService.ACTION_DOWNLOAD_RESUMED.equals(action)) {
                String downloadId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                if (adapter != null) {
                    adapter.updateDownloadPauseState(downloadId, false);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloading);

        // 设置工具栏
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.downloading_list);
        }

        // 初始化控件
        recyclerView = findViewById(R.id.downloadingRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        
        // 设置RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DownloadingAdapter(this);
        recyclerView.setAdapter(adapter);
        
        // 绑定下载服务
        Intent intent = new Intent(this, DownloadService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_FAILED);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_PAUSED);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_RESUMED);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_CANCELED);
        
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, filter);
    }
    
    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
        
        if (bound) {
            unbindService(serviceConnection);
            bound = false;
        }
        
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public void updateDownloadList() {
        try {
            if (bound && downloadService != null) {
                List<DownloadItem> downloads = downloadService.getActiveDownloads();
                
                if (downloads != null) {
                    // 修复缺失的缩略图
                    fixMissingThumbnails(downloads);
                    
                    runOnUiThread(() -> {
                        try {
                            if (!isFinishing() && !isDestroyed()) {
                                if (downloads.isEmpty()) {
                                    if (recyclerView != null) recyclerView.setVisibility(View.GONE);
                                    if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                                } else {
                                    if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
                                    if (emptyView != null) emptyView.setVisibility(View.GONE);
                                    if (adapter != null) adapter.updateDownloadItems(downloads);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating UI in updateDownloadList", e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in updateDownloadList", e);
            // 避免UI崩溃
            runOnUiThread(() -> {
                try {
                    Toast.makeText(this, "更新下载列表失败", Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Log.e(TAG, "Error showing toast", ex);
                }
            });
        }
    }
    
    @Override
    public void onPauseResumeClicked(DownloadItem item) {
        if (bound) {
            if (item.isPaused()) {
                downloadService.resumeDownload(item.getId());
            } else {
                downloadService.pauseDownload(item.getId());
            }
        }
    }
    
    @Override
    public void onCancelClicked(DownloadItem item) {
        if (bound) {
            downloadService.cancelDownload(item.getId());
        }
    }
    
    /**
     * 修复缺失的缩略图
     */
    private void fixMissingThumbnails(List<DownloadItem> items) {
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
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fixing thumbnail", e);
                }
            }
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
} 
package com.alootcold.youtubedownloader.fragment;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alootcold.youtubedownloader.R;
import com.alootcold.youtubedownloader.adapter.DownloadingAdapter;
import com.alootcold.youtubedownloader.model.DownloadItem;
import com.alootcold.youtubedownloader.service.DownloadService;
import com.alootcold.youtubedownloader.util.PreferenceManager;

import java.util.List;

public class DownloadingFragment extends Fragment implements DownloadingAdapter.DownloadItemListener {

    private RecyclerView downloadingRecyclerView;
    private TextView emptyDownloadingView;
    private Button clearDownloadingButton;
    private DownloadingAdapter adapter;
    private DownloadService downloadService;
    private boolean serviceBound = false;
    private PreferenceManager preferenceManager;
    private final Handler refreshHandler = new Handler();
    private static final long REFRESH_INTERVAL = 2000; // 每2秒刷新一次
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateDownloadList();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) service;
            downloadService = binder.getService();
            serviceBound = true;
            updateDownloadList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            downloadService = null;
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case DownloadService.ACTION_DOWNLOAD_PROGRESS:
                        String videoId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                        int progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0);
                        String eta = intent.getStringExtra(DownloadService.EXTRA_ETA);
                        if (adapter != null) {
                            adapter.updateDownloadProgress(videoId, progress, eta);
                        }
                        break;
                    case DownloadService.ACTION_DOWNLOAD_COMPLETE:
                        DownloadItem item = (DownloadItem) intent.getSerializableExtra(DownloadService.EXTRA_DOWNLOAD_ITEM);
                        videoId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                        
                        // 当接收到下载完成通知时，立即更新整个列表
                        // 这确保完成的下载项被正确显示，而不是立即消失
                        updateDownloadList();
                        
                        if (item != null && preferenceManager != null) {
                            preferenceManager.addDownloadToHistory(item);
                        }
                        break;
                    case DownloadService.ACTION_DOWNLOAD_FAILED:
                    case DownloadService.ACTION_DOWNLOAD_CANCELED:
                        videoId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                        if (adapter != null) {
                            // 不要立即从列表中移除，让服务来决定何时移除
                            // adapter.removeDownloadItem(videoId);
                            updateDownloadList();
                        }
                        break;
                    case DownloadService.ACTION_DOWNLOAD_PAUSED:
                        videoId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                        if (adapter != null) {
                            adapter.updateDownloadPauseState(videoId, true);
                        }
                        break;
                    case DownloadService.ACTION_DOWNLOAD_RESUMED:
                        videoId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                        if (adapter != null) {
                            adapter.updateDownloadPauseState(videoId, false);
                        }
                        break;
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloading, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        downloadingRecyclerView = view.findViewById(R.id.downloadingRecyclerView);
        emptyDownloadingView = view.findViewById(R.id.emptyDownloadingView);
        clearDownloadingButton = view.findViewById(R.id.clearDownloadingButton);

        preferenceManager = new PreferenceManager(requireContext());
        
        // 设置RecyclerView
        downloadingRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DownloadingAdapter(this);
        downloadingRecyclerView.setAdapter(adapter);

        // 设置清除按钮
        clearDownloadingButton.setOnClickListener(v -> clearAllDownloads());

        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_FAILED);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_PAUSED);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_RESUMED);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_CANCELED);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(downloadReceiver, filter);

        // 绑定下载服务
        Intent intent = new Intent(getContext(), DownloadService.class);
        requireContext().startService(intent);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // 开始定期刷新
        startPeriodicRefresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 刷新下载列表
        updateDownloadList();
        
        // 强制更新UI显示状态
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        // 恢复定期刷新
        startPeriodicRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        // 暂停定期刷新
        stopPeriodicRefresh();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser && isResumed()) {
            // 当Fragment可见时更新下载列表
            updateDownloadList();
        }
    }

    @Override
    public void onDestroyView() {
        // 停止定期刷新
        stopPeriodicRefresh();
        
        super.onDestroyView();
        // 注销广播接收器
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(downloadReceiver);

        // 解绑服务
        if (serviceBound) {
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void updateDownloadList() {
        if (serviceBound && downloadService != null) {
            List<DownloadItem> downloads = downloadService.getActiveDownloads();
            if (adapter != null) {
                adapter.updateDownloadItems(downloads);
                updateEmptyView();
            }
        } else {
            // 如果服务未绑定，尝试重新绑定
            try {
                Intent intent = new Intent(getContext(), DownloadService.class);
                requireContext().startService(intent);
                requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateEmptyView() {
        if (adapter.getItemCount() == 0) {
            emptyDownloadingView.setVisibility(View.VISIBLE);
            downloadingRecyclerView.setVisibility(View.GONE);
        } else {
            emptyDownloadingView.setVisibility(View.GONE);
            downloadingRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void clearAllDownloads() {
        if (serviceBound && downloadService != null) {
            List<DownloadItem> downloads = downloadService.getActiveDownloads();
            for (DownloadItem item : downloads) {
                downloadService.cancelDownload(item.getId());
            }
        }
    }

    @Override
    public void onPauseResumeClicked(DownloadItem item) {
        if (serviceBound && downloadService != null) {
            if (item.isPaused()) {
                downloadService.resumeDownload(item.getId());
            } else {
                downloadService.pauseDownload(item.getId());
            }
        }
    }

    @Override
    public void onCancelClicked(DownloadItem item) {
        if (serviceBound && downloadService != null) {
            downloadService.cancelDownload(item.getId());
        }
    }

    private void startPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }
    
    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }
} 
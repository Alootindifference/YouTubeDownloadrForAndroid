package com.alootcold.youtubedownloader.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
import com.alootcold.youtubedownloader.adapter.HistoryAdapter;
import com.alootcold.youtubedownloader.model.DownloadItem;
import com.alootcold.youtubedownloader.service.DownloadService;
import com.alootcold.youtubedownloader.util.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {
    private static final String TAG = "HistoryFragment";
    private static final long REFRESH_INTERVAL = 5000; // 每5秒刷新一次

    private RecyclerView historyRecyclerView;
    private TextView emptyHistoryView;
    private Button clearHistoryButton;
    private HistoryAdapter adapter;
    private PreferenceManager preferenceManager;
    private final Handler refreshHandler = new Handler();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadHistory();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL);
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case DownloadService.ACTION_DOWNLOAD_COMPLETE:
                    case DownloadService.ACTION_DOWNLOAD_FAILED:
                        // 下载完成或失败时都刷新历史记录
                        loadHistory();
                        break;
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView);
        emptyHistoryView = view.findViewById(R.id.emptyHistoryView);
        clearHistoryButton = view.findViewById(R.id.clearHistoryButton);
        
        preferenceManager = new PreferenceManager(requireContext());
        
        // 设置RecyclerView
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistoryAdapter();
        historyRecyclerView.setAdapter(adapter);

        // 设置清除按钮
        clearHistoryButton.setOnClickListener(v -> clearHistory());

        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_FAILED);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(downloadReceiver, filter);

        // 首次加载历史记录
        loadHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadHistory();
        startPeriodicRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPeriodicRefresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPeriodicRefresh();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(downloadReceiver);
    }

    private void startPeriodicRefresh() {
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
    }

    private void stopPeriodicRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void loadHistory() {
        try {
            List<DownloadItem> historyItems = preferenceManager.getDownloadHistory();
            Log.d(TAG, "Loaded " + historyItems.size() + " history items");
            
            // 过滤掉未完成或标题为"正在获取视频信息..."的项
            List<DownloadItem> validItems = new ArrayList<>();
            for (DownloadItem item : historyItems) {
                if (!item.getTitle().equals("正在获取视频信息...") && item.isCompleted()) {
                    validItems.add(item);
                }
            }
            
            if (isAdded() && getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    try {
                        if (adapter != null) {
                            adapter.updateHistoryItems(validItems);
                        }
                        
                        if (validItems.isEmpty()) {
                            if (emptyHistoryView != null && historyRecyclerView != null) {
                                emptyHistoryView.setVisibility(View.VISIBLE);
                                historyRecyclerView.setVisibility(View.GONE);
                            }
                        } else {
                            if (emptyHistoryView != null && historyRecyclerView != null) {
                                emptyHistoryView.setVisibility(View.GONE);
                                historyRecyclerView.setVisibility(View.VISIBLE);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error updating UI with history items", e);
                    }
                });
            } else {
                Log.w(TAG, "Fragment not attached, cannot update UI");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading history", e);
        }
    }

    private void clearHistory() {
        try {
            preferenceManager.clearDownloadHistory();
            loadHistory();
        } catch (Exception e) {
            Log.e(TAG, "Error clearing history", e);
        }
    }
} 
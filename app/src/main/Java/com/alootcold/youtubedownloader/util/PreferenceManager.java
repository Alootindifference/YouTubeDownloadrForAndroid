package com.alootcold.youtubedownloader.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.alootcold.youtubedownloader.model.DownloadItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PreferenceManager {
    private static final String TAG = "PreferenceManager";
    private static final String PREF_NAME = "youtube_downloader_prefs";
    private static final String KEY_DOWNLOAD_HISTORY = "download_history";

    private final SharedPreferences sharedPreferences;
    private final Gson gson;

    public PreferenceManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public boolean saveDownloadHistory(List<DownloadItem> historyList) {
        try {
            if (historyList == null) {
                Log.e(TAG, "Cannot save null history list");
                return false;
            }
            
            String json = gson.toJson(historyList);
            Log.d(TAG, "Saving download history: " + historyList.size() + " items");
            boolean success = sharedPreferences.edit().putString(KEY_DOWNLOAD_HISTORY, json).commit();
            Log.d(TAG, "Save result: " + success);
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Error saving download history", e);
            return false;
        }
    }

    public List<DownloadItem> getDownloadHistory() {
        try {
            String json = sharedPreferences.getString(KEY_DOWNLOAD_HISTORY, null);
            Log.d(TAG, "Loading download history: " + json);
            if (json == null) {
                return new ArrayList<>();
            }
            Type type = new TypeToken<List<DownloadItem>>() {}.getType();
            List<DownloadItem> historyList = gson.fromJson(json, type);
            Log.d(TAG, "Loaded " + (historyList != null ? historyList.size() : 0) + " items");
            return historyList != null ? historyList : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error loading download history", e);
            return new ArrayList<>();
        }
    }

    public void addDownloadToHistory(DownloadItem item) {
        try {
            if (item == null) {
                Log.e(TAG, "Cannot add null item to history");
                return;
            }
            
            Log.d(TAG, "Adding item to history: " + item.getId() + " - " + item.getTitle());
            
            List<DownloadItem> historyList = getDownloadHistory();
            // 检查是否已存在
            boolean existed = false;
            for (int i = 0; i < historyList.size(); i++) {
                if (historyList.get(i).getId().equals(item.getId())) {
                    historyList.remove(i);
                    existed = true;
                    Log.d(TAG, "Replacing existing item in history");
                    break;
                }
            }
            
            // 确保下载日期已设置
            if (item.getDownloadDate() == 0) {
                item.setDownloadDate(System.currentTimeMillis());
            }
            
            // 添加到列表头部
            historyList.add(0, item);
            boolean saved = saveDownloadHistory(historyList);
            Log.d(TAG, "Added item to history: " + item.getTitle() + ", saved: " + saved + 
                  ", history size: " + historyList.size());
        } catch (Exception e) {
            Log.e(TAG, "Error adding download to history", e);
        }
    }

    public void clearDownloadHistory() {
        try {
            boolean success = sharedPreferences.edit().remove(KEY_DOWNLOAD_HISTORY).commit();
            Log.d(TAG, "Clear history result: " + success);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing download history", e);
        }
    }
} 
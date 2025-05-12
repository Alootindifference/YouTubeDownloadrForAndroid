package com.alootcold.youtubedownloader.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import android.content.pm.ServiceInfo;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.alootcold.youtubedownloader.MainActivity;
import com.alootcold.youtubedownloader.R;
import com.alootcold.youtubedownloader.model.DownloadItem;
import com.alootcold.youtubedownloader.util.PreferenceManager;
import com.yausername.youtubedl_android.DownloadProgressCallback;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.AlertDialog;

import java.lang.reflect.Method;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DownloadService extends Service {

    private static final String TAG = "DownloadService";
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1;
    // 为Android 14定义常量，用于兼容低版本API编译
    private static final int UPSIDE_DOWN_CAKE = 34;

    public static final String ACTION_DOWNLOAD_PROGRESS = "com.alootcold.youtubedownloader.DOWNLOAD_PROGRESS";
    public static final String ACTION_DOWNLOAD_COMPLETE = "com.alootcold.youtubedownloader.DOWNLOAD_COMPLETE";
    public static final String ACTION_DOWNLOAD_FAILED = "com.alootcold.youtubedownloader.DOWNLOAD_FAILED";
    public static final String ACTION_DOWNLOAD_PAUSED = "com.alootcold.youtubedownloader.DOWNLOAD_PAUSED";
    public static final String ACTION_DOWNLOAD_RESUMED = "com.alootcold.youtubedownloader.DOWNLOAD_RESUMED";
    public static final String ACTION_DOWNLOAD_CANCELED = "com.alootcold.youtubedownloader.DOWNLOAD_CANCELED";
    public static final String ACTION_START_DOWNLOAD = "com.alootcold.youtubedownloader.START_DOWNLOAD";

    public static final String EXTRA_DOWNLOAD_ID = "download_id";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_ETA = "eta";
    public static final String EXTRA_DOWNLOAD_ITEM = "download_item";
    public static final String EXTRA_ERROR_MESSAGE = "error_message";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FORMAT = "format";

    private final Map<String, DownloadItem> downloads = new HashMap<>();
    private final Map<String, Future<?>> downloadTasks = new HashMap<>();
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final DownloadBinder binder = new DownloadBinder();
    private LocalBroadcastManager broadcaster;

    // 添加截流处理相关变量
    private final Handler handler = new Handler();
    private final Map<String, Long> lastProgressUpdateTime = new HashMap<>();
    private static final long PROGRESS_UPDATE_THROTTLE_MS = 500; // 每0.5秒最多更新一次UI

    // 添加常量定义下载完成后的停留时间
    private static final long COMPLETED_ITEM_RETENTION_MS = 10000; // 下载完成后保留10秒
    
    // 添加保存已完成下载项的列表
    private final Map<String, DownloadItem> completedDownloads = new HashMap<>();
    private final Map<String, Runnable> removalRunnables = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        broadcaster = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null) {
                String action = intent.getAction();
                if (ACTION_START_DOWNLOAD.equals(action)) {
                    String url = intent.getStringExtra(EXTRA_URL);
                    String format = intent.getStringExtra(EXTRA_FORMAT);
                    if (url != null && format != null) {
                        try {
                            // 根据Android版本处理前台服务启动
                            Notification notification = createNotification("准备下载...");
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                // Android 14+
                                Log.d(TAG, "Starting foreground service on Android 14+");
                                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // Android 10-13
                                Log.d(TAG, "Starting foreground service on Android 10-13");
                                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                            } else {
                                // Android 9及以下
                                Log.d(TAG, "Starting foreground service on Android 9 or below");
                                startForeground(NOTIFICATION_ID, notification);
                            }
                            
                            // 创建一个临时的DownloadItem对象
                            DownloadItem item = new DownloadItem(
                                url,                    // url
                                format,                 // format
                                "正在获取视频信息...",    // title
                                "准备下载",              // status
                                true                    // isDownload
                            );
                            startDownload(item);
                        } catch (SecurityException se) {
                            Log.e(TAG, "Security exception starting foreground service", se);
                            Toast.makeText(getApplicationContext(), 
                                "需要前台服务权限！请重新安装应用或在设置中授予权限。", 
                                Toast.LENGTH_LONG).show();
                            
                            // 尝试通知用户，但不使用前台服务
                            broadcastDownloadFailed("temp_" + System.currentTimeMillis(), 
                                "权限错误: 无法启动下载服务。\n\n需要前台服务权限！请重新安装应用或在设置中授予权限。\n\n详细信息:\n" + se.getMessage());
                            
                            // 终止服务
                            stopSelf();
                        }
                    } else {
                        Log.e(TAG, "URL or format is null");
                        Toast.makeText(getApplicationContext(), "无效的下载参数", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand", e);
            Toast.makeText(getApplicationContext(), "下载服务启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        // 清除所有挂起的任务
        handler.removeCallbacksAndMessages(null);
        for (Runnable runnable : removalRunnables.values()) {
            handler.removeCallbacks(runnable);
        }
        removalRunnables.clear();
        
        compositeDisposable.dispose();
        super.onDestroy();
    }

    public void startDownload(DownloadItem item) {
        try {
            String videoId = item.getId();
            if (downloads.containsKey(videoId)) {
                Log.w(TAG, "Download already in progress for video ID: " + videoId);
                return;
            }

            downloads.put(videoId, item);
            updateNotification(item, 0);
            broadcastDownloadProgress(videoId, 0, "");
            
            // 重复发送进度更新，确保UI能收到至少一次
            handler.postDelayed(() -> {
                if (downloads.containsKey(videoId)) {
                    broadcastDownloadProgress(videoId, item.getProgress(), item.getEta());
                }
            }, 1000);

            // 尝试获取视频信息（包括缩略图）
            new Thread(() -> {
                try {
                    // 只有当缩略图URL为空时才获取
                    if (item.getThumbnailUrl() == null || item.getThumbnailUrl().isEmpty()) {
                        // 获取视频信息
                        YoutubeDL.getInstance().init(getApplicationContext());
                        YoutubeDLRequest infoRequest = new YoutubeDLRequest(item.getUrl());
                        infoRequest.addOption("--dump-json");
                        infoRequest.addOption("--no-playlist");
                        infoRequest.addOption("--flat-playlist");
                        
                        try {
                            // 获取视频信息，然后转换为JSON字符串
                            com.yausername.youtubedl_android.mapper.VideoInfo videoInfo = YoutubeDL.getInstance().getInfo(infoRequest);
                            
                            // 尝试多种方法获取JSON数据
                            String videoInfoJson = null;
                            try {
                                // 尝试使用反射获取getJson方法
                                java.lang.reflect.Method getJsonMethod = videoInfo.getClass().getMethod("getJson");
                                videoInfoJson = (String) getJsonMethod.invoke(videoInfo);
                            } catch (Exception e) {
                                Log.d(TAG, "getJson方法不存在，尝试其他方法", e);
                                
                                // 尝试使用toString方法
                                videoInfoJson = videoInfo.toString();
                                
                                // 检查返回的字符串是否为JSON格式
                                if (videoInfoJson != null && !videoInfoJson.startsWith("{") && !videoInfoJson.endsWith("}")) {
                                    Log.d(TAG, "toString不是JSON格式，尝试获取其他字段");
                                    
                                    // 尝试获取原始信息
                                    try {
                                        // 尝试使用反射获取原始json字段
                                        java.lang.reflect.Field jsonField = videoInfo.getClass().getDeclaredField("json");
                                        jsonField.setAccessible(true);
                                        Object jsonObject = jsonField.get(videoInfo);
                                        if (jsonObject != null) {
                                            videoInfoJson = jsonObject.toString();
                                        }
                                    } catch (Exception ex) {
                                        Log.e(TAG, "无法通过反射获取json字段", ex);
                                    }
                                }
                            }
                            
                            // 如果所有获取JSON的尝试都失败，尝试从VideoInfo对象直接获取信息
                            if (videoInfoJson == null || videoInfoJson.isEmpty()) {
                                Log.d(TAG, "无法获取JSON数据，尝试直接获取字段");
                                
                                // 如果当前标题是默认的，尝试直接获取标题
                                if (item.getTitle().equals("正在获取视频信息...")) {
                                    try {
                                        String title = videoInfo.getTitle(); // 直接使用getTitle方法
                                        if (title != null && !title.isEmpty()) {
                                            item.setTitle(title);
                                            Log.d(TAG, "从VideoInfo直接获取标题: " + title);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "无法从VideoInfo获取标题", e);
                                    }
                                }
                                
                                // 尝试直接获取缩略图URL
                                try {
                                    // 尝试使用反射查找getThumbnail或getThumbnailUrl方法
                                    for (String methodName : new String[]{"getThumbnail", "getThumbnailUrl", "getThumbnail_url"}) {
                                        try {
                                            java.lang.reflect.Method thumbMethod = videoInfo.getClass().getMethod(methodName);
                                            Object thumbObj = thumbMethod.invoke(videoInfo);
                                            if (thumbObj != null && thumbObj instanceof String) {
                                                String thumbnailUrl = (String) thumbObj;
                                                if (!thumbnailUrl.isEmpty()) {
                                                    item.setThumbnailUrl(thumbnailUrl);
                                                    Log.d(TAG, "从VideoInfo直接获取缩略图URL: " + thumbnailUrl);
                                                    break;
                                                }
                                            }
                                        } catch (Exception e) {
                                            // 继续尝试下一个方法名
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "无法从VideoInfo获取缩略图", e);
                                }
                                
                                // 如果无法获取缩略图，使用YouTube ID回退方案
                                if (item.getThumbnailUrl() == null || item.getThumbnailUrl().isEmpty()) {
                                    String youtubeId = extractYouTubeId(item.getUrl());
                                    if (youtubeId != null && !youtubeId.isEmpty()) {
                                        String thumbnailUrl = "https://img.youtube.com/vi/" + youtubeId + "/0.jpg";
                                        item.setThumbnailUrl(thumbnailUrl);
                                        Log.d(TAG, "使用YouTube ID设置缩略图URL: " + thumbnailUrl);
                                    }
                                }
                                
                                // 更新通知和广播进度
                                if (downloads.containsKey(videoId)) {
                                    handler.post(() -> {
                                        updateNotification(item, item.getProgress());
                                        broadcastDownloadProgress(videoId, item.getProgress(), item.getEta());
                                    });
                                }
                            } else {
                                try {
                                    JSONObject json = new JSONObject(videoInfoJson);
                                    
                                    // 提取视频标题（如果当前标题是默认的）
                                    if (item.getTitle().equals("正在获取视频信息...") && json.has("title")) {
                                        String title = json.getString("title");
                                        item.setTitle(title);
                                        Log.d(TAG, "Updated title to: " + title);
                                    }
                                    
                                    // 提取视频缩略图
                                    if (json.has("thumbnail")) {
                                        String thumbnailUrl = json.getString("thumbnail");
                                        item.setThumbnailUrl(thumbnailUrl);
                                        Log.d(TAG, "Set thumbnail URL: " + thumbnailUrl);
                                    } else if (json.has("thumbnails") && json.getJSONArray("thumbnails").length() > 0) {
                                        JSONArray thumbnails = json.getJSONArray("thumbnails");
                                        JSONObject bestThumbnail = thumbnails.getJSONObject(thumbnails.length() - 1);
                                        if (bestThumbnail.has("url")) {
                                            String thumbnailUrl = bestThumbnail.getString("url");
                                            item.setThumbnailUrl(thumbnailUrl);
                                            Log.d(TAG, "Set best thumbnail URL: " + thumbnailUrl);
                                        }
                                    }
                                    
                                    // 更新通知并广播进度
                                    if (downloads.containsKey(videoId)) {
                                        handler.post(() -> {
                                            updateNotification(item, item.getProgress());
                                            broadcastDownloadProgress(videoId, item.getProgress(), item.getEta());
                                        });
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "Error parsing video info JSON", e);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to get video info: " + e.getMessage(), e);
                            
                            // 后备方案：尝试使用YouTube视频ID来设置缩略图
                            String youtubeId = extractYouTubeId(item.getUrl());
                            if (youtubeId != null && !youtubeId.isEmpty()) {
                                String thumbnailUrl = "https://img.youtube.com/vi/" + youtubeId + "/0.jpg";
                                item.setThumbnailUrl(thumbnailUrl);
                                Log.d(TAG, "Set fallback thumbnail URL: " + thumbnailUrl);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error getting video info", e);
                    // 不要因为缩略图获取失败而中断主下载流程
                }
            }).start();

            Future<?> downloadTask = Executors.newSingleThreadExecutor().submit(() -> {
                String downloadDir;
                try {
                    // 初始化YouTube-DL
                    try {
                        YoutubeDL.getInstance().init(getApplicationContext());
                        Log.d(TAG, "YouTube-DL initialized successfully");
                        
                        // 尝试更新youtube-dl，解决"Sign in to confirm you're not a bot"问题
                        updateYoutubeDL();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to initialize YouTube-DL", e);
                        throw new Exception("YouTube-DL初始化失败: " + e.getMessage());
                    }

                    // 确保DCIM目录存在
                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    File youtubeDCIMDir = new File(dcimDir, "YouTubeDownloads");
                    if (!youtubeDCIMDir.exists()) {
                        boolean dirCreated = youtubeDCIMDir.mkdirs();
                        if (!dirCreated) {
                            Log.e(TAG, "Failed to create DCIM/YouTubeDownloads directory");
                            throw new Exception("无法创建下载目录 DCIM/YouTubeDownloads");
                        }
                    }

                    // 使用DCIM目录作为下载路径
                    downloadDir = youtubeDCIMDir.getAbsolutePath();
                    
                    // 检查下载目录权限
                    if (!youtubeDCIMDir.canWrite()) {
                        throw new Exception("没有DCIM目录的写入权限");
                    }

                    File youtubeDLDir = new File(downloadDir, "YouTubeDownloads");
                    if (!youtubeDLDir.exists()) {
                        boolean dirCreated = youtubeDLDir.mkdirs(); // 使用mkdirs()而不是mkdir()，创建多级目录
                        if (!dirCreated) {
                            throw new Exception("无法创建下载目录");
                        }
                    }

                    YoutubeDLRequest request = new YoutubeDLRequest(item.getUrl());
                    
                    // 使用用户选择的格式而不是固定的best格式
                    String formatOption = item.getFormat();
                    if (formatOption == null || formatOption.isEmpty()) {
                        formatOption = "best";  // 默认使用最佳质量
                    }
                    
                    // 修改为更安全的格式选择，添加回退选项
                    // 避免"Requested format is not available"错误
                    if (formatOption.equals("best")) {
                        // 使用更可靠的格式字符串，优先选择最高质量视频+音频
                        // 添加多个分辨率选项，按质量降序排列
                        formatOption = "bestvideo[height>=1080]+bestaudio/bestvideo+bestaudio/best";
                    } else if (formatOption.equals("bestvideo+bestaudio")) {
                        // 确保能获取最高质量的视频
                        formatOption = "bestvideo[height>=1080]+bestaudio/bestvideo+bestaudio/best";
                    } else if (formatOption.contains("1080")) {
                        // 对于1080p，添加可能的更高分辨率选项
                        formatOption = "bestvideo[height>=1080]+bestaudio/bestvideo[height=1080]+bestaudio/best[height>=1080]/best";
                    } else if (formatOption.contains("720")) {
                        // 对于720p，尝试获取至少720p的视频
                        formatOption = "bestvideo[height>=720]+bestaudio/bestvideo[height=720]+bestaudio/best[height>=720]/best";
                    }
                    
                    Log.d(TAG, "Using format option: " + formatOption);
                    request.addOption("--format", formatOption);
                    
                    // 首先尝试获取可用格式列表
                    try {
                        Log.d(TAG, "Checking available formats for: " + item.getUrl());
                        YoutubeDLRequest formatRequest = new YoutubeDLRequest(item.getUrl());
                        formatRequest.addOption("--list-formats");
                        formatRequest.addOption("--no-playlist");
                        formatRequest.addOption("--no-warnings");
                        // 添加绕过YouTube限制的选项
                        addBypassOptions(formatRequest);
                        
                        // 尝试使用反射调用getCommandOutput方法，因为这个方法可能不存在于所有版本的库中
                        String formatsOutput = "";
                        try {
                            Method getCommandOutputMethod = YoutubeDL.class.getMethod("getCommandOutput", YoutubeDLRequest.class);
                            formatsOutput = (String) getCommandOutputMethod.invoke(YoutubeDL.getInstance(), formatRequest);
                            Log.d(TAG, "Available formats: " + formatsOutput);
                            
                            // 如果输出包含错误信息，尝试更简单的格式
                            if (formatsOutput.contains("ERROR") || formatsOutput.contains("error")) {
                                Log.w(TAG, "Error in formats list, switching to basic format");
                                formatOption = "bestvideo[height>=720]+bestaudio/best[height>=720]/best";
                                request.addOption("--format", formatOption);
                            }
                        } catch (NoSuchMethodException methodEx) {
                            // getCommandOutput方法不存在，使用回退格式
                            Log.w(TAG, "getCommandOutput method not available, using fallback format", methodEx);
                            formatOption = "bestvideo[height>=720]+bestaudio/best[height>=720]/best";
                            request.addOption("--format", formatOption);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error checking formats, using fallback format", e);
                        // 出错时使用更通用的回退格式
                        formatOption = "bestvideo[height>=720]+bestaudio/best[height>=720]/best";
                        request.addOption("--format", formatOption);
                    }
                    
                    // 基本选项
                    request.addOption("--no-warnings");
                    request.addOption("--no-playlist");
                    request.addOption("--prefer-ffmpeg");
                    
                    // 添加额外的debug选项
                    request.addOption("--verbose");
                    
                    // 设置文件名模板，使用youtube-dl的安全文件名功能
                    String outputTemplate = youtubeDLDir.getAbsolutePath() + "/%(title)s.%(ext)s";
                    request.addOption("-o", outputTemplate);
                    
                    // 添加自动匹配字幕选项
                    request.addOption("--write-auto-sub");
                    
                    // 添加绕过YouTube限制的选项
                    addBypassOptions(request);
                    
                    // 在Android 10+上添加额外选项以处理权限问题
                    if (Build.VERSION.SDK_INT >= 29) {
                        request.addOption("--no-mtime");
                    }

                    Log.d(TAG, "Starting download for: " + item.getTitle());
                    Log.d(TAG, "Download directory: " + youtubeDLDir.getAbsolutePath());
                    Log.d(TAG, "Video URL: " + item.getUrl());
                    Log.d(TAG, "Format option: " + item.getFormat());
                    
                    try {
                        YoutubeDL.getInstance().execute(
                                request,
                                videoId,
                                (progress, etaInSeconds, line) -> {
                                    // 确保进度值在0-100之间
                                    int progressPercent = Math.min(100, Math.max(0, (int) (progress * 100)));
                                    // 记录原始进度值和处理后的进度值，用于调试
                                    if (progress > 1.0) {
                                        Log.w(TAG, "Abnormal progress value detected: " + progress + 
                                              " -> corrected to: " + progressPercent + "%");
                                    }
                                    
                                    item.setProgress(progressPercent);
                                    item.setEta(formatEta(etaInSeconds));
                                    
                                    // 记录下载进度日志
                                    if (progressPercent % 10 == 0) {
                                        Log.d(TAG, "Download progress: " + progressPercent + "% - " + item.getTitle());
                                    }
                                    
                                    if (line != null && !line.isEmpty()) {
                                        Log.d(TAG, "YoutubeDL output: " + line);
                                    }
                                    
                                    // 截流处理，避免过于频繁的UI更新
                                    long currentTime = System.currentTimeMillis();
                                    Long lastUpdate = lastProgressUpdateTime.get(videoId);
                                    if (lastUpdate == null || (currentTime - lastUpdate) >= PROGRESS_UPDATE_THROTTLE_MS) {
                                        updateNotification(item, progressPercent);
                                        broadcastDownloadProgress(videoId, progressPercent, formatEta(etaInSeconds));
                                        lastProgressUpdateTime.put(videoId, currentTime);
                                    }
                                    
                                    return null;
                                }
                        );
                    } catch (Exception e) {
                        Log.e(TAG, "Exception during YoutubeDL execute: " + e.getMessage(), e);
                        
                        // 检查是否是格式不可用错误
                        if (e.getMessage() != null && (e.getMessage().contains("Requested format is not available") 
                                || e.getMessage().contains("format not available"))) {
                            
                            Log.w(TAG, "Format not available error, trying with simpler format");
                            
                            // 尝试使用更简单的格式重试下载
                            try {
                                // 创建新的请求，使用更高清晰度的格式
                                YoutubeDLRequest retryRequest = new YoutubeDLRequest(item.getUrl());
                                // 尝试获取更高清晰度的视频 (720p或更高)
                                retryRequest.addOption("--format", "bestvideo[height>=720]+bestaudio/bestvideo+bestaudio/best");
                                
                                // 复制其他基本选项
                                retryRequest.addOption("--no-warnings");
                                retryRequest.addOption("--no-playlist");
                                retryRequest.addOption("--prefer-ffmpeg");
                                retryRequest.addOption("--verbose");
                                
                                // 设置输出模板
                                String retryOutputTemplate = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), 
                                        "YouTubeDownloads/YouTubeDownloads/%(title)s.%(ext)s").getAbsolutePath();
                                retryRequest.addOption("-o", retryOutputTemplate);
                                
                                // 添加其他选项
                                retryRequest.addOption("--write-auto-sub");
                                addBypassOptions(retryRequest);
                                
                                // 在Android 10+上添加额外选项以处理权限问题
                                if (Build.VERSION.SDK_INT >= 29) {
                                    retryRequest.addOption("--no-mtime");
                                }
                                
                                Log.d(TAG, "Retrying download with format: bestvideo[height>=720]+bestaudio/bestvideo+bestaudio/best");
                                
                                // 执行重试下载
                                YoutubeDL.getInstance().execute(
                                        retryRequest,
                                        videoId,
                                        (progress, etaInSeconds, line) -> {
                                            // 与上面相同的进度处理逻辑
                                            int progressPercent = Math.min(100, Math.max(0, (int) (progress * 100)));
                                            item.setProgress(progressPercent);
                                            item.setEta(formatEta(etaInSeconds));
                                            
                                            long currentTime = System.currentTimeMillis();
                                            Long lastUpdate = lastProgressUpdateTime.get(videoId);
                                            if (lastUpdate == null || (currentTime - lastUpdate) >= PROGRESS_UPDATE_THROTTLE_MS) {
                                                updateNotification(item, progressPercent);
                                                broadcastDownloadProgress(videoId, progressPercent, formatEta(etaInSeconds));
                                                lastProgressUpdateTime.put(videoId, currentTime);
                                            }
                                            
                                            return null;
                                        }
                                );
                                
                                // 如果重试成功，直接返回，不抛出原始异常
                                return;
                                
                            } catch (Exception retryEx) {
                                Log.e(TAG, "Retry download also failed", retryEx);
                                // 重试也失败，继续处理原始异常
                            }
                        }
                        
                        // 检查是否是机器人验证错误
                        if (e.getMessage() != null && e.getMessage().contains("Sign in to confirm you're not a bot")) {
                            throw new Exception("YouTube需要验证您不是机器人。请尝试以下解决方法：\n\n" +
                                    "1. 在浏览器中登录您的YouTube账号\n" +
                                    "2. 打开需要下载的视频，正常观看一会儿\n" +
                                    "3. 更新应用程序以获取最新的下载引擎\n" +
                                    "4. 使用VPN或更换网络连接");
                        }
                        throw e;
                    }

                    // youtube-dl会处理文件名和扩展名，所以我们不能确定确切的文件名和扩展名
                    // 我们需要扫描下载目录查找新文件
                    File[] files = youtubeDLDir.listFiles();
                    if (files != null && files.length > 0) {
                        long latestModified = 0;
                        File latestFile = null;
                        
                        for (File file : files) {
                            if (file.lastModified() > latestModified) {
                                latestModified = file.lastModified();
                                latestFile = file;
                            }
                        }
                        
                        if (latestFile != null) {
                            Log.d(TAG, "Downloaded file: " + latestFile.getAbsolutePath());
                            
                            // 更新标题为实际文件名(如果当前标题是默认的)
                            if (item.getTitle().equals("正在获取视频信息...")) {
                                String fileName = latestFile.getName();
                                // 移除扩展名
                                int dotIndex = fileName.lastIndexOf(".");
                                if (dotIndex > 0) {
                                    fileName = fileName.substring(0, dotIndex);
                                }
                                item.setTitle(fileName);
                                Log.d(TAG, "Updated title to: " + fileName);
                            }
                            
                            // 扫描文件添加到媒体库
                            MediaScannerConnection.scanFile(
                                    getApplicationContext(),
                                    new String[]{latestFile.getAbsolutePath()},
                                    null,
                                    (path, uri) -> {
                                        Log.i(TAG, "Media scanned: " + path);
                                        Log.i(TAG, "Uri: " + uri);
                                    }
                            );
                        } else {
                            // 如果找不到任何文件，抛出异常
                            throw new Exception("下载完成但找不到任何文件");
                        }
                    } else {
                        // 如果目录为空，抛出异常
                        throw new Exception("下载完成但下载目录为空");
                    }

                    item.setProgress(100);
                    item.setCompleted(true);
                    item.setDownloadDate(System.currentTimeMillis());
                    downloads.remove(videoId);
                    downloadTasks.remove(videoId);
                    broadcastDownloadComplete(item);
                    showDownloadCompleteToast(item.getTitle());

                } catch (Exception e) {
                    Log.e(TAG, "Failed to download video: " + videoId, e);
                    Log.e(TAG, "Error message: " + e.getMessage());
                    Log.e(TAG, "Stack trace: " + Log.getStackTraceString(e));
                    Log.e(TAG, "Video URL: " + item.getUrl());
                    Log.e(TAG, "Video format: " + item.getFormat());
                    
                    item.setCompleted(false);
                    downloads.remove(videoId);
                    downloadTasks.remove(videoId);
                    
                    // 构建更详细的错误信息
                    String errorMessage = e.getMessage();
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        errorMessage = "未知错误";
                    }
                    String detailedError = String.format("下载失败: %s\n\n详细信息:\n%s\n\n视频URL: %s\n格式: %s", 
                        errorMessage, Log.getStackTraceString(e), item.getUrl(), item.getFormat());
                    
                    broadcastDownloadFailed(videoId, detailedError);
                    
                    // 在主线程显示错误信息toast
                    handler.post(() -> {
                        Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.download_failed) + ": " + item.getTitle(),
                            Toast.LENGTH_LONG
                        ).show();
                    });
                }
            });

            downloadTasks.put(videoId, downloadTask);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start download", e);
            Toast.makeText(getApplicationContext(), "启动下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 尝试更新youtube-dl
     */
    private void updateYoutubeDL() {
        try {
            // 直接创建一个UpdateChannel类的引用变量
            Class<?> updateChannelClass = null;
            Enum<?> updateChannelEnum = null;
            
            try {
                // 尝试获取UpdateChannel内部类
                Class<?>[] declaredClasses = YoutubeDL.class.getDeclaredClasses();
                for (Class<?> declaredClass : declaredClasses) {
                    if (declaredClass.getSimpleName().equals("UpdateChannel")) {
                        updateChannelClass = declaredClass;
                        break;
                    }
                }
                
                // 获取枚举常量
                if (updateChannelClass != null && updateChannelClass.isEnum()) {
                    Object[] enumConstants = updateChannelClass.getEnumConstants();
                    if (enumConstants != null && enumConstants.length > 0) {
                        // 尝试使用第一个枚举值
                        updateChannelEnum = (Enum<?>) enumConstants[0];
                        Log.d(TAG, "Found UpdateChannel enum value: " + updateChannelEnum);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting UpdateChannel values", e);
            }
            
            // 如果无法使用反射获取枚举值，则完全跳过更新
            if (updateChannelEnum == null) {
                Log.w(TAG, "Could not find UpdateChannel enum, skipping update");
                return;
            }
            
            // 使用找到的枚举值，带强制类型转换
            Method updateMethod = YoutubeDL.class.getMethod("updateYoutubeDL", Context.class, updateChannelClass);
            updateMethod.invoke(YoutubeDL.getInstance(), getApplicationContext(), updateChannelEnum);
            
            Log.d(TAG, "YouTube-DL updated successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to update YouTube-DL", e);
            // 不抛出异常，继续尝试下载
        }
    }
    
    /**
     * 添加绕过YouTube限制的选项
     */
    private void addBypassOptions(YoutubeDLRequest request) {
        // 添加额外的User-Agent
        request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36");
        
        // 强制使用IPv4，避免IPv6网络问题
        request.addOption("--force-ipv4");
        
        // 禁用缓存目录，避免权限问题
        request.addOption("--no-cache-dir");
        
        // 绕过地理限制
        request.addOption("--geo-bypass");
        
        // 重试次数
        request.addOption("--retries", "10");
        
        // 超时设置
        request.addOption("--socket-timeout", "30");
        
        // 添加cookies文件支持（如果可能）
        try {
            File cookiesFile = new File(getApplicationContext().getFilesDir(), "youtube_cookies.txt");
            if (cookiesFile.exists()) {
                request.addOption("--cookies", cookiesFile.getAbsolutePath());
                Log.d(TAG, "Using cookies file: " + cookiesFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting cookies file", e);
        }
    }

    public void pauseDownload(String videoId) {
        Future<?> task = downloadTasks.get(videoId);
        if (task != null) {
            task.cancel(true);
            downloadTasks.remove(videoId);
            DownloadItem item = downloads.get(videoId);
            if (item != null) {
                item.setPaused(true);
                broadcastDownloadPaused(videoId);
            }
        }
    }

    public void resumeDownload(String videoId) {
        DownloadItem item = downloads.get(videoId);
        if (item != null && item.isPaused()) {
            item.setPaused(false);
            startDownload(item);
            broadcastDownloadResumed(videoId);
        }
    }

    public void cancelDownload(String videoId) {
        Future<?> task = downloadTasks.get(videoId);
        if (task != null) {
            task.cancel(true);
            downloadTasks.remove(videoId);
        }
        downloads.remove(videoId);
        broadcastDownloadCanceled(videoId);
    }

    public List<DownloadItem> getActiveDownloads() {
        List<DownloadItem> allDownloads = new ArrayList<>(downloads.values());
        // 添加完成但还在显示的下载项
        allDownloads.addAll(completedDownloads.values());
        return allDownloads;
    }

    private void broadcastDownloadProgress(String videoId, int progress, String eta) {
        Intent intent = new Intent(ACTION_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_DOWNLOAD_ID, videoId);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_ETA, eta);
        broadcaster.sendBroadcast(intent);
        
        // 更新通知
        DownloadItem item = downloads.get(videoId);
        if (item != null) {
            updateNotification(item, progress);
        }
    }

    private void broadcastDownloadComplete(DownloadItem item) {
        try {
            // 确保设置下载日期
            if (item.getDownloadDate() == 0) {
                item.setDownloadDate(System.currentTimeMillis());
            }
            
            // 添加到已完成下载列表
            completedDownloads.put(item.getId(), item);
            
            // 再次检查缩略图是否存在，如果不存在则尝试获取一个默认的YouTube缩略图
            if (item.getThumbnailUrl() == null || item.getThumbnailUrl().isEmpty()) {
                try {
                    // 尝试从URL提取YouTube视频ID
                    String videoUrl = item.getUrl();
                    String youtubeId = extractYouTubeId(videoUrl);
                    
                    if (youtubeId != null && !youtubeId.isEmpty()) {
                        // 使用YouTube默认缩略图URL
                        String thumbnailUrl = "https://img.youtube.com/vi/" + youtubeId + "/0.jpg";
                        item.setThumbnailUrl(thumbnailUrl);
                        Log.d(TAG, "Set default YouTube thumbnail URL: " + thumbnailUrl);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting default thumbnail", e);
                }
            }
            
            // 添加到下载历史
            PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
            preferenceManager.addDownloadToHistory(item);
            
            // 发送广播
            Intent intent = new Intent(ACTION_DOWNLOAD_COMPLETE);
            intent.putExtra(EXTRA_DOWNLOAD_ID, item.getId());
            intent.putExtra(EXTRA_DOWNLOAD_ITEM, item);
            broadcaster.sendBroadcast(intent);
            
            // 设置延迟移除
            Runnable removalRunnable = () -> {
                completedDownloads.remove(item.getId());
                removalRunnables.remove(item.getId());
                // 发送更新通知
                Intent removeIntent = new Intent(ACTION_DOWNLOAD_CANCELED);
                removeIntent.putExtra(EXTRA_DOWNLOAD_ID, item.getId());
                broadcaster.sendBroadcast(removeIntent);
            };
            
            // 取消之前的移除任务（如果有）
            if (removalRunnables.containsKey(item.getId())) {
                handler.removeCallbacks(removalRunnables.get(item.getId()));
            }
            
            // 设置新的移除任务
            removalRunnables.put(item.getId(), removalRunnable);
            handler.postDelayed(removalRunnable, COMPLETED_ITEM_RETENTION_MS);
            
            Log.d(TAG, "Broadcast download complete for: " + item.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "Error broadcasting download complete", e);
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

    private void broadcastDownloadFailed(String videoId, String errorMessage) {
        Intent intent = new Intent(ACTION_DOWNLOAD_FAILED);
        intent.putExtra(EXTRA_DOWNLOAD_ID, videoId);
        intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage);
        broadcaster.sendBroadcast(intent);
        
        // 获取下载项，如果存在，先保存到已完成列表
        DownloadItem item = downloads.remove(videoId);
        if (item != null) {
            item.setCompleted(false);
            completedDownloads.put(videoId, item);
            
            // 添加到下载历史，标记为失败
            item.setDownloadDate(System.currentTimeMillis());
            PreferenceManager preferenceManager = new PreferenceManager(getApplicationContext());
            preferenceManager.addDownloadToHistory(item);
            
            // 使用AlertDialog显示错误信息
            handler.post(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
                builder.setTitle(getString(R.string.download_failed))
                       .setMessage(item.getTitle() + "\n\n错误信息：\n" + errorMessage)
                       .setPositiveButton("复制错误信息", (dialog, which) -> {
                           ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                           ClipData clip = ClipData.newPlainText("错误信息", errorMessage);
                           clipboard.setPrimaryClip(clip);
                           Toast.makeText(getApplicationContext(), "错误信息已复制到剪贴板", Toast.LENGTH_SHORT).show();
                       })
                       .setNegativeButton("确定", null)
                       .show();
            });
            
            // 延迟移除
            Runnable removalRunnable = () -> {
                completedDownloads.remove(videoId);
                removalRunnables.remove(videoId);
                
                // 发送更新通知
                Intent removeIntent = new Intent(ACTION_DOWNLOAD_CANCELED);
                removeIntent.putExtra(EXTRA_DOWNLOAD_ID, videoId);
                broadcaster.sendBroadcast(removeIntent);
            };
            
            if (removalRunnables.containsKey(videoId)) {
                handler.removeCallbacks(removalRunnables.get(videoId));
            }
            
            removalRunnables.put(videoId, removalRunnable);
            handler.postDelayed(removalRunnable, COMPLETED_ITEM_RETENTION_MS);
        }
    }

    private void broadcastDownloadPaused(String videoId) {
        Intent intent = new Intent(ACTION_DOWNLOAD_PAUSED);
        intent.putExtra(EXTRA_DOWNLOAD_ID, videoId);
        broadcaster.sendBroadcast(intent);
    }

    private void broadcastDownloadResumed(String videoId) {
        Intent intent = new Intent(ACTION_DOWNLOAD_RESUMED);
        intent.putExtra(EXTRA_DOWNLOAD_ID, videoId);
        broadcaster.sendBroadcast(intent);
    }

    private void broadcastDownloadCanceled(String videoId) {
        Intent intent = new Intent(ACTION_DOWNLOAD_CANCELED);
        intent.putExtra(EXTRA_DOWNLOAD_ID, videoId);
        broadcaster.sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String contentText) {
        try {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_youtube_download)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(contentText)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(pendingIntent);

            return builder.build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating notification", e);
            // 创建一个最基本的通知，避免服务崩溃
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("YouTube下载器")
                    .setContentText("下载中...")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();
        }
    }

    private void updateNotification(DownloadItem item, int progress) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification(item.getTitle() + " - " + progress + "%"));
    }

    private String formatEta(long etaInSeconds) {
        if (etaInSeconds < 0) {
            return "";
        }
        long hours = etaInSeconds / 3600;
        long minutes = (etaInSeconds % 3600) / 60;
        long seconds = etaInSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void showDownloadCompleteToast(String title) {
        Observable.just(title)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(t -> {
                    Toast.makeText(
                            getApplicationContext(),
                            getString(R.string.download_complete) + ": " + t,
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    public class DownloadBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
    }
} 
package com.alootcold.youtubedownloader;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.util.Log;
import android.widget.Toast;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

import androidx.core.content.ContextCompat;

import java.io.File;

public class YTApplication extends Application {

    private static final String TAG = "YTApplication";
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 2000; // 2秒

    @Override
    public void onCreate() {
        super.onCreate();
        
        configureRxJavaErrorHandler();
        initYoutubeDL();
    }

    private void configureRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if (e instanceof InterruptedException) {
                // 可忽略的异常
                return;
            }
            Log.e(TAG, "Undeliverable exception received", e);
        });
    }

    private void initYoutubeDL() {
        // 确保在主线程中执行初始化
        if (Thread.currentThread() != getMainLooper().getThread()) {
            new Handler(getMainLooper()).post(this::initYoutubeDL);
            return;
        }

        Completable.fromAction(() -> {
            int retryCount = 0;
            Exception lastException = null;

            while (retryCount < MAX_RETRY_COUNT) {
                try {
                    Log.d(TAG, "Attempting to initialize YouTube-DL (attempt " + (retryCount + 1) + ")");
                    
                    // 检查存储权限
                    if (!checkStoragePermission()) {
                        String errorMsg = "缺少存储权限，请授予应用存储权限";
                        Log.e(TAG, errorMsg);
                        throw new SecurityException(errorMsg);
                    }

                    // 检查存储空间
                    if (!checkStorageSpace()) {
                        String errorMsg = "存储空间不足，请确保有至少100MB可用空间";
                        Log.e(TAG, errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }

                    // 检查应用数据目录
                    File appDataDir = getApplicationContext().getFilesDir();
                    if (!appDataDir.exists() || !appDataDir.canWrite()) {
                        String errorMsg = "应用数据目录不可写: " + appDataDir.getAbsolutePath();
                        Log.e(TAG, errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }

                    // 检查外部存储目录
                    File externalDir = getApplicationContext().getExternalFilesDir(null);
                    if (externalDir != null && (!externalDir.exists() || !externalDir.canWrite())) {
                        String errorMsg = "外部存储目录不可写: " + externalDir.getAbsolutePath();
                        Log.e(TAG, errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }

                    // 检查lib目录
                    File libDir = new File(getApplicationContext().getApplicationInfo().nativeLibraryDir);
                    if (!libDir.exists() || !libDir.canRead()) {
                        String errorMsg = "无法访问lib目录: " + libDir.getAbsolutePath();
                        Log.e(TAG, errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }

                    // 检查并复制Python库文件
                    File pythonLib = new File(libDir, "libpython.zip.so");
                    if (!pythonLib.exists()) {
                        Log.d(TAG, "Python库文件不存在，尝试从assets复制...");
                        try {
                            // 从assets目录复制Python库文件
                            java.io.InputStream in = getAssets().open("libpython.zip.so");
                            java.io.FileOutputStream out = new java.io.FileOutputStream(pythonLib);
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                            in.close();
                            out.flush();
                            out.close();
                            Log.d(TAG, "Python库文件复制成功");
                        } catch (Exception e) {
                            String errorMsg = "无法复制Python库文件: " + e.getMessage();
                            Log.e(TAG, errorMsg, e);
                            throw new IllegalStateException(errorMsg);
                        }
                    }

                    Log.d(TAG, "开始初始化YouTube-DL...");
                    try {
                        // 初始化YouTube-DL
                        YoutubeDL.getInstance().init(this);
                        Log.d(TAG, "YouTube-DL初始化成功");
                    } catch (Exception e) {
                        String errorMsg = "YouTube-DL初始化失败: " + e.getMessage();
                        Log.e(TAG, errorMsg, e);
                        throw new YoutubeDLException(errorMsg, e);
                    }
                    
                    Log.d(TAG, "开始初始化FFmpeg...");
                    try {
                        // 初始化FFmpeg
                        FFmpeg.getInstance().init(this);
                        Log.d(TAG, "FFmpeg初始化成功");
                    } catch (Exception e) {
                        String errorMsg = "FFmpeg初始化失败: " + e.getMessage();
                        Log.e(TAG, errorMsg, e);
                        throw new YoutubeDLException(errorMsg, e);
                    }
                    
                    return; // 初始化成功，退出循环
                } catch (Exception e) {
                    lastException = e;
                    String errorMsg = "初始化失败 (尝试 " + (retryCount + 1) + "/" + MAX_RETRY_COUNT + "): " + e.getMessage();
                    Log.e(TAG, errorMsg, e);
                    Log.e(TAG, "错误堆栈: " + Log.getStackTraceString(e));
                    retryCount++;
                    
                    if (retryCount < MAX_RETRY_COUNT) {
                        Log.d(TAG, "等待 " + (RETRY_DELAY_MS/1000) + " 秒后重试...");
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            // 如果所有重试都失败了，抛出最后一个异常
            if (lastException != null) {
                String errorMsg = "YouTube-DL初始化失败，已重试" + MAX_RETRY_COUNT + "次。\n" +
                                "最后错误: " + lastException.getMessage() + "\n" +
                                "错误堆栈: " + Log.getStackTraceString(lastException) + "\n" +
                                "请检查：\n" +
                                "1. 存储权限是否已授予\n" +
                                "2. 存储空间是否充足\n" +
                                "3. 应用数据是否完整\n" +
                                "4. 网络连接是否正常\n" +
                                "5. lib目录是否可访问\n" +
                                "6. Python库文件是否存在";
                Log.e(TAG, errorMsg, lastException);
                throw new YoutubeDLException(errorMsg, lastException);
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            () -> {
                Log.i(TAG, "YouTube-DL初始化完成");
                Toast.makeText(getApplicationContext(), "YouTube-DL初始化成功", Toast.LENGTH_SHORT).show();
            },
            e -> {
                String errorMsg = "YouTube-DL初始化失败: " + e.getMessage();
                Log.e(TAG, errorMsg, e);
                Log.e(TAG, "错误堆栈: " + Log.getStackTraceString(e));
                Toast.makeText(getApplicationContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        );
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            boolean hasVideoPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                == PackageManager.PERMISSION_GRANTED;
            boolean hasImagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Android 13+: 视频权限: " + (hasVideoPermission ? "已授予" : "未授予") + 
                      ", 图片权限: " + (hasImagePermission ? "已授予" : "未授予"));
            return hasVideoPermission && hasImagePermission;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11-12
            boolean hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
            boolean hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Android 11-12: 读取权限: " + (hasReadPermission ? "已授予" : "未授予") + 
                      ", 写入权限: " + (hasWritePermission ? "已授予" : "未授予"));
            return hasReadPermission && hasWritePermission;
        } else { // Android 10及以下
            boolean hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
            boolean hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Android 10及以下: 读取权限: " + (hasReadPermission ? "已授予" : "未授予") + 
                      ", 写入权限: " + (hasWritePermission ? "已授予" : "未授予"));
            return hasReadPermission && hasWritePermission;
        }
    }

    private boolean checkStorageSpace() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSizeLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        long availableSpace = availableBlocks * blockSize;
        long requiredSpace = 100 * 1024 * 1024; // 100MB
        
        boolean hasEnoughSpace = availableSpace > requiredSpace;
        Log.d(TAG, String.format("存储空间检查: 可用空间 %.2f MB, 需要 %.2f MB, 状态: %s",
            availableSpace / (1024.0 * 1024.0),
            requiredSpace / (1024.0 * 1024.0),
            hasEnoughSpace ? "充足" : "不足"));
        
        return hasEnoughSpace;
    }
} 
package com.alootcold.youtubedownloader;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.alootcold.youtubedownloader.service.DownloadService;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 102;
    private static final int REQUEST_MEDIA_PERMISSION = 103;
    private static final int REQUEST_PICK_COOKIES_FILE = 104;
    // Android 13 (API 33) 的常量定义
    private static final int TIRAMISU = 33;
    
    private EditText urlEditText;
    private Button downloadButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private Spinner formatSpinner;
    
    private DownloadService downloadService;
    private boolean bound = false;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isInitializing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 设置工具栏
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // 设置底部导航栏
        setupBottomNavigation();
        
        // 初始化UI组件
        urlEditText = findViewById(R.id.urlEditText);
        downloadButton = findViewById(R.id.downloadButton);
        progressBar = findViewById(R.id.progressBar);
        statusTextView = findViewById(R.id.statusTextView);
        formatSpinner = findViewById(R.id.formatSpinner);
        
        // 设置格式选项
        setupFormatSpinner();
        
        // 主动请求权限，特别强调Android 13+的媒体权限
        if (Build.VERSION.SDK_INT >= TIRAMISU) {
            // 直接请求媒体权限
            String[] permissions = {
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
            
            List<String> permissionsToRequest = new ArrayList<>();
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            
            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        permissionsToRequest.toArray(new String[0]),
                        REQUEST_MEDIA_PERMISSION
                );
            } else {
                // 已有媒体权限，检查文件访问权限
                checkAndRequestPermissions();
            }
        } else {
            // 非Android 13+设备，使用常规权限检查
            checkAndRequestPermissions();
        }
        
        downloadButton.setOnClickListener(v -> {
            try {
                if (!checkAndRequestPermissions()) {
                    Toast.makeText(this, "需要存储权限才能下载视频", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String url = urlEditText.getText().toString().trim();
                
                if (url.isEmpty()) {
                    Toast.makeText(this, "请输入YouTube视频URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (!isYouTubeUrl(url)) {
                    Toast.makeText(this, "请输入有效的YouTube URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                String format = getSelectedFormat();
                if (format == null) {
                    Toast.makeText(this, "无法获取格式选项", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (!bound) {
                    Toast.makeText(this, "下载服务未连接，请稍后再试", Toast.LENGTH_SHORT).show();
                    // 尝试重新绑定服务
                    Intent intent = new Intent(this, DownloadService.class);
                    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
                    return;
                }
                
                startDownload(url, format);
            } catch (Exception e) {
                Log.e(TAG, "Error starting download", e);
                Toast.makeText(this, "启动下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        // 初始化YouTube-DL库
        initYoutubeDL();
        
        // 绑定下载服务
        Intent intent = new Intent(this, DownloadService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        
        // 注册广播接收器
        registerBroadcastReceivers();
    }
    
    private void setupFormatSpinner() {
        List<String> formatOptions = new ArrayList<>();
        formatOptions.add("best");                    // 最佳质量
        formatOptions.add("bestvideo+bestaudio");     // 最佳视频+音频
        formatOptions.add("bestvideo[height<=2160]+bestaudio"); // 4K
        formatOptions.add("bestvideo[height<=1440]+bestaudio"); // 2K/1440p
        formatOptions.add("bestvideo[height<=1080]+bestaudio"); // 1080p
        formatOptions.add("bestvideo[height<=720]+bestaudio");  // 720p
        formatOptions.add("bestvideo[height<=480]+bestaudio");  // 480p
        formatOptions.add("bestaudio[ext=m4a]");      // 仅音频 (M4A)
        
        List<String> formatDescriptions = new ArrayList<>();
        formatDescriptions.add("最佳综合质量 (自动调整)");
        formatDescriptions.add("最佳视频+音频 (可能更大)");
        formatDescriptions.add("4K超高清视频 (2160p)");
        formatDescriptions.add("2K高清视频 (1440p)");
        formatDescriptions.add("1080p高清视频");
        formatDescriptions.add("720p高清视频");
        formatDescriptions.add("480p标清视频");
        formatDescriptions.add("仅音频 (M4A格式)");
        
        // 显示格式选择提示
        TextView formatHintText = findViewById(R.id.formatHintText);
        if (formatHintText != null) {
            formatHintText.setText("如果选定格式不可用，将自动调整为最佳可用格式");
            formatHintText.setVisibility(View.VISIBLE);
        }
        
        // 创建自定义适配器，显示易懂的描述，但保留实际格式值
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                formatDescriptions
        ) {
            // 保存原始格式值的映射关系
            private final List<String> actualFormats = formatOptions;
            
            @Override
            public String getItem(int position) {
                return super.getItem(position);
            }
            
            // 获取实际的格式值
            public String getActualFormat(int position) {
                return actualFormats.get(position);
            }
        };
        
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(adapter);
    }
    
    private String getSelectedFormat() {
        try {
            if (formatSpinner == null || formatSpinner.getAdapter() == null) {
                Log.e(TAG, "Format spinner or its adapter is null");
                return "best"; // 返回默认值
            }
            
            int position = formatSpinner.getSelectedItemPosition();
            if (position < 0) {
                Log.e(TAG, "Invalid spinner position: " + position);
                return "best"; // 返回默认值
            }
            
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) formatSpinner.getAdapter();
            
            if (adapter instanceof ArrayAdapter) {
                try {
                    // 使用反射获取实际格式值
                    java.lang.reflect.Method method = adapter.getClass().getMethod("getActualFormat", int.class);
                    String format = (String) method.invoke(adapter, position);
                    if (format == null || format.isEmpty()) {
                        Log.e(TAG, "Empty format from adapter");
                        return "best"; // 返回默认值
                    }
                    return format;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get actual format", e);
                    // 假设顺序与setupFormatSpinner中定义的相同
                    switch (position) {
                        case 0: return "best";
                        case 1: return "bestvideo+bestaudio";
                        case 2: return "bestvideo[height<=2160]+bestaudio";
                        case 3: return "bestvideo[height<=1440]+bestaudio";
                        case 4: return "bestvideo[height<=1080]+bestaudio";
                        case 5: return "bestvideo[height<=720]+bestaudio";
                        case 6: return "bestvideo[height<=480]+bestaudio";
                        case 7: return "bestaudio[ext=m4a]";
                        default: return "best";
                    }
                }
            }
            
            // 如果不是自定义适配器，则返回文本值
            String formatText = adapter.getItem(position);
            return formatText != null && !formatText.isEmpty() ? formatText : "best";
        } catch (Exception e) {
            Log.e(TAG, "Error in getSelectedFormat", e);
            return "best"; // 发生任何异常都返回默认值
        }
    }
    
    private boolean isYouTubeUrl(String url) {
        return url.matches("^(https?://)?(www\\.)?(youtube\\.com|youtu\\.be)/.+$");
    }
    
    private void initYoutubeDL() {
        if (isInitializing) {
            return;
        }
        
        isInitializing = true;
        statusTextView.setText("正在初始化...");
        progressBar.setVisibility(View.VISIBLE);
        downloadButton.setEnabled(false);
        
        new Thread(() -> {
            try {
                YoutubeDL.getInstance().init(getApplication());
                
                handler.post(() -> {
                    statusTextView.setText("准备就绪");
                    progressBar.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                    isInitializing = false;
                });
                
            } catch (YoutubeDLException e) {
                Log.e(TAG, "Failed to initialize YouTube-DL", e);
                
                handler.post(() -> {
                    statusTextView.setText("初始化失败");
                    Toast.makeText(MainActivity.this, 
                            "初始化YouTube-DL失败: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(View.GONE);
                    isInitializing = false;
                });
            }
        }).start();
    }
    
    private boolean checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= TIRAMISU) {
            // Android 13+ (API 33+): 检查媒体权限
            String[] permissions = {
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
            
            List<String> permissionsToRequest = new ArrayList<>();
            
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            
            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        permissionsToRequest.toArray(new String[0]),
                        REQUEST_MEDIA_PERMISSION
                );
                return false;
            }
            
            // 还需要检查MANAGE_EXTERNAL_STORAGE权限
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to request MANAGE_EXTERNAL_STORAGE", e);
                    Toast.makeText(this, "无法请求文件访问权限: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                return false;
            }
            
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: 检查外部存储管理权限
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to request MANAGE_EXTERNAL_STORAGE", e);
                    Toast.makeText(this, "无法请求文件访问权限: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                return false;
            }
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10: 检查传统存储权限
            String[] permissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            
            List<String> permissionsToRequest = new ArrayList<>();
            
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission);
                }
            }
            
            if (!permissionsToRequest.isEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        permissionsToRequest.toArray(new String[0]),
                        REQUEST_STORAGE_PERMISSION
                );
                return false;
            }
            return true;
        }
        
        // Android 5 及以下版本无需运行时权限
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_STORAGE_PERMISSION || requestCode == REQUEST_MEDIA_PERMISSION) {
            boolean allPermissionsGranted = true;
            
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show();
                
                // 如果是Android 13+，获取了媒体权限后，还需要检查文件管理权限
                if (requestCode == REQUEST_MEDIA_PERMISSION && Build.VERSION.SDK_INT >= TIRAMISU) {
                    if (!Environment.isExternalStorageManager()) {
                        try {
                            Intent intent = new Intent();
                            intent.setAction(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to request MANAGE_EXTERNAL_STORAGE", e);
                        }
                    } else {
                        // 权限获取后可以尝试重新初始化
                        initYoutubeDL();
                    }
                } else {
                    // 权限获取后可以尝试重新初始化
                    initYoutubeDL();
                }
            } else {
                Toast.makeText(this, "需要存储权限才能下载视频", Toast.LENGTH_LONG).show();
                
                // 强制再次请求权限
                if (requestCode == REQUEST_MEDIA_PERMISSION) {
                    // 延迟一秒再次请求，避免用户感到困扰
                    new Handler().postDelayed(() -> {
                        checkAndRequestPermissions();
                    }, 1000);
                }
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                Toast.makeText(this, "文件访问权限已授予", Toast.LENGTH_SHORT).show();
                // 权限获取后可以尝试重新初始化
                initYoutubeDL();
            } else {
                Toast.makeText(this, "需要文件访问权限才能下载视频", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_PICK_COOKIES_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                processCookiesFile(data.getData());
            }
        }
    }
    
    private void startDownload(String url, String format) {
        try {
            if (!bound) {
                Toast.makeText(this, "下载服务未连接", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d(TAG, "Starting download for URL: " + url + " with format: " + format);
            
            // 检查前台服务权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && 
                ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) 
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "缺少前台服务权限，无法启动下载", Toast.LENGTH_LONG).show();
                return;
            }
            
            Intent intent = new Intent(this, DownloadService.class);
            intent.setAction(DownloadService.ACTION_START_DOWNLOAD);
            intent.putExtra(DownloadService.EXTRA_URL, url);
            intent.putExtra(DownloadService.EXTRA_FORMAT, format);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    startForegroundService(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting foreground service", e);
                    Toast.makeText(this, "启动前台服务失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                startService(intent);
            }
            
            // 清空链接输入框
            urlEditText.setText("");
            
            statusTextView.setText("已开始下载...");
            Toast.makeText(this, "已开始下载，您可以在下载列表中查看进度", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error in startDownload", e);
            Toast.makeText(this, "启动下载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void registerBroadcastReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_FAILED);
        
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, filter);
    }
    
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (DownloadService.ACTION_DOWNLOAD_PROGRESS.equals(action)) {
                String downloadId = intent.getStringExtra(DownloadService.EXTRA_DOWNLOAD_ID);
                int progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0);
                // 确保进度在0-100之间
                progress = Math.min(100, Math.max(0, progress));
                
                String eta = intent.getStringExtra(DownloadService.EXTRA_ETA);
                
                Log.d(TAG, "Download progress for " + downloadId + ": " + progress + "%, ETA: " + eta);
                
                progressBar.setProgress(progress);
                statusTextView.setText("下载中: " + progress + "%" + (eta != null && !eta.isEmpty() ? " (剩余时间: " + eta + ")" : ""));
                progressBar.setVisibility(View.VISIBLE);
                
            } else if (DownloadService.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                progressBar.setProgress(100);
                statusTextView.setText("下载完成");
                progressBar.setVisibility(View.GONE);
                
            } else if (DownloadService.ACTION_DOWNLOAD_FAILED.equals(action)) {
                String errorMessage = intent.getStringExtra(DownloadService.EXTRA_ERROR_MESSAGE);
                statusTextView.setText("下载失败");
                progressBar.setVisibility(View.GONE);
                
                Log.e(TAG, "Download failed: " + errorMessage);
                
                // 检查是否是常见错误，显示帮助对话框
                if (errorMessage != null && 
                   (errorMessage.contains("Sign in to confirm you're not a bot") || 
                    errorMessage.contains("验证您不是机器人"))) {
                    showBotDetectionHelpDialog();
                } else {
                    // 显示详细错误对话框
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("下载失败")
                           .setMessage(errorMessage)
                           .setPositiveButton("确定", null)
                           .show();
                }
            }
        }
    };
    
    /**
     * 显示机器人检测帮助对话框
     */
    private void showBotDetectionHelpDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("YouTube需要验证")
               .setMessage("YouTube检测到自动下载行为并需要验证。请尝试以下解决方法：\n\n" +
                       "1. 导入Cookies文件\n" +
                       "   - 在Chrome浏览器中登录YouTube\n" +
                       "   - 使用浏览器扩展导出cookies\n" +
                       "   - 在本应用中导入cookies文件\n\n" +
                       "2. 重新尝试下载\n" +
                       "   - 等待几分钟再试\n" +
                       "   - 清除应用数据后重试\n\n" +
                       "3. 使用不同的网络环境\n" +
                       "   - 尝试切换WiFi或移动网络\n" +
                       "   - 尝试使用VPN")
               .setPositiveButton("导入Cookies", (dialog, which) -> {
                   importCookiesFile();
               })
               .setNegativeButton("关闭", null)
               .setNeutralButton("更新youtube-dl", (dialog, which) -> {
                   updateYoutubeDL();
               })
               .show();
    }
    
    /**
     * 更新YouTube-DL
     */
    private void updateYoutubeDL() {
        statusTextView.setText("正在更新YouTube-DL...");
        progressBar.setVisibility(View.VISIBLE);
        downloadButton.setEnabled(false);
        
        new Thread(() -> {
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
                    handler.post(() -> {
                        statusTextView.setText("无法更新YouTube-DL");
                        progressBar.setVisibility(View.GONE);
                        downloadButton.setEnabled(true);
                        Toast.makeText(MainActivity.this, 
                                "更新失败: 无法获取更新通道信息", 
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                
                // 使用找到的枚举值，带强制类型转换
                Method updateMethod = YoutubeDL.class.getMethod("updateYoutubeDL", Context.class, updateChannelClass);
                updateMethod.invoke(YoutubeDL.getInstance(), getApplication(), updateChannelEnum);
                
                handler.post(() -> {
                    statusTextView.setText("YouTube-DL已更新，请重试下载");
                    progressBar.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, 
                            "YouTube-DL已成功更新！", 
                            Toast.LENGTH_LONG).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to update YouTube-DL", e);
                
                handler.post(() -> {
                    statusTextView.setText("更新失败");
                    Toast.makeText(MainActivity.this, 
                            "更新YouTube-DL失败: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                });
            }
        }).start();
    }
    
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            DownloadService.DownloadBinder binder = (DownloadService.DownloadBinder) service;
            downloadService = binder.getService();
            bound = true;
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
        }
    };
    
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.menu_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_history) {
            // 跳转到下载历史界面
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_download_list) {
            // 跳转到下载列表界面
            Intent intent = new Intent(this, DownloadingActivity.class);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_import_cookies) {
            // 导入cookies文件
            importCookiesFile();
            return true;
        } else if (itemId == R.id.menu_help) {
            // 显示帮助对话框
            showBotDetectionHelpDialog();
            return true;
        } else if (itemId == R.id.menu_update_ytdl) {
            // 更新YouTube-DL
            updateYoutubeDL();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void setupBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home) {
                // 已经在主页，无需操作
                return true;
            } else if (itemId == R.id.nav_download_list) {
                // 跳转到下载列表界面
                Intent intent = new Intent(this, DownloadingActivity.class);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_history) {
                // 跳转到下载历史界面
                Intent intent = new Intent(this, HistoryActivity.class);
                startActivity(intent);
                return true;
            }
            
            return false;
        });
    }
    
    /**
     * 导入cookies文件
     */
    private void importCookiesFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_PICK_COOKIES_FILE);
        } catch (Exception e) {
            Log.e(TAG, "Error importing cookies file", e);
            Toast.makeText(this, "无法打开文件选择器: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 处理cookies文件导入
     */
    private void processCookiesFile(android.net.Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "无法读取选择的文件", Toast.LENGTH_LONG).show();
                return;
            }
            
            File cookiesFile = new File(getApplicationContext().getFilesDir(), "youtube_cookies.txt");
            FileOutputStream outputStream = new FileOutputStream(cookiesFile);
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            inputStream.close();
            outputStream.close();
            
            Toast.makeText(this, "Cookies文件导入成功！现在可以尝试下载了", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Cookies file saved to: " + cookiesFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error processing cookies file", e);
            Toast.makeText(this, "处理cookies文件失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
} 
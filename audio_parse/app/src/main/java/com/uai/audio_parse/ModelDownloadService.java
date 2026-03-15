package com.uai.audio_parse;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModelDownloadService extends Service {
    private static final String TAG = "ModelDownloadService";
    private static final String CHANNEL_ID = "model_download_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    private static final String LOG_DIR_NAME = "TingJian_Logs";
    private static final String LOG_FILE_NAME = "transcription_log.txt";
    
    private File logFile;
    private FileOutputStream logOutputStream;
    
    public static final String ACTION_START_DOWNLOAD = "com.uai.audio_parse.START_DOWNLOAD";
    public static final String ACTION_CANCEL_DOWNLOAD = "com.uai.audio_parse.CANCEL_DOWNLOAD";
    
    public static final String EXTRA_MODEL_TYPE = "model_type";
    
    public static final String BROADCAST_DOWNLOAD_PROGRESS = "com.uai.audio_parse.DOWNLOAD_PROGRESS";
    public static final String BROADCAST_DOWNLOAD_COMPLETE = "com.uai.audio_parse.DOWNLOAD_COMPLETE";
    public static final String BROADCAST_DOWNLOAD_ERROR = "com.uai.audio_parse.DOWNLOAD_ERROR";
    
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_MODEL_PATH = "model_path";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_MODEL_NAME = "model_name";
    
    private static final String MODEL_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip";
    private static final String MODEL_SMALL_NAME = "vosk-model-small-cn-0.22";
    
    private static final String MODEL_STANDARD_URL = "https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip";
    private static final String MODEL_STANDARD_NAME = "vosk-model-cn-0.22";
    
    private static final String MODEL_MULTICN_URL = "https://alphacephei.com/vosk/models/vosk-model-cn-kaldi-multicn-0.15.zip";
    private static final String MODEL_MULTICN_NAME = "vosk-model-cn-kaldi-multicn-0.15";
    
    private static final String WHISPER_TINY_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin";
    private static final String WHISPER_TINY_NAME = "ggml-tiny.bin";
    
    private static final String WHISPER_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
    private static final String WHISPER_BASE_NAME = "ggml-base.bin";
    
    private static final String WHISPER_SMALL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin";
    private static final String WHISPER_SMALL_NAME = "ggml-small.bin";
    
    private static final String WHISPER_MEDIUM_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin";
    private static final String WHISPER_MEDIUM_NAME = "ggml-medium.bin";
    
    private static final String MODELS_DIR_NAME = "TingJian_Models";
    private static final String PREF_NAME = "model_download_prefs";
    private static final String KEY_DOWNLOADING_MODEL = "downloading_model";
    
    private OkHttpClient client;
    private NotificationManager notificationManager;
    private volatile boolean isCancelled = false;
    private Thread downloadThread;
    private String currentModelType;
    
    @Override
    public void onCreate() {
        super.onCreate();
        client = new OkHttpClient();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initLog();
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_DOWNLOAD.equals(action)) {
                String modelType = intent.getStringExtra(EXTRA_MODEL_TYPE);
                if (modelType != null) {
                    startDownload(modelType);
                }
            } else if (ACTION_CANCEL_DOWNLOAD.equals(action)) {
                cancelDownload();
                stopForeground(true);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }
    
    @Override
    public void onDestroy() {
        closeLog();
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "模型下载",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("语音识别模型下载进度");
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void startDownload(String modelType) {
        if (downloadThread != null && downloadThread.isAlive()) {
            return;
        }
        
        currentModelType = modelType;
        isCancelled = false;
        saveDownloadingModel(modelType);
        
        String modelName = getModelDisplayName(modelType);
        startForeground(NOTIFICATION_ID, createNotification(0, "准备下载 " + modelName));
        
        downloadThread = new Thread(() -> {
            try {
                downloadModel(modelType);
            } catch (Exception e) {
                writeErrorLog("错误: [ModelDownloadService] 下载异常 - " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                Log.e(TAG, "Download error", e);
                sendErrorBroadcast(e.getMessage());
                clearDownloadingModel();
                stopForeground(true);
                stopSelf();
            }
        });
        downloadThread.start();
    }
    
    private void cancelDownload() {
        isCancelled = true;
        if (downloadThread != null) {
            downloadThread.interrupt();
        }
        clearDownloadingModel();
        sendErrorBroadcast("下载已取消");
    }
    
    private void downloadModel(String modelType) throws IOException {
        String modelName = getModelName(modelType);
        String modelUrl = getModelUrl(modelType);
        String displayName = getModelDisplayName(modelType);
        boolean isWhisper = isWhisperModel(modelType);
        
        
        if (isWhisper) {
            downloadWhisperModel(modelType, modelName, modelUrl, displayName);
        } else {
            downloadVoskModel(modelType, modelName, modelUrl, displayName);
        }
    }
    
    private void downloadWhisperModel(String modelType, String modelName, String modelUrl, String displayName) throws IOException {
        File whisperDir = new File(getModelsStorageDir(), "whisper");
        if (!whisperDir.exists()) {
            whisperDir.mkdirs();
        }
        
        File modelFile = new File(whisperDir, modelName);
        if (modelFile.exists()) {
            modelFile.delete();
        }
        
        Request request = new Request.Builder()
                .url(modelUrl)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载失败: " + response.code());
            }
            
            long contentLength = response.body().contentLength();
            InputStream inputStream = response.body().byteStream();
            FileOutputStream outputStream = new FileOutputStream(modelFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            int lastProgress = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1 && !isCancelled) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                
                if (contentLength > 0) {
                    int progress = (int) ((totalBytes * 100) / contentLength);
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        updateNotification(progress, "下载中 " + displayName + " (" + progress + "%)");
                        sendProgressBroadcast(progress, "下载中...");
                    }
                }
            }
            
            outputStream.close();
            inputStream.close();
            
            if (isCancelled) {
                modelFile.delete();
                throw new IOException("下载已取消");
            }
            
            String modelPath = modelFile.getAbsolutePath();
            
            updateNotification(100, displayName + " 下载完成");
            sendCompleteBroadcast(modelPath, modelType);
            clearDownloadingModel();
            
            new PreferencesManager(this).saveSelectedModelType(modelType);
            new PreferencesManager(this).saveModelPath(modelPath);
            new PreferencesManager(this).saveEngineType(PreferencesManager.ENGINE_WHISPER);
            
            stopForeground(true);
            stopSelf();
        }
    }
    
    private void downloadVoskModel(String modelType, String modelName, String modelUrl, String displayName) throws IOException {
        File modelDir = new File(getModelsStorageDir(), modelName);
        if (modelDir.exists()) {
            deleteDirectory(modelDir);
        }
        
        File zipFile = new File(getCacheDir(), "model_" + modelType + ".zip");
        
        Request request = new Request.Builder()
                .url(modelUrl)
                .build();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载失败: " + response.code());
            }
            
            long contentLength = response.body().contentLength();
            InputStream inputStream = response.body().byteStream();
            FileOutputStream outputStream = new FileOutputStream(zipFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            int lastProgress = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1 && !isCancelled) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                
                if (contentLength > 0) {
                    int progress = (int) ((totalBytes * 50) / contentLength);
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        updateNotification(progress, "下载中 " + displayName + " (" + progress + "%)");
                        sendProgressBroadcast(progress, "下载中...");
                    }
                }
            }
            
            outputStream.close();
            inputStream.close();
            
            if (isCancelled) {
                zipFile.delete();
                throw new IOException("下载已取消");
            }
            
            
            updateNotification(50, "解压中 " + displayName);
            sendProgressBroadcast(50, "解压中...");
            
            unzipModel(zipFile, modelType, displayName);
            zipFile.delete();
            
        }
    }
    
    private void unzipModel(File zipFile, String modelType, String displayName) throws IOException {
        File modelDir = getModelsStorageDir();
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        
        long totalSize = zipFile.length();
        long extractedBytes = 0;
        int lastProgress = 50;
        
        while ((entry = zipInputStream.getNextEntry()) != null && !isCancelled) {
            File outputFile = new File(modelDir, entry.getName());
            
            if (entry.isDirectory()) {
                outputFile.mkdirs();
            } else {
                if (outputFile.getParentFile() != null) {
                    outputFile.getParentFile().mkdirs();
                }
                
                FileOutputStream outputStream = new FileOutputStream(outputFile);
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    extractedBytes += bytesRead;
                    
                    if (totalSize > 0) {
                        int progress = 50 + (int) ((extractedBytes * 50) / totalSize);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            updateNotification(progress, "解压中 " + displayName + " (" + (progress - 50) * 2 + "%)");
                            sendProgressBroadcast(progress, "解压中...");
                        }
                    }
                }
                
                outputStream.close();
            }
            
            zipInputStream.closeEntry();
        }
        
        zipInputStream.close();
        
        if (isCancelled) {
            throw new IOException("下载已取消");
        }
        
        String modelPath = getModelPath(modelType);
        
        updateNotification(100, displayName + " 下载完成");
        sendCompleteBroadcast(modelPath, modelType);
        clearDownloadingModel();
        
        new PreferencesManager(this).saveSelectedModelType(modelType);
        new PreferencesManager(this).saveModelPath(modelPath);
        new PreferencesManager(this).saveEngineType(PreferencesManager.ENGINE_VOSK);
        
        stopForeground(true);
        stopSelf();
    }
    
    private Notification createNotification(int progress, String status) {
        Intent cancelIntent = new Intent(this, ModelDownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL_DOWNLOAD);
        PendingIntent cancelPendingIntent = PendingIntent.getService(
                this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("模型下载")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPendingIntent)
                .build();
    }
    
    private void updateNotification(int progress, String status) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(progress, status));
    }
    
    private void sendProgressBroadcast(int progress, String status) {
        Intent intent = new Intent(BROADCAST_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_MODEL_TYPE, currentModelType);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void sendCompleteBroadcast(String modelPath, String modelType) {
        Intent intent = new Intent(BROADCAST_DOWNLOAD_COMPLETE);
        intent.putExtra(EXTRA_MODEL_PATH, modelPath);
        intent.putExtra(EXTRA_MODEL_TYPE, modelType);
        intent.putExtra(EXTRA_MODEL_NAME, getModelDisplayName(modelType));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void sendErrorBroadcast(String error) {
        Intent intent = new Intent(BROADCAST_DOWNLOAD_ERROR);
        intent.putExtra(EXTRA_ERROR, error);
        intent.putExtra(EXTRA_MODEL_TYPE, currentModelType);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private File getModelsStorageDir() {
        File externalDir = getExternalFilesDir(null);
        if (externalDir != null) {
            File modelsDir = new File(externalDir, MODELS_DIR_NAME);
            if (!modelsDir.exists()) {
                modelsDir.mkdirs();
            }
            return modelsDir;
        }
        return getFilesDir();
    }
    
    private String getModelPath(String modelType) {
        String modelName = getModelName(modelType);
        if (isWhisperModel(modelType)) {
            return new File(getModelsStorageDir(), "whisper/" + modelName).getAbsolutePath();
        }
        return new File(getModelsStorageDir(), modelName).getAbsolutePath();
    }
    
    private boolean isWhisperModel(String modelType) {
        return modelType.startsWith("whisper_");
    }
    
    private String getModelName(String modelType) {
        switch (modelType) {
            case PreferencesManager.MODEL_TYPE_STANDARD:
                return MODEL_STANDARD_NAME;
            case PreferencesManager.MODEL_TYPE_MULTICN:
                return MODEL_MULTICN_NAME;
            case PreferencesManager.WHISPER_MODEL_TINY:
                return WHISPER_TINY_NAME;
            case PreferencesManager.WHISPER_MODEL_BASE:
                return WHISPER_BASE_NAME;
            case PreferencesManager.WHISPER_MODEL_SMALL:
                return WHISPER_SMALL_NAME;
            case PreferencesManager.WHISPER_MODEL_MEDIUM:
                return WHISPER_MEDIUM_NAME;
            default:
                return MODEL_SMALL_NAME;
        }
    }
    
    private String getModelUrl(String modelType) {
        switch (modelType) {
            case PreferencesManager.MODEL_TYPE_STANDARD:
                return MODEL_STANDARD_URL;
            case PreferencesManager.MODEL_TYPE_MULTICN:
                return MODEL_MULTICN_URL;
            case PreferencesManager.WHISPER_MODEL_TINY:
                return WHISPER_TINY_URL;
            case PreferencesManager.WHISPER_MODEL_BASE:
                return WHISPER_BASE_URL;
            case PreferencesManager.WHISPER_MODEL_SMALL:
                return WHISPER_SMALL_URL;
            case PreferencesManager.WHISPER_MODEL_MEDIUM:
                return WHISPER_MEDIUM_URL;
            default:
                return MODEL_SMALL_URL;
        }
    }
    
    private String getModelDisplayName(String modelType) {
        switch (modelType) {
            case PreferencesManager.MODEL_TYPE_STANDARD:
                return "Vosk中文标准模型";
            case PreferencesManager.MODEL_TYPE_MULTICN:
                return "Vosk中文多方言模型";
            case PreferencesManager.WHISPER_MODEL_TINY:
                return "Whisper Tiny模型";
            case PreferencesManager.WHISPER_MODEL_BASE:
                return "Whisper Base模型";
            case PreferencesManager.WHISPER_MODEL_SMALL:
                return "Whisper Small模型";
            case PreferencesManager.WHISPER_MODEL_MEDIUM:
                return "Whisper Medium模型";
            default:
                return "Vosk中文小模型";
        }
    }
    
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
    
    private void saveDownloadingModel(String modelType) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_DOWNLOADING_MODEL, modelType).apply();
    }
    
    private void clearDownloadingModel() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_DOWNLOADING_MODEL).apply();
    }
    
    private File getLogDir() {
        File externalDir = getExternalFilesDir(null);
        if (externalDir == null) {
            externalDir = getFilesDir();
        }
        return new File(externalDir, LOG_DIR_NAME);
    }
    
    private void initLog() {
        try {
            File logDir = getLogDir();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            logFile = new File(logDir, LOG_FILE_NAME);
            logOutputStream = new FileOutputStream(logFile, true);
            
            Log.i(TAG, "日志文件已初始化: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            writeErrorLog("错误: [ModelDownloadService] 初始化日志失败 - " + e.getClass().getName() + ": " + e.getMessage());
            Log.e(TAG, "初始化日志失败", e);
        }
    }
    
    private void writeErrorLog(String message) {
        if (logOutputStream != null) {
            try {
                String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
                String logLine = "[" + timestamp + "] " + message + "\n";
                logOutputStream.write(logLine.getBytes());
                logOutputStream.flush();
            } catch (Exception e) {
                Log.e(TAG, "写入日志失败", e);
            }
        }
    }
    
    private void closeLog() {
        if (logOutputStream != null) {
            try {
                logOutputStream.write("\n=== 日志结束 ===\n".getBytes());
                logOutputStream.flush();
                logOutputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭日志失败", e);
            }
        }
    }
    
    public static String getDownloadingModel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_DOWNLOADING_MODEL, null);
    }
    
    public static boolean isDownloading(Context context) {
        return getDownloadingModel(context) != null;
    }
}

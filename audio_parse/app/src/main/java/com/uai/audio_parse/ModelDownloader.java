package com.uai.audio_parse;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

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

/**
 * 模型下载器
 * 负责下载和管理Vosk语音识别模型
 * 支持三种中文模型：小模型、标准模型、多方言模型
 * 模型存储在外部存储的TingJian_Models目录下
 * 
 * @author AI Generated
 * @version 1.0
 */
public class ModelDownloader {
    private static final String TAG = "ModelDownloader";
    
    private static final String LOG_DIR_NAME = "TingJian_Logs";
    private static final String LOG_FILE_NAME = "transcription_log.txt";
    
    private File logFile;
    private FileOutputStream logOutputStream;
    
    private static final String MODEL_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip";
    private static final String MODEL_SMALL_NAME = "vosk-model-small-cn-0.22";
    
    private static final String MODEL_STANDARD_URL = "https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip";
    private static final String MODEL_STANDARD_NAME = "vosk-model-cn-0.22";
    
    private static final String MODEL_MULTICN_URL = "https://alphacephei.com/vosk/models/vosk-model-cn-kaldi-multicn-0.15.zip";
    private static final String MODEL_MULTICN_NAME = "vosk-model-cn-kaldi-multicn-0.15";
    
    private static final String WHISPER_TINY_NAME = "ggml-tiny.bin";
    private static final String WHISPER_BASE_NAME = "ggml-base.bin";
    private static final String WHISPER_SMALL_NAME = "ggml-small.bin";
    private static final String WHISPER_MEDIUM_NAME = "ggml-medium.bin";
    
    private static final String MODELS_DIR_NAME = "TingJian_Models";
    
    private final OkHttpClient client;
    private final Context context;
    private volatile boolean isCancelled = false;
    
    public static final String[] SUPPORTED_MODELS = {
        "small",
        "standard",
        "multicn",
        "whisper_tiny",
        "whisper_base",
        "whisper_small",
        "whisper_medium"
    };
    
    public static String[] getModelDisplayNames() {
        return new String[] {
            "Vosk中文小模型 (50MB)",
            "Vosk中文标准模型 (1.3GB)",
            "Vosk中文多方言模型 (1.5GB)",
            "Whisper Tiny模型 (75MB)",
            "Whisper Base模型 (142MB)",
            "Whisper Small模型 (466MB)",
            "Whisper Medium模型 (1.5GB)"
        };
    }
    
    public interface DownloadCallback {
        void onProgress(int progress);
        void onComplete(String modelPath);
        void onError(String error);
    }
    
    public ModelDownloader(Context context) {
        this.context = context;
        this.client = new OkHttpClient();
    }
    
    private File getModelsStorageDir() {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir != null) {
            File modelsDir = new File(externalDir, MODELS_DIR_NAME);
            if (!modelsDir.exists()) {
                modelsDir.mkdirs();
            }
            return modelsDir;
        }
        return context.getFilesDir();
    }
    
    private File getLogDir() {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            externalDir = context.getFilesDir();
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
            writeErrorLog("错误: [ModelDownloader] 初始化日志失败 - " + e.getClass().getName() + ": " + e.getMessage());
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
    
    public String getModelsStoragePath() {
        return getModelsStorageDir().getAbsolutePath();
    }
    
    public boolean isModelDownloaded(String modelType) {
        if (isWhisperModel(modelType)) {
            String modelName = getModelName(modelType);
            File whisperDir = new File(getModelsStorageDir(), "whisper");
            File modelFile = new File(whisperDir, modelName);
            boolean exists = modelFile.exists();
            return exists;
        }
        
        String modelName = getModelName(modelType);
        File modelDir = new File(getModelsStorageDir(), modelName);
        File amDir = new File(modelDir, "am");
        File confFile = new File(modelDir, "conf");
        boolean exists = amDir.exists() && confFile.exists();
        return exists;
    }
    
    private boolean isWhisperModel(String modelType) {
        return modelType.startsWith("whisper_");
    }
    
    public boolean isModelDownloaded() {
        return isModelDownloaded(PreferencesManager.MODEL_TYPE_SMALL);
    }
    
    public String getModelPath(String modelType) {
        String modelName = getModelName(modelType);
        if (isWhisperModel(modelType)) {
            File whisperDir = new File(getModelsStorageDir(), "whisper");
            String path = new File(whisperDir, modelName).getAbsolutePath();
            return path;
        }
        File modelDir = getModelsStorageDir();
        String path = new File(modelDir, modelName).getAbsolutePath();
        return path;
    }
    
    public String getModelPath() {
        return getModelPath(PreferencesManager.MODEL_TYPE_SMALL);
    }
    
    public String getModelName(String modelType) {
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
    
    public String getModelUrl(String modelType) {
        switch (modelType) {
            case PreferencesManager.MODEL_TYPE_STANDARD:
                return MODEL_STANDARD_URL;
            case PreferencesManager.MODEL_TYPE_MULTICN:
                return MODEL_MULTICN_URL;
            case PreferencesManager.WHISPER_MODEL_TINY:
                return "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin";
            case PreferencesManager.WHISPER_MODEL_BASE:
                return "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
            case PreferencesManager.WHISPER_MODEL_SMALL:
                return "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin";
            case PreferencesManager.WHISPER_MODEL_MEDIUM:
                return "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin";
            default:
                return MODEL_SMALL_URL;
        }
    }
    
    public String getModelDisplayName(String modelType) {
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
    
    public void downloadModel(String modelType, DownloadCallback callback) {
        initLog();
        
        new Thread(() -> {
            try {
                String modelName = getModelName(modelType);
                
                if (isWhisperModel(modelType)) {
                    writeErrorLog("错误: [ModelDownloader] Whisper模型不能通过此方式下载");
                    callback.onError("请使用设置页面的下载功能下载Whisper模型");
                    return;
                }
                
                String modelUrl = getModelUrl(modelType);
                
                File modelDir = new File(getModelsStorageDir(), modelName);
                if (modelDir.exists()) {
                    deleteDirectory(modelDir);
                }
                
                File zipFile = new File(context.getCacheDir(), "model_" + modelType + ".zip");
                
                Request request = new Request.Builder()
                        .url(modelUrl)
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        writeErrorLog("错误: [ModelDownloader] 下载失败，HTTP状态码: " + response.code());
                        callback.onError("下载失败: " + response.code());
                        return;
                    }
                    
                    long contentLength = response.body().contentLength();
                    InputStream inputStream = response.body().byteStream();
                    FileOutputStream outputStream = new FileOutputStream(zipFile);
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1 && !isCancelled) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                        
                        if (contentLength > 0 && callback != null) {
                            int progress = (int) ((totalBytes * 100) / contentLength);
                            callback.onProgress(progress / 2);
                        }
                    }
                    
                    outputStream.close();
                    inputStream.close();
                    
                    if (isCancelled) {
                        zipFile.delete();
                        callback.onError("下载已取消");
                        return;
                    }
                    
                    unzipModel(zipFile, callback, modelType);
                    zipFile.delete();
                    
                }
                
            } catch (Exception e) {
                writeErrorLog("错误: [ModelDownloader] 下载异常 - " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                Log.e(TAG, "Download error", e);
                callback.onError("下载失败: " + e.getMessage());
            }
        }).start();
    }
    
    public void downloadModel(DownloadCallback callback) {
        downloadModel(PreferencesManager.MODEL_TYPE_SMALL, callback);
    }
    
    private void unzipModel(File zipFile, DownloadCallback callback, String modelType) throws IOException {
        File modelDir = getModelsStorageDir();
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        
        long totalSize = zipFile.length();
        long extractedBytes = 0;
        
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
                    
                    if (callback != null && totalSize > 0) {
                        int progress = 50 + (int) ((extractedBytes * 50) / totalSize);
                        callback.onProgress(progress);
                    }
                }
                
                outputStream.close();
            }
            
            zipInputStream.closeEntry();
        }
        
        zipInputStream.close();
        
        if (!isCancelled) {
            String modelPath = getModelPath(modelType);
            callback.onComplete(modelPath);
        } else {
        }
    }
    
    public boolean deleteModel(String modelType) {
        String modelName = getModelName(modelType);
        if (isWhisperModel(modelType)) {
            File whisperDir = new File(getModelsStorageDir(), "whisper");
            File modelFile = new File(whisperDir, modelName);
            if (modelFile.exists()) {
                boolean deleted = modelFile.delete();
                return deleted;
            }
            return false;
        }
        
        File modelDir = new File(getModelsStorageDir(), modelName);
        if (modelDir.exists()) {
            deleteDirectory(modelDir);
            return true;
        }
        return false;
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
    
    public void cancel() {
        isCancelled = true;
    }
}

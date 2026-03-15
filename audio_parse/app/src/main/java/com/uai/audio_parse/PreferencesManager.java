package com.uai.audio_parse;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 偏好设置管理器
 * 负责保存和读取应用的配置信息
 * 包括API配置、模型选择、模型路径等
 * 
 * @author AI Generated
 * @version 1.0
 */
public class PreferencesManager {
    private static final String TAG = "PreferencesManager";
    private static final String LOG_DIR_NAME = "TingJian_Logs";
    private static final String LOG_FILE_NAME = "transcription_log.txt";
    
    private static final String PREF_NAME = "audio_parse_prefs";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_API_TOKEN = "api_token";
    private static final String KEY_MODEL_NAME = "model_name";
    private static final String KEY_MODEL_PATH = "model_path";
    private static final String KEY_SELECTED_MODEL_TYPE = "selected_model_type";
    private static final String KEY_SPEECH_API_URL = "speech_api_url";
    private static final String KEY_SPEECH_API_TOKEN = "speech_api_token";
    private static final String KEY_USE_ONLINE_SPEECH = "use_online_speech";
    private static final String KEY_ENGINE_TYPE = "engine_type";
    
    private final Context context;
    
    public static final String MODEL_TYPE_SMALL = "small";
    public static final String MODEL_TYPE_STANDARD = "standard";
    public static final String MODEL_TYPE_MULTICN = "multicn";
    
    public static final String ENGINE_VOSK = "vosk";
    public static final String ENGINE_WHISPER = "whisper";
    
    public static final String WHISPER_MODEL_TINY = "whisper_tiny";
    public static final String WHISPER_MODEL_BASE = "whisper_base";
    public static final String WHISPER_MODEL_SMALL = "whisper_small";
    public static final String WHISPER_MODEL_MEDIUM = "whisper_medium";
    
    private final SharedPreferences sharedPreferences;
    
    public PreferencesManager(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public void saveApiConfig(String apiUrl, String apiToken, String modelName) {
        sharedPreferences.edit()
                .putString(KEY_API_URL, apiUrl)
                .putString(KEY_API_TOKEN, apiToken)
                .putString(KEY_MODEL_NAME, modelName)
                .apply();
    }
    
    public String getApiUrl() {
        return sharedPreferences.getString(KEY_API_URL, "");
    }
    
    public String getApiToken() {
        return sharedPreferences.getString(KEY_API_TOKEN, "");
    }
    
    public String getModelName() {
        return sharedPreferences.getString(KEY_MODEL_NAME, "gpt-3.5-turbo");
    }
    
    public boolean isApiConfigured() {
        String apiUrl = getApiUrl();
        String apiToken = getApiToken();
        return apiUrl != null && !apiUrl.isEmpty() && apiToken != null && !apiToken.isEmpty();
    }
    
    public void saveModelPath(String modelPath) {
        sharedPreferences.edit()
                .putString(KEY_MODEL_PATH, modelPath)
                .apply();
    }
    
    public String getModelPath() {
        return sharedPreferences.getString(KEY_MODEL_PATH, "");
    }
    
    public void saveSelectedModelType(String modelType) {
        sharedPreferences.edit()
                .putString(KEY_SELECTED_MODEL_TYPE, modelType)
                .apply();
    }
    
    public String getSelectedModelType() {
        return sharedPreferences.getString(KEY_SELECTED_MODEL_TYPE, MODEL_TYPE_SMALL);
    }
    
    public void saveSpeechApiConfig(String speechApiUrl, String speechApiToken) {
        sharedPreferences.edit()
                .putString(KEY_SPEECH_API_URL, speechApiUrl)
                .putString(KEY_SPEECH_API_TOKEN, speechApiToken)
                .apply();
    }
    
    public String getSpeechApiUrl() {
        return sharedPreferences.getString(KEY_SPEECH_API_URL, "");
    }
    
    public String getSpeechApiToken() {
        return sharedPreferences.getString(KEY_SPEECH_API_TOKEN, "");
    }
    
    public boolean isSpeechApiConfigured() {
        String speechApiUrl = getSpeechApiUrl();
        String speechApiToken = getSpeechApiToken();
        return speechApiUrl != null && !speechApiUrl.isEmpty() && 
               speechApiToken != null && !speechApiToken.isEmpty();
    }
    
    public void setUseOnlineSpeech(boolean useOnline) {
        sharedPreferences.edit()
                .putBoolean(KEY_USE_ONLINE_SPEECH, useOnline)
                .apply();
    }
    
    public boolean getUseOnlineSpeech() {
        return sharedPreferences.getBoolean(KEY_USE_ONLINE_SPEECH, false);
    }
    
    public void saveEngineType(String engineType) {
        sharedPreferences.edit()
                .putString(KEY_ENGINE_TYPE, engineType)
                .apply();
    }
    
    public String getEngineType() {
        String result = sharedPreferences.getString(KEY_ENGINE_TYPE, ENGINE_VOSK);
        return result;
    }
    
    public boolean isWhisperEngine() {
        boolean result = ENGINE_WHISPER.equals(getEngineType());
        return result;
    }
    
    private void writeErrorLog(String message) {
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                externalDir = context.getFilesDir();
            }
            
            File logDir = new File(externalDir, LOG_DIR_NAME);
            if (!logDir.exists()) {
                logDir.mkdirs();
                Log.i(TAG, "创建日志目录: " + logDir.getAbsolutePath());
            }
            
            File logFile = new File(logDir, LOG_FILE_NAME);
            FileOutputStream logOutputStream = new FileOutputStream(logFile, true);
            
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            String logLine = "[" + timestamp + "] " + message + "\n";
            logOutputStream.write(logLine.getBytes());
            logOutputStream.flush();
            logOutputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "写入日志失败", e);
        }
    }
}

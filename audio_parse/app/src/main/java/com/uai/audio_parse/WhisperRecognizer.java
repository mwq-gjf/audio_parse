package com.uai.audio_parse;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WhisperRecognizer {
    private static final String TAG = "WhisperRecognizer";
    
    private static final String LOG_DIR_NAME = "TingJian_Logs";
    private static final String LOG_FILE_NAME = "transcription_log.txt";
    
    private static final int SEGMENT_DURATION_SECONDS = 15;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;
    
    private File logFile;
    private FileOutputStream logOutputStream;
    private Context context;
    
    public static final String MODEL_TINY = "ggml-tiny.bin";
    public static final String MODEL_BASE = "ggml-base.bin";
    public static final String MODEL_SMALL = "ggml-small.bin";
    public static final String MODEL_MEDIUM = "ggml-medium.bin";
    
    public static final String LANG_AUTO = "auto";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";
    
    private String modelPath;
    private boolean isLoaded = false;
    private static boolean libraryLoaded = false;
    
    public interface SegmentCallback {
        void onSegmentTranscribed(String segmentText, int segmentIndex, int totalSegments);
        void onProgress(int progress);
    }
    
    static {
        try {
            System.loadLibrary("whisper");
            libraryLoaded = true;
            Log.i(TAG, "Whisper library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            libraryLoaded = false;
            Log.e(TAG, "Failed to load whisper library: " + e.getMessage());
        }
    }
    
    public static boolean isLibraryAvailable() {
        return libraryLoaded;
    }
    
    public WhisperRecognizer(Context context) {
        if (!libraryLoaded) {
            throw new RuntimeException("Whisper native library not loaded");
        }
        this.context = context;
        initLog();
    }
    
    private void initLog() {
        try {
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir == null) {
                externalDir = context.getFilesDir();
            }
            
            File logDir = new File(externalDir, LOG_DIR_NAME);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            logFile = new File(logDir, LOG_FILE_NAME);
            logOutputStream = new FileOutputStream(logFile, true);
        } catch (Exception e) {
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
    
    public boolean loadModel(String modelPath) {
        if (isLoaded) {
            freeModel();
        }
        
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            writeErrorLog("错误: [WhisperRecognizer] Whisper模型文件不存在 - " + modelPath);
            Log.e(TAG, "Model file not found: " + modelPath);
            return false;
        }
        
        this.modelPath = modelPath;
        isLoaded = loadModelNative(modelPath);
        
        if (isLoaded) {
            Log.i(TAG, "Model loaded successfully: " + modelPath);
        } else {
            writeErrorLog("错误: [WhisperRecognizer] Whisper模型加载失败 - " + modelPath);
            Log.e(TAG, "Failed to load model: " + modelPath);
        }
        
        return isLoaded;
    }
    
    public boolean isModelLoaded() {
        return isLoaded;
    }
    
    public void freeModel() {
        if (isLoaded) {
            freeModelNative();
            isLoaded = false;
            Log.i(TAG, "Model freed");
        }
    }
    
    public String transcribe(String audioPath) {
        return transcribe(audioPath, LANG_ZH);
    }
    
    public String transcribe(String audioPath, String language) {
        if (!isLoaded) {
            writeErrorLog("错误: [WhisperRecognizer] Whisper模型未加载");
            Log.e(TAG, "Model not loaded");
            return "";
        }
        
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            writeErrorLog("错误: [WhisperRecognizer] 音频文件不存在: " + audioPath);
            Log.e(TAG, "Audio file not found: " + audioPath);
            return "";
        }
        
        String result = transcribeNative(audioPath, language);
        Log.i(TAG, "Transcription completed");
        return result;
    }
    
    public String transcribeWithTimestamps(String audioPath, String language) {
        if (!isLoaded) {
            writeErrorLog("错误: [WhisperRecognizer] Whisper模型未加载");
            Log.e(TAG, "Model not loaded");
            return "";
        }
        
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            writeErrorLog("错误: [WhisperRecognizer] 音频文件不存在: " + audioPath);
            Log.e(TAG, "Audio file not found: " + audioPath);
            return "";
        }
        
        Log.i(TAG, "Starting transcription with timestamps for: " + audioPath);
        String result = transcribeWithTimestampsNative(audioPath, language);
        Log.i(TAG, "Transcription with timestamps completed");
        return result;
    }
    
    public String getModelPath() {
        return modelPath;
    }
    
    public String transcribeInSegments(String audioPath, String language, SegmentCallback callback) {
        if (!isLoaded) {
            writeErrorLog("错误: [WhisperRecognizer] Whisper模型未加载");
            Log.e(TAG, "Model not loaded");
            return "";
        }
        
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            writeErrorLog("错误: [WhisperRecognizer] 音频文件不存在: " + audioPath);
            Log.e(TAG, "Audio file not found: " + audioPath);
            return "";
        }
        
        try {
            FileInputStream fis = new FileInputStream(audioFile);
            byte[] header = new byte[44];
            fis.read(header);
            
            int audioSampleRate = byteArrayToInt(header, 24);
            int audioChannels = byteArrayToShort(header, 22);
            int audioBitsPerSample = byteArrayToShort(header, 34);
            int dataSize = byteArrayToInt(header, 40);
            
            if (audioSampleRate != SAMPLE_RATE || audioChannels != CHANNELS || audioBitsPerSample != BITS_PER_SAMPLE) {
                fis.close();
                return transcribe(audioPath, language);
            }
            
            int bytesPerSample = audioBitsPerSample / 8;
            int totalSamples = dataSize / bytesPerSample;
            int segmentSamples = SEGMENT_DURATION_SECONDS * SAMPLE_RATE;
            int totalSegments = (totalSamples + segmentSamples - 1) / segmentSamples;
            
            StringBuilder fullResult = new StringBuilder();
            byte[] audioData = new byte[dataSize];
            fis.read(audioData);
            fis.close();
            
            for (int i = 0; i < totalSegments; i++) {
                int startSample = i * segmentSamples;
                int endSample = Math.min(startSample + segmentSamples, totalSamples);
                int segmentDataSize = (endSample - startSample) * bytesPerSample;
                
                File segmentFile = createWavFile(audioData, startSample * bytesPerSample, segmentDataSize);
                if (segmentFile == null) {
                    writeErrorLog("错误: [WhisperRecognizer] 创建分段文件失败");
                    continue;
                }
                
                String segmentResult = transcribeNative(segmentFile.getAbsolutePath(), language);
                segmentFile.delete();
                
                if (segmentResult != null && !segmentResult.trim().isEmpty()) {
                    fullResult.append(segmentResult.trim()).append(" ");
                    
                    if (callback != null) {
                        final String text = segmentResult.trim();
                        final int segIndex = i + 1;
                        final int progress = (int) ((segIndex * 100.0) / totalSegments);
                        callback.onSegmentTranscribed(text, segIndex, totalSegments);
                        callback.onProgress(progress);
                    }
                }
            }
            
            return fullResult.toString().trim();
            
        } catch (Exception e) {
            writeErrorLog("错误: [WhisperRecognizer] 分段转写异常 - " + e.getClass().getName() + ": " + e.getMessage());
            Log.e(TAG, "Segment transcription error", e);
            return transcribe(audioPath, language);
        }
    }
    
    private File createWavFile(byte[] audioData, int offset, int dataSize) {
        try {
            File tempFile = File.createTempFile("segment_", ".wav", context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            
            int fileSize = 36 + dataSize;
            byte[] header = new byte[44];
            
            header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
            intToByteArray(fileSize, header, 4);
            header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
            header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
            intToByteArray(16, header, 16);
            shortToByteArray((short) 1, header, 20);
            shortToByteArray((short) CHANNELS, header, 22);
            intToByteArray(SAMPLE_RATE, header, 24);
            intToByteArray(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8, header, 28);
            shortToByteArray((short) (CHANNELS * BITS_PER_SAMPLE / 8), header, 32);
            shortToByteArray((short) BITS_PER_SAMPLE, header, 34);
            header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
            intToByteArray(dataSize, header, 40);
            
            fos.write(header);
            fos.write(audioData, offset, dataSize);
            fos.close();
            
            return tempFile;
        } catch (Exception e) {
            writeErrorLog("错误: [WhisperRecognizer] 创建WAV文件失败 - " + e.getMessage());
            Log.e(TAG, "Failed to create WAV file", e);
            return null;
        }
    }
    
    private int byteArrayToInt(byte[] b, int offset) {
        return (b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8) | 
               ((b[offset + 2] & 0xFF) << 16) | ((b[offset + 3] & 0xFF) << 24);
    }
    
    private short byteArrayToShort(byte[] b, int offset) {
        return (short) ((b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8));
    }
    
    private void intToByteArray(int value, byte[] b, int offset) {
        b[offset] = (byte) (value & 0xFF);
        b[offset + 1] = (byte) ((value >> 8) & 0xFF);
        b[offset + 2] = (byte) ((value >> 16) & 0xFF);
        b[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }
    
    private void shortToByteArray(short value, byte[] b, int offset) {
        b[offset] = (byte) (value & 0xFF);
        b[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }
    
    private native boolean loadModelNative(String modelPath);
    private native boolean isModelLoadedNative();
    private native void freeModelNative();
    private native String transcribeNative(String audioPath, String language);
    private native String transcribeWithTimestampsNative(String audioPath, String language);
}

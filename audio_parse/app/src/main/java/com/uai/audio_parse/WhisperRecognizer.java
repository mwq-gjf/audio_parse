package com.uai.audio_parse;

import android.util.Log;

import java.io.File;

public class WhisperRecognizer {
    private static final String TAG = "WhisperRecognizer";
    
    public static final String MODEL_TINY = "ggml-tiny.bin";
    public static final String MODEL_BASE = "ggml-base.bin";
    public static final String MODEL_SMALL = "ggml-small.bin";
    public static final String MODEL_MEDIUM = "ggml-medium.bin";
    
    public static final String LANG_AUTO = "auto";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";
    
    private String modelPath;
    private boolean isLoaded = false;
    
    static {
        try {
            System.loadLibrary("whisper");
            Log.i(TAG, "Whisper library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load whisper library: " + e.getMessage());
        }
    }
    
    public WhisperRecognizer() {
    }
    
    public boolean loadModel(String modelPath) {
        if (isLoaded) {
            freeModel();
        }
        
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: " + modelPath);
            return false;
        }
        
        this.modelPath = modelPath;
        isLoaded = loadModelNative(modelPath);
        
        if (isLoaded) {
            Log.i(TAG, "Model loaded successfully: " + modelPath);
        } else {
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
        return transcribe(audioPath, LANG_AUTO);
    }
    
    public String transcribe(String audioPath, String language) {
        if (!isLoaded) {
            Log.e(TAG, "Model not loaded");
            return "";
        }
        
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file not found: " + audioPath);
            return "";
        }
        
        Log.i(TAG, "Starting transcription for: " + audioPath);
        String result = transcribeNative(audioPath, language);
        Log.i(TAG, "Transcription completed");
        
        return result;
    }
    
    public String transcribeWithTimestamps(String audioPath, String language) {
        if (!isLoaded) {
            Log.e(TAG, "Model not loaded");
            return "";
        }
        
        File audioFile = new File(audioPath);
        if (!audioFile.exists()) {
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
    
    private native boolean loadModelNative(String modelPath);
    private native boolean isModelLoadedNative();
    private native void freeModelNative();
    private native String transcribeNative(String audioPath, String language);
    private native String transcribeWithTimestampsNative(String audioPath, String language);
}

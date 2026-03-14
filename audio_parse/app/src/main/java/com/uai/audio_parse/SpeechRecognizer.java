package com.uai.audio_parse;

import android.content.Context;
import android.util.Log;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 语音识别器
 * 使用Vosk离线语音识别引擎进行音频转文字
 * 支持长音频分段处理，提供实时进度反馈
 * 
 * @author AI Generated
 * @version 1.0
 */
public class SpeechRecognizer implements RecognitionListener {
    private static final String TAG = "SpeechRecognizer";
    private static final float SAMPLE_RATE = 16000.0f;
    
    private Model model;
    private SpeechService speechService;
    private RecognitionCallback callback;
    private volatile boolean isRunning = false;
    private StringBuilder resultBuilder;
    
    public interface RecognitionCallback {
        void onPartialResult(String partial);
        void onFinalResult(String result);
        void onComplete(String fullResult);
        void onError(String error);
        void onProgress(int progress);
    }
    
    public SpeechRecognizer(Context context, String modelPath, RecognitionCallback callback) throws IOException {
        this.callback = callback;
        this.resultBuilder = new StringBuilder();
        
        File modelDir = new File(modelPath);
        if (!modelDir.exists()) {
            throw new IOException("Model not found at: " + modelPath);
        }
        
        model = new Model(modelPath);
    }
    
    public void recognizeFile(String audioPath) {
        new Thread(() -> {
            try {
                isRunning = true;
                resultBuilder = new StringBuilder();
                
                File audioFile = new File(audioPath);
                long fileSize = audioFile.length();
                long totalBytes = 0;
                
                Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
                InputStream inputStream = new FileInputStream(audioFile);
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                
                int segmentCount = 0;
                int segmentDuration = 15;
                int bytesPerSegment = (int) (SAMPLE_RATE * 2 * segmentDuration);
                int bytesInCurrentSegment = 0;
                
                while ((bytesRead = inputStream.read(buffer)) >= 0 && isRunning) {
                    totalBytes += bytesRead;
                    bytesInCurrentSegment += bytesRead;
                    
                    if (bytesInCurrentSegment >= bytesPerSegment) {
                        segmentCount++;
                        bytesInCurrentSegment = 0;
                        
                        String finalResult = recognizer.getFinalResult().trim();
                        if (!finalResult.isEmpty()) {
                            String text = extractText(finalResult);
                            if (!text.isEmpty()) {
                                resultBuilder.append(text).append("\n");
                                if (callback != null) {
                                    callback.onFinalResult(text);
                                }
                            }
                        }
                        
                        recognizer.close();
                        recognizer = new Recognizer(model, SAMPLE_RATE);
                    }
                    
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        String result = recognizer.getResult().trim();
                        if (!result.isEmpty()) {
                            String text = extractText(result);
                            if (!text.isEmpty()) {
                                resultBuilder.append(text).append("\n");
                                if (callback != null) {
                                    callback.onFinalResult(text);
                                }
                            }
                        }
                    } else {
                        String partial = recognizer.getPartialResult().trim();
                        if (!partial.isEmpty()) {
                            String text = extractText(partial);
                            if (!text.isEmpty() && callback != null) {
                                callback.onPartialResult(text);
                            }
                        }
                    }
                    
                    if (callback != null && fileSize > 0) {
                        int progress = (int) ((totalBytes * 100) / fileSize);
                        callback.onProgress(progress);
                    }
                }
                
                String finalResult = recognizer.getFinalResult().trim();
                if (!finalResult.isEmpty()) {
                    String text = extractText(finalResult);
                    if (!text.isEmpty()) {
                        resultBuilder.append(text);
                        if (callback != null) {
                            callback.onFinalResult(text);
                        }
                    }
                }
                
                inputStream.close();
                recognizer.close();
                
                if (callback != null) {
                    callback.onComplete(resultBuilder.toString().trim());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Recognition error", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            } finally {
                isRunning = false;
            }
        }).start();
    }
    
    private String extractText(String jsonResult) {
        try {
            String text = jsonResult;
            if (text.contains("\"text\" : \"")) {
                int start = text.indexOf("\"text\" : \"") + 10;
                int end = text.indexOf("\"", start);
                if (start > 9 && end > start) {
                    return text.substring(start, end);
                }
            } else if (text.contains("\"partial\" : \"")) {
                int start = text.indexOf("\"partial\" : \"") + 13;
                int end = text.indexOf("\"", start);
                if (start > 12 && end > start) {
                    return text.substring(start, end);
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }
    
    public void stop() {
        isRunning = false;
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
    }
    
    public void close() {
        stop();
        if (model != null) {
            model.close();
        }
    }
    
    @Override
    public void onPartialResult(String hypothesis) {
    }
    
    @Override
    public void onResult(String hypothesis) {
    }
    
    @Override
    public void onFinalResult(String hypothesis) {
    }
    
    @Override
    public void onError(Exception e) {
        if (callback != null) {
            callback.onError(e.getMessage());
        }
    }
    
    @Override
    public void onTimeout() {
    }
}

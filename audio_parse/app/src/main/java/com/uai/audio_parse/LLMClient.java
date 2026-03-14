package com.uai.audio_parse;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * LLM客户端
 * 负责与大语言模型API进行通信
 * 支持OpenAI兼容的API接口，用于文本总结功能
 * 
 * @author AI Generated
 * @version 1.0
 */
public class LLMClient {
    private static final String TAG = "LLMClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient client;
    private final String apiUrl;
    private final String apiToken;
    private final String modelName;
    
    public LLMClient(String apiUrl, String apiToken, String modelName) {
        this.apiUrl = apiUrl;
        this.apiToken = apiToken;
        this.modelName = modelName;
        
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public interface SummaryCallback {
        void onSuccess(String summary);
        void onError(String error);
        void onProgress(String partial);
    }
    
    public void summarize(String text, SummaryCallback callback) {
        new Thread(() -> {
            try {
                String normalizedUrl = normalizeUrl(apiUrl);
                
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", modelName);
                
                JSONArray messages = new JSONArray();
                
                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", "你是一个专业的文本总结助手。请对用户提供的文本进行简洁、准确的总结，突出重点内容。");
                messages.put(systemMessage);
                
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", "请总结以下文本内容：\n\n" + text);
                messages.put(userMessage);
                
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.7);
                requestBody.put("max_tokens", 2000);
                
                String url = normalizedUrl + "/chat/completions";
                
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + apiToken)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody.toString(), JSON))
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        callback.onError("API请求失败: " + response.code() + " - " + errorBody);
                        return;
                    }
                    
                    String responseBody = response.body() != null ? response.body().string() : "";
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject firstChoice = choices.getJSONObject(0);
                        JSONObject message = firstChoice.getJSONObject("message");
                        String content = message.getString("content");
                        callback.onSuccess(content);
                    } else {
                        callback.onError("API返回结果为空");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Summary error", e);
                callback.onError("总结失败: " + e.getMessage());
            }
        }).start();
    }
    
    public void testConnection(TestCallback callback) {
        new Thread(() -> {
            try {
                String normalizedUrl = normalizeUrl(apiUrl);
                
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", modelName);
                
                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", "Hi");
                messages.put(message);
                
                requestBody.put("messages", messages);
                requestBody.put("max_tokens", 5);
                
                String url = normalizedUrl + "/chat/completions";
                
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + apiToken)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(requestBody.toString(), JSON))
                        .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        callback.onError("连接失败: " + response.code() + " - " + errorBody);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Test connection error", e);
                callback.onError("连接失败: " + e.getMessage());
            }
        }).start();
    }
    
    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        url = url.trim();
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        if (url.endsWith("/v1")) {
            return url;
        }
        
        return url + "/v1";
    }
    
    public interface TestCallback {
        void onSuccess();
        void onError(String error);
    }
}

package com.uai.audio_parse;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 转写历史记录管理器
 * 负责保存和读取转写历史记录
 * 使用SharedPreferences存储，最多保存10条记录
 * 
 * @author AI Generated
 * @version 1.0
 */
public class TranscriptionHistoryManager {
    private static final String PREF_NAME = "transcription_history";
    private static final String KEY_HISTORY = "history_list";
    private static final int MAX_HISTORY_SIZE = 10;
    
    private final SharedPreferences sharedPreferences;
    private final SimpleDateFormat dateFormat;
    
    public static class HistoryItem {
        public String id;
        public String fileName;
        public String text;
        public String summary;
        public long timestamp;
        
        public HistoryItem(String id, String fileName, String text, String summary, long timestamp) {
            this.id = id;
            this.fileName = fileName;
            this.text = text;
            this.summary = summary;
            this.timestamp = timestamp;
        }
        
        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
    
    public TranscriptionHistoryManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    }
    
    public void addHistory(String fileName, String text, String summary) {
        List<HistoryItem> historyList = getHistoryList();
        
        String id = String.valueOf(System.currentTimeMillis());
        HistoryItem newItem = new HistoryItem(id, fileName, text, summary, System.currentTimeMillis());
        
        historyList.add(0, newItem);
        
        while (historyList.size() > MAX_HISTORY_SIZE) {
            historyList.remove(historyList.size() - 1);
        }
        
        saveHistoryList(historyList);
    }
    
    public List<HistoryItem> getHistoryList() {
        List<HistoryItem> historyList = new ArrayList<>();
        
        String historyJson = sharedPreferences.getString(KEY_HISTORY, "[]");
        try {
            JSONArray jsonArray = new JSONArray(historyJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                HistoryItem historyItem = new HistoryItem(
                        item.getString("id"),
                        item.getString("fileName"),
                        item.getString("text"),
                        item.optString("summary", ""),
                        item.getLong("timestamp")
                );
                historyList.add(historyItem);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        return historyList;
    }
    
    public void deleteHistory(String id) {
        List<HistoryItem> historyList = getHistoryList();
        
        for (int i = 0; i < historyList.size(); i++) {
            if (historyList.get(i).id.equals(id)) {
                historyList.remove(i);
                break;
            }
        }
        
        saveHistoryList(historyList);
    }
    
    public void clearAllHistory() {
        sharedPreferences.edit().remove(KEY_HISTORY).apply();
    }
    
    public boolean hasHistory() {
        return !getHistoryList().isEmpty();
    }
    
    private void saveHistoryList(List<HistoryItem> historyList) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (HistoryItem item : historyList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", item.id);
                jsonObject.put("fileName", item.fileName);
                jsonObject.put("text", item.text);
                jsonObject.put("summary", item.summary);
                jsonObject.put("timestamp", item.timestamp);
                jsonArray.put(jsonObject);
            }
            
            sharedPreferences.edit().putString(KEY_HISTORY, jsonArray.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

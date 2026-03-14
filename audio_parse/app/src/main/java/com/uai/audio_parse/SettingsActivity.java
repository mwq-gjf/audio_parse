package com.uai.audio_parse;

import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.textfield.TextInputEditText;

/**
 * 设置界面Activity
 * 负责管理API配置和语音识别模型的选择与下载
 * 
 * @author AI Generated
 * @version 1.0
 */
public class SettingsActivity extends AppCompatActivity {
    private TextInputEditText apiUrlEditText;
    private TextInputEditText apiTokenEditText;
    private TextInputEditText modelNameEditText;
    private Button testConnectionButton;
    private Button saveButton;
    
    private TextView currentModelTextView;
    private CardView smallModelCard;
    private TextView smallModelStatusTextView;
    private ImageView smallModelCheckIcon;
    private CardView standardModelCard;
    private TextView standardModelStatusTextView;
    private ImageView standardModelCheckIcon;
    private CardView multicnModelCard;
    private TextView multicnModelStatusTextView;
    private ImageView multicnModelCheckIcon;
    
    private PreferencesManager preferencesManager;
    private ModelDownloader modelDownloader;
    private LLMClient llmClient;
    private ProgressDialog progressDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        initViews();
        initManagers();
        loadSettings();
        updateModelStatus();
    }
    
    private void initViews() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        
        apiUrlEditText = findViewById(R.id.apiUrlEditText);
        apiTokenEditText = findViewById(R.id.apiTokenEditText);
        modelNameEditText = findViewById(R.id.modelNameEditText);
        testConnectionButton = findViewById(R.id.testConnectionButton);
        saveButton = findViewById(R.id.saveButton);
        
        currentModelTextView = findViewById(R.id.currentModelTextView);
        smallModelCard = findViewById(R.id.smallModelCard);
        smallModelStatusTextView = findViewById(R.id.smallModelStatusTextView);
        smallModelCheckIcon = findViewById(R.id.smallModelCheckIcon);
        standardModelCard = findViewById(R.id.standardModelCard);
        standardModelStatusTextView = findViewById(R.id.standardModelStatusTextView);
        standardModelCheckIcon = findViewById(R.id.standardModelCheckIcon);
        multicnModelCard = findViewById(R.id.multicnModelCard);
        multicnModelStatusTextView = findViewById(R.id.multicnModelStatusTextView);
        multicnModelCheckIcon = findViewById(R.id.multicnModelCheckIcon);
        
        testConnectionButton.setOnClickListener(v -> testConnection());
        saveButton.setOnClickListener(v -> saveSettings());
        
        smallModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.MODEL_TYPE_SMALL));
        standardModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.MODEL_TYPE_STANDARD));
        multicnModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.MODEL_TYPE_MULTICN));
    }
    
    private void initManagers() {
        preferencesManager = new PreferencesManager(this);
        modelDownloader = new ModelDownloader(this);
    }
    
    private void loadSettings() {
        apiUrlEditText.setText(preferencesManager.getApiUrl());
        apiTokenEditText.setText(preferencesManager.getApiToken());
        modelNameEditText.setText(preferencesManager.getModelName());
    }
    
    private void updateModelStatus() {
        String selectedModelType = preferencesManager.getSelectedModelType();
        String selectedModelName = modelDownloader.getModelDisplayName(selectedModelType);
        currentModelTextView.setText(getString(R.string.current_model, selectedModelName));
        
        updateModelItem(PreferencesManager.MODEL_TYPE_SMALL, smallModelStatusTextView, smallModelCheckIcon);
        updateModelItem(PreferencesManager.MODEL_TYPE_STANDARD, standardModelStatusTextView, standardModelCheckIcon);
        updateModelItem(PreferencesManager.MODEL_TYPE_MULTICN, multicnModelStatusTextView, multicnModelCheckIcon);
    }
    
    private void updateModelItem(String modelType, TextView statusTextView, ImageView checkIcon) {
        boolean isDownloaded = modelDownloader.isModelDownloaded(modelType);
        boolean isSelected = modelType.equals(preferencesManager.getSelectedModelType());
        
        if (isDownloaded) {
            statusTextView.setText(R.string.model_downloaded);
            statusTextView.setTextColor(Color.parseColor("#4CAF50"));
            checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        } else {
            statusTextView.setText(R.string.model_not_downloaded);
            statusTextView.setTextColor(Color.parseColor("#F44336"));
            checkIcon.setVisibility(View.GONE);
        }
    }
    
    private void updateOnlineModelItem(String modelType, TextView statusTextView, ImageView checkIcon) {
        boolean isSelected = modelType.equals(preferencesManager.getSelectedModelType());
        
        statusTextView.setText("在线模型");
        statusTextView.setTextColor(Color.parseColor("#FF9800"));
        checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    }
    
    private void handleModelCardClick(String modelType) {
        boolean isDownloaded = modelDownloader.isModelDownloaded(modelType);
        boolean isSelected = modelType.equals(preferencesManager.getSelectedModelType());
        
        if (isSelected) {
            return;
        }
        
        if (isDownloaded) {
            switchModel(modelType);
        } else {
            downloadModel(modelType);
        }
    }
    
    private void downloadModel(String modelType) {
        String modelName = modelDownloader.getModelDisplayName(modelType);
        String sizeHint = PreferencesManager.MODEL_TYPE_STANDARD.equals(modelType) ? "1.3GB" : "50MB";
        
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.model_download_progress, 0) + "\n" + modelName + " (" + sizeHint + ")");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        modelDownloader.downloadModel(modelType, new ModelDownloader.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> {
                    progressDialog.setProgress(progress);
                    if (progress < 50) {
                        progressDialog.setMessage(getString(R.string.model_download_progress, progress * 2) + "\n" + modelName + " (" + sizeHint + ")");
                    } else {
                        progressDialog.setMessage(getString(R.string.model_extract_progress, (progress - 50) * 2) + "\n" + modelName + " (" + sizeHint + ")");
                    }
                });
            }
            
            @Override
            public void onComplete(String modelPath) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    preferencesManager.saveSelectedModelType(modelType);
                    preferencesManager.saveModelPath(modelPath);
                    updateModelStatus();
                    Toast.makeText(SettingsActivity.this, R.string.model_download_success,
                            Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void switchModel(String modelType) {
        String modelName = modelDownloader.getModelDisplayName(modelType);
        preferencesManager.saveSelectedModelType(modelType);
        String modelPath = modelDownloader.getModelPath(modelType);
        preferencesManager.saveModelPath(modelPath);
        
        updateModelStatus();
        Toast.makeText(this, getString(R.string.model_switch_success, modelName),
                Toast.LENGTH_SHORT).show();
    }
    
    private void showDeleteConfirmDialog(String modelType) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.model_delete_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) -> deleteModel(modelType))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    private void deleteModel(String modelType) {
        if (modelDownloader.deleteModel(modelType)) {
            String otherType = PreferencesManager.MODEL_TYPE_SMALL.equals(modelType) ?
                    PreferencesManager.MODEL_TYPE_STANDARD : PreferencesManager.MODEL_TYPE_SMALL;
            
            if (modelDownloader.isModelDownloaded(otherType)) {
                preferencesManager.saveSelectedModelType(otherType);
                String modelPath = modelDownloader.getModelPath(otherType);
                preferencesManager.saveModelPath(modelPath);
            }
            
            updateModelStatus();
            Toast.makeText(this, R.string.model_delete_success, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.model_delete_failed, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void testConnection() {
        String apiUrl = apiUrlEditText.getText().toString().trim();
        String apiToken = apiTokenEditText.getText().toString().trim();
        String modelName = modelNameEditText.getText().toString().trim();
        
        if (apiUrl.isEmpty() || apiToken.isEmpty() || modelName.isEmpty()) {
            Toast.makeText(this, "请填写所有配置项", Toast.LENGTH_SHORT).show();
            return;
        }
        
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在测试连接...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        llmClient = new LLMClient(apiUrl, apiToken, modelName);
        llmClient.testConnection(new LLMClient.TestCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(SettingsActivity.this, R.string.connection_success,
                            Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(SettingsActivity.this,
                            getString(R.string.connection_failed, error),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void saveSettings() {
        String apiUrl = apiUrlEditText.getText().toString().trim();
        String apiToken = apiTokenEditText.getText().toString().trim();
        String modelName = modelNameEditText.getText().toString().trim();
        
        if (apiUrl.isEmpty() || apiToken.isEmpty() || modelName.isEmpty()) {
            Toast.makeText(this, "请填写所有配置项", Toast.LENGTH_SHORT).show();
            return;
        }
        
        preferencesManager.saveApiConfig(apiUrl, apiToken, modelName);
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}

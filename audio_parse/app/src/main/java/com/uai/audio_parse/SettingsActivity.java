package com.uai.audio_parse;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
    private CardView whisperTinyModelCard;
    private TextView whisperTinyModelStatusTextView;
    private ImageView whisperTinyModelCheckIcon;
    private CardView whisperBaseModelCard;
    private TextView whisperBaseModelStatusTextView;
    private ImageView whisperBaseModelCheckIcon;
    private CardView whisperSmallModelCard;
    private TextView whisperSmallModelStatusTextView;
    private ImageView whisperSmallModelCheckIcon;
    private CardView whisperMediumModelCard;
    private TextView whisperMediumModelStatusTextView;
    private ImageView whisperMediumModelCheckIcon;
    
    private PreferencesManager preferencesManager;
    private ModelDownloader modelDownloader;
    private LLMClient llmClient;
    private ProgressDialog progressDialog;
    private String downloadingModelType;
    
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ModelDownloadService.BROADCAST_DOWNLOAD_PROGRESS.equals(action)) {
                int progress = intent.getIntExtra(ModelDownloadService.EXTRA_PROGRESS, 0);
                String status = intent.getStringExtra(ModelDownloadService.EXTRA_STATUS);
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.setProgress(progress);
                    String modelName = modelDownloader.getModelDisplayName(downloadingModelType);
                    String sizeHint = PreferencesManager.MODEL_TYPE_STANDARD.equals(downloadingModelType) ? "1.3GB" : 
                                     PreferencesManager.MODEL_TYPE_MULTICN.equals(downloadingModelType) ? "1.5GB" : "50MB";
                    if (progress < 50) {
                        progressDialog.setMessage(getString(R.string.model_download_progress, progress * 2) + "\n" + modelName + " (" + sizeHint + ")");
                    } else {
                        progressDialog.setMessage(getString(R.string.model_extract_progress, (progress - 50) * 2) + "\n" + modelName + " (" + sizeHint + ")");
                    }
                }
            } else if (ModelDownloadService.BROADCAST_DOWNLOAD_COMPLETE.equals(action)) {
                String modelPath = intent.getStringExtra(ModelDownloadService.EXTRA_MODEL_PATH);
                String modelName = intent.getStringExtra(ModelDownloadService.EXTRA_MODEL_NAME);
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                updateModelStatus();
                Toast.makeText(SettingsActivity.this, modelName + " 下载完成", Toast.LENGTH_SHORT).show();
            } else if (ModelDownloadService.BROADCAST_DOWNLOAD_ERROR.equals(action)) {
                String error = intent.getStringExtra(ModelDownloadService.EXTRA_ERROR);
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                Toast.makeText(SettingsActivity.this, error, Toast.LENGTH_LONG).show();
            }
        }
    };
    
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
        
        findViewById(R.id.licensesButton).setOnClickListener(v -> showLicensesDialog());
        
        smallModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.MODEL_TYPE_SMALL));
        standardModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.MODEL_TYPE_STANDARD));
        multicnModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.MODEL_TYPE_MULTICN));
        
        whisperTinyModelCard = findViewById(R.id.whisperTinyModelCard);
        whisperTinyModelStatusTextView = findViewById(R.id.whisperTinyModelStatusTextView);
        whisperTinyModelCheckIcon = findViewById(R.id.whisperTinyModelCheckIcon);
        whisperBaseModelCard = findViewById(R.id.whisperBaseModelCard);
        whisperBaseModelStatusTextView = findViewById(R.id.whisperBaseModelStatusTextView);
        whisperBaseModelCheckIcon = findViewById(R.id.whisperBaseModelCheckIcon);
        whisperSmallModelCard = findViewById(R.id.whisperSmallModelCard);
        whisperSmallModelStatusTextView = findViewById(R.id.whisperSmallModelStatusTextView);
        whisperSmallModelCheckIcon = findViewById(R.id.whisperSmallModelCheckIcon);
        whisperMediumModelCard = findViewById(R.id.whisperMediumModelCard);
        whisperMediumModelStatusTextView = findViewById(R.id.whisperMediumModelStatusTextView);
        whisperMediumModelCheckIcon = findViewById(R.id.whisperMediumModelCheckIcon);
        
        whisperTinyModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.WHISPER_MODEL_TINY));
        whisperBaseModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.WHISPER_MODEL_BASE));
        whisperSmallModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.WHISPER_MODEL_SMALL));
        whisperMediumModelCard.setOnClickListener(v -> handleModelCardClick(PreferencesManager.WHISPER_MODEL_MEDIUM));
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
        
        updateModelItem(PreferencesManager.WHISPER_MODEL_TINY, whisperTinyModelStatusTextView, whisperTinyModelCheckIcon);
        updateModelItem(PreferencesManager.WHISPER_MODEL_BASE, whisperBaseModelStatusTextView, whisperBaseModelCheckIcon);
        updateModelItem(PreferencesManager.WHISPER_MODEL_SMALL, whisperSmallModelStatusTextView, whisperSmallModelCheckIcon);
        updateModelItem(PreferencesManager.WHISPER_MODEL_MEDIUM, whisperMediumModelStatusTextView, whisperMediumModelCheckIcon);
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
        if (ModelDownloadService.isDownloading(this)) {
            Toast.makeText(this, "已有模型正在下载中", Toast.LENGTH_SHORT).show();
            return;
        }
        
        downloadingModelType = modelType;
        showDownloadProgressDialog(modelType);
        
        Intent intent = new Intent(this, ModelDownloadService.class);
        intent.setAction(ModelDownloadService.ACTION_START_DOWNLOAD);
        intent.putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, modelType);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
    
    private void showDownloadProgressDialog(String modelType) {
        String modelName = modelDownloader.getModelDisplayName(modelType);
        String sizeHint = PreferencesManager.MODEL_TYPE_STANDARD.equals(modelType) ? "1.3GB" : 
                         PreferencesManager.MODEL_TYPE_MULTICN.equals(modelType) ? "1.5GB" : "50MB";
        
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.model_download_progress, 0) + "\n" + modelName + " (" + sizeHint + ")");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "后台下载", (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(SettingsActivity.this, "模型将在后台继续下载", Toast.LENGTH_SHORT).show();
        });
        progressDialog.show();
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
    
    private void showLicensesDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.licenses_title)
                .setMessage(R.string.licenses_content)
                .setPositiveButton(R.string.confirm, null)
                .create()
                .show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ModelDownloadService.BROADCAST_DOWNLOAD_PROGRESS);
        filter.addAction(ModelDownloadService.BROADCAST_DOWNLOAD_COMPLETE);
        filter.addAction(ModelDownloadService.BROADCAST_DOWNLOAD_ERROR);
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadReceiver, filter);
        
        if (ModelDownloadService.isDownloading(this)) {
            String downloadingModel = ModelDownloadService.getDownloadingModel(this);
            if (downloadingModel != null) {
                downloadingModelType = downloadingModel;
                showDownloadProgressDialog(downloadingModel);
            }
        }
        
        updateModelStatus();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(downloadReceiver);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}

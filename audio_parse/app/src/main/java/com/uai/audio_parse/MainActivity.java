package com.uai.audio_parse;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 应用主界面Activity
 * 负责音频文件的转写、AI总结、结果展示和分享等功能
 * 
 * @author AI Generated
 * @version 1.0
 */
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView fileInfoTextView;
    private Button transcribeButton;
    private Button copyButton;
    private Button saveButton;
    private Button saveAudioButton;
    private Button shareButton;
    private Button summaryButton;
    private Button settingsButton;
    private ProgressBar progressBar;
    private TextView progressTextView;
    private EditText resultTextView;
    private TabLayout tabLayout;
    
    private PreferencesManager preferencesManager;
    private AudioExtractor audioExtractor;
    private SpeechRecognizer speechRecognizer;
    private ModelDownloader modelDownloader;
    private LLMClient llmClient;
    
    private Uri selectedFileUri;
    private String transcribedText = "";
    private String summaryText = "";
    private String extractedAudioPath = "";
    private boolean isShowingSummary = false;
    
    private ProgressDialog progressDialog;
    
    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedFileUri = uri;
                    updateFileInfo();
                }
            }
    );
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initManagers();
        checkPermissions();
        checkModel();
    }
    
    private void initViews() {
        fileInfoTextView = findViewById(R.id.fileInfoTextView);
        transcribeButton = findViewById(R.id.transcribeButton);
        copyButton = findViewById(R.id.copyButton);
        saveButton = findViewById(R.id.saveButton);
        saveAudioButton = findViewById(R.id.saveAudioButton);
        shareButton = findViewById(R.id.shareButton);
        summaryButton = findViewById(R.id.summaryButton);
        settingsButton = findViewById(R.id.settingsButton);
        progressBar = findViewById(R.id.progressBar);
        progressTextView = findViewById(R.id.progressTextView);
        resultTextView = findViewById(R.id.resultTextView);
        tabLayout = findViewById(R.id.tabLayout);
        
        findViewById(R.id.selectFileButton).setOnClickListener(v -> selectFile());
        transcribeButton.setOnClickListener(v -> startTranscription());
        copyButton.setOnClickListener(v -> copyText());
        saveButton.setOnClickListener(v -> saveText());
        saveAudioButton.setOnClickListener(v -> saveAudio());
        shareButton.setOnClickListener(v -> shareText());
        summaryButton.setOnClickListener(v -> summarizeText());
        settingsButton.setOnClickListener(v -> openSettings());
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isShowingSummary = tab.getPosition() == 1;
                updateResultText();
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }
    
    private void initManagers() {
        preferencesManager = new PreferencesManager(this);
        audioExtractor = new AudioExtractor();
        modelDownloader = new ModelDownloader(this);
    }
    
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "需要存储权限才能正常使用", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.stop();
        }
    }
    
    private void checkModel() {
        String selectedModelType = preferencesManager.getSelectedModelType();
        if (!modelDownloader.isModelDownloaded(selectedModelType)) {
            showModelDownloadDialog();
        }
    }
    
    private void showModelDownloadDialog() {
        String selectedModelType = preferencesManager.getSelectedModelType();
        String modelName = modelDownloader.getModelDisplayName(selectedModelType);
        String sizeHint = "";
        String descHint = "";
        
        switch (selectedModelType) {
            case PreferencesManager.MODEL_TYPE_STANDARD:
                sizeHint = "1.3GB";
                descHint = "准确率高，速度较慢";
                break;
            case PreferencesManager.MODEL_TYPE_MULTICN:
                sizeHint = "1.8GB";
                descHint = "支持多种方言，准确率最高";
                break;
            default:
                sizeHint = "50MB";
                descHint = "速度快，准确率适中（推荐）";
                break;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("下载语音模型")
                .setMessage("需要下载 " + modelName + " (" + sizeHint + ")\n" + descHint)
                .setPositiveButton("下载", (dialog, which) -> downloadModel())
                .setNegativeButton("取消", null)
                .setCancelable(false)
                .show();
    }
    
    private void downloadModel() {
        String selectedModelType = preferencesManager.getSelectedModelType();
        
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getString(R.string.downloading_model));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        modelDownloader.downloadModel(selectedModelType, new ModelDownloader.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> progressDialog.setProgress(progress));
            }
            
            @Override
            public void onComplete(String modelPath) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    preferencesManager.saveModelPath(modelPath);
                    Toast.makeText(MainActivity.this, R.string.model_ready, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void selectFile() {
        filePickerLauncher.launch("*/*");
    }
    
    private void updateFileInfo() {
        if (selectedFileUri != null) {
            String fileName = getFileName(selectedFileUri);
            long fileSize = getFileSize(selectedFileUri);
            String sizeStr = AudioExtractor.formatFileSize(fileSize);
            fileInfoTextView.setText(fileName + " (" + sizeStr + ")");
        }
    }
    
    private String getFileName(Uri uri) {
        String fileName = "未知文件";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            
            if ("未知文件".equals(fileName)) {
                String path = uri.getPath();
                if (path != null) {
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        fileName = path.substring(lastSlash + 1);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fileName;
    }
    
    private long getFileSize(Uri uri) {
        java.io.InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                return inputStream.available();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }
    
    private void startTranscription() {
        if (selectedFileUri == null) {
            Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String selectedModelType = preferencesManager.getSelectedModelType();
        if (!modelDownloader.isModelDownloaded(selectedModelType)) {
            showModelDownloadDialog();
            return;
        }
        
        transcribeButton.setEnabled(false);
        transcribeButton.setText(R.string.stop_transcribe);
        progressBar.setVisibility(View.VISIBLE);
        progressTextView.setVisibility(View.VISIBLE);
        transcribedText = "";
        summaryText = "";
        extractedAudioPath = "";
        resultTextView.setText("");
        
        new Thread(() -> {
            try {
                runOnUiThread(() -> {
                    progressTextView.setText(R.string.extracting_audio);
                });
                
                String audioPath = audioExtractor.extractAudio(this, selectedFileUri, progress -> {
                    runOnUiThread(() -> {
                        progressBar.setProgress(progress / 3);
                        String stepText = getString(R.string.extracting_audio);
                        progressTextView.setText(getString(R.string.progress_detail, stepText, progress));
                    });
                });
                
                extractedAudioPath = audioPath;
                
                runOnUiThread(() -> {
                    String stepText = getString(R.string.converting_audio);
                    progressTextView.setText(getString(R.string.progress_detail, stepText, 0));
                });
                
                String modelPath = modelDownloader.getModelPath(selectedModelType);
                speechRecognizer = new SpeechRecognizer(this, 
                        modelPath,
                        new SpeechRecognizer.RecognitionCallback() {
                            @Override
                            public void onPartialResult(String partial) {
                                runOnUiThread(() -> {
                                    if (!isShowingSummary) {
                                        resultTextView.setText(transcribedText + partial);
                                    }
                                });
                            }
                            
                            @Override
                            public void onFinalResult(String result) {
                                transcribedText += result + "\n";
                                runOnUiThread(() -> {
                                    if (!isShowingSummary) {
                                        resultTextView.setText(transcribedText);
                                    }
                                });
                            }
                            
                            @Override
                            public void onComplete(String fullResult) {
                                runOnUiThread(() -> {
                                    transcribedText = fullResult;
                                    if (!isShowingSummary) {
                                        resultTextView.setText(fullResult);
                                    }
                                    progressBar.setVisibility(View.GONE);
                                    progressTextView.setVisibility(View.GONE);
                                    transcribeButton.setEnabled(true);
                                    transcribeButton.setText(R.string.start_transcribe);
                                    Toast.makeText(MainActivity.this, 
                                            R.string.transcribe_complete, Toast.LENGTH_SHORT).show();
                                });
                            }
                            
                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    progressTextView.setVisibility(View.GONE);
                                    transcribeButton.setEnabled(true);
                                    transcribeButton.setText(R.string.start_transcribe);
                                    Toast.makeText(MainActivity.this, 
                                            "转写失败: " + error, Toast.LENGTH_LONG).show();
                                });
                            }
                            
                            @Override
                            public void onProgress(int progress) {
                                runOnUiThread(() -> {
                                    int totalProgress = 33 + (progress * 2 / 3);
                                    progressBar.setProgress(totalProgress);
                                    String stepText = getString(R.string.converting_audio);
                                    progressTextView.setText(getString(R.string.progress_detail, stepText, progress));
                                });
                            }
                        });
                
                speechRecognizer.recognizeFile(audioPath);
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    progressTextView.setVisibility(View.GONE);
                    transcribeButton.setEnabled(true);
                    transcribeButton.setText(R.string.start_transcribe);
                    Toast.makeText(MainActivity.this, 
                            "转写失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void copyText() {
        String text = isShowingSummary ? summaryText : transcribedText;
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可复制的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("text", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.copy_success, Toast.LENGTH_SHORT).show();
    }
    
    private void saveText() {
        String text = isShowingSummary ? summaryText : transcribedText;
        if (text.isEmpty()) {
            Toast.makeText(this, "没有可保存的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String fileName = (isShowingSummary ? "summary_" : "transcript_") + timeStamp + ".txt";
            
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, fileName);
            
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(text.getBytes("UTF-8"));
            }
            
            Toast.makeText(this, getString(R.string.save_success, file.getAbsolutePath()),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.save_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
    
    private void saveAudio() {
        if (extractedAudioPath == null || extractedAudioPath.isEmpty()) {
            Toast.makeText(this, R.string.no_audio_to_save, Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            String savedPath = audioExtractor.saveAudioToExternalStorage(this, extractedAudioPath);
            Toast.makeText(this, getString(R.string.audio_saved, savedPath), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.audio_save_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }
    
    private void shareText() {
        String text = isShowingSummary ? summaryText : transcribedText;
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.no_text_to_share, Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        shareIntent.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_title));
        
        Intent chooserIntent = Intent.createChooser(shareIntent, getString(R.string.share_title));
        startActivity(chooserIntent);
    }
    
    private void summarizeText() {
        if (transcribedText.isEmpty()) {
            Toast.makeText(this, R.string.no_text_to_summarize, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String apiUrl = preferencesManager.getApiUrl();
        String apiToken = preferencesManager.getApiToken();
        String modelName = preferencesManager.getModelName();
        
        if (apiUrl.isEmpty() || apiToken.isEmpty()) {
            Toast.makeText(this, R.string.please_configure_api, Toast.LENGTH_SHORT).show();
            openSettings();
            return;
        }
        
        summaryButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressTextView.setVisibility(View.VISIBLE);
        progressTextView.setText("正在连接AI服务...");
        
        llmClient = new LLMClient(apiUrl, apiToken, modelName);
        llmClient.summarize(transcribedText, new LLMClient.SummaryCallback() {
            @Override
            public void onSuccess(String summary) {
                runOnUiThread(() -> {
                    summaryText = summary;
                    if (isShowingSummary) {
                        resultTextView.setText(summary);
                    }
                    progressBar.setVisibility(View.GONE);
                    progressTextView.setVisibility(View.GONE);
                    summaryButton.setEnabled(true);
                    
                    tabLayout.selectTab(tabLayout.getTabAt(1));
                    
                    Toast.makeText(MainActivity.this, 
                            R.string.summary_complete, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    progressTextView.setVisibility(View.GONE);
                    summaryButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, 
                            "总结失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public void onProgress(String partial) {
                runOnUiThread(() -> {
                    progressTextView.setText("正在生成总结...");
                });
            }
        });
    }
    
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    
    private void updateResultText() {
        if (isShowingSummary) {
            resultTextView.setText(summaryText);
        } else {
            resultTextView.setText(transcribedText);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkModel();
    }
}

package com.uai.audio_parse;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 音频提取器
 * 负责从视频文件中提取音频，并转换为16kHz单声道PCM格式
 * 使用MediaCodec进行音频解码，支持各种视频格式
 * 
 * @author AI Generated
 * @version 1.0
 */
public class AudioExtractor {
    private static final String TAG = "AudioExtractor";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int TIMEOUT_US = 10000;
    
    private volatile boolean isCancelled = false;
    
    public interface ProgressCallback {
        void onProgress(int progress);
    }
    
    public String extractAudio(Context context, Uri videoUri, ProgressCallback callback) throws IOException {
        isCancelled = false;
        
        File tempFile = File.createTempFile("audio_", ".wav", context.getCacheDir());
        
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(context, videoUri, null);
        
        int audioTrackIndex = findAudioTrack(extractor);
        if (audioTrackIndex == -1) {
            throw new IOException("No audio track found");
        }
        
        extractor.selectTrack(audioTrackIndex);
        MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
        
        long duration = format.getLong(MediaFormat.KEY_DURATION);
        long totalSamples = (duration * SAMPLE_RATE) / 1000000;
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writeWavHeader(outputStream, 0, SAMPLE_RATE, CHANNELS, 16);
        
        MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, null, null, 0);
        codec.start();
        
        ByteBuffer[] inputBuffers = codec.getInputBuffers();
        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        
        boolean isEOS = false;
        long processedSamples = 0;
        
        while (!isCancelled) {
            if (!isEOS) {
                int inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();
                    }
                }
            }
            
            int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
            if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                
                byte[] chunk = new byte[bufferInfo.size];
                outputBuffer.get(chunk);
                
                byte[] pcmData = convertToPcm(chunk, format);
                outputStream.write(pcmData);
                
                processedSamples += pcmData.length / 2;
                if (callback != null && totalSamples > 0) {
                    int progress = (int) ((processedSamples * 100) / totalSamples);
                    callback.onProgress(Math.min(progress, 100));
                }
                
                codec.releaseOutputBuffer(outputBufferIndex, false);
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = codec.getOutputFormat();
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (isEOS) {
                    break;
                }
            }
        }
        
        codec.stop();
        codec.release();
        extractor.release();
        
        byte[] audioData = outputStream.toByteArray();
        updateWavHeader(audioData, audioData.length - 44);
        
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(audioData);
        fos.close();
        
        return tempFile.getAbsolutePath();
    }
    
    private int findAudioTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }
    
    private byte[] convertToPcm(byte[] audioData, MediaFormat format) {
        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) 
            ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) 
            : SAMPLE_RATE;
        int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) 
            ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) 
            : CHANNELS;
        
        if (sampleRate == SAMPLE_RATE && channelCount == CHANNELS) {
            return audioData;
        }
        
        byte[] monoData = audioData;
        if (channelCount > 1) {
            monoData = convertToMono(audioData, channelCount);
        }
        
        if (sampleRate != SAMPLE_RATE) {
            monoData = resample(monoData, sampleRate, SAMPLE_RATE);
        }
        
        return monoData;
    }
    
    private byte[] convertToMono(byte[] audioData, int channelCount) {
        int samplesPerFrame = audioData.length / (2 * channelCount);
        byte[] monoData = new byte[samplesPerFrame * 2];
        
        for (int i = 0; i < samplesPerFrame; i++) {
            int sum = 0;
            for (int ch = 0; ch < channelCount; ch++) {
                int sampleIndex = (i * channelCount + ch) * 2;
                short sample = (short) ((audioData[sampleIndex] & 0xFF) | (audioData[sampleIndex + 1] << 8));
                sum += sample;
            }
            short monoSample = (short) (sum / channelCount);
            monoData[i * 2] = (byte) (monoSample & 0xFF);
            monoData[i * 2 + 1] = (byte) ((monoSample >> 8) & 0xFF);
        }
        
        return monoData;
    }
    
    private byte[] resample(byte[] audioData, int fromRate, int toRate) {
        if (fromRate == toRate) {
            return audioData;
        }
        
        double ratio = (double) toRate / fromRate;
        int inputSamples = audioData.length / 2;
        int outputSamples = (int) (inputSamples * ratio);
        byte[] resampledData = new byte[outputSamples * 2];
        
        for (int i = 0; i < outputSamples; i++) {
            double srcIndex = i / ratio;
            int srcIndexInt = (int) srcIndex;
            
            if (srcIndexInt + 1 < inputSamples) {
                double fraction = srcIndex - srcIndexInt;
                short sample1 = (short) ((audioData[srcIndexInt * 2] & 0xFF) | (audioData[srcIndexInt * 2 + 1] << 8));
                short sample2 = (short) ((audioData[(srcIndexInt + 1) * 2] & 0xFF) | (audioData[(srcIndexInt + 1) * 2 + 1] << 8));
                short interpolated = (short) (sample1 + fraction * (sample2 - sample1));
                resampledData[i * 2] = (byte) (interpolated & 0xFF);
                resampledData[i * 2 + 1] = (byte) ((interpolated >> 8) & 0xFF);
            } else if (srcIndexInt < inputSamples) {
                short sample = (short) ((audioData[srcIndexInt * 2] & 0xFF) | (audioData[srcIndexInt * 2 + 1] << 8));
                resampledData[i * 2] = (byte) (sample & 0xFF);
                resampledData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        
        return resampledData;
    }
    
    private void writeWavHeader(ByteArrayOutputStream stream, long audioLength, int sampleRate, int channels, int bitsPerSample) {
        byte[] header = new byte[44];
        
        long totalDataLen = audioLength + 36;
        long byteRate = (long) sampleRate * channels * bitsPerSample / 8;
        
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * bitsPerSample / 8);
        header[33] = 0;
        header[34] = (byte) bitsPerSample;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (audioLength & 0xff);
        header[41] = (byte) ((audioLength >> 8) & 0xff);
        header[42] = (byte) ((audioLength >> 16) & 0xff);
        header[43] = (byte) ((audioLength >> 24) & 0xff);
        
        stream.write(header, 0, 44);
    }
    
    private void updateWavHeader(byte[] wavData, long audioLength) {
        long totalDataLen = audioLength + 36;
        wavData[4] = (byte) (totalDataLen & 0xff);
        wavData[5] = (byte) ((totalDataLen >> 8) & 0xff);
        wavData[6] = (byte) ((totalDataLen >> 16) & 0xff);
        wavData[7] = (byte) ((totalDataLen >> 24) & 0xff);
        wavData[40] = (byte) (audioLength & 0xff);
        wavData[41] = (byte) ((audioLength >> 8) & 0xff);
        wavData[42] = (byte) ((audioLength >> 16) & 0xff);
        wavData[43] = (byte) ((audioLength >> 24) & 0xff);
    }
    
    public void cancel() {
        isCancelled = true;
    }
    
    public String saveAudioToExternalStorage(Context context, String tempAudioPath) throws IOException {
        File tempFile = new File(tempAudioPath);
        if (!tempFile.exists()) {
            throw new IOException("临时音频文件不存在");
        }
        
        File audioDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "听见音频");
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String fileName = "audio_" + sdf.format(new Date()) + ".wav";
        File outputFile = new File(audioDir, fileName);
        
        FileInputStream fis = new FileInputStream(tempFile);
        FileOutputStream fos = new FileOutputStream(outputFile);
        
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        
        fos.close();
        fis.close();
        
        return outputFile.getAbsolutePath();
    }
    
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}

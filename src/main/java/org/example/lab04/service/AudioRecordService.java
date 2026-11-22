package org.example.lab04.service;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class AudioRecordService {

    private static TargetDataLine targetDataLine;
    private static volatile boolean isRecording = false;
    private static Clip activeClip;
    private static long recordStartTime = 0;

    public interface AudioRecordCallback {
        void onRecordingStarted();
        void onRecordingFinished(File audioFile, int durationSeconds);
        void onError(String message);
    }

    private static AudioFormat getAudioFormat() {
        float sampleRate = 16000.0f;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public static synchronized boolean startRecording(AudioRecordCallback callback) {
        if (isRecording) return false;

        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                if (callback != null) callback.onError("Microphone không được hệ thống hỗ trợ!");
                return false;
            }

            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(format);
            targetDataLine.start();

            isRecording = true;
            recordStartTime = System.currentTimeMillis();

            if (callback != null) callback.onRecordingStarted();

            Thread recordThread = new Thread(() -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];

                while (isRecording) {
                    int count = targetDataLine.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        out.write(buffer, 0, count);
                    }
                }

                long durationMs = System.currentTimeMillis() - recordStartTime;
                int durationSec = Math.max(1, (int) (durationMs / 1000));

                byte[] audioBytes = out.toByteArray();
                ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
                AudioInputStream audioInputStream = new AudioInputStream(bais, format, audioBytes.length / format.getFrameSize());

                try {
                    File dir = new File("downloads" + File.separator + "voice");
                    if (!dir.exists()) dir.mkdirs();

                    File wavFile = new File(dir, "voice_" + System.currentTimeMillis() + ".wav");
                    AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavFile);

                    if (callback != null) {
                        callback.onRecordingFinished(wavFile, durationSec);
                    }
                } catch (IOException e) {
                    if (callback != null) callback.onError("Lỗi lưu file ghi âm: " + e.getMessage());
                }
            });
            recordThread.setDaemon(true);
            recordThread.start();
            return true;

        } catch (Exception e) {
            isRecording = false;
            if (callback != null) callback.onError("Lỗi khởi động ghi âm: " + e.getMessage());
            return false;
        }
    }

    public static synchronized void stopRecording() {
        if (!isRecording) return;
        isRecording = false;
        if (targetDataLine != null) {
            targetDataLine.stop();
            targetDataLine.close();
        }
    }

    public static boolean isRecording() {
        return isRecording;
    }

    public static synchronized void playAudio(File audioFile, Runnable onFinished) {
        stopAudio();
        if (audioFile == null || !audioFile.exists()) return;

        new Thread(() -> {
            try {
                AudioInputStream ais = AudioSystem.getAudioInputStream(audioFile);
                activeClip = AudioSystem.getClip();
                activeClip.open(ais);
                activeClip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        activeClip.close();
                        if (onFinished != null) onFinished.run();
                    }
                });
                activeClip.start();
            } catch (Exception e) {
                System.err.println("Lỗi phát audio: " + e.getMessage());
                if (onFinished != null) onFinished.run();
            }
        }).start();
    }

    public static synchronized void stopAudio() {
        if (activeClip != null && activeClip.isOpen()) {
            activeClip.stop();
            activeClip.close();
            activeClip = null;
        }
    }
}

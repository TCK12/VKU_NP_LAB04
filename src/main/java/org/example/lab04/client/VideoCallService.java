package org.example.lab04.client;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoCallService {

    private DatagramSocket socket;
    private boolean running = false;
    private String targetIP;
    private int targetPort;

    // Threads
    private Thread receiveThread;
    private Thread videoCaptureThread;
    private Thread audioCaptureThread;

    // Hardware
    private Webcam webcam;
    private TargetDataLine microphone;
    private SourceDataLine speakers;

    // Controls
    private volatile boolean isMicMuted = false;
    private volatile boolean isCamOff = false;

    // Callbacks for UI
    private ImageView remoteVideoView;
    private ImageView localVideoView;

    public VideoCallService(ImageView remoteVideoView, ImageView localVideoView) {
        this.remoteVideoView = remoteVideoView;
        this.localVideoView = localVideoView;
    }

    public void start(int localUdpPort, String targetIP, int targetPort) throws Exception {
        this.targetIP = targetIP;
        this.targetPort = targetPort;
        this.socket = new DatagramSocket(localUdpPort);
        this.running = true;

        setupAudio();
        setupVideo();

        startReceiver();
        startAudioSender();
        startVideoSender();
    }

    private void setupAudio() throws Exception {
        AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
        microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
        microphone.open(format);
        microphone.start();

        DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, format);
        speakers = (SourceDataLine) AudioSystem.getLine(spkInfo);
        speakers.open(format);
        speakers.start();
    }

    private void setupVideo() {
        webcam = Webcam.getDefault();
        if (webcam != null) {
            webcam.setViewSize(WebcamResolution.QVGA.getSize()); // 320x240
            webcam.open();
        }
    }

    private void startReceiver() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[65000];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    byte type = buffer[0];

                    if (type == 0) { // Video
                        byte[] imgBytes = new byte[packet.getLength() - 1];
                        System.arraycopy(buffer, 1, imgBytes, 0, imgBytes.length);
                        Image img = new Image(new ByteArrayInputStream(imgBytes));
                        Platform.runLater(() -> remoteVideoView.setImage(img));
                    } else if (type == 1) { // Audio
                        speakers.write(buffer, 1, packet.getLength() - 1);
                    }
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                }
            }
        });
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private void startAudioSender() {
        audioCaptureThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (running) {
                int read = microphone.read(buffer, 0, buffer.length);
                if (read > 0 && !isMicMuted) {
                    byte[] data = new byte[read + 1];
                    data[0] = 1; // Audio type
                    System.arraycopy(buffer, 0, data, 1, read);
                    sendPacket(data);
                }
            }
        });
        audioCaptureThread.setDaemon(true);
        audioCaptureThread.start();
    }

    private void startVideoSender() {
        videoCaptureThread = new Thread(() -> {
            while (running) {
                try {
                    if (!isCamOff && webcam != null && webcam.isOpen()) {
                        BufferedImage image = webcam.getImage();
                        if (image != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            baos.write(0); // Video type
                            ImageIO.write(image, "JPG", baos);
                            byte[] data = baos.toByteArray();
                            if (data.length < 65000) {
                                sendPacket(data);
                            }
                            
                            // Update local view
                            Image localImg = new Image(new ByteArrayInputStream(baos.toByteArray()), 0, 0, false, false);
                            Platform.runLater(() -> localVideoView.setImage(localImg));
                        }
                    } else {
                        // Clear local view when cam is off
                        Platform.runLater(() -> localVideoView.setImage(null));
                    }
                    Thread.sleep(100); // ~10 fps
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        videoCaptureThread.setDaemon(true);
        videoCaptureThread.start();
    }

    private void sendPacket(byte[] data) {
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(targetIP), targetPort);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setMicMuted(boolean muted) { this.isMicMuted = muted; }
    public void setCamOff(boolean off) { this.isCamOff = off; }
    public boolean isMicMuted() { return isMicMuted; }
    public boolean isCamOff() { return isCamOff; }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        if (webcam != null && webcam.isOpen()) webcam.close();
        if (microphone != null) { microphone.stop(); microphone.close(); }
        if (speakers != null) { speakers.stop(); speakers.close(); }
    }
}

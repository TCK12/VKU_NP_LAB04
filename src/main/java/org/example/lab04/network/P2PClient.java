package org.example.lab04.network;

import java.io.*;
import java.net.Socket;

public class P2PClient {

    public static void sendMessage(String targetIP, int targetPort, String senderName, String message) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("MSG");
            dos.writeUTF(senderName);
            dos.writeUTF(message);
        }
    }

    public static void sendImage(String targetIP, int targetPort, String senderName, File imageFile) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(imageFile)) {
            dos.writeUTF("IMG");
            dos.writeUTF(senderName);
            dos.writeUTF(imageFile.getName());
            dos.writeLong(imageFile.length());

            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
        }
    }

    public static void sendVoice(String targetIP, int targetPort, String senderName, int durationSec, File voiceFile) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(voiceFile)) {
            dos.writeUTF("VOICE");
            dos.writeUTF(senderName);
            dos.writeInt(durationSec);
            dos.writeLong(voiceFile.length());

            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
        }
    }

    public static void sendVideo(String targetIP, int targetPort, String senderName, File videoFile) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(videoFile)) {
            dos.writeUTF("VIDEO");
            dos.writeUTF(senderName);
            dos.writeUTF(videoFile.getName());
            dos.writeLong(videoFile.length());

            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
        }
    }

    // A gửi thông báo file cho B
    public static void sendFileOffer(String targetIP, int targetPort, String senderName, File file) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("FILE_OFFER");
            dos.writeUTF(senderName);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
        }
    }

    // B yêu cầu tải file từ A
    public static void requestFileDownload(String targetIP, int targetPort, String fileName) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("DOWNLOAD_REQ");
            dos.writeUTF(fileName);
        }
    }

    // A gửi dữ liệu file cho B khi B yêu cầu
    public static void sendFileData(String targetIP, int targetPort, File file) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {
            dos.writeUTF("FILE_DATA");
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) > 0) {
                dos.write(buffer, 0, bytesRead);
            }
        }
    }
}

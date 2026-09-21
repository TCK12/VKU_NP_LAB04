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

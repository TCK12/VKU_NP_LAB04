package org.example.lab04.network;

import java.io.*;
import java.net.Socket;

public class P2PClient {

    /** Gửi tin nhắn văn bản P2P */
    public static void sendMessage(String targetIP, int targetPort, String senderName, String message) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("MSG");
            dos.writeUTF(senderName);
            dos.writeUTF(message);
        }
    }

    /** A gửi thông báo file cho B (B sẽ chủ động tải) */
    public static void sendFileOffer(String targetIP, int targetPort, String senderName, File file) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("FILE_OFFER");
            dos.writeUTF(senderName);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
        }
    }

    /**
     * A gửi ảnh cho B kèm thumbnail (tiết kiệm băng thông):
     * B nhận thumbnail để xem trước, ảnh gốc chỉ tải khi B bấm nút.
     */
    public static void sendImageOffer(String targetIP, int targetPort, String senderName,
                                      File file, byte[] thumbnail) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("IMG_OFFER");
            dos.writeUTF(senderName);
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            dos.writeInt(thumbnail.length);
            dos.write(thumbnail);
        }
    }

    /** B yêu cầu tải file / ảnh gốc từ A */
    public static void requestFileDownload(String targetIP, int targetPort, String fileName) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("DOWNLOAD_REQ");
            dos.writeUTF(fileName);
        }
    }
}

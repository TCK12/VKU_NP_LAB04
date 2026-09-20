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

    public static void sendFile(String targetIP, int targetPort, String senderName, File file) throws IOException {
        try (Socket socket = new Socket(targetIP, targetPort);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            dos.writeUTF("FILE");
            dos.writeUTF(senderName);
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
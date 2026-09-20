package org.example.lab04.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class P2PListener extends Thread {
    private final int port;
    private final INetworkCallback callback;

    public P2PListener(int port, INetworkCallback callback) {
        this.port = port;
        this.callback = callback;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleIncomingData(socket)).start();
            }
        } catch (IOException e) {
            callback.onError("Lỗi Port P2P: " + port + " đã bị chiếm dụng!");
        }
    }

    private void handleIncomingData(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            String type = dis.readUTF();

            if ("MSG".equals(type)) {
                String sender = dis.readUTF();
                String text = dis.readUTF();
                callback.onMessageReceived(sender, text);

            } else if ("FILE".equals(type)) {
                String sender = dis.readUTF();
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                File dir = new File("downloads");
                if (!dir.exists()) dir.mkdirs();

                File saveFile = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                    byte[] buffer = new byte[4096];
                    long totalRead = 0;
                    int read;
                    while (totalRead < fileSize && (read = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) > 0) {
                        fos.write(buffer, 0, read);
                        totalRead += read;
                    }
                }
                callback.onFileReceived(sender, fileName);
            }
        } catch (IOException e) {
            callback.onError("Lỗi ngắt kết nối P2P!");
        }
    }
}
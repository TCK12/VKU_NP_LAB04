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
        setDaemon(true);
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
                String text   = dis.readUTF();
                callback.onMessageReceived(sender, text);

            } else if ("FILE_OFFER".equals(type)) {
                String sender   = dis.readUTF();
                String fileName = dis.readUTF();
                long   fileSize = dis.readLong();
                callback.onFileOfferReceived(sender, fileName, fileSize);

            } else if ("IMG_OFFER".equals(type)) {
                // Giao thức ảnh tiết kiệm băng thông:
                // Người gửi chỉ truyền thumbnail nhỏ. Ảnh gốc chỉ tải khi người nhận bấm nút.
                String sender    = dis.readUTF();
                String fileName  = dis.readUTF();
                long   fileSize  = dis.readLong();
                int    thumbLen  = dis.readInt();
                byte[] thumbnail = dis.readNBytes(thumbLen);
                callback.onImageOfferReceived(sender, fileName, fileSize, thumbnail);

            } else if ("DOWNLOAD_REQ".equals(type)) {
                // Người nhận yêu cầu tải file / ảnh gốc
                String fileName = dis.readUTF();
                String filePath = org.example.lab04.client.ChatClientApp.offeredFiles.get(fileName);
                File   file     = null;

                if (filePath != null) {
                    file = new File(filePath);
                } else {
                    file = new File(System.getProperty("user.home"), "Downloads/P2PChat/" + fileName);
                    if (!file.exists()) file = new File(fileName);
                }

                try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
                    if (file != null && file.exists() && file.isFile()) {
                        dos.writeUTF("FILE_DATA");
                        dos.writeUTF(file.getName());
                        dos.writeLong(file.length());
                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) > 0) {
                                dos.write(buffer, 0, bytesRead);
                            }
                        }
                    } else {
                        dos.writeUTF("FILE_ERROR");
                        dos.writeUTF("Không tìm thấy file: " + fileName);
                    }
                    dos.flush();
                }
            }
        } catch (IOException e) {
            // Bỏ qua lỗi kết nối đơn lẻ
        }
    }
}
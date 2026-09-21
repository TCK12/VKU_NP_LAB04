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

            } else if ("FILE_OFFER".equals(type)) {
                String sender = dis.readUTF();
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                callback.onFileOfferReceived(sender, fileName, fileSize);

            } else if ("DOWNLOAD_REQ".equals(type)) {
                String fileName = dis.readUTF();
                System.out.println("[P2PListener] Nhận yêu cầu tải file: " + fileName);

                String filePath = org.example.lab04.client.ChatClientApp.offeredFiles.get(fileName);
                File file = null;

                if (filePath != null) {
                    file = new File(filePath);
                    System.out.println("[P2PListener] Đã tìm thấy đường dẫn gốc trong cache: " + filePath);
                } else {
                    System.out.println("[P2PListener] Không có trong cache, tìm ở thư mục mặc định...");
                    file = new File("C:\\downloads", fileName);
                    if (!file.exists()) {
                        file = new File(fileName);
                    }
                }

                try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
                    if (file != null && file.exists() && file.isFile()) {
                        System.out.println("[P2PListener] File hợp lệ. Bắt đầu truyền dữ liệu...");
                        dos.writeUTF("FILE_DATA");
                        dos.writeUTF(file.getName());
                        dos.writeLong(file.length());
                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fis.read(buffer)) > 0) {
                                dos.write(buffer, 0, bytesRead);
                            }
                        }
                    } else {
                        System.out.println("[P2PListener] Báo lỗi FILE_ERROR do không tìm thấy file.");
                        dos.writeUTF("FILE_ERROR");
                        dos.writeUTF("Không tìm thấy file: " + fileName);
                    }
                    dos.flush();
                }
            } else if ("FILE_DATA".equals(type)) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                // Cập nhật thư mục tải vào ổ C
                File dir = new File("C:\\downloads");
                if (!dir.exists()) dir.mkdirs();
                File saveFile = new File(dir, fileName);

                long totalRead = 0;
                long startTime = System.currentTimeMillis();
                byte[] buffer = new byte[4096];
                int read;

                try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                    while (totalRead < fileSize && (read = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) > 0) {
                        fos.write(buffer, 0, read);
                        totalRead += read;

                        long now = System.currentTimeMillis();
                        long elapsedMs = now - startTime;
                        if (elapsedMs > 0) {
                            double speedKBps = (totalRead / 1024.0) / (elapsedMs / 1000.0);
                            int etaSeconds = (int) (((fileSize - totalRead) / 1024.0) / Math.max(speedKBps, 0.1));
                            if (etaSeconds < 0) etaSeconds = 0;
                            callback.onFileDownloadProgress(fileName, totalRead, fileSize, speedKBps, etaSeconds);
                        }
                    }
                }
                callback.onFileSaved(fileName, saveFile.getAbsolutePath());
            }
        } catch (IOException e) {
            callback.onError("Lỗi ngắt kết nối P2P!");
        }
    }
}
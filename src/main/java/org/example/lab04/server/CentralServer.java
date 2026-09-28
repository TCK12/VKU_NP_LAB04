package org.example.lab04.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CentralServer {
    public static final int DEFAULT_PORT = 9999;
    public static final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    /** Listener để UI (ServerApp) nhận sự kiện từ server. Null nếu chạy dạng console. */
    public static volatile IServerEventListener eventListener = null;

    private static volatile ServerSocket serverSocket;
    private static volatile boolean running = false;

    /**
     * Chạy server theo kiểu blocking (dùng cho console / cũ).
     * Giờ chỉ giữ lại để tương thích — UI nên dùng startAsync().
     */
    public static void main(String[] args) {
        System.out.println("=== CENTRAL DIRECTORY SERVER ĐÃ MỞ (PORT " + DEFAULT_PORT + ") ===");
        try (ServerSocket ss = new ServerSocket(DEFAULT_PORT)) {
            serverSocket = ss;
            while (true) {
                Socket socket = ss.accept();
                ClientHandler handler = new ClientHandler(socket);
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Khởi động server không blocking (dùng cho UI).
     */
    public static void startAsync(int port) throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(port);
        running = true;

        Thread serverThread = new Thread(() -> {
            if (eventListener != null) eventListener.onServerStarted(port);
            System.out.println("=== SERVER KHỞI ĐỘNG TRÊN PORT " + port + " ===");
            try {
                while (running && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    handler.setDaemon(true);
                    handler.start();
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[Server lỗi]: " + e.getMessage());
                    if (eventListener != null) eventListener.onLog("❌ Lỗi server: " + e.getMessage());
                }
            } finally {
                running = false;
                if (eventListener != null) eventListener.onServerStopped();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Dừng server.
     */
    public static void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        // Đóng tất cả client
        for (ClientHandler ch : activeClients) {
            ch.interrupt();
        }
        activeClients.clear();
    }

    public static boolean isRunning() {
        return running;
    }

    public static void broadcastUserList() {
        List<String> userList = new ArrayList<>();
        for (ClientHandler client : activeClients) {
            if (client.getUserInfo() != null) {
                userList.add(client.getUserInfo());
            }
        }

        if (eventListener != null) {
            eventListener.onUserListUpdated(new ArrayList<>(userList));
        }

        for (ClientHandler client : activeClients) {
            client.sendUserList(userList);
        }
    }
}
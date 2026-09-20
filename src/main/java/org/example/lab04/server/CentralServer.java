package org.example.lab04.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CentralServer {
    private static final int PORT = 9999;
    public static final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println("=== CENTRAL DIRECTORY SERVER ĐÃ MỞ (PORT " + PORT + ") ===");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void broadcastUserList() {
        List<String> userList = new ArrayList<>();
        for (ClientHandler client : activeClients) {
            if (client.getUserInfo() != null) {
                userList.add(client.getUserInfo());
            }
        }

        for (ClientHandler client : activeClients) {
            client.sendUserList(userList);
        }
    }
}
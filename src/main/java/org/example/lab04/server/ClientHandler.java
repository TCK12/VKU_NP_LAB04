package org.example.lab04.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class ClientHandler extends Thread {
    private final Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private String userInfo;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            String command = dis.readUTF();
            if ("REGISTER".equals(command)) {
                username = dis.readUTF();
                int p2pPort = dis.readInt();
                String clientIP = socket.getInetAddress().getHostAddress();

                this.userInfo = username + " - " + clientIP + ":" + p2pPort;
                CentralServer.activeClients.add(this);

                System.out.println("[🟢 VỪA ONLINE]: " + userInfo);
                if (CentralServer.eventListener != null) {
                    CentralServer.eventListener.onClientConnected(userInfo);
                }
                CentralServer.broadcastUserList();

                // Giữ kết nối để nhận log sự kiện P2P từ client
                while (true) {
                    String logType = dis.readUTF();

                    if ("LOG_MSG".equals(logType)) {
                        // Client gửi log: tôi vừa gửi tin nhắn cho toUser
                        String toUser = dis.readUTF();
                        String message = dis.readUTF();
                        System.out.println("[📨 LOG]: " + username + " -> " + toUser + ": " + message);
                        if (CentralServer.eventListener != null) {
                            CentralServer.eventListener.onMessageSent(username, toUser, message);
                        }

                    } else if ("LOG_FILE".equals(logType)) {
                        // Client gửi log: tôi vừa gửi file cho toUser
                        String toUser = dis.readUTF();
                        String fileName = dis.readUTF();
                        long fileSize = dis.readLong();
                        System.out.println("[📁 LOG]: " + username + " -> " + toUser + ": " + fileName + " (" + fileSize + " bytes)");
                        if (CentralServer.eventListener != null) {
                            CentralServer.eventListener.onFileSent(username, toUser, fileName, fileSize);
                        }
                    }
                    // Bất kỳ command không rõ thì bỏ qua (keepalive cũ)
                }
            }
        } catch (IOException e) {
            // Client ngắt kết nối
        } finally {
            cleanup();
        }
    }

    public void sendUserList(List<String> userList) {
        try {
            dos.writeUTF("UPDATE_USER_LIST");
            dos.writeInt(userList.size());
            for (String user : userList) {
                dos.writeUTF(user);
            }
            dos.flush();
        } catch (IOException e) {
            // Lỗi gửi tới client này
        }
    }

    private void cleanup() {
        CentralServer.activeClients.remove(this);
        if (userInfo != null) {
            System.out.println("[🔴 ĐÃ OFFLINE]: " + userInfo);
            if (CentralServer.eventListener != null) {
                CentralServer.eventListener.onClientDisconnected(userInfo);
            }
            CentralServer.broadcastUserList();
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
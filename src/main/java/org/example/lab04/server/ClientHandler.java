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

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUserInfo() {
        return userInfo;
    }

    @Override
    public void run() {
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            String command = dis.readUTF();
            if ("REGISTER".equals(command)) {
                String username = dis.readUTF();
                int p2pPort = dis.readInt();
                String clientIP = socket.getInetAddress().getHostAddress();

                this.userInfo = username + " - " + clientIP + ":" + p2pPort;
                CentralServer.activeClients.add(this);

                System.out.println("[🟢 VỪA ONLINE]: " + userInfo);
                CentralServer.broadcastUserList();

                // Giữ kết nối ngầm để theo dõi trạng thái Online/Offline
                while (true) {
                    dis.readUTF();
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
            CentralServer.broadcastUserList();
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
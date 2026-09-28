package org.example.lab04.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class ClientHandler extends Thread {
    private final Socket socket;
    private DataInputStream  dis;
    private DataOutputStream dos;
    private String userInfo;
    private String username;

    public ClientHandler(Socket socket) { this.socket = socket; }

    public String getUserInfo() { return userInfo; }
    public String getUsername()  { return username; }

    @Override
    public void run() {
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            String command = dis.readUTF();
            if ("REGISTER".equals(command)) {
                username = dis.readUTF();
                int    p2pPort  = dis.readInt();
                String clientIP = socket.getInetAddress().getHostAddress();

                this.userInfo = username + " - " + clientIP + ":" + p2pPort;
                CentralServer.activeClients.add(this);

                System.out.println("[🟢 ONLINE]: " + userInfo);
                if (CentralServer.eventListener != null)
                    CentralServer.eventListener.onClientConnected(userInfo);
                CentralServer.broadcastUserList();

                // Giữ kết nối để nhận log sự kiện P2P từ client
                while (true) {
                    String logType = dis.readUTF();

                    if ("LOG_MSG".equals(logType)) {
                        String toUser  = dis.readUTF();
                        String message = dis.readUTF();
                        if (CentralServer.eventListener != null)
                            CentralServer.eventListener.onMessageSent(username, toUser, message);

                    } else if ("LOG_FILE".equals(logType)) {
                        String toUser   = dis.readUTF();
                        String fileName = dis.readUTF();
                        long   fileSize = dis.readLong();
                        if (CentralServer.eventListener != null)
                            CentralServer.eventListener.onFileSent(username, toUser, fileName, fileSize);

                    } else if ("LOG_IMG".equals(logType)) {
                        String toUser   = dis.readUTF();
                        String fileName = dis.readUTF();
                        long   fileSize = dis.readLong();
                        if (CentralServer.eventListener != null)
                            CentralServer.eventListener.onImageSent(username, toUser, fileName, fileSize);
                    
                    } else if ("CALL_REQ".equals(logType)) {
                        String toUser = dis.readUTF();
                        int myUdpPort = dis.readInt();
                        ClientHandler target = getClientHandler(toUser);
                        if (target != null) {
                            String myIP = socket.getInetAddress().getHostAddress();
                            target.sendCallReq(username, myIP, myUdpPort);
                        }
                    } else if ("CALL_RES".equals(logType)) {
                        String toUser = dis.readUTF();
                        boolean isAccept = dis.readBoolean();
                        int myUdpPort = 0;
                        if (isAccept) myUdpPort = dis.readInt();
                        
                        ClientHandler target = getClientHandler(toUser);
                        if (target != null) {
                            String myIP = socket.getInetAddress().getHostAddress();
                            target.sendCallRes(username, isAccept, myIP, myUdpPort);
                        }
                    } else if ("END_CALL".equals(logType)) {
                        String toUser = dis.readUTF();
                        ClientHandler target = getClientHandler(toUser);
                        if (target != null) {
                            target.sendEndCall(username);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Client ngắt kết nối
        } finally {
            cleanup();
        }
    }

    private ClientHandler getClientHandler(String targetUser) {
        for (ClientHandler client : CentralServer.activeClients) {
            if (targetUser.equals(client.getUsername())) {
                return client;
            }
        }
        return null;
    }

    public synchronized void sendCallReq(String fromUser, String fromIP, int fromUdpPort) {
        try {
            dos.writeUTF("CALL_REQ");
            dos.writeUTF(fromUser);
            dos.writeUTF(fromIP);
            dos.writeInt(fromUdpPort);
            dos.flush();
        } catch (IOException ignored) {}
    }

    public synchronized void sendCallRes(String fromUser, boolean isAccept, String fromIP, int fromUdpPort) {
        try {
            dos.writeUTF("CALL_RES");
            dos.writeUTF(fromUser);
            dos.writeBoolean(isAccept);
            if (isAccept) dos.writeInt(fromUdpPort);
            dos.flush();
        } catch (IOException ignored) {}
    }

    public synchronized void sendEndCall(String fromUser) {
        try {
            dos.writeUTF("END_CALL");
            dos.writeUTF(fromUser);
            dos.flush();
        } catch (IOException ignored) {}
    }

    public synchronized void sendUserList(List<String> userList) {
        try {
            dos.writeUTF("UPDATE_USER_LIST");
            dos.writeInt(userList.size());
            for (String user : userList) dos.writeUTF(user);
            dos.flush();
        } catch (IOException ignored) {}
    }

    private void cleanup() {
        CentralServer.activeClients.remove(this);
        if (userInfo != null) {
            System.out.println("[🔴 OFFLINE]: " + userInfo);
            if (CentralServer.eventListener != null)
                CentralServer.eventListener.onClientDisconnected(userInfo);
            CentralServer.broadcastUserList();
        }
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }
}
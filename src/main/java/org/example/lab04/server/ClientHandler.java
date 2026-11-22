package org.example.lab04.server;

import org.example.lab04.model.ChatMessageModel;
import org.example.lab04.model.UserLocation;
import org.example.lab04.model.UserRole;
import org.example.lab04.service.GeoLocationService;
import org.example.lab04.service.MongoDbService;

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
    private String userLocation;
    private UserRole userRole = UserRole.USER;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public String getUsername() {
        return username;
    }

    public String getUserLocation() {
        return userLocation;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public void setUserRole(UserRole role) {
        this.userRole = role;
        updateUserInfoString();
    }

    private void updateUserInfoString() {
        if (username != null && socket != null) {
            String clientIP = socket.getInetAddress().getHostAddress();
            int p2pPort = 0;
            if (userInfo != null && userInfo.contains(":")) {
                try {
                    String[] parts = userInfo.split(" - ");
                    p2pPort = Integer.parseInt(parts[1].split(":")[1]);
                } catch (Exception ignored) {}
            }
            this.userInfo = username + " - " + clientIP + ":" + p2pPort + " - " + userLocation + " - " + userRole.name();
        }
    }

    @Override
    public void run() {
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            String command = dis.readUTF();
            if ("REGISTER".equals(command) || "REGISTER_V2".equals(command)) {
                username = dis.readUTF();
                int p2pPort = dis.readInt();
                String clientIP = socket.getInetAddress().getHostAddress();

                if ("REGISTER_V2".equals(command)) {
                    userLocation = dis.readUTF();
                } else {
                    UserLocation loc = GeoLocationService.resolveLocationForIp(clientIP);
                    userLocation = loc.getDisplayLocation();
                }

                if (userLocation == null || userLocation.trim().isEmpty()) {
                    userLocation = "📍 Unknown location";
                }

                // Lấy quyền từ CSDL MongoDB hoặc gán ADMIN nếu là user đầu tiên / "admin"
                userRole = MongoDbService.getUserRole(username);

                // Lưu vào CSDL MongoDB (IP thay đổi được cập nhật tự động theo username)
                MongoDbService.saveUser(username, userRole, userLocation, clientIP, p2pPort);

                this.userInfo = username + " - " + clientIP + ":" + p2pPort + " - " + userLocation + " - " + userRole.name();
                CentralServer.activeClients.add(this);

                System.out.println("[🟢 VỪA ONLINE]: " + userInfo);
                if (CentralServer.eventListener != null) {
                    CentralServer.eventListener.onClientConnected(userInfo);
                }
                CentralServer.broadcastUserList();

                // Giữ kết nối để nhận log sự kiện và lệnh phân quyền từ client
                while (true) {
                    String logType = dis.readUTF();

                    if ("LOG_MSG".equals(logType)) {
                        String toUser = dis.readUTF();
                        String message = dis.readUTF();
                        System.out.println("[📨 LOG]: " + username + " -> " + toUser + ": " + message);

                        // Lưu vào MongoDB
                        ChatMessageModel chatMsg = new ChatMessageModel(username, toUser, ChatMessageModel.MessageType.TEXT, message);
                        MongoDbService.saveMessage(chatMsg);

                        if (CentralServer.eventListener != null) {
                            CentralServer.eventListener.onMessageSent(username, toUser, message);
                        }

                    } else if ("LOG_FILE".equals(logType)) {
                        String toUser = dis.readUTF();
                        String fileName = dis.readUTF();
                        long fileSize = dis.readLong();
                        System.out.println("[📁 LOG]: " + username + " -> " + toUser + ": " + fileName + " (" + fileSize + " bytes)");

                        ChatMessageModel fileMsg = new ChatMessageModel(username, toUser, ChatMessageModel.MessageType.FILE, fileName);
                        fileMsg.setFileName(fileName);
                        fileMsg.setFileSize(fileSize);
                        MongoDbService.saveMessage(fileMsg);

                        if (CentralServer.eventListener != null) {
                            CentralServer.eventListener.onFileSent(username, toUser, fileName, fileSize);
                        }

                    } else if ("LOG_MEDIA".equals(logType)) {
                        String toUser = dis.readUTF();
                        String mediaTypeStr = dis.readUTF(); // IMAGE, VIDEO, VOICE
                        String mediaName = dis.readUTF();
                        long mediaSize = dis.readLong();

                        ChatMessageModel.MessageType mType = ChatMessageModel.MessageType.valueOf(mediaTypeStr);
                        ChatMessageModel mediaMsg = new ChatMessageModel(username, toUser, mType, mediaName);
                        mediaMsg.setFileName(mediaName);
                        mediaMsg.setFileSize(mediaSize);
                        MongoDbService.saveMessage(mediaMsg);

                        if (CentralServer.eventListener != null) {
                            CentralServer.eventListener.onLog("🎨 [" + mediaTypeStr + "] " + username + " -> " + toUser + ": " + mediaName);
                        }

                    } else if ("ADMIN_KICK".equals(logType)) {
                        String targetToKick = dis.readUTF();
                        if (userRole == UserRole.ADMIN || userRole == UserRole.MODERATOR) {
                            for (ClientHandler ch : CentralServer.activeClients) {
                                if (ch.getUsername() != null && ch.getUsername().equalsIgnoreCase(targetToKick)) {
                                    ch.forceDisconnect();
                                    break;
                                }
                            }
                        }

                    } else if ("ADMIN_SET_ROLE".equals(logType)) {
                        String targetUser = dis.readUTF();
                        String newRoleStr = dis.readUTF();
                        if (userRole == UserRole.ADMIN) {
                            UserRole newRole = UserRole.fromString(newRoleStr);
                            MongoDbService.updateUserRole(targetUser, newRole);
                            for (ClientHandler ch : CentralServer.activeClients) {
                                if (ch.getUsername() != null && ch.getUsername().equalsIgnoreCase(targetUser)) {
                                    ch.setUserRole(newRole);
                                    break;
                                }
                            }
                            CentralServer.broadcastUserList();
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

    public void forceDisconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}

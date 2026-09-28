package org.example.lab04.server;

import java.util.List;

public interface IServerEventListener {
    void onServerStarted(int port);
    void onServerStopped();
    void onClientConnected(String userInfo);
    void onClientDisconnected(String userInfo);
    void onMessageSent(String fromUser, String toUser, String message);
    void onFileSent(String fromUser, String toUser, String fileName, long fileSize);
    void onImageSent(String fromUser, String toUser, String fileName, long fileSize);
    void onUserListUpdated(List<String> activeUsers);
    void onLog(String message);
}

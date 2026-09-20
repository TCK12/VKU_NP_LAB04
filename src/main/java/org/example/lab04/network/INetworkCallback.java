package org.example.lab04.network;

public interface INetworkCallback {
    void onMessageReceived(String sender, String message);
    void onFileReceived(String sender, String fileName);
    void onError(String errorMessage);
}
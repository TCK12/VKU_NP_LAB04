package org.example.lab04.network;

public interface INetworkCallback {
    void onMessageReceived(String sender, String message);
    void onFileOfferReceived(String sender, String fileName, long fileSize);
    void onFileDownloadProgress(String fileName, long downloaded, long total, double speedKBps, int etaSeconds);
    void onFileSaved(String fileName, String path);
    void onError(String errorMessage);
}

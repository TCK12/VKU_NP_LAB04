package org.example.lab04.network;

public interface INetworkCallback {
    void onMessageReceived(String sender, String message);
    void onFileOfferReceived(String sender, String fileName, long fileSize);

    /**
     * Nhận ảnh từ người gửi (tiết kiệm băng thông):
     * thumbnail là ảnh JPEG thu nhỏ để xem trước — ảnh gốc chỉ tải khi user bấm.
     */
    void onImageOfferReceived(String sender, String fileName, long fileSize, byte[] thumbnail);

    void onFileDownloadProgress(String fileName, long downloaded, long total, double speedKBps, int etaSeconds);
    void onFileSaved(String fileName, String path);
    void onError(String errorMessage);
}

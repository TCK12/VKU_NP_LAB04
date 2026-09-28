package org.example.lab04.server;

/**
 * Interface để ServerApp (UI) lắng nghe các sự kiện từ CentralServer.
 */
public interface IServerEventListener {

    /** Server đã khởi động thành công trên port. */
    void onServerStarted(int port);

    /** Server đã dừng. */
    void onServerStopped();

    /** Một client vừa đăng nhập. */
    void onClientConnected(String userInfo);

    /** Một client vừa offline. */
    void onClientDisconnected(String userInfo);

    /** Client A vừa gửi tin nhắn cho B qua P2P (log từ A). */
    void onMessageSent(String fromUser, String toUser, String message);

    /** Client A vừa gửi file cho B qua P2P (log từ A). */
    void onFileSent(String fromUser, String toUser, String fileName, long fileSize);

    /** Danh sách user online cập nhật. */
    void onUserListUpdated(java.util.List<String> activeUsers);

    /** Ghi log lỗi hoặc thông tin server. */
    void onLog(String message);
}

package org.example.lab04.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.example.lab04.model.ChatMessageModel;
import org.example.lab04.model.UserRole;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MongoDbService {

    private static final String DEFAULT_MONGO_URL = "mongodb+srv://tranvangiabao96206_db_user:???@giabao.ewq3tu9.mongodb.net/";
    private static final String DATABASE_NAME = "chatapp_db";
    private static final String LOCAL_HISTORY_FILE = "local_chat_history.json";
    private static final String LOCAL_USERS_FILE = "local_users.json";

    private static MongoClient mongoClient;
    private static MongoDatabase database;
    private static MongoCollection<Document> usersCol;
    private static MongoCollection<Document> messagesCol;
    private static MongoCollection<Document> conversationsCol;

    private static volatile boolean isConnected = false;
    private static String connectionStatusMessage = "Chưa kết nối";

    // Bộ nhớ đệm cục bộ dự phòng nếu MongoDB chưa có mật khẩu hoặc offline
    private static final Map<String, List<ChatMessageModel>> localMessageHistory = new ConcurrentHashMap<>();
    private static final Map<String, UserRole> localUserRoles = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    static {
        loadLocalFallback();
    }

    public static synchronized void init() {
        String mongoUrl = System.getenv("MONGO_URL");
        if (mongoUrl == null || mongoUrl.trim().isEmpty()) {
            mongoUrl = loadMongoUrlFromConfig();
        }
        if (mongoUrl == null || mongoUrl.trim().isEmpty()) {
            mongoUrl = DEFAULT_MONGO_URL;
        }

        if (mongoUrl.contains("???")) {
            isConnected = false;
            connectionStatusMessage = "⚠️ Cần điền mật khẩu trong MONGO_URL (Đang dùng lưu trữ cục bộ)";
            System.out.println("[MongoDB] URL chứa '???' (chưa điền mật khẩu). Chuyển sang chế độ lưu trữ cục bộ.");
            return;
        }

        try {
            ConnectionString connString = new ConnectionString(mongoUrl);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connString)
                    .applyToSocketSettings(builder -> builder.connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS))
                    .applyToClusterSettings(builder -> builder.serverSelectionTimeout(5, TimeUnit.SECONDS))
                    .build();

            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(DATABASE_NAME);

            // Kiểm tra kết nối nhanh bằng lệnh ping
            database.runCommand(new Document("ping", 1));

            // Khởi tạo các Collections
            usersCol = database.getCollection("users");
            messagesCol = database.getCollection("messages");
            conversationsCol = database.getCollection("conversations");

            // Tạo các chỉ mục (Indexes) để tăng tốc độ truy vấn
            try {
                usersCol.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
                messagesCol.createIndex(Indexes.compoundIndex(
                        Indexes.ascending("sender"),
                        Indexes.ascending("receiver"),
                        Indexes.ascending("timestamp")
                ));
                conversationsCol.createIndex(Indexes.ascending("participants"));
            } catch (Exception ignored) {}

            isConnected = true;
            connectionStatusMessage = "🟢 Đã kết nối MongoDB Cloud (" + DATABASE_NAME + ")";
            System.out.println("[MongoDB] " + connectionStatusMessage);

        } catch (Exception e) {
            isConnected = false;
            connectionStatusMessage = "❌ Lỗi kết nối MongoDB: " + e.getMessage() + " (Đã bật lưu trữ cục bộ)";
            System.err.println("[MongoDB] " + connectionStatusMessage);
        }
    }

    public static boolean isConnected() {
        return isConnected;
    }

    public static String getConnectionStatusMessage() {
        return connectionStatusMessage;
    }

    /**
     * Lưu hoặc cập nhật thông tin người dùng vào MongoDB.
     * Giải quyết bài toán IP mạng thay đổi: IP được lưu dưới dạng currentIP, còn username là khóa định danh vĩnh viễn.
     */
    public static void saveUser(String username, UserRole role, String location, String ip, int port) {
        if (username == null || username.trim().isEmpty()) return;

        localUserRoles.put(username, role != null ? role : UserRole.USER);
        saveLocalFallback();

        if (!isConnected || usersCol == null) return;

        new Thread(() -> {
            try {
                Document existing = usersCol.find(Filters.eq("username", username)).first();
                if (existing == null) {
                    Document newUser = new Document("username", username)
                            .append("role", (role != null ? role : UserRole.USER).name())
                            .append("location", location != null ? location : "Unknown")
                            .append("currentIP", ip != null ? ip : "")
                            .append("currentPort", port)
                            .append("status", "ONLINE")
                            .append("createdAt", Instant.now().toString())
                            .append("lastSeen", Instant.now().toString());
                    usersCol.insertOne(newUser);
                } else {
                    usersCol.updateOne(Filters.eq("username", username),
                            Updates.combine(
                                    Updates.set("location", location != null ? location : "Unknown"),
                                    Updates.set("currentIP", ip != null ? ip : ""),
                                    Updates.set("currentPort", port),
                                    Updates.set("status", "ONLINE"),
                                    Updates.set("lastSeen", Instant.now().toString())
                            ));
                }
            } catch (Exception e) {
                System.err.println("[MongoDB] Lỗi lưu user: " + e.getMessage());
            }
        }).start();
    }

    public static UserRole getUserRole(String username) {
        if (username == null) return UserRole.USER;
        if (localUserRoles.containsKey(username)) {
            return localUserRoles.get(username);
        }

        if (isConnected && usersCol != null) {
            try {
                Document doc = usersCol.find(Filters.eq("username", username)).first();
                if (doc != null && doc.containsKey("role")) {
                    UserRole role = UserRole.fromString(doc.getString("role"));
                    localUserRoles.put(username, role);
                    return role;
                }
            } catch (Exception ignored) {}
        }

        // Người dùng "admin" hoặc "Server" tự động cấp quyền ADMIN
        if (username.equalsIgnoreCase("admin") || username.equalsIgnoreCase("server")) {
            return UserRole.ADMIN;
        }
        return UserRole.USER;
    }

    public static void updateUserRole(String username, UserRole newRole) {
        if (username == null || newRole == null) return;
        localUserRoles.put(username, newRole);
        saveLocalFallback();

        if (isConnected && usersCol != null) {
            new Thread(() -> {
                try {
                    usersCol.updateOne(Filters.eq("username", username), Updates.set("role", newRole.name()));
                } catch (Exception e) {
                    System.err.println("[MongoDB] Lỗi cập nhật role: " + e.getMessage());
                }
            }).start();
        }
    }

    /**
     * Lưu tin nhắn vào CSDL.
     */
    public static void saveMessage(ChatMessageModel msg) {
        if (msg == null || msg.getSender() == null || msg.getReceiver() == null) return;

        // Lưu vào bộ nhớ cục bộ
        String convoKey = getConvoKey(msg.getSender(), msg.getReceiver());
        localMessageHistory.computeIfAbsent(convoKey, k -> Collections.synchronizedList(new ArrayList<>())).add(msg);
        saveLocalFallback();

        if (!isConnected || messagesCol == null) return;

        new Thread(() -> {
            try {
                Document doc = new Document("sender", msg.getSender())
                        .append("receiver", msg.getReceiver())
                        .append("type", msg.getType().name())
                        .append("content", msg.getContent() != null ? msg.getContent() : "")
                        .append("fileName", msg.getFileName() != null ? msg.getFileName() : "")
                        .append("fileSize", msg.getFileSize())
                        .append("mediaData", msg.getMediaData() != null ? msg.getMediaData() : "")
                        .append("timestamp", msg.getTimestamp())
                        .append("sourceLang", msg.getSourceLang() != null ? msg.getSourceLang() : "")
                        .append("translatedText", msg.getTranslatedText() != null ? msg.getTranslatedText() : "");

                messagesCol.insertOne(doc);

                // Cập nhật conversation
                List<String> participants = Arrays.asList(msg.getSender(), msg.getReceiver());
                Collections.sort(participants);
                String convoId = participants.get(0) + "_" + participants.get(1);

                conversationsCol.updateOne(
                        Filters.eq("_id", convoId),
                        Updates.combine(
                                Updates.set("participants", participants),
                                Updates.set("lastMessage", msg.getContent()),
                                Updates.set("lastUpdated", msg.getTimestamp())
                        ),
                        new com.mongodb.client.model.UpdateOptions().upsert(true)
                );
            } catch (Exception e) {
                System.err.println("[MongoDB] Lỗi lưu message: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Lấy lịch sử trò chuyện giữa 2 người dùng.
     */
    public static List<ChatMessageModel> getChatHistory(String user1, String user2, int limit) {
        List<ChatMessageModel> history = new ArrayList<>();

        if (isConnected && messagesCol != null) {
            try {
                var filter = Filters.or(
                        Filters.and(Filters.eq("sender", user1), Filters.eq("receiver", user2)),
                        Filters.and(Filters.eq("sender", user2), Filters.eq("receiver", user1))
                );

                for (Document doc : messagesCol.find(filter).sort(Indexes.ascending("timestamp")).limit(limit)) {
                    ChatMessageModel m = new ChatMessageModel();
                    m.setSender(doc.getString("sender"));
                    m.setReceiver(doc.getString("receiver"));
                    m.setContent(doc.getString("content"));
                    if (doc.containsKey("type")) {
                        try {
                            m.setType(ChatMessageModel.MessageType.valueOf(doc.getString("type")));
                        } catch (Exception ignored) {}
                    }
                    m.setFileName(doc.getString("fileName"));
                    if (doc.containsKey("fileSize")) {
                        m.setFileSize(doc.getLong("fileSize"));
                    }
                    m.setMediaData(doc.getString("mediaData"));
                    m.setTimestamp(doc.getString("timestamp"));
                    m.setSourceLang(doc.getString("sourceLang"));
                    m.setTranslatedText(doc.getString("translatedText"));
                    history.add(m);
                }
                return history;
            } catch (Exception e) {
                System.err.println("[MongoDB] Lỗi truy vấn lịch sử: " + e.getMessage());
            }
        }

        // Fallback từ local storage
        String key = getConvoKey(user1, user2);
        List<ChatMessageModel> localList = localMessageHistory.get(key);
        if (localList != null) {
            synchronized (localList) {
                int start = Math.max(0, localList.size() - limit);
                history.addAll(localList.subList(start, localList.size()));
            }
        }
        return history;
    }

    private static String getConvoKey(String u1, String u2) {
        if (u1.compareTo(u2) < 0) {
            return u1 + "__" + u2;
        }
        return u2 + "__" + u1;
    }

    private static String loadMongoUrlFromConfig() {
        File file = new File("config.properties");
        if (file.exists()) {
            Properties prop = new Properties();
            try (FileInputStream fis = new FileInputStream(file)) {
                prop.load(fis);
                return prop.getProperty("MONGO_URL");
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static void loadLocalFallback() {
        try {
            File fMsg = new File(LOCAL_HISTORY_FILE);
            if (fMsg.exists()) {
                try (Reader r = new InputStreamReader(new FileInputStream(fMsg), StandardCharsets.UTF_8)) {
                    Map<String, List<ChatMessageModel>> map = GSON.fromJson(r, new TypeToken<Map<String, List<ChatMessageModel>>>(){}.getType());
                    if (map != null) localMessageHistory.putAll(map);
                }
            }
            File fRoles = new File(LOCAL_USERS_FILE);
            if (fRoles.exists()) {
                try (Reader r = new InputStreamReader(new FileInputStream(fRoles), StandardCharsets.UTF_8)) {
                    Map<String, UserRole> map = GSON.fromJson(r, new TypeToken<Map<String, UserRole>>(){}.getType());
                    if (map != null) localUserRoles.putAll(map);
                }
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void saveLocalFallback() {
        try {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(LOCAL_HISTORY_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(localMessageHistory, w);
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(LOCAL_USERS_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(localUserRoles, w);
            }
        } catch (Exception ignored) {}
    }
}

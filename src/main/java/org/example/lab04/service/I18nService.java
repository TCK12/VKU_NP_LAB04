package org.example.lab04.service;

import java.util.HashMap;
import java.util.Map;

public class I18nService {
    private static String currentLanguage = "vi"; // "vi" or "en"
    private static final Map<String, Map<String, String>> DICT = new HashMap<>();

    static {
        Map<String, String> vi = new HashMap<>();
        vi.put("app.title", "Hệ Thống Chat Đa Ngôn Ngữ P2P");
        vi.put("login.title", "Đăng nhập Client");
        vi.put("login.name", "Tên người dùng:");
        vi.put("login.p2p_port", "Cổng P2P:");
        vi.put("login.server_ip", "Địa chỉ IP Server:");
        vi.put("login.server_port", "Cổng Server:");
        vi.put("online_users", "🟢 Người dùng Online");
        vi.put("search_user_prompt", "🔍 Tìm kiếm người dùng...");
        vi.put("status.waiting", "Đang chờ chọn người dùng để trò chuyện...");
        vi.put("status.chatting_with", "Đang trò chuyện với:");
        vi.put("status.no_other_users", "Chưa có người dùng nào khác online.");
        vi.put("status.list_updated", "Danh sách người dùng đã được cập nhật.");
        vi.put("status.disconnected", "❌ Mất kết nối tới Central Server!");
        vi.put("btn.send", "Gửi");
        vi.put("btn.send_file", "📁 Gửi File");
        vi.put("btn.settings", "⚙ Cài đặt");
        vi.put("btn.auto_translate_on", "⚡ Dịch tự động: BẬT");
        vi.put("btn.auto_translate_off", "⏸ Dịch tự động: TẮT");
        vi.put("target_lang_label", "Dịch sang:");
        vi.put("translation.translating", "⏳ Đang dịch...");
        vi.put("translation.hide", "👁 Ẩn bản dịch");
        vi.put("translation.show", "👁 Xem bản dịch");
        vi.put("translation.manual_translate", "🌐 Dịch");
        vi.put("translation.retry", "🔄 Thử lại");
        vi.put("translation.failed", "⚠️ Lỗi dịch:");
        vi.put("translation.label", "Bản dịch");
        vi.put("translation.from", "từ");
        vi.put("btn.download", "⬇ Tải file:");
        vi.put("downloading", "Đang tải...");
        vi.put("settings.title", "⚙ Cài Đặt Hệ Thống & Ngôn Ngữ");
        vi.put("settings.interface_lang", "Ngôn ngữ giao diện:");
        vi.put("settings.default_target_lang", "Ngôn ngữ dịch mặc định:");
        vi.put("settings.auto_translate", "Tự động dịch tin nhắn mới (Realtime)");
        vi.put("settings.translate_only_foreign", "Chỉ tự động dịch khi khác ngôn ngữ đích");
        vi.put("settings.show_location", "Hiển thị vị trí gần đúng của tôi cho người khác");
        vi.put("settings.manual_location", "Vị trí tùy chỉnh (ghi đè IP):");
        vi.put("settings.reset_btn", "🔄 Đặt lại mặc định");
        vi.put("settings.save_btn", "💾 Lưu & Áp dụng");
        vi.put("settings.saved_msg", "Đã lưu cài đặt thành công!");
        vi.put("location.unknown", "Unknown location");
        vi.put("location.hidden", "🔒 Ẩn vị trí");
        vi.put("file.saved_to", "Đã lưu file thành công tại:");
        vi.put("file.error_not_found", "File gốc trên máy đối phương đã bị xóa hoặc di chuyển!");
        DICT.put("vi", vi);

        Map<String, String> en = new HashMap<>();
        en.put("app.title", "Multilingual P2P Chat System");
        en.put("login.title", "Client Login");
        en.put("login.name", "Username:");
        en.put("login.p2p_port", "P2P Port:");
        en.put("login.server_ip", "Server IP:");
        en.put("login.server_port", "Server Port:");
        en.put("online_users", "🟢 Online Users");
        en.put("search_user_prompt", "🔍 Search users...");
        en.put("status.waiting", "Select a user from the list to start chatting...");
        en.put("status.chatting_with", "Direct chat with:");
        en.put("status.no_other_users", "No other users currently online.");
        en.put("status.list_updated", "Online user list updated.");
        en.put("status.disconnected", "❌ Disconnected from Central Server!");
        en.put("btn.send", "Send");
        en.put("btn.send_file", "📁 Send File");
        en.put("btn.settings", "⚙ Settings");
        en.put("btn.auto_translate_on", "⚡ Auto Translate: ON");
        en.put("btn.auto_translate_off", "⏸ Auto Translate: OFF");
        en.put("target_lang_label", "Translate to:");
        en.put("translation.translating", "⏳ Translating...");
        en.put("translation.hide", "👁 Hide translation");
        en.put("translation.show", "👁 Show translation");
        en.put("translation.manual_translate", "🌐 Translate");
        en.put("translation.retry", "🔄 Retry");
        en.put("translation.failed", "⚠️ Translation error:");
        en.put("translation.label", "Translation");
        en.put("translation.from", "from");
        en.put("btn.download", "⬇ Download:");
        en.put("downloading", "Downloading...");
        en.put("settings.title", "⚙ System & Language Settings");
        en.put("settings.interface_lang", "Interface Language:");
        en.put("settings.default_target_lang", "Default Target Language:");
        en.put("settings.auto_translate", "Auto-translate new incoming/outgoing messages (Realtime)");
        en.put("settings.translate_only_foreign", "Translate only foreign-language messages");
        en.put("settings.show_location", "Show my approximate location to other users");
        en.put("settings.manual_location", "Custom Location (override IP):");
        en.put("settings.reset_btn", "🔄 Reset Preferences");
        en.put("settings.save_btn", "💾 Save & Apply");
        en.put("settings.saved_msg", "Settings saved successfully!");
        en.put("location.unknown", "Unknown location");
        en.put("location.hidden", "🔒 Hidden location");
        en.put("file.saved_to", "File saved successfully at:");
        en.put("file.error_not_found", "Original file on peer's machine was deleted or moved!");
        DICT.put("en", en);
    }

    public static void setLanguage(String lang) {
        if ("en".equalsIgnoreCase(lang)) {
            currentLanguage = "en";
        } else {
            currentLanguage = "vi";
        }
    }

    public static String getLanguage() {
        return currentLanguage;
    }

    public static String get(String key) {
        Map<String, String> langDict = DICT.get(currentLanguage);
        if (langDict != null && langDict.containsKey(key)) {
            return langDict.get(key);
        }
        // Fallback to Vietnamese
        Map<String, String> fallback = DICT.get("vi");
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }
        return key;
    }
}

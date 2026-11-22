package org.example.lab04.model;

import java.io.Serializable;
import java.time.Instant;

public class ChatMessageModel implements Serializable {
    public enum MessageType {
        TEXT,
        IMAGE,
        VIDEO,
        VOICE,
        FILE
    }

    private String id;
    private String sender;
    private String receiver;
    private MessageType type = MessageType.TEXT;
    private String content;
    private String fileName;
    private long fileSize;
    private String mediaData; // Local path or base64
    private String timestamp;
    private String sourceLang;
    private String translatedText;

    public ChatMessageModel() {
        this.timestamp = Instant.now().toString();
    }

    public ChatMessageModel(String sender, String receiver, MessageType type, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.type = type != null ? type : MessageType.TEXT;
        this.content = content;
        this.timestamp = Instant.now().toString();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getMediaData() { return mediaData; }
    public void setMediaData(String mediaData) { this.mediaData = mediaData; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSourceLang() { return sourceLang; }
    public void setSourceLang(String sourceLang) { this.sourceLang = sourceLang; }

    public String getTranslatedText() { return translatedText; }
    public void setTranslatedText(String translatedText) { this.translatedText = translatedText; }
}

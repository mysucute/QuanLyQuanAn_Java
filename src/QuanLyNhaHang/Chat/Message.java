package QuanLyNhaHang.Chat;

import java.io.Serializable;
import java.time.LocalTime;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L;
    
    public enum Type {TEXT, IMAGE, FILE, AUDIO, GROUP_MESSAGE, CREATE_GROUP, ICON}
    private Type type;
    private String sender;
    private String content;
    private byte[] data;
    private String fileName;
    private boolean encrypted;
    private String groupId;
    private Type originalType;
    private String hiddenMessageId;
    private String hiddenPasswordId;
    private LocalTime timestamp;
    private String audioId;
    private String role; // Thêm role: admin/employee

    public Message(Type type, String sender, String role, String content, byte[] data, String fileName, boolean encrypted) {
        this.type = type;
        this.sender = sender;
        this.role = role;
        this.content = content;
        this.data = data;
        this.fileName = fileName;
        this.encrypted = encrypted;
        this.timestamp = LocalTime.now();
    }

    public Message(Type type, String sender, String role, String content) {
        this(type, sender, role, content, null, null, false);
    }

    // Getters and setters
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public Type getOriginalType() { return originalType; }
    public void setOriginalType(Type originalType) { this.originalType = originalType; }
    public String getHiddenMessageId() { return hiddenMessageId; }
    public void setHiddenMessageId(String hiddenMessageId) { this.hiddenMessageId = hiddenMessageId; }
    public String getHiddenPasswordId() { return hiddenPasswordId; }
    public void setHiddenPasswordId(String hiddenPasswordId) { this.hiddenPasswordId = hiddenPasswordId; }
    public LocalTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalTime timestamp) { this.timestamp = timestamp; }
    public String getAudioId() { return audioId; }
    public void setAudioId(String audioId) { this.audioId = audioId; }
}

package com.bloodbuddy.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Model representing an individual message in a secure in-app conversation.
 * Persisted in data/messages.json.
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String conversationId;
    private final String senderUserId;
    private final String messageText;
    private final String createdAt;
    private final boolean read;
    private final String readAt;

    public ChatMessage(String id, String conversationId, String senderUserId,
                       String messageText, String createdAt, boolean read, String readAt) {
        this.id = id;
        this.conversationId = conversationId != null ? conversationId.trim() : "";
        this.senderUserId = senderUserId != null ? senderUserId.trim() : "";
        this.messageText = messageText != null ? messageText : "";
        this.createdAt = createdAt != null ? createdAt : "";
        this.read = read;
        this.readAt = readAt != null ? readAt : "";
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public String getMessageText() {
        return messageText;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public String getReadAt() {
        return readAt;
    }

    public ChatMessage withRead(boolean newRead, String newReadAt) {
        return new ChatMessage(this.id, this.conversationId, this.senderUserId,
                this.messageText, this.createdAt, newRead, newReadAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id='" + id + '\'' +
                ", conversationId='" + conversationId + '\'' +
                ", senderUserId='" + senderUserId + '\'' +
                ", read=" + read +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}

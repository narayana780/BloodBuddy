package com.bloodbuddy.model;

/**
 * Model representing an in-app notification in Blood Buddy.
 * Persisted to local file storage, with unique IDs, categories,
 * timestamps, read/unread status, and optional admin authorization restriction.
 */
public class AppNotification {

    public static final String CAT_REQUEST_SUBMITTED = "REQUEST_SUBMITTED";
    public static final String CAT_MATCHES_FOUND = "MATCHES_FOUND";
    public static final String CAT_NO_MATCHES = "NO_MATCHES";
    public static final String CAT_STATUS_CHANGED = "STATUS_CHANGED";
    public static final String CAT_ADMIN_ALERT = "ADMIN_ALERT";
    public static final String CAT_CRITICAL_REQUEST = "CRITICAL_REQUEST";
    public static final String CAT_OVERDUE_ALERT = "OVERDUE_ALERT";
    public static final String CAT_DUE_SOON_ALERT = "DUE_SOON_ALERT";
    public static final String CAT_DONOR = "DONOR_UPDATE";
    public static final String CAT_USER_ACCOUNT = "USER_ACCOUNT";
    public static final String CAT_CHAT_REQUEST = "CHAT_REQUEST";
    public static final String CAT_CHAT_ACCEPTED = "CHAT_ACCEPTED";
    public static final String CAT_CHAT_DECLINED = "CHAT_DECLINED";
    public static final String CAT_CHAT_MESSAGE = "CHAT_MESSAGE";

    private final String id;
    private final String timestamp;
    private final String category;
    private final String title;
    private final String message;
    private final String targetId;
    private final boolean read;
    private final boolean adminOnly;
    private final String linkUrl;
    private final String recipientUserId;

    public AppNotification(String id, String timestamp, String category, String title,
                           String message, String targetId, boolean read,
                           boolean adminOnly, String linkUrl) {
        this(id, timestamp, category, title, message, targetId, read, adminOnly, linkUrl, "");
    }

    public AppNotification(String id, String timestamp, String category, String title,
                           String message, String targetId, boolean read,
                           boolean adminOnly, String linkUrl, String recipientUserId) {
        this.id = id;
        this.timestamp = timestamp;
        this.category = category;
        this.title = title;
        this.message = message;
        this.targetId = targetId != null ? targetId.trim() : "";
        this.read = read;
        this.adminOnly = adminOnly;
        this.linkUrl = linkUrl != null ? linkUrl.trim() : "";
        this.recipientUserId = recipientUserId != null ? recipientUserId.trim() : "";
    }

    public String getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getTargetId() {
        return targetId;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public String getRecipientUserId() {
        return recipientUserId != null ? recipientUserId : "";
    }

    public AppNotification withRead(boolean newRead) {
        return new AppNotification(this.id, this.timestamp, this.category, this.title,
                this.message, this.targetId, newRead, this.adminOnly, this.linkUrl, this.recipientUserId);
    }
}

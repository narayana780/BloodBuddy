package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.Conversation;
import com.bloodbuddy.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-based repository for persisting in-app notifications to local storage.
 * Provides thread-safe, atomic file writes, crash-resistant JSON serialization,
 * and duplicate notification prevention across server restarts and page refreshes.
 */
public class NotificationRepository {

    private static final NotificationRepository INSTANCE = new NotificationRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static NotificationRepository getInstance() {
        return INSTANCE;
    }

    public NotificationRepository() {
        this(Paths.get(AppConfig.NOTIFICATIONS_FILE));
    }

    public NotificationRepository(Path storagePath) {
        this.storagePath = storagePath;
        ensureStorageDirectory();
        initializeDefaultSamplesIfEmpty();
    }

    private void ensureStorageDirectory() {
        try {
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("[NotificationRepository] Error creating storage directory: " + e.getMessage());
        }
    }

    private void initializeDefaultSamplesIfEmpty() {
        lock.writeLock().lock();
        try {
            if (!Files.exists(storagePath) || Files.size(storagePath) == 0) {
                List<AppNotification> defaults = new ArrayList<>();
                defaults.add(new AppNotification(
                        "NOTIF-1001",
                        "11 Oct 2026, 09:30 AM",
                        AppNotification.CAT_REQUEST_SUBMITTED,
                        "New Blood Requisition: REQ-1001",
                        "Emergency requisition submitted for 2 units of O+ blood at Government General Hospital, Danavaipeta.",
                        "REQ-1001",
                        true,
                        false,
                        "/matching?requestId=REQ-1001"
                ));
                defaults.add(new AppNotification(
                        "NOTIF-1002",
                        "11 Oct 2026, 09:31 AM",
                        AppNotification.CAT_MATCHES_FOUND,
                        "Smart Matches: REQ-1001",
                        "Algorithm identified potential matching voluntary donors in Rajahmundry for REQ-1001 (O+).",
                        "REQ-1001",
                        false,
                        false,
                        "/matching?requestId=REQ-1001"
                ));
                defaults.add(new AppNotification(
                        "NOTIF-1003",
                        "11 Oct 2026, 10:15 AM",
                        AppNotification.CAT_REQUEST_SUBMITTED,
                        "Critical Requisition: REQ-1002",
                        "Critical blood requisition logged for 1 unit of rare B- blood at Bollineni Super Speciality Hospital.",
                        "REQ-1002",
                        false,
                        false,
                        "/matching?requestId=REQ-1002"
                ));
                writeNotificationsToFile(defaults);
            }
        } catch (IOException e) {
            System.err.println("[NotificationRepository] Error initializing default notifications: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<AppNotification> getAllNotifications() {
        lock.readLock().lock();
        try {
            return getAllNotificationsInternal();
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<AppNotification> getAllNotificationsInternal() {
        if (!Files.exists(storagePath)) {
            return Collections.emptyList();
        }
        try {
            String content = Files.readString(storagePath, StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || content.equals("[]")) {
                return Collections.emptyList();
            }
            return parseJson(content);
        } catch (IOException e) {
            System.err.println("[NotificationRepository] Failed to read notifications from file: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<AppNotification> getNotificationsForUser(boolean isAdmin) {
        return getNotificationsForUser(null, isAdmin);
    }

    public List<AppNotification> getNotificationsForUser(String userId, boolean isAdmin) {
        List<AppNotification> all = getAllNotifications();
        String cleanUserId = (userId != null && !userId.trim().isEmpty()) ? userId.trim() : null;
        List<AppNotification> filtered = new ArrayList<>();
        Optional<User> userOpt = (cleanUserId != null) ? UserRepository.getInstance().findById(cleanUserId) : Optional.empty();
        String linkedDonorId = userOpt.map(User::getLinkedDonorId).orElse("");

        for (AppNotification n : all) {
            if (!isAdmin && n.isAdminOnly()) {
                continue;
            }
            if (!n.getRecipientUserId().isEmpty()) {
                if (cleanUserId == null) {
                    continue; // Skip user-targeted notifications for unauthenticated visitors
                }
                boolean matchesUser = n.getRecipientUserId().equalsIgnoreCase(cleanUserId);
                if (!matchesUser && !linkedDonorId.isEmpty() && n.getTargetId() != null && n.getTargetId().startsWith("CONV-")) {
                    Optional<Conversation> convOpt = ConversationRepository.getInstance().findById(n.getTargetId());
                    if (convOpt.isPresent() && linkedDonorId.equalsIgnoreCase(convOpt.get().getDonorProfileId())) {
                        matchesUser = true;
                    }
                }
                if (!matchesUser) {
                    continue;
                }
            }
            filtered.add(n);
        }
        return Collections.unmodifiableList(filtered);
    }

    public int getUnreadCount(boolean isAdmin) {
        return getUnreadCount(null, isAdmin);
    }

    public int getUnreadCount(String userId, boolean isAdmin) {
        List<AppNotification> userList = getNotificationsForUser(userId, isAdmin);
        int unread = 0;
        for (AppNotification n : userList) {
            if (!n.isRead()) {
                unread++;
            }
        }
        return unread;
    }

    public boolean hasNotification(String category, String targetId) {
        if (category == null || targetId == null) {
            return false;
        }
        String cleanCat = category.trim();
        String cleanTarget = targetId.trim();

        List<AppNotification> all = getAllNotifications();
        for (AppNotification n : all) {
            if (n.getCategory().equalsIgnoreCase(cleanCat) && n.getTargetId().equalsIgnoreCase(cleanTarget)) {
                return true;
            }
        }
        return false;
    }

    public Optional<AppNotification> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String searchId = id.trim();
        for (AppNotification n : getAllNotifications()) {
            if (n.getId().equalsIgnoreCase(searchId)) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }

    public AppNotification save(AppNotification notification) {
        if (notification == null || notification.getId() == null) {
            throw new IllegalArgumentException("Notification and Notification ID must not be null");
        }

        lock.writeLock().lock();
        try {
            List<AppNotification> current = new ArrayList<>(getAllNotificationsInternal());
            int existingIndex = -1;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).getId().equalsIgnoreCase(notification.getId())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                current.set(existingIndex, notification);
            } else {
                // Add new notification at top of list so newest appears first
                current.add(0, notification);
            }

            writeNotificationsToFile(current);
            return notification;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean markAsRead(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        lock.writeLock().lock();
        try {
            List<AppNotification> current = new ArrayList<>(getAllNotificationsInternal());
            boolean updated = false;
            for (int i = 0; i < current.size(); i++) {
                AppNotification n = current.get(i);
                if (n.getId().equalsIgnoreCase(id.trim())) {
                    if (!n.isRead()) {
                        current.set(i, n.withRead(true));
                        updated = true;
                    }
                    break;
                }
            }
            if (updated) {
                writeNotificationsToFile(current);
            }
            return updated;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int markAllAsRead(boolean isAdmin) {
        lock.writeLock().lock();
        try {
            List<AppNotification> current = new ArrayList<>(getAllNotificationsInternal());
            int count = 0;
            for (int i = 0; i < current.size(); i++) {
                AppNotification n = current.get(i);
                if (!n.isRead()) {
                    if (isAdmin || !n.isAdminOnly()) {
                        current.set(i, n.withRead(true));
                        count++;
                    }
                }
            }
            if (count > 0) {
                writeNotificationsToFile(current);
            }
            return count;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String generateNextId() {
        lock.readLock().lock();
        try {
            List<AppNotification> current = getAllNotificationsInternal();
            int maxNum = 1000;
            for (AppNotification n : current) {
                String id = n.getId();
                if (id != null && id.startsWith("NOTIF-")) {
                    try {
                        int num = Integer.parseInt(id.substring(6));
                        if (num > maxNum) {
                            maxNum = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return "NOTIF-" + (maxNum + 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void writeNotificationsToFile(List<AppNotification> list) {
        ensureStorageDirectory();
        Path tempPath = storagePath.resolveSibling(storagePath.getFileName().toString() + ".tmp");
        String json = serializeJson(list);

        try {
            Files.writeString(tempPath, json, StandardCharsets.UTF_8);
            try {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[NotificationRepository] Error writing notifications to disk: " + e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to persist notifications to " + storagePath, e);
        }
    }

    private String serializeJson(List<AppNotification> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            AppNotification n = list.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(n.getId())).append("\",\n");
            sb.append("    \"timestamp\": \"").append(escapeJson(n.getTimestamp())).append("\",\n");
            sb.append("    \"category\": \"").append(escapeJson(n.getCategory())).append("\",\n");
            sb.append("    \"title\": \"").append(escapeJson(n.getTitle())).append("\",\n");
            sb.append("    \"message\": \"").append(escapeJson(n.getMessage())).append("\",\n");
            sb.append("    \"targetId\": \"").append(escapeJson(n.getTargetId())).append("\",\n");
            sb.append("    \"read\": ").append(n.isRead()).append(",\n");
            sb.append("    \"adminOnly\": ").append(n.isAdminOnly()).append(",\n");
            sb.append("    \"linkUrl\": \"").append(escapeJson(n.getLinkUrl())).append("\",\n");
            sb.append("    \"recipientUserId\": \"").append(escapeJson(n.getRecipientUserId())).append("\"\n");
            sb.append("  }");
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private List<AppNotification> parseJson(String json) {
        List<AppNotification> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String timestamp = getStringProp(obj, "timestamp");
            String category = getStringProp(obj, "category");
            String title = getStringProp(obj, "title");
            String message = getStringProp(obj, "message");
            String targetId = getStringProp(obj, "targetId");
            boolean read = getBoolProp(obj, "read", false);
            boolean adminOnly = getBoolProp(obj, "adminOnly", false);
            String linkUrl = getStringProp(obj, "linkUrl");
            String recipientUserId = getStringProp(obj, "recipientUserId");

            if (!id.isEmpty() && !title.isEmpty()) {
                list.add(new AppNotification(id, timestamp, category, title, message, targetId, read, adminOnly, linkUrl, recipientUserId));
            }
        }

        return Collections.unmodifiableList(list);
    }

    private static String getStringProp(String objJson, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(objJson);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        return "";
    }

    private static boolean getBoolProp(String objJson, String key, boolean def) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(objJson);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return def;
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}

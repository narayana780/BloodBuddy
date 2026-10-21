package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.ChatMessage;
import com.bloodbuddy.model.Conversation;

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
 * Thread-safe file-based repository for persisting ChatMessage records in data/messages.json.
 * Uses ReentrantReadWriteLock, atomic file operations, and safe JSON serialization.
 */
public class ChatMessageRepository {

    private static final ChatMessageRepository INSTANCE = new ChatMessageRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static ChatMessageRepository getInstance() {
        return INSTANCE;
    }

    public ChatMessageRepository() {
        this(Paths.get(AppConfig.MESSAGES_FILE));
    }

    public ChatMessageRepository(Path storagePath) {
        this.storagePath = storagePath;
        ensureStorageDirectory();
    }

    private void ensureStorageDirectory() {
        try {
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("[ChatMessageRepository] Error creating storage directory: " + e.getMessage());
        }
    }

    public List<ChatMessage> findAll() {
        lock.readLock().lock();
        try {
            if (!Files.exists(storagePath)) {
                return Collections.emptyList();
            }
            String content = Files.readString(storagePath, StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || "[]".equals(content)) {
                return Collections.emptyList();
            }
            return parseJson(content);
        } catch (IOException e) {
            System.err.println("[ChatMessageRepository] Error reading messages: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ChatMessage> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleanId = conversationId.trim();
        List<ChatMessage> list = new ArrayList<>(findAll().stream()
                .filter(m -> cleanId.equalsIgnoreCase(m.getConversationId()))
                .toList());

        // Chronological order
        list.sort((m1, m2) -> {
            int cmp = m1.getCreatedAt().compareTo(m2.getCreatedAt());
            if (cmp != 0) return cmp;
            return m1.getId().compareTo(m2.getId());
        });
        return Collections.unmodifiableList(list);
    }

    public Optional<ChatMessage> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanId = id.trim();
        return findAll().stream()
                .filter(m -> cleanId.equalsIgnoreCase(m.getId()))
                .findFirst();
    }

    public synchronized ChatMessage save(ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        lock.writeLock().lock();
        try {
            List<ChatMessage> current = new ArrayList<>(findAll());
            String msgId = message.getId();
            if (msgId == null || msgId.trim().isEmpty()) {
                msgId = generateNextId(current);
                message = new ChatMessage(msgId, message.getConversationId(),
                        message.getSenderUserId(), message.getMessageText(),
                        message.getCreatedAt(), message.isRead(), message.getReadAt());
            } else {
                for (ChatMessage existing : current) {
                    if (existing.getId().equalsIgnoreCase(msgId)) {
                        throw new IllegalArgumentException("ChatMessage with ID already exists: " + msgId);
                    }
                }
            }

            current.add(message);
            persistToFile(current);
            return message;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public synchronized int markMessagesAsRead(String conversationId, String readingUserId, String readTimestamp) {
        if (conversationId == null || readingUserId == null) {
            return 0;
        }
        String cleanConvId = conversationId.trim();
        String cleanReader = readingUserId.trim();

        lock.writeLock().lock();
        try {
            List<ChatMessage> current = new ArrayList<>(findAll());
            int modifiedCount = 0;

            for (int i = 0; i < current.size(); i++) {
                ChatMessage m = current.get(i);
                if (cleanConvId.equalsIgnoreCase(m.getConversationId())
                        && !cleanReader.equalsIgnoreCase(m.getSenderUserId())
                        && !m.isRead()) {
                    current.set(i, m.withRead(true, readTimestamp));
                    modifiedCount++;
                }
            }

            if (modifiedCount > 0) {
                persistToFile(current);
            }
            return modifiedCount;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getUnreadCountForConversation(String conversationId, String currentUserId) {
        if (conversationId == null || currentUserId == null) return 0;
        String cleanConv = conversationId.trim();
        String cleanUser = currentUserId.trim();

        int unread = 0;
        for (ChatMessage m : findAll()) {
            if (cleanConv.equalsIgnoreCase(m.getConversationId())
                    && !cleanUser.equalsIgnoreCase(m.getSenderUserId())
                    && !m.isRead()) {
                unread++;
            }
        }
        return unread;
    }

    public int getUnreadCountForUser(String userId, List<Conversation> userConversations) {
        if (userId == null || userConversations == null || userConversations.isEmpty()) return 0;
        String cleanUser = userId.trim();

        List<String> convIds = userConversations.stream().map(Conversation::getId).toList();
        int unread = 0;
        for (ChatMessage m : findAll()) {
            if (convIds.contains(m.getConversationId())
                    && !cleanUser.equalsIgnoreCase(m.getSenderUserId())
                    && !m.isRead()) {
                unread++;
            }
        }
        return unread;
    }

    public String generateNextId() {
        return generateNextId(findAll());
    }

    public String generateNextId(List<ChatMessage> current) {
        int maxSeq = 1000;
        Pattern p = Pattern.compile("MSG-(\\d+)", Pattern.CASE_INSENSITIVE);
        for (ChatMessage m : current) {
            if (m.getId() != null) {
                Matcher mat = p.matcher(m.getId().trim());
                if (mat.matches()) {
                    try {
                        int val = Integer.parseInt(mat.group(1));
                        if (val > maxSeq) {
                            maxSeq = val;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return "MSG-" + (maxSeq + 1);
    }

    private void persistToFile(List<ChatMessage> list) {
        ensureStorageDirectory();
        String json = serializeJson(list);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(storagePath.getParent(), "messages-atomic-", ".tmp");
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);

            try {
                Files.move(tempFile, storagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ChatMessageRepository] Error persisting messages: " + e.getMessage());
            throw new RuntimeException("Failed to persist message storage: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.delete(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }

    private String serializeJson(List<ChatMessage> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            ChatMessage m = list.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(m.getId())).append("\",\n");
            sb.append("    \"conversationId\": \"").append(escapeJson(m.getConversationId())).append("\",\n");
            sb.append("    \"senderUserId\": \"").append(escapeJson(m.getSenderUserId())).append("\",\n");
            sb.append("    \"messageText\": \"").append(escapeJson(m.getMessageText())).append("\",\n");
            sb.append("    \"createdAt\": \"").append(escapeJson(m.getCreatedAt())).append("\",\n");
            sb.append("    \"read\": ").append(m.isRead()).append(",\n");
            sb.append("    \"readAt\": \"").append(escapeJson(m.getReadAt())).append("\"\n");
            sb.append("  }");
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private List<ChatMessage> parseJson(String json) {
        List<ChatMessage> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String conversationId = getStringProp(obj, "conversationId");
            String senderUserId = getStringProp(obj, "senderUserId");
            String messageText = getStringProp(obj, "messageText");
            String createdAt = getStringProp(obj, "createdAt");
            boolean read = getBoolProp(obj, "read", false);
            String readAt = getStringProp(obj, "readAt");

            if (!id.isEmpty() && !conversationId.isEmpty() && !senderUserId.isEmpty()) {
                list.add(new ChatMessage(id, conversationId, senderUserId, messageText, createdAt, read, readAt));
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

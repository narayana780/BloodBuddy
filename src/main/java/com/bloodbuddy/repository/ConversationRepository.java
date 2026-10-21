package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
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
 * Thread-safe file-based repository for persisting Conversation records in data/conversations.json.
 * Uses ReentrantReadWriteLock, atomic file moves, and safe JSON serialization.
 */
public class ConversationRepository {

    private static final ConversationRepository INSTANCE = new ConversationRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static ConversationRepository getInstance() {
        return INSTANCE;
    }

    public ConversationRepository() {
        this(Paths.get(AppConfig.CONVERSATIONS_FILE));
    }

    public ConversationRepository(Path storagePath) {
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
            System.err.println("[ConversationRepository] Error creating storage directory: " + e.getMessage());
        }
    }

    public List<Conversation> findAll() {
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
            System.err.println("[ConversationRepository] Error reading conversations from storage: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Conversation> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanId = id.trim();
        return findAll().stream()
                .filter(c -> cleanId.equalsIgnoreCase(c.getId()))
                .findFirst();
    }

    public List<Conversation> findByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleanId = userId.trim();
        Optional<User> userOpt = UserRepository.getInstance().findById(cleanId);
        String linkedDonorId = userOpt.map(User::getLinkedDonorId).orElse("");
        return findByUserId(cleanId, linkedDonorId);
    }

    public List<Conversation> findByUserId(String userId, String linkedDonorId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String cleanId = userId.trim();
        List<Conversation> list = new ArrayList<>(findAll().stream()
                .filter(c -> c.isParticipant(cleanId, linkedDonorId))
                .toList());

        // Sort by lastActivityAt descending (most recent first)
        list.sort((c1, c2) -> c2.getLastActivityAt().compareTo(c1.getLastActivityAt()));
        return Collections.unmodifiableList(list);
    }

    public Optional<Conversation> findExistingConversation(String requesterUserId, String donorUserId, String requestId) {
        if (requesterUserId == null || donorUserId == null) {
            return Optional.empty();
        }
        String reqId = requesterUserId.trim();
        String donId = donorUserId.trim();
        String bloodReqId = (requestId != null) ? requestId.trim() : "";

        return findAll().stream()
                .filter(c -> c.getRequesterUserId().equalsIgnoreCase(reqId)
                        && c.getDonorUserId().equalsIgnoreCase(donId)
                        && c.getRequestId().equalsIgnoreCase(bloodReqId))
                .findFirst();
    }

    public synchronized Conversation save(Conversation conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation cannot be null");
        }

        lock.writeLock().lock();
        try {
            List<Conversation> current = new ArrayList<>(findAll());
            String convId = conversation.getId();
            if (convId == null || convId.trim().isEmpty()) {
                convId = generateNextId(current);
                conversation = new Conversation(convId, conversation.getRequestId(),
                        conversation.getRequesterUserId(), conversation.getDonorUserId(),
                        conversation.getDonorProfileId(), conversation.getStatus(),
                        conversation.getCreatedAt(), conversation.getLastActivityAt(),
                        conversation.getAcceptedAt(), conversation.getDeclinedAt(), conversation.getClosedAt());
            } else {
                for (Conversation existing : current) {
                    if (existing.getId().equalsIgnoreCase(convId)) {
                        throw new IllegalArgumentException("Conversation with ID already exists: " + convId);
                    }
                }
            }

            current.add(conversation);
            persistToFile(current);
            return conversation;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public synchronized boolean update(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            List<Conversation> current = new ArrayList<>(findAll());
            int targetIndex = -1;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).getId().equalsIgnoreCase(conversation.getId())) {
                    targetIndex = i;
                    break;
                }
            }

            if (targetIndex < 0) {
                return false;
            }

            current.set(targetIndex, conversation);
            persistToFile(current);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String generateNextId() {
        return generateNextId(findAll());
    }

    public String generateNextId(List<Conversation> current) {
        int maxSeq = 1000;
        Pattern p = Pattern.compile("CONV-(\\d+)", Pattern.CASE_INSENSITIVE);
        for (Conversation c : current) {
            if (c.getId() != null) {
                Matcher m = p.matcher(c.getId().trim());
                if (m.matches()) {
                    try {
                        int val = Integer.parseInt(m.group(1));
                        if (val > maxSeq) {
                            maxSeq = val;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return "CONV-" + (maxSeq + 1);
    }

    private void persistToFile(List<Conversation> list) {
        ensureStorageDirectory();
        String json = serializeJson(list);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(storagePath.getParent(), "conversations-atomic-", ".tmp");
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);

            try {
                Files.move(tempFile, storagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ConversationRepository] Error persisting conversations: " + e.getMessage());
            throw new RuntimeException("Failed to persist conversation storage: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.delete(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }

    private String serializeJson(List<Conversation> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            Conversation c = list.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(c.getId())).append("\",\n");
            sb.append("    \"requestId\": \"").append(escapeJson(c.getRequestId())).append("\",\n");
            sb.append("    \"requesterUserId\": \"").append(escapeJson(c.getRequesterUserId())).append("\",\n");
            sb.append("    \"donorUserId\": \"").append(escapeJson(c.getDonorUserId())).append("\",\n");
            sb.append("    \"donorProfileId\": \"").append(escapeJson(c.getDonorProfileId())).append("\",\n");
            sb.append("    \"status\": \"").append(escapeJson(c.getStatus())).append("\",\n");
            sb.append("    \"createdAt\": \"").append(escapeJson(c.getCreatedAt())).append("\",\n");
            sb.append("    \"lastActivityAt\": \"").append(escapeJson(c.getLastActivityAt())).append("\",\n");
            sb.append("    \"acceptedAt\": \"").append(escapeJson(c.getAcceptedAt())).append("\",\n");
            sb.append("    \"declinedAt\": \"").append(escapeJson(c.getDeclinedAt())).append("\",\n");
            sb.append("    \"closedAt\": \"").append(escapeJson(c.getClosedAt())).append("\"\n");
            sb.append("  }");
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private List<Conversation> parseJson(String json) {
        List<Conversation> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String requestId = getStringProp(obj, "requestId");
            String requesterUserId = getStringProp(obj, "requesterUserId");
            String donorUserId = getStringProp(obj, "donorUserId");
            String donorProfileId = getStringProp(obj, "donorProfileId");
            String status = getStringProp(obj, "status");
            String createdAt = getStringProp(obj, "createdAt");
            String lastActivityAt = getStringProp(obj, "lastActivityAt");
            String acceptedAt = getStringProp(obj, "acceptedAt");
            String declinedAt = getStringProp(obj, "declinedAt");
            String closedAt = getStringProp(obj, "closedAt");

            if (!id.isEmpty() && !requesterUserId.isEmpty() && !donorUserId.isEmpty()) {
                list.add(new Conversation(id, requestId, requesterUserId, donorUserId,
                        donorProfileId, status, createdAt, lastActivityAt,
                        acceptedAt, declinedAt, closedAt));
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

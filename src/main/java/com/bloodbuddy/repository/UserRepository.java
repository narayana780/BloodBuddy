package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.User;

import java.io.File;
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
 * Thread-safe file-based repository for persisting user accounts in data/users.json.
 * Provides atomic file swapping, re-entrant read-write locking, and duplicate prevention.
 */
public class UserRepository {

    private static final UserRepository INSTANCE = new UserRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static UserRepository getInstance() {
        return INSTANCE;
    }

    public UserRepository() {
        this(Paths.get(AppConfig.USERS_FILE));
    }

    public UserRepository(Path storagePath) {
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
            System.err.println("[UserRepository] Error creating storage directory: " + e.getMessage());
        }
    }

    public List<User> findAll() {
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
            System.err.println("[UserRepository] Error reading users from storage: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<User> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return findAll().stream()
                .filter(u -> id.equalsIgnoreCase(u.getId()))
                .findFirst();
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanEmail = email.trim().toLowerCase();
        return findAll().stream()
                .filter(u -> cleanEmail.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanUser = username.trim().toLowerCase();
        return findAll().stream()
                .filter(u -> cleanUser.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    public Optional<User> findByEmailOrUsername(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = identifier.trim().toLowerCase();
        return findAll().stream()
                .filter(u -> clean.equalsIgnoreCase(u.getEmail()) || clean.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    public Optional<User> findByLinkedDonorId(String donorId) {
        if (donorId == null || donorId.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = donorId.trim();
        return findAll().stream()
                .filter(u -> clean.equalsIgnoreCase(u.getLinkedDonorId()))
                .findFirst();
    }

    public synchronized User save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        lock.writeLock().lock();
        try {
            List<User> users = new ArrayList<>(findAll());

            // Validate uniqueness
            for (User existing : users) {
                if (existing.getId().equalsIgnoreCase(user.getId())) {
                    throw new IllegalArgumentException("User with ID already exists: " + user.getId());
                }
                if (!user.getEmail().isEmpty() && existing.getEmail().equalsIgnoreCase(user.getEmail())) {
                    throw new IllegalArgumentException("An account with this email address already exists.");
                }
                if (!user.getUsername().isEmpty() && existing.getUsername().equalsIgnoreCase(user.getUsername())) {
                    throw new IllegalArgumentException("An account with this username already exists.");
                }
            }

            if (user.getId() == null || user.getId().trim().isEmpty()) {
                user.setId(generateNextId(users));
            }

            users.add(user);
            persistToFile(users);
            return user;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public synchronized boolean update(User user) {
        if (user == null || user.getId() == null) {
            return false;
        }

        lock.writeLock().lock();
        try {
            List<User> users = new ArrayList<>(findAll());
            int targetIndex = -1;
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getId().equalsIgnoreCase(user.getId())) {
                    targetIndex = i;
                    break;
                }
            }

            if (targetIndex < 0) {
                return false;
            }

            users.set(targetIndex, user);
            persistToFile(users);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String generateNextId(List<User> currentUsers) {
        int maxSeq = 1000;
        Pattern p = Pattern.compile("USR-(\\d+)", Pattern.CASE_INSENSITIVE);
        for (User u : currentUsers) {
            if (u.getId() != null) {
                Matcher m = p.matcher(u.getId().trim());
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
        return "USR-" + (maxSeq + 1);
    }

    private void persistToFile(List<User> users) {
        ensureStorageDirectory();
        String json = serializeJson(users);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(storagePath.getParent(), "users-atomic-", ".tmp");
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);

            try {
                Files.move(tempFile, storagePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[UserRepository] Error persisting users to file: " + e.getMessage());
            throw new RuntimeException("Failed to persist user storage: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.delete(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }

    private String serializeJson(List<User> users) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(u.getId())).append("\",\n");
            sb.append("    \"fullName\": \"").append(escapeJson(u.getFullName())).append("\",\n");
            sb.append("    \"email\": \"").append(escapeJson(u.getEmail())).append("\",\n");
            sb.append("    \"username\": \"").append(escapeJson(u.getUsername())).append("\",\n");
            sb.append("    \"phone\": \"").append(escapeJson(u.getPhone())).append("\",\n");
            sb.append("    \"passwordHash\": \"").append(escapeJson(u.getPasswordHash())).append("\",\n");
            sb.append("    \"salt\": \"").append(escapeJson(u.getSalt())).append("\",\n");
            sb.append("    \"role\": \"").append(escapeJson(u.getRole())).append("\",\n");
            sb.append("    \"registeredAt\": \"").append(escapeJson(u.getRegisteredAt())).append("\",\n");
            sb.append("    \"linkedDonorId\": \"").append(escapeJson(u.getLinkedDonorId())).append("\",\n");
            sb.append("    \"purpose\": \"").append(escapeJson(u.getPurpose())).append("\"\n");
            sb.append("  }");
            if (i < users.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private List<User> parseJson(String json) {
        List<User> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String fullName = getStringProp(obj, "fullName");
            String email = getStringProp(obj, "email");
            String username = getStringProp(obj, "username");
            String phone = getStringProp(obj, "phone");
            String passwordHash = getStringProp(obj, "passwordHash");
            String salt = getStringProp(obj, "salt");
            String role = getStringProp(obj, "role");
            String registeredAt = getStringProp(obj, "registeredAt");
            String linkedDonorId = getStringProp(obj, "linkedDonorId");
            String purpose = getStringProp(obj, "purpose");

            if (!id.isEmpty() && (!email.isEmpty() || !username.isEmpty())) {
                User u = new User(id, fullName, email, username, phone,
                        passwordHash, salt, role, registeredAt, linkedDonorId, purpose);
                list.add(u);
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

package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.CampRegistration;

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
 * Thread-safe file repository for user camp registrations.
 */
public class CampRegistrationRepository {

    private static final CampRegistrationRepository INSTANCE = new CampRegistrationRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static CampRegistrationRepository getInstance() {
        return INSTANCE;
    }

    public CampRegistrationRepository() {
        this(Paths.get(AppConfig.CAMP_REGISTRATIONS_FILE));
    }

    public CampRegistrationRepository(Path storagePath) {
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
            System.err.println("[CampRegistrationRepository] Error creating storage directory: " + e.getMessage());
        }
    }

    public List<CampRegistration> getAllRegistrations() {
        lock.readLock().lock();
        try {
            if (!Files.exists(storagePath)) {
                return Collections.emptyList();
            }
            String content = Files.readString(storagePath, StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || content.equals("[]")) {
                return Collections.emptyList();
            }
            return parseJson(content);
        } catch (IOException e) {
            System.err.println("[CampRegistrationRepository] Error reading registrations: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<CampRegistration> findById(String id) {
        if (id == null || id.trim().isEmpty()) return Optional.empty();
        String cleanId = id.trim();
        for (CampRegistration r : getAllRegistrations()) {
            if (r.getId().equalsIgnoreCase(cleanId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public Optional<CampRegistration> findByCampAndUser(String campId, String userId) {
        if (campId == null || userId == null) return Optional.empty();
        for (CampRegistration r : getAllRegistrations()) {
            if (r.getCampId().equalsIgnoreCase(campId.trim()) &&
                    r.getUserId().equalsIgnoreCase(userId.trim()) &&
                    r.isConfirmed()) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public List<CampRegistration> findByUser(String userId) {
        if (userId == null) return Collections.emptyList();
        List<CampRegistration> list = new ArrayList<>();
        for (CampRegistration r : getAllRegistrations()) {
            if (r.getUserId().equalsIgnoreCase(userId.trim())) {
                list.add(r);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public int countConfirmedByCamp(String campId) {
        if (campId == null) return 0;
        int count = 0;
        for (CampRegistration r : getAllRegistrations()) {
            if (r.getCampId().equalsIgnoreCase(campId.trim()) && r.isConfirmed()) {
                count++;
            }
        }
        return count;
    }

    public CampRegistration save(CampRegistration reg) {
        if (reg == null || reg.getId().isEmpty()) {
            throw new IllegalArgumentException("Registration and ID must not be empty");
        }
        lock.writeLock().lock();
        try {
            List<CampRegistration> current = new ArrayList<>(getAllRegistrations());
            int idx = -1;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).getId().equalsIgnoreCase(reg.getId())) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                current.set(idx, reg);
            } else {
                current.add(reg);
            }
            writeRegistrationsToFile(current);
            return reg;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean cancelRegistration(String registrationId, String userId) {
        if (registrationId == null) return false;
        lock.writeLock().lock();
        try {
            List<CampRegistration> current = new ArrayList<>(getAllRegistrations());
            for (int i = 0; i < current.size(); i++) {
                CampRegistration r = current.get(i);
                if (r.getId().equalsIgnoreCase(registrationId.trim())) {
                    if (userId != null && !userId.isEmpty() && !r.getUserId().equalsIgnoreCase(userId.trim())) {
                        return false; // not owner
                    }
                    CampRegistration updated = new CampRegistration(
                            r.getId(), r.getCampId(), r.getUserId(), r.getUserName(),
                            r.getUserEmail(), r.getUserPhone(), r.getPreferredSlot(),
                            r.getBloodGroup(), r.getRegisteredAt(), "Cancelled"
                    );
                    current.set(i, updated);
                    writeRegistrationsToFile(current);
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String generateNextId() {
        lock.readLock().lock();
        try {
            int max = 5000;
            for (CampRegistration r : getAllRegistrations()) {
                String id = r.getId();
                if (id != null && id.startsWith("CREG-")) {
                    try {
                        int num = Integer.parseInt(id.substring(5));
                        if (num > max) max = num;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return "CREG-" + (max + 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void writeRegistrationsToFile(List<CampRegistration> list) {
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
            System.err.println("[CampRegistrationRepository] Error writing to file: " + e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to persist camp registrations", e);
        }
    }

    private String serializeJson(List<CampRegistration> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            CampRegistration r = list.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(r.getId())).append("\",\n");
            sb.append("    \"campId\": \"").append(escapeJson(r.getCampId())).append("\",\n");
            sb.append("    \"userId\": \"").append(escapeJson(r.getUserId())).append("\",\n");
            sb.append("    \"userName\": \"").append(escapeJson(r.getUserName())).append("\",\n");
            sb.append("    \"userEmail\": \"").append(escapeJson(r.getUserEmail())).append("\",\n");
            sb.append("    \"userPhone\": \"").append(escapeJson(r.getUserPhone())).append("\",\n");
            sb.append("    \"preferredSlot\": \"").append(escapeJson(r.getPreferredSlot())).append("\",\n");
            sb.append("    \"bloodGroup\": \"").append(escapeJson(r.getBloodGroup())).append("\",\n");
            sb.append("    \"registeredAt\": \"").append(escapeJson(r.getRegisteredAt())).append("\",\n");
            sb.append("    \"status\": \"").append(escapeJson(r.getStatus())).append("\"\n");
            sb.append("  }");
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private List<CampRegistration> parseJson(String json) {
        List<CampRegistration> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String campId = getStringProp(obj, "campId");
            String userId = getStringProp(obj, "userId");
            String userName = getStringProp(obj, "userName");
            String userEmail = getStringProp(obj, "userEmail");
            String userPhone = getStringProp(obj, "userPhone");
            String preferredSlot = getStringProp(obj, "preferredSlot");
            String bloodGroup = getStringProp(obj, "bloodGroup");
            String registeredAt = getStringProp(obj, "registeredAt");
            String status = getStringProp(obj, "status");

            if (!id.isEmpty() && !campId.isEmpty()) {
                list.add(new CampRegistration(id, campId, userId, userName, userEmail,
                        userPhone, preferredSlot, bloodGroup, registeredAt, status));
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
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}

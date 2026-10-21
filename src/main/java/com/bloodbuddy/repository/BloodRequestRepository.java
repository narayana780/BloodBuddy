package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodRequest;

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
 * File-based repository for persisting blood requests.
 * Uses atomic swap writes, UTF-8 encoding, and crash-resistant JSON serialization.
 */
public class BloodRequestRepository {

    private static final BloodRequestRepository INSTANCE = new BloodRequestRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static BloodRequestRepository getInstance() {
        return INSTANCE;
    }

    public BloodRequestRepository() {
        this(Paths.get(AppConfig.BLOOD_REQUESTS_FILE));
    }

    public BloodRequestRepository(Path storagePath) {
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
            System.err.println("[BloodRequestRepository] Error creating storage directory: " + e.getMessage());
        }
    }

    /**
     * Initializes default fictional demonstration requests if storage file is absent or empty.
     * All sample requests are explicitly flagged as demo records.
     */
    private void initializeDefaultSamplesIfEmpty() {
        lock.writeLock().lock();
        try {
            if (!Files.exists(storagePath) || Files.size(storagePath) == 0) {
                List<BloodRequest> samples = new ArrayList<>();
                samples.add(new BloodRequest(
                        "REQ-1001",
                        "Lakshmi Narayana (Demo Patient)",
                        "O+",
                        2,
                        "Government General Hospital",
                        "Danavaipeta, Rajahmundry",
                        "K. Varma (Volunteer Attendant)",
                        "+91 98480 00001",
                        "2026-10-15 14:00",
                        "Urgent",
                        "[Fictional Demo Request] Orthopedic surgery blood reserve requirement.",
                        "Pending",
                        "11 Oct 2026, 09:30 AM",
                        true
                ));
                samples.add(new BloodRequest(
                        "REQ-1002",
                        "Ramesh Babu (Demo Patient)",
                        "B-",
                        1,
                        "Bollineni Super Speciality Hospital",
                        "Morampudi, Rajahmundry",
                        "P. Sharma (Volunteer Attendant)",
                        "+91 98480 00002",
                        "2026-10-14 18:30",
                        "Critical",
                        "[Fictional Demo Request] Rare B- blood group requirement for ICU stabilization.",
                        "Pending",
                        "11 Oct 2026, 10:15 AM",
                        true
                ));
                samples.add(new BloodRequest(
                        "REQ-1003",
                        "Sowmya Devi (Demo Patient)",
                        "AB+",
                        3,
                        "Trust Multi-Speciality Hospital",
                        "Prakash Nagar, Rajahmundry",
                        "R. Kumar (Volunteer Attendant)",
                        "+91 98480 00003",
                        "2026-10-18 10:00",
                        "Normal",
                        "[Fictional Demo Request] Scheduled clinical transfusion requisition.",
                        "Pending",
                        "11 Oct 2026, 11:00 AM",
                        true
                ));
                writeRequestsToFile(samples);
            }
        } catch (IOException e) {
            System.err.println("[BloodRequestRepository] Failed checking/initializing sample requests: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<BloodRequest> getAllRequests() {
        lock.readLock().lock();
        try {
            return getAllRequestsInternal();
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<BloodRequest> getAllRequestsInternal() {
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
            System.err.println("[BloodRequestRepository] Failed to read requests from file: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Atomically allocates a sequential ID and persists a new BloodRequest under a single write lock.
     */
    public BloodRequest createAndSave(String patientName, String bloodGroup, int units,
                                      String hospitalName, String hospitalLocality,
                                      String contactPerson, String contactPhone,
                                      String requiredBy, String urgency, String notes,
                                      String status, String createdAt, boolean sample) {
        lock.writeLock().lock();
        try {
            List<BloodRequest> current = new ArrayList<>(getAllRequestsInternal());
            int maxNum = 1000;
            for (BloodRequest r : current) {
                String id = r.getId();
                if (id != null && id.startsWith("REQ-")) {
                    try {
                        int num = Integer.parseInt(id.substring(4));
                        if (num > maxNum) {
                            maxNum = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            String newId = "REQ-" + (maxNum + 1);
            BloodRequest req = new BloodRequest(newId, patientName, bloodGroup, units,
                    hospitalName, hospitalLocality, contactPerson, contactPhone,
                    requiredBy, urgency, notes, status, createdAt, sample);
            current.add(req);
            writeRequestsToFile(current);
            return req;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public BloodRequest save(BloodRequest request) {
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("Request and Request ID must not be null");
        }

        lock.writeLock().lock();
        try {
            List<BloodRequest> current = new ArrayList<>(getAllRequestsInternal());
            int existingIndex = -1;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).getId().equalsIgnoreCase(request.getId())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                current.set(existingIndex, request);
            } else {
                current.add(request);
            }

            writeRequestsToFile(current);
            return request;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<BloodRequest> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String searchId = id.trim();
        List<BloodRequest> all = getAllRequests();
        for (BloodRequest req : all) {
            if (req.getId().equalsIgnoreCase(searchId)) {
                return Optional.of(req);
            }
        }
        return Optional.empty();
    }

    /**
     * Updates status for a blood request by ID (e.g. Pending, In Progress, Fulfilled).
     */
    public boolean updateStatus(String requestId, String newStatus) {
        if (requestId == null || requestId.trim().isEmpty() || newStatus == null || newStatus.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            List<BloodRequest> current = new ArrayList<>(getAllRequestsInternal());
            int targetIndex = -1;
            BloodRequest target = null;

            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).getId().equalsIgnoreCase(requestId.trim())) {
                    targetIndex = i;
                    target = current.get(i);
                    break;
                }
            }

            if (targetIndex >= 0 && target != null) {
                BloodRequest updated = target.withStatus(newStatus.trim());
                current.set(targetIndex, updated);
                writeRequestsToFile(current);
                return true;
            }

            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String generateNextId() {
        lock.readLock().lock();
        try {
            List<BloodRequest> current = getAllRequests();
            int maxNum = 1000;
            for (BloodRequest r : current) {
                String id = r.getId();
                if (id != null && id.startsWith("REQ-")) {
                    try {
                        int num = Integer.parseInt(id.substring(4));
                        if (num > maxNum) {
                            maxNum = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return "REQ-" + (maxNum + 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void writeRequestsToFile(List<BloodRequest> list) {
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
            System.err.println("[BloodRequestRepository] Error writing requests to disk: " + e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to persist blood requests to " + storagePath, e);
        }
    }

    private String serializeJson(List<BloodRequest> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            BloodRequest r = list.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(r.getId())).append("\",\n");
            sb.append("    \"patientName\": \"").append(escapeJson(r.getPatientName())).append("\",\n");
            sb.append("    \"bloodGroup\": \"").append(escapeJson(r.getBloodGroup())).append("\",\n");
            sb.append("    \"units\": ").append(r.getUnits()).append(",\n");
            sb.append("    \"hospitalName\": \"").append(escapeJson(r.getHospitalName())).append("\",\n");
            sb.append("    \"hospitalLocality\": \"").append(escapeJson(r.getHospitalLocality())).append("\",\n");
            sb.append("    \"contactPerson\": \"").append(escapeJson(r.getContactPerson())).append("\",\n");
            sb.append("    \"contactPhone\": \"").append(escapeJson(r.getContactPhone())).append("\",\n");
            sb.append("    \"requiredBy\": \"").append(escapeJson(r.getRequiredBy())).append("\",\n");
            sb.append("    \"urgency\": \"").append(escapeJson(r.getUrgency())).append("\",\n");
            sb.append("    \"notes\": \"").append(escapeJson(r.getNotes())).append("\",\n");
            sb.append("    \"status\": \"").append(escapeJson(r.getStatus())).append("\",\n");
            sb.append("    \"createdAt\": \"").append(escapeJson(r.getCreatedAt())).append("\",\n");
            sb.append("    \"sample\": ").append(r.isSample()).append("\n");
            sb.append("  }");
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private List<BloodRequest> parseJson(String json) {
        List<BloodRequest> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String patientName = getStringProp(obj, "patientName");
            String bloodGroup = getStringProp(obj, "bloodGroup");
            int units = getIntProp(obj, "units", 1);
            String hospitalName = getStringProp(obj, "hospitalName");
            String hospitalLocality = getStringProp(obj, "hospitalLocality");
            String contactPerson = getStringProp(obj, "contactPerson");
            String contactPhone = getStringProp(obj, "contactPhone");
            String requiredBy = getStringProp(obj, "requiredBy");
            String urgency = getStringProp(obj, "urgency");
            String notes = getStringProp(obj, "notes");
            String status = getStringProp(obj, "status");
            String createdAt = getStringProp(obj, "createdAt");
            boolean sample = getBoolProp(obj, "sample", false);

            if (!id.isEmpty() && !patientName.isEmpty() && !bloodGroup.isEmpty()) {
                list.add(new BloodRequest(id, patientName, bloodGroup, units, hospitalName,
                        hospitalLocality, contactPerson, contactPhone, requiredBy, urgency,
                        notes, status, createdAt, sample));
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

    private static int getIntProp(String objJson, String key, int def) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(objJson);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
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

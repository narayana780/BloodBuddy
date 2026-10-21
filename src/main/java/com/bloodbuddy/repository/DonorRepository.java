package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.Donor;

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
 * File-based repository for persisting registered donors.
 * Stores records in a predictable JSON file outside target build outputs.
 * Provides thread-safe, atomic file writes, crash-resistant serialization,
 * and zero record duplication on server restarts.
 */
public class DonorRepository {

    private static final DonorRepository INSTANCE = new DonorRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static DonorRepository getInstance() {
        return INSTANCE;
    }

    public DonorRepository() {
        this(Paths.get(AppConfig.REGISTERED_DONORS_FILE));
    }

    public DonorRepository(Path storagePath) {
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
            System.err.println("[DonorRepository] Error creating storage directories: " + e.getMessage());
        }
    }

    /**
     * Reads all currently registered donors from local file storage.
     * Guaranteed to never return duplicates.
     */
    public List<Donor> getAllRegisteredDonors() {
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
            System.err.println("[DonorRepository] Failed to read registered donors from file: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Saves or updates a registered donor record.
     * Prevents duplicates by matching donor ID.
     */
    public Donor save(Donor donor) {
        if (donor == null || donor.getId() == null) {
            throw new IllegalArgumentException("Donor and Donor ID must not be null");
        }

        lock.writeLock().lock();
        try {
            List<Donor> currentDonors = new ArrayList<>(getAllRegisteredDonors());
            int existingIndex = -1;
            for (int i = 0; i < currentDonors.size(); i++) {
                if (currentDonors.get(i).getId().equalsIgnoreCase(donor.getId())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                currentDonors.set(existingIndex, donor);
            } else {
                currentDonors.add(donor);
            }

            writeDonorsToFile(currentDonors);
            return donor;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates availability status for a donor by ID.
     */
    public boolean updateAvailability(String donorId, boolean available, String statusText) {
        if (donorId == null || donorId.trim().isEmpty()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            List<Donor> currentDonors = new ArrayList<>(getAllRegisteredDonors());
            int targetIndex = -1;
            Donor target = null;

            for (int i = 0; i < currentDonors.size(); i++) {
                if (currentDonors.get(i).getId().equalsIgnoreCase(donorId.trim())) {
                    targetIndex = i;
                    target = currentDonors.get(i);
                    break;
                }
            }

            if (targetIndex >= 0 && target != null) {
                Donor updated = target.withAvailability(available, statusText);
                currentDonors.set(targetIndex, updated);
                writeDonorsToFile(currentDonors);
                return true;
            }

            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds a registered donor by ID.
     */
    public Optional<Donor> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        String searchId = id.trim();
        List<Donor> donors = getAllRegisteredDonors();
        for (Donor d : donors) {
            if (d.getId().equalsIgnoreCase(searchId)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    /**
     * Generates the next sequential unique donor registration ID (e.g. REG-1001, REG-1002).
     */
    public String generateNextId() {
        lock.readLock().lock();
        try {
            List<Donor> donors = getAllRegisteredDonors();
            int maxNum = 1000;
            for (Donor d : donors) {
                String id = d.getId();
                if (id != null && id.startsWith("REG-")) {
                    try {
                        int num = Integer.parseInt(id.substring(4));
                        if (num > maxNum) {
                            maxNum = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return "REG-" + (maxNum + 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Atomically writes donor list to disk using temporary file swap.
     */
    private void writeDonorsToFile(List<Donor> donors) {
        ensureStorageDirectory();
        Path tempPath = storagePath.resolveSibling(storagePath.getFileName().toString() + ".tmp");
        String json = serializeJson(donors);

        try {
            Files.writeString(tempPath, json, StandardCharsets.UTF_8);
            try {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[DonorRepository] Error writing donors to file: " + e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to persist donor records to " + storagePath, e);
        }
    }

    /**
     * Serializes a list of donors into JSON array format.
     */
    private String serializeJson(List<Donor> donors) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < donors.size(); i++) {
            Donor d = donors.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(d.getId())).append("\",\n");
            sb.append("    \"name\": \"").append(escapeJson(d.getName())).append("\",\n");
            sb.append("    \"bloodGroup\": \"").append(escapeJson(d.getBloodGroup())).append("\",\n");
            sb.append("    \"age\": ").append(d.getAge()).append(",\n");
            sb.append("    \"city\": \"").append(escapeJson(d.getCity())).append("\",\n");
            sb.append("    \"locality\": \"").append(escapeJson(d.getLocality())).append("\",\n");
            sb.append("    \"contactInfo\": \"").append(escapeJson(d.getContactInfo())).append("\",\n");
            sb.append("    \"lastDonated\": \"").append(escapeJson(d.getLastDonated())).append("\",\n");
            sb.append("    \"eligible\": ").append(d.isEligible()).append(",\n");
            sb.append("    \"badge\": \"").append(escapeJson(d.getBadge())).append("\",\n");
            sb.append("    \"availabilityStatus\": \"").append(escapeJson(d.getAvailabilityStatus())).append("\",\n");
            sb.append("    \"registered\": ").append(d.isRegistered()).append(",\n");
            sb.append("    \"registeredAt\": \"").append(escapeJson(d.getRegisteredAt())).append("\"\n");
            sb.append("  }");
            if (i < donors.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    /**
     * Robust, pure Java JSON parser without third-party dependencies.
     */
    private List<Donor> parseJson(String json) {
        List<Donor> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String name = getStringProp(obj, "name");
            String bloodGroup = getStringProp(obj, "bloodGroup");
            int age = getIntProp(obj, "age", 25);
            String city = getStringProp(obj, "city");
            String locality = getStringProp(obj, "locality");
            String contactInfo = getStringProp(obj, "contactInfo");
            String lastDonated = getStringProp(obj, "lastDonated");
            boolean eligible = getBoolProp(obj, "eligible", true);
            String badge = getStringProp(obj, "badge");
            String availabilityStatus = getStringProp(obj, "availabilityStatus");
            boolean registered = getBoolProp(obj, "registered", true);
            String registeredAt = getStringProp(obj, "registeredAt");

            if (!id.isEmpty() && !name.isEmpty() && !bloodGroup.isEmpty()) {
                Donor d = new Donor(id, name, bloodGroup, locality, lastDonated,
                        eligible, badge, availabilityStatus, age, city, contactInfo, registered, registeredAt);
                list.add(d);
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

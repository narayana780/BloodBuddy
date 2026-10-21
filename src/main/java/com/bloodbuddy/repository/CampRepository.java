package com.bloodbuddy.repository;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.Camp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thread-safe file-based repository for Blood Donation Camps.
 */
public class CampRepository {

    private static final CampRepository INSTANCE = new CampRepository();

    private final Path storagePath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static CampRepository getInstance() {
        return INSTANCE;
    }

    public CampRepository() {
        this(Paths.get(AppConfig.CAMPS_FILE));
    }

    public CampRepository(Path storagePath) {
        this.storagePath = storagePath;
        ensureStorageDirectory();
        initializeDefaultCampsIfEmpty();
    }

    private void ensureStorageDirectory() {
        try {
            Path parent = storagePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            System.err.println("[CampRepository] Error creating storage directory: " + e.getMessage());
        }
    }

    private void initializeDefaultCampsIfEmpty() {
        lock.writeLock().lock();
        try {
            if (Files.exists(storagePath)) {
                String existing = Files.readString(storagePath, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty() && !existing.equals("[]")) {
                    return;
                }
            }

            // Create initial community camps in Rajahmundry
            LocalDate now = LocalDate.now();
            String upcomingDate1 = now.plusDays(4).format(DateTimeFormatter.ISO_LOCAL_DATE);
            String upcomingDate2 = now.plusDays(11).format(DateTimeFormatter.ISO_LOCAL_DATE);
            String upcomingDate3 = now.plusDays(18).format(DateTimeFormatter.ISO_LOCAL_DATE);
            String pastDate = now.minusDays(10).format(DateTimeFormatter.ISO_LOCAL_DATE);

            List<Camp> defaults = new ArrayList<>();
            defaults.add(new Camp(
                    "CAMP-101",
                    "Rajahmundry Rotary Community Blood Drive",
                    "Rotary Club of Rajahmundry & District Blood Centre",
                    "0883-2471200",
                    upcomingDate1,
                    "09:00 AM",
                    "03:30 PM",
                    "Anam Kala Kendram Community Auditorium",
                    "Subbarao Street, Near Kotipalli Bus Stand, Rajahmundry",
                    "Rajahmundry",
                    "Kotipalli Bus Stand",
                    "Upcoming",
                    "Age 18-65 years, weight above 45kg, normal haemoglobin (>=12.5 g/dL). Please bring government photo ID.",
                    120,
                    160,
                    true,
                    "Nutritious refreshments, certified blood group card, and certificate of appreciation provided.",
                    "2026-10-01"
            ));

            defaults.add(new Camp(
                    "CAMP-102",
                    "Youth Voluntary Lifesaver Camp",
                    "Red Cross Society & Government General Hospital",
                    "0883-2462345",
                    upcomingDate2,
                    "08:30 AM",
                    "02:00 PM",
                    "Danavaipeta Municipal High School Grounds",
                    "Ward 12, Main Road, Danavaipeta, Rajahmundry",
                    "Rajahmundry",
                    "Danavaipeta",
                    "Upcoming",
                    "First-time donors warmly welcome. Pre-donation health screening and haemoglobin test conducted on-site.",
                    100,
                    140,
                    true,
                    "Air-conditioned mobile donation unit on-site. Light meal recommended 2 hours before donation.",
                    "2026-10-02"
            ));

            defaults.add(new Camp(
                    "CAMP-103",
                    "Godavari Regional Healthcare Blood Donation Mission",
                    "Apollo Hospitals Community Outreach Wing",
                    "0883-2489000",
                    upcomingDate3,
                    "09:30 AM",
                    "04:00 PM",
                    "Prakash Nagar Community Welfare Hall",
                    "Opposite RTC Complex, Prakash Nagar, Rajahmundry",
                    "Rajahmundry",
                    "Prakash Nagar",
                    "Upcoming",
                    "Voluntary donors of all blood groups. Universal donor groups (O-, O+) strongly encouraged.",
                    150,
                    200,
                    true,
                    "Fully equipped clinical team with physician supervision and emergency support.",
                    "2026-10-03"
            ));

            defaults.add(new Camp(
                    "CAMP-104",
                    "Morampudi Industrial Zone Lifesaver Drive",
                    "Lions Club of Rajahmundry City",
                    "0883-2495555",
                    pastDate,
                    "09:00 AM",
                    "01:30 PM",
                    "Morampudi Community Centre",
                    "Near Bypass Junction, Morampudi, Rajahmundry",
                    "Rajahmundry",
                    "Morampudi",
                    "Completed",
                    "Standard clinical blood donation eligibility criteria applied.",
                    80,
                    100,
                    false,
                    "Donation drive successfully completed. Total 86 units collected.",
                    "2026-09-25"
            ));

            writeCampsToFile(defaults);
        } catch (Exception e) {
            System.err.println("[CampRepository] Failed to seed default camps: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Camp> findAll() {
        return getAllCamps();
    }

    public List<Camp> getAllCamps() {
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
            System.err.println("[CampRepository] Error reading camps from file: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<Camp> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String cleanId = id.trim();
        for (Camp c : getAllCamps()) {
            if (c.getId().equalsIgnoreCase(cleanId)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public Camp save(Camp camp) {
        if (camp == null || camp.getId().isEmpty()) {
            throw new IllegalArgumentException("Camp and Camp ID must not be empty");
        }
        lock.writeLock().lock();
        try {
            List<Camp> current = new ArrayList<>(getAllCamps());
            int idx = -1;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).getId().equalsIgnoreCase(camp.getId())) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                current.set(idx, camp);
            } else {
                current.add(camp);
            }
            writeCampsToFile(current);
            return camp;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean updateStatus(String campId, String newStatus) {
        if (campId == null || newStatus == null) return false;
        lock.writeLock().lock();
        try {
            List<Camp> current = new ArrayList<>(getAllCamps());
            for (int i = 0; i < current.size(); i++) {
                Camp c = current.get(i);
                if (c.getId().equalsIgnoreCase(campId.trim())) {
                    boolean regOpen = c.isRegistrationOpen() && !"Cancelled".equalsIgnoreCase(newStatus) && !"Completed".equalsIgnoreCase(newStatus);
                    Camp updated = new Camp(
                            c.getId(), c.getName(), c.getOrganizer(), c.getOrganizerContact(),
                            c.getDate(), c.getStartTime(), c.getEndTime(), c.getVenue(),
                            c.getAddress(), c.getCity(), c.getLocality(), newStatus,
                            c.getEligibilityInfo(), c.getTargetUnits(), c.getMaxCapacity(),
                            regOpen, c.getNotes(), c.getCreatedAt()
                    );
                    current.set(i, updated);
                    writeCampsToFile(current);
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean delete(String id) {
        if (id == null) return false;
        lock.writeLock().lock();
        try {
            List<Camp> current = new ArrayList<>(getAllCamps());
            boolean removed = current.removeIf(c -> c.getId().equalsIgnoreCase(id.trim()));
            if (removed) {
                writeCampsToFile(current);
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String generateNextId() {
        lock.readLock().lock();
        try {
            int max = 100;
            for (Camp c : getAllCamps()) {
                String id = c.getId();
                if (id != null && id.startsWith("CAMP-")) {
                    try {
                        int num = Integer.parseInt(id.substring(5));
                        if (num > max) max = num;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return "CAMP-" + (max + 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void writeCampsToFile(List<Camp> camps) {
        ensureStorageDirectory();
        Path tempPath = storagePath.resolveSibling(storagePath.getFileName().toString() + ".tmp");
        String json = serializeJson(camps);

        try {
            Files.writeString(tempPath, json, StandardCharsets.UTF_8);
            try {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[CampRepository] Error writing camps to file: " + e.getMessage());
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
            throw new RuntimeException("Failed to persist camps to " + storagePath, e);
        }
    }

    private String serializeJson(List<Camp> camps) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < camps.size(); i++) {
            Camp c = camps.get(i);
            sb.append("  {\n");
            sb.append("    \"id\": \"").append(escapeJson(c.getId())).append("\",\n");
            sb.append("    \"name\": \"").append(escapeJson(c.getName())).append("\",\n");
            sb.append("    \"organizer\": \"").append(escapeJson(c.getOrganizer())).append("\",\n");
            sb.append("    \"organizerContact\": \"").append(escapeJson(c.getOrganizerContact())).append("\",\n");
            sb.append("    \"date\": \"").append(escapeJson(c.getDate())).append("\",\n");
            sb.append("    \"startTime\": \"").append(escapeJson(c.getStartTime())).append("\",\n");
            sb.append("    \"endTime\": \"").append(escapeJson(c.getEndTime())).append("\",\n");
            sb.append("    \"venue\": \"").append(escapeJson(c.getVenue())).append("\",\n");
            sb.append("    \"address\": \"").append(escapeJson(c.getAddress())).append("\",\n");
            sb.append("    \"city\": \"").append(escapeJson(c.getCity())).append("\",\n");
            sb.append("    \"locality\": \"").append(escapeJson(c.getLocality())).append("\",\n");
            sb.append("    \"status\": \"").append(escapeJson(c.getStatus())).append("\",\n");
            sb.append("    \"eligibilityInfo\": \"").append(escapeJson(c.getEligibilityInfo())).append("\",\n");
            sb.append("    \"targetUnits\": ").append(c.getTargetUnits()).append(",\n");
            sb.append("    \"maxCapacity\": ").append(c.getMaxCapacity()).append(",\n");
            sb.append("    \"registrationOpen\": ").append(c.isRegistrationOpen()).append(",\n");
            sb.append("    \"notes\": \"").append(escapeJson(c.getNotes())).append("\",\n");
            sb.append("    \"createdAt\": \"").append(escapeJson(c.getCreatedAt())).append("\"\n");
            sb.append("  }");
            if (i < camps.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private List<Camp> parseJson(String json) {
        List<Camp> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher m = objectPattern.matcher(json);

        while (m.find()) {
            String obj = m.group();
            String id = getStringProp(obj, "id");
            String name = getStringProp(obj, "name");
            String organizer = getStringProp(obj, "organizer");
            String organizerContact = getStringProp(obj, "organizerContact");
            String date = getStringProp(obj, "date");
            String startTime = getStringProp(obj, "startTime");
            String endTime = getStringProp(obj, "endTime");
            String venue = getStringProp(obj, "venue");
            String address = getStringProp(obj, "address");
            String city = getStringProp(obj, "city");
            String locality = getStringProp(obj, "locality");
            String status = getStringProp(obj, "status");
            String eligibilityInfo = getStringProp(obj, "eligibilityInfo");
            int targetUnits = getIntProp(obj, "targetUnits", 50);
            int maxCapacity = getIntProp(obj, "maxCapacity", 100);
            boolean registrationOpen = getBoolProp(obj, "registrationOpen", true);
            String notes = getStringProp(obj, "notes");
            String createdAt = getStringProp(obj, "createdAt");

            if (!id.isEmpty() && !name.isEmpty()) {
                list.add(new Camp(id, name, organizer, organizerContact, date, startTime, endTime,
                        venue, address, city, locality, status, eligibilityInfo, targetUnits, maxCapacity,
                        registrationOpen, notes, createdAt));
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

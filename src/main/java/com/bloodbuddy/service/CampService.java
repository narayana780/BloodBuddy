package com.bloodbuddy.service;

import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.Camp;
import com.bloodbuddy.model.CampRegistration;
import com.bloodbuddy.model.User;
import com.bloodbuddy.repository.CampRegistrationRepository;
import com.bloodbuddy.repository.CampRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing Blood Donation Camps, search and filtering,
 * summary metrics, and user registrations.
 */
public class CampService {

    private final CampRepository campRepository;
    private final CampRegistrationRepository registrationRepository;
    private final NotificationService notificationService;

    public CampService() {
        this(CampRepository.getInstance(), CampRegistrationRepository.getInstance(), new NotificationService());
    }

    public CampService(CampRepository campRepository,
                       CampRegistrationRepository registrationRepository,
                       NotificationService notificationService) {
        this.campRepository = campRepository;
        this.registrationRepository = registrationRepository;
        this.notificationService = notificationService;
    }

    public List<Camp> getAllCamps() {
        return campRepository.getAllCamps();
    }

    public Optional<Camp> getCampById(String id) {
        return campRepository.findById(id);
    }

    /**
     * Filters camps by keyword, city, locality, status, and date range.
     */
    public List<Camp> searchCamps(String query, String city, String locality, String status, String dateFilter) {
        return searchCamps(query, city, locality, status, dateFilter, null, null);
    }

    /**
     * Filters camps by keyword, city, locality, status, date presets, and date range (from/to).
     */
    public List<Camp> searchCamps(String query, String city, String locality, String status, String dateFilter, String dateFrom, String dateTo) {
        List<Camp> all = campRepository.getAllCamps();
        List<Camp> results = new ArrayList<>();

        String cleanQuery = (query != null && !query.trim().isEmpty()) ? query.trim().toLowerCase() : null;
        String cleanCity = (city != null && !city.trim().isEmpty() && !"ALL".equalsIgnoreCase(city.trim()))
                ? city.trim().toLowerCase() : null;
        String cleanLocality = (locality != null && !locality.trim().isEmpty() && !"ALL".equalsIgnoreCase(locality.trim()))
                ? locality.trim().toLowerCase() : null;
        String cleanStatus = (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim()))
                ? status.trim().toLowerCase() : null;
        String cleanDate = (dateFilter != null && !dateFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(dateFilter.trim()))
                ? dateFilter.trim().toLowerCase() : null;

        LocalDate fromDate = null;
        if (dateFrom != null && !dateFrom.trim().isEmpty()) {
            try {
                fromDate = LocalDate.parse(dateFrom.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }
        LocalDate toDate = null;
        if (dateTo != null && !dateTo.trim().isEmpty()) {
            try {
                toDate = LocalDate.parse(dateTo.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }

        LocalDate today = LocalDate.now();

        for (Camp c : all) {
            // 1. Text Search (name, organizer, venue, address)
            if (cleanQuery != null) {
                boolean matchesText = c.getName().toLowerCase().contains(cleanQuery) ||
                        c.getOrganizer().toLowerCase().contains(cleanQuery) ||
                        c.getVenue().toLowerCase().contains(cleanQuery) ||
                        c.getAddress().toLowerCase().contains(cleanQuery);
                if (!matchesText) continue;
            }

            // 2. City Filter
            if (cleanCity != null) {
                if (!c.getCity().toLowerCase().contains(cleanCity)) {
                    continue;
                }
            }

            // 3. Locality Filter
            if (cleanLocality != null) {
                if (!c.getLocality().toLowerCase().equalsIgnoreCase(cleanLocality)) {
                    continue;
                }
            }

            // 4. Status Filter (matches computed real-time status)
            if (cleanStatus != null) {
                String comp = c.getComputedStatus().toLowerCase();
                if (!comp.equalsIgnoreCase(cleanStatus)) {
                    continue;
                }
            }

            // 5. Date Filter (e.g. "this_month", "upcoming", "past", or YYYY-MM-DD)
            LocalDate cDate = null;
            try {
                cDate = LocalDate.parse(c.getDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
            }

            if (cDate != null) {
                if (fromDate != null && cDate.isBefore(fromDate)) {
                    continue;
                }
                if (toDate != null && cDate.isAfter(toDate)) {
                    continue;
                }

                if (cleanDate != null) {
                    if ("this_month".equals(cleanDate)) {
                        if (cDate.getYear() != today.getYear() || cDate.getMonth() != today.getMonth()) {
                            continue;
                        }
                    } else if ("upcoming".equals(cleanDate)) {
                        if (cDate.isBefore(today)) {
                            continue;
                        }
                    } else if ("past".equals(cleanDate)) {
                        if (!cDate.isBefore(today)) {
                            continue;
                        }
                    } else {
                        try {
                            LocalDate filterD = LocalDate.parse(cleanDate, DateTimeFormatter.ISO_LOCAL_DATE);
                            if (!cDate.isEqual(filterD)) {
                                continue;
                            }
                        } catch (DateTimeParseException ignored) {
                        }
                    }
                }
            }

            results.add(c);
        }

        // Sort: Upcoming / Ongoing first by date ascending, then Completed by date descending
        results.sort((c1, c2) -> {
            boolean active1 = !"Completed".equalsIgnoreCase(c1.getComputedStatus()) && !"Cancelled".equalsIgnoreCase(c1.getComputedStatus());
            boolean active2 = !"Completed".equalsIgnoreCase(c2.getComputedStatus()) && !"Cancelled".equalsIgnoreCase(c2.getComputedStatus());
            if (active1 != active2) {
                return active1 ? -1 : 1;
            }
            return c1.getDate().compareTo(c2.getDate());
        });

        return Collections.unmodifiableList(results);
    }

    /**
     * Number of upcoming or ongoing camps.
     */
    public int getUpcomingCampsCount() {
        int count = 0;
        for (Camp c : campRepository.getAllCamps()) {
            String s = c.getComputedStatus();
            if ("Upcoming".equalsIgnoreCase(s) || "Ongoing".equalsIgnoreCase(s)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Number of camps scheduled in the current month.
     */
    public int getThisMonthCampsCount() {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Camp c : campRepository.getAllCamps()) {
            if ("Cancelled".equalsIgnoreCase(c.getStatus())) {
                continue;
            }
            try {
                LocalDate d = LocalDate.parse(c.getDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                if (d.getYear() == today.getYear() && d.getMonth() == today.getMonth()) {
                    count++;
                }
            } catch (DateTimeParseException ignored) {
            }
        }
        return count;
    }

    /**
     * Number of unique active localities hosting upcoming camps.
     */
    public int getUniqueActiveLocalitiesCount() {
        Set<String> set = new HashSet<>();
        for (Camp c : campRepository.getAllCamps()) {
            String s = c.getComputedStatus();
            if ("Upcoming".equalsIgnoreCase(s) || "Ongoing".equalsIgnoreCase(s)) {
                if (!c.getLocality().isEmpty()) {
                    set.add(c.getLocality().toLowerCase());
                }
            }
        }
        return set.size();
    }

    /**
     * Registers a logged-in user for a camp.
     */
    public RegistrationResult registerUserForCamp(String campId, User user, String preferredSlot, String bloodGroup) {
        if (user == null) {
            return new RegistrationResult(false, "You must be signed in to register for a blood donation camp.", null);
        }
        if (campId == null || campId.trim().isEmpty()) {
            return new RegistrationResult(false, "Invalid camp identifier.", null);
        }

        Optional<Camp> campOpt = campRepository.findById(campId.trim());
        if (campOpt.isEmpty()) {
            return new RegistrationResult(false, "Camp not found or has been removed.", null);
        }

        Camp camp = campOpt.get();

        // 1. Check if registration is open
        if (!camp.isRegistrationOpen()) {
            String status = camp.getComputedStatus();
            if ("Completed".equalsIgnoreCase(status)) {
                return new RegistrationResult(false, "Registration is closed because this camp has already concluded.", null);
            } else if ("Cancelled".equalsIgnoreCase(status)) {
                return new RegistrationResult(false, "Registration is unavailable because this camp was cancelled.", null);
            } else {
                return new RegistrationResult(false, "Registration for this camp is currently closed by the organizer.", null);
            }
        }

        // 2. Check for duplicate registration
        Optional<CampRegistration> existing = registrationRepository.findByCampAndUser(camp.getId(), user.getId());
        if (existing.isPresent()) {
            return new RegistrationResult(false, "You are already registered for this blood donation camp (Registration ID: " + existing.get().getId() + ").", existing.get());
        }

        // 3. Check capacity limit if set
        if (camp.getMaxCapacity() > 0) {
            int currentCount = registrationRepository.countConfirmedByCamp(camp.getId());
            if (currentCount >= camp.getMaxCapacity()) {
                return new RegistrationResult(false, "This camp has reached its maximum registration capacity (" + camp.getMaxCapacity() + " donors).", null);
            }
        }

        // 4. Create and persist registration
        String regId = registrationRepository.generateNextId();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
        String finalGroup = (bloodGroup != null && !bloodGroup.trim().isEmpty()) ? bloodGroup.trim() : "";

        CampRegistration reg = new CampRegistration(
                regId,
                camp.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                preferredSlot,
                finalGroup,
                timestamp,
                "Confirmed"
        );

        CampRegistration saved = registrationRepository.save(reg);

        // Record user notification
        try {
            notificationService.recordNotification(
                    "📅 Camp Registration Confirmed",
                    "You have successfully registered for \"" + camp.getName() + "\" on " + camp.getFormattedDate() + " at " + camp.getVenue() + ". Your Registration ID is " + regId + ".",
                    "Camp",
                    "/camp-details?id=" + camp.getId()
            );
        } catch (Exception ignored) {
        }

        return new RegistrationResult(true, "Your registration for \"" + camp.getName() + "\" has been confirmed!", saved);
    }

    public Optional<CampRegistration> getUserRegistration(String campId, String userId) {
        if (campId == null || userId == null) return Optional.empty();
        return registrationRepository.findByCampAndUser(campId, userId);
    }

    public int getConfirmedCount(String campId) {
        return registrationRepository.countConfirmedByCamp(campId);
    }

    public boolean cancelRegistration(String regId, String userId) {
        return registrationRepository.cancelRegistration(regId, userId);
    }

    public Camp createCamp(String name, String organizer, String organizerContact,
                           String date, String startTime, String endTime, String venue,
                           String address, String city, String locality,
                           String eligibilityInfo, int targetUnits, int maxCapacity,
                           boolean registrationOpen, String notes) {

        String id = campRepository.generateNextId();
        String createdAt = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        Camp camp = new Camp(
                id, name, organizer, organizerContact, date, startTime, endTime,
                venue, address, city, locality, "Upcoming", eligibilityInfo,
                targetUnits, maxCapacity, registrationOpen, notes, createdAt
        );

        return campRepository.save(camp);
    }

    public boolean updateCampStatus(String campId, String newStatus) {
        return campRepository.updateStatus(campId, newStatus);
    }

    public static class RegistrationResult {
        private final boolean success;
        private final String message;
        private final CampRegistration registration;

        public RegistrationResult(boolean success, String message, CampRegistration registration) {
            this.success = success;
            this.message = message;
            this.registration = registration;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public CampRegistration getRegistration() {
            return registration;
        }
    }
}

package com.bloodbuddy.service;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.DonorMatch;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Service managing smart emergency prioritization, deadline monitoring,
 * and automated alert triggers (Stage 9).
 *
 * Prioritizes active hospital blood requisitions based on clinical urgency,
 * deadline proximity (overdue, due soon), match availability, and logged timestamps.
 * Prioritization is a calculated analytical ranking and does not silently alter
 * source records or make automated medical decisions.
 */
public class EmergencyPrioritizationService {

    private final BloodRequestService requestService;
    private final DonorMatchingService matchingService;
    private final NotificationService notificationService;

    // Standard timestamp formatters for required-by and created-at values
    private static final DateTimeFormatter FMT_REQUIRED_BY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_CREATED_AT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM yyyy, h:m a")
            .toFormatter(Locale.ENGLISH);

    public EmergencyPrioritizationService() {
        this(new BloodRequestService(), new DonorMatchingService(), new NotificationService());
    }

    public EmergencyPrioritizationService(BloodRequestService requestService,
                                         DonorMatchingService matchingService,
                                         NotificationService notificationService) {
        this.requestService = requestService;
        this.matchingService = matchingService;
        this.notificationService = notificationService;
    }

    /**
     * DTO representing an active blood request evaluated with emergency priority metrics.
     */
    public static class EmergencyRequestItem {
        private final BloodRequest request;
        private final int matchCount;
        private final boolean hasNoMatches;
        private final boolean overdue;
        private final boolean dueSoon;
        private final boolean deadlineUnavailable;
        private final LocalDateTime requiredByTime;
        private final LocalDateTime createdAtTime;
        private final String timeRemainingText;
        private final long minutesRemaining;
        private final int priorityScore;
        private final List<String> highlightTags;
        private final String reasonForPrioritization;

        public EmergencyRequestItem(BloodRequest request, int matchCount, boolean hasNoMatches,
                                    boolean overdue, boolean dueSoon, boolean deadlineUnavailable,
                                    LocalDateTime requiredByTime, LocalDateTime createdAtTime,
                                    String timeRemainingText, long minutesRemaining,
                                    int priorityScore, List<String> highlightTags,
                                    String reasonForPrioritization) {
            this.request = request;
            this.matchCount = matchCount;
            this.hasNoMatches = hasNoMatches;
            this.overdue = overdue;
            this.dueSoon = dueSoon;
            this.deadlineUnavailable = deadlineUnavailable;
            this.requiredByTime = requiredByTime;
            this.createdAtTime = createdAtTime;
            this.timeRemainingText = timeRemainingText;
            this.minutesRemaining = minutesRemaining;
            this.priorityScore = priorityScore;
            this.highlightTags = Collections.unmodifiableList(highlightTags);
            this.reasonForPrioritization = reasonForPrioritization;
        }

        public BloodRequest getRequest() { return request; }
        public int getMatchCount() { return matchCount; }
        public boolean isHasNoMatches() { return hasNoMatches; }
        public boolean isOverdue() { return overdue; }
        public boolean isDueSoon() { return dueSoon; }
        public boolean isDeadlineUnavailable() { return deadlineUnavailable; }
        public LocalDateTime getRequiredByTime() { return requiredByTime; }
        public LocalDateTime getCreatedAtTime() { return createdAtTime; }
        public String getTimeRemainingText() { return timeRemainingText; }
        public long getMinutesRemaining() { return minutesRemaining; }
        public int getPriorityScore() { return priorityScore; }
        public List<String> getHighlightTags() { return highlightTags; }
        public String getReasonForPrioritization() { return reasonForPrioritization; }
    }

    /**
     * Aggregated summary holding emergency card metrics and prioritized request items.
     */
    public static class EmergencySummary {
        private final int criticalActiveCount;
        private final int urgentActiveCount;
        private final int normalActiveCount;
        private final int overdueCount;
        private final int dueSoonCount;
        private final int noMatchesCount;
        private final int awaitingReviewCount;
        private final int totalActiveCount;
        private final List<EmergencyRequestItem> prioritizedItems;

        public EmergencySummary(int criticalActiveCount, int urgentActiveCount, int normalActiveCount,
                                int overdueCount, int dueSoonCount, int noMatchesCount,
                                int awaitingReviewCount, int totalActiveCount,
                                List<EmergencyRequestItem> prioritizedItems) {
            this.criticalActiveCount = criticalActiveCount;
            this.urgentActiveCount = urgentActiveCount;
            this.normalActiveCount = normalActiveCount;
            this.overdueCount = overdueCount;
            this.dueSoonCount = dueSoonCount;
            this.noMatchesCount = noMatchesCount;
            this.awaitingReviewCount = awaitingReviewCount;
            this.totalActiveCount = totalActiveCount;
            this.prioritizedItems = Collections.unmodifiableList(prioritizedItems);
        }

        public int getCriticalActiveCount() { return criticalActiveCount; }
        public int getUrgentActiveCount() { return urgentActiveCount; }
        public int getNormalActiveCount() { return normalActiveCount; }
        public int getOverdueCount() { return overdueCount; }
        public int getDueSoonCount() { return dueSoonCount; }
        public int getNoMatchesCount() { return noMatchesCount; }
        public int getAwaitingReviewCount() { return awaitingReviewCount; }
        public int getTotalActiveCount() { return totalActiveCount; }
        public List<EmergencyRequestItem> getPrioritizedItems() { return prioritizedItems; }
    }

    /**
     * Filter parameters for the Emergency Control Center request table.
     */
    public static class EmergencyFilter {
        private String urgency = "ALL";
        private String deadlineStatus = "ALL"; // ALL, OVERDUE, DUE_SOON, ON_SCHEDULE
        private String bloodGroup = "ALL";
        private String locality = "ALL";
        private String matchAvailability = "ALL"; // ALL, HAS_MATCHES, NO_MATCHES

        public String getUrgency() { return urgency; }
        public void setUrgency(String urgency) { this.urgency = (urgency != null && !urgency.trim().isEmpty()) ? urgency.trim() : "ALL"; }

        public String getDeadlineStatus() { return deadlineStatus; }
        public void setDeadlineStatus(String deadlineStatus) { this.deadlineStatus = (deadlineStatus != null && !deadlineStatus.trim().isEmpty()) ? deadlineStatus.trim() : "ALL"; }

        public String getBloodGroup() { return bloodGroup; }
        public void setBloodGroup(String bloodGroup) { this.bloodGroup = (bloodGroup != null && !bloodGroup.trim().isEmpty()) ? bloodGroup.trim() : "ALL"; }

        public String getLocality() { return locality; }
        public void setLocality(String locality) { this.locality = (locality != null && !locality.trim().isEmpty()) ? locality.trim() : "ALL"; }

        public String getMatchAvailability() { return matchAvailability; }
        public void setMatchAvailability(String matchAvailability) { this.matchAvailability = (matchAvailability != null && !matchAvailability.trim().isEmpty()) ? matchAvailability.trim() : "ALL"; }
    }

    /**
     * Computes the complete emergency summary using current system storage and server clock.
     */
    public EmergencySummary computeEmergencySummary() {
        return computeEmergencySummary(requestService.getAllRequests(), null, LocalDateTime.now(), AppConfig.getDueSoonHoursThreshold());
    }

    /**
     * Computes emergency summary with explicit parameters for testing and isolated simulations.
     */
    public EmergencySummary computeEmergencySummary(List<BloodRequest> allRequests,
                                                    List<Donor> explicitDonorPool,
                                                    LocalDateTime referenceTime,
                                                    int dueSoonHoursThreshold) {
        if (allRequests == null) {
            allRequests = Collections.emptyList();
        }
        if (referenceTime == null) {
            referenceTime = LocalDateTime.now();
        }
        if (dueSoonHoursThreshold <= 0) {
            dueSoonHoursThreshold = AppConfig.DEFAULT_DUE_SOON_HOURS_THRESHOLD;
        }

        int criticalCount = 0;
        int urgentCount = 0;
        int normalCount = 0;
        int overdueCount = 0;
        int dueSoonCount = 0;
        int noMatchesCount = 0;
        int awaitingReviewCount = 0;
        int totalActiveCount = 0;

        List<EmergencyRequestItem> items = new ArrayList<>();

        for (BloodRequest r : allRequests) {
            if (r == null) continue;

            // Only evaluate active requests (exclude Completed, Fulfilled, Cancelled)
            if (!isActiveRequest(r)) {
                continue;
            }

            totalActiveCount++;

            String urgency = r.getUrgency() != null ? r.getUrgency().trim() : "Normal";
            if ("Critical".equalsIgnoreCase(urgency)) {
                criticalCount++;
            } else if ("Urgent".equalsIgnoreCase(urgency)) {
                urgentCount++;
            } else {
                normalCount++;
            }

            // Awaiting administrator review (Pending status)
            if ("Pending".equalsIgnoreCase(r.getStatus())) {
                awaitingReviewCount++;
            }

            // Deadline evaluation
            LocalDateTime reqTime = parseRequiredBy(r.getRequiredBy());
            boolean isDeadlineUnavailable = (reqTime == null);
            boolean isOverdue = false;
            boolean isDueSoon = false;
            long minutesRemaining = Long.MAX_VALUE;
            String timeText;

            if (isDeadlineUnavailable) {
                timeText = "Deadline unavailable";
            } else {
                if (referenceTime.isAfter(reqTime)) {
                    isOverdue = true;
                    overdueCount++;
                    long diffMins = Duration.between(reqTime, referenceTime).toMinutes();
                    minutesRemaining = -diffMins;
                    timeText = formatOverdueDuration(diffMins);
                } else {
                    long diffMins = Duration.between(referenceTime, reqTime).toMinutes();
                    minutesRemaining = diffMins;
                    long dueSoonMins = dueSoonHoursThreshold * 60L;
                    if (diffMins <= dueSoonMins) {
                        isDueSoon = true;
                        dueSoonCount++;
                        timeText = formatRemainingDuration(diffMins, true);
                    } else {
                        timeText = formatRemainingDuration(diffMins, false);
                    }
                }
            }

            // Match evaluation
            List<DonorMatch> matches = (explicitDonorPool != null)
                    ? matchingService.matchDonors(r, explicitDonorPool)
                    : matchingService.findMatches(r);

            int matchCount = matches != null ? matches.size() : 0;
            boolean hasNoMatches = (matchCount == 0);
            if (hasNoMatches) {
                noMatchesCount++;
            }

            LocalDateTime createdTime = parseCreatedAt(r.getCreatedAt());

            // Calculate Priority Score & Highlight Tags
            List<String> tags = new ArrayList<>();
            int score = calculatePriorityScore(urgency, isOverdue, isDueSoon, hasNoMatches);

            if (isOverdue) tags.add("OVERDUE");
            if ("Critical".equalsIgnoreCase(urgency)) tags.add("CRITICAL");
            if (isDueSoon) tags.add("DUE_SOON");
            if (hasNoMatches) tags.add("NO_MATCHES");
            if ("Urgent".equalsIgnoreCase(urgency)) tags.add("URGENT");

            String reason = generateReason(urgency, isOverdue, isDueSoon, hasNoMatches, matchCount, timeText);

            items.add(new EmergencyRequestItem(
                    r, matchCount, hasNoMatches, isOverdue, isDueSoon, isDeadlineUnavailable,
                    reqTime, createdTime, timeText, minutesRemaining, score, tags, reason
            ));
        }

        // Sort items using Section 3 prioritization rules
        items.sort(createEmergencyComparator());

        return new EmergencySummary(
                criticalCount, urgentCount, normalCount, overdueCount, dueSoonCount,
                noMatchesCount, awaitingReviewCount, totalActiveCount, items
        );
    }

    /**
     * Transparent priority scoring hierarchy:
     * Critical (3000) > Urgent (2000) > Normal (1000).
     * Overdue (+500) > Due Soon (+300) > On Schedule (+100).
     * No Matches (+50).
     */
    public static int calculatePriorityScore(String urgency, boolean overdue, boolean dueSoon, boolean noMatches) {
        int score = 0;
        if ("Critical".equalsIgnoreCase(urgency)) {
            score += 3000;
        } else if ("Urgent".equalsIgnoreCase(urgency)) {
            score += 2000;
        } else {
            score += 1000;
        }

        if (overdue) {
            score += 500;
        } else if (dueSoon) {
            score += 300;
        } else {
            score += 100;
        }

        if (noMatches) {
            score += 50;
        }

        return score;
    }

    /**
     * Comparator enforcing Section 3 ranking rules:
     * 1. Priority score descending (Critical > Urgent > Normal; Overdue > Due Soon > On-Time).
     * 2. Earliest deadline / largest overdue duration.
     * 3. Tie-breaker: Earliest creation time.
     * 4. Tie-breaker: Request ID.
     */
    public static Comparator<EmergencyRequestItem> createEmergencyComparator() {
        return (a, b) -> {
            // 1. Primary Priority Score Descending
            int cmp = Integer.compare(b.getPriorityScore(), a.getPriorityScore());
            if (cmp != 0) return cmp;

            // 2. Both Overdue: Most overdue (smallest minutesRemaining, i.e. largest negative) first
            if (a.isOverdue() && b.isOverdue()) {
                int cmpMin = Long.compare(a.getMinutesRemaining(), b.getMinutesRemaining());
                if (cmpMin != 0) return cmpMin;
            }

            // 3. Both in future: Nearest deadline (smallest positive minutesRemaining) first
            if (!a.isOverdue() && !b.isOverdue() && !a.isDeadlineUnavailable() && !b.isDeadlineUnavailable()) {
                int cmpMin = Long.compare(a.getMinutesRemaining(), b.getMinutesRemaining());
                if (cmpMin != 0) return cmpMin;
            }

            // 4. Creation Time tie-breaker (Earliest created first)
            if (a.getCreatedAtTime() != null && b.getCreatedAtTime() != null) {
                int cmpCreated = a.getCreatedAtTime().compareTo(b.getCreatedAtTime());
                if (cmpCreated != 0) return cmpCreated;
            }

            // 5. Final fallback: Request ID
            return a.getRequest().getId().compareTo(b.getRequest().getId());
        };
    }

    /**
     * Generates a clear, human-readable reason why this request is highlighted.
     */
    private static String generateReason(String urgency, boolean overdue, boolean dueSoon,
                                         boolean hasNoMatches, int matchCount, String timeText) {
        StringBuilder sb = new StringBuilder();

        if (overdue) {
            sb.append("DEADLINE EXPIRED (").append(timeText).append(")");
        } else if (dueSoon) {
            sb.append("IMMINENT DEADLINE (").append(timeText).append(")");
        } else {
            sb.append("Triage: ").append(urgency);
        }

        if ("Critical".equalsIgnoreCase(urgency)) {
            sb.append(" • Critical ICU/Surgical requisition");
        } else if ("Urgent".equalsIgnoreCase(urgency)) {
            sb.append(" • High-priority clinical requisition");
        }

        if (hasNoMatches) {
            sb.append(" • 0 matches in local donor pool (Admin review required)");
        } else {
            sb.append(" • ").append(matchCount).append(" potential donor match").append(matchCount > 1 ? "es" : "");
        }

        return sb.toString();
    }

    /**
     * Filters a list of EmergencyRequestItem based on user filter criteria.
     */
    public List<EmergencyRequestItem> filterItems(List<EmergencyRequestItem> items, EmergencyFilter filter) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        if (filter == null) {
            return items;
        }

        String fUrgency = filter.getUrgency();
        String fDeadline = filter.getDeadlineStatus();
        String fGroup = filter.getBloodGroup();
        String fLocality = filter.getLocality();
        String fMatch = filter.getMatchAvailability();

        boolean allUrgency = "ALL".equalsIgnoreCase(fUrgency);
        boolean allDeadline = "ALL".equalsIgnoreCase(fDeadline);
        boolean allGroup = "ALL".equalsIgnoreCase(fGroup);
        boolean allLocality = "ALL".equalsIgnoreCase(fLocality);
        boolean allMatch = "ALL".equalsIgnoreCase(fMatch);

        String normGroup = allGroup ? null : BloodCompatibility.normalizeGroup(fGroup);
        String normLoc = allLocality ? null : DonorMatchingService.extractLocality(fLocality);

        List<EmergencyRequestItem> result = new ArrayList<>();
        for (EmergencyRequestItem item : items) {
            BloodRequest r = item.getRequest();

            if (!allUrgency && !fUrgency.equalsIgnoreCase(r.getUrgency())) {
                continue;
            }

            if (!allDeadline) {
                if ("OVERDUE".equalsIgnoreCase(fDeadline) && !item.isOverdue()) continue;
                if ("DUE_SOON".equalsIgnoreCase(fDeadline) && !item.isDueSoon()) continue;
                if ("ON_SCHEDULE".equalsIgnoreCase(fDeadline) && (item.isOverdue() || item.isDueSoon())) continue;
            }

            if (normGroup != null && !normGroup.equalsIgnoreCase(r.getBloodGroup())) {
                continue;
            }

            if (normLoc != null) {
                String rLoc = DonorMatchingService.extractLocality(r.getHospitalLocality());
                if (rLoc == null || !rLoc.equalsIgnoreCase(normLoc)) {
                    continue;
                }
            }

            if (!allMatch) {
                if ("NO_MATCHES".equalsIgnoreCase(fMatch) && !item.isHasNoMatches()) continue;
                if ("HAS_MATCHES".equalsIgnoreCase(fMatch) && item.isHasNoMatches()) continue;
            }

            result.add(item);
        }

        return result;
    }

    /**
     * Evaluates active requests and emits deduplicated administrative notifications.
     * Prevents duplicate alerts using (Category + TargetId) stable keys.
     */
    public List<AppNotification> evaluateAndGenerateAlerts(LocalDateTime referenceTime) {
        if (referenceTime == null) {
            referenceTime = LocalDateTime.now();
        }

        List<BloodRequest> allRequests = requestService.getAllRequests();
        List<AppNotification> generated = new ArrayList<>();

        for (BloodRequest r : allRequests) {
            if (r == null || !isActiveRequest(r)) {
                continue;
            }

            String reqId = r.getId();

            // 1. Critical Urgency Alert
            if ("Critical".equalsIgnoreCase(r.getUrgency())) {
                if (!notificationService.hasNotification(AppNotification.CAT_CRITICAL_REQUEST, reqId)) {
                    String title = "🚨 Critical Demand: " + reqId + " (" + r.getBloodGroup() + ")";
                    String msg = "Critical emergency blood requisition for " + r.getUnits()
                            + " unit(s) of " + r.getBloodGroup() + " at " + r.getHospitalName()
                            + ". High-priority clinical intervention recommended.";
                    AppNotification notif = createAdminNotification(
                            AppNotification.CAT_CRITICAL_REQUEST, title, msg, reqId, "/matching?requestId=" + reqId
                    );
                    if (notif != null) generated.add(notif);
                }
            }

            // 2. Overdue Deadline Alert
            LocalDateTime reqTime = parseRequiredBy(r.getRequiredBy());
            if (reqTime != null && referenceTime.isAfter(reqTime)) {
                if (!notificationService.hasNotification(AppNotification.CAT_OVERDUE_ALERT, reqId)) {
                    String title = "⏰ Requisition Overdue: " + reqId;
                    String msg = "Required-by deadline (" + r.getRequiredBy() + ") has passed for "
                            + r.getBloodGroup() + " at " + r.getHospitalName() + ". Immediate administrative review required.";
                    AppNotification notif = createAdminNotification(
                            AppNotification.CAT_OVERDUE_ALERT, title, msg, reqId, "/matching?requestId=" + reqId
                    );
                    if (notif != null) generated.add(notif);
                }
            } else if (reqTime != null) {
                // 3. Due Soon Alert (Next 2 Hours)
                long diffMins = Duration.between(referenceTime, reqTime).toMinutes();
                int thresholdMins = AppConfig.getDueSoonHoursThreshold() * 60;
                if (diffMins > 0 && diffMins <= thresholdMins) {
                    if (!notificationService.hasNotification(AppNotification.CAT_DUE_SOON_ALERT, reqId)) {
                        String title = "⏳ Approaching Deadline: " + reqId + " (" + diffMins + "m left)";
                        String msg = "Requisition required within " + diffMins + " minutes for "
                                + r.getBloodGroup() + " at " + r.getHospitalName() + " (" + r.getRequiredBy() + ").";
                        AppNotification notif = createAdminNotification(
                            AppNotification.CAT_DUE_SOON_ALERT, title, msg, reqId, "/matching?requestId=" + reqId
                        );
                        if (notif != null) generated.add(notif);
                    }
                }
            }

            // 4. No Potential Matches Alert
            List<DonorMatch> matches = matchingService.findMatches(r);
            if (matches == null || matches.isEmpty()) {
                if (!notificationService.hasNotification(AppNotification.CAT_NO_MATCHES, reqId)) {
                    String title = "⚠️ Zero Matches: " + reqId + " (" + r.getBloodGroup() + ")";
                    String msg = "Zero available donor matches found in regional records for "
                            + r.getBloodGroup() + " at " + r.getHospitalName() + ". Coordinate directly with hospital blood banks.";
                    AppNotification notif = createAdminNotification(
                            AppNotification.CAT_NO_MATCHES, title, msg, reqId, "/matching?requestId=" + reqId
                    );
                    if (notif != null) generated.add(notif);
                }
            }
        }

        return generated;
    }

    private AppNotification createAdminNotification(String category, String title, String msg, String targetId, String link) {
        String id = "NOTIF-EMG-" + System.nanoTime();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
        AppNotification notif = new AppNotification(id, timestamp, category, title, msg, targetId, false, true, link);
        return notificationService.saveNotification(notif);
    }

    public static boolean isActiveRequest(BloodRequest r) {
        if (r == null) return false;
        String status = r.getStatus() != null ? r.getStatus().trim() : "Pending";
        return !"Fulfilled".equalsIgnoreCase(status)
                && !"Completed".equalsIgnoreCase(status)
                && !"Cancelled".equalsIgnoreCase(status);
    }

    /**
     * Parses the requiredBy timestamp into LocalDateTime safely without throwing exceptions.
     */
    public static LocalDateTime parseRequiredBy(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String clean = raw.trim();

        // 1. Standard "yyyy-MM-dd HH:mm"
        if (clean.length() == 16 && clean.charAt(4) == '-' && clean.charAt(7) == '-' && clean.charAt(10) == ' ') {
            try {
                return LocalDateTime.parse(clean, FMT_REQUIRED_BY);
            } catch (Exception ignored) {}
        }

        // 2. ISO "yyyy-MM-dd'T'HH:mm"
        if (clean.length() >= 16 && clean.contains("T")) {
            try {
                return LocalDateTime.parse(clean.substring(0, 16));
            } catch (Exception ignored) {}
        }

        // 3. Date only "yyyy-MM-dd" -> default to end of day
        if (clean.length() == 10 && clean.charAt(4) == '-' && clean.charAt(7) == '-') {
            try {
                LocalDate d = LocalDate.parse(clean);
                return d.atTime(23, 59, 59);
            } catch (Exception ignored) {}
        }

        // 4. "d MMM yyyy, h:m a"
        try {
            return LocalDateTime.parse(clean, FMT_CREATED_AT);
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Parses the createdAt timestamp into LocalDateTime safely without throwing exceptions.
     */
    public static LocalDateTime parseCreatedAt(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String clean = raw.trim();

        try {
            return LocalDateTime.parse(clean, FMT_CREATED_AT);
        } catch (Exception ignored) {}

        // Fallback to ISO or yyyy-MM-dd HH:mm
        try {
            return parseRequiredBy(clean);
        } catch (Exception ignored) {}

        return null;
    }

    private static String formatOverdueDuration(long diffMinutes) {
        if (diffMinutes < 60) {
            return "+ " + diffMinutes + "m overdue";
        }
        long hours = diffMinutes / 60;
        long mins = diffMinutes % 60;
        if (hours < 24) {
            return "+ " + hours + "h " + mins + "m overdue";
        }
        long days = hours / 24;
        long remHours = hours % 24;
        return "+ " + days + "d " + remHours + "h overdue";
    }

    private static String formatRemainingDuration(long diffMinutes, boolean dueSoon) {
        if (diffMinutes < 60) {
            return "Due in " + diffMinutes + "m";
        }
        long hours = diffMinutes / 60;
        long mins = diffMinutes % 60;
        if (hours < 24) {
            return "Due in " + hours + "h " + mins + "m";
        }
        long days = hours / 24;
        long remHours = hours % 24;
        return days + "d " + remHours + "h remaining";
    }
}

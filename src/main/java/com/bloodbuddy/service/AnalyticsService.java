package com.bloodbuddy.service;

import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.DonorMatch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service managing analytics aggregation, metrics calculation,
 * filtering, actionable insights, and secure CSV report exports (Stage 8).
 */
public class AnalyticsService {

    private final DonorService donorService;
    private final BloodRequestService requestService;
    private final DonorMatchingService matchingService;

    // Parsers for diverse timestamps present in existing records
    private static final DateTimeFormatter CREATED_AT_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM yyyy, h:m a")
            .toFormatter(Locale.ENGLISH);

    private static final DateTimeFormatter REQUIRED_BY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter ISO_LOCAL_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public AnalyticsService() {
        this(new DonorService(), new BloodRequestService(), new DonorMatchingService());
    }

    public AnalyticsService(DonorService donorService, BloodRequestService requestService, DonorMatchingService matchingService) {
        this.donorService = donorService;
        this.requestService = requestService;
        this.matchingService = matchingService;
    }

    /**
     * Filter criteria for analytics querying.
     */
    public static class AnalyticsFilter {
        private String datePreset = "ALL"; // ALL, 7_DAYS, 30_DAYS, CUSTOM
        private LocalDate customStartDate = null;
        private LocalDate customEndDate = null;
        private String dateField = "CREATED_AT"; // CREATED_AT or REQUIRED_BY
        private String bloodGroup = "ALL";
        private String locality = "ALL";
        private String status = "ALL";
        private String urgency = "ALL";

        public AnalyticsFilter() {
        }

        public String getDatePreset() {
            return datePreset;
        }

        public void setDatePreset(String datePreset) {
            this.datePreset = (datePreset != null && !datePreset.trim().isEmpty()) ? datePreset.trim() : "ALL";
        }

        public LocalDate getCustomStartDate() {
            return customStartDate;
        }

        public void setCustomStartDate(LocalDate customStartDate) {
            this.customStartDate = customStartDate;
        }

        public LocalDate getCustomEndDate() {
            return customEndDate;
        }

        public void setCustomEndDate(LocalDate customEndDate) {
            this.customEndDate = customEndDate;
        }

        public String getDateField() {
            return dateField;
        }

        public void setDateField(String dateField) {
            this.dateField = (dateField != null && !dateField.trim().isEmpty()) ? dateField.trim() : "CREATED_AT";
        }

        public String getBloodGroup() {
            return bloodGroup;
        }

        public void setBloodGroup(String bloodGroup) {
            this.bloodGroup = (bloodGroup != null && !bloodGroup.trim().isEmpty()) ? bloodGroup.trim() : "ALL";
        }

        public String getLocality() {
            return locality;
        }

        public void setLocality(String locality) {
            this.locality = (locality != null && !locality.trim().isEmpty()) ? locality.trim() : "ALL";
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = (status != null && !status.trim().isEmpty()) ? status.trim() : "ALL";
        }

        public String getUrgency() {
            return urgency;
        }

        public void setUrgency(String urgency) {
            this.urgency = (urgency != null && !urgency.trim().isEmpty()) ? urgency.trim() : "ALL";
        }
    }

    /**
     * Data Transfer Object holding computed summary statistics and distribution maps.
     */
    public static class AnalyticsSummary {
        private final int totalDonors;
        private final int availableDonors;
        private final int unavailableDonors;
        private final int totalRequests;
        private final int pendingRequests;
        private final int inProgressRequests;
        private final int completedRequests;
        private final int cancelledRequests;
        private final int urgentAndCriticalRequests;
        private final int requestsWithNoMatches;
        private final double completionRate;

        private final Map<String, Integer> donorBloodGroupCounts;
        private final Map<String, Integer> requestBloodGroupCounts;
        private final Map<String, Integer> requestUnitsByBloodGroup;
        private final Map<String, Integer> statusCounts;
        private final Map<String, Integer> urgencyCounts;
        private final Map<String, Integer> localityDemandCounts;
        private final List<String> actionableInsights;

        public AnalyticsSummary(int totalDonors, int availableDonors, int unavailableDonors,
                                int totalRequests, int pendingRequests, int inProgressRequests,
                                int completedRequests, int cancelledRequests, int urgentAndCriticalRequests,
                                int requestsWithNoMatches, double completionRate,
                                Map<String, Integer> donorBloodGroupCounts,
                                Map<String, Integer> requestBloodGroupCounts,
                                Map<String, Integer> requestUnitsByBloodGroup,
                                Map<String, Integer> statusCounts,
                                Map<String, Integer> urgencyCounts,
                                Map<String, Integer> localityDemandCounts,
                                List<String> actionableInsights) {
            this.totalDonors = totalDonors;
            this.availableDonors = availableDonors;
            this.unavailableDonors = unavailableDonors;
            this.totalRequests = totalRequests;
            this.pendingRequests = pendingRequests;
            this.inProgressRequests = inProgressRequests;
            this.completedRequests = completedRequests;
            this.cancelledRequests = cancelledRequests;
            this.urgentAndCriticalRequests = urgentAndCriticalRequests;
            this.requestsWithNoMatches = requestsWithNoMatches;
            this.completionRate = completionRate;
            this.donorBloodGroupCounts = Collections.unmodifiableMap(donorBloodGroupCounts);
            this.requestBloodGroupCounts = Collections.unmodifiableMap(requestBloodGroupCounts);
            this.requestUnitsByBloodGroup = Collections.unmodifiableMap(requestUnitsByBloodGroup);
            this.statusCounts = Collections.unmodifiableMap(statusCounts);
            this.urgencyCounts = Collections.unmodifiableMap(urgencyCounts);
            this.localityDemandCounts = Collections.unmodifiableMap(localityDemandCounts);
            this.actionableInsights = Collections.unmodifiableList(actionableInsights);
        }

        public int getTotalDonors() { return totalDonors; }
        public int getAvailableDonors() { return availableDonors; }
        public int getUnavailableDonors() { return unavailableDonors; }
        public int getTotalRequests() { return totalRequests; }
        public int getPendingRequests() { return pendingRequests; }
        public int getInProgressRequests() { return inProgressRequests; }
        public int getCompletedRequests() { return completedRequests; }
        public int getCancelledRequests() { return cancelledRequests; }
        public int getUrgentAndCriticalRequests() { return urgentAndCriticalRequests; }
        public int getRequestsWithNoMatches() { return requestsWithNoMatches; }
        public double getCompletionRate() { return completionRate; }
        public Map<String, Integer> getDonorBloodGroupCounts() { return donorBloodGroupCounts; }
        public Map<String, Integer> getRequestBloodGroupCounts() { return requestBloodGroupCounts; }
        public Map<String, Integer> getRequestUnitsByBloodGroup() { return requestUnitsByBloodGroup; }
        public Map<String, Integer> getStatusCounts() { return statusCounts; }
        public Map<String, Integer> getUrgencyCounts() { return urgencyCounts; }
        public Map<String, Integer> getLocalityDemandCounts() { return localityDemandCounts; }
        public List<String> getActionableInsights() { return actionableInsights; }
    }

    /**
     * Computes the complete analytics summary given the provided filter using current system storage.
     */
    public AnalyticsSummary computeSummary(AnalyticsFilter filter) {
        return computeSummary(donorService.getAllDemonstrationDonors(), requestService.getAllRequests(), filter);
    }

    /**
     * Computes the complete analytics summary given explicit donor and request datasets.
     */
    public AnalyticsSummary computeSummary(List<Donor> allDonors, List<BloodRequest> allRequests, AnalyticsFilter filter) {
        if (allDonors == null) allDonors = Collections.emptyList();
        if (allRequests == null) allRequests = Collections.emptyList();

        List<Donor> filteredDonors = filterDonors(allDonors, filter);
        List<BloodRequest> filteredRequests = filterRequests(allRequests, filter);

        // 1. Donor Metrics
        int totalDonors = filteredDonors.size();
        int availableDonors = 0;
        int unavailableDonors = 0;

        Map<String, Integer> donorGroups = initializeBloodGroupMap();
        for (Donor d : filteredDonors) {
            if (DonorMatchingService.isDonorAvailable(d)) {
                availableDonors++;
            } else {
                unavailableDonors++;
            }

            String group = BloodCompatibility.normalizeGroup(d.getBloodGroup());
            if (group != null) {
                donorGroups.put(group, donorGroups.getOrDefault(group, 0) + 1);
            }
        }

        // 2. Request Metrics
        int totalRequests = filteredRequests.size();
        int pending = 0;
        int inProgress = 0;
        int completed = 0;
        int cancelled = 0;
        int urgentAndCritical = 0;
        int requestsNoMatches = 0;

        Map<String, Integer> requestGroups = initializeBloodGroupMap();
        Map<String, Integer> requestUnitsByGroup = initializeBloodGroupMap();
        Map<String, Integer> statusMap = new LinkedHashMap<>();
        statusMap.put("Pending", 0);
        statusMap.put("In Progress", 0);
        statusMap.put("Completed", 0);
        statusMap.put("Cancelled", 0);

        Map<String, Integer> urgencyMap = new LinkedHashMap<>();
        urgencyMap.put("Normal", 0);
        urgencyMap.put("Urgent", 0);
        urgencyMap.put("Critical", 0);

        Map<String, Integer> localityMap = new LinkedHashMap<>();

        for (BloodRequest r : filteredRequests) {
            String status = r.getStatus() != null ? r.getStatus().trim() : "Pending";
            if ("Fulfilled".equalsIgnoreCase(status) || "Completed".equalsIgnoreCase(status)) {
                completed++;
                statusMap.put("Completed", statusMap.get("Completed") + 1);
            } else if ("In Progress".equalsIgnoreCase(status)) {
                inProgress++;
                statusMap.put("In Progress", statusMap.get("In Progress") + 1);
            } else if ("Cancelled".equalsIgnoreCase(status)) {
                cancelled++;
                statusMap.put("Cancelled", statusMap.get("Cancelled") + 1);
            } else {
                pending++;
                statusMap.put("Pending", statusMap.get("Pending") + 1);
            }

            String urgency = r.getUrgency() != null ? r.getUrgency().trim() : "Normal";
            if ("Critical".equalsIgnoreCase(urgency)) {
                urgentAndCritical++;
                urgencyMap.put("Critical", urgencyMap.get("Critical") + 1);
            } else if ("Urgent".equalsIgnoreCase(urgency)) {
                urgentAndCritical++;
                urgencyMap.put("Urgent", urgencyMap.get("Urgent") + 1);
            } else {
                urgencyMap.put("Normal", urgencyMap.get("Normal") + 1);
            }

            String reqGroup = BloodCompatibility.normalizeGroup(r.getBloodGroup());
            if (reqGroup != null) {
                requestGroups.put(reqGroup, requestGroups.getOrDefault(reqGroup, 0) + 1);
                requestUnitsByGroup.put(reqGroup, requestUnitsByGroup.getOrDefault(reqGroup, 0) + Math.max(1, r.getUnits()));
            }

            String loc = DonorMatchingService.extractLocality(r.getHospitalLocality());
            if (loc != null && !loc.isEmpty()) {
                localityMap.put(loc, localityMap.getOrDefault(loc, 0) + 1);
            }

            // Check potential donor matches
            List<DonorMatch> matches = matchingService.matchDonors(r, allDonors);
            if (matches.isEmpty()) {
                requestsNoMatches++;
            }
        }

        // 3. Completion Rate Calculation (with zero denominator protection)
        // Definition: completed requests divided by all eligible requests (excluding cancelled)
        int eligibleDenominator = totalRequests - cancelled;
        double completionRate = (eligibleDenominator > 0)
                ? ((double) completed / eligibleDenominator) * 100.0
                : 0.0;

        // 4. Actionable Insights Generation
        List<String> insights = generateInsights(totalRequests, pending, urgentAndCritical,
                completed, eligibleDenominator, completionRate, requestGroups, donorGroups, localityMap);

        return new AnalyticsSummary(totalDonors, availableDonors, unavailableDonors,
                totalRequests, pending, inProgress, completed, cancelled, urgentAndCritical,
                requestsNoMatches, completionRate, donorGroups, requestGroups, requestUnitsByGroup,
                statusMap, urgencyMap, localityMap, insights);
    }

    /**
     * Filters donors by blood group and locality.
     */
    public List<Donor> filterDonors(List<Donor> donors, AnalyticsFilter filter) {
        if (donors == null || donors.isEmpty()) {
            return Collections.emptyList();
        }
        if (filter == null) {
            return donors;
        }

        String filterGroup = filter.getBloodGroup();
        String filterLocality = filter.getLocality();

        boolean matchAllGroups = (filterGroup == null || filterGroup.isEmpty() || "ALL".equalsIgnoreCase(filterGroup));
        boolean matchAllLocs = (filterLocality == null || filterLocality.isEmpty() || "ALL".equalsIgnoreCase(filterLocality));

        if (matchAllGroups && matchAllLocs) {
            return donors;
        }

        String normGroup = matchAllGroups ? null : BloodCompatibility.normalizeGroup(filterGroup);
        String normLoc = matchAllLocs ? null : DonorMatchingService.extractLocality(filterLocality);

        List<Donor> result = new ArrayList<>();
        for (Donor d : donors) {
            if (normGroup != null && !normGroup.equalsIgnoreCase(d.getBloodGroup())) {
                continue;
            }
            if (normLoc != null) {
                String dLoc = DonorMatchingService.extractLocality(d.getLocality());
                if (dLoc == null || !dLoc.equalsIgnoreCase(normLoc)) {
                    continue;
                }
            }
            result.add(d);
        }
        return result;
    }

    /**
     * Filters blood requests based on date ranges, blood group, locality, status, and urgency.
     */
    public List<BloodRequest> filterRequests(List<BloodRequest> requests, AnalyticsFilter filter) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        if (filter == null) {
            return requests;
        }

        LocalDate now = LocalDate.now();
        LocalDate startDate = null;
        LocalDate endDate = null;

        String preset = filter.getDatePreset();
        if ("7_DAYS".equalsIgnoreCase(preset)) {
            startDate = now.minusDays(7);
            endDate = now;
        } else if ("30_DAYS".equalsIgnoreCase(preset)) {
            startDate = now.minusDays(30);
            endDate = now;
        } else if ("CUSTOM".equalsIgnoreCase(preset)) {
            startDate = filter.getCustomStartDate();
            endDate = filter.getCustomEndDate();
        }

        String targetField = filter.getDateField();
        String filterGroup = filter.getBloodGroup();
        String filterLocality = filter.getLocality();
        String filterStatus = filter.getStatus();
        String filterUrgency = filter.getUrgency();

        boolean matchAllGroups = (filterGroup == null || filterGroup.isEmpty() || "ALL".equalsIgnoreCase(filterGroup));
        boolean matchAllLocs = (filterLocality == null || filterLocality.isEmpty() || "ALL".equalsIgnoreCase(filterLocality));
        boolean matchAllStatuses = (filterStatus == null || filterStatus.isEmpty() || "ALL".equalsIgnoreCase(filterStatus));
        boolean matchAllUrgencies = (filterUrgency == null || filterUrgency.isEmpty() || "ALL".equalsIgnoreCase(filterUrgency));

        String normGroup = matchAllGroups ? null : BloodCompatibility.normalizeGroup(filterGroup);
        String normLoc = matchAllLocs ? null : DonorMatchingService.extractLocality(filterLocality);

        List<BloodRequest> result = new ArrayList<>();
        for (BloodRequest r : requests) {
            // 1. Blood group filter
            if (normGroup != null && !normGroup.equalsIgnoreCase(r.getBloodGroup())) {
                continue;
            }

            // 2. Locality filter
            if (normLoc != null) {
                String rLoc = DonorMatchingService.extractLocality(r.getHospitalLocality());
                if (rLoc == null || !rLoc.equalsIgnoreCase(normLoc)) {
                    continue;
                }
            }

            // 3. Status filter
            if (!matchAllStatuses) {
                String rStatus = r.getStatus() != null ? r.getStatus().trim() : "Pending";
                if ("Completed".equalsIgnoreCase(filterStatus) || "Fulfilled".equalsIgnoreCase(filterStatus)) {
                    if (!"Fulfilled".equalsIgnoreCase(rStatus) && !"Completed".equalsIgnoreCase(rStatus)) {
                        continue;
                    }
                } else if (!filterStatus.equalsIgnoreCase(rStatus)) {
                    continue;
                }
            }

            // 4. Urgency filter
            if (!matchAllUrgencies && !filterUrgency.equalsIgnoreCase(r.getUrgency())) {
                continue;
            }

            // 5. Date filter
            if (startDate != null || endDate != null) {
                LocalDate recordDate = parseRequestDate(r, targetField);
                if (recordDate == null) {
                    continue;
                }
                if (startDate != null && recordDate.isBefore(startDate)) {
                    continue;
                }
                if (endDate != null && recordDate.isAfter(endDate)) {
                    continue;
                }
            }

            result.add(r);
        }

        return result;
    }

    /**
     * Parses a date from a BloodRequest according to target field ("CREATED_AT" or "REQUIRED_BY").
     */
    public static LocalDate parseRequestDate(BloodRequest req, String targetField) {
        if (req == null) {
            return null;
        }

        String rawDateStr = "REQUIRED_BY".equalsIgnoreCase(targetField)
                ? req.getRequiredBy()
                : req.getCreatedAt();

        if (rawDateStr == null || rawDateStr.trim().isEmpty()) {
            return null;
        }

        String clean = rawDateStr.trim();

        // 1. Try ISO date (yyyy-MM-dd or yyyy-MM-dd'T'HH:mm or yyyy-MM-dd HH:mm)
        if (clean.length() >= 10 && clean.charAt(4) == '-' && clean.charAt(7) == '-') {
            try {
                return LocalDate.parse(clean.substring(0, 10), ISO_DATE_FORMATTER);
            } catch (Exception ignored) {
            }
        }

        // 2. Try "d MMM yyyy, h:m a" format (e.g. "11 Oct 2026, 09:30 AM")
        try {
            LocalDateTime dt = LocalDateTime.parse(clean, CREATED_AT_FORMATTER);
            return dt.toLocalDate();
        } catch (Exception ignored) {
        }

        // 3. Try splitting before comma or space
        try {
            String[] parts = clean.split(",");
            if (parts.length > 0) {
                String firstPart = parts[0].trim();
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
                return LocalDate.parse(firstPart, dtf);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Generates data-driven actionable insights without unsupported claims.
     */
    private List<String> generateInsights(int totalRequests, int pending, int urgentAndCritical,
                                          int completed, int eligibleDenominator, double completionRate,
                                          Map<String, Integer> requestGroups, Map<String, Integer> donorGroups,
                                          Map<String, Integer> localityMap) {
        List<String> list = new ArrayList<>();

        if (totalRequests == 0) {
            list.add("No blood requisitions found matching current filter parameters.");
            return list;
        }

        // 1. Highest demand blood group
        String topDemandGroup = findTopKey(requestGroups);
        int topDemandCount = topDemandGroup != null ? requestGroups.get(topDemandGroup) : 0;
        if (topDemandGroup != null && topDemandCount > 0) {
            list.add("Blood Group " + topDemandGroup + " has the highest requisition volume with "
                    + topDemandCount + " request" + (topDemandCount > 1 ? "s" : "") + ".");
        }

        // 2. Highest registered donor group
        String topDonorGroup = findTopKey(donorGroups);
        int topDonorCount = topDonorGroup != null ? donorGroups.get(topDonorGroup) : 0;
        if (topDonorGroup != null && topDonorCount > 0) {
            list.add("Blood Group " + topDonorGroup + " represents the largest registered donor pool with "
                    + topDonorCount + " lifesaver profile" + (topDonorCount > 1 ? "s" : "") + ".");
        }

        // 3. Pending demand
        if (pending > 0) {
            list.add("There are currently " + pending + " active requisition" + (pending > 1 ? "s" : "")
                    + " in 'Pending' status awaiting donor matching and clinical confirmation.");
        } else {
            list.add("Zero requisitions currently pending — all active requests are assigned or processed.");
        }

        // 4. Urgent triage demands
        if (urgentAndCritical > 0) {
            list.add(urgentAndCritical + " requisition" + (urgentAndCritical > 1 ? "s are" : " is")
                    + " marked as Urgent or Critical, requiring high-priority hospital coordination.");
        }

        // 5. Highest locality demand
        String topLocality = findTopKey(localityMap);
        int topLocalityCount = topLocality != null ? localityMap.get(topLocality) : 0;
        if (topLocality != null && topLocalityCount > 0) {
            list.add("Hospital facilities in " + topLocality + " logged the highest requisition volume ("
                    + topLocalityCount + " request" + (topLocalityCount > 1 ? "s" : "") + ").");
        }

        // 6. Completion rate
        if (eligibleDenominator > 0) {
            list.add(String.format(Locale.ENGLISH,
                    "Requisition fulfillment rate is %.1f%% (%d fulfilled out of %d active eligible requisitions).",
                    completionRate, completed, eligibleDenominator));
        } else {
            list.add("Fulfillment rate cannot be calculated (zero non-cancelled requisitions in current view).");
        }

        return list;
    }

    private static String findTopKey(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return null;
        String topKey = null;
        int maxVal = -1;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue() > maxVal) {
                maxVal = e.getValue();
                topKey = e.getKey();
            }
        }
        return topKey;
    }

    private static Map<String, Integer> initializeBloodGroupMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String g : BloodCompatibility.ALL_GROUPS) {
            map.put(g, 0);
        }
        return map;
    }

    // =========================================================================
    // SECURE CSV REPORT EXPORT GENERATION
    // =========================================================================

    /**
     * Generates CSV report of blood requisitions with spreadsheet formula injection protection.
     */
    public String generateRequestsCsv(List<BloodRequest> requests) {
        StringBuilder sb = new StringBuilder();
        // CSV Header
        sb.append("Request ID,Patient Name,Blood Group,Units Required,Hospital Name,Hospital Locality,Required By,Urgency,Status,Logged At,Demo Record\n");

        if (requests == null || requests.isEmpty()) {
            return sb.toString();
        }

        for (BloodRequest r : requests) {
            sb.append(escapeCsv(r.getId())).append(",");
            sb.append(escapeCsv(r.getPatientName())).append(",");
            sb.append(escapeCsv(r.getBloodGroup())).append(",");
            sb.append(r.getUnits()).append(",");
            sb.append(escapeCsv(r.getHospitalName())).append(",");
            sb.append(escapeCsv(r.getHospitalLocality())).append(",");
            sb.append(escapeCsv(r.getRequiredBy())).append(",");
            sb.append(escapeCsv(r.getUrgency())).append(",");
            sb.append(escapeCsv(r.getStatus())).append(",");
            sb.append(escapeCsv(r.getCreatedAt())).append(",");
            sb.append(r.isSample() ? "Yes" : "No").append("\n");
        }
        return sb.toString();
    }

    /**
     * Generates CSV report of donor statistics without exposing sensitive contact details.
     */
    public String generateDonorsCsv(List<Donor> donors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Donor ID,Name,Blood Group,City,Locality,Age,Eligible,Availability Status,Badge,Registered Type\n");

        if (donors == null || donors.isEmpty()) {
            return sb.toString();
        }

        for (Donor d : donors) {
            sb.append(escapeCsv(d.getId())).append(",");
            sb.append(escapeCsv(d.getName())).append(",");
            sb.append(escapeCsv(d.getBloodGroup())).append(",");
            sb.append(escapeCsv(d.getCity())).append(",");
            sb.append(escapeCsv(d.getLocality())).append(",");
            sb.append(d.getAge()).append(",");
            sb.append(d.isEligible() ? "Yes" : "No").append(",");
            sb.append(escapeCsv(d.getAvailabilityStatus())).append(",");
            sb.append(escapeCsv(d.getBadge())).append(",");
            sb.append(d.isRegistered() ? "User Registered" : "Seed Demo").append("\n");
        }
        return sb.toString();
    }

    /**
     * Generates CSV summary of key analytics metrics.
     */
    public String generateSummaryCsv(AnalyticsSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("Metric,Value\n");

        if (summary == null) {
            return sb.toString();
        }

        sb.append("Total Donors,").append(summary.getTotalDonors()).append("\n");
        sb.append("Available Donors,").append(summary.getAvailableDonors()).append("\n");
        sb.append("Unavailable Donors,").append(summary.getUnavailableDonors()).append("\n");
        sb.append("Total Requisitions,").append(summary.getTotalRequests()).append("\n");
        sb.append("Pending Requisitions,").append(summary.getPendingRequests()).append("\n");
        sb.append("In Progress Requisitions,").append(summary.getInProgressRequests()).append("\n");
        sb.append("Completed Requisitions,").append(summary.getCompletedRequests()).append("\n");
        sb.append("Cancelled Requisitions,").append(summary.getCancelledRequests()).append("\n");
        sb.append("Urgent & Critical Requisitions,").append(summary.getUrgentAndCriticalRequests()).append("\n");
        sb.append("Requisitions With No Matches,").append(summary.getRequestsWithNoMatches()).append("\n");
        sb.append(String.format(Locale.ENGLISH, "Fulfillment Rate (%%),%.2f\n", summary.getCompletionRate()));

        sb.append("\nBlood Group Demand Breakdown (Units Required),\n");
        for (Map.Entry<String, Integer> e : summary.getRequestUnitsByBloodGroup().entrySet()) {
            sb.append(escapeCsv(e.getKey())).append(",").append(e.getValue()).append("\n");
        }

        sb.append("\nDonor Blood Group Pool Distribution,\n");
        for (Map.Entry<String, Integer> e : summary.getDonorBloodGroupCounts().entrySet()) {
            sb.append(escapeCsv(e.getKey())).append(",").append(e.getValue()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Escapes CSV values and neutralizes spreadsheet formula injection.
     * Prevents execution of malicious formulas starting with =, +, -, @, \t, or \r.
     */
    public static String escapeCsv(String input) {
        if (input == null) {
            return "\"\"";
        }

        String value = input;

        // Spreadsheet Formula Injection defense (OWASP guidelines: =, +, -, @, \t, \r)
        if (!value.isEmpty()) {
            char firstChar = value.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                    || firstChar == '\t' || firstChar == '\r') {
                value = "'" + value;
            } else {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    char trimmedFirst = trimmed.charAt(0);
                    if (trimmedFirst == '=' || trimmedFirst == '+' || trimmedFirst == '-' || trimmedFirst == '@') {
                        value = "'" + value;
                    }
                }
            }
        }

        // Escape internal quotes as ""
        String escaped = value.replace("\"", "\"\"");

        // Wrap in quotes if it contains commas, quotes, newlines or formula prefix
        return "\"" + escaped + "\"";
    }
}

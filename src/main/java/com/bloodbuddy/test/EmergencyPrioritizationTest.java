package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.repository.BloodRequestRepository;
import com.bloodbuddy.repository.DonorRepository;
import com.bloodbuddy.repository.NotificationRepository;
import com.bloodbuddy.service.AnalyticsService;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorMatchingService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.EmergencyPrioritizationService;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencyFilter;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencyRequestItem;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencySummary;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automated Verification Test Suite for Smart Alerts & Emergency Prioritization.
 * Validates clinical urgency rankings, overdue detection, due-soon boundaries (2-hour threshold),
 * exclusion of completed/cancelled requisitions, invalid deadline handling, zero-match flags,
 * notification deduplication across repeated refreshes, access control, and core preservation.
 *
 * Uses isolated temporary directories and clearly identified fictional records.
 */
public class EmergencyPrioritizationTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  BLOOD BUDDY — EMERGENCY PRIORITIZATION VERIFICATION SUITE");
        System.out.println("===============================================================");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("bloodbuddy-emergency-test");
            Path donorJson = tempDir.resolve("registered_donors.json");
            Path requestJson = tempDir.resolve("blood_requests.json");
            Path notifJson = tempDir.resolve("notifications.json");

            DonorRepository donorRepo = new DonorRepository(donorJson);
            BloodRequestRepository reqRepo = new BloodRequestRepository(requestJson);
            NotificationRepository notifRepo = new NotificationRepository(notifJson);

            DonorService donorService = new DonorService(donorRepo);
            BloodRequestService requestService = new BloodRequestService(reqRepo);
            DonorMatchingService matchingService = new DonorMatchingService(donorService);
            NotificationService notifService = new NotificationService(notifRepo);
            EmergencyPrioritizationService emergencyService = new EmergencyPrioritizationService(
                    requestService, matchingService, notifService
            );

            LocalDateTime now = LocalDateTime.of(2026, 10, 11, 14, 0); // Reference time: 2:00 PM

            // Test 1: Critical, Urgent, and Normal ranking
            testUrgencyRanking(emergencyService, now);

            // Test 2: Overdue request detection
            testOverdueDetection(emergencyService, now);

            // Test 3: Due-soon boundary at two hours
            testDueSoonBoundaryAtTwoHours(emergencyService, now);

            // Test 4: Completed and Cancelled request exclusion
            testCompletedAndCancelledExclusion(emergencyService, now);

            // Test 5: Missing or invalid deadline handling
            testMissingOrInvalidDeadlineHandling(emergencyService, now);

            // Test 6: Requests with no matches
            testRequestsWithNoMatches(emergencyService, now);

            // Test 7: Notification deduplication across repeated refreshes
            testNotificationDeduplicationAcrossRefreshes(tempDir);

            // Test 8: Admin authentication and unauthorized access
            testAdminAuthenticationAndUnauthorizedAccess();

            // Test 9: Request status updates through the existing workflow
            testRequestStatusUpdateWorkflow(requestService, emergencyService, now);

            // Test 10: Empty datasets and malformed records
            testEmptyDatasetsAndMalformedRecords(emergencyService, now);

            // Test 11: Regression testing for core features
            testRegressionCoreFeatures();

        } catch (Exception e) {
            System.err.println("[EmergencyTest] Fatal unexpected error during suite execution: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir.toFile());
            }
        }

        System.out.println("===============================================================");
        System.out.println(String.format("  EMERGENCY TEST SUMMARY: Total=%d | Passed=%d | Failed=%d",
                testsRun, testsPassed, testsFailed));
        System.out.println("===============================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Test 1: Critical, Urgent, and Normal ranking.
     */
    private static void testUrgencyRanking(EmergencyPrioritizationService service, LocalDateTime now) {
        System.out.println("\n[Test 1] Testing Critical, Urgent, and Normal Clinical Ranking...");
        testsRun++;

        String futureTime = now.plusHours(5).format(FMT);
        BloodRequest rNormal = createTestRequest("R-NORM", "Normal Patient", "O+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", futureTime);
        BloodRequest rUrgent = createTestRequest("R-URG", "Urgent Patient", "O+", 1, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 09:00 AM", futureTime);
        BloodRequest rCritical = createTestRequest("R-CRIT", "Critical Patient", "O+", 1, "Danavaipeta", "Pending", "Critical", "11 Oct 2026, 09:00 AM", futureTime);

        List<BloodRequest> list = List.of(rNormal, rUrgent, rCritical);
        EmergencySummary summary = service.computeEmergencySummary(list, Collections.emptyList(), now, 2);
        List<EmergencyRequestItem> items = summary.getPrioritizedItems();

        boolean pass = items.size() == 3
                && items.get(0).getRequest().getId().equals("R-CRIT")
                && items.get(1).getRequest().getId().equals("R-URG")
                && items.get(2).getRequest().getId().equals("R-NORM");

        if (pass) {
            System.out.println("  ✓ PASS: Critical ranked before Urgent, and Urgent before Normal strictly.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Urgency ranking order mismatch: " + items.stream().map(i -> i.getRequest().getId()).toList());
            testsFailed++;
        }
    }

    /**
     * Test 2: Overdue request detection.
     */
    private static void testOverdueDetection(EmergencyPrioritizationService service, LocalDateTime now) {
        System.out.println("\n[Test 2] Testing Overdue Request Detection...");
        testsRun++;

        String pastDeadline = now.minusHours(1).format(FMT);
        String futureDeadline = now.plusHours(4).format(FMT);

        BloodRequest rOverdue = createTestRequest("R-OVER", "Patient Past", "A+", 1, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 08:00 AM", pastDeadline);
        BloodRequest rActive = createTestRequest("R-ACT", "Patient Future", "A+", 1, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 08:00 AM", futureDeadline);

        EmergencySummary summary = service.computeEmergencySummary(List.of(rOverdue, rActive), Collections.emptyList(), now, 2);

        boolean pass = summary.getOverdueCount() == 1
                && summary.getPrioritizedItems().get(0).isOverdue()
                && summary.getPrioritizedItems().get(0).getRequest().getId().equals("R-OVER")
                && summary.getPrioritizedItems().get(0).getTimeRemainingText().contains("overdue")
                && !summary.getPrioritizedItems().get(1).isOverdue();

        if (pass) {
            System.out.println("  ✓ PASS: Overdue request correctly flagged, counted, and highlighted.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Overdue detection failure. Overdue count=" + summary.getOverdueCount());
            testsFailed++;
        }
    }

    /**
     * Test 3: Due-soon boundary at two hours.
     */
    private static void testDueSoonBoundaryAtTwoHours(EmergencyPrioritizationService service, LocalDateTime now) {
        System.out.println("\n[Test 3] Testing Due-Soon Boundary at 2 Hours...");
        testsRun++;

        String t90m = now.plusMinutes(90).format(FMT);
        String t119m = now.plusMinutes(119).format(FMT);
        String t135m = now.plusMinutes(135).format(FMT);

        BloodRequest r1 = createTestRequest("R-90M", "P1", "B+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", t90m);
        BloodRequest r2 = createTestRequest("R-119M", "P2", "B+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", t119m);
        BloodRequest r3 = createTestRequest("R-135M", "P3", "B+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", t135m);

        EmergencySummary summary = service.computeEmergencySummary(List.of(r1, r2, r3), Collections.emptyList(), now, 2);

        boolean pass = summary.getDueSoonCount() == 2;
        boolean r1Due = false;
        boolean r2Due = false;
        boolean r3Due = true;

        for (EmergencyRequestItem item : summary.getPrioritizedItems()) {
            if ("R-90M".equals(item.getRequest().getId())) r1Due = item.isDueSoon();
            if ("R-119M".equals(item.getRequest().getId())) r2Due = item.isDueSoon();
            if ("R-135M".equals(item.getRequest().getId())) r3Due = item.isDueSoon();
        }

        pass = pass && r1Due && r2Due && (!r3Due);

        if (pass) {
            System.out.println("  ✓ PASS: Due-soon correctly evaluated at the 2-hour window (90m, 119m included; 135m excluded).");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Due-soon boundary mismatch: count=%d (exp 2), r1=%b, r2=%b, r3=%b",
                    summary.getDueSoonCount(), r1Due, r2Due, r3Due));
            testsFailed++;
        }
    }

    /**
     * Test 4: Completed and Cancelled request exclusion.
     */
    private static void testCompletedAndCancelledExclusion(EmergencyPrioritizationService service, LocalDateTime now) {
        System.out.println("\n[Test 4] Testing Exclusion of Completed and Cancelled Requests...");
        testsRun++;

        String overdue = now.minusHours(2).format(FMT);
        BloodRequest rActive = createTestRequest("R-ACT", "Active", "O+", 1, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 09:00 AM", overdue);
        BloodRequest rFulfilled = createTestRequest("R-FUL", "Fulfilled", "O+", 1, "Danavaipeta", "Fulfilled", "Critical", "11 Oct 2026, 09:00 AM", overdue);
        BloodRequest rCompleted = createTestRequest("R-COM", "Completed", "O+", 1, "Danavaipeta", "Completed", "Critical", "11 Oct 2026, 09:00 AM", overdue);
        BloodRequest rCancelled = createTestRequest("R-CAN", "Cancelled", "O+", 1, "Danavaipeta", "Cancelled", "Critical", "11 Oct 2026, 09:00 AM", overdue);

        EmergencySummary summary = service.computeEmergencySummary(List.of(rActive, rFulfilled, rCompleted, rCancelled), Collections.emptyList(), now, 2);

        boolean pass = summary.getTotalActiveCount() == 1
                && summary.getCriticalActiveCount() == 0
                && summary.getOverdueCount() == 1
                && summary.getPrioritizedItems().size() == 1
                && summary.getPrioritizedItems().get(0).getRequest().getId().equals("R-ACT");

        if (pass) {
            System.out.println("  ✓ PASS: Completed, fulfilled, and cancelled requests strictly excluded from active counts and rankings.");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Inactive request exclusion mismatch: totalActive=%d, critical=%d, overdue=%d",
                    summary.getTotalActiveCount(), summary.getCriticalActiveCount(), summary.getOverdueCount()));
            testsFailed++;
        }
    }

    /**
     * Test 5: Missing or invalid deadline handling.
     */
    private static void testMissingOrInvalidDeadlineHandling(EmergencyPrioritizationService service, LocalDateTime now) {
        System.out.println("\n[Test 5] Testing Missing or Invalid Deadline Handling...");
        testsRun++;

        BloodRequest rNull = createTestRequest("R-NULL", "P1", "A+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", null);
        BloodRequest rEmpty = createTestRequest("R-EMPTY", "P2", "A+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", "  ");
        BloodRequest rMalformed = createTestRequest("R-MAL", "P3", "A+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", "INVALID_DATE_STRING");

        EmergencySummary summary = service.computeEmergencySummary(List.of(rNull, rEmpty, rMalformed), Collections.emptyList(), now, 2);

        boolean pass = summary.getPrioritizedItems().size() == 3;
        for (EmergencyRequestItem item : summary.getPrioritizedItems()) {
            if (!item.isDeadlineUnavailable() || !"Deadline unavailable".equals(item.getTimeRemainingText())
                    || item.isOverdue() || item.isDueSoon()) {
                pass = false;
                break;
            }
        }

        if (pass) {
            System.out.println("  ✓ PASS: Missing and malformed deadlines safely display 'Deadline unavailable' without errors.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Missing or invalid deadline handling failed.");
            testsFailed++;
        }
    }

    /**
     * Test 6: Requests with no matches.
     */
    private static void testRequestsWithNoMatches(EmergencyPrioritizationService service, LocalDateTime now) {
        System.out.println("\n[Test 6] Testing Identification of Requests with No Matches...");
        testsRun++;

        List<Donor> pool = List.of(createTestDonor("D-A", "Donor A", "A+", "Danavaipeta", "Available"));

        String future = now.plusHours(3).format(FMT);
        BloodRequest rNoMatch = createTestRequest("R-BMINUS", "Patient B-", "B-", 1, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 09:00 AM", future);
        BloodRequest rMatch = createTestRequest("R-APLUS", "Patient A+", "A+", 1, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 09:00 AM", future);

        EmergencySummary summary = service.computeEmergencySummary(List.of(rNoMatch, rMatch), pool, now, 2);

        boolean pass = summary.getNoMatchesCount() == 1;
        EmergencyRequestItem itemNoMatch = summary.getPrioritizedItems().stream()
                .filter(i -> "R-BMINUS".equals(i.getRequest().getId())).findFirst().orElse(null);
        EmergencyRequestItem itemMatch = summary.getPrioritizedItems().stream()
                .filter(i -> "R-APLUS".equals(i.getRequest().getId())).findFirst().orElse(null);

        pass = pass && (itemNoMatch != null && itemNoMatch.isHasNoMatches() && itemNoMatch.getHighlightTags().contains("NO_MATCHES"))
                && (itemMatch != null && !itemMatch.isHasNoMatches() && itemMatch.getMatchCount() == 1);

        if (pass) {
            System.out.println("  ✓ PASS: Unmatched requests flagged and counted (0 matches flagged for administrative review).");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: No-matches detection failed.");
            testsFailed++;
        }
    }

    /**
     * Test 7: Notification deduplication across repeated refreshes.
     */
    private static void testNotificationDeduplicationAcrossRefreshes(Path tempBase) throws IOException {
        System.out.println("\n[Test 7] Testing Notification Deduplication Across Repeated Refreshes...");
        testsRun++;

        Path testReqJson = tempBase.resolve("dedup_requests.json");
        Path testNotifJson = tempBase.resolve("dedup_notifications.json");

        BloodRequestRepository reqRepo = new BloodRequestRepository(testReqJson);
        NotificationRepository notifRepo = new NotificationRepository(testNotifJson);
        BloodRequestService reqService = new BloodRequestService(reqRepo);
        NotificationService notifService = new NotificationService(notifRepo);
        DonorMatchingService matchService = new DonorMatchingService(new DonorService());

        EmergencyPrioritizationService emService = new EmergencyPrioritizationService(reqService, matchService, notifService);

        LocalDateTime now = LocalDateTime.now();
        String past = now.minusHours(1).format(FMT);
        reqRepo.createAndSave("Patient 1", "O+", 1, "Govt Hospital", "Danavaipeta", "Attendant", "+91 9900000001",
                past, "Critical", "Test notes", "Pending", "11 Oct 2026, 09:00 AM", true);

        List<AppNotification> firstAlerts = emService.evaluateAndGenerateAlerts(now);
        int initialNotifCount = notifRepo.getAllNotifications().size();

        List<AppNotification> secondAlerts = emService.evaluateAndGenerateAlerts(now);
        int afterRefreshCount = notifRepo.getAllNotifications().size();

        List<AppNotification> thirdAlerts = emService.evaluateAndGenerateAlerts(now);
        int afterThirdRefreshCount = notifRepo.getAllNotifications().size();

        boolean pass = !firstAlerts.isEmpty()
                && secondAlerts.isEmpty()
                && thirdAlerts.isEmpty()
                && (initialNotifCount == afterRefreshCount)
                && (afterRefreshCount == afterThirdRefreshCount);

        if (pass) {
            System.out.println("  ✓ PASS: Notifications generated on first run; 0 duplicates generated on subsequent refreshes.");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Deduplication failure: 1st=%d, 2nd=%d, 3rd=%d (counts: %d -> %d -> %d)",
                    firstAlerts.size(), secondAlerts.size(), thirdAlerts.size(), initialNotifCount, afterRefreshCount, afterThirdRefreshCount));
            testsFailed++;
        }
    }

    /**
     * Test 8: Admin authentication and unauthorized access.
     */
    private static void testAdminAuthenticationAndUnauthorizedAccess() {
        System.out.println("\n[Test 8] Testing Admin Authorization & Access Control on Emergency Center...");
        testsRun++;

        AuthService authService = new AuthService();

        HttpServletRequest unauthReq = createMockRequest(null);
        boolean isUnauthAdmin = authService.isAdmin(unauthReq);

        Map<String, Object> session = new HashMap<>();
        session.put(AuthService.SESSION_ADMIN_KEY, "admin");
        HttpServletRequest authReq = createMockRequest(session);
        boolean isAuthAdmin = authService.isAdmin(authReq);

        boolean pass = (!isUnauthAdmin) && isAuthAdmin;

        if (pass) {
            System.out.println("  ✓ PASS: Admin authentication properly enforced; unauthorized requests rejected.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Admin authentication check failed: unauth=" + isUnauthAdmin + ", auth=" + isAuthAdmin);
            testsFailed++;
        }
    }

    /**
     * Test 9: Request status updates through the existing workflow.
     */
    private static void testRequestStatusUpdateWorkflow(BloodRequestService reqService,
                                                       EmergencyPrioritizationService emService,
                                                       LocalDateTime now) {
        System.out.println("\n[Test 9] Testing Status Transition Workflow & Removal from Emergency Board...");
        testsRun++;

        String overdue = now.minusHours(1).format(FMT);
        BloodRequest r = reqService.createRequest("Status Patient", "O+", 1, "Hospital", "Danavaipeta",
                "Attendant", "+91 9900000001", overdue, "Critical", "Test notes");

        EmergencySummary s1 = emService.computeEmergencySummary(List.of(r), Collections.emptyList(), now, 2);
        boolean activeInitially = s1.getTotalActiveCount() == 1;

        reqService.updateRequestStatus(r.getId(), "In Progress");
        BloodRequest updated1 = reqService.getRequestById(r.getId()).orElse(null);
        boolean inProgress = updated1 != null && "In Progress".equalsIgnoreCase(updated1.getStatus());

        reqService.updateRequestStatus(r.getId(), "Fulfilled");
        BloodRequest updated2 = reqService.getRequestById(r.getId()).orElse(null);
        boolean fulfilled = updated2 != null && "Fulfilled".equalsIgnoreCase(updated2.getStatus());

        EmergencySummary s2 = emService.computeEmergencySummary(List.of(updated2), Collections.emptyList(), now, 2);
        boolean excludedAfterFulfilled = s2.getTotalActiveCount() == 0;

        boolean pass = activeInitially && inProgress && fulfilled && excludedAfterFulfilled;

        if (pass) {
            System.out.println("  ✓ PASS: Status transitions update underlying records cleanly and fulfilled requests drop off emergency list.");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Status update workflow failed: init=%b, inProg=%b, ful=%b, excl=%b",
                    activeInitially, inProgress, fulfilled, excludedAfterFulfilled));
            testsFailed++;
        }
    }

    /**
     * Test 10: Empty datasets and malformed records.
     */
    private static void testEmptyDatasetsAndMalformedRecords(EmergencyPrioritizationService service, LocalDateTime now) {
        System.out.println("\n[Test 10] Testing Empty Datasets and Malformed Records Safety...");
        testsRun++;

        EmergencySummary emptySummary = service.computeEmergencySummary(Collections.emptyList(), Collections.emptyList(), now, 2);
        boolean emptyPass = emptySummary.getTotalActiveCount() == 0
                && emptySummary.getCriticalActiveCount() == 0
                && emptySummary.getOverdueCount() == 0
                && emptySummary.getPrioritizedItems().isEmpty();

        BloodRequest rNullFields = createTestRequest("R-NULL-FIELDS", null, null, -5, null, null, null, null, null);
        EmergencySummary malformedSummary = service.computeEmergencySummary(List.of(rNullFields), Collections.emptyList(), now, 2);
        boolean malformedPass = malformedSummary.getTotalActiveCount() == 1;

        boolean pass = emptyPass && malformedPass;

        if (pass) {
            System.out.println("  ✓ PASS: Empty datasets and malformed inputs handled safely without crashes or null pointer errors.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Empty or malformed dataset evaluation failed.");
            testsFailed++;
        }
    }

    /**
     * Test 11: Regression testing for core features.
     */
    private static void testRegressionCoreFeatures() {
        System.out.println("\n[Test 11] Testing Non-Regression Across Core Features...");
        testsRun++;

        boolean compPass = BloodCompatibility.getCanGiveTo("O-").contains("AB+")
                && !BloodCompatibility.getCanGiveTo("AB+").contains("O-");

        Donor testDonor = createTestDonor("D-REG", "Regression Donor", "B+", "Danavaipeta", "Available");
        boolean donorPass = testDonor.getBloodGroup().equals("B+") && testDonor.isEligible();

        String formula = "=1+1";
        String escaped = AnalyticsService.escapeCsv(formula);
        boolean formulaPass = escaped.startsWith("\"'=");

        boolean configPass = AppConfig.getDueSoonHoursThreshold() >= 1;

        boolean pass = compPass && donorPass && formulaPass && configPass;

        if (pass) {
            System.out.println("  ✓ PASS: Compatibility matrix, donor models, and formula injection protection fully preserved.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Regression detected in core functionality.");
            testsFailed++;
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static Donor createTestDonor(String id, String name, String group, String locality, String status) {
        return new Donor(
                id,
                name,
                group,
                locality,
                "15 Aug 2026",
                true,
                "Verified Lifesaver",
                status,
                28,
                "Rajahmundry",
                "+91 98480 99999",
                true,
                "10 Oct 2026"
        );
    }

    private static BloodRequest createTestRequest(String id, String patient, String group, int units,
                                                  String locality, String status, String urgency,
                                                  String createdAt, String requiredBy) {
        return new BloodRequest(
                id,
                patient,
                group,
                units,
                "Test Care Hospital",
                locality,
                "Test Attendant",
                "+91 98480 88888",
                requiredBy,
                urgency,
                "Fictional Test Requisition",
                status,
                createdAt,
                true
        );
    }

    private static HttpServletRequest createMockRequest(Map<String, Object> sessionAttributes) {
        HttpSession mockSession = (sessionAttributes != null)
                ? (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, methodArgs) -> {
                    if ("getAttribute".equals(method.getName())) {
                        return sessionAttributes.get(methodArgs[0]);
                    }
                    return null;
                })
                : null;

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, methodArgs) -> {
                    if ("getSession".equals(method.getName())) {
                        boolean create = methodArgs != null && methodArgs.length > 0 ? (Boolean) methodArgs[0] : true;
                        return create || mockSession != null ? mockSession : null;
                    }
                    return null;
                });
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}

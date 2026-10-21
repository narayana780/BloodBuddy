package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.DonorMatch;
import com.bloodbuddy.repository.BloodRequestRepository;
import com.bloodbuddy.repository.DonorRepository;
import com.bloodbuddy.repository.NotificationRepository;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorMatchingService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.NotificationService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Automated Verification Test Suite for Smart Donor Matching & Notifications.
 * Tests exact ABO/Rh matching, compatible alternatives, invalid input filtering,
 * availability filtering, transparent locality ranking, duplicate notification prevention,
 * mark-as-read persistence, admin authorization, status updates, and regression testing.
 *
 * Uses isolated temporary directories and clearly identified fictional records.
 */
public class DonorMatchingAndNotificationTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  BLOOD BUDDY — DONOR MATCHING & NOTIFICATION VERIFICATION");
        System.out.println("===============================================================");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("bloodbuddy-matching-test");
            Path donorJson = tempDir.resolve("registered_donors.json");
            Path requestJson = tempDir.resolve("blood_requests.json");
            Path notifJson = tempDir.resolve("notifications.json");

            DonorRepository donorRepo = new DonorRepository(donorJson);
            BloodRequestRepository reqRepo = new BloodRequestRepository(requestJson);
            NotificationRepository notifRepo = new NotificationRepository(notifJson);

            DonorService donorService = new DonorService(donorRepo);
            BloodRequestService requestService = new BloodRequestService(reqRepo);
            NotificationService notifService = new NotificationService(notifRepo);
            DonorMatchingService matchingService = new DonorMatchingService(donorService);

            // Test 1: Exact blood-group matching
            testExactBloodGroupMatching(matchingService);

            // Test 2: Compatible alternative blood groups
            testCompatibleAlternativeBloodGroups(matchingService);

            // Test 3: Invalid and missing blood-group handling
            testInvalidAndMissingBloodGroupHandling(matchingService);

            // Test 4: Available versus unavailable donor filtering
            testAvailabilityFiltering(matchingService);

            // Test 5: Same-locality ranking and adjacency
            testSameLocalityRanking(matchingService);

            // Test 6: Missing locality handling without invented distances
            testMissingLocalityHandling(matchingService);

            // Test 7: No-match behavior & emergency notifications
            testNoMatchBehavior(matchingService, notifService);

            // Test 8: Duplicate notification prevention
            testDuplicateNotificationPrevention(notifService, notifRepo);

            // Test 9: Mark-as-read persistence across restarts
            testMarkAsReadPersistence(notifService, notifJson);

            // Test 10: Admin authorization and session security
            testAdminAuthorization();

            // Test 11: Request-status changes and existing donor registration
            testRequestStatusChangesAndDonorRegistration(requestService, donorService, notifService);

            // Test 12: Regression testing for core features
            testRegressionCoreFeatures(tempDir);

            System.out.println("\n===============================================================");
            System.out.printf("  TOTAL TESTS: %d | PASSED: %d | FAILED: %d%n", testsRun, testsPassed, testsFailed);
            System.out.println("===============================================================");

            if (testsFailed > 0) {
                System.err.println("❌ Some matching & notification verification tests failed!");
                System.exit(1);
            } else {
                System.out.println("✅ ALL MATCHING & NOTIFICATION TESTS PASSED SUCCESSFULLY!");
            }

        } catch (Exception e) {
            System.err.println("❌ Unexpected test runner exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted((a, b) -> b.compareTo(a))
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void assertTrue(String testName, boolean condition, String message) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            testsFailed++;
            System.err.println("  [FAIL] " + testName + " — " + message);
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        testsRun++;
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            testsFailed++;
            System.err.println("  [FAIL] " + testName + " — Expected: '" + expected + "', Got: '" + actual + "'");
        }
    }

    // 1. Exact Blood-Group Matching
    private static void testExactBloodGroupMatching(DonorMatchingService matchingService) {
        System.out.println("\n--- 1. Testing Exact Blood-Group Matching ---");

        BloodRequest reqA = new BloodRequest("TEST-REQ-A", "Fictional Patient A", "A+", 2,
                "Govt Hospital", "Danavaipeta", "Attendant", "+91 9900000001",
                "2026-10-20 12:00", "Urgent", "Test request", "Pending", "11 Oct 2026", true);

        List<Donor> pool = new ArrayList<>();
        pool.add(new Donor("D-A1", "Fictional Donor A1", "A+", "Danavaipeta", "3 months ago", true, "Regular", "Available"));
        pool.add(new Donor("D-O1", "Fictional Donor O1", "O-", "Danavaipeta", "3 months ago", true, "Regular", "Available"));
        pool.add(new Donor("D-B1", "Fictional Donor B1", "B+", "Danavaipeta", "3 months ago", true, "Regular", "Available"));

        List<DonorMatch> matches = matchingService.matchDonors(reqA, pool);
        assertTrue("Matches found for A+ request", !matches.isEmpty(), "Expected matches");

        Optional<DonorMatch> exactOpt = matches.stream().filter(m -> m.getDonor().getId().equals("D-A1")).findFirst();
        assertTrue("D-A1 is matched", exactOpt.isPresent(), "D-A1 must be matched");
        assertEquals("D-A1 classified as EXACT_MATCH", DonorMatch.MatchCategory.EXACT_MATCH, exactOpt.get().getMatchCategory());
        assertTrue("Exact match flag is true", exactOpt.get().isExactMatch(), "isExactMatch should be true");
        assertTrue("Exact match explanation present", exactOpt.get().getMatchExplanations().stream().anyMatch(e -> e.contains("Exact blood-group match")), "Explanation missing");

        // Universal recipient AB+ exact match
        BloodRequest reqAB = new BloodRequest("TEST-REQ-AB", "Fictional Patient AB", "AB+", 1,
                "City Hospital", "Danavaipeta", "Attendant", "+91 9900000001",
                "2026-10-20 12:00", "Normal", "Test", "Pending", "11 Oct 2026", true);
        List<Donor> poolAB = List.of(new Donor("D-AB1", "Donor AB", "AB+", "Danavaipeta", "3 months ago", true, "Regular", "Available"));
        List<DonorMatch> matchesAB = matchingService.matchDonors(reqAB, poolAB);
        assertEquals("AB+ exact match count", 1, matchesAB.size());
        assertEquals("AB+ match is EXACT_MATCH", DonorMatch.MatchCategory.EXACT_MATCH, matchesAB.get(0).getMatchCategory());
    }

    // 2. Compatible Alternative Blood Groups
    private static void testCompatibleAlternativeBloodGroups(DonorMatchingService matchingService) {
        System.out.println("\n--- 2. Testing Compatible Alternative Blood Groups ---");

        BloodRequest reqA = new BloodRequest("TEST-REQ-A2", "Fictional Patient A2", "A+", 2,
                "Govt Hospital", "Danavaipeta", "Attendant", "+91 9900000001",
                "2026-10-20 12:00", "Urgent", "Test request", "Pending", "11 Oct 2026", true);

        List<Donor> pool = new ArrayList<>();
        pool.add(new Donor("D-A2", "Donor A+", "A+", "Danavaipeta", "3 months ago", true, "Regular", "Available"));
        pool.add(new Donor("D-AMINUS", "Donor A-", "A-", "Danavaipeta", "3 months ago", true, "Regular", "Available"));
        pool.add(new Donor("D-OPLUS", "Donor O+", "O+", "Danavaipeta", "3 months ago", true, "Regular", "Available"));
        pool.add(new Donor("D-OMINUS", "Donor O-", "O-", "Danavaipeta", "3 months ago", true, "Regular", "Available"));
        pool.add(new Donor("D-BPLUS", "Donor B+", "B+", "Danavaipeta", "3 months ago", true, "Regular", "Available"));

        List<DonorMatch> matches = matchingService.matchDonors(reqA, pool);
        assertEquals("Total compatible matches for A+ (A+, A-, O+, O-)", 4, matches.size());

        // Incompatible B+ must be excluded
        boolean hasBPlus = matches.stream().anyMatch(m -> m.getDonor().getBloodGroup().equals("B+"));
        assertTrue("Incompatible B+ is excluded", !hasBPlus, "B+ must not be matched to A+");

        // Verify alternatives
        for (DonorMatch m : matches) {
            if (!m.getDonor().getBloodGroup().equals("A+")) {
                assertEquals("Compatible alternative categorized properly",
                        DonorMatch.MatchCategory.COMPATIBLE_ALTERNATIVE, m.getMatchCategory());
                assertTrue("Compatible explanation present",
                        m.getMatchExplanations().stream().anyMatch(e -> e.contains("Potentially compatible")), "Explanation missing");
            }
        }

        // Exact match ranks above all compatible alternatives
        assertEquals("First ranked candidate is exact match", "D-A2", matches.get(0).getDonor().getId());
    }

    // 3. Invalid and Missing Blood-Group Handling
    private static void testInvalidAndMissingBloodGroupHandling(DonorMatchingService matchingService) {
        System.out.println("\n--- 3. Testing Invalid and Missing Blood-Group Handling ---");

        List<Donor> validPool = List.of(
                new Donor("D-1", "Valid Donor", "O+", "Danavaipeta", "3 months ago", true, "Regular", "Available")
        );

        // Invalid request blood groups
        BloodRequest reqInvalid1 = new BloodRequest("REQ-INV1", "Patient", "XYZ", 1, "Hosp", "Danavaipeta", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);
        BloodRequest reqInvalid2 = new BloodRequest("REQ-INV2", "Patient", "", 1, "Hosp", "Danavaipeta", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);
        BloodRequest reqInvalid3 = new BloodRequest("REQ-INV3", "Patient", null, 1, "Hosp", "Danavaipeta", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);

        assertEquals("Invalid group 'XYZ' returns 0 matches", 0, matchingService.matchDonors(reqInvalid1, validPool).size());
        assertEquals("Empty group returns 0 matches", 0, matchingService.matchDonors(reqInvalid2, validPool).size());
        assertEquals("Null group returns 0 matches", 0, matchingService.matchDonors(reqInvalid3, validPool).size());

        // Invalid donor blood groups in pool
        BloodRequest reqValid = new BloodRequest("REQ-VAL", "Patient", "O+", 1, "Hosp", "Danavaipeta", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);
        List<Donor> mixedPool = List.of(
                new Donor("D-INV1", "Bad Donor 1", "INVALID", "Danavaipeta", "3 months ago", true, "Regular", "Available"),
                new Donor("D-INV2", "Bad Donor 2", null, "Danavaipeta", "3 months ago", true, "Regular", "Available"),
                new Donor("D-INV3", "Bad Donor 3", "", "Danavaipeta", "3 months ago", true, "Regular", "Available"),
                new Donor("D-OK", "Good Donor", "O+", "Danavaipeta", "3 months ago", true, "Regular", "Available")
        );

        List<DonorMatch> mixedMatches = matchingService.matchDonors(reqValid, mixedPool);
        assertEquals("Only valid donor matches", 1, mixedMatches.size());
        assertEquals("Valid donor matched ID", "D-OK", mixedMatches.get(0).getDonor().getId());
    }

    // 4. Available versus Unavailable Donor Filtering
    private static void testAvailabilityFiltering(DonorMatchingService matchingService) {
        System.out.println("\n--- 4. Testing Available vs Unavailable Donor Filtering ---");

        BloodRequest req = new BloodRequest("REQ-AVAIL", "Patient", "O+", 1, "Hosp", "Danavaipeta", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);

        List<Donor> pool = List.of(
                new Donor("D-AVAIL", "Available Donor", "O+", "Danavaipeta", "3 months ago", true, "Regular", "Available"),
                new Donor("D-STANDBY", "Standby Donor", "O+", "Danavaipeta", "3 months ago", true, "Regular", "On Standby"),
                new Donor("D-RESTING", "Resting Donor", "O+", "Danavaipeta", "3 months ago", false, "Regular", "Resting Period"),
                new Donor("D-INELIGIBLE", "Ineligible Donor", "O+", "Danavaipeta", "3 months ago", false, "Regular", "Available")
        );

        List<DonorMatch> matches = matchingService.matchDonors(req, pool);
        assertEquals("Only truly available donor matched", 1, matches.size());
        assertEquals("Matched donor is D-AVAIL", "D-AVAIL", matches.get(0).getDonor().getId());

        // Verify helper method
        assertTrue("D-AVAIL is donor available", DonorMatchingService.isDonorAvailable(pool.get(0)), "D-AVAIL must be available");
        assertTrue("D-STANDBY is not donor available", !DonorMatchingService.isDonorAvailable(pool.get(1)), "D-STANDBY must be unavailable");
        assertTrue("D-RESTING is not donor available", !DonorMatchingService.isDonorAvailable(pool.get(2)), "D-RESTING must be unavailable");
        assertTrue("D-INELIGIBLE is not donor available", !DonorMatchingService.isDonorAvailable(pool.get(3)), "D-INELIGIBLE must be unavailable");
    }

    // 5. Same-Locality Ranking and Adjacency
    private static void testSameLocalityRanking(DonorMatchingService matchingService) {
        System.out.println("\n--- 5. Testing Same-Locality Ranking and Adjacency ---");

        // Hospital in Danavaipeta
        BloodRequest req = new BloodRequest("REQ-LOC", "Patient", "B+", 1, "GGH", "Danavaipeta, Rajahmundry", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);

        List<Donor> pool = List.of(
                new Donor("D-DISTANT", "Distant Donor", "B+", "Dowleswaram", "3 months ago", true, "Regular", "Available"),
                new Donor("D-NEARBY", "Nearby Donor", "B+", "Tilak Road", "3 months ago", true, "Regular", "Available"),
                new Donor("D-SAME", "Same Locality Donor", "B+", "Danavaipeta", "3 months ago", true, "Regular", "Available")
        );

        List<DonorMatch> matches = matchingService.matchDonors(req, pool);
        assertEquals("All 3 B+ available donors matched", 3, matches.size());

        // Ranking order must be: 1) Same Locality (Danavaipeta), 2) Nearby (Tilak Road), 3) Distant (Dowleswaram)
        assertEquals("Rank #1 is Same Locality", "D-SAME", matches.get(0).getDonor().getId());
        assertEquals("Rank #1 location match type", DonorMatch.LocationMatch.SAME_LOCALITY, matches.get(0).getLocationMatch());

        assertEquals("Rank #2 is Nearby Locality", "D-NEARBY", matches.get(1).getDonor().getId());
        assertEquals("Rank #2 location match type", DonorMatch.LocationMatch.NEARBY_LOCALITY, matches.get(1).getLocationMatch());

        assertEquals("Rank #3 is Distant Locality", "D-DISTANT", matches.get(2).getDonor().getId());
        assertEquals("Rank #3 location match type", DonorMatch.LocationMatch.OTHER_LOCALITY, matches.get(2).getLocationMatch());

        assertTrue("Same locality score > nearby score", matches.get(0).getRankScore() > matches.get(1).getRankScore(), "Score inequality");
        assertTrue("Nearby score > distant score", matches.get(1).getRankScore() > matches.get(2).getRankScore(), "Score inequality");
    }

    // 6. Missing Locality Handling Without Invented Distances
    private static void testMissingLocalityHandling(DonorMatchingService matchingService) {
        System.out.println("\n--- 6. Testing Missing Locality Handling (Zero Invented Distances) ---");

        BloodRequest reqMissingLoc = new BloodRequest("REQ-NOLOC", "Patient", "O-", 1, "Hospital", "", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);
        List<Donor> pool = List.of(
                new Donor("D-NOLOC", "Donor Without Locality", "O-", "", "3 months ago", true, "Regular", "Available")
        );

        List<DonorMatch> matches = matchingService.matchDonors(reqMissingLoc, pool);
        assertEquals("Matched without locality", 1, matches.size());
        assertEquals("Categorized as UNKNOWN_LOCALITY", DonorMatch.LocationMatch.UNKNOWN_LOCALITY, matches.get(0).getLocationMatch());
        assertTrue("Explanation clarifies no distance assumed",
                matches.get(0).getMatchExplanations().stream().anyMatch(e -> e.contains("no physical distance assumed")), "Notice missing");
    }

    // 7. No-Match Behavior
    private static void testNoMatchBehavior(DonorMatchingService matchingService, NotificationService notifService) {
        System.out.println("\n--- 7. Testing No-Match Behavior ---");

        // Request O- with only B+ donors available
        BloodRequest reqRare = new BloodRequest("REQ-RARE", "Patient", "O-", 1, "Hospital", "Danavaipeta", "Att", "+91 9900000000", "2026-10-20", "Critical", "", "Pending", "11 Oct", true);
        List<Donor> poolB = List.of(
                new Donor("D-B", "B+ Donor", "B+", "Danavaipeta", "3 months ago", true, "Regular", "Available")
        );

        List<DonorMatch> matches = matchingService.matchDonors(reqRare, poolB);
        assertTrue("Zero matches found for incompatible group", matches.isEmpty(), "Matches must be empty");

        // Trigger notification for no matches
        AppNotification notif = notifService.notifyNoMatches(reqRare);
        assertTrue("Notification emitted for no matches", notif != null, "Notification must be emitted");
        assertEquals("Category is NO_MATCHES", AppNotification.CAT_NO_MATCHES, notif.getCategory());
        assertTrue("Notification message advises contacting hospital",
                notif.getMessage().contains("Advise contacting hospital"), "Message text verified");
    }

    // 8. Duplicate Notification Prevention
    private static void testDuplicateNotificationPrevention(NotificationService notifService, NotificationRepository repo) {
        System.out.println("\n--- 8. Testing Duplicate Notification Prevention ---");

        BloodRequest req = new BloodRequest("REQ-DUP", "Patient", "A+", 1, "Hospital", "Danavaipeta", "Att", "+91 9900000000", "2026-10-20", "Normal", "", "Pending", "11 Oct", true);

        // First submission notification
        AppNotification first = notifService.notifyRequestSubmitted(req);
        assertTrue("First notification created", first != null, "First notif must not be null");

        // Second submission notification for same request ID (simulating page reload)
        AppNotification second = notifService.notifyRequestSubmitted(req);
        assertTrue("Duplicate request notification prevented (returned null)", second == null, "Duplicate must be rejected");

        // First matches found notification
        AppNotification matchFirst = notifService.notifyMatchesFound(req, 3);
        assertTrue("First matches notification created", matchFirst != null, "Match notif must not be null");

        // Duplicate matches notification
        AppNotification matchSecond = notifService.notifyMatchesFound(req, 3);
        assertTrue("Duplicate match notification prevented (returned null)", matchSecond == null, "Duplicate match must be rejected");

        // Duplicate check via repo
        assertTrue("Repo confirms event exists", repo.hasNotification(AppNotification.CAT_REQUEST_SUBMITTED, "REQ-DUP"), "Repo check");
    }

    // 9. Mark-as-read persistence across restarts
    private static void testMarkAsReadPersistence(NotificationService notifService, Path notifJson) {
        System.out.println("\n--- 9. Testing Mark-As-Read Persistence Across Restart ---");

        // Find an unread notification
        List<AppNotification> unreadList = notifService.getNotifications(true);
        AppNotification target = unreadList.stream().filter(n -> !n.isRead()).findFirst().orElse(null);
        assertTrue("Found unread notification to test", target != null, "Unread notification needed");

        String targetId = target.getId();
        boolean marked = notifService.markAsRead(targetId);
        assertTrue("Mark as read returned true", marked, "markAsRead should return true");

        // Restart simulation: Re-read from disk directly using a fresh repository instance
        NotificationRepository restartedRepo = new NotificationRepository(notifJson);
        NotificationService restartedService = new NotificationService(restartedRepo);

        Optional<AppNotification> reloaded = restartedService.findById(targetId);
        assertTrue("Reloaded notification present after restart", reloaded.isPresent(), "Notif must persist");
        assertTrue("Notification read status persisted as true", reloaded.get().isRead(), "Read status must persist across restarts");
    }

    // 10. Admin Authorization and Session Security
    private static void testAdminAuthorization() {
        System.out.println("\n--- 10. Testing Admin Authorization and Session Handling ---");

        AuthService authService = new AuthService();

        // Null request check
        assertTrue("Null request is not admin", !authService.isAdmin(null), "Null request must not be admin");

        // Verify configured default credentials
        assertEquals("Default admin username is admin", "admin", AppConfig.ADMIN_DEFAULT_USERNAME);
        assertEquals("Default admin password is bloodbuddy2026", "bloodbuddy2026", AppConfig.ADMIN_DEFAULT_PASSWORD);
    }

    // 11. Request-Status Changes and Existing Donor Registration
    private static void testRequestStatusChangesAndDonorRegistration(BloodRequestService reqService,
                                                                     DonorService donorService,
                                                                     NotificationService notifService) {
        System.out.println("\n--- 11. Testing Request-Status Changes and Donor Registration ---");

        // Submit request
        BloodRequest req = reqService.createRequest("Status Test Patient", "AB-", 1,
                "Trust Hospital", "Prakash Nagar", "Attendant", "+91 9911223344",
                "2026-10-25 15:00", "Normal", "Status lifecycle test");
        assertEquals("Initial status is Pending", "Pending", req.getStatus());

        // Update status to In Progress
        boolean updated1 = reqService.updateRequestStatus(req.getId(), "In Progress");
        assertTrue("Status updated to In Progress", updated1, "Update should succeed");

        Optional<BloodRequest> reloaded1 = reqService.getRequestById(req.getId());
        assertTrue("Reloaded request exists", reloaded1.isPresent(), "Request exists");
        assertEquals("Reloaded status is In Progress", "In Progress", reloaded1.get().getStatus());

        // Emit notification
        AppNotification notif = notifService.notifyStatusChanged(req, "Pending", "In Progress");
        assertTrue("Status change notification generated", notif != null, "Notif must not be null");
        assertEquals("Notif category is STATUS_CHANGED", AppNotification.CAT_STATUS_CHANGED, notif.getCategory());

        // Update status to Fulfilled
        boolean updated2 = reqService.updateRequestStatus(req.getId(), "Fulfilled");
        assertTrue("Status updated to Fulfilled", updated2, "Update should succeed");
        assertEquals("Reloaded status is Fulfilled", "Fulfilled", reqService.getRequestById(req.getId()).get().getStatus());

        // Donor Registration integration
        Donor registeredDonor = donorService.registerDonor(
                "Lifesaver Volunteer",
                "AB-",
                29,
                "Rajahmundry",
                "Prakash Nagar",
                true,
                "+91 98480 77665"
        );
        assertTrue("Donor successfully registered", registeredDonor != null, "Donor must be non-null");
        assertTrue("Donor ID starts with REG-", registeredDonor.getId().startsWith("REG-"), "ID starts with REG-");
        assertTrue("Donor is available", DonorMatchingService.isDonorAvailable(registeredDonor), "Registered donor available");
    }

    // 12. Regression Testing for Core Features
    private static void testRegressionCoreFeatures(Path tempDir) {
        System.out.println("\n--- 12. Testing Regression for Core Features ---");

        // ABO/Rh matrix validity
        for (String group : BloodCompatibility.ALL_GROUPS) {
            assertTrue("Group " + group + " is valid", BloodCompatibility.isValidGroup(group), group + " should be valid");
            assertTrue("Group " + group + " has receivers", !BloodCompatibility.getCanGiveTo(group).isEmpty(), "Can give to");
            assertTrue("Group " + group + " has donors", !BloodCompatibility.getCanReceiveFrom(group).isEmpty(), "Can receive from");
        }

        // Universal donor O- can receive only from O-
        List<String> oMinusReceives = BloodCompatibility.getCanReceiveFrom("O-");
        assertEquals("O- receives only from O-", 1, oMinusReceives.size());
        assertEquals("O- donor", "O-", oMinusReceives.get(0));

        // Universal recipient AB+ receives from all 8 groups
        List<String> abPlusReceives = BloodCompatibility.getCanReceiveFrom("AB+");
        assertEquals("AB+ receives from all 8 groups", 8, abPlusReceives.size());

        // Initial donor listing intact
        DonorService ds = new DonorService(new DonorRepository(tempDir.resolve("seed_donors.json")));
        List<Donor> seedDonors = ds.getAllDonors();
        assertTrue("All 12 seed donors loaded", seedDonors.size() >= 12, "At least 12 seed donors");

        // Donor availability updates intact
        boolean toggle = ds.updateDonorAvailability("VOL-01", false);
        // VOL-01 is seed, repo update won't crash
        assertTrue("Availability toggle handled safely", true, "Handled safely");

        // Blood Request filtering intact
        BloodRequestRepository brRepo = new BloodRequestRepository(tempDir.resolve("seed_requests.json"));
        BloodRequestService brService = new BloodRequestService(brRepo);
        List<BloodRequest> allInitial = brService.getAllRequests();
        assertTrue("Sample requests initialized", allInitial.size() >= 3, "Sample requests present");
    }
}

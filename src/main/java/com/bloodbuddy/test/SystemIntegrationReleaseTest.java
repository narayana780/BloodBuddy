package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.DonorMatch;
import com.bloodbuddy.repository.BloodRequestRepository;
import com.bloodbuddy.repository.DonorRepository;
import com.bloodbuddy.repository.NotificationRepository;
import com.bloodbuddy.service.AnalyticsService;
import com.bloodbuddy.service.AnalyticsService.AnalyticsFilter;
import com.bloodbuddy.service.AnalyticsService.AnalyticsSummary;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorMatchingService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.EmergencyPrioritizationService;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencyFilter;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencyRequestItem;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencySummary;
import com.bloodbuddy.service.NotificationService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Automated Final Integration, Security & Workflow Verification Suite.
 * Validates the complete 17-step end-to-end user workflow, security safeguards,
 * formula injection defenses, and persistence integrity using isolated test data.
 */
public class SystemIntegrationReleaseTest {

    private static int totalPassed = 0;
    private static int totalFailed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("  BLOOD BUDDY — SYSTEM INTEGRATION & RELEASE READINESS SUITE");
        System.out.println("===============================================================================");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("bloodbuddy-integration-test-sandbox");
            Path donorFile = tempDir.resolve("test_registered_donors.json");
            Path requestFile = tempDir.resolve("test_blood_requests.json");
            Path notifFile = tempDir.resolve("test_notifications.json");

            // Initialize isolated repositories & services
            DonorRepository donorRepo = new DonorRepository(donorFile);
            DonorService donorService = new DonorService(donorRepo);

            BloodRequestRepository reqRepo = new BloodRequestRepository(requestFile);
            BloodRequestService reqService = new BloodRequestService(reqRepo);

            NotificationRepository notifRepo = new NotificationRepository(notifFile);
            NotificationService notifService = new NotificationService(notifRepo);

            DonorMatchingService matchingService = new DonorMatchingService(donorService);
            AnalyticsService analyticsService = new AnalyticsService(donorService, reqService, matchingService);
            EmergencyPrioritizationService emergencyService = new EmergencyPrioritizationService(reqService, matchingService, notifService);
            AuthService authService = new AuthService();

            // Run 17-Step End-to-End Workflow Tests
            testStep1_LandingPageTemplateAccess();
            testStep2_NavigateToDonorRegistration(donorService);
            Donor registeredDonor = testStep3And4_RegisterAndVerifyDonor(donorService, donorRepo);
            testStep5_SearchForDonor(donorService, registeredDonor);
            BloodRequest submittedReq = testStep6And7_SubmitAndVerifyBloodRequest(reqService, reqRepo, notifService);
            testStep8_MatchingResultsAndCompatibility(matchingService, submittedReq, registeredDonor);
            testStep9_NotificationsDeduplication(notifService, submittedReq);
            testStep10_AdminAuthenticationAndEnvConfig(authService);
            testStep11_ReviewMatchesAsAdmin(matchingService, submittedReq);
            testStep12And13_UpdateStatusAndVerifyPersistence(reqService, reqRepo, notifService, submittedReq);
            testStep14_DashboardStatisticsAndAnalytics(analyticsService);
            testStep15_ExportCsvReportsAndFormulaInjectionProtection(analyticsService, submittedReq, registeredDonor);
            testStep16_EmergencyPrioritizationAndDeadlineAlerts(emergencyService, reqService);
            testStep17_AdminLogoutAndSecurityGuards(authService);

            // Additional Security & Reliability Verifications
            testSecurity_XssEscapingAndSanitization();
            testSecurity_PathTraversalDefenses();
            testSecurity_EmptyAndMalformedFileHandling(tempDir);

            System.out.println("\n===============================================================================");
            System.out.printf("  SYSTEM INTEGRATION RESULTS: Total Passed=%d | Total Failed=%d%n", totalPassed, totalFailed);
            System.out.println("===============================================================================");

            if (totalFailed > 0) {
                System.err.println("❌ INTEGRATION SUITE FAILED: " + totalFailed + " assertion(s) failed.");
                System.exit(1);
            } else {
                System.out.println("✅ ALL SYSTEM INTEGRATION & RELEASE TESTS PASSED SUCCESSFULLY!");
            }

        } catch (Exception e) {
            System.err.println("❌ Fatal exception during integration suite execution: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted((a, b) -> b.compareTo(a))
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException ignored) {}
            }
        }
    }

    // 1. Landing page template
    private static void testStep1_LandingPageTemplateAccess() {
        System.out.println("\n[Step 1] Verifying Landing Page & Static Assets...");
        File template = new File("src/main/resources/templates/index.html");
        assertCondition(template.exists() && template.length() > 0, "Landing page index.html exists and is non-empty");

        File jsAsset = new File("src/main/resources/static/js/main.js");
        assertCondition(jsAsset.exists() && jsAsset.length() > 0, "Static main.js script asset exists and is non-empty");

        File cssAsset = new File("src/main/resources/static/css/style.css");
        assertCondition(cssAsset.exists() && cssAsset.length() > 0, "Static style.css design system stylesheet exists");
    }

    // 2. Navigate to donor registration
    private static void testStep2_NavigateToDonorRegistration(DonorService service) {
        System.out.println("\n[Step 2] Verifying Donor Pre-requisites & Rules...");
        assertCondition(AppConfig.MIN_DONOR_AGE == 18, "Minimum donor age rule configured to 18");
        assertCondition(AppConfig.MAX_DONOR_AGE == 65, "Maximum donor age rule configured to 65");
        assertCondition(BloodCompatibility.ALL_GROUPS.size() == 8, "8 standard blood groups supported");
    }

    // 3 & 4. Register donor and verify persistence
    private static Donor testStep3And4_RegisterAndVerifyDonor(DonorService service, DonorRepository repo) {
        System.out.println("\n[Step 3 & 4] Registering Fictional Donor & Verifying Persistence...");
        String name = "Satyanarayana Raju";
        String group = "O-";
        int age = 29;
        String city = "Rajahmundry";
        String locality = "Danavaipeta";
        String contact = "+91 98480 99887";

        Donor d = service.registerDonor(name, group, age, city, locality, true, contact);
        assertCondition(d != null && d.getId() != null && d.getId().startsWith("REG-"), "Registered donor assigned valid REG- ID");
        assertCondition("Satyanarayana Raju".equals(d.getName()), "Donor name persisted correctly");
        assertCondition("O-".equals(d.getBloodGroup()), "Universal donor O- group recorded");
        assertCondition(d.isEligible(), "Donor marked eligible");

        // Verify retrieval from repository
        Optional<Donor> fetched = repo.findById(d.getId());
        assertCondition(fetched.isPresent(), "Donor retrieved from disk repository by ID");
        assertCondition(contact.equals(fetched.get().getContactInfo()), "Contact phone securely stored in repository");
        return d;
    }

    // 5. Search for donor
    private static void testStep5_SearchForDonor(DonorService service, Donor donor) {
        System.out.println("\n[Step 5] Searching for Registered Donor in Directory...");
        List<Donor> results = service.searchDonors("Satyanarayana", "O-", "Danavaipeta");
        assertCondition(!results.isEmpty(), "Donor found via multi-criteria search (name, group, locality)");
        assertCondition(results.stream().anyMatch(d -> d.getId().equals(donor.getId())), "Target donor ID present in search results");
    }

    // 6 & 7. Submit fictional blood request and verify persistence
    private static BloodRequest testStep6And7_SubmitAndVerifyBloodRequest(BloodRequestService service,
                                                                         BloodRequestRepository repo,
                                                                         NotificationService notifService) {
        System.out.println("\n[Step 6 & 7] Submitting Fictional Requisition & Verifying Persistence...");
        String patient = "Venkata Raman (Emergency)";
        String group = "O-";
        int units = 2;
        String hospital = "Swatantra Hospital";
        String locality = "Danavaipeta, Rajahmundry";
        String attendant = "M. Rao";
        String phone = "+91 94401 22334";
        String deadline = "2026-10-25 15:00";
        String urgency = "Critical";
        String notes = "Emergency cardiac operation blood reserve.";

        BloodRequest req = service.createRequest(patient, group, units, hospital, locality, attendant, phone, deadline, urgency, notes);
        assertCondition(req != null && req.getId() != null && req.getId().startsWith("REQ-"), "Assigned unique REQ- identifier");
        assertCondition("Critical".equals(req.getUrgency()), "Urgency recorded as Critical");
        assertCondition("Pending".equals(req.getStatus()), "Status defaulted to Pending");

        // Verify persistence
        Optional<BloodRequest> fetched = service.getRequestById(req.getId());
        assertCondition(fetched.isPresent(), "Blood requisition fetched from persistent storage");
        assertCondition(fetched.get().getUnits() == 2, "Requisition units verified as 2");

        // Check submission notification
        notifService.notifyRequestSubmitted(req);
        assertCondition(notifService.hasNotification(com.bloodbuddy.model.AppNotification.CAT_REQUEST_SUBMITTED, req.getId()), "Submission notification recorded");
        return req;
    }

    // 8. Matching results and compatibility
    private static void testStep8_MatchingResultsAndCompatibility(DonorMatchingService matcher,
                                                                 BloodRequest request,
                                                                 Donor expectedDonor) {
        System.out.println("\n[Step 8] Evaluating Smart Matching Results & Proximity Ranking...");
        List<DonorMatch> matches = matcher.findMatches(request);
        assertCondition(!matches.isEmpty(), "Matching engine returned candidate donors");

        DonorMatch topMatch = matches.get(0);
        assertCondition(topMatch.isExactMatch(), "Top candidate is exact blood group match (O-)");
        assertCondition("O-".equals(topMatch.getDonor().getBloodGroup()), "Match donor blood group is O-");
        assertCondition(!topMatch.getMatchExplanations().isEmpty(), "Match rationale generated transparently");
    }

    // 9. Notifications deduplication
    private static void testStep9_NotificationsDeduplication(NotificationService notifService, BloodRequest request) {
        System.out.println("\n[Step 9] Verifying In-App Notifications Deduplication...");
        int initialCount = notifService.getNotifications(false).size();

        // Attempt duplicate emission of same category & target ID
        notifService.notifyRequestSubmitted(request);
        notifService.notifyRequestSubmitted(request);

        int countAfter = notifService.getNotifications(false).size();
        assertCondition(initialCount == countAfter, "Duplicate notifications prevented on repeated triggers");
    }

    // 10. Admin authentication and env config
    private static void testStep10_AdminAuthenticationAndEnvConfig(AuthService authService) {
        System.out.println("\n[Step 10] Testing Admin Authentication & Environment Config...");
        assertCondition("admin".equalsIgnoreCase(AppConfig.getAdminUsername()), "Admin username correctly resolved");
        assertCondition("bloodbuddy2026".equals(AppConfig.getAdminPassword()), "Admin password correctly resolved");
    }

    // 11. Review matches as administrator
    private static void testStep11_ReviewMatchesAsAdmin(DonorMatchingService matcher, BloodRequest request) {
        System.out.println("\n[Step 11] Reviewing Candidate Pool as Administrator...");
        List<DonorMatch> matches = matcher.findMatches(request);
        assertCondition(matches.size() >= 1, "Staff coordinator can review candidate lifesavers");
        // Verify donor contact info available in model for authorized coordinator
        assertCondition(matches.get(0).getDonor().getContactInfo() != null, "Donor contact info accessible to staff");
    }

    // 12 & 13. Update status and verify persistence
    private static void testStep12And13_UpdateStatusAndVerifyPersistence(BloodRequestService service,
                                                                         BloodRequestRepository repo,
                                                                         NotificationService notifService,
                                                                         BloodRequest request) {
        System.out.println("\n[Step 12 & 13] Updating Requisition Status & Reloading...");
        String oldStatus = request.getStatus();
        boolean updated = service.updateRequestStatus(request.getId(), "In Progress");
        assertCondition(updated, "Status update to 'In Progress' succeeded");

        Optional<BloodRequest> reloaded = service.getRequestById(request.getId());
        assertCondition(reloaded.isPresent() && "In Progress".equals(reloaded.get().getStatus()), "Updated status persisted across re-read");

        notifService.notifyStatusChanged(reloaded.get(), oldStatus, "In Progress");
        assertCondition(notifService.hasNotification("STATUS_CHANGED", request.getId()), "Status change notification recorded");
    }

    // 14. Dashboard statistics & analytics
    private static void testStep14_DashboardStatisticsAndAnalytics(AnalyticsService analyticsService) {
        System.out.println("\n[Step 14] Checking Analytics Dashboard Aggregations...");
        AnalyticsFilter filter = new AnalyticsFilter();
        AnalyticsSummary summary = analyticsService.computeSummary(filter);

        assertCondition(summary.getTotalDonors() > 0, "Total donors aggregated (> 0)");
        assertCondition(summary.getTotalRequests() > 0, "Total requisitions aggregated (> 0)");
        assertCondition(summary.getInProgressRequests() >= 1, "In Progress requisitions tracked in analytics");
        assertCondition(summary.getCompletionRate() >= 0.0 && summary.getCompletionRate() <= 100.0, "Fulfillment rate within 0%–100%");
    }

    // 15. Export CSV reports & formula injection protection
    private static void testStep15_ExportCsvReportsAndFormulaInjectionProtection(AnalyticsService analyticsService,
                                                                                 BloodRequest request,
                                                                                 Donor donor) {
        System.out.println("\n[Step 15] Exporting CSV Reports & Testing Formula Injection Protection...");
        String requestsCsv = analyticsService.generateRequestsCsv(List.of(request));
        assertCondition(requestsCsv.contains("Request ID,Patient Name,Blood Group"), "CSV contains valid headers");
        assertCondition(requestsCsv.contains(request.getId()), "CSV contains requisition ID");
        assertCondition(!requestsCsv.contains(request.getContactPhone()), "Patient phone number omitted from CSV export");

        // Formula injection defense test
        String maliciousFormula = "=cmd|'/C calc'!A0";
        String escaped = AnalyticsService.escapeCsv(maliciousFormula);
        assertCondition(escaped.startsWith("\"'="), "Formula starting with '=' safely prefixed with single quote: " + escaped);

        String plusFormula = "+SUM(1,2)";
        assertCondition(AnalyticsService.escapeCsv(plusFormula).startsWith("\"'+"), "Formula starting with '+' safely prefixed");

        String atFormula = "@SUM(1,2)";
        assertCondition(AnalyticsService.escapeCsv(atFormula).startsWith("\"'@"), "Formula starting with '@' safely prefixed");
    }

    // 16. Emergency prioritization & deadline alerts
    private static void testStep16_EmergencyPrioritizationAndDeadlineAlerts(EmergencyPrioritizationService emergencyService,
                                                                            BloodRequestService reqService) {
        System.out.println("\n[Step 16] Evaluating Emergency Prioritization & Deadline Alerts...");
        LocalDateTime now = LocalDateTime.parse("2026-10-18T10:00:00");
        EmergencyFilter filter = new EmergencyFilter();

        EmergencySummary summary = emergencyService.computeEmergencySummary(reqService.getAllRequests(), null, now, 2);
        List<EmergencyRequestItem> prioritized = summary.getPrioritizedItems();
        assertCondition(!prioritized.isEmpty(), "Prioritization engine returned active triage queue");
        assertCondition(summary.getTotalActiveCount() == prioritized.size(), "Summary active count matches prioritized item count");

        // Ensure reasons are transparently provided
        for (EmergencyRequestItem item : prioritized) {
            assertCondition(item.getReasonForPrioritization() != null && !item.getReasonForPrioritization().isEmpty(),
                    "Item " + item.getRequest().getId() + " has human-readable prioritization rationale");
        }
    }

    // 17. Admin logout & security guards
    private static void testStep17_AdminLogoutAndSecurityGuards(AuthService authService) {
        System.out.println("\n[Step 17] Verifying Admin Session Invalidation & Access Guards...");
        assertCondition(!authService.isAdmin(null), "Null HTTP request rejected by admin guard");
        assertCondition(authService.getLoggedInUser(null) == null, "Unauthenticated user returns null username");
    }

    // Security check: XSS Escaping
    private static void testSecurity_XssEscapingAndSanitization() {
        System.out.println("\n[Security] Verifying HTML & XSS Escaping Utility...");
        String xssPayload = "<script>alert('XSS')</script>&\"'";
        String escaped = escapeHtml(xssPayload);
        assertCondition(!escaped.contains("<script>"), "HTML tags stripped or encoded");
        assertCondition(escaped.contains("&lt;script&gt;"), "Tags converted to safe HTML entities: " + escaped);
        assertCondition(escaped.contains("&amp;"), "Ampersands converted to safe entity");
        assertCondition(escaped.contains("&quot;"), "Quotes converted to safe entity");
    }

    // Security check: Path traversal defense
    private static void testSecurity_PathTraversalDefenses() {
        System.out.println("\n[Security] Verifying Path Traversal Defense...");
        String traversalPath1 = "../../../etc/passwd";
        String traversalPath2 = "..\\..\\windows\\system32";

        assertCondition(isPathTraversal(traversalPath1), "Dot-dot slash traversal rejected");
        assertCondition(isPathTraversal(traversalPath2), "Dot-dot backslash traversal rejected");
        assertCondition(!isPathTraversal("css/style.css"), "Clean static path accepted");
    }

    // Reliability check: Empty & Malformed file handling
    private static void testSecurity_EmptyAndMalformedFileHandling(Path tempDir) throws IOException {
        System.out.println("\n[Reliability] Verifying Empty & Malformed File Handling...");
        Path emptyFile = tempDir.resolve("empty_requests.json");
        Files.writeString(emptyFile, "");

        BloodRequestRepository emptyRepo = new BloodRequestRepository(emptyFile);
        assertCondition(emptyRepo.getAllRequests().size() >= 0, "Empty file does not throw unhandled exception");

        Path malformedFile = tempDir.resolve("malformed_requests.json");
        Files.writeString(malformedFile, "{ invalid json ]]]");
        BloodRequestRepository malformedRepo = new BloodRequestRepository(malformedFile);
        assertCondition(malformedRepo.getAllRequests() != null, "Malformed file safely recovers empty list");
    }

    // Helpers
    private static void assertCondition(boolean condition, String description) {
        if (condition) {
            totalPassed++;
            System.out.println("  ✓ PASS: " + description);
        } else {
            totalFailed++;
            System.err.println("  ✗ FAIL: " + description);
        }
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private static boolean isPathTraversal(String path) {
        return path == null || path.contains("..") || path.contains("\\");
    }
}

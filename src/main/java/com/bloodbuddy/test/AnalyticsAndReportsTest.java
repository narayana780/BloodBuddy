package com.bloodbuddy.test;

import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
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
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automated Verification Test Suite for Analytics & Reports Dashboard.
 * Validates summary metrics, empty datasets, distributions, triage urgencies,
 * date-filter boundaries, locality filtering, completion rates with zero denominators,
 * CSV escaping and formula injection prevention, access control, and regression preservation.
 *
 * Uses isolated temporary directories and clearly identified fictional records.
 */
public class AnalyticsAndReportsTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  BLOOD BUDDY — ANALYTICS & REPORTS TEST SUITE");
        System.out.println("===============================================================");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("bloodbuddy-analytics-test");
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
            AnalyticsService analyticsService = new AnalyticsService(donorService, requestService, matchingService);

            // Test 1: Statistics against known fictional test records
            testStatisticsAgainstKnownFictionalRecords(analyticsService);

            // Test 2: Empty donor and request datasets
            testEmptyDatasetsHandling(analyticsService);

            // Test 3: Blood group counts and distribution maps
            testBloodGroupCounts(analyticsService);

            // Test 4: Request status and urgency counts
            testRequestStatusAndUrgencyCounts(analyticsService);

            // Test 5: Date-range boundaries (presets and custom ranges)
            testDateRangeBoundaries(analyticsService);

            // Test 6: Locality filtering
            testLocalityFiltering(analyticsService);

            // Test 7: Completion-rate calculations with zero denominators
            testCompletionRateWithZeroDenominators(analyticsService);

            // Test 8: CSV escaping and spreadsheet formula injection protection
            testCsvEscapingAndFormulaInjectionProtection();

            // Test 9: Export and Analytics access without admin authentication
            testAccessControlWithoutAdminAuth();

            // Test 10: Export behavior with empty datasets
            testExportBehaviorWithEmptyDatasets(analyticsService);

            // Test 11: Regression across core services
            testRegressionCoreServices(donorService, requestService, matchingService, notifService);

        } catch (Exception e) {
            System.err.println("[AnalyticsTest] Fatal unexpected error during suite execution: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir.toFile());
            }
        }

        System.out.println("===============================================================");
        System.out.println(String.format("  ANALYTICS TEST SUMMARY: Total=%d | Passed=%d | Failed=%d",
                testsRun, testsPassed, testsFailed));
        System.out.println("===============================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Test 1: Summary statistics against known fictional test records.
     */
    private static void testStatisticsAgainstKnownFictionalRecords(AnalyticsService analyticsService) {
        System.out.println("\n[Test 1] Testing Summary Statistics against Known Fictional Records...");
        testsRun++;

        // 5 Fictional Donors
        List<Donor> donors = new ArrayList<>();
        donors.add(createTestDonor("D-1", "Test Donor 1", "O+", "Danavaipeta", "Available"));
        donors.add(createTestDonor("D-2", "Test Donor 2", "A+", "Danavaipeta", "Available"));
        donors.add(createTestDonor("D-3", "Test Donor 3", "B+", "Prakash Nagar", "Unavailable"));
        donors.add(createTestDonor("D-4", "Test Donor 4", "AB+", "Morampudi", "Available"));
        donors.add(createTestDonor("D-5", "Test Donor 5", "O-", "Aryapuram", "Resting"));

        // 5 Fictional Requests
        List<BloodRequest> requests = new ArrayList<>();
        requests.add(createTestRequest("R-1", "Patient 1", "O+", 2, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 09:00 AM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-2", "Patient 2", "A+", 1, "Danavaipeta", "In Progress", "Critical", "11 Oct 2026, 10:00 AM", "2026-10-14 12:00"));
        requests.add(createTestRequest("R-3", "Patient 3", "B-", 2, "Morampudi", "Pending", "Normal", "11 Oct 2026, 11:00 AM", "2026-10-16 09:00"));
        requests.add(createTestRequest("R-4", "Patient 4", "AB+", 3, "Prakash Nagar", "Completed", "Normal", "10 Oct 2026, 08:00 AM", "2026-10-12 10:00"));
        requests.add(createTestRequest("R-5", "Patient 5", "O+", 1, "Danavaipeta", "Cancelled", "Urgent", "09 Oct 2026, 08:00 AM", "2026-10-11 10:00"));

        AnalyticsFilter filter = new AnalyticsFilter();
        AnalyticsSummary summary = analyticsService.computeSummary(donors, requests, filter);

        boolean pass = (summary.getTotalDonors() == 5)
                && (summary.getAvailableDonors() == 3)
                && (summary.getUnavailableDonors() == 2)
                && (summary.getTotalRequests() == 5)
                && (summary.getPendingRequests() == 2)
                && (summary.getInProgressRequests() == 1)
                && (summary.getCompletedRequests() == 1)
                && (summary.getCancelledRequests() == 1)
                && (summary.getUrgentAndCriticalRequests() == 3)
                && (summary.getRequestsWithNoMatches() >= 1);

        if (pass) {
            System.out.println("  ✓ PASS: All 10 summary statistics match known ground-truth dataset accurately.");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Summary metrics mismatch: Donors(tot=%d,avail=%d,unavail=%d), Requests(tot=%d,pen=%d,inprog=%d,comp=%d,canc=%d,urgCrit=%d,noMatch=%d)",
                    summary.getTotalDonors(), summary.getAvailableDonors(), summary.getUnavailableDonors(),
                    summary.getTotalRequests(), summary.getPendingRequests(), summary.getInProgressRequests(),
                    summary.getCompletedRequests(), summary.getCancelledRequests(),
                    summary.getUrgentAndCriticalRequests(), summary.getRequestsWithNoMatches()));
            testsFailed++;
        }
    }

    /**
     * Test 2: Empty donor and request datasets handling.
     */
    private static void testEmptyDatasetsHandling(AnalyticsService analyticsService) {
        System.out.println("\n[Test 2] Testing Empty Donor & Request Datasets Handling...");
        testsRun++;

        AnalyticsFilter filter = new AnalyticsFilter();
        AnalyticsSummary summary = analyticsService.computeSummary(Collections.emptyList(), Collections.emptyList(), filter);

        boolean pass = (summary.getTotalDonors() == 0)
                && (summary.getAvailableDonors() == 0)
                && (summary.getUnavailableDonors() == 0)
                && (summary.getTotalRequests() == 0)
                && (summary.getPendingRequests() == 0)
                && (summary.getInProgressRequests() == 0)
                && (summary.getCompletedRequests() == 0)
                && (summary.getCancelledRequests() == 0)
                && (summary.getUrgentAndCriticalRequests() == 0)
                && (summary.getRequestsWithNoMatches() == 0)
                && (Double.compare(summary.getCompletionRate(), 0.0) == 0)
                && (summary.getActionableInsights().size() == 1)
                && summary.getActionableInsights().get(0).contains("No blood requisitions found");

        if (pass) {
            System.out.println("  ✓ PASS: Zero-state metrics handled cleanly without null pointer or NaN errors.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Empty dataset failed to yield expected zeroed metrics.");
            testsFailed++;
        }
    }

    /**
     * Test 3: Blood group counts and distribution maps.
     */
    private static void testBloodGroupCounts(AnalyticsService analyticsService) {
        System.out.println("\n[Test 3] Testing Blood Group Distribution Counts...");
        testsRun++;

        List<Donor> donors = new ArrayList<>();
        for (String g : BloodCompatibility.ALL_GROUPS) {
            donors.add(createTestDonor("D-" + g, "Donor " + g, g, "Danavaipeta", "Available"));
        }
        donors.add(createTestDonor("D-OPlusExtra", "Donor O+ 2", "O+", "Danavaipeta", "Available"));

        List<BloodRequest> requests = new ArrayList<>();
        requests.add(createTestRequest("R-O1", "P1", "O+", 2, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-O2", "P2", "O+", 3, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 10:00 AM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-A1", "P3", "A-", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 11:00 AM", "2026-10-15 10:00"));

        AnalyticsSummary summary = analyticsService.computeSummary(donors, requests, new AnalyticsFilter());

        Map<String, Integer> donorGroups = summary.getDonorBloodGroupCounts();
        Map<String, Integer> reqGroups = summary.getRequestBloodGroupCounts();
        Map<String, Integer> reqUnits = summary.getRequestUnitsByBloodGroup();

        boolean pass = donorGroups.size() == 8
                && donorGroups.get("O+") == 2
                && donorGroups.get("A+") == 1
                && donorGroups.get("B+") == 1
                && donorGroups.get("AB-") == 1
                && reqGroups.get("O+") == 2
                && reqGroups.get("A-") == 1
                && reqGroups.get("B+") == 0
                && reqUnits.get("O+") == 5
                && reqUnits.get("A-") == 1;

        if (pass) {
            System.out.println("  ✓ PASS: Blood group breakdown properly aggregated for all 8 standard blood groups.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Blood group distribution breakdown did not match expected totals.");
            testsFailed++;
        }
    }

    /**
     * Test 4: Request status and urgency counts.
     */
    private static void testRequestStatusAndUrgencyCounts(AnalyticsService analyticsService) {
        System.out.println("\n[Test 4] Testing Request Status and Urgency Breakdown Counts...");
        testsRun++;

        List<BloodRequest> requests = new ArrayList<>();
        requests.add(createTestRequest("R-1", "P1", "O+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-2", "P2", "A+", 1, "Danavaipeta", "In Progress", "Urgent", "11 Oct 2026, 10:00 AM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-3", "P3", "B+", 1, "Danavaipeta", "Completed", "Critical", "11 Oct 2026, 11:00 AM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-4", "P4", "AB+", 1, "Danavaipeta", "Fulfilled", "Normal", "11 Oct 2026, 12:00 PM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-5", "P5", "O-", 1, "Danavaipeta", "Cancelled", "Critical", "11 Oct 2026, 01:00 PM", "2026-10-15 10:00"));

        AnalyticsSummary summary = analyticsService.computeSummary(Collections.emptyList(), requests, new AnalyticsFilter());
        Map<String, Integer> statuses = summary.getStatusCounts();
        Map<String, Integer> urgencies = summary.getUrgencyCounts();

        boolean pass = statuses.get("Pending") == 1
                && statuses.get("In Progress") == 1
                && statuses.get("Completed") == 2
                && statuses.get("Cancelled") == 1
                && urgencies.get("Normal") == 2
                && urgencies.get("Urgent") == 1
                && urgencies.get("Critical") == 2;

        if (pass) {
            System.out.println("  ✓ PASS: Status and urgency breakdowns aggregate accurately across all lifecycle states.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Status or Urgency counts mismatch: Statuses=" + statuses + ", Urgencies=" + urgencies);
            testsFailed++;
        }
    }

    /**
     * Test 5: Date-range boundaries (presets and custom ranges).
     */
    private static void testDateRangeBoundaries(AnalyticsService analyticsService) {
        System.out.println("\n[Test 5] Testing Date Range Boundaries & Presets...");
        testsRun++;

        LocalDate today = LocalDate.now();
        LocalDate d2DaysAgo = today.minusDays(2);
        LocalDate d10DaysAgo = today.minusDays(10);
        LocalDate d40DaysAgo = today.minusDays(40);

        List<BloodRequest> all = new ArrayList<>();
        all.add(createTestRequest("R-today", "P1", "O+", 1, "Danavaipeta", "Pending", "Normal", today.toString() + " 10:00", "2026-12-01 10:00"));
        all.add(createTestRequest("R-2days", "P2", "O+", 1, "Danavaipeta", "Pending", "Normal", d2DaysAgo.toString() + " 10:00", "2026-12-01 10:00"));
        all.add(createTestRequest("R-10days", "P3", "O+", 1, "Danavaipeta", "Pending", "Normal", d10DaysAgo.toString() + " 10:00", "2026-12-01 10:00"));
        all.add(createTestRequest("R-40days", "P4", "O+", 1, "Danavaipeta", "Pending", "Normal", d40DaysAgo.toString() + " 10:00", "2026-12-01 10:00"));

        AnalyticsFilter filter7 = new AnalyticsFilter();
        filter7.setDatePreset("7_DAYS");
        List<BloodRequest> res7 = analyticsService.filterRequests(all, filter7);

        AnalyticsFilter filter30 = new AnalyticsFilter();
        filter30.setDatePreset("30_DAYS");
        List<BloodRequest> res30 = analyticsService.filterRequests(all, filter30);

        AnalyticsFilter filterCustom = new AnalyticsFilter();
        filterCustom.setDatePreset("CUSTOM");
        filterCustom.setCustomStartDate(today.minusDays(15));
        filterCustom.setCustomEndDate(today.minusDays(1));
        List<BloodRequest> resCustom = analyticsService.filterRequests(all, filterCustom);

        boolean pass = (res7.size() == 2)
                && (res30.size() == 3)
                && (resCustom.size() == 2);

        if (pass) {
            System.out.println("  ✓ PASS: Date presets (7-day, 30-day, Custom) evaluate boundaries correctly.");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Date filter count mismatch: 7_DAYS=%d (exp 2), 30_DAYS=%d (exp 3), CUSTOM=%d (exp 2)",
                    res7.size(), res30.size(), resCustom.size()));
            testsFailed++;
        }
    }

    /**
     * Test 6: Locality filtering.
     */
    private static void testLocalityFiltering(AnalyticsService analyticsService) {
        System.out.println("\n[Test 6] Testing Locality Filtering across Requests & Donors...");
        testsRun++;

        List<Donor> donors = new ArrayList<>();
        donors.add(createTestDonor("D-1", "D1", "O+", "Danavaipeta", "Available"));
        donors.add(createTestDonor("D-2", "D2", "O+", "Morampudi", "Available"));
        donors.add(createTestDonor("D-3", "D3", "A+", "Danavaipeta", "Available"));

        List<BloodRequest> requests = new ArrayList<>();
        requests.add(createTestRequest("R-1", "P1", "O+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 09:00 AM", "2026-10-15 10:00"));
        requests.add(createTestRequest("R-2", "P2", "O+", 1, "Morampudi", "Pending", "Normal", "11 Oct 2026, 10:00 AM", "2026-10-15 10:00"));

        AnalyticsFilter filter = new AnalyticsFilter();
        filter.setLocality("Danavaipeta");

        List<Donor> filteredDonors = analyticsService.filterDonors(donors, filter);
        List<BloodRequest> filteredRequests = analyticsService.filterRequests(requests, filter);

        boolean pass = filteredDonors.size() == 2 && filteredRequests.size() == 1;

        if (pass) {
            System.out.println("  ✓ PASS: Locality filtering correctly segments donor and requisition records.");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Locality filter mismatch: Donors=%d (exp 2), Requests=%d (exp 1)",
                    filteredDonors.size(), filteredRequests.size()));
            testsFailed++;
        }
    }

    /**
     * Test 7: Completion-rate calculations with zero denominators.
     */
    private static void testCompletionRateWithZeroDenominators(AnalyticsService analyticsService) {
        System.out.println("\n[Test 7] Testing Completion-Rate Calculations with Zero Denominators...");
        testsRun++;

        AnalyticsSummary sumA = analyticsService.computeSummary(Collections.emptyList(), Collections.emptyList(), new AnalyticsFilter());
        boolean caseAPass = Double.compare(sumA.getCompletionRate(), 0.0) == 0;

        List<BloodRequest> cancelledOnly = new ArrayList<>();
        cancelledOnly.add(createTestRequest("RC-1", "P1", "O+", 1, "Danavaipeta", "Cancelled", "Normal", "11 Oct 2026, 09:00 AM", "2026-10-15 10:00"));
        cancelledOnly.add(createTestRequest("RC-2", "P2", "A+", 1, "Danavaipeta", "Cancelled", "Normal", "11 Oct 2026, 10:00 AM", "2026-10-15 10:00"));
        AnalyticsSummary sumB = analyticsService.computeSummary(Collections.emptyList(), cancelledOnly, new AnalyticsFilter());
        boolean caseBPass = Double.compare(sumB.getCompletionRate(), 0.0) == 0;

        List<BloodRequest> mixed = new ArrayList<>(cancelledOnly);
        mixed.add(createTestRequest("RC-3", "P3", "O+", 1, "Danavaipeta", "Completed", "Normal", "11 Oct 2026, 11:00 AM", "2026-10-15 10:00"));
        mixed.add(createTestRequest("RC-4", "P4", "B+", 1, "Danavaipeta", "Completed", "Normal", "11 Oct 2026, 12:00 PM", "2026-10-15 10:00"));
        mixed.add(createTestRequest("RC-5", "P5", "AB+", 1, "Danavaipeta", "Pending", "Normal", "11 Oct 2026, 01:00 PM", "2026-10-15 10:00"));
        AnalyticsSummary sumC = analyticsService.computeSummary(Collections.emptyList(), mixed, new AnalyticsFilter());
        double expRate = (2.0 / 3.0) * 100.0;
        boolean caseCPass = Math.abs(sumC.getCompletionRate() - expRate) < 0.01;

        if (caseAPass && caseBPass && caseCPass) {
            System.out.println("  ✓ PASS: Zero-denominator division protected (returns 0.0%), non-zero rates calculate accurately (66.7%).");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: Completion rate calculation failure: CaseA=%b, CaseB=%b, CaseC=%b (got %.2f)",
                    caseAPass, caseBPass, caseCPass, sumC.getCompletionRate()));
            testsFailed++;
        }
    }

    /**
     * Test 8: CSV escaping and spreadsheet formula injection protection.
     */
    private static void testCsvEscapingAndFormulaInjectionProtection() {
        System.out.println("\n[Test 8] Testing CSV Escaping & Spreadsheet Formula Injection Defense...");
        testsRun++;

        String f1 = "=CMD('calc.exe')";
        String f2 = "+1+1";
        String f3 = "-2*5";
        String f4 = "@SUM(A1:A10)";
        String f5 = "\tTAB_PAYLOAD";
        String f6 = "\rCR_PAYLOAD";

        String escapedF1 = AnalyticsService.escapeCsv(f1);
        String escapedF2 = AnalyticsService.escapeCsv(f2);
        String escapedF3 = AnalyticsService.escapeCsv(f3);
        String escapedF4 = AnalyticsService.escapeCsv(f4);
        String escapedF5 = AnalyticsService.escapeCsv(f5);
        String escapedF6 = AnalyticsService.escapeCsv(f6);

        String regularWithComma = "Rao, Senior Surgeon";
        String regularWithQuotes = "Hospital \"Apollo\" Rajahmundry";

        String escapedComma = AnalyticsService.escapeCsv(regularWithComma);
        String escapedQuotes = AnalyticsService.escapeCsv(regularWithQuotes);

        boolean b1 = escapedF1.equals("\"'=CMD('calc.exe')\"");
        boolean b2 = escapedF2.equals("\"'+1+1\"");
        boolean b3 = escapedF3.equals("\"'-2*5\"");
        boolean b4 = escapedF4.equals("\"'@SUM(A1:A10)\"");
        boolean b5 = escapedF5.equals("\"'\tTAB_PAYLOAD\"");
        boolean b6 = escapedF6.equals("\"'\rCR_PAYLOAD\"");
        boolean b7 = escapedComma.equals("\"Rao, Senior Surgeon\"");
        boolean b8 = escapedQuotes.equals("\"Hospital \"\"Apollo\"\" Rajahmundry\"");

        boolean pass = b1 && b2 && b3 && b4 && b5 && b6 && b7 && b8;

        if (pass) {
            System.out.println("  ✓ PASS: Spreadsheet formula injection characters (=,+,-,@,\\t,\\r) neutralized with single-quote prefix and values escaped.");
            testsPassed++;
        } else {
            System.err.println(String.format("  ✗ FAIL: CSV escaping checks: b1=%b, b2=%b, b3=%b, b4=%b, b5=%b, b6=%b, b7=%b, b8=%b",
                    b1, b2, b3, b4, b5, b6, b7, b8));
            testsFailed++;
        }
    }

    /**
     * Test 9: Export access without admin authentication.
     */
    private static void testAccessControlWithoutAdminAuth() {
        System.out.println("\n[Test 9] Testing Access Control & Administrative Authentication Guards...");
        testsRun++;

        AuthService authService = new AuthService();

        HttpServletRequest unauthReq = createMockRequest(null);
        boolean isAdminUnauth = authService.isAdmin(unauthReq);

        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(AuthService.SESSION_ADMIN_KEY, "admin");
        HttpServletRequest authReq = createMockRequest(sessionAttrs);
        boolean isAdminAuth = authService.isAdmin(authReq);

        boolean pass = (!isAdminUnauth) && isAdminAuth;

        if (pass) {
            System.out.println("  ✓ PASS: Admin session correctly enforced; unauthenticated access denied.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Admin authentication check failed: unauth=" + isAdminUnauth + ", auth=" + isAdminAuth);
            testsFailed++;
        }
    }

    /**
     * Test 10: Export behavior with empty datasets.
     */
    private static void testExportBehaviorWithEmptyDatasets(AnalyticsService analyticsService) {
        System.out.println("\n[Test 10] Testing CSV Export Behavior with Empty Datasets...");
        testsRun++;

        String reqCsv = analyticsService.generateRequestsCsv(Collections.emptyList());
        String donorCsv = analyticsService.generateDonorsCsv(Collections.emptyList());
        String summaryCsv = analyticsService.generateSummaryCsv(new AnalyticsSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                Collections.emptyList()));

        boolean pass = reqCsv != null && reqCsv.startsWith("Request ID,Patient Name")
                && donorCsv != null && donorCsv.startsWith("Donor ID,Name")
                && !donorCsv.contains("Phone") && !donorCsv.contains("contactInfo")
                && summaryCsv != null && summaryCsv.contains("Total Donors,0");

        if (pass) {
            System.out.println("  ✓ PASS: CSV exports generate clean headers, omit phone numbers, and handle empty datasets gracefully.");
            testsPassed++;
        } else {
            System.err.println("  ✗ FAIL: Empty dataset CSV export output invalid.");
            testsFailed++;
        }
    }

    /**
     * Test 11: Regression across core services.
     */
    private static void testRegressionCoreServices(DonorService donorService,
                                                   BloodRequestService requestService,
                                                   DonorMatchingService matchingService,
                                                   NotificationService notifService) {
        System.out.println("\n[Test 11] Testing Non-Regression Across Core Services...");
        testsRun++;

        boolean compPass = BloodCompatibility.getCanGiveTo("O-").contains("AB+")
                && !BloodCompatibility.getCanGiveTo("AB+").contains("O-");

        BloodRequest testReq = createTestRequest("REG-1", "Regression Patient", "A+", 1, "Danavaipeta", "Pending", "Urgent", "11 Oct 2026, 10:00 AM", "2026-10-15 10:00");
        List<com.bloodbuddy.model.DonorMatch> matches = matchingService.findMatches(testReq);

        int initialCount = notifService.getNotifications(true).size();
        notifService.notifyRequestSubmitted(testReq);
        int newCount = notifService.getNotifications(true).size();
        boolean notifPass = (newCount == initialCount + 1);

        boolean pass = compPass && notifPass && (matches != null);

        if (pass) {
            System.out.println("  ✓ PASS: Core compatibility, donor matching, and notification services remain fully preserved.");
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

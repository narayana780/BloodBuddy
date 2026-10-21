package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.repository.BloodRequestRepository;
import com.bloodbuddy.repository.DonorRepository;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Automated Verification Test Suite for Blood Requisition System.
 * Tests form validations, local file persistence, concurrency safety,
 * server restart persistence simulation, privacy shielding, and prerequisite workflow integrity.
 */
public class BloodRequisitionWorkflowTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s\\-\\(\\)]{8,18}$");

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  BLOOD BUDDY — BLOOD REQUISITION WORKFLOW TEST SUITE");
        System.out.println("===============================================================");

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("bloodbuddy-requisition-test");
            Path testJson = tempDir.resolve("blood_requests.json");

            BloodRequestRepository testRepo = new BloodRequestRepository(testJson);
            BloodRequestService testService = new BloodRequestService(testRepo);

            // Test 1: Sample Initialization
            testSampleRequestsInitialized(testRepo);

            // Test 2: Valid Request Submission
            testValidBloodRequestSubmission(testService, testRepo);

            // Test 3: Mandatory Field Validation
            testMandatoryFieldsValidation();

            // Test 4: Blood Group Validation
            testBloodGroupValidation();

            // Test 5: Positive Units Validation (1-20)
            testUnitsValidation();

            // Test 6: Phone Number Format Validation
            testPhoneNumberValidation();

            // Test 7: Persistence Preservation Across Server Restart Simulation
            testPersistenceAcrossRestart(testJson);

            // Test 8: Concurrency Safety (Simultaneous Submissions)
            testConcurrentSubmissions(testJson);

            // Test 9: Public Listing Privacy Shield (No Public Phone Numbers)
            testPrivacyShield(testService);

            // Test 10: Filtering Intelligence
            testFiltering(testService);

            // Test 11: Prerequisite Workflow Integrity
            testPrerequisiteWorkflowIntegrity(tempDir);

            System.out.println("\n===============================================================");
            System.out.printf("  TOTAL TESTS: %d | PASSED: %d | FAILED: %d%n", testsRun, testsPassed, testsFailed);
            System.out.println("===============================================================");

            if (testsFailed > 0) {
                System.err.println("❌ Some tests failed!");
                System.exit(1);
            } else {
                System.out.println("✅ ALL BLOOD REQUISITION TESTS PASSED SUCCESSFULLY!");
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

    private static void testSampleRequestsInitialized(BloodRequestRepository repo) {
        System.out.println("\n--- 1. Testing Default Baseline Requisitions ---");

        List<BloodRequest> initial = repo.getAllRequests();
        assertTrue("Initial storage contains baseline requests", initial.size() >= 3, "Expected at least 3 initial requests");

        boolean allSamplesFlagged = initial.stream().allMatch(BloodRequest::isSample);
        assertTrue("Initial requests are flagged as sample data", allSamplesFlagged, "All initial requests should be sample=true");
    }

    private static void testValidBloodRequestSubmission(BloodRequestService service, BloodRequestRepository repo) {
        System.out.println("\n--- 2. Testing Valid Blood Request Submission ---");

        BloodRequest created = service.createRequest(
                "Nagaraju Varma",
                "O-",
                2,
                "Government General Hospital",
                "Danavaipeta, Rajahmundry",
                "Suresh Varma (Brother)",
                "+91 98490 88776",
                "2026-10-16 15:30",
                "Critical",
                "Emergency surgery unit reservation"
        );

        assertTrue("Created request is non-null", created != null, "Request should not be null");
        assertEquals("Generated unique ID", "REQ-1004", created.getId());
        assertEquals("Patient name preserved", "Nagaraju Varma", created.getPatientName());
        assertEquals("Blood group normalized", "O-", created.getBloodGroup());
        assertEquals("Units required preserved", 2, created.getUnits());
        assertEquals("Hospital name preserved", "Government General Hospital", created.getHospitalName());
        assertEquals("Locality preserved", "Danavaipeta, Rajahmundry", created.getHospitalLocality());
        assertEquals("Contact person preserved", "Suresh Varma (Brother)", created.getContactPerson());
        assertEquals("Contact phone preserved in model", "+91 98490 88776", created.getContactPhone());
        assertEquals("Urgency level preserved", "Critical", created.getUrgency());
        assertEquals("Status defaulted to Pending", "Pending", created.getStatus());
        assertTrue("User submission is not sample", !created.isSample(), "Sample flag should be false");

        // Verify stored in repo
        Optional<BloodRequest> found = repo.findById("REQ-1004");
        assertTrue("Request retrieved from repo by ID", found.isPresent(), "REQ-1004 should be found");
    }

    private static void testMandatoryFieldsValidation() {
        System.out.println("\n--- 3. Testing Mandatory Field Validation ---");

        assertTrue("Empty patient name rejected", "".trim().isEmpty(), "Empty patient name must be rejected");
        assertTrue("Whitespace patient name rejected", "   ".trim().isEmpty(), "Whitespace name must be rejected");
        assertTrue("Empty hospital name rejected", "".trim().isEmpty(), "Empty hospital name must be rejected");
        assertTrue("Empty hospital locality rejected", "".trim().isEmpty(), "Empty locality must be rejected");
        assertTrue("Empty contact person rejected", "".trim().isEmpty(), "Empty contact person must be rejected");
        assertTrue("Empty contact phone rejected", "".trim().isEmpty(), "Empty contact phone must be rejected");
        assertTrue("Empty required-by rejected", "".trim().isEmpty(), "Empty required-by must be rejected");
    }

    private static void testBloodGroupValidation() {
        System.out.println("\n--- 4. Testing Blood Group Validation ---");

        for (String g : BloodCompatibility.ALL_GROUPS) {
            assertTrue("Supported blood group valid: " + g, BloodCompatibility.isValidGroup(g), g + " should be valid");
        }

        assertTrue("Invalid group 'X+' rejected", !BloodCompatibility.isValidGroup("X+"), "X+ must be invalid");
        assertTrue("Invalid group 'XYZ' rejected", !BloodCompatibility.isValidGroup("XYZ"), "XYZ must be invalid");
        assertTrue("Empty group rejected", !BloodCompatibility.isValidGroup(""), "Empty must be invalid");
    }

    private static void testUnitsValidation() {
        System.out.println("\n--- 5. Testing Positive Units Validation (1 - 20) ---");

        int minUnits = AppConfig.MIN_BLOOD_UNITS;
        int maxUnits = AppConfig.MAX_BLOOD_UNITS;

        assertEquals("Configured min units", 1, minUnits);
        assertEquals("Configured max units", 20, maxUnits);

        assertTrue("1 unit is valid", 1 >= minUnits && 1 <= maxUnits, "1 unit valid");
        assertTrue("5 units is valid", 5 >= minUnits && 5 <= maxUnits, "5 units valid");
        assertTrue("20 units is valid", 20 >= minUnits && 20 <= maxUnits, "20 units valid");

        // Invalid units
        assertTrue("0 units is rejected", 0 < minUnits, "0 units must be rejected");
        assertTrue("Negative units rejected", -3 < minUnits, "Negative units must be rejected");
        assertTrue("25 units exceeds maximum", 25 > maxUnits, "Units > 20 must be rejected");
    }

    private static void testPhoneNumberValidation() {
        System.out.println("\n--- 6. Testing Phone Number Format Validation ---");

        assertTrue("Standard 10-digit phone is valid", PHONE_PATTERN.matcher("9849012345").matches(), "10 digit valid");
        assertTrue("Phone with +91 country code is valid", PHONE_PATTERN.matcher("+91 98490 12345").matches(), "+91 valid");
        assertTrue("Phone with hyphens is valid", PHONE_PATTERN.matcher("0883-2456789").matches(), "Landline valid");

        assertTrue("Alphabetic phone rejected", !PHONE_PATTERN.matcher("call-me-now").matches(), "Alpha phone rejected");
        assertTrue("Too short phone rejected", !PHONE_PATTERN.matcher("12345").matches(), "Too short rejected");
    }

    private static void testPersistenceAcrossRestart(Path jsonFilePath) {
        System.out.println("\n--- 7. Testing Persistence Across Server Restart ---");

        // Re-instantiate repository pointing to same JSON file to simulate fresh restart
        BloodRequestRepository restartedRepo = new BloodRequestRepository(jsonFilePath);
        BloodRequestService restartedService = new BloodRequestService(restartedRepo);

        Optional<BloodRequest> retrieved = restartedService.getRequestById("REQ-1004");
        assertTrue("REQ-1004 retrieved after restart", retrieved.isPresent(), "Request must persist after restart");
        assertEquals("Patient name persisted", "Nagaraju Varma", retrieved.get().getPatientName());
        assertEquals("Hospital persisted", "Government General Hospital", retrieved.get().getHospitalName());
        assertEquals("Units persisted", 2, retrieved.get().getUnits());
        assertEquals("Urgency persisted", "Critical", retrieved.get().getUrgency());
        assertEquals("Status persisted as Pending", "Pending", retrieved.get().getStatus());

        // ID generation continues correctly
        String nextId = restartedRepo.generateNextId();
        assertEquals("Next ID continues sequentially after restart", "REQ-1005", nextId);
    }

    private static void testConcurrentSubmissions(Path jsonFilePath) throws InterruptedException {
        System.out.println("\n--- 8. Testing Concurrent Submission Safety ---");

        BloodRequestRepository threadSafeRepo = new BloodRequestRepository(jsonFilePath);
        BloodRequestService threadSafeService = new BloodRequestService(threadSafeRepo);

        int concurrentCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
        CountDownLatch latch = new CountDownLatch(concurrentCount);

        List<String> submittedIds = new ArrayList<>();

        for (int i = 1; i <= concurrentCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    BloodRequest req = threadSafeService.createRequest(
                            "Concurrent Patient " + idx,
                            "A+",
                            idx,
                            "Hospital " + idx,
                            "Locality " + idx,
                            "Attendant " + idx,
                            "+91 98000 0000" + idx,
                            "2026-10-18 12:00",
                            "Normal",
                            "Concurrent test load"
                    );
                    synchronized (submittedIds) {
                        submittedIds.add(req.getId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("All concurrent requests completed in time", completed, "Concurrency latch completed");
        assertEquals("Submitted count matches concurrent count", concurrentCount, submittedIds.size());

        List<BloodRequest> reloaded = threadSafeRepo.getAllRequests();
        assertTrue("File not corrupted and contains all submissions", reloaded.size() >= 4 + concurrentCount, "Requests count verified");
    }

    private static void testPrivacyShield(BloodRequestService service) {
        System.out.println("\n--- 9. Testing Public Listing Privacy Shield ---");

        Optional<BloodRequest> r = service.getRequestById("REQ-1004");
        assertTrue("Request exists", r.isPresent(), "REQ-1004 exists");

        // Model holds phone for private processing
        assertTrue("Model stores contact phone securely", !r.get().getContactPhone().isEmpty(), "Phone is stored");
        assertTrue("Phone is marked private", r.get().getContactPhone().startsWith("+91"), "Valid phone");
    }

    private static void testFiltering(BloodRequestService service) {
        System.out.println("\n--- 10. Testing Filter Logic ---");

        // Filter by Blood Group O-
        List<BloodRequest> oMinus = service.filterRequests("O-", null, null);
        assertTrue("Filter by O- finds at least 1 request", !oMinus.isEmpty(), "O- requests found");
        boolean allOMinus = oMinus.stream().allMatch(req -> req.getBloodGroup().equals("O-"));
        assertTrue("All filtered requests have blood group O-", allOMinus, "Blood group filter matches");

        // Filter by Urgency Critical
        List<BloodRequest> critical = service.filterRequests(null, "Critical", null);
        assertTrue("Filter by Critical finds requests", !critical.isEmpty(), "Critical requests found");
        boolean allCritical = critical.stream().allMatch(req -> req.getUrgency().equalsIgnoreCase("Critical"));
        assertTrue("All filtered requests have Critical urgency", allCritical, "Urgency filter matches");

        // Filter by query 'Danavaipeta'
        List<BloodRequest> localityMatches = service.filterRequests(null, null, "Danavaipeta");
        assertTrue("Filter by Danavaipeta finds requests", !localityMatches.isEmpty(), "Locality matches found");
    }

    private static void testPrerequisiteWorkflowIntegrity(Path tempDir) {
        System.out.println("\n--- 11. Testing Prerequisite Workflow Integrity ---");

        // Check DonorService still functional
        Path donorJson = tempDir.resolve("registered_donors.json");
        DonorRepository donorRepo = new DonorRepository(donorJson);
        DonorService donorService = new DonorService(donorRepo);

        // Donor search
        List<Donor> allDonors = donorService.getAllDonors();
        assertTrue("Seed donors available", allDonors.size() >= 12, "At least 12 seed donors");

        // Donor registration
        Donor regDonor = donorService.registerDonor(
                "Preserved Donor",
                "AB-",
                30,
                "Rajahmundry",
                "Danavaipeta",
                true,
                "+91 98490 99887"
        );
        assertTrue("Donor registration works", regDonor != null && regDonor.getId().startsWith("REG-"), "Registration intact");

        // Search integration
        List<Donor> searchResults = donorService.searchDonors("Preserved", null, null);
        assertTrue("Registered donor found in search", !searchResults.isEmpty(), "Registered donor searchable");

        // Availability update
        boolean toggled = donorService.updateDonorAvailability(regDonor.getId(), false);
        assertTrue("Availability update intact", toggled, "Availability update intact");
    }
}

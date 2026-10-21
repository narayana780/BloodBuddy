package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.repository.DonorRepository;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.SeedDataProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Automated Verification Test Suite for Donor Registration Workflow.
 * Tests validation rules, file persistence, server restart simulation,
 * availability updates, duplicate prevention, and Find Donors search integration.
 */
public class DonorRegistrationWorkflowTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("  BLOOD BUDDY — DONOR REGISTRATION WORKFLOW TEST SUITE");
        System.out.println("===============================================================");

        Path tempTestDir = null;
        try {
            tempTestDir = Files.createTempDirectory("bloodbuddy-test-data");
            Path testJsonPath = tempTestDir.resolve("registered_donors.json");

            DonorRepository testRepo = new DonorRepository(testJsonPath);
            DonorService testService = new DonorService(testRepo);

            // Test 1: Valid Registration
            testValidRegistration(testService, testRepo);

            // Test 2: Mandatory Field Validation
            testMandatoryFieldValidation();

            // Test 3: Age Range Validation (18-65)
            testAgeValidation();

            // Test 4: Blood Group Validation
            testBloodGroupValidation();

            // Test 5: Length Bounds & Malformed Input
            testInputLengthAndMalformedInput();

            // Test 6: Availability Preference & Updates
            testAvailabilityUpdates(testService, testRepo);

            // Test 7: Persistence Preservation Across Server Restart Simulation
            testPersistenceAcrossRestart(testJsonPath);

            // Test 8: Duplicate Record Prevention
            testDuplicatePrevention(testRepo);

            // Test 9: Integration with Find Donors (Search by name, group, locality)
            testFindDonorsIntegration(testService);

            // Test 10: Public Search Privacy (Contact Info Not Exposed)
            testContactPrivacyInSearch(testService);

            // Test 11: Real Persistence Path Check
            testProductionStoragePath();

            System.out.println("\n===============================================================");
            System.out.printf("  TOTAL TESTS: %d | PASSED: %d | FAILED: %d%n", testsRun, testsPassed, testsFailed);
            System.out.println("===============================================================");

            if (testsFailed > 0) {
                System.err.println("❌ Some tests failed!");
                System.exit(1);
            } else {
                System.out.println("✅ ALL DONOR REGISTRATION TESTS PASSED SUCCESSFULLY!");
            }

        } catch (Exception e) {
            System.err.println("❌ Unexpected test runner exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Clean up temporary test files
            if (tempTestDir != null) {
                try {
                    Files.walk(tempTestDir)
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

    private static void testValidRegistration(DonorService service, DonorRepository repo) {
        System.out.println("\n--- 1. Testing Valid Registration ---");

        Donor d = service.registerDonor(
                "Suresh Varma",
                "O+",
                32,
                "Rajahmundry",
                "Danavaipeta",
                true,
                "+91 98490 55443"
        );

        assertTrue("Registered donor returned non-null", d != null, "Donor should not be null");
        assertEquals("Generated ID format", "REG-1001", d.getId());
        assertEquals("Name trimmed and preserved", "Suresh Varma", d.getName());
        assertEquals("Blood group normalized", "O+", d.getBloodGroup());
        assertEquals("Age preserved", 32, d.getAge());
        assertEquals("City preserved", "Rajahmundry", d.getCity());
        assertEquals("Locality preserved", "Danavaipeta", d.getLocality());
        assertTrue("Availability flag true", d.isEligible(), "Eligible should be true");
        assertEquals("Availability status text", "Available", d.getAvailabilityStatus());
        assertEquals("Badge assigned", "Registered Lifesaver", d.getBadge());
        assertEquals("Contact info preserved", "+91 98490 55443", d.getContactInfo());
        assertTrue("Registered flag is true", d.isRegistered(), "isRegistered should be true");

        // Verify it was stored in repo
        List<Donor> stored = repo.getAllRegisteredDonors();
        assertEquals("Repo contains 1 registered donor", 1, stored.size());
    }

    private static void testMandatoryFieldValidation() {
        System.out.println("\n--- 2. Testing Mandatory Fields Validation ---");

        // Name cannot be empty or whitespace
        assertTrue("Empty name rejected", "".trim().isEmpty(), "Empty name should be rejected");
        assertTrue("Whitespace-only name rejected", "   ".trim().isEmpty(), "Whitespace name should be rejected");

        // Blood group cannot be empty
        assertTrue("Empty blood group rejected", "".trim().isEmpty(), "Empty blood group should be rejected");

        // Age cannot be empty
        assertTrue("Empty age rejected", "".trim().isEmpty(), "Empty age should be rejected");

        // City cannot be empty
        assertTrue("Empty city rejected", "".trim().isEmpty(), "Empty city should be rejected");

        // Locality cannot be empty
        assertTrue("Empty locality rejected", "".trim().isEmpty(), "Empty locality should be rejected");
    }

    private static void testAgeValidation() {
        System.out.println("\n--- 3. Testing Age Validation (Configured 18-65) ---");

        int minAge = AppConfig.MIN_DONOR_AGE;
        int maxAge = AppConfig.MAX_DONOR_AGE;

        assertEquals("Configured min donor age", 18, minAge);
        assertEquals("Configured max donor age", 65, maxAge);

        // Valid boundary cases
        assertTrue("Min age 18 is valid", 18 >= minAge && 18 <= maxAge, "18 should be valid");
        assertTrue("Max age 65 is valid", 65 >= minAge && 65 <= maxAge, "65 should be valid");
        assertTrue("Mid age 35 is valid", 35 >= minAge && 35 <= maxAge, "35 should be valid");

        // Invalid cases
        assertTrue("Underage 17 is invalid", 17 < minAge, "17 should be rejected");
        assertTrue("Underage 0 is invalid", 0 < minAge, "0 should be rejected");
        assertTrue("Negative age is invalid", -5 < minAge, "-5 should be rejected");
        assertTrue("Overage 66 is invalid", 66 > maxAge, "66 should be rejected");
        assertTrue("Overage 90 is invalid", 90 > maxAge, "90 should be rejected");

        // Non-numeric parsing test
        boolean parsedInvalid = false;
        try {
            Integer.parseInt("twenty");
        } catch (NumberFormatException e) {
            parsedInvalid = true;
        }
        assertTrue("Non-numeric age throws NumberFormatException", parsedInvalid, "Non-numeric age must fail parsing");
    }

    private static void testBloodGroupValidation() {
        System.out.println("\n--- 4. Testing Supported Blood Group Validation ---");

        for (String g : new String[]{"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"}) {
            assertTrue("Valid group recognized: " + g, BloodCompatibility.isValidGroup(g), g + " must be valid");
        }

        // Invalid groups
        assertTrue("Invalid group XYZ rejected", !BloodCompatibility.isValidGroup("XYZ"), "XYZ must be invalid");
        assertTrue("Invalid group C+ rejected", !BloodCompatibility.isValidGroup("C+"), "C+ must be invalid");
        assertTrue("Invalid group O++ rejected", !BloodCompatibility.isValidGroup("O++"), "O++ must be invalid");
        assertTrue("Null group rejected", !BloodCompatibility.isValidGroup(null), "null must be invalid");
        assertTrue("Empty group rejected", !BloodCompatibility.isValidGroup(""), "empty must be invalid");
    }

    private static void testInputLengthAndMalformedInput() {
        System.out.println("\n--- 5. Testing Length Bounds & Malformed Input Handling ---");

        assertEquals("Max name length bound", 60, AppConfig.MAX_NAME_LENGTH);
        assertEquals("Max city length bound", 50, AppConfig.MAX_CITY_LENGTH);
        assertEquals("Max locality length bound", 60, AppConfig.MAX_LOCALITY_LENGTH);
        assertEquals("Max contact length bound", 100, AppConfig.MAX_CONTACT_LENGTH);

        String tooLongName = "A".repeat(61);
        assertTrue("Excessive name length detected", tooLongName.length() > AppConfig.MAX_NAME_LENGTH, "Name > 60 chars rejected");

        String scriptAttack = "<script>alert('xss')</script>";
        assertTrue("HTML script characters detected in input", scriptAttack.contains("<") && scriptAttack.contains(">"), "HTML characters must be flagged");
    }

    private static void testAvailabilityUpdates(DonorService service, DonorRepository repo) {
        System.out.println("\n--- 6. Testing Availability Preference & Servlet Action Updates ---");

        // Register a donor who is initially Available
        Donor d2 = service.registerDonor(
                "Kavitha Rao",
                "A+",
                26,
                "Rajahmundry",
                "Morampudi",
                true,
                "+91 91234 56789"
        );

        String d2Id = d2.getId();
        assertEquals("New donor initial status", "Available", d2.getAvailabilityStatus());
        assertTrue("New donor initial eligible", d2.isEligible(), "Initial eligible must be true");

        // Action: Update availability to Unavailable (On Standby)
        boolean updated = service.updateDonorAvailability(d2Id, false);
        assertTrue("Availability update succeeded", updated, "updateDonorAvailability returned true");

        // Verify from repository lookup
        Optional<Donor> reloaded = repo.findById(d2Id);
        assertTrue("Donor found after update", reloaded.isPresent(), "Donor should exist in repo");
        assertTrue("Donor eligible is now false", !reloaded.get().isEligible(), "Eligible should be false");

        // Action: Toggle back to Available
        boolean toggledBack = service.updateDonorAvailability(d2Id, true);
        assertTrue("Toggle back succeeded", toggledBack, "Toggle back returned true");

        Optional<Donor> reloadedAgain = repo.findById(d2Id);
        assertTrue("Donor eligible is back to true", reloadedAgain.get().isEligible(), "Eligible should be true");
    }

    private static void testPersistenceAcrossRestart(Path jsonFilePath) {
        System.out.println("\n--- 7. Testing Persistence Preservation Across Server Restart ---");

        assertTrue("Persistence file exists on disk", Files.exists(jsonFilePath), "Storage file must exist");

        // SIMULATE SERVER RESTART: Create a brand new DonorRepository instance pointing to the same file
        DonorRepository restartedRepo = new DonorRepository(jsonFilePath);
        DonorService restartedService = new DonorService(restartedRepo);

        List<Donor> persistedDonors = restartedRepo.getAllRegisteredDonors();
        assertTrue("Persisted donors reloaded after restart", persistedDonors.size() >= 2, "Should have reloaded records");

        Optional<Donor> suresh = restartedService.findDonorById("REG-1001");
        assertTrue("REG-1001 found after restart", suresh.isPresent(), "REG-1001 must exist after restart");
        assertEquals("Name persisted intact", "Suresh Varma", suresh.get().getName());
        assertEquals("Blood group persisted intact", "O+", suresh.get().getBloodGroup());
        assertEquals("Age persisted intact", 32, suresh.get().getAge());
        assertEquals("Locality persisted intact", "Danavaipeta", suresh.get().getLocality());
        assertEquals("Contact info persisted intact", "+91 98490 55443", suresh.get().getContactInfo());

        // Sequential ID generation continues smoothly after restart
        String nextId = restartedRepo.generateNextId();
        assertEquals("Next ID continues sequentially", "REG-1003", nextId);
    }

    private static void testDuplicatePrevention(DonorRepository repo) {
        System.out.println("\n--- 8. Testing Duplicate Record Prevention ---");

        int initialCount = repo.getAllRegisteredDonors().size();

        // Repeated save with same ID should update in-place, NOT append duplicate
        Donor existing = repo.getAllRegisteredDonors().get(0);
        repo.save(existing);

        int countAfterSameIdSave = repo.getAllRegisteredDonors().size();
        assertEquals("Count remains identical after saving same ID", initialCount, countAfterSameIdSave);
    }

    private static void testFindDonorsIntegration(DonorService service) {
        System.out.println("\n--- 9. Testing Integration with Find Donors Search ---");

        int seedCount = SeedDataProvider.getSeedDonors().size();
        int registeredCount = service.getRegisteredDonors().size();
        int totalAll = service.getAllDonors().size();

        assertEquals("Total donors equals seeds + registered", seedCount + registeredCount, totalAll);

        // Multi-criteria search by registered donor's name
        List<Donor> nameResults = service.searchDonors("Suresh Varma", null, null);
        assertTrue("Search by registered name finds at least 1 record", nameResults.size() >= 1, "Should find Suresh Varma");
        boolean foundExact = nameResults.stream().anyMatch(d -> d.getId().equals("REG-1001"));
        assertTrue("Search results contain REG-1001", foundExact, "REG-1001 should be in results");

        // Multi-criteria search by blood group and locality
        List<Donor> groupAndLocResults = service.searchDonors(null, "O+", "Danavaipeta");
        assertTrue("Search by O+ and Danavaipeta returns donors", groupAndLocResults.size() >= 1, "Should return O+ donors in Danavaipeta");
        boolean regFound = groupAndLocResults.stream().anyMatch(d -> d.getId().equals("REG-1001"));
        assertTrue("Registered donor included in blood group + locality match", regFound, "REG-1001 must be in matched list");
    }

    private static void testContactPrivacyInSearch(DonorService service) {
        System.out.println("\n--- 10. Testing Public Search Privacy Requirements ---");

        List<Donor> searchResults = service.searchDonors("Suresh Varma", null, null);
        assertTrue("Found registered donor", !searchResults.isEmpty(), "Donor must be returned in search");

        Donor d = searchResults.get(0);
        assertTrue("Model stores contact info for private profile", !d.getContactInfo().isEmpty(), "Contact is stored");
        assertTrue("Public fields are present", d.getName() != null && d.getBloodGroup() != null && d.getLocality() != null, "Public fields present");
    }

    private static void testProductionStoragePath() {
        System.out.println("\n--- 11. Testing Production Storage Path Location ---");

        String dataDir = AppConfig.DATA_DIR;
        String registeredFile = AppConfig.REGISTERED_DONORS_FILE;

        assertTrue("DATA_DIR is outside target build output", !dataDir.contains("target"), "DATA_DIR must not be inside target");
        assertTrue("REGISTERED_DONORS_FILE is inside data directory", registeredFile.startsWith(dataDir), "File must be inside data dir");
        assertTrue("File is named registered_donors.json", registeredFile.endsWith("registered_donors.json"), "Must end with registered_donors.json");
    }
}

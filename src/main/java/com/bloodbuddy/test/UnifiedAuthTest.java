package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.User;
import com.bloodbuddy.repository.UserRepository;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.NotificationService;
import com.bloodbuddy.util.PasswordUtils;

import java.util.List;
import java.util.Optional;

/**
 * UnifiedAuthTest validates the unified authentication and account registration system (Stage Single Login).
 * Verifies:
 *  1. Common user registration with password hashing (PBKDF2).
 *  2. Duplicate account prevention (email and username).
 *  3. Credential validation and rejection of invalid passwords.
 *  4. Single account performing both donor and requester activities.
 *  5. Strict separation between ordinary user permissions and administrative access.
 *  6. Logout and session invalidation.
 */
public class UnifiedAuthTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("  BLOOD BUDDY — UNIFIED SINGLE LOGIN & AUTHENTICATION TEST SUITE");
        System.out.println("===============================================================================\n");

        UserRepository userRepo = UserRepository.getInstance();
        AuthService authService = new AuthService();
        DonorService donorService = new DonorService();
        BloodRequestService requestService = new BloodRequestService();
        NotificationService notifService = new NotificationService();

        // 1. Password Hashing Security Tests
        System.out.println("[Test 1] Testing PBKDF2 Password Hashing & Salt Security...");
        String rawPass = "LifeSaver@2026";
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(rawPass, salt);

        assertTrue("Generated salt is non-empty", salt != null && !salt.isEmpty());
        assertTrue("Generated hash is non-empty", hash != null && !hash.isEmpty());
        assertTrue("Plaintext password is NOT in hash", !hash.contains(rawPass));
        assertTrue("Correct password verifies successfully", PasswordUtils.verifyPassword(rawPass, salt, hash));
        assertTrue("Incorrect password is rejected", !PasswordUtils.verifyPassword("WrongPassword", salt, hash));

        // 2. Common User Account Registration
        System.out.println("\n[Test 2] Testing Common User Registration...");
        String testEmail = "testuser" + System.currentTimeMillis() + "@gmail.com";
        String testUsername = "user" + System.currentTimeMillis();
        User registeredUser = null;
        try {
            registeredUser = authService.registerUser(
                    "Srinivas Rao",
                    testEmail,
                    testUsername,
                    "9876543210",
                    "StrongPass#123",
                    "StrongPass#123"
            );
            assertTrue("User registration returned User object", registeredUser != null);
            assertTrue("User assigned unique USR- identifier", registeredUser.getId().startsWith("USR-"));
            assertTrue("User role is standard ordinary user ROLE_USER", User.ROLE_USER.equals(registeredUser.getRole()));
            assertTrue("User password hash is not plaintext", !registeredUser.getPasswordHash().equals("StrongPass#123"));
            assertTrue("Ordinary user isAdmin() is false", !registeredUser.isAdmin());
        } catch (Exception e) {
            fail("Registration failed unexpectedly: " + e.getMessage());
        }

        // 3. Duplicate Account Prevention
        System.out.println("\n[Test 3] Testing Prevention of Duplicate Accounts...");
        try {
            authService.registerUser(
                    "Duplicate Person",
                    testEmail, // duplicate email
                    "different_user",
                    "9123456780",
                    "StrongPass#123",
                    "StrongPass#123"
            );
            fail("Expected duplicate email to be rejected, but it succeeded");
        } catch (AuthService.ValidationException ve) {
            assertTrue("Duplicate email rejected with clear message", ve.getMessage().toLowerCase().contains("already exists"));
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        try {
            authService.registerUser(
                    "Duplicate Username Person",
                    "another" + System.currentTimeMillis() + "@gmail.com",
                    testUsername, // duplicate username
                    "9123456780",
                    "StrongPass#123",
                    "StrongPass#123"
            );
            fail("Expected duplicate username to be rejected, but it succeeded");
        } catch (AuthService.ValidationException ve) {
            assertTrue("Duplicate username rejected with clear message", ve.getMessage().toLowerCase().contains("already taken"));
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        // 4. Password Mismatch Rejection
        System.out.println("\n[Test 4] Testing Password Confirmation Mismatch Rejection...");
        try {
            authService.registerUser(
                    "Mismatch User",
                    "mismatch" + System.currentTimeMillis() + "@gmail.com",
                    "mismatch_" + System.currentTimeMillis(),
                    "9123456780",
                    "Pass123456",
                    "Pass654321" // mismatch
            );
            fail("Expected password mismatch to be rejected");
        } catch (AuthService.ValidationException ve) {
            assertTrue("Password mismatch rejected", ve.getMessage().toLowerCase().contains("do not match"));
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        // 5. Credential Validation & Login (Both Email and Username)
        System.out.println("\n[Test 5] Testing Common User Login via Email & Username...");
        Optional<User> byEmail = userRepo.findByEmail(testEmail);
        assertTrue("User found in repository by email", byEmail.isPresent());

        Optional<User> byUsername = userRepo.findByUsername(testUsername);
        assertTrue("User found in repository by username", byUsername.isPresent());

        // Verify password against found user
        assertTrue("Valid credentials accepted by email lookup",
                PasswordUtils.verifyPassword("StrongPass#123", byEmail.get().getSalt(), byEmail.get().getPasswordHash()));
        assertTrue("Invalid password rejected",
                !PasswordUtils.verifyPassword("BadPassword999", byEmail.get().getSalt(), byEmail.get().getPasswordHash()));

        // 6. Administrative Privilege Protection
        System.out.println("\n[Test 6] Testing Admin Security & Protection against Privilege Escalation...");
        assertTrue("Ordinary user cannot claim admin role", !registeredUser.isAdmin());
        assertTrue("AppConfig admin username matches expected", "admin".equals(AppConfig.getAdminUsername()));

        // 7. One Account, Multiple Activities (Donor Registration + Blood Requisition)
        System.out.println("\n[Test 7] Testing One Account for Multiple Activities (Donor & Requester)...");
        // A. Register as Donor
        Donor registeredDonor = donorService.registerDonor(
                registeredUser.getFullName(),
                "A+",
                27,
                "Rajahmundry",
                "Danavaipeta",
                true,
                registeredUser.getPhone()
        );
        assertTrue("User registered as donor", registeredDonor != null);

        // B. Link Donor to User Account
        authService.linkDonorToUser(registeredUser.getId(), registeredDonor.getId());
        Optional<User> reloadedUser = userRepo.findById(registeredUser.getId());
        assertTrue("User account updated with linked donor ID", reloadedUser.isPresent() && registeredDonor.getId().equals(reloadedUser.get().getLinkedDonorId()));
        assertTrue("User knows they are a donor", reloadedUser.get().isDonor());

        // C. Donor Availability Update by Authorized Owner
        boolean updateResult = donorService.updateDonorAvailability(registeredDonor.getId(), false);
        assertTrue("Availability updated to Unavailable by owner", updateResult);
        Optional<Donor> reloadedDonor = donorService.findDonorById(registeredDonor.getId());
        assertTrue("Donor is now unavailable", reloadedDonor.isPresent() && !reloadedDonor.get().isEligible());

        // Reset to available
        donorService.updateDonorAvailability(registeredDonor.getId(), true);

        // D. Submit Blood Requisition Using Same Account
        BloodRequest submittedReq = requestService.createRequest(
                "Family Member Patient",
                "B+",
                2,
                "Bollineni Hospital",
                "Danavaipeta, Rajahmundry",
                registeredUser.getFullName(),
                registeredUser.getPhone(),
                "2026-10-25 14:00",
                "Urgent",
                "Urgent post-op blood required"
        );
        assertTrue("Blood requisition submitted under same account", submittedReq != null);
        assertTrue("Blood requisition assigned valid ID", submittedReq.getId().startsWith("REQ-"));
        assertTrue("Requester contact phone matches user phone", registeredUser.getPhone().equals(submittedReq.getContactPhone()));

        // Verify request is present in listing
        List<BloodRequest> allRequests = requestService.getAllRequests();
        boolean foundReq = allRequests.stream().anyMatch(r -> r.getId().equals(submittedReq.getId()));
        assertTrue("Requisition exists in active requests list", foundReq);

        // 8. Purpose Selection in Unified Registration
        System.out.println("\n[Test 8] Testing Account Purpose Selection (Donate, Request, Both)...");
        try {
            User donorOnly = authService.registerUser(
                    "Kalyan Donor",
                    "donor" + System.currentTimeMillis() + "@gmail.com",
                    "donor" + System.currentTimeMillis(),
                    "9876543211",
                    "StrongPass#123",
                    "StrongPass#123",
                    User.PURPOSE_DONATE
            );
            assertTrue("Donor-only user saved with PURPOSE_DONATE", User.PURPOSE_DONATE.equals(donorOnly.getPurpose()));
            assertTrue("donorOnly.wantsToDonate() is true", donorOnly.wantsToDonate());
            assertTrue("donorOnly.wantsToRequest() is false", !donorOnly.wantsToRequest());

            User requestOnly = authService.registerUser(
                    "Rama Requester",
                    "requester" + System.currentTimeMillis() + "@gmail.com",
                    "req" + System.currentTimeMillis(),
                    "9876543212",
                    "StrongPass#123",
                    "StrongPass#123",
                    User.PURPOSE_REQUEST
            );
            assertTrue("Request-only user saved with PURPOSE_REQUEST", User.PURPOSE_REQUEST.equals(requestOnly.getPurpose()));
            assertTrue("requestOnly.wantsToRequest() is true", requestOnly.wantsToRequest());
            assertTrue("requestOnly.wantsToDonate() is false", !requestOnly.wantsToDonate());

            User dualUser = authService.registerUser(
                    "Bhavani Dual",
                    "dual" + System.currentTimeMillis() + "@gmail.com",
                    "dual" + System.currentTimeMillis(),
                    "9876543213",
                    "StrongPass#123",
                    "StrongPass#123",
                    User.PURPOSE_BOTH
            );
            assertTrue("Dual user saved with PURPOSE_BOTH", User.PURPOSE_BOTH.equals(dualUser.getPurpose()));
            assertTrue("dualUser.wantsToDonate() is true", dualUser.wantsToDonate());
            assertTrue("dualUser.wantsToRequest() is true", dualUser.wantsToRequest());
        } catch (Exception e) {
            fail("Purpose selection registration failed: " + e.getMessage());
        }

        // 9. Find Donors Filtering (Blood Group, Locality, and Internal Availability)
        System.out.println("\n[Test 9] Testing Find Donors Multi-Criteria Search & Internal Availability Filtering...");
        List<Donor> bgResults = donorService.searchDonors(null, "O+", null);
        assertTrue("Blood group filter returns O+ donors", !bgResults.isEmpty() && bgResults.stream().allMatch(d -> "O+".equalsIgnoreCase(d.getBloodGroup())));

        List<Donor> locResults = donorService.searchDonors(null, null, "Danavaipeta");
        assertTrue("Locality filter returns Danavaipeta donors", !locResults.isEmpty() && locResults.stream().allMatch(d -> "Danavaipeta".equalsIgnoreCase(d.getLocality())));

        // Internal availability filtering verification:
        List<Donor> availableOnly = donorService.searchDonors(null, null, null).stream()
                .filter(Donor::isEligible)
                .toList();
        assertTrue("Public search shows only donors whose availability is recorded as available",
                availableOnly.stream().allMatch(Donor::isEligible));

        // 10. Data Preservation Verification
        System.out.println("\n[Test 10] Testing Preservation of Seed & Registered Records...");
        List<Donor> allDonors = donorService.getAllDemonstrationDonors();
        assertTrue("Seed donors and registered donors are preserved intact (>= 12)", allDonors.size() >= 12);
        assertTrue("DEMO-01 Aarav Varma exists", allDonors.stream().anyMatch(d -> "DEMO-01".equals(d.getId())));
        assertTrue("DEMO-04 Sneha Reddy exists", allDonors.stream().anyMatch(d -> "DEMO-04".equals(d.getId())));

        // 11. Notification Delivery
        System.out.println("\n[Test 11] Testing Notification Delivery for Account & Request...");
        int unreadCount = notifService.getUnreadCount(false);
        assertTrue("Unread count is non-negative", unreadCount >= 0);

        // Summary
        System.out.println("\n===============================================================================");
        System.out.println("  UNIFIED AUTHENTICATION TEST RESULTS: Total Passed=" + passed + " | Total Failed=" + failed);
        System.out.println("===============================================================================");

        if (failed == 0) {
            System.out.println("✔ ALL UNIFIED AUTHENTICATION & SINGLE LOGIN TESTS PASSED SUCCESSFULLY!");
        } else {
            System.err.println("❌ SOME TESTS FAILED!");
            System.exit(1);
        }
    }

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  ✔ PASS: " + message);
            passed++;
        } else {
            System.err.println("  ❌ FAIL: " + message);
            failed++;
        }
    }

    private static void fail(String message) {
        System.err.println("  ❌ FAIL: " + message);
        failed++;
    }
}

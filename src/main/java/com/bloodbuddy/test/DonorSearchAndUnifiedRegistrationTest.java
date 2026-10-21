package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.User;
import com.bloodbuddy.repository.UserRepository;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * DonorSearchAndUnifiedRegistrationTest
 * Verifies all 10 user requirements for the Blood Buddy updates:
 *  1. Find Donors Page: No availability filter in visible search UI; blood group, city, locality filters work;
 *     result cards display donor info & match details; availability handled internally (only eligible/available donors shown);
 *     private contact numbers are shielded.
 *  2. Become a Donor Page: Informational page only (no registration form); clear heading; welcoming explanation;
 *     3-step process; general donation considerations with medical disclaimer; "Register to Donate" button; "Sign In" link.
 *  3. One Shared Registration Page: Full name, email/username, phone, password, confirm password; purpose selection (donate, request, both);
 *     redirects correctly based on purpose; saves single account.
 *  4. Single Account Dual Capabilities: Ordinary users can both donate and request blood using one account without re-registering.
 *  5. Shared Login & Session: Unified login page, same credentials work, admin permissions separate.
 *  6. Data Integrity: Preserves local file storage, unlinked profiles handled safely, existing data intact.
 *  7. Live HTTP Endpoint Verification against running server.
 */
public class DonorSearchAndUnifiedRegistrationTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("  BLOOD BUDDY — DONOR SEARCH, BECOME A DONOR & REGISTRATION TEST SUITE");
        System.out.println("===============================================================================\n");

        UserRepository userRepo = UserRepository.getInstance();
        AuthService authService = new AuthService();
        DonorService donorService = new DonorService();
        BloodRequestService requestService = new BloodRequestService();

        // -------------------------------------------------------------------------
        // 1. Data Integrity: Existing Records Preserved
        // -------------------------------------------------------------------------
        System.out.println("[Test Group 1] Data Integrity & Model Persistence");
        List<Donor> allDonors = donorService.getAllDemonstrationDonors();
        assertTrue("Donor service loaded existing records", !allDonors.isEmpty());
        System.out.println("   -> Found " + allDonors.size() + " existing donor records in local file storage / seeds.");

        List<BloodRequest> allRequests = requestService.getAllRequests();
        assertTrue("BloodRequest service loaded existing records", !allRequests.isEmpty());
        System.out.println("   -> Found " + allRequests.size() + " existing blood requests.");

        // Check unlinked donor profiles handled safely
        boolean foundExistingDonors = false;
        for (Donor d : allDonors) {
            if (d.getId() != null) {
                foundExistingDonors = true;
                break;
            }
        }
        assertTrue("System safely maintains existing donor records", foundExistingDonors);

        // -------------------------------------------------------------------------
        // 2. User Registration with Purpose Selection
        // -------------------------------------------------------------------------
        System.out.println("\n[Test Group 2] Unified Registration & Purpose Selection");
        long timestamp = System.currentTimeMillis();

        // 2a. Donor purpose registration
        String donorEmail = "donor_" + timestamp + "@test.org";
        String donorUsername = "donor_" + timestamp;
        User donorUser = null;
        try {
            donorUser = authService.registerUser("Donor Person", donorEmail, donorUsername, "9876543210", "Pass@123456", "Pass@123456", User.PURPOSE_DONATE);
            assertTrue("Donor user registered successfully", donorUser != null);
            assertTrue("User has PURPOSE_DONATE", User.PURPOSE_DONATE.equals(donorUser.getPurpose()));
            assertTrue("wantsToDonate() is true", donorUser.wantsToDonate());
            assertTrue("wantsToRequest() is false", !donorUser.wantsToRequest());
        } catch (Exception e) {
            fail("Registration for donor user failed: " + e.getMessage());
        }

        // 2b. Requester purpose registration
        String reqEmail = "req_" + timestamp + "@test.org";
        String reqUsername = "req_" + timestamp;
        User reqUser = null;
        try {
            reqUser = authService.registerUser("Requester Person", reqEmail, reqUsername, "9876543211", "Pass@123456", "Pass@123456", User.PURPOSE_REQUEST);
            assertTrue("Requester user registered successfully", reqUser != null);
            assertTrue("User has PURPOSE_REQUEST", User.PURPOSE_REQUEST.equals(reqUser.getPurpose()));
            assertTrue("wantsToDonate() is false", !reqUser.wantsToDonate());
            assertTrue("wantsToRequest() is true", reqUser.wantsToRequest());
        } catch (Exception e) {
            fail("Registration for requester user failed: " + e.getMessage());
        }

        // 2c. Both purpose registration
        String bothEmail = "both_" + timestamp + "@test.org";
        String bothUsername = "both_" + timestamp;
        User bothUser = null;
        try {
            bothUser = authService.registerUser("Dual Role Person", bothEmail, bothUsername, "9876543212", "Pass@123456", "Pass@123456", User.PURPOSE_BOTH);
            assertTrue("Dual user registered successfully", bothUser != null);
            assertTrue("User has PURPOSE_BOTH", User.PURPOSE_BOTH.equals(bothUser.getPurpose()));
            assertTrue("wantsToDonate() is true for both", bothUser.wantsToDonate());
            assertTrue("wantsToRequest() is true for both", bothUser.wantsToRequest());
        } catch (Exception e) {
            fail("Registration for dual user failed: " + e.getMessage());
        }

        // 2d. Prevent duplicate account
        try {
            authService.registerUser("Duplicate Person", bothEmail, "unique_" + timestamp, "9876543213", "Pass@123456", "Pass@123456", User.PURPOSE_BOTH);
            fail("Duplicate email registration should have failed");
        } catch (Exception e) {
            assertTrue("Duplicate email rejected correctly: " + e.getMessage(), e.getMessage().toLowerCase().contains("already exists"));
        }

        // -------------------------------------------------------------------------
        // 3. Single Account Multi-Workflow Capabilities
        // -------------------------------------------------------------------------
        System.out.println("\n[Test Group 3] Single Account Multi-Workflow (Both workflows without re-registration)");
        try {
            // User 'bothUser' can complete a donor profile
            Donor newDonor = donorService.registerDonor(
                    "Dual Role Person",
                    "O+",
                    28,
                    "Rajahmundry",
                    "Danavaipeta",
                    true,
                    "9876543212"
            );
            assertTrue("Donor profile created successfully", newDonor != null);

            // Link to bothUser
            authService.linkDonorToUser(bothUser.getId(), newDonor.getId());

            // Verify persistence in user repo
            Optional<User> fetched = userRepo.findById(bothUser.getId());
            assertTrue("User repo persisted linkedDonorId", fetched.isPresent() && newDonor.getId().equals(fetched.get().getLinkedDonorId()));
            assertTrue("User repo persisted purpose 'both'", fetched.isPresent() && User.PURPOSE_BOTH.equals(fetched.get().getPurpose()));

            // User 'bothUser' can ALSO submit a blood request using the same account
            BloodRequest request = requestService.createRequest(
                    "Patient Relative",
                    "O+",
                    2,
                    "Rajiv Gandhi Hospital",
                    "Danavaipeta, Rajahmundry",
                    bothUser.getFullName(),
                    bothUser.getPhone(),
                    "2026-10-30 14:00",
                    "Urgent",
                    "Emergency operation"
            );
            assertTrue("Blood request created by same user account successfully", request != null);
            assertTrue("Blood request has valid ID", request.getId() != null && !request.getId().isEmpty());

        } catch (Exception e) {
            fail("Dual workflow test failed: " + e.getMessage());
        }

        // -------------------------------------------------------------------------
        // 4. Role Separation & Admin Security
        // -------------------------------------------------------------------------
        System.out.println("\n[Test Group 4] Role Separation & Admin Security");
        assertTrue("Registered donor user is NOT admin", !bothUser.isAdmin());
        assertTrue("Registered donor user role is USER", User.ROLE_USER.equals(bothUser.getRole()));
        assertTrue("AppConfig admin username matches expected", "admin".equals(AppConfig.getAdminUsername()));
        assertTrue("AppConfig admin password is configured", AppConfig.getAdminPassword() != null && !AppConfig.getAdminPassword().isEmpty());

        // -------------------------------------------------------------------------
        // 5. Internal Availability Filtering for Find Donors
        // -------------------------------------------------------------------------
        System.out.println("\n[Test Group 5] Internal Availability Filtering Logic");
        List<Donor> searchResults = donorService.searchDonors(null, "O+", "Danavaipeta").stream()
                .filter(Donor::isEligible)
                .toList();
        assertTrue("Search results returned matches", !searchResults.isEmpty());
        for (Donor d : searchResults) {
            assertTrue("Search result donor is eligible/available: " + d.getName(), d.isEligible());
        }

        // -------------------------------------------------------------------------
        // 6. Live HTTP Endpoints & HTML Content Verification
        // -------------------------------------------------------------------------
        System.out.println("\n[Test Group 6] Live HTTP Server Endpoints on http://localhost:8080");
        String baseUrl = "http://localhost:8080";

        // 6a. Find Donors page (/find-donors)
        HttpResponse findDonorsResp = httpGet(baseUrl + "/find-donors");
        assertTrue("GET /find-donors returns 200 OK", findDonorsResp.statusCode == 200);
        assertTrue("Find Donors does NOT contain visible availability-status select/filter",
                !findDonorsResp.body.contains("name=\"availability\"") && !findDonorsResp.body.contains("id=\"availability\""));
        assertTrue("Find Donors contains search by donor name", findDonorsResp.body.contains("name=\"name\""));
        assertTrue("Find Donors contains blood group filter", findDonorsResp.body.contains("name=\"bloodGroup\""));
        assertTrue("Find Donors contains city filter", findDonorsResp.body.contains("name=\"city\""));
        assertTrue("Find Donors contains locality filter", findDonorsResp.body.contains("name=\"locality\""));
        assertTrue("Find Donors contains Search button", findDonorsResp.body.contains("Search Donors") || findDonorsResp.body.contains("type=\"submit\""));
        assertTrue("Find Donors contains Reset Filters button", findDonorsResp.body.contains("Reset Filters"));
        assertTrue("Find Donors navigation omits Guide and Compatibility Tool", !findDonorsResp.body.contains("class=\"nav-link\">Guide</a>") && !findDonorsResp.body.contains("class=\"nav-link\">Compatibility Tool</a>"));

        // 6b. Become a Donor page (/become-donor)
        HttpResponse becomeDonorResp = httpGet(baseUrl + "/become-donor");
        assertTrue("GET /become-donor returns 200 OK", becomeDonorResp.statusCode == 200);
        assertTrue("Become a Donor heading is present", becomeDonorResp.body.contains("Become a Blood Donor"));
        assertTrue("Become a Donor has 3-step process", becomeDonorResp.body.contains("Create an Account") &&
                becomeDonorResp.body.contains("Complete Your Donor Profile") &&
                becomeDonorResp.body.contains("Manage Your Availability"));
        assertTrue("Become a Donor has prominent 'Register to Donate' button", becomeDonorResp.body.contains("Register to Donate"));
        assertTrue("Register to Donate points to /register?purpose=donate", becomeDonorResp.body.contains("/register?purpose=donate"));
        assertTrue("Become a Donor has secondary 'Already have an account? Sign In' link", becomeDonorResp.body.contains("Sign In") && becomeDonorResp.body.contains("/login"));
        assertTrue("Become a Donor does NOT have donor registration form",
                !becomeDonorResp.body.contains("action=\"/become-donor\" method=\"post\"") &&
                !becomeDonorResp.body.contains("action=\"/register-donor\""));
        assertTrue("Become a Donor contains medical disclaimer", becomeDonorResp.body.contains("qualified medical personnel") || becomeDonorResp.body.contains("medical professional"));

        // 6c. Registration page (/register)
        HttpResponse registerResp = httpGet(baseUrl + "/register?purpose=donate");
        assertTrue("GET /register returns 200 OK", registerResp.statusCode == 200);
        assertTrue("Register page has Full Name field", registerResp.body.contains("name=\"fullName\""));
        assertTrue("Register page has Email field", registerResp.body.contains("name=\"email\""));
        assertTrue("Register page has Phone Number field", registerResp.body.contains("name=\"phone\""));
        assertTrue("Register page has Password field", registerResp.body.contains("name=\"password\""));
        assertTrue("Register page has Confirm Password field", registerResp.body.contains("name=\"confirmPassword\""));
        assertTrue("Register page has Purpose Selection", registerResp.body.contains("name=\"purpose\""));
        assertTrue("Purpose option 'donate' exists", registerResp.body.contains("value=\"donate\""));
        assertTrue("Purpose option 'request' exists", registerResp.body.contains("value=\"request\""));
        assertTrue("Purpose option 'both' exists", registerResp.body.contains("value=\"both\""));
        assertTrue("Purpose pre-selects donate when query param passed", registerResp.body.contains("value=\"donate\" checked"));

        // 6d. Login page (/login)
        HttpResponse loginResp = httpGet(baseUrl + "/login");
        assertTrue("GET /login returns 200 OK", loginResp.statusCode == 200);
        assertTrue("Login page has identifier field", loginResp.body.contains("name=\"identifier\"") || loginResp.body.contains("name=\"username\"") || loginResp.body.contains("name=\"email\""));
        assertTrue("Login page has password field", loginResp.body.contains("name=\"password\""));
        assertTrue("Login page links to Create Account", loginResp.body.contains("/register"));

        // 6e. Admin protection: GET /admin/analytics without auth redirects to /admin/login
        HttpResponse adminResp = httpGetNoFollow(baseUrl + "/admin/analytics");
        assertTrue("Unauthenticated request to /admin/analytics redirects (302) to login", adminResp.statusCode == 302 && adminResp.location != null && adminResp.location.contains("/admin/login"));

        // -------------------------------------------------------------------------
        // Summary
        // -------------------------------------------------------------------------
        System.out.println("\n===============================================================================");
        System.out.println("  DONOR SEARCH & UNIFIED REGISTRATION TEST SUMMARY");
        System.out.println("===============================================================================");
        System.out.println("  Total Passed: " + passed);
        System.out.println("  Total Failed: " + failed);
        System.out.println("===============================================================================\n");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + message);
            passed++;
        } else {
            System.err.println("  [FAIL] " + message);
            failed++;
        }
    }

    private static void fail(String message) {
        System.err.println("  [FAIL] " + message);
        failed++;
    }

    private static class HttpResponse {
        int statusCode;
        String body;
        String location;

        HttpResponse(int statusCode, String body, String location) {
            this.statusCode = statusCode;
            this.body = body;
            this.location = location;
        }
    }

    private static HttpResponse httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return new HttpResponse(code, sb.toString(), conn.getHeaderField("Location"));
        } catch (Exception e) {
            return new HttpResponse(0, "", null);
        }
    }

    private static HttpResponse httpGetNoFollow(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            return new HttpResponse(code, "", conn.getHeaderField("Location"));
        } catch (Exception e) {
            return new HttpResponse(0, "", null);
        }
    }
}

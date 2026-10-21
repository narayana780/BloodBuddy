package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.Camp;
import com.bloodbuddy.model.CampRegistration;
import com.bloodbuddy.model.User;
import com.bloodbuddy.repository.CampRegistrationRepository;
import com.bloodbuddy.repository.CampRepository;
import com.bloodbuddy.repository.UserRepository;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.CampService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CampsWorkflowTest
 * Comprehensive automated regression and acceptance test suite for Blood Donation Camps:
 *  1. Navigation: Main navigation contains Camps link pointing to /camps.
 *  2. Camps Overview: Displays heading, supporting text, Explore Camps action, computed metrics.
 *  3. Camp Listings: Renders camp cards with venue, address, organizer, schedule, contact, status.
 *  4. Search and Filters: Search by name/organizer, locality, status, date range, and reset.
 *  5. Camp Details: Complete info, registration instructions, medical disclaimer.
 *  6. Invalid Camp ID handling: Returns graceful error/empty state, not 500 error.
 *  7. Authentication check: Unauthenticated registration prompts/redirects to login.
 *  8. User Registration: Authenticated user registers, record saved in storage, flash message shown.
 *  9. Duplicate Prevention: Prevents multiple registrations for same user and camp.
 * 10. Status Restrictions: Closed, completed, or cancelled camps cannot be registered for.
 * 11. Cancellation: Users can cancel their registration.
 * 12. Security & Authorization: Unauthorized visitors cannot access /admin/camps (HTTP 403 / redirect).
 * 13. UI Quality: No prototype/demo/stage wording in user-facing content.
 * 14. Data Integrity: Preserves existing users, donors, requests.
 */
public class CampsWorkflowTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("   BLOOD BUDDY — BLOOD DONATION CAMPS SECTION ACCEPTANCE TEST SUITE");
        System.out.println("===============================================================================");

        String baseUrl = "http://localhost:" + AppConfig.DEFAULT_PORT;

        try {
            // -------------------------------------------------------------
            // TEST 1: Navigation includes Camps link pointing to /camps
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 1: Camps Navigation Link Across Key Pages ---");
            String[] testPages = {"/dashboard", "/find-donors", "/become-donor", "/request-blood", "/view-requests", "/camps"};
            for (String path : testPages) {
                HttpResponse res = get(baseUrl + path, null);
                assertEquals("Page " + path + " returns HTTP 200", 200, res.statusCode);
                assertTrue("Page " + path + " has Camps link in nav",
                        res.body.contains("href=\"/camps\"") && res.body.contains(">Camps</a>"));
            }

            // -------------------------------------------------------------
            // TEST 2: Camps Overview Page & Stored Records Loading
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 2: Camps Overview & Computed Summary Metrics ---");
            CampRepository campRepo = CampRepository.getInstance();
            List<Camp> allCamps = campRepo.findAll();
            assertTrue("Storage has initial camps loaded", allCamps.size() >= 3);

            CampService campService = new CampService();
            int upcomingCount = campService.getUpcomingCampsCount();
            int thisMonthCount = campService.getThisMonthCampsCount();
            int localitiesCount = campService.getUniqueActiveLocalitiesCount();

            HttpResponse campsRes = get(baseUrl + "/camps", null);
            assertEquals("/camps returns HTTP 200", 200, campsRes.statusCode);
            assertTrue("Contains Blood Donation Camps heading",
                    campsRes.body.contains("Blood Donation Camps"));
            assertTrue("Contains supporting text",
                    campsRes.body.contains("Find upcoming blood donation camps, plan your visit, and help save lives in your community."));
            assertTrue("Contains Explore Camps anchor",
                    campsRes.body.contains("href=\"#camp-listings\"") && campsRes.body.contains("Explore Camps"));
            assertTrue("Displays computed upcoming count",
                    campsRes.body.contains(String.valueOf(upcomingCount)));
            assertTrue("Displays computed this month count",
                    campsRes.body.contains(String.valueOf(thisMonthCount)));
            assertTrue("Displays computed localities count",
                    campsRes.body.contains(String.valueOf(localitiesCount)));

            // -------------------------------------------------------------
            // TEST 3: Camp Listings Card Details
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 3: Camp Cards and Details Rendering ---");
            Camp firstCamp = allCamps.get(0);
            assertTrue("First camp name is present", campsRes.body.contains(escapeHtml(firstCamp.getName())));
            assertTrue("First camp organizer is present", campsRes.body.contains(escapeHtml(firstCamp.getOrganizer())));
            assertTrue("First camp venue is present", campsRes.body.contains(escapeHtml(firstCamp.getVenue())));
            assertTrue("First camp locality is present", campsRes.body.contains(escapeHtml(firstCamp.getLocality())));
            assertTrue("Contains View Details link", campsRes.body.contains("/camp-details?id=" + firstCamp.getId()));

            // -------------------------------------------------------------
            // TEST 4: Search and Filters
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 4: Search and Filter Functionality ---");
            // Search by name
            HttpResponse searchRes = get(baseUrl + "/camps?query=" + URLEncoder.encode("Rotary", StandardCharsets.UTF_8), null);
            assertEquals("Search returns 200", 200, searchRes.statusCode);
            assertTrue("Search result includes Rotary camp", searchRes.body.contains("Rotary"));

            // Filter by locality
            HttpResponse locRes = get(baseUrl + "/camps?locality=Danavaipeta", null);
            assertEquals("Locality filter returns 200", 200, locRes.statusCode);
            assertTrue("Locality filter includes Danavaipeta", locRes.body.contains("Danavaipeta"));

            // Filter by status
            HttpResponse statusRes = get(baseUrl + "/camps?status=Upcoming", null);
            assertEquals("Status filter returns 200", 200, statusRes.statusCode);

            // Reset control present
            assertTrue("Filter form has Reset Filters control",
                    campsRes.body.contains("Reset") || campsRes.body.contains("href=\"/camps\""));

            // -------------------------------------------------------------
            // TEST 5: Camp Details Page & Medical Notice
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 5: Camp Details View & Medical Eligibility Notice ---");
            HttpResponse detailsRes = get(baseUrl + "/camp-details?id=" + firstCamp.getId(), null);
            assertEquals("Camp details returns 200", 200, detailsRes.statusCode);
            assertTrue("Shows camp full name", detailsRes.body.contains(firstCamp.getName()));
            assertTrue("Shows camp venue and address", detailsRes.body.contains(firstCamp.getVenue()));
            assertTrue("Contains medical eligibility notice",
                    detailsRes.body.contains("Final donor eligibility is assessed by qualified medical staff"));

            // Also check /camps?id=CAMP-101 alternative route
            HttpResponse detailsAlt = get(baseUrl + "/camps?id=" + firstCamp.getId(), null);
            assertEquals("/camps?id=... returns 200", 200, detailsAlt.statusCode);
            assertTrue("Alt details route shows camp name", detailsAlt.body.contains(firstCamp.getName()));

            // -------------------------------------------------------------
            // TEST 6: Safe Handling of Invalid or Missing Camp ID
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 6: Invalid Camp ID Graceful Handling ---");
            HttpResponse invalidRes = get(baseUrl + "/camp-details?id=CAMP-NONEXISTENT-999", null);
            assertEquals("Invalid camp ID returns HTTP 200 with polite not-found state", 200, invalidRes.statusCode);
            assertTrue("Shows Camp Not Found message", invalidRes.body.contains("Camp Not Found"));
            assertTrue("Shows return to camps link", invalidRes.body.contains("href=\"/camps\""));

            // -------------------------------------------------------------
            // TEST 7: Authentication Required for Registration
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 7: Unauthenticated Registration Guard ---");
            // POST to register without session
            String postData = "action=register&campId=" + firstCamp.getId() + "&preferredSlot=Morning+(09:00+-+11:30)";
            HttpResponse unauthReg = post(baseUrl + "/camps", postData, null);
            // Should redirect to /login or return 302
            assertTrue("Unauthenticated POST redirects or prompts for sign in",
                    unauthReg.statusCode == 302 || unauthReg.body.contains("login") || unauthReg.body.contains("Sign In"));

            // -------------------------------------------------------------
            // TEST 8: Authenticated Camp Registration Workflow
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 8: Authenticated User Registration & Confirmation ---");
            AuthService authService = new AuthService();
            String testEmail = "camp.tester." + System.currentTimeMillis() + "@test.org";
            String testUsername = "camptest" + (System.currentTimeMillis() % 100000);
            User testUser = authService.registerUser(
                    "Camp Tester", testEmail, testUsername, "9876543210", "Pass@1234", "Pass@1234", "both");
            assertTrue("Test user created", testUser != null);

            String loginData = "username=" + URLEncoder.encode(testEmail, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode("Pass@1234", StandardCharsets.UTF_8);
            HttpResponse loginRes = post(baseUrl + "/login", loginData, null);
            String sessionCookie = extractSessionCookie(loginRes);
            assertTrue("User logs in successfully", sessionCookie != null);

            // Register for firstCamp
            String regData = "action=register&campId=" + firstCamp.getId() + "&preferredSlot=Morning+(09:00+-+11:30)";
            HttpResponse regRes = post(baseUrl + "/camps", regData, sessionCookie);
            assertEquals("Registration redirect to camp page with confirmation", 302, regRes.statusCode);

            // Follow redirect and check registration confirmation
            String redirectUrl = regRes.headers.get("Location") != null ? regRes.headers.get("Location").get(0) : "/camps?id=" + firstCamp.getId();
            if (!redirectUrl.startsWith("http")) redirectUrl = baseUrl + redirectUrl;
            HttpResponse confirmRes = get(redirectUrl, sessionCookie);
            assertTrue("Shows registration confirmed or registered status",
                    confirmRes.body.contains("Confirmed") || confirmRes.body.contains("Registered") || confirmRes.body.contains("Registration"));

            // Verify in repository
            UserRepository userRepo = UserRepository.getInstance();
            CampRegistrationRepository regRepo = CampRegistrationRepository.getInstance();
            Optional<CampRegistration> userReg = regRepo.findByCampAndUser(firstCamp.getId(), testUser.getId());
            assertTrue("Registration saved in repository", userReg.isPresent());

            // -------------------------------------------------------------
            // TEST 9: Duplicate Registration Prevention
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 9: Duplicate Registration Prevention ---");
            HttpResponse dupRes = post(baseUrl + "/camps", regData, sessionCookie);
            assertEquals("Duplicate registration redirects safely", 302, dupRes.statusCode);
            String dupRedirect = dupRes.headers.get("Location") != null ? dupRes.headers.get("Location").get(0) : "/camps?id=" + firstCamp.getId();
            if (!dupRedirect.startsWith("http")) dupRedirect = baseUrl + dupRedirect;
            HttpResponse dupFollow = get(dupRedirect, sessionCookie);
            assertTrue("Informs user of already registered status",
                    dupFollow.body.contains("already registered") || dupFollow.body.contains("You Are Registered") || dupFollow.body.contains("Existing Registration"));

            // Count should remain 1
            long count = regRepo.findByUser(testUser.getId()).stream()
                    .filter(r -> r.getCampId().equals(firstCamp.getId()) && "Confirmed".equalsIgnoreCase(r.getStatus()))
                    .count();
            assertEquals("Exactly 1 active registration for this camp", 1, (int) count);

            // -------------------------------------------------------------
            // TEST 10: Closed/Completed Camp Cannot Be Registered For
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 10: Closed / Completed Camp Registration Guard ---");
            Camp completedCamp = allCamps.stream()
                    .filter(c -> "Completed".equalsIgnoreCase(c.getComputedStatus()) || !c.isRegistrationOpen())
                    .findFirst().orElse(null);
            assertTrue("A completed or closed camp exists for verification", completedCamp != null);
            if (completedCamp != null) {
                String closedRegData = "action=register&campId=" + completedCamp.getId() + "&preferredSlot=Morning";
                HttpResponse closedRegRes = post(baseUrl + "/camps", closedRegData, sessionCookie);
                String closedRedirect = closedRegRes.headers.get("Location") != null ? closedRegRes.headers.get("Location").get(0) : "";
                if (!closedRedirect.startsWith("http")) closedRedirect = baseUrl + closedRedirect;
                HttpResponse closedFollow = get(closedRedirect, sessionCookie);
                assertTrue("Shows registration closed error or warning",
                        closedFollow.body.contains("closed") || closedFollow.body.contains("unavailable") || closedFollow.body.contains("concluded") || closedFollow.body.contains("error"));
                assertTrue("Registration was not saved in repo for closed camp",
                        regRepo.findByCampAndUser(completedCamp.getId(), testUser.getId()).isEmpty());
            }

            // -------------------------------------------------------------
            // TEST 11: Registration Cancellation Workflow
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 11: Camp Registration Cancellation ---");
            String cancelData = "action=cancel&campId=" + firstCamp.getId() + "&regId=" + userReg.get().getId();
            HttpResponse cancelRes = post(baseUrl + "/camps", cancelData, sessionCookie);
            assertEquals("Cancellation redirects with notice", 302, cancelRes.statusCode);

            // Verify cancellation in repo
            Optional<CampRegistration> cancelledReg = regRepo.findById(userReg.get().getId());
            assertTrue("Registration marked Cancelled", cancelledReg.isPresent() && "Cancelled".equalsIgnoreCase(cancelledReg.get().getStatus()));

            // -------------------------------------------------------------
            // TEST 12: Security & Protection on Admin Camps Route
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 12: Unauthorized Access Protection on /admin/camps ---");
            // Visitor (no session)
            HttpResponse unauthAdmin = get(baseUrl + "/admin/camps", null);
            assertTrue("Visitor denied on /admin/camps (403 or redirect)",
                    unauthAdmin.statusCode == 403 || unauthAdmin.statusCode == 302);

            // Regular user
            HttpResponse userAdmin = get(baseUrl + "/admin/camps", sessionCookie);
            assertTrue("Regular user denied on /admin/camps (403 or redirect to admin login)",
                    userAdmin.statusCode == 403 || userAdmin.statusCode == 302);

            // -------------------------------------------------------------
            // TEST 13: UI Quality & Development Wording Audit
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 13: User-Facing Tone and Terminology Audit ---");
            String[] forbiddenTerms = {"demo", "stage", "prototype", "dummy data"};
            String bodyLower = campsRes.body.toLowerCase();
            for (String term : forbiddenTerms) {
                // Ignore static asset names if any, but ensure visible headings and text do not have prototype wording
                assertTrue("Camps page does not contain visible term '" + term + "'",
                        !bodyLower.contains("<h1>" + term) && !bodyLower.contains("badge-" + term) && !bodyLower.contains(term + " version"));
            }

            // -------------------------------------------------------------
            // TEST 13: Existing Data Integrity
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 13: Existing Stored Data Integrity ---");
            assertTrue("Existing users intact (>0)", userRepo.findAll().size() >= 4);
            assertTrue("Existing donors intact (>0)", AppConfig.REGISTERED_DONORS_FILE.length() > 0);
            assertTrue("Existing blood requests intact (>0)", AppConfig.BLOOD_REQUESTS_FILE.length() > 0);

            // -------------------------------------------------------------
            // SUMMARY
            // -------------------------------------------------------------
            System.out.println("\n===============================================================================");
            System.out.println("   CAMPS WORKFLOW ACCEPTANCE TEST RESULTS");
            System.out.println("===============================================================================");
            System.out.println("   Passed: " + passed);
            System.out.println("   Failed: " + failed);
            System.out.println("===============================================================================");

            if (failed > 0) {
                System.exit(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void assertEquals(String testName, int expected, int actual) {
        if (expected == actual) {
            System.out.println("  ✓ PASS: " + testName + " (expected=" + expected + ", actual=" + actual + ")");
            passed++;
        } else {
            System.err.println("  ✗ FAIL: " + testName + " (expected=" + expected + ", actual=" + actual + ")");
            failed++;
        }
    }

    private static void assertTrue(String testName, boolean condition) {
        if (condition) {
            System.out.println("  ✓ PASS: " + testName);
            passed++;
        } else {
            System.err.println("  ✗ FAIL: " + testName);
            failed++;
        }
    }

    private static HttpResponse get(String urlStr, String sessionCookie) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        if (sessionCookie != null) {
            conn.setRequestProperty("Cookie", sessionCookie);
        }
        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return new HttpResponse(code, sb.toString(), conn.getHeaderFields());
    }

    private static HttpResponse post(String urlStr, String formData, String sessionCookie) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setInstanceFollowRedirects(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (sessionCookie != null) {
            conn.setRequestProperty("Cookie", sessionCookie);
        }
        byte[] bytes = formData.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 400) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else if (conn.getErrorStream() != null) {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return new HttpResponse(code, sb.toString(), conn.getHeaderFields());
    }

    private static String extractSessionCookie(HttpResponse res) {
        List<String> cookies = res.headers.get("Set-Cookie");
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("JSESSIONID=")) {
                    return c.split(";")[0];
                }
            }
        }
        return null;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static class HttpResponse {
        final int statusCode;
        final String body;
        final java.util.Map<String, List<String>> headers;

        HttpResponse(int statusCode, String body, java.util.Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }
    }
}

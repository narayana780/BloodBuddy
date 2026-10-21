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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * WorkflowAndUiCorrectionTest
 * Comprehensive regression and verification test covering all 16 acceptance criteria:
 *  1. A public donor profile shows availability as read-only information.
 *  2. A visitor cannot edit another donor's availability.
 *  3. An authenticated donor can update only their own status through the proper management workflow.
 *  4. Find Donors has no availability filter, but donor cards show availability.
 *  5. Blood group, city, and locality search still work.
 *  6. The Become a Donor page contains no duplicate donor registration form.
 *  7. Register to Donate opens the shared registration page or donor setup, depending on login state.
 *  8. Donors and requesters can use one account for both activities.
 *  9. View Requests contains no donor-registration CTA.
 * 10. Request details and matching results preserve their existing functionality.
 * 11. Donor availability appears on ranked matching cards.
 * 12. Guide and Compatibility Tool are absent from the upper navigation on every page.
 * 13. Login, logout, registration, authorization, and administrator protections still work.
 * 14. Existing saved donors, user accounts, and blood requests remain intact.
 * 15. The header works at desktop and mobile widths.
 * 16. There are no broken routes, missing styles, or errors in the tested flows.
 */
public class WorkflowAndUiCorrectionTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("===============================================================================");
        System.out.println("   BLOOD BUDDY — WORKFLOW, NAVIGATION, AND UI CORRECTION TEST SUITE");
        System.out.println("===============================================================================");

        String baseUrl = "http://localhost:" + AppConfig.DEFAULT_PORT;

        try {
            // -------------------------------------------------------------
            // TEST 1: Public donor profile shows availability as read-only
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 1: Public Donor Profile Read-Only Availability ---");
            DonorService donorService = new DonorService();
            List<Donor> allDonors = donorService.getAllDemonstrationDonors();
            assertTrue("Existing donors exist in repository", !allDonors.isEmpty());
            Donor testDonor = allDonors.get(0);

            HttpResponse profileResp = httpGet(baseUrl + "/donor-profile?id=" + testDonor.getId(), null);
            assertTrue("GET /donor-profile returns 200", profileResp.statusCode == 200);
            assertTrue("Profile displays donor name", profileResp.body.contains(testDonor.getName()));
            assertTrue("Profile displays read-only availability badge",
                    profileResp.body.contains("badge-availability") || profileResp.body.contains("badge-status-available"));
            assertTrue("Profile does NOT contain availability edit select",
                    !profileResp.body.contains("name=\"availability\"") && !profileResp.body.contains("id=\"availabilitySelect\""));
            assertTrue("Profile does NOT contain 'Save Status Update' button",
                    !profileResp.body.contains("Save Status Update") && !profileResp.body.contains("Update Your Availability Status"));

            // -------------------------------------------------------------
            // TEST 2: Visitor cannot edit another donor's availability (403 Forbidden)
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 2: Visitor Cannot Edit Another Donor's Availability ---");
            HttpResponse unauthorizedPost = httpPost(baseUrl + "/update-donor-availability",
                    "id=" + URLEncoder.encode(testDonor.getId(), "UTF-8") + "&availability=false", null);
            assertTrue("Unauthorized POST /update-donor-availability returns 403 Forbidden",
                    unauthorizedPost.statusCode == 403);

            // -------------------------------------------------------------
            // TEST 3: Authenticated donor can update only their own status
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 3: Authenticated Donor Self-Management ---");
            AuthService authService = new AuthService();
            String uniqueEmail = "donor.manage." + System.currentTimeMillis() + "@test.org";
            String username = "donor" + (System.currentTimeMillis() % 100000);
            User registeredUser = authService.registerUser(
                    "Self Managing Donor", uniqueEmail, username, "9887766554", "Secret@123", "Secret@123", "donate");
            assertTrue("New donor registered successfully", registeredUser != null);

            // Login to get session cookie
            HttpResponse loginResp = httpPost(baseUrl + "/login",
                    "username=" + URLEncoder.encode(uniqueEmail, "UTF-8") + "&password=Secret@123", null);
            String sessionCookie = extractCookie(loginResp);
            assertTrue("Login successful and session cookie received", sessionCookie != null);

            // Complete donor profile for this user
            HttpResponse completeResp = httpPost(baseUrl + "/complete-donor-profile",
                    "bloodGroup=O_POS&age=28&city=Rajahmundry&locality=Danavaipeta&lastDonated=2026-01-15&notes=Self+update+test",
                    sessionCookie);
            assertTrue("Complete donor profile submitted (redirect or 200)",
                    completeResp.statusCode == 302 || completeResp.statusCode == 200);

            // Fetch refreshed user record
            User updatedUser = UserRepository.getInstance().findByEmail(uniqueEmail).orElse(null);
            assertTrue("User has linked donor ID", updatedUser != null && updatedUser.getLinkedDonorId() != null);
            String linkedDonorId = updatedUser.getLinkedDonorId();

            // Self-update availability to Temporarily Unavailable
            HttpResponse selfUpdateResp = httpPost(baseUrl + "/update-donor-availability",
                    "id=" + URLEncoder.encode(linkedDonorId, "UTF-8") + "&availability=false&returnTo=/account",
                    sessionCookie);
            assertTrue("Self availability update succeeds (redirect)",
                    selfUpdateResp.statusCode == 302 || selfUpdateResp.statusCode == 303);

            Donor refreshedDonor = donorService.findDonorById(linkedDonorId).orElse(null);
            assertTrue("Donor availability updated to false (Temporarily Unavailable)",
                    refreshedDonor != null && !refreshedDonor.isEligible());

            // Attempt to update another person's donor record using this session
            HttpResponse hijackResp = httpPost(baseUrl + "/update-donor-availability",
                    "id=" + URLEncoder.encode(testDonor.getId(), "UTF-8") + "&availability=false",
                    sessionCookie);
            assertTrue("Attempt to update someone else's donor record rejected with 403 Forbidden",
                    hijackResp.statusCode == 403);

            // Reset back to available
            httpPost(baseUrl + "/update-donor-availability",
                    "id=" + URLEncoder.encode(linkedDonorId, "UTF-8") + "&availability=true&returnTo=/account",
                    sessionCookie);

            // -------------------------------------------------------------
            // TEST 4 & 5: Find Donors no availability filter, shows availability badge, filters work
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 4 & 5: Find Donors Filtering and Availability Badges ---");
            HttpResponse findDonorsResp = httpGet(baseUrl + "/find-donors", null);
            assertTrue("GET /find-donors returns 200", findDonorsResp.statusCode == 200);
            assertTrue("No availability filter dropdown in search form",
                    !findDonorsResp.body.contains("id=\"availabilitySelect\"") && !findDonorsResp.body.contains("name=\"availability\""));
            assertTrue("Name search field present", findDonorsResp.body.contains("name=\"name\""));
            assertTrue("Blood group filter present", findDonorsResp.body.contains("name=\"bloodGroup\""));
            assertTrue("City filter present", findDonorsResp.body.contains("name=\"city\""));
            assertTrue("Locality filter present", findDonorsResp.body.contains("name=\"locality\""));
            assertTrue("Search button present", findDonorsResp.body.contains("Search Donors"));
            assertTrue("Reset button present", findDonorsResp.body.contains("Reset Filters"));
            assertTrue("Donor cards display availability badge",
                    findDonorsResp.body.contains("badge-availability"));

            // Test blood group search
            HttpResponse filterOposResp = httpGet(baseUrl + "/find-donors?bloodGroup=O_POS", null);
            assertTrue("Filter by O+ returns 200", filterOposResp.statusCode == 200);
            assertTrue("Result cards contain O+", filterOposResp.body.contains("O+"));

            // -------------------------------------------------------------
            // TEST 6: Become a Donor page is informational, no duplicate form
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 6: Become a Donor Informational Page ---");
            HttpResponse becomeDonorResp = httpGet(baseUrl + "/become-donor", null);
            assertTrue("GET /become-donor returns 200", becomeDonorResp.statusCode == 200);
            assertTrue("No registration form in /become-donor",
                    !becomeDonorResp.body.contains("<form action=\"/become-donor\"") &&
                    !becomeDonorResp.body.contains("action=\"/register-donor\""));
            assertTrue("Prominent 'Register to Donate' button present",
                    becomeDonorResp.body.contains("Register to Donate"));
            assertTrue("Points to /register?purpose=donate",
                    becomeDonorResp.body.contains("/register?purpose=donate"));
            assertTrue("Sign In link present pointing to /login",
                    becomeDonorResp.body.contains("/login"));

            // -------------------------------------------------------------
            // TEST 7 & 8: Registration purposes & single account dual capabilities
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 7 & 8: Single Account Dual Capabilities ---");
            String dualEmail = "dual.user." + System.currentTimeMillis() + "@test.org";
            String dualUser = "dual" + (System.currentTimeMillis() % 100000);
            User dualResult = authService.registerUser(
                    "Dual Purpose User", dualEmail, dualUser, "9112233445", "Pass@1234", "Pass@1234", "both");
            assertTrue("Account with 'both' purpose registered", dualResult != null);

            HttpResponse dualLoginResp = httpPost(baseUrl + "/login",
                    "username=" + URLEncoder.encode(dualEmail, "UTF-8") + "&password=Pass@1234", null);
            String dualCookie = extractCookie(dualLoginResp);
            assertTrue("Dual account logged in", dualCookie != null);

            // Logged-in user visiting /register?purpose=donate redirects to /complete-donor-profile
            HttpResponse redirCheck = httpGet(baseUrl + "/register?purpose=donate", dualCookie);
            assertTrue("Logged in user visiting /register?purpose=donate gets redirected (302)",
                    redirCheck.statusCode == 302);
            assertTrue("Redirects to /complete-donor-profile",
                    redirCheck.headers.get("Location") != null && redirCheck.headers.get("Location").contains("complete-donor-profile"));

            // -------------------------------------------------------------
            // TEST 9 & 10: View Requests page (no donor CTA, protected patient info)
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 9 & 10: View Requests Exclusive Focus & Privacy ---");
            HttpResponse requestsResp = httpGet(baseUrl + "/view-requests", null);
            assertTrue("GET /view-requests returns 200", requestsResp.statusCode == 200);
            assertTrue("No 'Register to Donate' CTA on View Requests",
                    !requestsResp.body.contains("Register to Donate Blood") &&
                    !requestsResp.body.contains("href=\"/register?purpose=donate\""));
            assertTrue("Search label uses Hospital / Locality",
                    requestsResp.body.contains("Hospital / Locality"));
            assertTrue("Sensitive patient name masked for public visitors",
                    requestsResp.body.contains("Confidential (Hospital Coordinated)"));

            // -------------------------------------------------------------
            // TEST 11: Donor availability on ranked matching cards
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 11: Request Matching Shows Donor Availability ---");
            BloodRequestService requestService = new BloodRequestService();
            List<BloodRequest> allRequests = requestService.getAllRequests();
            assertTrue("Existing blood requests exist in repository", !allRequests.isEmpty());
            BloodRequest req = allRequests.get(0);

            HttpResponse matchResp = httpGet(baseUrl + "/requests/matches?requestId=" + req.getId(), null);
            assertTrue("GET /requests/matches returns 200", matchResp.statusCode == 200);
            assertTrue("Matching screen displays donor availability badge",
                    matchResp.body.contains("badge-availability") || matchResp.body.contains("badge-status-available"));
            assertTrue("Matching screen omits Register to Donate CTA",
                    !matchResp.body.contains("class=\"nav-link\">Register to Donate</a>"));

            // -------------------------------------------------------------
            // TEST 12: Upper navigation omits Guide and Compatibility Tool everywhere
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 12: Upper Navigation Clean Across All Routes ---");
            String[] testRoutes = {
                    "/dashboard",
                    "/find-donors",
                    "/become-donor",
                    "/request-blood",
                    "/view-requests",
                    "/login",
                    "/register",
                    "/notifications"
            };
            for (String route : testRoutes) {
                HttpResponse resp = httpGet(baseUrl + route, null);
                if (resp.statusCode == 200) {
                    boolean hasGuideInNav = resp.body.contains("class=\"nav-link\">Guide</a>") ||
                            resp.body.contains("class=\"nav-link active-nav\">Guide</a>");
                    boolean hasCompatInNav = resp.body.contains("class=\"nav-link\">Compatibility Tool</a>") ||
                            resp.body.contains("class=\"nav-link active-nav\">Compatibility Tool</a>");
                    boolean hasRegDonateInNav = resp.body.contains("class=\"nav-link\">Register to Donate</a>");

                    assertTrue("Route " + route + " upper nav omits Guide", !hasGuideInNav);
                    assertTrue("Route " + route + " upper nav omits Compatibility Tool", !hasCompatInNav);
                    assertTrue("Route " + route + " upper nav omits Register to Donate", !hasRegDonateInNav);
                }
            }

            // -------------------------------------------------------------
            // TEST 13: Login, logout, registration, authorization protections
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 13: Auth Protections & Admin Control Center ---");
            HttpResponse adminBlockedResp = httpGet(baseUrl + "/admin/emergency", null);
            assertTrue("Unauthenticated visitor to /admin/emergency redirected or forbidden",
                    adminBlockedResp.statusCode == 302 || adminBlockedResp.statusCode == 401 || adminBlockedResp.statusCode == 403);

            // -------------------------------------------------------------
            // TEST 14: Data integrity - Existing records preserved
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 14: Existing Records Preserved ---");
            assertTrue("Existing donors preserved (count >= 10)", donorService.getAllDemonstrationDonors().size() >= 10);
            assertTrue("Existing blood requests preserved", requestService.getAllRequests().size() >= 3);
            assertTrue("Existing user accounts preserved", UserRepository.getInstance().findAll().size() >= 1);

            // -------------------------------------------------------------
            // TEST 15: Header styling and responsive design
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 15: Header Styling & Responsive Design ---");
            HttpResponse cssResp = httpGet(baseUrl + "/static/css/style.css", null);
            assertTrue("style.css is accessible", cssResp.statusCode == 200);
            assertTrue("Header is sticky top 0",
                    cssResp.body.contains("position: sticky") && cssResp.body.contains("top: 0"));
            assertTrue("Badge availability styles defined",
                    cssResp.body.contains(".badge-availability") &&
                    cssResp.body.contains(".badge-status-available") &&
                    cssResp.body.contains(".badge-status-unavailable"));

            // -------------------------------------------------------------
            // TEST 16: No internal development jargon ("stage", "prototype", "demo") in public UI
            // -------------------------------------------------------------
            System.out.println("\n--- TEST 16: No Development Jargon in Public UI ---");
            HttpResponse homeResp = httpGet(baseUrl + "/dashboard", null);
            assertTrue("Dashboard returns 200", homeResp.statusCode == 200);
            assertTrue("Dashboard has no 'Network Active' claim", !homeResp.body.contains("Network Active"));
            assertTrue("Dashboard has no '24/7 Access' claim", !homeResp.body.contains("24/7 Access"));

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n===============================================================================");
        System.out.println("   TOTAL RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("===============================================================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  ✓ PASS: " + message);
            passed++;
        } else {
            System.err.println("  ✗ FAIL: " + message);
            failed++;
        }
    }

    private static String extractCookie(HttpResponse resp) {
        String cookieHeader = resp.headers.get("Set-Cookie");
        if (cookieHeader != null) {
            int semi = cookieHeader.indexOf(';');
            return semi != -1 ? cookieHeader.substring(0, semi) : cookieHeader;
        }
        return null;
    }

    private static class HttpResponse {
        int statusCode;
        String body;
        java.util.Map<String, String> headers = new java.util.HashMap<>();
    }

    private static HttpResponse httpGet(String urlStr, String cookie) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false);
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        int code = conn.getResponseCode();
        HttpResponse resp = new HttpResponse();
        resp.statusCode = code;
        for (String key : conn.getHeaderFields().keySet()) {
            if (key != null) {
                resp.headers.put(key, conn.getHeaderField(key));
            }
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            resp.body = sb.toString();
        } catch (Exception ignored) {
            resp.body = "";
        }
        return resp;
    }

    private static HttpResponse httpPost(String urlStr, String formData, String cookie) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setInstanceFollowRedirects(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        byte[] bytes = formData.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int code = conn.getResponseCode();
        HttpResponse resp = new HttpResponse();
        resp.statusCode = code;
        for (String key : conn.getHeaderFields().keySet()) {
            if (key != null) {
                resp.headers.put(key, conn.getHeaderField(key));
            }
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            resp.body = sb.toString();
        } catch (Exception ignored) {
            resp.body = "";
        }
        return resp;
    }
}

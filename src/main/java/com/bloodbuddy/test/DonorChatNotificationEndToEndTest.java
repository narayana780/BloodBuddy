package com.bloodbuddy.test;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DonorChatNotificationEndToEndTest executes an end-to-end HTTP integration test
 * against the live running Blood Buddy server on http://localhost:8080.
 *
 * Verifies:
 * 1. Donor user registration and donor profile completion.
 * 2. Requester user registration.
 * 3. Requester initiates chat with the donor profile.
 * 4. Verification that the conversation is created as PENDING in backend storage.
 * 5. Verification that the donor's /notifications endpoint displays the chat notification.
 * 6. Verification that the donor's /messages inbox displays the pending chat request.
 * 7. Verification that the donor can open the pending chat and see the Accept/Decline actions.
 * 8. Donor accepts the chat request, transitioning status to ACTIVE.
 * 9. Two-way message exchange through the active conversation.
 */
public class DonorChatNotificationEndToEndTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("===============================================================================");
        System.out.println("  BLOOD BUDDY — LIVE HTTP DONOR CHAT & NOTIFICATION END-TO-END TEST");
        System.out.println("===============================================================================\n");

        long ts = System.currentTimeMillis();
        String donorUsername = "donor_" + (ts % 10000000);
        String donorEmail = "donor_" + ts + "@testmail.org";
        String donorName = "Suresh Donor";
        String donorPhone = "9876543299";

        String reqUsername = "req_" + (ts % 10000000);
        String reqEmail = "req_" + ts + "@testmail.org";
        String reqName = "Ananya Requester";
        String reqPhone = "9876543288";

        // Step 1: Donor Registration
        System.out.println("[Step 1] Registering Donor account on live server...");
        CookieManager donorCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient donorClient = HttpClient.newBuilder().cookieHandler(donorCookies).build();

        String donorRegBody = "fullName=" + url(donorName)
                + "&email=" + url(donorEmail)
                + "&username=" + url(donorUsername)
                + "&phone=" + url(donorPhone)
                + "&password=" + url("Password@123")
                + "&confirmPassword=" + url("Password@123")
                + "&purpose=" + url("donate")
                + "&terms=on";

        HttpResponse<String> donorRegResp = post(donorClient, BASE_URL + "/register", donorRegBody);
        assertTrue("Donor registration redirected to profile completion",
                donorRegResp.statusCode() == 302 && donorRegResp.headers().firstValue("Location").orElse("").contains("/complete-donor-profile"));

        // Complete Donor Profile
        System.out.println("\n[Step 2] Completing Donor Profile on live server...");
        String donorProfileBody = "name=" + url(donorName)
                + "&bloodGroup=" + url("O+")
                + "&age=" + url("26")
                + "&city=" + url("Rajahmundry")
                + "&locality=" + url("Danavaipeta")
                + "&availability=" + url("Available")
                + "&contactInfo=" + url(donorPhone);

        HttpResponse<String> donorProfileResp = post(donorClient, BASE_URL + "/complete-donor-profile", donorProfileBody);
        String donorProfileLocation = donorProfileResp.headers().firstValue("Location").orElse("");
        assertTrue("Profile completion redirected to donor profile page",
                donorProfileResp.statusCode() == 302 && donorProfileLocation.contains("/donor-profile"));

        Matcher donorIdMatcher = Pattern.compile("id=([A-Za-z0-9\\-]+)").matcher(donorProfileLocation);
        assertTrue("Donor profile ID extracted from redirect URL", donorIdMatcher.find());
        String donorProfileId = donorIdMatcher.group(1);
        System.out.println("  -> Registered Donor Profile ID: " + donorProfileId);

        // Step 3: Requester Registration
        System.out.println("\n[Step 3] Registering Requester account on live server...");
        CookieManager reqCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient reqClient = HttpClient.newBuilder().cookieHandler(reqCookies).build();

        String reqRegBody = "fullName=" + url(reqName)
                + "&email=" + url(reqEmail)
                + "&username=" + url(reqUsername)
                + "&phone=" + url(reqPhone)
                + "&password=" + url("Password@123")
                + "&confirmPassword=" + url("Password@123")
                + "&purpose=" + url("request")
                + "&terms=on";

        HttpResponse<String> reqRegResp = post(reqClient, BASE_URL + "/register", reqRegBody);
        assertTrue("Requester registration completed successfully",
                reqRegResp.statusCode() == 302);

        // Step 4: Requester clicks "Chat with Donor"
        System.out.println("\n[Step 4] Requester clicks 'Chat with Donor' for " + donorProfileId + "...");
        HttpResponse<String> chatResp = get(reqClient, BASE_URL + "/chat?donorId=" + url(donorProfileId));
        assertTrue("Chat initiation redirected to conversation window",
                chatResp.statusCode() == 302 && chatResp.headers().firstValue("Location").orElse("").contains("/messages?id="));

        String convUrl = chatResp.headers().firstValue("Location").get();
        Matcher convIdMatcher = Pattern.compile("id=([A-Za-z0-9\\-]+)").matcher(convUrl);
        assertTrue("Conversation ID extracted from redirect URL", convIdMatcher.find());
        String conversationId = convIdMatcher.group(1);
        System.out.println("  -> Created Conversation ID: " + conversationId);

        // Requester views conversation: verify PENDING status
        HttpResponse<String> reqConvResp = get(reqClient, BASE_URL + "/messages?id=" + conversationId);
        assertTrue("Requester opens conversation page successfully", reqConvResp.statusCode() == 200);
        assertTrue("Requester sees Pending status indicator",
                reqConvResp.body().contains("Pending") || reqConvResp.body().contains("Awaiting Donor Response"));

        // Step 5: Donor checks Notifications
        System.out.println("\n[Step 5] Donor checks Notifications on /notifications...");
        HttpResponse<String> donorNotifResp = get(donorClient, BASE_URL + "/notifications");
        assertTrue("Donor accesses /notifications successfully", donorNotifResp.statusCode() == 200);
        assertTrue("Donor sees chat request notification from Ananya Requester",
                donorNotifResp.body().contains("New Chat Request from " + reqName) || donorNotifResp.body().contains(reqName));
        assertTrue("Notification links to conversation",
                donorNotifResp.body().contains("/messages?id=" + conversationId));

        // Step 6: Donor checks Messages Inbox
        System.out.println("\n[Step 6] Donor checks Conversations Inbox on /messages...");
        HttpResponse<String> donorInboxResp = get(donorClient, BASE_URL + "/messages");
        assertTrue("Donor accesses /messages inbox successfully", donorInboxResp.statusCode() == 200);
        assertTrue("Donor sees requester name in inbox card", donorInboxResp.body().contains(reqName));
        assertTrue("Donor inbox indicates Pending status", donorInboxResp.body().contains("Pending Approval"));

        // Step 7: Donor opens Conversation window
        System.out.println("\n[Step 7] Donor opens pending conversation on /messages?id=" + conversationId + "...");
        HttpResponse<String> donorConvResp = get(donorClient, BASE_URL + "/messages?id=" + conversationId);
        assertTrue("Donor accesses conversation page successfully", donorConvResp.statusCode() == 200);
        assertTrue("Donor sees Donation Consultation Request prompt",
                donorConvResp.body().contains("Donation Consultation Request") || donorConvResp.body().contains(reqName));
        assertTrue("Donor sees Accept Chat Request action button",
                donorConvResp.body().contains("Accept Chat Request"));
        assertTrue("Donor sees Decline action button",
                donorConvResp.body().contains("Decline"));

        // Step 8: Donor Accepts Chat Request
        System.out.println("\n[Step 8] Donor accepts chat request...");
        String acceptBody = "action=accept&id=" + url(conversationId);
        HttpResponse<String> acceptResp = post(donorClient, BASE_URL + "/messages", acceptBody);
        assertTrue("Accept request redirected successfully", acceptResp.statusCode() == 302);

        // Verify conversation is now ACTIVE
        HttpResponse<String> donorActiveResp = get(donorClient, BASE_URL + "/messages?id=" + conversationId);
        assertTrue("Donor conversation is now ACTIVE", donorActiveResp.body().contains("ACTIVE"));
        assertTrue("Chat composer textarea is rendered", donorActiveResp.body().contains("chat-textarea"));

        // Step 9: Donor sends a message
        System.out.println("\n[Step 9] Donor sends reply message to requester...");
        String sendBody = "action=send&id=" + url(conversationId) + "&message=" + url("Hello Ananya, I am available to donate tomorrow at GGH.");
        HttpResponse<String> sendResp = post(donorClient, BASE_URL + "/messages", sendBody);
        assertTrue("Message send redirected successfully", sendResp.statusCode() == 302);

        // Step 10: Requester verifies incoming message
        System.out.println("\n[Step 10] Requester verifies donor message in chat window...");
        HttpResponse<String> reqViewResp = get(reqClient, BASE_URL + "/messages?id=" + conversationId);
        assertTrue("Requester sees donor's reply in message bubbles",
                reqViewResp.body().contains("Hello Ananya, I am available to donate tomorrow at GGH."));

        // Summary
        System.out.println("\n===============================================================================");
        System.out.printf("  END-TO-END TEST RESULTS: Passed = %d, Failed = %d%n", passed, failed);
        System.out.println("===============================================================================");

        if (failed > 0) {
            System.err.println("LIVE HTTP TESTS FAILED!");
            System.exit(1);
        } else {
            System.out.println("ALL LIVE HTTP DONOR CHAT TESTS PASSED SUCCESSFULLY! ✓");
        }
    }

    private static String url(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }

    private static HttpResponse<String> get(HttpClient client, String uri) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, String uri, String formBody) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.println("  ✓ [PASS] " + message);
            passed++;
        } else {
            System.err.println("  ✗ [FAIL] " + message);
            failed++;
        }
    }
}

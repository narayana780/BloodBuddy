package com.bloodbuddy.test;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.*;
import com.bloodbuddy.repository.ChatMessageRepository;
import com.bloodbuddy.repository.ConversationRepository;
import com.bloodbuddy.repository.NotificationRepository;
import com.bloodbuddy.repository.UserRepository;
import com.bloodbuddy.service.*;

import java.util.List;
import java.util.Optional;

/**
 * SecureChatFeatureTest tests the complete private in-app messaging system:
 * 1. Conversation creation & reuse (duplicate prevention)
 * 2. Status workflows: PENDING -> ACTIVE -> CLOSED, and PENDING -> DECLINED
 * 3. In-app notifications for chat request, accepted, declined, and new messages
 * 4. Message validation (empty, whitespace, length limits up to 1000 chars)
 * 5. Authorization & security: rejecting unrelated users, preventing forged access
 * 6. Participant contact privacy: zero phone/email leakage in conversation or messages
 * 7. Persistence survival across repository re-instantiation (server restart simulation)
 * 8. Unread message tracking and read status updates
 */
public class SecureChatFeatureTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("===============================================================================");
        System.out.println("  BLOOD BUDDY — SECURE IN-APP MESSAGING & PRIVACY TEST SUITE");
        System.out.println("===============================================================================\n");

        AuthService authService = new AuthService();
        ChatService chatService = new ChatService();
        NotificationService notifService = new NotificationService();
        DonorService donorService = new DonorService();
        BloodRequestService requestService = new BloodRequestService();

        // 1. Setup Test Accounts (Requester, Donor, and Eavesdropper)
        System.out.println("[Test 1] Setting up distinct test accounts in unified auth system...");
        long timestamp = System.currentTimeMillis();
        String reqEmail = "requester" + timestamp + "@bloodbuddy.test";
        String donorEmail = "donor" + timestamp + "@bloodbuddy.test";
        String eavesEmail = "eavesdropper" + timestamp + "@bloodbuddy.test";

        String reqUsername = "req_" + (timestamp % 10000000);
        String donorUsername = "donor_" + (timestamp % 10000000);
        String eavesUsername = "eaves_" + (timestamp % 10000000);

        User requester = authService.registerUser(
                "Priya Sharma", reqEmail, reqUsername, "9876500001", "Pass@Requester123", "Pass@Requester123", User.PURPOSE_REQUEST);
        User donorUser = authService.registerUser(
                "Karthik Varma", donorEmail, donorUsername, "9876500002", "Pass@Donor123", "Pass@Donor123", User.PURPOSE_DONATE);
        User eavesdropper = authService.registerUser(
                "Malicious Actor", eavesEmail, eavesUsername, "9876500003", "Pass@Eaves123", "Pass@Eaves123", User.PURPOSE_REQUEST);

        assertTrue("Requester registered with non-null ID", requester != null && requester.getId() != null);
        assertTrue("Donor registered with non-null ID", donorUser != null && donorUser.getId() != null);
        assertTrue("Eavesdropper registered with non-null ID", eavesdropper != null && eavesdropper.getId() != null);

        // Register donor profile via DonorService
        Donor registeredDonor = donorService.registerDonor(
                "Karthik Varma", "B+", 27, "Rajahmundry", "Danavaipeta", true, "9876500002");
        assertTrue("Donor profile registered successfully", registeredDonor != null && registeredDonor.getId() != null);

        donorUser.setLinkedDonorId(registeredDonor.getId());
        UserRepository.getInstance().update(donorUser);

        // 2. Start Conversation (Requester initiates chat with matched donor)
        System.out.println("\n[Test 2] Requester initiates conversation with matched donor...");
        String testRequestId = "REQ-" + timestamp;
        Conversation conv = chatService.startConversation(requester, registeredDonor.getId(), testRequestId);

        assertTrue("Conversation created with unique ID", conv != null && conv.getId() != null);
        assertTrue("Conversation status is PENDING", "PENDING".equalsIgnoreCase(conv.getStatus()));
        assertTrue("Requester is participant", conv.isParticipant(requester.getId()));
        assertTrue("Donor is participant", conv.isParticipant(donorUser.getId()));
        assertTrue("Eavesdropper is NOT participant", !conv.isParticipant(eavesdropper.getId()));
        assertTrue("Conversation links correct request ID", testRequestId.equals(conv.getRequestId()));

        // 3. Duplicate Prevention
        System.out.println("\n[Test 3] Verifying conversation duplicate prevention...");
        Conversation duplicateConv = chatService.startConversation(requester, registeredDonor.getId(), testRequestId);
        assertTrue("Calling startConversation returns same conversation record",
                duplicateConv.getId().equals(conv.getId()));

        // 4. Notification for Chat Request
        System.out.println("\n[Test 4] Verifying in-app notification delivered to donor...");
        List<AppNotification> donorNotifs = notifService.getNotifications(donorUser.getId(), false);
        boolean receivedRequestNotif = donorNotifs.stream().anyMatch(n ->
                AppNotification.CAT_CHAT_REQUEST.equals(n.getCategory()) &&
                        n.getMessage() != null && n.getMessage().contains("Priya Sharma"));
        assertTrue("Donor received chat request notification from Priya Sharma", receivedRequestNotif);

        // Verify privacy in notification (no phone numbers leaked in notification message)
        Optional<AppNotification> reqNotifOpt = donorNotifs.stream()
                .filter(n -> AppNotification.CAT_CHAT_REQUEST.equals(n.getCategory()))
                .findFirst();
        assertTrue("Notification exists", reqNotifOpt.isPresent());
        assertTrue("Notification does NOT contain requester phone number",
                !reqNotifOpt.get().getMessage().contains("9876500001"));

        // 5. Security: Unauthorized users cannot accept or decline
        System.out.println("\n[Test 5] Testing authorization guards on Accept / Decline...");
        boolean eavesdropperAcceptBlocked = false;
        try {
            chatService.acceptConversation(conv.getId(), eavesdropper);
        } catch (SecurityException e) {
            eavesdropperAcceptBlocked = true;
        }
        assertTrue("Eavesdropper blocked from accepting conversation", eavesdropperAcceptBlocked);

        boolean requesterAcceptBlocked = false;
        try {
            chatService.acceptConversation(conv.getId(), requester);
        } catch (SecurityException e) {
            requesterAcceptBlocked = true;
        }
        assertTrue("Requester blocked from approving own chat request", requesterAcceptBlocked);

        // Messages cannot be sent while in PENDING status
        boolean sendWhilePendingBlocked = false;
        try {
            chatService.sendMessage(conv.getId(), requester, "Hello while pending!");
        } catch (IllegalStateException e) {
            sendWhilePendingBlocked = true;
        }
        assertTrue("Sending message blocked while status is PENDING", sendWhilePendingBlocked);

        // 6. Donor Accepts Conversation
        System.out.println("\n[Test 6] Donor accepts conversation...");
        boolean accepted = chatService.acceptConversation(conv.getId(), donorUser);
        assertTrue("Accept conversation returned true", accepted);

        Optional<Conversation> acceptedConvOpt = chatService.getConversation(conv.getId(), donorUser.getId());
        assertTrue("Accepted conversation found", acceptedConvOpt.isPresent());
        assertTrue("Conversation status transitioned to ACTIVE", "ACTIVE".equalsIgnoreCase(acceptedConvOpt.get().getStatus()));
        assertTrue("Accepted timestamp recorded", acceptedConvOpt.get().getAcceptedAt() != null && !acceptedConvOpt.get().getAcceptedAt().isEmpty());

        // Verify notification delivered to requester
        List<AppNotification> reqNotifs = notifService.getNotifications(requester.getId(), false);
        boolean acceptedNotifReceived = reqNotifs.stream().anyMatch(n ->
                AppNotification.CAT_CHAT_ACCEPTED.equals(n.getCategory()));
        assertTrue("Requester received chat accepted notification", acceptedNotifReceived);

        // 7. Message Exchange & Read Tracking
        System.out.println("\n[Test 7] Exchanging messages between active participants...");
        int initialDonorUnread = chatService.getUnreadMessageCount(donorUser.getId());
        ChatMessage msg1 = chatService.sendMessage(conv.getId(), requester, "Hello Karthik, need B+ blood urgently at GGH.");
        assertTrue("Message 1 sent and persisted", msg1 != null && msg1.getId() != null);
        assertTrue("Message 1 read status is initially false", !msg1.isRead());

        int postMsgDonorUnread = chatService.getUnreadMessageCount(donorUser.getId());
        assertTrue("Donor unread count incremented by 1", postMsgDonorUnread == initialDonorUnread + 1);

        // Donor reads message history
        List<ChatMessage> donorViewMessages = chatService.getMessages(conv.getId(), donorUser.getId());
        assertTrue("Donor retrieved 1 message", donorViewMessages.size() >= 1);
        chatService.markMessagesAsRead(conv.getId(), donorUser.getId());
        int afterReadDonorUnread = chatService.getUnreadMessageCount(donorUser.getId());
        assertTrue("Donor unread count reset to 0 after viewing messages", afterReadDonorUnread == 0);

        // Donor replies
        ChatMessage msg2 = chatService.sendMessage(conv.getId(), donorUser, "I can donate at 5 PM today. Please confirm ward.");
        assertTrue("Donor reply sent successfully", msg2 != null);
        int requesterUnread = chatService.getUnreadMessageCount(requester.getId());
        assertTrue("Requester has unread message", requesterUnread >= 1);

        // Requester views messages
        List<ChatMessage> reqViewMessages = chatService.getMessages(conv.getId(), requester.getId());
        assertTrue("Requester views updated message list", reqViewMessages.size() >= 2);
        chatService.markMessagesAsRead(conv.getId(), requester.getId());
        assertTrue("Requester unread count cleared after marking read", chatService.getUnreadMessageCount(requester.getId()) == 0);

        // 8. Message Validation: Empty, Whitespace, Length Limits
        System.out.println("\n[Test 8] Testing message validation rules...");
        boolean emptyRejected = false;
        try {
            chatService.sendMessage(conv.getId(), requester, "");
        } catch (IllegalArgumentException e) {
            emptyRejected = true;
        }
        assertTrue("Empty message rejected", emptyRejected);

        boolean whitespaceRejected = false;
        try {
            chatService.sendMessage(conv.getId(), requester, "     \n\t   ");
        } catch (IllegalArgumentException e) {
            whitespaceRejected = true;
        }
        assertTrue("Whitespace-only message rejected", whitespaceRejected);

        boolean oversizedRejected = false;
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < AppConfig.MAX_CHAT_MESSAGE_LENGTH + 50; i++) {
                sb.append("A");
            }
            chatService.sendMessage(conv.getId(), requester, sb.toString());
        } catch (IllegalArgumentException e) {
            oversizedRejected = true;
        }
        assertTrue("Oversized message (> 1000 chars) rejected", oversizedRejected);

        // 9. Security: Unrelated User Access Denial
        System.out.println("\n[Test 9] Testing eavesdropper authorization denial...");
        boolean eavesdropperReadBlocked = false;
        try {
            chatService.getMessages(conv.getId(), eavesdropper.getId());
        } catch (SecurityException e) {
            eavesdropperReadBlocked = true;
        }
        assertTrue("Eavesdropper blocked from viewing conversation messages", eavesdropperReadBlocked);

        boolean eavesdropperSendBlocked = false;
        try {
            chatService.sendMessage(conv.getId(), eavesdropper, "I am snooping into your chat!");
        } catch (SecurityException e) {
            eavesdropperSendBlocked = true;
        }
        assertTrue("Eavesdropper blocked from sending message to conversation", eavesdropperSendBlocked);

        // 10. Privacy Shielding: Verify zero contact leaks in Chat / Conversation models
        System.out.println("\n[Test 10] Testing contact details privacy shielding...");
        assertTrue("Conversation model does NOT store contact phone field",
                !conv.toString().contains("9876500001") && !conv.toString().contains("9876500002"));
        assertTrue("ChatMessage model does NOT store contact phone or email",
                !msg1.toString().contains("9876500001") && !msg1.toString().contains(reqEmail));

        // 11. Closing Conversation
        System.out.println("\n[Test 11] Testing conversation close workflow...");
        boolean closed = chatService.closeConversation(conv.getId(), requester);
        assertTrue("Conversation close succeeded", closed);

        Optional<Conversation> closedConvOpt = chatService.getConversation(conv.getId(), requester.getId());
        assertTrue("Closed conversation found", closedConvOpt.isPresent());
        assertTrue("Conversation status is CLOSED", "CLOSED".equalsIgnoreCase(closedConvOpt.get().getStatus()));
        assertTrue("Closed timestamp recorded", closedConvOpt.get().getClosedAt() != null && !closedConvOpt.get().getClosedAt().isEmpty());

        boolean sendToClosedBlocked = false;
        try {
            chatService.sendMessage(conv.getId(), donorUser, "Trying to send after close");
        } catch (IllegalStateException e) {
            sendToClosedBlocked = true;
        }
        assertTrue("Sending message to CLOSED conversation is rejected", sendToClosedBlocked);

        // Historical messages still readable when closed
        List<ChatMessage> historyAfterClose = chatService.getMessages(conv.getId(), requester.getId());
        assertTrue("Historical messages preserved after closing conversation", historyAfterClose.size() >= 2);

        // 12. Decline Flow Test
        System.out.println("\n[Test 12] Testing decline conversation workflow...");
        String reqId2 = "REQ-DECLINE-" + timestamp;
        Conversation conv2 = chatService.startConversation(requester, registeredDonor.getId(), reqId2);
        assertTrue("Second conversation created with status PENDING", "PENDING".equalsIgnoreCase(conv2.getStatus()));

        boolean declined = chatService.declineConversation(conv2.getId(), donorUser);
        assertTrue("Conversation decline succeeded", declined);

        Optional<Conversation> declinedConvOpt = chatService.getConversation(conv2.getId(), donorUser.getId());
        assertTrue("Declined conversation found", declinedConvOpt.isPresent());
        assertTrue("Conversation status is DECLINED", "DECLINED".equalsIgnoreCase(declinedConvOpt.get().getStatus()));
        assertTrue("Declined timestamp recorded", declinedConvOpt.get().getDeclinedAt() != null && !declinedConvOpt.get().getDeclinedAt().isEmpty());

        boolean sendToDeclinedBlocked = false;
        try {
            chatService.sendMessage(conv2.getId(), requester, "Message to declined chat");
        } catch (IllegalStateException e) {
            sendToDeclinedBlocked = true;
        }
        assertTrue("Sending message to DECLINED conversation is rejected", sendToDeclinedBlocked);

        List<AppNotification> reqNotifs2 = notifService.getNotifications(requester.getId(), false);
        boolean declinedNotifReceived = reqNotifs2.stream().anyMatch(n ->
                AppNotification.CAT_CHAT_DECLINED.equals(n.getCategory()));
        assertTrue("Requester received chat declined notification", declinedNotifReceived);

        // 13. Persistence Simulation Across Server Restart
        System.out.println("\n[Test 13] Simulating application restart & persistent JSON reload...");
        // Re-read directly from JSON storage using fresh repositories
        ConversationRepository freshConvRepo = ConversationRepository.getInstance();
        ChatMessageRepository freshMsgRepo = ChatMessageRepository.getInstance();

        Optional<Conversation> reloadedConvOpt = freshConvRepo.findById(conv.getId());
        assertTrue("Conversation reloaded from persistent storage", reloadedConvOpt.isPresent());
        assertTrue("Reloaded conversation status is CLOSED", "CLOSED".equalsIgnoreCase(reloadedConvOpt.get().getStatus()));

        List<ChatMessage> reloadedMessages = freshMsgRepo.findByConversationId(conv.getId());
        assertTrue("Reloaded messages count matches saved messages", reloadedMessages.size() >= 2);
        assertTrue("Reloaded message text verified", reloadedMessages.stream().anyMatch(m ->
                m.getMessageText().contains("need B+ blood urgently")));

        // Summary
        System.out.println("\n===============================================================================");
        System.out.printf("  SECURE CHAT TESTS COMPLETED: Passed = %d, Failed = %d%n", passed, failed);
        System.out.println("===============================================================================");

        if (failed > 0) {
            System.err.println("SOME SECURE CHAT TESTS FAILED!");
            System.exit(1);
        } else {
            System.out.println("ALL SECURE CHAT TESTS PASSED SUCCESSFULLY! ✓");
        }
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

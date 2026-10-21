package com.bloodbuddy.service;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.ChatMessage;
import com.bloodbuddy.model.Conversation;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.User;
import com.bloodbuddy.repository.ChatMessageRepository;
import com.bloodbuddy.repository.ConversationRepository;
import com.bloodbuddy.repository.UserRepository;
import com.bloodbuddy.util.PasswordUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service managing secure in-app conversations, message delivery,
 * authorization checks, approval lifecycle, and notifications.
 */
public class ChatService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final DonorService donorService;
    private final BloodRequestService bloodRequestService;
    private final NotificationService notificationService;

    public ChatService() {
        this(ConversationRepository.getInstance(),
             ChatMessageRepository.getInstance(),
             UserRepository.getInstance(),
             new DonorService(),
             new BloodRequestService(),
             new NotificationService());
    }

    public ChatService(ConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       UserRepository userRepository,
                       DonorService donorService,
                       BloodRequestService bloodRequestService,
                       NotificationService notificationService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.donorService = donorService;
        this.bloodRequestService = bloodRequestService;
        this.notificationService = notificationService;
    }

    /**
     * Resolves or provisions a real user account for a donor so the donor can receive notifications and log in.
     */
    public User findOrCreateDonorUser(Donor donor) {
        if (donor == null || donor.getId() == null) {
            throw new IllegalArgumentException("Donor must not be null");
        }

        // 1. Direct link check
        Optional<User> byLinked = userRepository.findByLinkedDonorId(donor.getId());
        if (byLinked.isPresent()) {
            return byLinked.get();
        }

        // 2. Phone match check
        String contact = donor.getContactInfo();
        if (contact != null && !contact.trim().isEmpty()) {
            String digits = contact.replaceAll("[^0-9]", "");
            if (digits.length() >= 10) {
                for (User u : userRepository.findAll()) {
                    String uDigits = u.getPhone() != null ? u.getPhone().replaceAll("[^0-9]", "") : "";
                    if (!uDigits.isEmpty() && uDigits.equals(digits)) {
                        u.setLinkedDonorId(donor.getId());
                        userRepository.update(u);
                        return u;
                    }
                }
            }
        }

        // 3. Provision real user account for seed/unlinked donor
        String cleanId = donor.getId().toLowerCase().replaceAll("[^a-z0-9]", "");
        String username = "donor_" + cleanId;
        String email = "donor." + cleanId + "@bloodbuddy.org";

        // Check if user with this username/email already exists
        Optional<User> existing = userRepository.findByEmailOrUsername(username);
        if (existing.isPresent()) {
            User u = existing.get();
            u.setLinkedDonorId(donor.getId());
            userRepository.update(u);
            return u;
        }

        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("LifeSaver@2026", salt);
        String nowStr = LocalDateTime.now().format(DATE_FORMATTER);

        User newUser = new User(null, donor.getName(), email, username,
                donor.getContactInfo(), hash, salt, User.ROLE_USER, nowStr, donor.getId(), User.PURPOSE_DONATE);

        return userRepository.save(newUser);
    }

    /**
     * Initiates or retrieves an existing conversation between an authenticated requester and a donor.
     * Prevents duplicate conversations for the same participants and request.
     */
    public Conversation startConversation(User requester, String donorProfileId, String requestId) {
        if (requester == null || requester.getId() == null) {
            throw new SecurityException("Authentication is required to start a conversation.");
        }
        if (donorProfileId == null || donorProfileId.trim().isEmpty()) {
            throw new IllegalArgumentException("Donor profile identifier is required.");
        }

        Optional<Donor> donorOpt = donorService.findDonorById(donorProfileId.trim());
        if (donorOpt.isEmpty()) {
            throw new IllegalArgumentException("Selected donor profile was not found.");
        }

        Donor donor = donorOpt.get();
        if (!donor.isEligible()) {
            throw new IllegalStateException("Selected donor is currently marked as unavailable or in a resting period.");
        }

        User donorUser = findOrCreateDonorUser(donor);

        if (requester.getId().equalsIgnoreCase(donorUser.getId())) {
            throw new IllegalArgumentException("You cannot initiate a conversation with your own donor profile.");
        }

        String cleanRequestId = (requestId != null) ? requestId.trim() : "";

        // Check for existing eligible (active or pending) conversation to prevent accidental duplicates
        Optional<Conversation> existing = conversationRepository.findExistingConversation(
                requester.getId(), donorUser.getId(), cleanRequestId);
        if (existing.isPresent() && !existing.get().isClosed() && !existing.get().isDeclined()) {
            return existing.get();
        }

        // Also check if an active conversation exists without request filter
        if (cleanRequestId.isEmpty()) {
            for (Conversation c : conversationRepository.findByUserId(requester.getId())) {
                if (c.getDonorUserId().equalsIgnoreCase(donorUser.getId()) && !c.isClosed() && !c.isDeclined()) {
                    return c;
                }
            }
        }

        // Create new conversation with PENDING status
        String nowStr = LocalDateTime.now().format(DATE_FORMATTER);
        String convId = conversationRepository.generateNextId();

        Conversation newConv = new Conversation(convId, cleanRequestId, requester.getId(),
                donorUser.getId(), donor.getId(), Conversation.STATUS_PENDING, nowStr, nowStr, "", "", "");

        Conversation saved = conversationRepository.save(newConv);
        System.out.println("[ChatService] Created conversation " + saved.getId() + " requester=" + requester.getId()
                + " donorProfile=" + donor.getId() + " donorUser=" + donorUser.getId() + " status=" + saved.getStatus());

        // Notify donor of new chat request
        try {
            notificationService.notifyChatRequest(donorUser.getId(), requester.getFullName(), cleanRequestId, saved.getId());
            System.out.println("[ChatService] Dispatched chat request notification to recipient=" + donorUser.getId());
        } catch (Exception e) {
            System.err.println("[ChatService] Error notifying donor: " + e.getMessage());
        }

        return saved;
    }

    public List<Conversation> getConversationsForUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return List.of();
        }
        String cleanUserId = userId.trim();
        Optional<User> userOpt = userRepository.findById(cleanUserId);
        String linkedDonorId = userOpt.map(User::getLinkedDonorId).orElse("");

        List<Conversation> all = conversationRepository.findAll();
        List<Conversation> userConvs = new ArrayList<>();
        for (Conversation c : all) {
            boolean isPart = c.isParticipant(cleanUserId);
            if (!isPart && !linkedDonorId.isEmpty() && linkedDonorId.equalsIgnoreCase(c.getDonorProfileId())) {
                isPart = true;
                if (!cleanUserId.equalsIgnoreCase(c.getDonorUserId())) {
                    c.setDonorUserId(cleanUserId);
                    conversationRepository.update(c);
                    System.out.println("[ChatService] Re-bound conversation " + c.getId() + " donorUserId to " + cleanUserId + " via linkedDonorId " + linkedDonorId);
                }
            }
            if (isPart) {
                userConvs.add(c);
            }
        }
        userConvs.sort((c1, c2) -> c2.getLastActivityAt().compareTo(c1.getLastActivityAt()));
        System.out.println("[ChatService] getConversationsForUser user=" + cleanUserId + " linkedDonorId=" + linkedDonorId + " total=" + all.size() + " matched=" + userConvs.size());
        return Collections.unmodifiableList(userConvs);
    }

    public Optional<Conversation> getConversation(String conversationId, String userId) {
        if (conversationId == null || userId == null) {
            return Optional.empty();
        }
        Optional<Conversation> opt = conversationRepository.findById(conversationId.trim());
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Conversation c = opt.get();
        String cleanUserId = userId.trim();
        Optional<User> userOpt = userRepository.findById(cleanUserId);
        String linkedDonorId = userOpt.map(User::getLinkedDonorId).orElse("");

        if (!c.isParticipant(cleanUserId)) {
            if (!linkedDonorId.isEmpty() && linkedDonorId.equalsIgnoreCase(c.getDonorProfileId())) {
                c.setDonorUserId(cleanUserId);
                conversationRepository.update(c);
            } else {
                return Optional.empty(); // Strict authorization guard
            }
        }
        return opt;
    }

    public List<ChatMessage> getMessages(String conversationId, String userId) {
        if (getConversation(conversationId, userId).isEmpty()) {
            throw new SecurityException("User is not authorized to read messages in this conversation.");
        }
        return messageRepository.findByConversationId(conversationId.trim());
    }

    public ChatMessage sendMessage(String conversationId, User sender, String text) {
        if (sender == null || sender.getId() == null) {
            throw new SecurityException("Authentication is required to send messages.");
        }
        if (conversationId == null || conversationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Conversation ID is required.");
        }

        Conversation conv = getConversation(conversationId, sender.getId())
                .orElseThrow(() -> new SecurityException("User is not an authorized participant in this conversation."));

        if (conv.isPending()) {
            throw new IllegalStateException("Messages cannot be sent until the donor accepts the conversation request.");
        }
        if (conv.isDeclined()) {
            throw new IllegalStateException("This conversation request was declined. No messages can be sent.");
        }
        if (conv.isClosed()) {
            throw new IllegalStateException("This conversation has been closed. No further messages can be sent.");
        }

        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }

        String cleanText = text.trim();
        if (cleanText.length() > AppConfig.MAX_CHAT_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message exceeds maximum permitted length of "
                    + AppConfig.MAX_CHAT_MESSAGE_LENGTH + " characters.");
        }

        String nowStr = LocalDateTime.now().format(DATE_FORMATTER);
        String msgId = messageRepository.generateNextId();

        ChatMessage message = new ChatMessage(msgId, conv.getId(), sender.getId(), cleanText, nowStr, false, "");
        ChatMessage saved = messageRepository.save(message);

        // Update conversation last activity
        Conversation updatedConv = conv.withLastActivity(nowStr);
        conversationRepository.update(updatedConv);

        // Notify other participant
        String otherUserId = conv.getOtherParticipantUserId(sender.getId());
        try {
            notificationService.notifyNewMessage(otherUserId, sender.getFullName(), cleanText, conv.getId());
        } catch (Exception e) {
            System.err.println("[ChatService] Error notifying message recipient: " + e.getMessage());
        }

        return saved;
    }

    public boolean acceptConversation(String conversationId, User user) {
        if (user == null || user.getId() == null) {
            throw new SecurityException("Authentication required.");
        }

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found."));

        boolean isDesignatedDonor = conv.isDonor(user.getId());
        if (!isDesignatedDonor && user.getLinkedDonorId() != null && !user.getLinkedDonorId().trim().isEmpty()) {
            if (user.getLinkedDonorId().trim().equalsIgnoreCase(conv.getDonorProfileId())) {
                isDesignatedDonor = true;
                conv.setDonorUserId(user.getId());
                conversationRepository.update(conv);
            }
        }

        if (!isDesignatedDonor) {
            throw new SecurityException("Only the designated donor can accept this conversation request.");
        }

        if (!conv.isPending()) {
            return false;
        }

        String nowStr = LocalDateTime.now().format(DATE_FORMATTER);
        Conversation updated = conv.withStatus(Conversation.STATUS_ACTIVE, nowStr);
        boolean ok = conversationRepository.update(updated);

        if (ok) {
            try {
                notificationService.notifyChatAccepted(conv.getRequesterUserId(), user.getFullName(), conv.getId());
            } catch (Exception e) {
                System.err.println("[ChatService] Error notifying requester of acceptance: " + e.getMessage());
            }
        }
        return ok;
    }

    public boolean declineConversation(String conversationId, User user) {
        if (user == null || user.getId() == null) {
            throw new SecurityException("Authentication required.");
        }

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found."));

        boolean isDesignatedDonor = conv.isDonor(user.getId());
        if (!isDesignatedDonor && user.getLinkedDonorId() != null && !user.getLinkedDonorId().trim().isEmpty()) {
            if (user.getLinkedDonorId().trim().equalsIgnoreCase(conv.getDonorProfileId())) {
                isDesignatedDonor = true;
                conv.setDonorUserId(user.getId());
                conversationRepository.update(conv);
            }
        }

        if (!isDesignatedDonor) {
            throw new SecurityException("Only the designated donor can decline this conversation request.");
        }

        if (!conv.isPending()) {
            return false;
        }

        String nowStr = LocalDateTime.now().format(DATE_FORMATTER);
        Conversation updated = conv.withStatus(Conversation.STATUS_DECLINED, nowStr);
        boolean ok = conversationRepository.update(updated);

        if (ok) {
            try {
                notificationService.notifyChatDeclined(conv.getRequesterUserId(), user.getFullName(), conv.getId());
            } catch (Exception e) {
                System.err.println("[ChatService] Error notifying requester of decline: " + e.getMessage());
            }
        }
        return ok;
    }

    public boolean closeConversation(String conversationId, User user) {
        if (user == null || user.getId() == null) {
            throw new SecurityException("Authentication required.");
        }

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found."));

        boolean isPart = conv.isParticipant(user.getId());
        if (!isPart && user.getLinkedDonorId() != null && !user.getLinkedDonorId().trim().isEmpty()) {
            isPart = user.getLinkedDonorId().trim().equalsIgnoreCase(conv.getDonorProfileId());
        }

        if (!isPart) {
            throw new SecurityException("Only authorized conversation participants can close this chat.");
        }

        if (conv.isClosed()) {
            return true;
        }

        String nowStr = LocalDateTime.now().format(DATE_FORMATTER);
        Conversation updated = conv.withStatus(Conversation.STATUS_CLOSED, nowStr);
        return conversationRepository.update(updated);
    }

    public int markMessagesAsRead(String conversationId, String userId) {
        if (conversationId == null || userId == null) return 0;
        String nowStr = LocalDateTime.now().format(DATE_FORMATTER);
        return messageRepository.markMessagesAsRead(conversationId, userId, nowStr);
    }

    public int getUnreadMessageCount(String userId) {
        if (userId == null || userId.trim().isEmpty()) return 0;
        List<Conversation> convs = conversationRepository.findByUserId(userId.trim());
        return messageRepository.getUnreadCountForUser(userId.trim(), convs);
    }

    public int getUnreadMessageCountForConversation(String conversationId, String userId) {
        if (conversationId == null || userId == null) return 0;
        return messageRepository.getUnreadCountForConversation(conversationId, userId);
    }

    public Optional<User> getUserById(String userId) {
        return userRepository.findById(userId);
    }

    public Optional<Donor> getDonorById(String donorId) {
        return donorService.findDonorById(donorId);
    }

    public Optional<BloodRequest> getBloodRequestById(String requestId) {
        return bloodRequestService.getRequestById(requestId);
    }
}

package com.bloodbuddy.service;

import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Service managing in-app notifications, unread counts,
 * deduplication, and lifecycle alerts.
 */
public class NotificationService {

    private final NotificationRepository repository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public NotificationService() {
        this(NotificationRepository.getInstance());
    }

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public List<AppNotification> getNotifications(boolean isAdmin) {
        return repository.getNotificationsForUser(isAdmin);
    }

    public List<AppNotification> getNotifications(String userId, boolean isAdmin) {
        return repository.getNotificationsForUser(userId, isAdmin);
    }

    public int getUnreadCount(boolean isAdmin) {
        return repository.getUnreadCount(isAdmin);
    }

    public int getUnreadCount(String userId, boolean isAdmin) {
        return repository.getUnreadCount(userId, isAdmin);
    }

    public boolean markAsRead(String id) {
        return repository.markAsRead(id);
    }

    public int markAllAsRead(boolean isAdmin) {
        return repository.markAllAsRead(isAdmin);
    }

    public Optional<AppNotification> findById(String id) {
        return repository.findById(id);
    }

    public AppNotification saveNotification(AppNotification notification) {
        if (notification == null) return null;
        return repository.save(notification);
    }

    public boolean hasNotification(String category, String targetId) {
        return repository.hasNotification(category, targetId);
    }

    public AppNotification notifyRequestSubmitted(BloodRequest request) {
        if (request == null || request.getId() == null) {
            return null;
        }

        // Prevent duplicate notification on page refreshes
        if (repository.hasNotification(AppNotification.CAT_REQUEST_SUBMITTED, request.getId())) {
            return null;
        }

        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "New Request: " + request.getId() + " (" + request.getBloodGroup() + ")";
        String message = "Requisition submitted for " + request.getUnits() + " unit(s) of "
                + request.getBloodGroup() + " at " + request.getHospitalName() + ", " + request.getHospitalLocality() + ".";
        String linkUrl = "/matching?requestId=" + request.getId();

        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_REQUEST_SUBMITTED,
                title, message, request.getId(), false, false, linkUrl);
        return repository.save(notif);
    }

    public AppNotification notifyMatchesFound(BloodRequest request, int matchCount) {
        if (request == null || request.getId() == null) {
            return null;
        }

        // Prevent duplicate notification on page refreshes
        if (repository.hasNotification(AppNotification.CAT_MATCHES_FOUND, request.getId())) {
            return null;
        }

        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "Potential Matches: " + request.getId() + " (" + matchCount + " found)";
        String message = "Smart matching engine identified " + matchCount
                + " potential voluntary donor candidate(s) for " + request.getBloodGroup()
                + " requisition at " + request.getHospitalName() + ".";
        String linkUrl = "/matching?requestId=" + request.getId();

        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_MATCHES_FOUND,
                title, message, request.getId(), false, false, linkUrl);
        return repository.save(notif);
    }

    public AppNotification notifyNoMatches(BloodRequest request) {
        if (request == null || request.getId() == null) {
            return null;
        }

        // Prevent duplicate notification on page refreshes
        if (repository.hasNotification(AppNotification.CAT_NO_MATCHES, request.getId())) {
            return null;
        }

        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "No Matches Found: " + request.getId();
        String message = "No matching available donors currently in local records for "
                + request.getBloodGroup() + ". Advise contacting hospital or emergency blood bank directly.";
        String linkUrl = "/matching?requestId=" + request.getId();

        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_NO_MATCHES,
                title, message, request.getId(), false, false, linkUrl);
        return repository.save(notif);
    }

    public AppNotification notifyStatusChanged(BloodRequest request, String oldStatus, String newStatus) {
        if (request == null || request.getId() == null) {
            return null;
        }

        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "Status Updated: " + request.getId() + " → " + newStatus;
        String message = "Requisition " + request.getId() + " (" + request.getBloodGroup()
                + ") status transitioned from '" + oldStatus + "' to '" + newStatus + "'.";
        String linkUrl = "/matching?requestId=" + request.getId();

        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_STATUS_CHANGED,
                title, message, request.getId(), false, false, linkUrl);
        return repository.save(notif);
    }

    public AppNotification notifyUserRegistered(String fullName, String userId) {
        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "New Lifesaver Account Created";
        String message = "Welcome " + fullName + " to Blood Buddy! Unified account created successfully.";
        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_USER_ACCOUNT,
                title, message, userId != null ? userId : "", false, false, "/account");
        return repository.save(notif);
    }

    public AppNotification notifyCampRegistration(String title, String message, String category, String linkUrl) {
        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        AppNotification notif = new AppNotification(id, timestamp, category != null ? category : "CAMP",
                title, message, "", false, false, linkUrl != null ? linkUrl : "/camps");
        return repository.save(notif);
    }

    public AppNotification recordNotification(String title, String message, String category, String linkUrl) {
        return notifyCampRegistration(title, message, category, linkUrl);
    }

    public AppNotification notifyChatRequest(String donorUserId, String requesterName, String requestId, String convId) {
        if (donorUserId == null || convId == null) return null;
        if (repository.hasNotification(AppNotification.CAT_CHAT_REQUEST, convId)) {
            return null;
        }
        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "New Chat Request from " + requesterName;
        String reqSuffix = (requestId != null && !requestId.trim().isEmpty()) ? " regarding requisition " + requestId : "";
        String message = requesterName + " wants to communicate with you directly" + reqSuffix + ". Open conversation to respond.";
        String linkUrl = "/messages?id=" + convId;
        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_CHAT_REQUEST,
                title, message, convId, false, false, linkUrl, donorUserId);
        return repository.save(notif);
    }

    public AppNotification notifyChatAccepted(String requesterUserId, String donorName, String convId) {
        if (requesterUserId == null || convId == null) return null;
        if (repository.hasNotification(AppNotification.CAT_CHAT_ACCEPTED, convId)) {
            return null;
        }
        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "Chat Request Accepted";
        String message = donorName + " accepted your chat request! You can now exchange messages directly.";
        String linkUrl = "/messages?id=" + convId;
        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_CHAT_ACCEPTED,
                title, message, convId, false, false, linkUrl, requesterUserId);
        return repository.save(notif);
    }

    public AppNotification notifyChatDeclined(String requesterUserId, String donorName, String convId) {
        if (requesterUserId == null || convId == null) return null;
        if (repository.hasNotification(AppNotification.CAT_CHAT_DECLINED, convId)) {
            return null;
        }
        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "Chat Request Declined";
        String message = donorName + " is unable to accept your chat request at this time.";
        String linkUrl = "/messages?id=" + convId;
        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_CHAT_DECLINED,
                title, message, convId, false, false, linkUrl, requesterUserId);
        return repository.save(notif);
    }

    public AppNotification notifyNewMessage(String recipientUserId, String senderName, String snippet, String convId) {
        if (recipientUserId == null || convId == null) return null;
        String id = repository.generateNextId();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String title = "New Message from " + senderName;
        String displaySnippet = snippet != null ? snippet : "";
        if (displaySnippet.length() > 60) {
            displaySnippet = displaySnippet.substring(0, 57) + "...";
        }
        String message = "\"" + displaySnippet + "\"";
        String linkUrl = "/messages?id=" + convId;
        AppNotification notif = new AppNotification(id, timestamp, AppNotification.CAT_CHAT_MESSAGE,
                title, message, convId, false, false, linkUrl, recipientUserId);
        return repository.save(notif);
    }
}

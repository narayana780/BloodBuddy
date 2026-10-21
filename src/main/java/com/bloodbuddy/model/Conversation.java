package com.bloodbuddy.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Model representing a private, secure in-app conversation between a blood requester and a matched donor.
 * Persisted in data/conversations.json.
 */
public class Conversation implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_CLOSED = "CLOSED";

    private final String id;
    private final String requestId;
    private final String requesterUserId;
    private String donorUserId;
    private final String donorProfileId;
    private final String status;
    private final String createdAt;
    private final String lastActivityAt;
    private final String acceptedAt;
    private final String declinedAt;
    private final String closedAt;

    public Conversation(String id, String requestId, String requesterUserId, String donorUserId,
                        String donorProfileId, String status, String createdAt, String lastActivityAt,
                        String acceptedAt, String declinedAt, String closedAt) {
        this.id = id;
        this.requestId = requestId != null ? requestId.trim() : "";
        this.requesterUserId = requesterUserId != null ? requesterUserId.trim() : "";
        this.donorUserId = donorUserId != null ? donorUserId.trim() : "";
        this.donorProfileId = donorProfileId != null ? donorProfileId.trim() : "";
        this.status = status != null && !status.trim().isEmpty() ? status.trim().toUpperCase() : STATUS_PENDING;
        this.createdAt = createdAt != null ? createdAt : "";
        this.lastActivityAt = lastActivityAt != null && !lastActivityAt.trim().isEmpty() ? lastActivityAt : createdAt;
        this.acceptedAt = acceptedAt != null ? acceptedAt : "";
        this.declinedAt = declinedAt != null ? declinedAt : "";
        this.closedAt = closedAt != null ? closedAt : "";
    }

    public String getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean hasLinkedRequest() {
        return requestId != null && !requestId.trim().isEmpty();
    }

    public String getRequesterUserId() {
        return requesterUserId;
    }

    public String getDonorUserId() {
        return donorUserId;
    }

    public void setDonorUserId(String donorUserId) {
        this.donorUserId = donorUserId;
    }

    public String getDonorProfileId() {
        return donorProfileId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(status);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equalsIgnoreCase(status);
    }

    public boolean isDeclined() {
        return STATUS_DECLINED.equalsIgnoreCase(status);
    }

    public boolean isClosed() {
        return STATUS_CLOSED.equalsIgnoreCase(status);
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getLastActivityAt() {
        return lastActivityAt;
    }

    public String getAcceptedAt() {
        return acceptedAt;
    }

    public String getDeclinedAt() {
        return declinedAt;
    }

    public String getClosedAt() {
        return closedAt;
    }

    public boolean isParticipant(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        String clean = userId.trim();
        return clean.equalsIgnoreCase(requesterUserId) || clean.equalsIgnoreCase(donorUserId);
    }

    public boolean isParticipant(String userId, String linkedDonorId) {
        if (isParticipant(userId)) {
            return true;
        }
        if (linkedDonorId != null && !linkedDonorId.trim().isEmpty() && donorProfileId != null) {
            return linkedDonorId.trim().equalsIgnoreCase(donorProfileId);
        }
        return false;
    }

    public boolean isDonor(String userId) {
        return userId != null && userId.trim().equalsIgnoreCase(donorUserId);
    }

    public boolean isDonor(String userId, String linkedDonorId) {
        if (isDonor(userId)) {
            return true;
        }
        if (linkedDonorId != null && !linkedDonorId.trim().isEmpty() && donorProfileId != null) {
            return linkedDonorId.trim().equalsIgnoreCase(donorProfileId);
        }
        return false;
    }

    public boolean isRequester(String userId) {
        return userId != null && userId.trim().equalsIgnoreCase(requesterUserId);
    }

    public String getOtherParticipantUserId(String currentUserId) {
        if (currentUserId == null) return "";
        if (currentUserId.equalsIgnoreCase(requesterUserId)) {
            return donorUserId;
        }
        return requesterUserId;
    }

    public Conversation withStatus(String newStatus, String timestamp) {
        String newAccepted = this.acceptedAt;
        String newDeclined = this.declinedAt;
        String newClosed = this.closedAt;

        if (STATUS_ACTIVE.equalsIgnoreCase(newStatus)) {
            newAccepted = timestamp;
        } else if (STATUS_DECLINED.equalsIgnoreCase(newStatus)) {
            newDeclined = timestamp;
        } else if (STATUS_CLOSED.equalsIgnoreCase(newStatus)) {
            newClosed = timestamp;
        }

        return new Conversation(this.id, this.requestId, this.requesterUserId, this.donorUserId,
                this.donorProfileId, newStatus, this.createdAt, timestamp,
                newAccepted, newDeclined, newClosed);
    }

    public Conversation withLastActivity(String timestamp) {
        return new Conversation(this.id, this.requestId, this.requesterUserId, this.donorUserId,
                this.donorProfileId, this.status, this.createdAt, timestamp,
                this.acceptedAt, this.declinedAt, this.closedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Conversation that = (Conversation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "id='" + id + '\'' +
                ", requestId='" + requestId + '\'' +
                ", requesterUserId='" + requesterUserId + '\'' +
                ", donorUserId='" + donorUserId + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

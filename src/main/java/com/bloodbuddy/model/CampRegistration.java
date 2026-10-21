package com.bloodbuddy.model;

/**
 * Model representing an authenticated user's registration for a Blood Donation Camp.
 */
public class CampRegistration {

    private final String id;
    private final String campId;
    private final String userId;
    private final String userName;
    private final String userEmail;
    private final String userPhone;
    private final String preferredSlot; // e.g. "Morning (09:00 AM - 12:00 PM)", "Afternoon (12:00 PM - 03:00 PM)"
    private final String bloodGroup; // user's blood group if supplied
    private final String registeredAt;
    private final String status; // "Confirmed", "Cancelled"

    public CampRegistration(String id, String campId, String userId, String userName,
                            String userEmail, String userPhone, String preferredSlot,
                            String bloodGroup, String registeredAt, String status) {
        this.id = id != null ? id.trim() : "";
        this.campId = campId != null ? campId.trim() : "";
        this.userId = userId != null ? userId.trim() : "";
        this.userName = userName != null ? userName.trim() : "";
        this.userEmail = userEmail != null ? userEmail.trim() : "";
        this.userPhone = userPhone != null ? userPhone.trim() : "";
        this.preferredSlot = preferredSlot != null && !preferredSlot.trim().isEmpty() ? preferredSlot.trim() : "Morning (09:00 AM - 12:00 PM)";
        this.bloodGroup = bloodGroup != null ? bloodGroup.trim() : "";
        this.registeredAt = registeredAt != null ? registeredAt.trim() : "";
        this.status = status != null && !status.trim().isEmpty() ? status.trim() : "Confirmed";
    }

    public String getId() {
        return id;
    }

    public String getCampId() {
        return campId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public String getPreferredSlot() {
        return preferredSlot;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    public String getStatus() {
        return status;
    }

    public boolean isConfirmed() {
        return "Confirmed".equalsIgnoreCase(status);
    }
}

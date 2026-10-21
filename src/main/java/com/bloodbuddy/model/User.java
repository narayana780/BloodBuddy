package com.bloodbuddy.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * User represents an authenticated account on Blood Buddy.
 * Used uniformly for both blood donors and blood requesters.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String PURPOSE_DONATE = "donate";
    public static final String PURPOSE_REQUEST = "request";
    public static final String PURPOSE_BOTH = "both";

    private String id;
    private String fullName;
    private String email;
    private String username;
    private String phone;
    private String passwordHash;
    private String salt;
    private String role;
    private String registeredAt;
    private String linkedDonorId;
    private String purpose;

    public User() {
        this.role = ROLE_USER;
        this.purpose = PURPOSE_BOTH;
    }

    public User(String id, String fullName, String email, String username, String phone,
                String passwordHash, String salt, String role, String registeredAt, String linkedDonorId) {
        this(id, fullName, email, username, phone, passwordHash, salt, role, registeredAt, linkedDonorId, PURPOSE_BOTH);
    }

    public User(String id, String fullName, String email, String username, String phone,
                String passwordHash, String salt, String role, String registeredAt, String linkedDonorId, String purpose) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.username = username;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role != null && !role.trim().isEmpty() ? role.trim().toUpperCase() : ROLE_USER;
        this.registeredAt = registeredAt;
        this.linkedDonorId = linkedDonorId;
        this.purpose = (purpose != null && !purpose.trim().isEmpty()) ? purpose.trim().toLowerCase() : PURPOSE_BOTH;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName != null ? fullName : "";
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email != null ? email : "";
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username != null ? username : "";
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash != null ? passwordHash : "";
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt != null ? salt : "";
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getRole() {
        return role != null ? role : ROLE_USER;
    }

    public void setRole(String role) {
        this.role = role != null ? role.trim().toUpperCase() : ROLE_USER;
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(this.role);
    }

    public String getRegisteredAt() {
        return registeredAt != null ? registeredAt : "";
    }

    public void setRegisteredAt(String registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getLinkedDonorId() {
        return linkedDonorId != null ? linkedDonorId : "";
    }

    public void setLinkedDonorId(String linkedDonorId) {
        this.linkedDonorId = linkedDonorId;
    }

    public boolean isDonor() {
        return linkedDonorId != null && !linkedDonorId.trim().isEmpty();
    }

    public String getPurpose() {
        return (purpose != null && !purpose.trim().isEmpty()) ? purpose : PURPOSE_BOTH;
    }

    public void setPurpose(String purpose) {
        this.purpose = (purpose != null && !purpose.trim().isEmpty()) ? purpose.trim().toLowerCase() : PURPOSE_BOTH;
    }

    public boolean wantsToDonate() {
        String p = getPurpose();
        return PURPOSE_DONATE.equalsIgnoreCase(p) || PURPOSE_BOTH.equalsIgnoreCase(p);
    }

    public boolean wantsToRequest() {
        String p = getPurpose();
        return PURPOSE_REQUEST.equalsIgnoreCase(p) || PURPOSE_BOTH.equalsIgnoreCase(p);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", linkedDonorId='" + linkedDonorId + '\'' +
                '}';
    }
}

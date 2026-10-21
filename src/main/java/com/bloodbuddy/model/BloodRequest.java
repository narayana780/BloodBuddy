package com.bloodbuddy.model;

/**
 * Blood Request Model for Stage 5.
 * Represents emergency and scheduled patient blood unit requisitions.
 */
public class BloodRequest {
    private final String id;
    private final String patientName;
    private final String bloodGroup;
    private final int units;
    private final String hospitalName;
    private final String hospitalLocality;
    private final String contactPerson;
    private final String contactPhone; // Kept private, never exposed on public listing
    private final String requiredBy;
    private final String urgency; // Normal, Urgent, Critical
    private final String notes;
    private final String status; // Pending, In Progress, Fulfilled
    private final String createdAt;
    private final boolean sample;

    public BloodRequest(String id, String patientName, String bloodGroup, int units,
                        String hospitalName, String hospitalLocality, String contactPerson,
                        String contactPhone, String requiredBy, String urgency, String notes,
                        String status, String createdAt, boolean sample) {
        this.id = id;
        this.patientName = patientName;
        this.bloodGroup = bloodGroup;
        this.units = units;
        this.hospitalName = hospitalName;
        this.hospitalLocality = hospitalLocality;
        this.contactPerson = contactPerson;
        this.contactPhone = contactPhone != null ? contactPhone.trim() : "";
        this.requiredBy = requiredBy;
        this.urgency = urgency;
        this.notes = notes != null ? notes.trim() : "";
        this.status = (status != null && !status.trim().isEmpty()) ? status.trim() : "Pending";
        this.createdAt = createdAt;
        this.sample = sample;
    }

    public String getId() {
        return id;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public int getUnits() {
        return units;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getHospitalLocality() {
        return hospitalLocality;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    /**
     * Confidential contact phone number. Strictly not rendered on public listing cards.
     */
    public String getContactPhone() {
        return contactPhone;
    }

    public String getRequiredBy() {
        return requiredBy;
    }

    public String getUrgency() {
        return urgency;
    }

    public String getNotes() {
        return notes;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isSample() {
        return sample;
    }

    /**
     * Returns an updated BloodRequest with a new status (e.g. Pending, In Progress, Fulfilled).
     */
    public BloodRequest withStatus(String newStatus) {
        return new BloodRequest(this.id, this.patientName, this.bloodGroup, this.units,
                this.hospitalName, this.hospitalLocality, this.contactPerson, this.contactPhone,
                this.requiredBy, this.urgency, this.notes, newStatus, this.createdAt, this.sample);
    }
}

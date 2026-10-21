package com.bloodbuddy.model;

/**
 * Fictional Donor Model for prototype demonstration and voluntary registration.
 * Represents both demonstration seed records and user-registered lifesaver profiles.
 */
public class Donor {
    private final String id;
    private final String name;
    private final String bloodGroup;
    private final String locality;
    private final String lastDonated;
    private final boolean eligible;
    private final String badge;
    private final String availabilityStatus;
    private final int age;
    private final String city;
    private final String contactInfo;
    private final boolean registered;
    private final String registeredAt;

    /**
     * Backward-compatible 8-argument constructor for seed demonstration records.
     */
    public Donor(String id, String name, String bloodGroup, String locality, 
                 String lastDonated, boolean eligible, String badge, String availabilityStatus) {
        this(id, name, bloodGroup, locality, lastDonated, eligible, badge, availabilityStatus,
             28, "Rajahmundry", "", false, "Seed Record");
    }

    /**
     * Full constructor supporting Stage 4 registered donor profiles.
     */
    public Donor(String id, String name, String bloodGroup, String locality, 
                 String lastDonated, boolean eligible, String badge, String availabilityStatus,
                 int age, String city, String contactInfo, boolean registered, String registeredAt) {
        this.id = id;
        this.name = name;
        this.bloodGroup = bloodGroup;
        this.locality = locality;
        this.lastDonated = lastDonated;
        this.eligible = eligible;
        this.badge = badge;
        this.availabilityStatus = availabilityStatus;
        this.age = age;
        this.city = (city != null && !city.trim().isEmpty()) ? city.trim() : "Rajahmundry";
        this.contactInfo = (contactInfo != null) ? contactInfo.trim() : "";
        this.registered = registered;
        this.registeredAt = (registeredAt != null && !registeredAt.trim().isEmpty()) ? registeredAt.trim() : "N/A";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getLocality() {
        return locality;
    }

    public String getLastDonated() {
        return lastDonated;
    }

    public boolean isEligible() {
        return eligible;
    }

    public String getBadge() {
        return badge;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }

    public int getAge() {
        return age;
    }

    public String getCity() {
        return city;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public boolean isRegistered() {
        return registered;
    }

    public String getRegisteredAt() {
        return registeredAt;
    }

    /**
     * Returns an updated Donor instance with new availability preferences.
     */
    public Donor withAvailability(boolean newEligible, String newStatus) {
        return new Donor(this.id, this.name, this.bloodGroup, this.locality,
                this.lastDonated, newEligible, this.badge, newStatus,
                this.age, this.city, this.contactInfo, this.registered, this.registeredAt);
    }
}

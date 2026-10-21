package com.bloodbuddy.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Model representing a published Blood Donation Camp.
 */
public class Camp {

    private final String id;
    private final String name;
    private final String organizer;
    private final String organizerContact;
    private final String date; // YYYY-MM-DD
    private final String startTime; // e.g. "09:00 AM"
    private final String endTime; // e.g. "03:00 PM"
    private final String venue;
    private final String address;
    private final String city;
    private final String locality;
    private final String status; // Scheduled status: "Upcoming", "Ongoing", "Completed", "Cancelled"
    private final String eligibilityInfo;
    private final int targetUnits;
    private final int maxCapacity;
    private final boolean registrationOpen;
    private final String notes;
    private final String createdAt;

    public Camp(String id, String name, String organizer, String organizerContact,
                String date, String startTime, String endTime, String venue,
                String address, String city, String locality, String status,
                String eligibilityInfo, int targetUnits, int maxCapacity,
                boolean registrationOpen, String notes, String createdAt) {
        this.id = id != null ? id.trim() : "";
        this.name = name != null ? name.trim() : "";
        this.organizer = organizer != null ? organizer.trim() : "";
        this.organizerContact = organizerContact != null ? organizerContact.trim() : "";
        this.date = date != null ? date.trim() : "";
        this.startTime = startTime != null ? startTime.trim() : "09:00 AM";
        this.endTime = endTime != null ? endTime.trim() : "03:00 PM";
        this.venue = venue != null ? venue.trim() : "";
        this.address = address != null ? address.trim() : "";
        this.city = city != null && !city.trim().isEmpty() ? city.trim() : "Rajahmundry";
        this.locality = locality != null ? locality.trim() : "";
        this.status = status != null && !status.trim().isEmpty() ? status.trim() : "Upcoming";
        this.eligibilityInfo = eligibilityInfo != null ? eligibilityInfo.trim() : "";
        this.targetUnits = targetUnits > 0 ? targetUnits : 50;
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 100;
        this.registrationOpen = registrationOpen;
        this.notes = notes != null ? notes.trim() : "";
        this.createdAt = createdAt != null ? createdAt.trim() : "";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrganizer() {
        return organizer;
    }

    public String getOrganizerContact() {
        return organizerContact;
    }

    public String getDate() {
        return date;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getVenue() {
        return venue;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getLocality() {
        return locality;
    }

    public String getStatus() {
        return status;
    }

    public String getEligibilityInfo() {
        return eligibilityInfo;
    }

    public int getTargetUnits() {
        return targetUnits;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public boolean isRegistrationOpen() {
        if ("Cancelled".equalsIgnoreCase(status)) {
            return false;
        }
        String computed = getComputedStatus();
        if ("Completed".equalsIgnoreCase(computed) || "Cancelled".equalsIgnoreCase(computed)) {
            return false;
        }
        return registrationOpen;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    /**
     * Calculates the real-time status of the camp based on scheduled date and time.
     */
    public String getComputedStatus() {
        if ("Cancelled".equalsIgnoreCase(status)) {
            return "Cancelled";
        }
        if (date.isEmpty()) {
            return status;
        }

        try {
            LocalDate campDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate today = LocalDate.now();

            if (today.isBefore(campDate)) {
                return "Upcoming";
            } else if (today.isAfter(campDate)) {
                return "Completed";
            } else {
                // Same day: check end time if available
                return "Ongoing";
            }
        } catch (DateTimeParseException e) {
            return status;
        }
    }

    /**
     * Human-friendly formatted date (e.g. 24 Oct 2026).
     */
    public String getFormattedDate() {
        if (date == null || date.isEmpty()) {
            return "Date to be announced";
        }
        try {
            LocalDate d = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            return d.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (DateTimeParseException e) {
            return date;
        }
    }
}

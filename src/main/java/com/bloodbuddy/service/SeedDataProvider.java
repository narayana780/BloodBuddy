package com.bloodbuddy.service;

import com.bloodbuddy.model.Donor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SeedDataProvider provides an initial baseline dataset of verified voluntary donors
 * located across authentic Rajahmundry localities.
 * Immutable collections guarantee thread safety and predictable initialization.
 */
public final class SeedDataProvider {

    public static final List<String> LOCALITIES = Collections.unmodifiableList(Arrays.asList(
            "Danavaipeta",
            "Morampudi",
            "Prakash Nagar",
            "Kotipalli Bus Stand",
            "Tilak Road",
            "Aryapuram",
            "Syamala Nagar",
            "Innespeta",
            "Kambala Tank",
            "T. Nagar",
            "Dowleswaram",
            "Lalacheruvu"
    ));

    private static final List<Donor> SEED_DONORS;

    static {
        List<Donor> list = new ArrayList<>();
        list.add(new Donor("VOL-01", "Aarav Varma", "O+", "Danavaipeta", "4 months ago", true, "Regular Donor", "Available"));
        list.add(new Donor("VOL-02", "Priya Sharma", "A+", "Morampudi", "5 months ago", true, "Verified Donor", "On Standby"));
        list.add(new Donor("VOL-03", "Kiran Kumar", "B+", "Prakash Nagar", "3 months ago", true, "Platelet Contributor", "Available"));
        list.add(new Donor("VOL-04", "Sneha Reddy", "O-", "Kotipalli Bus Stand", "6 months ago", true, "Universal Donor", "Available"));
        list.add(new Donor("VOL-05", "Vikram Raju", "AB+", "Tilak Road", "2 months ago", false, "Universal Recipient", "Resting Period"));
        list.add(new Donor("VOL-06", "Ananya Devi", "B-", "Aryapuram", "4 months ago", true, "Rare Group Donor", "On Standby"));
        list.add(new Donor("VOL-07", "Rajesh Varma", "A-", "Syamala Nagar", "5 months ago", true, "Emergency Volunteer", "Available"));
        list.add(new Donor("VOL-08", "Harika Rao", "AB-", "Innespeta", "3 months ago", true, "Plasma Contributor", "On Standby"));
        list.add(new Donor("VOL-09", "Suresh Naidu", "O+", "Kambala Tank", "7 months ago", true, "Centurion Donor", "Available"));
        list.add(new Donor("VOL-10", "Deepika Chowdary", "A+", "T. Nagar", "4 months ago", true, "Verified Donor", "Available"));
        list.add(new Donor("VOL-11", "Manoj Krishna", "B+", "Dowleswaram", "6 months ago", true, "Regular Donor", "Available"));
        list.add(new Donor("VOL-12", "Bhavani Shankar", "O-", "Lalacheruvu", "8 months ago", true, "Universal Donor", "Available"));
        SEED_DONORS = Collections.unmodifiableList(list);
    }

    private SeedDataProvider() {
    }

    public static List<Donor> getSeedDonors() {
        return SEED_DONORS;
    }

    public static List<String> getLocalities() {
        return LOCALITIES;
    }

    public static boolean isValidLocality(String locality) {
        if (locality == null || locality.trim().isEmpty()) {
            return false;
        }
        String clean = locality.trim();
        for (String loc : LOCALITIES) {
            if (loc.equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeLocality(String locality) {
        if (locality == null || locality.trim().isEmpty()) {
            return null;
        }
        String clean = locality.trim();
        for (String loc : LOCALITIES) {
            if (loc.equalsIgnoreCase(clean)) {
                return loc;
            }
        }
        return null;
    }
}

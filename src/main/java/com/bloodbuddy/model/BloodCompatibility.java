package com.bloodbuddy.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side Blood Group validation and compatibility matrix logic.
 */
public final class BloodCompatibility {

    public static final List<String> ALL_GROUPS = Collections.unmodifiableList(
            Arrays.asList("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
    );

    private static final Map<String, List<String>> CAN_GIVE_TO = new HashMap<>();
    private static final Map<String, List<String>> CAN_RECEIVE_FROM = new HashMap<>();
    private static final Map<String, String> CLINICAL_SUMMARIES = new HashMap<>();

    static {
        // Can Donate To
        CAN_GIVE_TO.put("O-", Arrays.asList("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+"));
        CAN_GIVE_TO.put("O+", Arrays.asList("O+", "A+", "B+", "AB+"));
        CAN_GIVE_TO.put("A-", Arrays.asList("A-", "A+", "AB-", "AB+"));
        CAN_GIVE_TO.put("A+", Arrays.asList("A+", "AB+"));
        CAN_GIVE_TO.put("B-", Arrays.asList("B-", "B+", "AB-", "AB+"));
        CAN_GIVE_TO.put("B+", Arrays.asList("B+", "AB+"));
        CAN_GIVE_TO.put("AB-", Arrays.asList("AB-", "AB+"));
        CAN_GIVE_TO.put("AB+", Arrays.asList("AB+"));

        // Can Receive From
        CAN_RECEIVE_FROM.put("O-", Collections.singletonList("O-"));
        CAN_RECEIVE_FROM.put("O+", Arrays.asList("O+", "O-"));
        CAN_RECEIVE_FROM.put("A-", Arrays.asList("A-", "O-"));
        CAN_RECEIVE_FROM.put("A+", Arrays.asList("A+", "A-", "O+", "O-"));
        CAN_RECEIVE_FROM.put("B-", Arrays.asList("B-", "O-"));
        CAN_RECEIVE_FROM.put("B+", Arrays.asList("B+", "B-", "O+", "O-"));
        CAN_RECEIVE_FROM.put("AB-", Arrays.asList("AB-", "A-", "B-", "O-"));
        CAN_RECEIVE_FROM.put("AB+", Arrays.asList("AB+", "AB-", "A+", "A-", "B+", "B-", "O+", "O-"));

        // Clinical Summaries
        CLINICAL_SUMMARIES.put("O-", "Universal Red Cell Donor: Can be transfused to any patient in life-threatening trauma and emergency operations.");
        CLINICAL_SUMMARIES.put("O+", "Most Common Blood Group: Heavily demanded for daily transfusions, pediatric support, and surgeries.");
        CLINICAL_SUMMARIES.put("A-", "Vital Platelet & Cell Source: Compatible with A and AB recipients, critical for cancer and surgical therapies.");
        CLINICAL_SUMMARIES.put("A+", "High Demand Group: Over 32% of regional patients benefit directly from A+ donations.");
        CLINICAL_SUMMARIES.put("B-", "Rare & Essential: Only ~2% of donors share this group; indispensable for specialized patient care.");
        CLINICAL_SUMMARIES.put("B+", "Widely Needed: Freely transfusable across B+ and AB+ recipients in regional healthcare centers.");
        CLINICAL_SUMMARIES.put("AB-", "Rarest Blood Type: Exceptional universal plasma component donor for critical ICU transfusions.");
        CLINICAL_SUMMARIES.put("AB+", "Universal Red Cell Recipient: Can receive red blood cells from any blood group during medical emergencies.");
    }

    private BloodCompatibility() {
    }

    public static String normalizeGroup(String group) {
        if (group == null) {
            return null;
        }
        String clean = group.trim().toUpperCase()
                .replace("_POS", "+")
                .replace("_NEG", "-")
                .replace(" POS", "+")
                .replace(" NEG", "-");
        return ALL_GROUPS.contains(clean) ? clean : null;
    }

    public static boolean isValidGroup(String group) {
        return normalizeGroup(group) != null;
    }

    public static List<String> getCanGiveTo(String group) {
        String clean = normalizeGroup(group);
        return clean != null ? CAN_GIVE_TO.getOrDefault(clean, Collections.emptyList()) : Collections.emptyList();
    }

    public static List<String> getCanReceiveFrom(String group) {
        String clean = normalizeGroup(group);
        return clean != null ? CAN_RECEIVE_FROM.getOrDefault(clean, Collections.emptyList()) : Collections.emptyList();
    }

    public static String getClinicalSummary(String group) {
        String clean = normalizeGroup(group);
        return clean != null ? CLINICAL_SUMMARIES.getOrDefault(clean, "") : "";
    }
}

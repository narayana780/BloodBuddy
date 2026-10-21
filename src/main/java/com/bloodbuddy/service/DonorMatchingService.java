package com.bloodbuddy.service;

import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.DonorMatch;
import com.bloodbuddy.model.DonorMatch.LocationMatch;
import com.bloodbuddy.model.DonorMatch.MatchCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Smart Donor Matching Engine for Blood Buddy (Stage 7).
 *
 * Implements:
 * 1. Explicit ABO/Rh red-blood-cell compatibility matrix.
 * 2. Separation of exact blood-group matches from medically compatible alternatives.
 * 3. Transparent ranking based on group compatibility, availability, and locality.
 * 4. Genuine Rajahmundry locality adjacency mapping without inventing distances or GPS.
 * 5. Strict exclusion of unavailable donors and invalid/missing blood groups.
 * 6. Preliminary match labeling requiring confirmation by qualified hospital staff.
 */
public class DonorMatchingService {

    private final DonorService donorService;

    // Authentic Rajahmundry locality adjacency graph (topological adjacency only; zero fake distances)
    private static final Map<String, Set<String>> ADJACENT_LOCALITIES = new HashMap<>();

    static {
        ADJACENT_LOCALITIES.put("Danavaipeta", new HashSet<>(Arrays.asList("Tilak Road", "Kambala Tank", "Prakash Nagar", "T. Nagar")));
        ADJACENT_LOCALITIES.put("Prakash Nagar", new HashSet<>(Arrays.asList("Danavaipeta", "Morampudi", "Lalacheruvu", "Syamala Nagar")));
        ADJACENT_LOCALITIES.put("Morampudi", new HashSet<>(Arrays.asList("Prakash Nagar", "Lalacheruvu")));
        ADJACENT_LOCALITIES.put("Tilak Road", new HashSet<>(Arrays.asList("Danavaipeta", "Aryapuram", "Kotipalli Bus Stand")));
        ADJACENT_LOCALITIES.put("Aryapuram", new HashSet<>(Arrays.asList("Innespeta", "Kotipalli Bus Stand", "Tilak Road")));
        ADJACENT_LOCALITIES.put("Innespeta", new HashSet<>(Arrays.asList("Aryapuram", "Kotipalli Bus Stand")));
        ADJACENT_LOCALITIES.put("Kotipalli Bus Stand", new HashSet<>(Arrays.asList("Innespeta", "Aryapuram", "Tilak Road", "Dowleswaram")));
        ADJACENT_LOCALITIES.put("Dowleswaram", new HashSet<>(Arrays.asList("Kotipalli Bus Stand")));
        ADJACENT_LOCALITIES.put("Lalacheruvu", new HashSet<>(Arrays.asList("Morampudi", "Prakash Nagar")));
        ADJACENT_LOCALITIES.put("T. Nagar", new HashSet<>(Arrays.asList("Danavaipeta", "Kambala Tank")));
        ADJACENT_LOCALITIES.put("Kambala Tank", new HashSet<>(Arrays.asList("Danavaipeta", "T. Nagar", "Syamala Nagar")));
        ADJACENT_LOCALITIES.put("Syamala Nagar", new HashSet<>(Arrays.asList("Kambala Tank", "Prakash Nagar")));
    }

    public DonorMatchingService() {
        this(new DonorService());
    }

    public DonorMatchingService(DonorService donorService) {
        this.donorService = donorService;
    }

    /**
     * Finds and ranks potential donor matches for a blood request across all available donors.
     */
    public List<DonorMatch> findMatches(BloodRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }
        return matchDonors(request, donorService.getAllDemonstrationDonors());
    }

    /**
     * Core matching engine: Evaluates a candidate donor pool against a blood request.
     */
    public List<DonorMatch> matchDonors(BloodRequest request, List<Donor> donorPool) {
        if (request == null || donorPool == null || donorPool.isEmpty()) {
            return Collections.emptyList();
        }

        String reqGroup = BloodCompatibility.normalizeGroup(request.getBloodGroup());
        if (reqGroup == null) {
            // Missing or invalid requested blood group
            return Collections.emptyList();
        }

        List<String> compatibleGroups = BloodCompatibility.getCanReceiveFrom(reqGroup);
        String hospitalLocalityClean = extractLocality(request.getHospitalLocality());

        List<DonorMatch> matches = new ArrayList<>();

        for (Donor donor : donorPool) {
            if (donor == null) {
                continue;
            }

            // 1. Exclude records with missing or invalid blood groups
            String donorGroup = BloodCompatibility.normalizeGroup(donor.getBloodGroup());
            if (donorGroup == null) {
                continue;
            }

            // 2. Availability & Eligibility Filtering:
            // Exclude donors explicitly marked unavailable or ineligible
            if (!isDonorAvailable(donor)) {
                continue;
            }

            // 3. Blood Compatibility Evaluation:
            // Check if donor blood group can be received by the recipient
            if (!compatibleGroups.contains(donorGroup)) {
                continue;
            }

            // Determine Exact Match vs Compatible Alternative
            boolean isExact = donorGroup.equalsIgnoreCase(reqGroup);
            MatchCategory matchCategory = isExact
                    ? MatchCategory.EXACT_MATCH
                    : MatchCategory.COMPATIBLE_ALTERNATIVE;

            // 4. Locality Relevance Evaluation
            String donorLocalityClean = extractLocality(donor.getLocality());
            LocationMatch locationMatch = evaluateLocation(hospitalLocalityClean, donorLocalityClean);

            // 5. Transparent Ranking Score Calculation
            int rankScore = calculateRankScore(matchCategory, locationMatch);

            // 6. Generate Human-Readable Explanations
            List<String> explanations = buildExplanations(reqGroup, donorGroup, matchCategory,
                    hospitalLocalityClean, donorLocalityClean, locationMatch);

            DonorMatch match = new DonorMatch(donor, request, matchCategory, locationMatch, rankScore, explanations);
            matches.add(match);
        }

        // Sort by rank score descending, then tie-break deterministically
        Collections.sort(matches);

        return Collections.unmodifiableList(matches);
    }

    /**
     * Checks if a donor is eligible and explicitly recorded as available.
     * Excludes donors on standby, resting period, or ineligible.
     */
    public static boolean isDonorAvailable(Donor donor) {
        if (donor == null || !donor.isEligible()) {
            return false;
        }

        String status = donor.getAvailabilityStatus();
        if (status == null || status.trim().isEmpty()) {
            return false;
        }

        String norm = status.trim().toLowerCase();
        // Explicitly exclude unavailable states
        if (norm.contains("unavailable") || norm.contains("standby") || norm.contains("resting")) {
            return false;
        }

        // Must explicitly indicate availability
        return norm.contains("available");
    }

    /**
     * Transparent scoring formula:
     * - Blood Group Compatibility: Exact Match = 1000 pts, Compatible Alternative = 500 pts
     * - Locality Relevance: Same Locality = +300 pts, Nearby Locality = +150 pts, Other Locality = +50 pts, Unknown = +0 pts
     * - Availability: Verified Available = +100 pts
     */
    private int calculateRankScore(MatchCategory category, LocationMatch location) {
        int score = 0;

        // 1. Compatibility Tier (Primary)
        if (category == MatchCategory.EXACT_MATCH) {
            score += 1000;
        } else {
            score += 500;
        }

        // 2. Locality Relevance (Secondary)
        switch (location) {
            case SAME_LOCALITY:
                score += 300;
                break;
            case NEARBY_LOCALITY:
                score += 150;
                break;
            case OTHER_LOCALITY:
                score += 50;
                break;
            case UNKNOWN_LOCALITY:
            default:
                score += 0; // Neutral; zero invented distances
                break;
        }

        // 3. Availability Confirmation
        score += 100;

        return score;
    }

    /**
     * Determines locality relationship without inventing distances.
     */
    public static LocationMatch evaluateLocation(String hospitalLocality, String donorLocality) {
        if (hospitalLocality == null || hospitalLocality.isEmpty()
                || donorLocality == null || donorLocality.isEmpty()) {
            return LocationMatch.UNKNOWN_LOCALITY;
        }

        // Exact locality match
        if (hospitalLocality.equalsIgnoreCase(donorLocality)) {
            return LocationMatch.SAME_LOCALITY;
        }

        // Check topological adjacency map
        String normHospital = SeedDataProvider.normalizeLocality(hospitalLocality);
        String normDonor = SeedDataProvider.normalizeLocality(donorLocality);

        if (normHospital != null && normDonor != null) {
            Set<String> neighbors = ADJACENT_LOCALITIES.get(normHospital);
            if (neighbors != null && neighbors.contains(normDonor)) {
                return LocationMatch.NEARBY_LOCALITY;
            }
            return LocationMatch.OTHER_LOCALITY;
        }

        return LocationMatch.UNKNOWN_LOCALITY;
    }

    /**
     * Normalizes a locality input string (e.g. "Danavaipeta, Rajahmundry" -> "Danavaipeta").
     */
    public static String extractLocality(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String clean = raw.trim();
        if (clean.contains(",")) {
            clean = clean.split(",")[0].trim();
        }
        String norm = SeedDataProvider.normalizeLocality(clean);
        return norm != null ? norm : clean;
    }

    private List<String> buildExplanations(String reqGroup, String donorGroup, MatchCategory category,
                                          String hospitalLocality, String donorLocality, LocationMatch location) {
        List<String> list = new ArrayList<>();

        // 1. Compatibility Explanation
        if (category == MatchCategory.EXACT_MATCH) {
            list.add("Exact blood-group match (Donor " + donorGroup + " matches requested " + reqGroup + ").");
        } else {
            list.add("Potentially compatible blood group (Donor " + donorGroup + " red cells can be transfused to " + reqGroup + " recipients per ABO/Rh compatibility).");
        }

        // 2. Locality Explanation
        switch (location) {
            case SAME_LOCALITY:
                list.add("Same locality as the hospital (" + donorLocality + ").");
                break;
            case NEARBY_LOCALITY:
                list.add("Nearby locality in Rajahmundry (" + donorLocality + " adjacent to hospital locality " + hospitalLocality + ").");
                break;
            case OTHER_LOCALITY:
                list.add("Regional Rajahmundry locality (" + donorLocality + ").");
                break;
            case UNKNOWN_LOCALITY:
            default:
                list.add("Locality not specified (no physical distance assumed).");
                break;
        }

        // 3. Availability Explanation
        list.add("Availability recorded as available in volunteer lifesaver registry.");

        return list;
    }
}

package com.bloodbuddy.model;

import java.util.Collections;
import java.util.List;

/**
 * Model representing a smart donor match for a specific blood request.
 * Encapsulates ABO/Rh compatibility category, locality relevance,
 * transparent score ranking, and verified explanations.
 */
public class DonorMatch implements Comparable<DonorMatch> {

    public enum MatchCategory {
        EXACT_MATCH("Exact Blood-Group Match"),
        COMPATIBLE_ALTERNATIVE("Potentially Compatible Alternative");

        private final String label;

        MatchCategory(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum LocationMatch {
        SAME_LOCALITY("Same Locality as Hospital"),
        NEARBY_LOCALITY("Nearby Locality in Rajahmundry"),
        OTHER_LOCALITY("Regional Rajahmundry Locality"),
        UNKNOWN_LOCALITY("Locality Not Specified");

        private final String label;

        LocationMatch(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final Donor donor;
    private final BloodRequest request;
    private final MatchCategory matchCategory;
    private final LocationMatch locationMatch;
    private final int rankScore;
    private final List<String> matchExplanations;
    private final String preliminaryNotice;

    public DonorMatch(Donor donor, BloodRequest request, MatchCategory matchCategory,
                      LocationMatch locationMatch, int rankScore, List<String> matchExplanations) {
        this.donor = donor;
        this.request = request;
        this.matchCategory = matchCategory;
        this.locationMatch = locationMatch;
        this.rankScore = rankScore;
        this.matchExplanations = Collections.unmodifiableList(matchExplanations);
        this.preliminaryNotice = "Preliminary match only. Does not guarantee medical suitability. Requires serological cross-matching and verification by qualified hospital blood bank staff.";
    }

    public Donor getDonor() {
        return donor;
    }

    public BloodRequest getRequest() {
        return request;
    }

    public MatchCategory getMatchCategory() {
        return matchCategory;
    }

    public LocationMatch getLocationMatch() {
        return locationMatch;
    }

    public int getRankScore() {
        return rankScore;
    }

    public List<String> getMatchExplanations() {
        return matchExplanations;
    }

    public String getPreliminaryNotice() {
        return preliminaryNotice;
    }

    public boolean isExactMatch() {
        return matchCategory == MatchCategory.EXACT_MATCH;
    }

    @Override
    public int compareTo(DonorMatch o) {
        // Higher rank score first
        int cmp = Integer.compare(o.rankScore, this.rankScore);
        if (cmp != 0) {
            return cmp;
        }
        // Stable secondary tie-breaker by donor ID
        return this.donor.getId().compareTo(o.donor.getId());
    }
}

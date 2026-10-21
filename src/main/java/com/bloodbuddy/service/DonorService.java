package com.bloodbuddy.service;

import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.repository.DonorRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service managing demonstration donor queries, search, voluntary registration,
 * and availability status updates.
 * Integrates static seed donors with dynamically registered and persisted donors.
 */
public class DonorService {

    private final DonorRepository repository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public DonorService() {
        this(DonorRepository.getInstance());
    }

    public DonorService(DonorRepository repository) {
        this.repository = repository;
    }

    public List<Donor> getAllDonors() {
        List<Donor> combined = new ArrayList<>(SeedDataProvider.getSeedDonors());
        combined.addAll(repository.getAllRegisteredDonors());
        return Collections.unmodifiableList(combined);
    }

    /**
     * Backward-compatible alias for getAllDonors().
     */
    @Deprecated
    public List<Donor> getAllDemonstrationDonors() {
        return getAllDonors();
    }

    /**
     * Returns only registered donors from local file storage.
     */
    public List<Donor> getRegisteredDonors() {
        return repository.getAllRegisteredDonors();
    }

    /**
     * Backward-compatible filter by blood group for Home Dashboard.
     */
    public List<Donor> filterByBloodGroup(String bloodGroup) {
        return searchDonors(null, bloodGroup, null);
    }

    /**
     * Multi-criteria search and filter across name, blood group, and locality.
     * All criteria are combined with logical AND.
     * Operates over all available donors (seeds + registered).
     */
    public List<Donor> searchDonors(String nameQuery, String bloodGroup, String locality) {
        List<Donor> all = getAllDonors();
        List<Donor> results = new ArrayList<>();

        String cleanName = (nameQuery != null && !nameQuery.trim().isEmpty())
                ? nameQuery.trim().toLowerCase() : null;

        String cleanGroup = (bloodGroup != null && !bloodGroup.trim().isEmpty() && !"ALL".equalsIgnoreCase(bloodGroup.trim()))
                ? BloodCompatibility.normalizeGroup(bloodGroup) : null;

        String cleanLocality = null;
        if (locality != null && !locality.trim().isEmpty() && !"ALL".equalsIgnoreCase(locality.trim())) {
            String norm = SeedDataProvider.normalizeLocality(locality);
            cleanLocality = (norm != null) ? norm : locality.trim();
        }

        for (Donor d : all) {
            boolean matchesName = true;
            boolean matchesGroup = true;
            boolean matchesLocality = true;

            // 1. Partial case-insensitive name check
            if (cleanName != null) {
                matchesName = d.getName().toLowerCase().contains(cleanName);
            }

            // 2. Exact validated blood group check
            if (cleanGroup != null) {
                matchesGroup = d.getBloodGroup().equalsIgnoreCase(cleanGroup);
            }

            // 3. Validated locality check (case-insensitive)
            if (cleanLocality != null) {
                matchesLocality = d.getLocality().equalsIgnoreCase(cleanLocality);
            }

            if (matchesName && matchesGroup && matchesLocality) {
                results.add(d);
            }
        }

        return results;
    }

    /**
     * Finds a donor by ID across registered records and seed records.
     */
    public Optional<Donor> findDonorById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }

        String searchId = id.trim();

        // 1. Check registered donors in file storage
        Optional<Donor> registered = repository.findById(searchId);
        if (registered.isPresent()) {
            return registered;
        }

        // 2. Check seed records
        for (Donor d : SeedDataProvider.getSeedDonors()) {
            if (d.getId().equalsIgnoreCase(searchId)) {
                return Optional.of(d);
            }
        }

        return Optional.empty();
    }

    /**
     * Registers a new voluntary donor, persists to local file storage, and returns the created Donor.
     */
    public Donor registerDonor(String name, String bloodGroup, int age, String city,
                              String locality, boolean available, String contactInfo) {

        String id = repository.generateNextId();
        String normalizedGroup = BloodCompatibility.normalizeGroup(bloodGroup);
        String badge = "Registered Lifesaver";
        String statusText = available ? "Available" : "On Standby";
        String lastDonated = "Ready / First-time";
        String registeredAt = LocalDateTime.now().format(DATE_FORMATTER);

        Donor donor = new Donor(
                id,
                name.trim(),
                normalizedGroup,
                locality.trim(),
                lastDonated,
                available,
                badge,
                statusText,
                age,
                city.trim(),
                contactInfo != null ? contactInfo.trim() : "",
                true,
                registeredAt
        );

        return repository.save(donor);
    }

    /**
     * Updates donor availability preference in local persistence.
     */
    public boolean updateDonorAvailability(String donorId, boolean available) {
        String statusText = available ? "Demo Available" : "Demo On Standby";
        return repository.updateAvailability(donorId, available, statusText);
    }
}

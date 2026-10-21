package com.bloodbuddy.service;

import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.repository.BloodRequestRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing blood requisitions, query filtering, and validated submissions.
 */
public class BloodRequestService {

    private final BloodRequestRepository repository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public BloodRequestService() {
        this(BloodRequestRepository.getInstance());
    }

    public BloodRequestService(BloodRequestRepository repository) {
        this.repository = repository;
    }

    public List<BloodRequest> getAllRequests() {
        return repository.getAllRequests();
    }

    public Optional<BloodRequest> getRequestById(String id) {
        return repository.findById(id);
    }

    public boolean updateRequestStatus(String requestId, String newStatus) {
        return repository.updateStatus(requestId, newStatus);
    }

    public BloodRequest createRequest(String patientName, String bloodGroup, int units,
                                      String hospitalName, String hospitalLocality,
                                      String contactPerson, String contactPhone,
                                      String requiredBy, String urgency, String notes) {

        String normalizedGroup = BloodCompatibility.normalizeGroup(bloodGroup);
        String createdAt = LocalDateTime.now().format(DATE_FORMATTER);
        String status = "Pending";

        return repository.createAndSave(
                patientName.trim(),
                normalizedGroup,
                units,
                hospitalName.trim(),
                hospitalLocality.trim(),
                contactPerson.trim(),
                contactPhone != null ? contactPhone.trim() : "",
                requiredBy.trim(),
                urgency.trim(),
                notes != null ? notes.trim() : "",
                status,
                createdAt,
                false
        );
    }

    /**
     * Server-side filtering across blood group, urgency, and hospital/locality query.
     */
    public List<BloodRequest> filterRequests(String bloodGroup, String urgency, String query) {
        List<BloodRequest> all = getAllRequests();
        List<BloodRequest> results = new ArrayList<>();

        String cleanGroup = (bloodGroup != null && !bloodGroup.trim().isEmpty() && !"ALL".equalsIgnoreCase(bloodGroup.trim()))
                ? BloodCompatibility.normalizeGroup(bloodGroup) : null;

        String cleanUrgency = (urgency != null && !urgency.trim().isEmpty() && !"ALL".equalsIgnoreCase(urgency.trim()))
                ? urgency.trim() : null;

        String cleanQuery = (query != null && !query.trim().isEmpty())
                ? query.trim().toLowerCase() : null;

        for (BloodRequest r : all) {
            boolean matchesGroup = true;
            boolean matchesUrgency = true;
            boolean matchesQuery = true;

            if (cleanGroup != null) {
                matchesGroup = r.getBloodGroup().equalsIgnoreCase(cleanGroup);
            }

            if (cleanUrgency != null) {
                matchesUrgency = r.getUrgency().equalsIgnoreCase(cleanUrgency);
            }

            if (cleanQuery != null) {
                String target = (r.getHospitalName() + " " + r.getHospitalLocality() + " " + r.getPatientName()).toLowerCase();
                matchesQuery = target.contains(cleanQuery);
            }

            if (matchesGroup && matchesUrgency && matchesQuery) {
                results.add(r);
            }
        }

        return results;
    }
}

package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.DonorMatch;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorMatchingService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * RequestBloodServlet handles patient blood unit requisition submissions (Stage 5/7).
 * Validates all required inputs strictly on the server using Java.
 * Pure Java Servlets & CSS3 • POST/Redirect/GET architecture.
 */
public class RequestBloodServlet extends HttpServlet {

    private final BloodRequestService requestService = new BloodRequestService();
    private final DonorMatchingService matchingService = new DonorMatchingService();
    private final NotificationService notificationService = new NotificationService();
    private final AuthService authService = new AuthService();

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s.'\\-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s\\-\\(\\)]{8,18}$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        String initialContactPerson = (currentUser != null) ? currentUser.getFullName() : "";
        String initialContactPhone = (currentUser != null) ? currentUser.getPhone() : "";

        // Default default datetime 24 hours from now
        String defaultRequiredBy = LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));

        try (PrintWriter out = resp.getWriter()) {
            renderFormPage(out, null, "", "", "1", "", "Danavaipeta, Rajahmundry",
                    initialContactPerson, initialContactPhone, defaultRequiredBy, "Urgent", "", unreadCount, isAdmin, currentUser);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String rawPatientName = req.getParameter("patientName");
        String rawGroup = req.getParameter("bloodGroup");
        String rawUnits = req.getParameter("units");
        String rawHospital = req.getParameter("hospitalName");
        String rawLocality = req.getParameter("hospitalLocality");
        String rawContactPerson = req.getParameter("contactPerson");
        String rawContactPhone = req.getParameter("contactPhone");
        String rawRequiredBy = req.getParameter("requiredBy");
        String rawUrgency = req.getParameter("urgency");
        String rawNotes = req.getParameter("notes");

        String patientName = (rawPatientName != null) ? rawPatientName.trim() : "";
        String bloodGroup = (rawGroup != null) ? rawGroup.trim() : "";
        String unitsStr = (rawUnits != null) ? rawUnits.trim() : "1";
        String hospitalName = (rawHospital != null) ? rawHospital.trim() : "";
        String hospitalLocality = (rawLocality != null) ? rawLocality.trim() : "";
        String contactPerson = (rawContactPerson != null) ? rawContactPerson.trim() : "";
        String contactPhone = (rawContactPhone != null) ? rawContactPhone.trim() : "";
        String requiredBy = (rawRequiredBy != null) ? rawRequiredBy.trim() : "";
        String urgency = (rawUrgency != null) ? rawUrgency.trim() : "Urgent";
        String notes = (rawNotes != null) ? rawNotes.trim() : "";

        List<String> errors = new ArrayList<>();

        // 1. Patient Name Validation
        if (patientName.isEmpty()) {
            errors.add("Patient Name is required and cannot be empty.");
        } else if (patientName.length() < 2 || patientName.length() > AppConfig.MAX_NAME_LENGTH) {
            errors.add("Patient Name must be between 2 and " + AppConfig.MAX_NAME_LENGTH + " characters.");
        } else if (!NAME_PATTERN.matcher(patientName).matches()) {
            errors.add("Patient Name contains invalid characters. Use letters, spaces, and hyphens only.");
        }

        // 2. Blood Group Validation
        if (bloodGroup.isEmpty()) {
            errors.add("Required Blood Group must be specified.");
        } else if (!BloodCompatibility.isValidGroup(bloodGroup)) {
            errors.add("Invalid Blood Group '" + escapeHtml(bloodGroup) + "'. Supported groups: A+, A-, B+, B-, AB+, AB-, O+, O-.");
        }

        // 3. Units Validation
        int parsedUnits = 1;
        if (unitsStr.isEmpty()) {
            errors.add("Required units of blood is mandatory.");
        } else {
            try {
                parsedUnits = Integer.parseInt(unitsStr);
                if (parsedUnits < AppConfig.MIN_BLOOD_UNITS || parsedUnits > AppConfig.MAX_BLOOD_UNITS) {
                    errors.add("Units of blood must be between " + AppConfig.MIN_BLOOD_UNITS + " and " + AppConfig.MAX_BLOOD_UNITS + " units.");
                }
            } catch (NumberFormatException e) {
                errors.add("Units of blood must be a valid positive whole number.");
            }
        }

        // 4. Hospital Name Validation
        if (hospitalName.isEmpty()) {
            errors.add("Hospital Name is required.");
        } else if (hospitalName.length() < 2 || hospitalName.length() > AppConfig.MAX_HOSPITAL_NAME_LENGTH) {
            errors.add("Hospital Name must be between 2 and " + AppConfig.MAX_HOSPITAL_NAME_LENGTH + " characters.");
        } else if (hospitalName.contains("<") || hospitalName.contains(">")) {
            errors.add("Hospital Name contains invalid markup characters.");
        }

        // 5. Hospital Locality / City Validation
        if (hospitalLocality.isEmpty()) {
            errors.add("Hospital Locality/City is required.");
        } else if (hospitalLocality.length() < 2 || hospitalLocality.length() > AppConfig.MAX_LOCALITY_LENGTH) {
            errors.add("Hospital Locality must be between 2 and " + AppConfig.MAX_LOCALITY_LENGTH + " characters.");
        } else if (hospitalLocality.contains("<") || hospitalLocality.contains(">")) {
            errors.add("Hospital Locality contains invalid characters.");
        }

        // 6. Contact Person Validation
        if (contactPerson.isEmpty()) {
            errors.add("Contact Person Name is required.");
        } else if (contactPerson.length() < 2 || contactPerson.length() > AppConfig.MAX_NAME_LENGTH) {
            errors.add("Contact Person Name must be between 2 and " + AppConfig.MAX_NAME_LENGTH + " characters.");
        } else if (!NAME_PATTERN.matcher(contactPerson).matches()) {
            errors.add("Contact Person Name contains invalid characters.");
        }

        // 7. Contact Phone Validation
        if (contactPhone.isEmpty()) {
            errors.add("Contact Phone Number is required for medical coordination.");
        } else if (!PHONE_PATTERN.matcher(contactPhone).matches()) {
            errors.add("Invalid phone number format. Please provide a valid 8 to 15 digit telephone or mobile number.");
        }

        // 8. Required-By Date/Time Validation
        if (requiredBy.isEmpty()) {
            errors.add("Required-by Date and Time is mandatory.");
        } else {
            // Validate it parses as an ISO local datetime
            try {
                LocalDateTime.parse(requiredBy);
            } catch (DateTimeParseException ignored) {
                // Also accept space separator format if submitted
                try {
                    LocalDateTime.parse(requiredBy.replace(" ", "T"));
                } catch (DateTimeParseException e) {
                    errors.add("Required-by Date and Time must be a valid date and time value.");
                }
            }
        }

        // 9. Urgency Validation
        if (urgency.isEmpty()) {
            errors.add("Urgency level must be specified.");
        } else if (!"Normal".equalsIgnoreCase(urgency) && !"Urgent".equalsIgnoreCase(urgency) && !"Critical".equalsIgnoreCase(urgency)) {
            errors.add("Urgency level must be Normal, Urgent, or Critical.");
        }

        // 10. Notes Validation
        if (notes.length() > AppConfig.MAX_NOTES_LENGTH) {
            errors.add("Additional notes must not exceed " + AppConfig.MAX_NOTES_LENGTH + " characters.");
        } else if (notes.contains("<") || notes.contains(">")) {
            errors.add("Additional notes contain invalid HTML markup characters.");
        }

        // Process Result
        if (!errors.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/html;charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");
            boolean isAdmin = authService.isAdmin(req);
            User currentUser = authService.getCurrentUser(req);
            int unreadCount = notificationService.getUnreadCount(isAdmin);
            try (PrintWriter out = resp.getWriter()) {
                renderFormPage(out, errors, patientName, bloodGroup, unitsStr, hospitalName,
                        hospitalLocality, contactPerson, contactPhone, requiredBy, urgency, notes,
                        unreadCount, isAdmin, currentUser);
            }
        } else {
            // Valid submission: Persist to storage and perform POST/Redirect/GET
            String formattedRequiredBy = requiredBy.replace("T", " ");
            BloodRequest created = requestService.createRequest(patientName, bloodGroup, parsedUnits,
                    hospitalName, hospitalLocality, contactPerson, contactPhone, formattedRequiredBy, urgency, notes);

            // Stage 7: Trigger notification and run smart donor matching
            notificationService.notifyRequestSubmitted(created);
            List<DonorMatch> matches = matchingService.findMatches(created);
            if (matches.isEmpty()) {
                notificationService.notifyNoMatches(created);
            } else {
                notificationService.notifyMatchesFound(created, matches.size());
            }

            String redirectUrl = req.getContextPath() + "/request-confirmation?id="
                    + URLEncoder.encode(created.getId(), StandardCharsets.UTF_8);

            resp.setStatus(HttpServletResponse.SC_SEE_OTHER); // HTTP 303 prevents duplicate form submissions
            resp.setHeader("Location", redirectUrl);
        }
    }

    private void renderFormPage(PrintWriter out, List<String> errors, String patientName,
                                String bloodGroup, String unitsStr, String hospitalName,
                                String hospitalLocality, String contactPerson, String contactPhone,
                                String requiredBy, String urgency, String notes,
                                int unreadCount, boolean isAdmin, User currentUser) {

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Request Blood — Every Second Matters | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Submit an urgent blood requisition across Rajahmundry healthcare facilities. Connect admitted patients with compatible donors.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        // Header Navigation
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\">\n");
        out.write("          <img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Droplet Logo\" width=\"38\" height=\"38\">\n");
        out.write("        </div>\n");
        out.write("        <div class=\"brand-text\">\n");
        out.write("          <span class=\"brand-name\">" + AppConfig.APP_NAME + "</span>\n");
        out.write("          <span class=\"brand-tagline-sub\">" + AppConfig.TAGLINE + "</span>\n");
        out.write("        </div>\n");
        out.write("      </a>\n");
        out.write("      <nav aria-label=\"Main Navigation\">\n");
        out.write("        <ul class=\"nav-links\">\n");
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("          <li><a href=\"/find-donors\" class=\"nav-link\">Find Donors</a></li>\n");
        out.write("          <li><a href=\"/camps\" class=\"nav-link\">Camps</a></li>\n");
        out.write("          <li><a href=\"/become-donor\" class=\"nav-link\">Become a Donor</a></li>\n");
        out.write("          <li><a href=\"/request-blood\" class=\"nav-link active-nav\">Request Blood</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        out.write("        <a href=\"/notifications\" class=\"nav-utility-link\" title=\"View Notifications\">\n");
        out.write("          <span>🔔 Alerts" + (unreadCount > 0 ? " <span class=\"notif-badge\">" + unreadCount + "</span>" : "") + "</span>\n");
        out.write("        </a>\n");
        if (currentUser != null) {
            String displayName = currentUser.getFullName();
            if (displayName.length() > 14) {
                displayName = displayName.substring(0, 12) + "…";
            }
            out.write("        <a href=\"/account\" class=\"btn btn-secondary btn-sm\" title=\"Account Profile\">👤 " + escapeHtml(displayName) + "</a>\n");
            if (isAdmin) {
                out.write("        <a href=\"/admin/emergency\" class=\"btn btn-secondary btn-sm\" style=\"color: var(--ruby-red);\">🚨 Control</a>\n");
            }
            out.write("        <a href=\"/logout\" class=\"btn btn-secondary btn-sm\">Sign Out</a>\n");
        } else {
            out.write("        <a href=\"/login\" class=\"btn btn-primary btn-sm\">Sign In</a>\n");
        }
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </header>\n");

        out.write("  <main class=\"request-blood-main\">\n");
        out.write("    <div class=\"container\">\n");

        // Hero Header Area
        out.write("      <div class=\"request-header-section\">\n");
        out.write("        <div class=\"stage-badge stage-badge-urgent\">Patient Blood Requisition</div>\n");
        out.write("        <h1 class=\"request-title\">Every Second Matters.</h1>\n");
        out.write("        <p class=\"request-subtitle\">Submit a blood request and help connect patients with potential donors. Enter patient hospital details to broadcast verified urgent requisitions across Rajahmundry.</p>\n");
        out.write("      </div>\n");

        // Student Prototype & Emergency Notice Card (Rule 7)
        out.write("      <div class=\"emergency-notice-card\" role=\"alert\">\n");
        out.write("        <div class=\"notice-icon-box\">🚨</div>\n");
        out.write("        <div class=\"notice-content\">\n");
        out.write("          <h4 class=\"notice-heading\">Emergency Coordination Protocol</h4>\n");
        out.write("          <p>Blood Buddy coordinates voluntary community blood requests. For active life-threatening clinical emergencies, please <strong>immediately contact treating hospital emergency desks or dial <a href=\"tel:108\" class=\"emergency-tel\">108</a></strong> without delay.</p>\n");
        out.write("          <div class=\"notice-sub\">Submitting alerts community donors and coordinators. Clinical cross-matching must be performed prior to transfusion.</div>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Validation Error Alert Box
        if (errors != null && !errors.isEmpty()) {
            out.write("      <div class=\"form-error-alert\" role=\"alert\" aria-live=\"assertive\">\n");
            out.write("        <div class=\"error-alert-header\">\n");
            out.write("          <span class=\"error-icon\">⚠️</span>\n");
            out.write("          <strong>Please resolve the following " + errors.size() + " issue" + (errors.size() > 1 ? "s" : "") + ":</strong>\n");
            out.write("        </div>\n");
            out.write("        <ul class=\"form-error-list\">\n");
            for (String err : errors) {
                out.write("          <li>" + escapeHtml(err) + "</li>\n");
            }
            out.write("        </ul>\n");
            out.write("      </div>\n");
        }

        // Form Container Card
        out.write("      <section class=\"request-form-card\">\n");
        out.write("        <div class=\"form-card-header\">\n");
        out.write("          <h2 class=\"form-card-title\">Patient Blood Requisition Details</h2>\n");
        out.write("          <span class=\"form-mandatory-note\">Fields marked with <span class=\"required-star\">*</span> are required</span>\n");
        out.write("        </div>\n");

        out.write("        <form method=\"POST\" action=\"/request-blood\" class=\"blood-req-form\" novalidate>\n");
        out.write("          <div class=\"form-grid-2col\">\n");

        // 1. Patient Name
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"patientName\" class=\"form-label\">Patient Name <span class=\"required-star\">*</span></label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">🏥</span>\n");
        out.write("                <input type=\"text\" id=\"patientName\" name=\"patientName\" class=\"form-control\" \n");
        out.write("                       placeholder=\"e.g. Ramesh Varma\" value=\"" + escapeHtml(patientName) + "\" \n");
        out.write("                       maxlength=\"" + AppConfig.MAX_NAME_LENGTH + "\" required>\n");
        out.write("              </div>\n");
        out.write("              <span class=\"form-help-text\">Full name of admitted patient requiring transfusion.</span>\n");
        out.write("            </div>\n");

        // 2. Blood Group
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"bloodGroup\" class=\"form-label\">Required Blood Group <span class=\"required-star\">*</span></label>\n");
        out.write("              <select id=\"bloodGroup\" name=\"bloodGroup\" class=\"form-control form-select\" required>\n");
        out.write("                <option value=\"\">-- Select Blood Group --</option>\n");
        for (String g : BloodCompatibility.ALL_GROUPS) {
            boolean selected = g.equalsIgnoreCase(bloodGroup);
            out.write("                <option value=\"" + g + "\"" + (selected ? " selected" : "") + ">" + g + " (" + BloodCompatibility.getClinicalSummary(g).substring(0, Math.min(28, BloodCompatibility.getClinicalSummary(g).length())) + "...)</option>\n");
        }
        out.write("              </select>\n");
        out.write("              <span class=\"form-help-text\">Clinical ABO/Rh blood type as indicated on prescription.</span>\n");
        out.write("            </div>\n");

        // 3. Units Required
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"units\" class=\"form-label\">Units Required <span class=\"required-star\">*</span></label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">🩸</span>\n");
        out.write("                <input type=\"number\" id=\"units\" name=\"units\" class=\"form-control\" \n");
        out.write("                       placeholder=\"e.g. 2\" value=\"" + escapeHtml(unitsStr) + "\" \n");
        out.write("                       min=\"" + AppConfig.MIN_BLOOD_UNITS + "\" max=\"" + AppConfig.MAX_BLOOD_UNITS + "\" required>\n");
        out.write("              </div>\n");
        out.write("              <span class=\"form-help-text\">Standard units requested (1 to 20 units).</span>\n");
        out.write("            </div>\n");

        // 4. Urgency Level
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"urgency\" class=\"form-label\">Urgency Level <span class=\"required-star\">*</span></label>\n");
        out.write("              <select id=\"urgency\" name=\"urgency\" class=\"form-control form-select\" required>\n");
        out.write("                <option value=\"Normal\"" + ("Normal".equalsIgnoreCase(urgency) ? " selected" : "") + ">🟢 Normal — Scheduled procedure or elective operation</option>\n");
        out.write("                <option value=\"Urgent\"" + ("Urgent".equalsIgnoreCase(urgency) ? " selected" : "") + ">🟠 Urgent — Required within 12 to 24 hours</option>\n");
        out.write("                <option value=\"Critical\"" + ("Critical".equalsIgnoreCase(urgency) ? " selected" : "") + ">🚨 Critical — Emergency ICU / Immediate trauma requirement</option>\n");
        out.write("              </select>\n");
        out.write("              <span class=\"form-help-text\">Triage classification determines priority highlighting.</span>\n");
        out.write("            </div>\n");

        // 5. Hospital Name
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"hospitalName\" class=\"form-label\">Hospital Name <span class=\"required-star\">*</span></label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">🏛️</span>\n");
        out.write("                <input type=\"text\" id=\"hospitalName\" name=\"hospitalName\" class=\"form-control\" \n");
        out.write("                       placeholder=\"e.g. Government General Hospital\" value=\"" + escapeHtml(hospitalName) + "\" \n");
        out.write("                       maxlength=\"" + AppConfig.MAX_HOSPITAL_NAME_LENGTH + "\" required>\n");
        out.write("              </div>\n");
        out.write("              <span class=\"form-help-text\">Facility where patient is admitted or surgery is scheduled.</span>\n");
        out.write("            </div>\n");

        // 6. Hospital Locality / City
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"hospitalLocality\" class=\"form-label\">Hospital Locality / City <span class=\"required-star\">*</span></label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">📍</span>\n");
        out.write("                <input type=\"text\" id=\"hospitalLocality\" name=\"hospitalLocality\" class=\"form-control\" \n");
        out.write("                       placeholder=\"e.g. Danavaipeta, Rajahmundry\" value=\"" + escapeHtml(hospitalLocality) + "\" \n");
        out.write("                       maxlength=\"" + AppConfig.MAX_LOCALITY_LENGTH + "\" required>\n");
        out.write("              </div>\n");
        out.write("              <span class=\"form-help-text\">Specific zone to allow local volunteer donor mobilization.</span>\n");
        out.write("            </div>\n");

        // 7. Contact Person Name
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"contactPerson\" class=\"form-label\">Contact Person Name <span class=\"required-star\">*</span></label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">👤</span>\n");
        out.write("                <input type=\"text\" id=\"contactPerson\" name=\"contactPerson\" class=\"form-control\" \n");
        out.write("                       placeholder=\"e.g. P. Satyanarayana (Attendant / Relative)\" value=\"" + escapeHtml(contactPerson) + "\" \n");
        out.write("                       maxlength=\"" + AppConfig.MAX_NAME_LENGTH + "\" required>\n");
        out.write("              </div>\n");
        out.write("              <span class=\"form-help-text\">Attendant, relative, or hospital liaison point of contact.</span>\n");
        out.write("            </div>\n");

        // 8. Contact Phone Number (Private)
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"contactPhone\" class=\"form-label\">Contact Phone Number <span class=\"required-star\">*</span></label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">📞</span>\n");
        out.write("                <input type=\"tel\" id=\"contactPhone\" name=\"contactPhone\" class=\"form-control\" \n");
        out.write("                       placeholder=\"e.g. +91 98480 12345\" value=\"" + escapeHtml(contactPhone) + "\" \n");
        out.write("                       maxlength=\"20\" required>\n");
        out.write("              </div>\n");
        out.write("              <span class=\"form-help-text\">🔒 <strong>Privacy Protected:</strong> Phone numbers are NEVER displayed publicly on the request board.</span>\n");
        out.write("            </div>\n");

        // 9. Required-By Date and Time
        out.write("            <div class=\"form-group form-group-full\">\n");
        out.write("              <label for=\"requiredBy\" class=\"form-label\">Required-By Date &amp; Time <span class=\"required-star\">*</span></label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">🕒</span>\n");
        out.write("                <input type=\"datetime-local\" id=\"requiredBy\" name=\"requiredBy\" class=\"form-control\" \n");
        out.write("                       value=\"" + escapeHtml(requiredBy) + "\" required>\n");
        out.write("              </div>\n");
        out.write("              <span class=\"form-help-text\">Latest date/time by which units must be ready at the blood bank.</span>\n");
        out.write("            </div>\n");

        // 10. Additional Clinical Notes
        out.write("            <div class=\"form-group form-group-full\">\n");
        out.write("              <label for=\"notes\" class=\"form-label\">Additional Notes / Ward Details <span class=\"optional-pill\">Optional</span></label>\n");
        out.write("              <textarea id=\"notes\" name=\"notes\" class=\"form-control form-textarea\" rows=\"3\" \n");
        out.write("                        placeholder=\"e.g. Admitted in ICU Bed 14. Doctor: Dr. K. Rao. Cross-matching sample submitted to blood bank.\" \n");
        out.write("                        maxlength=\"" + AppConfig.MAX_NOTES_LENGTH + "\">" + escapeHtml(notes) + "</textarea>\n");
        out.write("              <span class=\"form-help-text\">Include specific room, attending physician, or transport details if relevant.</span>\n");
        out.write("            </div>\n");

        out.write("          </div>\n"); // End form-grid-2col

        // Form Submit Actions
        out.write("          <div class=\"form-actions-row\">\n");
        out.write("            <div class=\"form-actions-buttons\">\n");
        out.write("              <button type=\"submit\" class=\"btn btn-primary\" style=\"padding: 14px 32px; font-size: 1rem;\">\n");
        out.write("                <span>Submit Blood Request</span>\n");
        out.write("                <span class=\"btn-icon\">🚨</span>\n");
        out.write("              </button>\n");
        out.write("              <a href=\"/request-blood\" class=\"btn btn-secondary\" style=\"padding: 14px 24px;\">\n");
        out.write("                <span>Reset Form</span>\n");
        out.write("                <span class=\"btn-icon\">↺</span>\n");
        out.write("              </a>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"form-submit-note\">\n");
        out.write("              <a href=\"/view-requests\" class=\"view-board-link\">View Submitted Blood Requests Board →</a>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("        </form>\n");
        out.write("      </section>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        // Footer
        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container\">\n");
        out.write("      <div class=\"footer-top\">\n");
        out.write("        <div>\n");
        out.write("          <div class=\"brand\">\n");
        out.write("            <div class=\"brand-logo-wrap\">\n");
        out.write("              <img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Logo\" width=\"36\" height=\"36\">\n");
        out.write("            </div>\n");
        out.write("            <div class=\"brand-text\">\n");
        out.write("              <span class=\"brand-name\">" + AppConfig.APP_NAME + "</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("          <p class=\"footer-brand-desc\">" + AppConfig.TAGLINE + "<br>Community-focused patient requisition and voluntary blood matching.</p>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Platform Services</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"/dashboard\">Home Dashboard</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Find Donors Directory</a></li>\n");
        out.write("            <li><a href=\"/become-donor\">Become a Donor</a></li>\n");
        out.write("            <li><a href=\"/request-blood\">Request Blood</a></li>\n");
        out.write("            <li><a href=\"/view-requests\">View Requests</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Platform Standards</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><span style=\"color: var(--mint);\">✓ Strict Input Validation</span></li>\n");
        out.write("            <li><span style=\"color: var(--mint);\">✓ Patient Contact Privacy Shield</span></li>\n");
        out.write("            <li><span style=\"color: var(--mint);\">✓ Clinical Urgency Triage</span></li>\n");
        out.write("            <li><span style=\"color: var(--mint);\">✓ Community Coordination</span></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Emergency Contacts</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"tel:108\" style=\"color: var(--neon-coral); font-weight: 700;\">🚨 Emergency Ambulance: 108</a></li>\n");
        out.write("            <li><a href=\"tel:104\" style=\"color: var(--mint); font-weight: 700;\">🩺 Health Information: 104</a></li>\n");
        out.write("            <li><a href=\"/view-requests\">Rajahmundry Govt Hospital Blood Bank</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("      <div class=\"footer-bottom\">\n");
        out.write("        <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved.</div>\n");
        out.write("        <div class=\"backend-status-badge\">\n");
        out.write("          <span class=\"status-dot\"></span> System Active &amp; Operational\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
        out.write("</body>\n</html>\n");
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

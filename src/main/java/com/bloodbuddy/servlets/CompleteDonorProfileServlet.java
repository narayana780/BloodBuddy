package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.NotificationService;
import com.bloodbuddy.service.SeedDataProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CompleteDonorProfileServlet enables authenticated users to complete their donor profile
 * after registration and join the voluntary blood donor directory.
 */
public class CompleteDonorProfileServlet extends HttpServlet {

    private final DonorService donorService = new DonorService();
    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s.'\\-]+$");
    private static final Pattern CITY_PATTERN = Pattern.compile("^[a-zA-Z\\s.'\\-]+$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = authService.getCurrentUser(req);
        if (currentUser == null) {
            resp.sendRedirect(req.getContextPath() + "/login?redirect=/complete-donor-profile");
            return;
        }

        if (currentUser.isDonor()) {
            resp.sendRedirect(req.getContextPath() + "/donor-profile?id="
                    + URLEncoder.encode(currentUser.getLinkedDonorId(), StandardCharsets.UTF_8));
            return;
        }

        boolean isAdmin = authService.isAdmin(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        String initialName = currentUser.getFullName();
        String initialContact = currentUser.getPhone();

        try (PrintWriter out = resp.getWriter()) {
            renderFormPage(out, null, initialName, "", "", "Rajahmundry", "", "Available", initialContact, unreadCount, isAdmin, currentUser);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        User currentUser = authService.getCurrentUser(req);
        if (currentUser == null) {
            resp.sendRedirect(req.getContextPath() + "/login?redirect=/complete-donor-profile");
            return;
        }

        String rawName = req.getParameter("name");
        String rawGroup = req.getParameter("bloodGroup");
        String rawAge = req.getParameter("age");
        String rawCity = req.getParameter("city");
        String rawLocality = req.getParameter("locality");
        String rawCustomLocality = req.getParameter("customLocality");
        String rawAvailability = req.getParameter("availability");
        String rawContact = req.getParameter("contactInfo");

        String name = (rawName != null && !rawName.trim().isEmpty()) ? rawName.trim() : currentUser.getFullName();
        String bloodGroup = (rawGroup != null) ? rawGroup.trim() : "";
        String ageStr = (rawAge != null) ? rawAge.trim() : "";
        String city = (rawCity != null && !rawCity.trim().isEmpty()) ? rawCity.trim() : "Rajahmundry";
        String locality = (rawLocality != null) ? rawLocality.trim() : "";
        String customLocality = (rawCustomLocality != null) ? rawCustomLocality.trim() : "";
        String availability = (rawAvailability != null) ? rawAvailability.trim() : "Available";
        String contactInfo = (rawContact != null) ? rawContact.trim() : currentUser.getPhone();

        if ("OTHER".equalsIgnoreCase(locality) && !customLocality.isEmpty()) {
            locality = customLocality;
        }

        List<String> errors = new ArrayList<>();

        // 1. Validate Full Name
        if (name.isEmpty()) {
            errors.add("Full Name is mandatory.");
        } else if (name.length() < 2 || name.length() > AppConfig.MAX_NAME_LENGTH) {
            errors.add("Full Name must be between 2 and " + AppConfig.MAX_NAME_LENGTH + " characters.");
        } else if (!NAME_PATTERN.matcher(name).matches()) {
            errors.add("Full Name contains invalid characters.");
        }

        // 2. Validate Blood Group
        if (bloodGroup.isEmpty()) {
            errors.add("Blood Group selection is mandatory.");
        } else if (!BloodCompatibility.isValidGroup(bloodGroup)) {
            errors.add("Unsupported blood group '" + escapeHtml(bloodGroup) + "'.");
        }

        // 3. Validate Age
        int parsedAge = -1;
        if (ageStr.isEmpty()) {
            errors.add("Age is mandatory.");
        } else {
            try {
                parsedAge = Integer.parseInt(ageStr);
                if (parsedAge < AppConfig.MIN_DONOR_AGE || parsedAge > AppConfig.MAX_DONOR_AGE) {
                    errors.add("Age must be between " + AppConfig.MIN_DONOR_AGE + " and " + AppConfig.MAX_DONOR_AGE + " years per clinical eligibility standards.");
                }
            } catch (NumberFormatException e) {
                errors.add("Age must be a valid whole number.");
            }
        }

        // 4. Validate City
        if (city.isEmpty()) {
            errors.add("City is mandatory.");
        } else if (city.length() < 2 || city.length() > AppConfig.MAX_CITY_LENGTH) {
            errors.add("City must be between 2 and " + AppConfig.MAX_CITY_LENGTH + " characters.");
        } else if (!CITY_PATTERN.matcher(city).matches()) {
            errors.add("City contains invalid characters.");
        }

        // 5. Validate Locality
        if (locality.isEmpty() || "OTHER".equalsIgnoreCase(locality)) {
            errors.add("Locality is mandatory. Please select your residential zone.");
        } else if (locality.length() < 2 || locality.length() > AppConfig.MAX_LOCALITY_LENGTH) {
            errors.add("Locality must be between 2 and " + AppConfig.MAX_LOCALITY_LENGTH + " characters.");
        }

        // 6. Validate Availability
        boolean isAvailable = true;
        if ("Unavailable".equalsIgnoreCase(availability)) {
            isAvailable = false;
        }

        // 7. Validate Contact Info
        if (!contactInfo.isEmpty() && contactInfo.length() > AppConfig.MAX_CONTACT_LENGTH) {
            errors.add("Contact information must not exceed " + AppConfig.MAX_CONTACT_LENGTH + " characters.");
        }

        if (!errors.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/html;charset=UTF-8");
            boolean isAdmin = authService.isAdmin(req);
            int unreadCount = notificationService.getUnreadCount(isAdmin);
            try (PrintWriter out = resp.getWriter()) {
                renderFormPage(out, errors, name, bloodGroup, ageStr, city, locality, availability, contactInfo, unreadCount, isAdmin, currentUser);
            }
        } else {
            Donor created = donorService.registerDonor(name, bloodGroup, parsedAge, city, locality, isAvailable, contactInfo);
            authService.linkDonorToUser(currentUser.getId(), created.getId());
            currentUser.setLinkedDonorId(created.getId());
            req.getSession().setAttribute(AuthService.SESSION_USER_KEY, currentUser);

            String redirectUrl = req.getContextPath() + "/donor-profile?id="
                    + URLEncoder.encode(created.getId(), StandardCharsets.UTF_8)
                    + "&registered=true";
            resp.sendRedirect(redirectUrl);
        }
    }

    private void renderFormPage(PrintWriter out, List<String> errors, String name, String bloodGroup,
                                String age, String city, String locality, String availability,
                                String contactInfo, int unreadCount, boolean isAdmin, User currentUser) {

        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Complete Your Donor Profile — Blood Buddy</title>\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/become-donor", unreadCount, isAdmin, currentUser);

        out.write("  <main class=\"become-donor-main\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 680px;\">\n");

        out.write("      <div class=\"become-donor-header\" style=\"text-align: center; margin-bottom: 24px;\">\n");
        out.write("        <div class=\"stage-badge stage-badge-verified\">Step 2 of 3: Complete Donor Profile</div>\n");
        out.write("        <h1 class=\"become-donor-title\" style=\"font-size: 2.1rem; margin: 8px 0;\">Complete Your Donor Profile</h1>\n");
        out.write("        <p class=\"become-donor-subtitle\" style=\"color: var(--text-secondary);\">Provide your blood group and residential area so emergency coordinators and patients can locate compatible lifesavers across Rajahmundry.</p>\n");
        out.write("      </div>\n");

        if (errors != null && !errors.isEmpty()) {
            out.write("      <div class=\"validation-alert-banner\" role=\"alert\" style=\"margin-bottom: 20px;\">\n");
            out.write("        <span class=\"alert-icon\">⚠️</span>\n");
            out.write("        <div class=\"alert-errors-list\">\n");
            out.write("          <strong>Please correct the following before continuing:</strong>\n");
            out.write("          <ul>\n");
            for (String err : errors) {
                out.write("            <li>" + escapeHtml(err) + "</li>\n");
            }
            out.write("          </ul>\n");
            out.write("        </div>\n");
            out.write("      </div>\n");
        }

        // Medical guidance disclaimer
        out.write("      <div class=\"demo-disclaimer-card\" style=\"margin-bottom: 24px;\">\n");
        out.write("        <span class=\"disclaimer-icon\">ℹ️</span>\n");
        out.write("        <div class=\"disclaimer-content\">\n");
        out.write("          <strong>Preliminary Screening Notice:</strong>\n");
        out.write("          <span>Profile details assist with voluntary matching triage. Actual donor fitness, hemoglobin evaluation, and blood cross-matching must be verified by qualified hospital laboratory staff prior to donation. Direct contact phone numbers are never published in public search results.</span>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        out.write("      <section class=\"registration-card glass-panel\" style=\"padding: 32px 28px; border-radius: var(--radius-lg);\">\n");
        out.write("        <form method=\"POST\" action=\"/complete-donor-profile\" class=\"donor-form\">\n");

        // Full Name
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 18px;\">\n");
        out.write("            <label for=\"donorName\" class=\"form-label\">Full Name <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("            <input type=\"text\" id=\"donorName\" name=\"name\" required class=\"form-control\" value=\"" + escapeHtml(name) + "\" placeholder=\"e.g. Rajesh Kumar\">\n");
        out.write("          </div>\n");

        // Blood Group & Age
        out.write("          <div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 18px;\">\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"bloodGroupSelect\" class=\"form-label\">Blood Group <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("              <select id=\"bloodGroupSelect\" name=\"bloodGroup\" required class=\"form-control form-select\">\n");
        out.write("                <option value=\"\">-- Select Blood Group --</option>\n");
        for (String bg : BloodCompatibility.ALL_GROUPS) {
            boolean isSel = bg.equalsIgnoreCase(bloodGroup);
            out.write("                <option value=\"" + bg + "\"" + (isSel ? " selected" : "") + ">" + bg + "</option>\n");
        }
        out.write("              </select>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"donorAge\" class=\"form-label\">Age (18–65) <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("              <input type=\"number\" id=\"donorAge\" name=\"age\" required min=\"18\" max=\"65\" class=\"form-control\" placeholder=\"e.g. 26\" value=\"" + escapeHtml(age) + "\">\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        // City & Locality
        out.write("          <div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 18px;\">\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"donorCity\" class=\"form-label\">City</label>\n");
        out.write("              <input type=\"text\" id=\"donorCity\" name=\"city\" class=\"form-control\" value=\"" + escapeHtml(city) + "\" readonly style=\"background: rgba(255, 255, 255, 0.05);\">\n");
        out.write("            </div>\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"donorLocality\" class=\"form-label\">Locality (Rajahmundry) <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("              <select id=\"donorLocality\" name=\"locality\" required class=\"form-control form-select\">\n");
        out.write("                <option value=\"\">-- Select Locality --</option>\n");
        for (String loc : SeedDataProvider.getLocalities()) {
            boolean isSel = loc.equalsIgnoreCase(locality);
            out.write("                <option value=\"" + loc + "\"" + (isSel ? " selected" : "") + ">📍 " + loc + "</option>\n");
        }
        out.write("              </select>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        // Availability Preference
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 18px;\">\n");
        out.write("            <label for=\"donorAvailability\" class=\"form-label\">Initial Availability Preference <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("            <select id=\"donorAvailability\" name=\"availability\" class=\"form-control form-select\">\n");
        out.write("              <option value=\"Available\"" + ("Available".equalsIgnoreCase(availability) ? " selected" : "") + ">🟢 Available — Ready to respond to urgent requests</option>\n");
        out.write("              <option value=\"Unavailable\"" + ("Unavailable".equalsIgnoreCase(availability) ? " selected" : "") + ">🟠 Temporarily Resting / Standby</option>\n");
        out.write("            </select>\n");
        out.write("            <span style=\"font-size: 0.78rem; color: var(--text-muted); display: block; margin-top: 4px;\">You can change your availability at any time from your profile dashboard.</span>\n");
        out.write("          </div>\n");

        // Contact Phone
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 22px;\">\n");
        out.write("            <label for=\"donorContact\" class=\"form-label\">Contact Phone</label>\n");
        out.write("            <input type=\"tel\" id=\"donorContact\" name=\"contactInfo\" class=\"form-control\" placeholder=\"e.g. 98480 12345\" value=\"" + escapeHtml(contactInfo) + "\">\n");
        out.write("            <span style=\"font-size: 0.78rem; color: var(--text-muted); display: block; margin-top: 4px;\">Kept private. Used strictly by hospital coordinators for emergency dispatch.</span>\n");
        out.write("          </div>\n");

        out.write("          <button type=\"submit\" class=\"btn btn-primary\" style=\"width: 100%; justify-content: center; padding: 14px; font-size: 1.05rem;\">\n");
        out.write("            <span>Complete Profile &amp; Join Directory</span>\n");
        out.write("            <span class=\"btn-icon\">🩸</span>\n");
        out.write("          </button>\n");
        out.write("        </form>\n");
        out.write("      </section>\n");

        out.write("    </div>\n");
        out.write("  </main>\n");

        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderHeader(PrintWriter out, String currentPath, int unreadCount, boolean isAdmin, User user) {
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\"><img src=\"/static/images/logo.svg\" alt=\"Blood Buddy\" width=\"38\" height=\"38\"></div>\n");
        out.write("        <div class=\"brand-text\"><span class=\"brand-name\">" + AppConfig.APP_NAME + "</span><span class=\"brand-tagline-sub\">" + AppConfig.TAGLINE + "</span></div>\n");
        out.write("      </a>\n");
        out.write("      <nav aria-label=\"Main Navigation\">\n");
        out.write("        <ul class=\"nav-links\">\n");
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("          <li><a href=\"/find-donors\" class=\"nav-link\">Find Donors</a></li>\n");
        out.write("          <li><a href=\"/camps\" class=\"nav-link\">Camps</a></li>\n");
        out.write("          <li><a href=\"/become-donor\" class=\"nav-link active-nav\">Become a Donor</a></li>\n");
        out.write("          <li><a href=\"/request-blood\" class=\"nav-link\">Request Blood</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        out.write("        <a href=\"/notifications\" class=\"nav-utility-link\" title=\"View Notifications\">\n");
        out.write("          <span>🔔 Alerts" + (unreadCount > 0 ? " <span class=\"notif-badge\">" + unreadCount + "</span>" : "") + "</span>\n");
        out.write("        </a>\n");
        if (user != null) {
            String displayName = user.getFullName();
            if (displayName.length() > 16) {
                displayName = displayName.substring(0, 14) + "…";
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
    }

    private void renderFooter(PrintWriter out) {
        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Voluntary Blood Donation Coordination Network.</div>\n");
        out.write("      <div>Preliminary ABO/Rh compatibility requires professional hospital confirmation. &bull; <a href=\"/admin/login\" style=\"color: var(--text-muted); text-decoration: none;\">Hospital Staff Portal</a></div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

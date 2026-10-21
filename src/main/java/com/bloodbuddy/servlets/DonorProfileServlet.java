package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.service.DonorService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * DonorProfileServlet renders individual registered donor demonstration profiles
 * and handles server-validated availability status updates.
 * Zero JavaScript used • Pure Java Servlets & CSS3.
 */
public class DonorProfileServlet extends HttpServlet {

    private final DonorService donorService = new DonorService();
    private final NotificationService notificationService = new NotificationService();
    private final AuthService authService = new AuthService();
    private final com.bloodbuddy.service.ChatService chatService = new com.bloodbuddy.service.ChatService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);
        int unreadMsgCount = (currentUser != null) ? chatService.getUnreadMessageCount(currentUser.getId()) : 0;

        String id = req.getParameter("id");
        boolean isJustRegistered = "true".equalsIgnoreCase(req.getParameter("registered"));
        boolean isJustUpdated = "true".equalsIgnoreCase(req.getParameter("updated"));

        Optional<Donor> donorOpt = (id != null && !id.trim().isEmpty())
                ? donorService.findDonorById(id.trim())
                : Optional.empty();

        try (PrintWriter out = resp.getWriter()) {
            if (donorOpt.isPresent()) {
                Donor d = donorOpt.get();
                boolean canEdit = isAuthorizedToEdit(d, currentUser, isAdmin);
                renderProfilePage(out, d, isJustRegistered, isJustUpdated, null, unreadCount, unreadMsgCount, isAdmin, currentUser, canEdit);
            } else {
                renderNotFoundPage(out, id, unreadCount, isAdmin, currentUser);
            }
        }
    }

    public static String renderAvailabilityBadge(Donor d) {
        if (d == null || d.getAvailabilityStatus() == null || d.getAvailabilityStatus().trim().isEmpty()) {
            return "<span class=\"badge-availability badge-status-none\">● Status Not Provided</span>";
        }
        if (d.isEligible()) {
            return "<span class=\"badge-availability badge-status-available\"><span style=\"width: 7px; height: 7px; border-radius: 50%; background: #10b981; display: inline-block;\"></span>Available</span>";
        } else {
            return "<span class=\"badge-availability badge-status-unavailable\"><span style=\"width: 7px; height: 7px; border-radius: 50%; background: #f59e0b; display: inline-block;\"></span>Temporarily Unavailable</span>";
        }
    }

    private boolean isAuthorizedToEdit(Donor d, User user, boolean isAdmin) {
        if (isAdmin) return true;
        if (user == null || d == null) return false;
        if (d.getId().equals(user.getLinkedDonorId())) return true;
        if (user.getPhone() != null && d.getContactInfo() != null) {
            String uP = user.getPhone().replaceAll("[^0-9]", "");
            String dP = d.getContactInfo().replaceAll("[^0-9]", "");
            if (!uP.isEmpty() && uP.equals(dP)) return true;
        }
        return false;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String donorId = req.getParameter("donorId");
        if (donorId == null || donorId.trim().isEmpty()) {
            donorId = req.getParameter("id");
        }
        String availability = req.getParameter("availability");

        if (donorId == null || donorId.trim().isEmpty()) {
            resp.sendRedirect(req.getContextPath() + "/find-donors");
            return;
        }

        String cleanId = donorId.trim();
        Optional<Donor> donorOpt = donorService.findDonorById(cleanId);
        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        if (donorOpt.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try (PrintWriter out = resp.getWriter()) {
                renderNotFoundPage(out, cleanId, unreadCount, isAdmin, currentUser);
            }
            return;
        }

        Donor donor = donorOpt.get();
        boolean canEdit = isAuthorizedToEdit(donor, currentUser, isAdmin);
        if (!canEdit) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "You are not authorized to update another donor's availability.");
            return;
        }

        // Validate availability parameter
        String validationError = null;
        boolean newAvailable = true;
        if (availability == null || availability.trim().isEmpty()) {
            validationError = "Please select a valid availability preference.";
        } else if ("true".equalsIgnoreCase(availability.trim()) || "Available".equalsIgnoreCase(availability.trim())) {
            newAvailable = true;
        } else if ("false".equalsIgnoreCase(availability.trim()) || "Unavailable".equalsIgnoreCase(availability.trim()) || "Temporarily Unavailable".equalsIgnoreCase(availability.trim())) {
            newAvailable = false;
        } else {
            validationError = "Unrecognized availability status '" + escapeHtml(availability) + "'.";
        }

        if (validationError != null) {
            resp.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) {
                int unreadMsgCount = (currentUser != null) ? chatService.getUnreadMessageCount(currentUser.getId()) : 0;
                renderProfilePage(out, donorOpt.get(), false, false, validationError, unreadCount, unreadMsgCount, isAdmin, currentUser, canEdit);
            }
            return;
        }

        // Apply validated availability update
        boolean updated = donorService.updateDonorAvailability(cleanId, newAvailable);

        String returnTo = req.getParameter("returnTo");
        String redirectUrl;
        if (returnTo != null && returnTo.startsWith("/")) {
            redirectUrl = req.getContextPath() + returnTo + (returnTo.contains("?") ? "&" : "?") + (updated ? "updated=true" : "error=update_failed");
        } else {
            redirectUrl = req.getContextPath() + "/donor-profile?id="
                    + URLEncoder.encode(cleanId, StandardCharsets.UTF_8)
                    + (updated ? "&updated=true" : "");
        }

        resp.setStatus(HttpServletResponse.SC_SEE_OTHER); // HTTP 303
        resp.setHeader("Location", redirectUrl);
    }

    private void renderProfilePage(PrintWriter out, Donor d, boolean isJustRegistered,
                                   boolean isJustUpdated, String errorNote,
                                   int unreadCount, int unreadMsgCount, boolean isAdmin, User currentUser, boolean canEdit) {

        List<String> canGiveTo = BloodCompatibility.getCanGiveTo(d.getBloodGroup());
        List<String> canReceiveFrom = BloodCompatibility.getCanReceiveFrom(d.getBloodGroup());

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Donor Profile: " + escapeHtml(d.getName()) + " (" + d.getBloodGroup() + ") | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Registered voluntary donor profile for " + escapeHtml(d.getName()) + " in Rajahmundry.\">\n");
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
        out.write("          <li><a href=\"/request-blood\" class=\"nav-link\">Request Blood</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        if (currentUser != null) {
            out.write("        <a href=\"/messages\" class=\"nav-utility-link\" title=\"Direct Messages\">\n");
            out.write("          <span>💬 Messages" + (unreadMsgCount > 0 ? " <span class=\"notif-badge\">" + unreadMsgCount + "</span>" : "") + "</span>\n");
            out.write("        </a>\n");
        }
        out.write("        <a href=\"/notifications\" class=\"nav-utility-link\" title=\"View Notifications\">\n");
        out.write("          <span>🔔 Alerts" + (unreadCount > 0 ? " <span class=\"notif-badge\">" + unreadCount + "</span>" : "") + "</span>\n");
        out.write("        </a>\n");
        if (currentUser != null) {
            out.write("        <a href=\"/account\" class=\"btn btn-secondary btn-sm\" title=\"Account Profile\">👤 " + escapeHtml(currentUser.getFullName()) + "</a>\n");
            if (isAdmin) {
                out.write("        <a href=\"/admin/emergency\" class=\"btn btn-secondary btn-sm\" style=\"color: var(--ruby-red);\" title=\"Hospital Staff Control Center\">🚨 Control</a>\n");
            }
            out.write("        <a href=\"/logout\" class=\"btn btn-secondary btn-sm\">Sign Out</a>\n");
        } else {
            out.write("        <a href=\"/login\" class=\"btn btn-primary btn-sm\">Sign In</a>\n");
        }
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </header>\n");

        out.write("  <main class=\"profile-main\">\n");
        out.write("    <div class=\"container\">\n");

        // Flash Banners
        if (isJustRegistered) {
            out.write("      <div class=\"success-alert-banner\" role=\"status\">\n");
            out.write("        <div class=\"alert-icon\">🎉</div>\n");
            out.write("        <div class=\"alert-text-content\">\n");
            out.write("          <h4 class=\"alert-heading\">Registration Confirmed!</h4>\n");
            out.write("          <p>Welcome to Blood Buddy! Your donor profile ID is <strong>" + escapeHtml(d.getId()) + "</strong>. Your record has been saved and is active in the voluntary directory.</p>\n");
            out.write("        </div>\n");
            out.write("      </div>\n");
        }

        if (isJustUpdated) {
            out.write("      <div class=\"success-alert-banner\" role=\"status\">\n");
            out.write("        <div class=\"alert-icon\">✅</div>\n");
            out.write("        <div class=\"alert-text-content\">\n");
            out.write("          <h4 class=\"alert-heading\">Availability Status Updated!</h4>\n");
            out.write("          <p>Your preference has been persisted. Current status is now: <strong>" + escapeHtml(d.getAvailabilityStatus()) + "</strong>.</p>\n");
            out.write("        </div>\n");
            out.write("      </div>\n");
        }

        if (errorNote != null) {
            out.write("      <div class=\"validation-alert-banner\" role=\"alert\">\n");
            out.write("        <span class=\"alert-icon\">⚠️</span>\n");
            out.write("        <span>" + errorNote + "</span>\n");
            out.write("      </div>\n");
        }

        // Profile Container Card
        out.write("      <article class=\"profile-card-container\">\n");
        out.write("        <div class=\"profile-header-strip\">\n");
        out.write("          <div class=\"profile-avatar-wrap\">\n");
        out.write("            <div class=\"profile-avatar-circle\">\n");
        out.write("              <span>" + escapeHtml(d.getName().substring(0, 1)) + "</span>\n");
        out.write("            </div>\n");
        out.write("            <div>\n");
        out.write("              <div class=\"profile-id-pill\">ID: " + escapeHtml(d.getId()) + "</div>\n");
        out.write("              <h1 class=\"profile-name\">" + escapeHtml(d.getName()) + "</h1>\n");
        out.write("              <p class=\"profile-location\">📍 " + escapeHtml(d.getLocality()) + ", " + escapeHtml(d.getCity()) + "</p>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"profile-group-wrap\">\n");
        out.write("            <div class=\"profile-blood-badge\">\n");
        out.write("              <span class=\"blood-drop-mini\">🩸</span>\n");
        out.write("              <span class=\"blood-type-big\">" + escapeHtml(d.getBloodGroup()) + "</span>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"profile-badge-tag\">" + escapeHtml(d.getBadge()) + "</div>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Profile Details Grid
        out.write("        <div class=\"profile-details-grid\">\n");

        out.write("          <div class=\"profile-info-card\">\n");
        out.write("            <span class=\"info-label\">Current Availability</span>\n");
        out.write("            <div style=\"margin: 6px 0;\">\n");
        out.write("              " + renderAvailabilityBadge(d) + "\n");
        out.write("            </div>\n");
        out.write("            <span class=\"info-sub\">" + (d.isEligible() ? "Recorded ready to respond" : "Currently on resting standby") + "</span>\n");
        out.write("          </div>\n");

        out.write("          <div class=\"profile-info-card\">\n");
        out.write("            <span class=\"info-label\">Age &amp; Screening</span>\n");
        out.write("            <span class=\"info-value\">" + d.getAge() + " Years</span>\n");
        out.write("            <span class=\"info-sub\">Within eligible range (18–65)</span>\n");
        out.write("          </div>\n");

        out.write("          <div class=\"profile-info-card\">\n");
        out.write("            <span class=\"info-label\">Registered Timestamp</span>\n");
        out.write("            <span class=\"info-value\">" + escapeHtml(d.getRegisteredAt()) + "</span>\n");
        out.write("            <span class=\"info-sub\">Saved in local persistence</span>\n");
        out.write("          </div>\n");

        out.write("          <div class=\"profile-info-card\">\n");
        out.write("            <span class=\"info-label\">Last Donated Interval</span>\n");
        out.write("            <span class=\"info-value\">" + escapeHtml(d.getLastDonated()) + "</span>\n");
        out.write("            <span class=\"info-sub\">Whole blood interval verified</span>\n");
        out.write("          </div>\n");

        out.write("        </div>\n");

        // Compatibility Intelligence
        out.write("        <div class=\"profile-compat-box\">\n");
        out.write("          <h3 class=\"compat-box-title\">Transfusion Compatibility for " + escapeHtml(d.getBloodGroup()) + "</h3>\n");
        out.write("          <div class=\"compat-rows\">\n");
        out.write("            <div class=\"compat-row-item\">\n");
        out.write("              <span class=\"compat-row-label\">Can Donate To:</span>\n");
        out.write("              <div class=\"compat-badges-row\">\n");
        for (String g : canGiveTo) {
            out.write("                <span class=\"compat-badge give\">" + g + "</span>\n");
        }
        out.write("              </div>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"compat-row-item\">\n");
        out.write("              <span class=\"compat-row-label\">Can Receive From:</span>\n");
        out.write("              <div class=\"compat-badges-row\">\n");
        for (String g : canReceiveFrom) {
            out.write("                <span class=\"compat-badge receive\">" + g + "</span>\n");
        }
        out.write("              </div>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Demonstration Contact Details (Private Notice)
        out.write("        <div class=\"profile-private-contact-box\">\n");
        out.write("          <div class=\"private-header\">\n");
        out.write("            <span class=\"lock-icon\">🔒</span>\n");
        out.write("            <strong>Confidential Contact Information (Private Profile Only)</strong>\n");
        out.write("          </div>\n");
        out.write("          <p class=\"private-desc\">This contact information is securely stored on this profile. It is <strong>never disclosed in public donor search results</strong>.</p>\n");
        out.write("          <div class=\"contact-pill\">\n");
        if (canEdit) {
            if (d.getContactInfo().isEmpty()) {
                out.write("            <span class=\"text-muted\">No optional contact information provided during registration.</span>\n");
            } else {
                out.write("            <span class=\"contact-val\">📞 " + escapeHtml(d.getContactInfo()) + "</span>\n");
            }
        } else {
            out.write("            <span class=\"text-muted\">🔒 Contact phone number is shielded to protect donor privacy. Emergency requests are coordinated through hospital triage.</span>\n");
        }
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Authenticated Self-Management Action (Issues 1 & 5)
        if (canEdit) {
            out.write("        <div class=\"profile-availability-manage-card\" style=\"background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 18px 22px; margin-top: 24px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 14px;\">\n");
            out.write("          <div>\n");
            out.write("            <div style=\"font-weight: 700; color: var(--text-white); font-size: 1rem;\">Donor Self-Management</div>\n");
            out.write("            <p style=\"color: var(--text-secondary); font-size: 0.88rem; margin: 4px 0 0;\">You are signed in as the authorized owner of this profile. You can update your availability anytime in your account.</p>\n");
            out.write("          </div>\n");
            out.write("          <a href=\"/account\" class=\"btn btn-secondary btn-sm\" style=\"white-space: nowrap;\">⚙️ Manage Availability in My Account →</a>\n");
            out.write("        </div>\n");
        }

        // Medical & Demonstration Disclaimer Card
        out.write("        <div class=\"demo-disclaimer-card\" style=\"margin-top: 24px;\">\n");
        out.write("          <span class=\"disclaimer-icon\">ℹ️</span>\n");
        out.write("          <div class=\"disclaimer-content\">\n");
        out.write("            <strong>Medical &amp; Transfusion Notice:</strong>\n");
        out.write("            <span>Voluntary registration does not replace standard hospital screening. Final donation requires official blood bank screening, haemoglobin evaluation, and laboratory cross-matching.</span>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Action Navigation Links
        out.write("        <div class=\"profile-footer-actions\" style=\"display: flex; gap: 12px; align-items: center; flex-wrap: wrap;\">\n");
        if (!canEdit) {
            out.write("          <a href=\"/messages?start=true&donorId=" + URLEncoder.encode(d.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-primary\">\n");
            out.write("            <span>💬 Chat with Donor</span>\n");
            out.write("          </a>\n");
        }
        out.write("          <a href=\"/dashboard\" class=\"btn btn-secondary\">\n");
        out.write("            <span>← Return to Dashboard</span>\n");
        out.write("          </a>\n");
        out.write("        </div>\n");

        out.write("      </article>\n");
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
        out.write("          <p class=\"footer-brand-desc\">" + AppConfig.TAGLINE + "<br>Community-focused voluntary blood donation and emergency coordination.</p>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Navigation</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"/dashboard\">Home Dashboard</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Find Donors</a></li>\n");
        out.write("            <li><a href=\"/become-donor\">Become a Donor</a></li>\n");
        out.write("            <li><a href=\"/request-blood\">Request Blood</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("      <div class=\"footer-bottom\">\n");
        out.write("        <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
        out.write("</body>\n</html>\n");
    }

    private void renderNotFoundPage(PrintWriter out, String requestedId, int unreadCount, boolean isAdmin, User currentUser) {
        List<Donor> allRegistered = donorService.getRegisteredDonors();

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Donor Profile Not Found | Blood Buddy</title>\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");
        out.write("  <header class=\"header\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\">\n");
        out.write("        <div class=\"brand-logo-wrap\"><img src=\"/static/images/logo.svg\" alt=\"Blood Buddy\" width=\"38\" height=\"38\"></div>\n");
        out.write("        <div class=\"brand-text\"><span class=\"brand-name\">" + AppConfig.APP_NAME + "</span></div>\n");
        out.write("      </a>\n");
        out.write("      <nav><ul class=\"nav-links\">\n");
        out.write("        <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("        <li><a href=\"/find-donors\" class=\"nav-link\">Find Donors</a></li>\n");
        out.write("        <li><a href=\"/camps\" class=\"nav-link\">Camps</a></li>\n");
        out.write("        <li><a href=\"/become-donor\" class=\"nav-link\">Become a Donor</a></li>\n");
        out.write("        <li><a href=\"/request-blood\" class=\"nav-link\">Request Blood</a></li>\n");
        out.write("        <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
        out.write("      </ul></nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        out.write("        <a href=\"/notifications\" class=\"nav-utility-link\" title=\"View Notifications\">\n");
        out.write("          <span>🔔 Alerts" + (unreadCount > 0 ? " <span class=\"notif-badge\">" + unreadCount + "</span>" : "") + "</span>\n");
        out.write("        </a>\n");
        if (currentUser != null) {
            out.write("        <a href=\"/account\" class=\"btn btn-secondary btn-sm\" title=\"Account Profile\">👤 " + escapeHtml(currentUser.getFullName()) + "</a>\n");
            if (isAdmin) {
                out.write("        <a href=\"/admin/emergency\" class=\"btn btn-secondary btn-sm\" style=\"color: var(--ruby-red);\" title=\"Hospital Staff Control Center\">🚨 Control</a>\n");
            }
            out.write("        <a href=\"/logout\" class=\"btn btn-secondary btn-sm\">Sign Out</a>\n");
        } else {
            out.write("        <a href=\"/login\" class=\"btn btn-primary btn-sm\">Sign In</a>\n");
        }
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </header>\n");
        out.write("  <main class=\"profile-main\">\n");
        out.write("    <div class=\"container\">\n");
        out.write("      <div class=\"empty-state-card\" style=\"margin-top: 40px;\">\n");
        out.write("        <div class=\"empty-icon\">🔍</div>\n");
        out.write("        <h2 class=\"empty-title\">Donor Profile Not Found</h2>\n");
        out.write("        <p class=\"empty-desc\">No registered donor matches profile identifier: <strong>\"" + escapeHtml(requestedId != null ? requestedId : "N/A") + "\"</strong>.<br>It may not exist or has not been registered yet.</p>\n");
        out.write("        <div class=\"empty-actions\">\n");
        out.write("          <a href=\"/become-donor\" class=\"btn btn-primary\"><span>Register as a Donor</span><span class=\"btn-icon\">❤️</span></a>\n");
        out.write("          <a href=\"/find-donors\" class=\"btn btn-secondary\"><span>Search Donors</span><span class=\"btn-icon\">🔍</span></a>\n");
        out.write("        </div>\n");

        if (!allRegistered.isEmpty()) {
            out.write("        <div style=\"margin-top: 36px; text-align: left; border-top: 1px solid var(--border-glass); padding-top: 24px;\">\n");
            out.write("          <h4 style=\"color: var(--text-primary); margin-bottom: 12px;\">Currently Registered Profiles:</h4>\n");
            out.write("          <ul style=\"list-style: none; display: flex; flex-direction: column; gap: 8px;\">\n");
            for (Donor d : allRegistered) {
                out.write("            <li><a href=\"/donor-profile?id=" + d.getId() + "\" style=\"color: var(--icy-blue); text-decoration: underline;\">" + d.getId() + " — " + escapeHtml(d.getName()) + " (" + d.getBloodGroup() + ", " + escapeHtml(d.getLocality()) + ")</a></li>\n");
            }
            out.write("          </ul>\n");
            out.write("        </div>\n");
        }

        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");
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

package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.service.BloodRequestService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.NotificationService;

/**
 * RequestConfirmationServlet displays the post-submission confirmation screen
 * with unique Request ID, details summary, and safety notices.
 */
public class RequestConfirmationServlet extends HttpServlet {

    private final BloodRequestService requestService = new BloodRequestService();
    private final NotificationService notificationService = new NotificationService();
    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        String id = req.getParameter("id");
        Optional<BloodRequest> reqOpt = (id != null && !id.trim().isEmpty())
                ? requestService.getRequestById(id.trim())
                : Optional.empty();

        try (PrintWriter out = resp.getWriter()) {
            if (reqOpt.isPresent()) {
                renderConfirmation(out, reqOpt.get(), unreadCount, isAdmin, currentUser);
            } else {
                renderNotFound(out, id, unreadCount, isAdmin, currentUser);
            }
        }
    }

    private void renderConfirmation(PrintWriter out, BloodRequest r, int unreadCount, boolean isAdmin, User currentUser) {
        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Request Confirmed: " + escapeHtml(r.getId()) + " | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Blood requisition confirmation for " + escapeHtml(r.getId()) + "\">\n");
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
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link active-nav\">View Requests</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
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

        // Confirmation Card
        out.write("      <article class=\"confirmation-card-container\">\n");
        out.write("        <div class=\"confirm-icon-wrap\">\n");
        out.write("          <div class=\"confirm-check-circle\">✓</div>\n");
        out.write("        </div>\n");
        out.write("        <span class=\"stage-badge stage-badge-urgent\" style=\"margin-bottom: 12px;\">Requisition Confirmed</span>\n");
        out.write("        <h1 class=\"confirm-title\">Blood Requisition Successfully Submitted!</h1>\n");
        out.write("        <p class=\"confirm-subtitle\">Your blood requisition has been recorded and posted to the community request board for donor coordination.</p>\n");

        out.write("        <div class=\"confirm-id-box\">\n");
        out.write("          <span class=\"confirm-id-label\">Assigned Request ID:</span>\n");
        out.write("          <span class=\"confirm-id-val\">" + escapeHtml(r.getId()) + "</span>\n");
        out.write("          <span class=\"status-pill status-pending\">Status: " + escapeHtml(r.getStatus()) + "</span>\n");
        out.write("        </div>\n");

        // Details Summary
        out.write("        <div class=\"confirm-summary-grid\">\n");
        out.write("          <div class=\"summary-item\">\n");
        out.write("            <span class=\"summary-label\">Patient Name</span>\n");
        out.write("            <span class=\"summary-val\">" + escapeHtml(r.getPatientName()) + "</span>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"summary-item\">\n");
        out.write("            <span class=\"summary-label\">Blood Group &amp; Quantity</span>\n");
        out.write("            <span class=\"summary-val highlight-red\">" + escapeHtml(r.getBloodGroup()) + " (" + r.getUnits() + " Unit" + (r.getUnits() > 1 ? "s" : "") + ")</span>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"summary-item\">\n");
        out.write("            <span class=\"summary-label\">Hospital &amp; Location</span>\n");
        out.write("            <span class=\"summary-val\">" + escapeHtml(r.getHospitalName()) + ", " + escapeHtml(r.getHospitalLocality()) + "</span>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"summary-item\">\n");
        out.write("            <span class=\"summary-label\">Triage Urgency</span>\n");
        out.write("            <span class=\"summary-val urgency-tag urgency-" + r.getUrgency().toLowerCase() + "\">" + escapeHtml(r.getUrgency()) + "</span>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"summary-item\">\n");
        out.write("            <span class=\"summary-label\">Required Deadline</span>\n");
        out.write("            <span class=\"summary-val\">🕒 " + escapeHtml(r.getRequiredBy()) + "</span>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"summary-item\">\n");
        out.write("            <span class=\"summary-label\">Contact Attendant</span>\n");
        out.write("            <span class=\"summary-val\">" + escapeHtml(r.getContactPerson()) + "</span>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Safety & Privacy Alerts
        out.write("        <div class=\"profile-private-contact-box\" style=\"text-align: left; margin: 24px 0;\">\n");
        out.write("          <div class=\"private-header\">\n");
        out.write("            <span class=\"lock-icon\">🔒</span>\n");
        out.write("            <strong>Privacy Shield Active</strong>\n");
        out.write("          </div>\n");
        out.write("          <p class=\"private-desc\">Patient phone numbers and sensitive contact numbers are securely recorded in the backend repository and will <strong>never be made visible on public request cards</strong>.</p>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"emergency-notice-card\" style=\"text-align: left; margin-bottom: 32px;\">\n");
        out.write("          <div class=\"notice-icon-box\">⚠️</div>\n");
        out.write("          <div class=\"notice-content\">\n");
        out.write("            <strong>Important Coordination Notice:</strong>\n");
        out.write("            <span>Submitting this requisition alerts available community donors and coordinators. It does not certify that units have been arranged or dispatched. For urgent clinical emergencies, please coordinate directly with the treating hospital blood bank or <strong>dial 108 immediately</strong>.</span>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Navigation Actions
        out.write("        <div class=\"confirm-actions-row\">\n");
        out.write("          <a href=\"/matching?requestId=" + URLEncoder.encode(r.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-primary\" style=\"padding: 13px 26px; background: var(--grad-blood);\">\n");
        out.write("            <span>View Smart Donor Matches</span>\n");
        out.write("            <span class=\"btn-icon\">→</span>\n");
        out.write("          </a>\n");
        out.write("          <a href=\"/view-requests\" class=\"btn btn-secondary\" style=\"padding: 13px 24px;\">\n");
        out.write("            <span>View Blood Requests Board</span>\n");
        out.write("            <span class=\"btn-icon\">📋</span>\n");
        out.write("          </a>\n");
        out.write("          <a href=\"/find-donors?bloodGroup=" + URLEncoder.encode(r.getBloodGroup(), StandardCharsets.UTF_8) + "\" class=\"btn btn-secondary\" style=\"padding: 13px 24px;\">\n");
        out.write("            <span>Find Compatible " + escapeHtml(r.getBloodGroup()) + " Donors</span>\n");
        out.write("            <span class=\"btn-icon\">🔍</span>\n");
        out.write("          </a>\n");
        out.write("          <a href=\"/request-blood\" class=\"btn btn-secondary\" style=\"padding: 13px 22px;\">\n");
        out.write("            <span>Submit Another Request</span>\n");
        out.write("            <span class=\"btn-icon\">+</span>\n");
        out.write("          </a>\n");
        out.write("          <a href=\"/dashboard\" class=\"btn btn-secondary\" style=\"padding: 13px 20px;\">\n");
        out.write("            <span>Home Dashboard</span>\n");
        out.write("          </a>\n");
        out.write("        </div>\n");

        out.write("      </article>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container\">\n");
        out.write("      <div class=\"footer-bottom\">\n");
        out.write("        <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
        out.write("</body>\n</html>\n");
    }

    private void renderNotFound(PrintWriter out, String id, int unreadCount, boolean isAdmin, User currentUser) {
        out.write("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>Request Not Found | Blood Buddy</title><link rel=\"stylesheet\" href=\"/static/css/style.css\"></head><body>\n");
        out.write("  <div class=\"container\" style=\"padding-top: 60px;\">\n");
        out.write("    <div class=\"empty-state-card\">\n");
        out.write("      <div class=\"empty-icon\">🔍</div>\n");
        out.write("      <h2 class=\"empty-title\">Blood Request Not Found</h2>\n");
        out.write("      <p class=\"empty-desc\">No request was found matching ID: <strong>" + escapeHtml(id != null ? id : "N/A") + "</strong>.</p>\n");
        out.write("      <div class=\"empty-actions\">\n");
        out.write("        <a href=\"/request-blood\" class=\"btn btn-primary\"><span>Submit a Blood Request</span><span class=\"btn-icon\">🚨</span></a>\n");
        out.write("        <a href=\"/view-requests\" class=\"btn btn-secondary\"><span>View All Requests</span><span class=\"btn-icon\">📋</span></a>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </div>\n</body></html>\n");
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

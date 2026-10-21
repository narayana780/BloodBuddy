package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.Camp;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.CampService;
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
import java.util.List;

/**
 * AdminCampsServlet provides authorized hospital coordinators and staff
 * with tools to publish new blood donation camps, update schedules, and manage events.
 * Strictly guarded against unauthorized public access.
 */
public class AdminCampsServlet extends HttpServlet {

    private final CampService campService = new CampService();
    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        if (!authService.isAdmin(req)) {
            resp.sendRedirect(req.getContextPath() + "/admin/login?redirect=/admin/camps");
            return;
        }

        User adminUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(true);
        List<Camp> camps = campService.getAllCamps();

        String flashMsg = req.getParameter("msg");
        String errorMsg = req.getParameter("error");

        try (PrintWriter out = resp.getWriter()) {
            renderAdminCampsPage(out, camps, flashMsg, errorMsg, adminUser, unreadCount);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        if (!authService.isAdmin(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Administrative privileges required to modify camp schedules.");
            return;
        }

        String action = req.getParameter("action");

        if ("create".equalsIgnoreCase(action)) {
            String name = req.getParameter("name");
            String organizer = req.getParameter("organizer");
            String contact = req.getParameter("organizerContact");
            String date = req.getParameter("date");
            String startTime = req.getParameter("startTime");
            String endTime = req.getParameter("endTime");
            String venue = req.getParameter("venue");
            String address = req.getParameter("address");
            String city = req.getParameter("city");
            String locality = req.getParameter("locality");
            String eligibility = req.getParameter("eligibilityInfo");
            String notes = req.getParameter("notes");
            String targetUnitsStr = req.getParameter("targetUnits");
            String maxCapStr = req.getParameter("maxCapacity");
            boolean regOpen = "true".equalsIgnoreCase(req.getParameter("registrationOpen"));

            if (name == null || name.trim().length() < 3 || organizer == null || organizer.trim().isEmpty() || date == null || date.trim().isEmpty()) {
                resp.sendRedirect(req.getContextPath() + "/admin/camps?error=" + URLEncoder.encode("Please fill in all mandatory camp details.", StandardCharsets.UTF_8));
                return;
            }

            int targetUnits = 50;
            int maxCap = 100;
            try {
                if (targetUnitsStr != null && !targetUnitsStr.trim().isEmpty()) {
                    targetUnits = Integer.parseInt(targetUnitsStr.trim());
                }
                if (maxCapStr != null && !maxCapStr.trim().isEmpty()) {
                    maxCap = Integer.parseInt(maxCapStr.trim());
                }
            } catch (NumberFormatException ignored) {
            }

            Camp created = campService.createCamp(
                    name.trim(), organizer.trim(), contact != null ? contact.trim() : "",
                    date.trim(), startTime != null ? startTime.trim() : "09:00 AM",
                    endTime != null ? endTime.trim() : "03:00 PM",
                    venue != null ? venue.trim() : "", address != null ? address.trim() : "",
                    city != null && !city.trim().isEmpty() ? city.trim() : "Rajahmundry",
                    locality != null ? locality.trim() : "Danavaipeta",
                    eligibility != null ? eligibility.trim() : "Standard voluntary blood donation clinical guidelines apply.",
                    targetUnits, maxCap, regOpen, notes != null ? notes.trim() : ""
            );

            resp.sendRedirect(req.getContextPath() + "/admin/camps?msg=" + URLEncoder.encode("New camp \"" + created.getName() + "\" published successfully!", StandardCharsets.UTF_8));
            return;
        }

        if ("updateStatus".equalsIgnoreCase(action)) {
            String campId = req.getParameter("campId");
            String newStatus = req.getParameter("status");
            if (campId != null && newStatus != null) {
                campService.updateCampStatus(campId.trim(), newStatus.trim());
            }
            resp.sendRedirect(req.getContextPath() + "/admin/camps?msg=" + URLEncoder.encode("Camp status updated.", StandardCharsets.UTF_8));
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/admin/camps");
    }

    private void renderAdminCampsPage(PrintWriter out, List<Camp> camps, String flashMsg, String errorMsg, User adminUser, int unreadCount) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Manage Blood Camps — Hospital Control | Blood Buddy</title>\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        // Admin Header
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\">\n");
        out.write("        <div class=\"brand-logo-wrap\"><img src=\"/static/images/logo.svg\" alt=\"Blood Buddy\" width=\"38\" height=\"38\"></div>\n");
        out.write("        <div class=\"brand-text\"><span class=\"brand-name\">" + AppConfig.APP_NAME + "</span><span class=\"brand-tagline-sub\">Staff Portal</span></div>\n");
        out.write("      </a>\n");
        out.write("      <nav><ul class=\"nav-links\">\n");
        out.write("        <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("        <li><a href=\"/camps\" class=\"nav-link\">Public Camps</a></li>\n");
        out.write("        <li><a href=\"/admin/emergency\" class=\"nav-link\" style=\"color: var(--ruby-red);\">🚨 Control Center</a></li>\n");
        out.write("        <li><a href=\"/admin/camps\" class=\"nav-link active-nav\">🏕️ Manage Camps</a></li>\n");
        out.write("        <li><a href=\"/admin/analytics\" class=\"nav-link\">📊 Analytics</a></li>\n");
        out.write("      </ul></nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        out.write("        <a href=\"/logout\" class=\"btn btn-secondary btn-sm\">Sign Out</a>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </header>\n");

        out.write("  <main class=\"container\" style=\"padding: 40px 20px 80px;\">\n");

        out.write("    <div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 14px; margin-bottom: 24px;\">\n");
        out.write("      <div>\n");
        out.write("        <h1 style=\"font-size: 2rem; color: var(--text-white); margin: 0 0 6px; font-family: var(--font-heading);\">Blood Donation Camps Coordinator</h1>\n");
        out.write("        <p style=\"color: var(--text-secondary); margin: 0;\">Authorize, publish, and schedule community blood donation events in Rajahmundry.</p>\n");
        out.write("      </div>\n");
        out.write("      <a href=\"#publishForm\" class=\"btn btn-primary\">+ Publish New Camp</a>\n");
        out.write("    </div>\n");

        if (flashMsg != null && !flashMsg.isEmpty()) {
            out.write("    <div class=\"alert alert-success\" style=\"background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.4); border-radius: var(--radius-sm); padding: 12px 16px; margin-bottom: 20px; color: #10b981;\">✓ " + escapeHtml(flashMsg) + "</div>\n");
        }
        if (errorMsg != null && !errorMsg.isEmpty()) {
            out.write("    <div class=\"alert alert-error\" style=\"background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.4); border-radius: var(--radius-sm); padding: 12px 16px; margin-bottom: 20px; color: #ef4444;\">⚠️ " + escapeHtml(errorMsg) + "</div>\n");
        }

        // Table of existing camps
        out.write("    <div class=\"glass-panel\" style=\"padding: 24px; border-radius: var(--radius-md); border: 1px solid var(--border-glass); margin-bottom: 40px; overflow-x: auto;\">\n");
        out.write("      <h3 style=\"margin-top: 0; color: var(--text-white);\">Published Camps (" + camps.size() + ")</h3>\n");
        out.write("      <table style=\"width: 100%; border-collapse: collapse; font-size: 0.9rem; color: var(--text-secondary); text-align: left;\">\n");
        out.write("        <thead>\n");
        out.write("          <tr style=\"border-bottom: 1.5px solid var(--border-glass); color: var(--text-white); font-weight: 700;\">\n");
        out.write("            <th style=\"padding: 12px 8px;\">ID</th>\n");
        out.write("            <th style=\"padding: 12px 8px;\">Camp Name &amp; Organizer</th>\n");
        out.write("            <th style=\"padding: 12px 8px;\">Date &amp; Time</th>\n");
        out.write("            <th style=\"padding: 12px 8px;\">Locality</th>\n");
        out.write("            <th style=\"padding: 12px 8px;\">Registrations</th>\n");
        out.write("            <th style=\"padding: 12px 8px;\">Status</th>\n");
        out.write("            <th style=\"padding: 12px 8px;\">Actions</th>\n");
        out.write("          </tr>\n");
        out.write("        </thead>\n");
        out.write("        <tbody>\n");

        for (Camp c : camps) {
            int regs = campService.getConfirmedCount(c.getId());
            out.write("          <tr style=\"border-bottom: 1px solid rgba(255, 255, 255, 0.05);\">\n");
            out.write("            <td style=\"padding: 12px 8px; font-weight: 700; color: var(--text-white);\">" + escapeHtml(c.getId()) + "</td>\n");
            out.write("            <td style=\"padding: 12px 8px;\"><strong>" + escapeHtml(c.getName()) + "</strong><br><span style=\"font-size: 0.8rem; color: var(--text-muted);\">" + escapeHtml(c.getOrganizer()) + "</span></td>\n");
            out.write("            <td style=\"padding: 12px 8px;\">" + escapeHtml(c.getFormattedDate()) + "<br><span style=\"font-size: 0.8rem;\">" + escapeHtml(c.getStartTime()) + " – " + escapeHtml(c.getEndTime()) + "</span></td>\n");
            out.write("            <td style=\"padding: 12px 8px;\">" + escapeHtml(c.getLocality()) + "</td>\n");
            out.write("            <td style=\"padding: 12px 8px;\"><strong>" + regs + "</strong> / " + c.getMaxCapacity() + "</td>\n");
            out.write("            <td style=\"padding: 12px 8px;\"><span style=\"font-weight: 600;\">" + escapeHtml(c.getComputedStatus()) + "</span></td>\n");
            out.write("            <td style=\"padding: 12px 8px;\">\n");
            out.write("              <form method=\"POST\" action=\"/admin/camps\" style=\"display: inline; margin: 0;\">\n");
            out.write("                <input type=\"hidden\" name=\"campId\" value=\"" + escapeHtml(c.getId()) + "\">\n");
            out.write("                <input type=\"hidden\" name=\"action\" value=\"updateStatus\">\n");
            out.write("                <select name=\"status\" onchange=\"this.form.submit()\" style=\"background: #111; color: #eee; border: 1px solid #444; border-radius: 4px; padding: 4px 8px; font-size: 0.8rem;\">\n");
            out.write("                  <option value=\"Upcoming\"" + ("Upcoming".equalsIgnoreCase(c.getStatus()) ? " selected" : "") + ">Upcoming</option>\n");
            out.write("                  <option value=\"Ongoing\"" + ("Ongoing".equalsIgnoreCase(c.getStatus()) ? " selected" : "") + ">Ongoing</option>\n");
            out.write("                  <option value=\"Completed\"" + ("Completed".equalsIgnoreCase(c.getStatus()) ? " selected" : "") + ">Completed</option>\n");
            out.write("                  <option value=\"Cancelled\"" + ("Cancelled".equalsIgnoreCase(c.getStatus()) ? " selected" : "") + ">Cancelled</option>\n");
            out.write("                </select>\n");
            out.write("              </form>\n");
            out.write("              <a href=\"/camps?id=" + escapeHtml(c.getId()) + "\" target=\"_blank\" style=\"color: var(--icy-blue); font-size: 0.82rem; margin-left: 8px;\">View ↗</a>\n");
            out.write("            </td>\n");
            out.write("          </tr>\n");
        }

        out.write("        </tbody>\n");
        out.write("      </table>\n");
        out.write("    </div>\n");

        // Publish Form
        out.write("    <div id=\"publishForm\" class=\"glass-panel\" style=\"padding: 32px; border-radius: var(--radius-md); border: 1px solid var(--border-glass); max-width: 760px;\">\n");
        out.write("      <h2 style=\"margin-top: 0; color: var(--text-white); font-family: var(--font-heading); font-size: 1.5rem;\">Publish New Blood Donation Camp</h2>\n");
        out.write("      <p style=\"color: var(--text-secondary); font-size: 0.9rem; margin-bottom: 24px;\">Authorized organizers can list community donation drives here. Published events will immediately appear on the public Camps directory.</p>\n");

        out.write("      <form method=\"POST\" action=\"/admin/camps\" style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 18px;\">\n");
        out.write("        <input type=\"hidden\" name=\"action\" value=\"create\">\n");

        out.write("        <div class=\"form-group\" style=\"grid-column: 1 / -1;\">\n");
        out.write("          <label class=\"form-label\">Camp Name *</label>\n");
        out.write("          <input type=\"text\" name=\"name\" required class=\"form-control\" placeholder=\"e.g. Rajahmundry Rotary Blood Drive\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Organizer / Institution *</label>\n");
        out.write("          <input type=\"text\" name=\"organizer\" required class=\"form-control\" placeholder=\"e.g. Rotary Club &amp; District Blood Bank\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Coordinator Contact Phone</label>\n");
        out.write("          <input type=\"text\" name=\"organizerContact\" class=\"form-control\" placeholder=\"e.g. 0883-2471200\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Date (YYYY-MM-DD) *</label>\n");
        out.write("          <input type=\"date\" name=\"date\" required class=\"form-control\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Hours</label>\n");
        out.write("          <div style=\"display: flex; gap: 8px;\">\n");
        out.write("            <input type=\"text\" name=\"startTime\" value=\"09:00 AM\" class=\"form-control\" placeholder=\"Start\">\n");
        out.write("            <input type=\"text\" name=\"endTime\" value=\"03:00 PM\" class=\"form-control\" placeholder=\"End\">\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Venue / Facility *</label>\n");
        out.write("          <input type=\"text\" name=\"venue\" required class=\"form-control\" placeholder=\"e.g. Anam Kala Kendram Auditorium\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Locality (Rajahmundry) *</label>\n");
        out.write("          <select name=\"locality\" class=\"form-control form-select\">\n");
        for (String loc : SeedDataProvider.getLocalities()) {
            out.write("            <option value=\"" + loc + "\">" + loc + "</option>\n");
        }
        out.write("          </select>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\" style=\"grid-column: 1 / -1;\">\n");
        out.write("          <label class=\"form-label\">Full Venue Address</label>\n");
        out.write("          <input type=\"text\" name=\"address\" class=\"form-control\" placeholder=\"e.g. Subbarao Street, Near Kotipalli Bus Stand, Rajahmundry\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Target Units (Goal)</label>\n");
        out.write("          <input type=\"number\" name=\"targetUnits\" value=\"100\" class=\"form-control\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\">\n");
        out.write("          <label class=\"form-label\">Maximum Capacity (Donor Slots)</label>\n");
        out.write("          <input type=\"number\" name=\"maxCapacity\" value=\"150\" class=\"form-control\">\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\" style=\"grid-column: 1 / -1;\">\n");
        out.write("          <label class=\"form-label\">Donor Eligibility &amp; Instructions</label>\n");
        out.write("          <textarea name=\"eligibilityInfo\" rows=\"2\" class=\"form-control\">Age 18-65 years, weight &gt; 45 kg, haemoglobin &gt;= 12.5 g/dL. Bring government photo ID and have a light meal prior to donation.</textarea>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"form-group\" style=\"grid-column: 1 / -1;\">\n");
        out.write("          <label class=\"form-label\">Special Amenities / Notes</label>\n");
        out.write("          <input type=\"text\" name=\"notes\" class=\"form-control\" placeholder=\"e.g. Donor certificate, refreshments, and free health checkup provided.\">\n");
        out.write("        </div>\n");

        out.write("        <div style=\"grid-column: 1 / -1; display: flex; align-items: center; gap: 10px;\">\n");
        out.write("          <input type=\"checkbox\" id=\"regOpen\" name=\"registrationOpen\" value=\"true\" checked>\n");
        out.write("          <label for=\"regOpen\" style=\"color: var(--text-white); font-weight: 600;\">Open Online Donor Slot Registration Immediately</label>\n");
        out.write("        </div>\n");

        out.write("        <div style=\"grid-column: 1 / -1; margin-top: 10px;\">\n");
        out.write("          <button type=\"submit\" class=\"btn btn-primary\" style=\"padding: 12px 28px; font-weight: 700;\">Publish Camp Schedule →</button>\n");
        out.write("        </div>\n");

        out.write("      </form>\n");
        out.write("    </div>\n");

        out.write("  </main>\n");

        out.write("</body>\n</html>");
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

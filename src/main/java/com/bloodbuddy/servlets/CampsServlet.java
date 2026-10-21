package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.Camp;
import com.bloodbuddy.model.CampRegistration;
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
import java.util.Optional;

/**
 * CampsServlet manages the public Blood Donation Camps directory,
 * search & filters, camp detail views, and user registrations.
 */
public class CampsServlet extends HttpServlet {

    private final CampService campService = new CampService();
    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        String path = req.getServletPath();
        String campId = req.getParameter("id");

        // If path is /camp-details or an ID is specified, show camp details
        if ("/camp-details".equalsIgnoreCase(path) || (campId != null && !campId.trim().isEmpty())) {
            try (PrintWriter out = resp.getWriter()) {
                renderCampDetailsView(out, req, resp, campId, currentUser, isAdmin, unreadCount);
            }
            return;
        }

        // Otherwise show camp listing directory
        String query = req.getParameter("query");
        if (query == null || query.trim().isEmpty()) {
            query = req.getParameter("q");
        }
        String city = req.getParameter("city");
        String locality = req.getParameter("locality");
        String status = req.getParameter("status");
        String dateFilter = req.getParameter("dateFilter");
        String dateFrom = req.getParameter("dateFrom");
        String dateTo = req.getParameter("dateTo");

        List<Camp> camps = campService.searchCamps(query, city, locality, status, dateFilter, dateFrom, dateTo);

        try (PrintWriter out = resp.getWriter()) {
            renderDirectoryView(out, query, city, locality, status, dateFilter, dateFrom, dateTo, camps, currentUser, isAdmin, unreadCount);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        User currentUser = authService.getCurrentUser(req);
        String campId = req.getParameter("campId");
        if (campId == null || campId.trim().isEmpty()) {
            campId = req.getParameter("id");
        }

        if (currentUser == null) {
            String target = "/camps" + (campId != null ? "?id=" + URLEncoder.encode(campId, StandardCharsets.UTF_8) : "");
            resp.sendRedirect(req.getContextPath() + "/login?redirect=" + URLEncoder.encode(target, StandardCharsets.UTF_8));
            return;
        }

        String action = req.getParameter("action");
        if ("cancel".equalsIgnoreCase(action)) {
            String regId = req.getParameter("regId");
            if (regId != null && !regId.trim().isEmpty()) {
                campService.cancelRegistration(regId.trim(), currentUser.getId());
            }
            resp.sendRedirect(req.getContextPath() + "/camps?id=" + URLEncoder.encode(campId != null ? campId : "", StandardCharsets.UTF_8) + "&cancelled=true");
            return;
        }

        String preferredSlot = req.getParameter("preferredSlot");
        String bloodGroup = req.getParameter("bloodGroup");

        CampService.RegistrationResult result = campService.registerUserForCamp(campId, currentUser, preferredSlot, bloodGroup);

        if (result.isSuccess()) {
            resp.sendRedirect(req.getContextPath() + "/camps?id=" + URLEncoder.encode(campId, StandardCharsets.UTF_8) + "&registered=true");
        } else {
            resp.sendRedirect(req.getContextPath() + "/camps?id=" + URLEncoder.encode(campId, StandardCharsets.UTF_8) + "&error=" + URLEncoder.encode(result.getMessage(), StandardCharsets.UTF_8));
        }
    }

    private void renderDirectoryView(PrintWriter out, String query, String city, String locality,
                                     String status, String dateFilter, String dateFrom, String dateTo, List<Camp> camps,
                                     User currentUser, boolean isAdmin, int unreadCount) {

        int upcomingCount = campService.getUpcomingCampsCount();
        int thisMonthCount = campService.getThisMonthCampsCount();
        int uniqueLocalities = campService.getUniqueActiveLocalitiesCount();

        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Blood Donation Camps — Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Find upcoming blood donation camps, plan your visit, and help save lives across Rajahmundry communities.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/camps", unreadCount, isAdmin, currentUser);

        out.write("  <main class=\"camps-main\">\n");

        // 1. Camps Hero Overview Section
        out.write("    <section class=\"camps-hero\" style=\"padding: 56px 0 36px; border-bottom: 1px solid var(--border-glass); background: radial-gradient(circle at 50% 20%, rgba(220, 20, 60, 0.08), transparent 70%);\">\n");
        out.write("      <div class=\"container\">\n");
        out.write("        <div style=\"max-width: 800px; margin: 0 auto; text-align: center;\">\n");
        out.write("          <div class=\"badge-accent\" style=\"background: rgba(220, 20, 60, 0.15); color: var(--neon-coral); border: 1px solid rgba(220, 20, 60, 0.3); padding: 4px 12px; border-radius: 9999px; display: inline-block; font-size: 0.82rem; font-weight: 700; margin-bottom: 14px;\">Community Donation Drives</div>\n");
        out.write("          <h1 style=\"font-size: 2.5rem; margin: 0 0 14px; color: var(--text-white); font-family: var(--font-heading); font-weight: 800;\">Blood Donation Camps</h1>\n");
        out.write("          <p style=\"font-size: 1.12rem; color: var(--text-secondary); line-height: 1.6; margin: 0 0 24px;\">Find upcoming blood donation camps, plan your visit, and help save lives in your community.</p>\n");
        out.write("          <div style=\"display: flex; gap: 14px; justify-content: center; flex-wrap: wrap;\">\n");
        out.write("            <a href=\"#camp-listings\" class=\"btn btn-primary\" style=\"padding: 12px 28px; font-weight: 700;\">Explore Camps ↓</a>\n");
        if (isAdmin) {
            out.write("            <a href=\"/admin/camps\" class=\"btn btn-secondary\" style=\"padding: 12px 24px;\">⚙️ Manage Camps (Staff)</a>\n");
        }
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Summary Metric Cards (Calculated strictly from actual records)
        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 18px; max-width: 900px; margin: 40px auto 0;\">\n");
        out.write("          <div class=\"glass-panel\" style=\"padding: 22px 20px; border-radius: var(--radius-md); text-align: center; border: 1px solid var(--border-glass);\">\n");
        out.write("            <div style=\"font-size: 2rem; font-weight: 800; color: var(--neon-coral); font-family: var(--font-heading);\">" + upcomingCount + "</div>\n");
        out.write("            <div style=\"font-size: 0.95rem; font-weight: 700; color: var(--text-white); margin-top: 4px;\">Upcoming Camps</div>\n");
        out.write("            <div style=\"font-size: 0.8rem; color: var(--text-muted); margin-top: 2px;\">Active donation schedules</div>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"glass-panel\" style=\"padding: 22px 20px; border-radius: var(--radius-md); text-align: center; border: 1px solid var(--border-glass);\">\n");
        out.write("            <div style=\"font-size: 2rem; font-weight: 800; color: var(--mint); font-family: var(--font-heading);\">" + thisMonthCount + "</div>\n");
        out.write("            <div style=\"font-size: 0.95rem; font-weight: 700; color: var(--text-white); margin-top: 4px;\">This Month</div>\n");
        out.write("            <div style=\"font-size: 0.8rem; color: var(--text-muted); margin-top: 2px;\">Current calendar month</div>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"glass-panel\" style=\"padding: 22px 20px; border-radius: var(--radius-md); text-align: center; border: 1px solid var(--border-glass);\">\n");
        out.write("            <div style=\"font-size: 2rem; font-weight: 800; color: var(--icy-blue); font-family: var(--font-heading);\">" + uniqueLocalities + "</div>\n");
        out.write("            <div style=\"font-size: 0.95rem; font-weight: 700; color: var(--text-white); margin-top: 4px;\">Nearby Locations</div>\n");
        out.write("            <div style=\"font-size: 0.8rem; color: var(--text-muted); margin-top: 2px;\">Regional hosting zones</div>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </section>\n");

        // 2. Search and Filters Section
        out.write("    <section class=\"container\" id=\"camp-listings\" style=\"padding-top: 36px; padding-bottom: 60px;\">\n");
        out.write("      <div class=\"search-filter-card glass-panel\" style=\"padding: 24px; border-radius: var(--radius-md); margin-bottom: 32px; border: 1px solid var(--border-glass);\">\n");
        out.write("        <form method=\"GET\" action=\"/camps\" style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; align-items: flex-end;\">\n");

        // Search text
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 0;\">\n");
        out.write("            <label for=\"campQuery\" class=\"form-label\" style=\"font-size: 0.85rem; font-weight: 600;\">Search Camps</label>\n");
        out.write("            <input type=\"text\" id=\"campQuery\" name=\"query\" class=\"form-control\" placeholder=\"Camp name or organizer...\" value=\"" + escapeHtml(query != null ? query : "") + "\">\n");
        out.write("          </div>\n");

        // Locality filter
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 0;\">\n");
        out.write("            <label for=\"campLocality\" class=\"form-label\" style=\"font-size: 0.85rem; font-weight: 600;\">Locality</label>\n");
        out.write("            <select id=\"campLocality\" name=\"locality\" class=\"form-control form-select\">\n");
        out.write("              <option value=\"ALL\"" + (locality == null || "ALL".equalsIgnoreCase(locality) ? " selected" : "") + ">All Localities</option>\n");
        for (String loc : SeedDataProvider.getLocalities()) {
            boolean sel = loc.equalsIgnoreCase(locality);
            out.write("              <option value=\"" + loc + "\"" + (sel ? " selected" : "") + ">📍 " + loc + "</option>\n");
        }
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Status filter
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 0;\">\n");
        out.write("            <label for=\"campStatus\" class=\"form-label\" style=\"font-size: 0.85rem; font-weight: 600;\">Camp Status</label>\n");
        out.write("            <select id=\"campStatus\" name=\"status\" class=\"form-control form-select\">\n");
        out.write("              <option value=\"ALL\"" + (status == null || "ALL".equalsIgnoreCase(status) ? " selected" : "") + ">All Statuses</option>\n");
        out.write("              <option value=\"Upcoming\"" + ("Upcoming".equalsIgnoreCase(status) ? " selected" : "") + ">🟢 Upcoming</option>\n");
        out.write("              <option value=\"Ongoing\"" + ("Ongoing".equalsIgnoreCase(status) ? " selected" : "") + ">⚡ Ongoing Today</option>\n");
        out.write("              <option value=\"Completed\"" + ("Completed".equalsIgnoreCase(status) ? " selected" : "") + ">⚪ Completed</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Date range from
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 0;\">\n");
        out.write("            <label for=\"dateFrom\" class=\"form-label\" style=\"font-size: 0.85rem; font-weight: 600;\">Date From</label>\n");
        out.write("            <input type=\"date\" id=\"dateFrom\" name=\"dateFrom\" class=\"form-control\" value=\"" + escapeHtml(dateFrom != null ? dateFrom : "") + "\">\n");
        out.write("          </div>\n");

        // Date range to
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 0;\">\n");
        out.write("            <label for=\"dateTo\" class=\"form-label\" style=\"font-size: 0.85rem; font-weight: 600;\">Date To</label>\n");
        out.write("            <input type=\"date\" id=\"dateTo\" name=\"dateTo\" class=\"form-control\" value=\"" + escapeHtml(dateTo != null ? dateTo : "") + "\">\n");
        out.write("          </div>\n");

        // Buttons
        out.write("          <div style=\"display: flex; gap: 10px;\">\n");
        out.write("            <button type=\"submit\" class=\"btn btn-primary\" style=\"flex: 1;\">Search</button>\n");
        out.write("            <a href=\"/camps\" class=\"btn btn-secondary\" style=\"flex: 1; text-align: center;\">Reset</a>\n");
        out.write("          </div>\n");

        out.write("        </form>\n");
        out.write("      </div>\n");

        // 3. Camp Listings Grid
        if (camps.isEmpty()) {
            out.write("      <div class=\"empty-state-card\" style=\"padding: 48px 24px; text-align: center; border: 1px solid var(--border-glass); border-radius: var(--radius-md); background: rgba(0, 0, 0, 0.25);\">\n");
            out.write("        <div style=\"font-size: 3rem; margin-bottom: 12px;\">🏕️</div>\n");
            out.write("        <h3 style=\"font-size: 1.4rem; color: var(--text-white); margin: 0 0 8px; font-family: var(--font-heading);\">No camps match your selection</h3>\n");
            out.write("        <p style=\"color: var(--text-secondary); max-width: 520px; margin: 0 auto 20px; font-size: 0.95rem;\">No upcoming camps have been published yet. Please check again soon.</p>\n");
            out.write("        <a href=\"/camps\" class=\"btn btn-secondary\">Reset Filters ↺</a>\n");
            out.write("      </div>\n");
        } else {
            out.write("      <div style=\"display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 24px;\">\n");

            for (Camp c : camps) {
                String compStatus = c.getComputedStatus();
                String badgeStyle;
                String statusLabel;

                switch (compStatus) {
                    case "Ongoing":
                        badgeStyle = "background: rgba(0, 240, 255, 0.15); color: var(--icy-blue); border: 1px solid rgba(0, 240, 255, 0.4);";
                        statusLabel = "● Ongoing Today";
                        break;
                    case "Completed":
                        badgeStyle = "background: rgba(148, 163, 184, 0.15); color: #94a3b8; border: 1px solid rgba(148, 163, 184, 0.3);";
                        statusLabel = "✓ Completed";
                        break;
                    case "Cancelled":
                        badgeStyle = "background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.4);";
                        statusLabel = "✕ Cancelled";
                        break;
                    default:
                        badgeStyle = "background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.4);";
                        statusLabel = "● Upcoming";
                        break;
                }

                int confirmedCount = campService.getConfirmedCount(c.getId());
                boolean hasCapacity = c.getMaxCapacity() > 0;
                int spotsLeft = hasCapacity ? Math.max(0, c.getMaxCapacity() - confirmedCount) : -1;

                boolean userRegistered = currentUser != null && campService.getUserRegistration(c.getId(), currentUser.getId()).isPresent();

                out.write("        <article class=\"camp-card glass-panel\" style=\"border-radius: var(--radius-md); padding: 24px; border: 1px solid var(--border-glass); display: flex; flex-direction: column; justify-content: space-between; transition: transform 0.2s ease, border-color 0.2s ease;\">\n");

                out.write("          <div>\n");
                out.write("            <div style=\"display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; margin-bottom: 12px;\">\n");
                out.write("              <span style=\"display: inline-block; padding: 4px 10px; font-size: 0.75rem; font-weight: 700; border-radius: 9999px; text-transform: uppercase; letter-spacing: 0.05em; " + badgeStyle + "\">" + statusLabel + "</span>\n");
                out.write("              <span style=\"font-size: 0.8rem; color: var(--text-muted); font-weight: 600;\">" + escapeHtml(c.getId()) + "</span>\n");
                out.write("            </div>\n");

                out.write("            <h3 style=\"font-size: 1.25rem; margin: 0 0 8px; color: var(--text-white); font-family: var(--font-heading); font-weight: 700; line-height: 1.35;\">" + escapeHtml(c.getName()) + "</h3>\n");
                out.write("            <div style=\"color: var(--neon-coral); font-size: 0.88rem; font-weight: 600; margin-bottom: 14px;\">🏛️ " + escapeHtml(c.getOrganizer()) + "</div>\n");

                out.write("            <div style=\"display: flex; flex-direction: column; gap: 8px; font-size: 0.86rem; color: var(--text-secondary); margin-bottom: 18px;\">\n");
                out.write("              <div>📅 <strong>" + escapeHtml(c.getFormattedDate()) + "</strong> • " + escapeHtml(c.getStartTime()) + " – " + escapeHtml(c.getEndTime()) + "</div>\n");
                out.write("              <div>📍 " + escapeHtml(c.getVenue()) + ", " + escapeHtml(c.getLocality()) + ", " + escapeHtml(c.getCity()) + "</div>\n");
                if (!c.getOrganizerContact().isEmpty()) {
                    out.write("              <div>📞 Contact: " + escapeHtml(c.getOrganizerContact()) + "</div>\n");
                }
                out.write("            </div>\n");

                // Registration capacity indicator
                out.write("            <div style=\"background: rgba(0, 0, 0, 0.2); border-radius: var(--radius-sm); padding: 8px 12px; margin-bottom: 18px; font-size: 0.82rem;\">\n");
                if (userRegistered) {
                    out.write("              <span style=\"color: #10b981; font-weight: 600;\">✓ You are registered for this camp</span>\n");
                } else if (!c.isRegistrationOpen()) {
                    out.write("              <span style=\"color: var(--text-muted);\">Registration is currently closed</span>\n");
                } else if (hasCapacity) {
                    out.write("              <span style=\"color: var(--text-secondary);\">Available Spots: <strong style=\"color: var(--text-white);\">" + spotsLeft + "</strong> / " + c.getMaxCapacity() + "</span>\n");
                } else {
                    out.write("              <span style=\"color: #10b981;\">🟢 Open Registration — Walk-ins Welcome</span>\n");
                }
                out.write("            </div>\n");
                out.write("          </div>\n");

                // Actions Footer
                out.write("          <div style=\"display: flex; gap: 10px; margin-top: 10px; border-top: 1px solid var(--border-glass); padding-top: 16px;\">\n");
                out.write("            <a href=\"/camp-details?id=" + escapeHtml(c.getId()) + "\" class=\"btn btn-secondary\" style=\"flex: 1; text-align: center; font-size: 0.88rem;\">View Details</a>\n");
                if (c.isRegistrationOpen()) {
                    out.write("            <a href=\"/camp-details?id=" + escapeHtml(c.getId()) + "#register\" class=\"btn btn-primary\" style=\"flex: 1; text-align: center; font-size: 0.88rem;\">Register for Camp</a>\n");
                }
                out.write("          </div>\n");

                out.write("        </article>\n");
            }

            out.write("      </div>\n");
        }

        out.write("    </section>\n");
        out.write("  </main>\n");

        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderCampDetailsView(PrintWriter out, HttpServletRequest req, HttpServletResponse resp,
                                       String campId, User currentUser, boolean isAdmin, int unreadCount) {

        Optional<Camp> campOpt = (campId != null) ? campService.getCampById(campId.trim()) : Optional.empty();

        if (campOpt.isEmpty()) {
            renderNotFoundView(out, campId, currentUser, isAdmin, unreadCount);
            return;
        }

        Camp c = campOpt.get();
        String compStatus = c.getComputedStatus();
        boolean isRegistered = currentUser != null && campService.getUserRegistration(c.getId(), currentUser.getId()).isPresent();
        Optional<CampRegistration> userReg = isRegistered ? campService.getUserRegistration(c.getId(), currentUser.getId()) : Optional.empty();
        int confirmedCount = campService.getConfirmedCount(c.getId());

        boolean isJustRegistered = "true".equalsIgnoreCase(req.getParameter("registered"));
        boolean isJustCancelled = "true".equalsIgnoreCase(req.getParameter("cancelled"));
        String errorMsg = req.getParameter("error");

        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>" + escapeHtml(c.getName()) + " — Blood Donation Camp | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"" + escapeHtml(c.getName()) + " organized by " + escapeHtml(c.getOrganizer()) + " on " + escapeHtml(c.getFormattedDate()) + " in Rajahmundry.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/camps", unreadCount, isAdmin, currentUser);

        out.write("  <main class=\"camp-detail-main\" style=\"padding: 36px 0 60px;\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 860px;\">\n");

        // Back link
        out.write("      <div style=\"margin-bottom: 20px;\">\n");
        out.write("        <a href=\"/camps\" style=\"color: var(--text-secondary); text-decoration: none; font-size: 0.9rem;\">← Back to All Camps</a>\n");
        out.write("      </div>\n");

        // Alerts / Flashes
        if (isJustRegistered) {
            out.write("      <div class=\"alert alert-success\" style=\"background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.4); border-radius: var(--radius-sm); padding: 14px 18px; margin-bottom: 24px; color: #10b981;\">\n");
            out.write("        ✓ <strong>Registration Confirmed!</strong> Thank you for volunteering to donate blood. A notification has been sent to your account.\n");
            out.write("      </div>\n");
        }
        if (isJustCancelled) {
            out.write("      <div class=\"alert alert-info\" style=\"background: rgba(0, 240, 255, 0.1); border: 1px solid rgba(0, 240, 255, 0.3); border-radius: var(--radius-sm); padding: 14px 18px; margin-bottom: 24px; color: var(--icy-blue);\">\n");
            out.write("        ℹ️ Your registration for this camp has been cancelled.\n");
            out.write("      </div>\n");
        }
        if (errorMsg != null && !errorMsg.isEmpty()) {
            out.write("      <div class=\"alert alert-error\" style=\"background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.4); border-radius: var(--radius-sm); padding: 14px 18px; margin-bottom: 24px; color: #ef4444;\">\n");
            out.write("        ⚠️ " + escapeHtml(errorMsg) + "\n");
            out.write("      </div>\n");
        }

        // Camp Header Card
        out.write("      <article class=\"glass-panel\" style=\"padding: 36px 32px; border-radius: var(--radius-md); border: 1px solid var(--border-glass); margin-bottom: 28px;\">\n");

        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; flex-wrap: wrap; margin-bottom: 12px;\">\n");
        out.write("          <div class=\"stage-badge\" style=\"background: rgba(220, 20, 60, 0.15); color: var(--neon-coral); border: 1px solid rgba(220, 20, 60, 0.3);\">Camp ID: " + escapeHtml(c.getId()) + "</div>\n");
        out.write("          <span style=\"display: inline-block; padding: 6px 14px; font-size: 0.8rem; font-weight: 700; border-radius: 9999px;\">Status: " + escapeHtml(compStatus) + "</span>\n");
        out.write("        </div>\n");

        out.write("        <h1 style=\"font-size: 2.1rem; color: var(--text-white); margin: 0 0 8px; font-family: var(--font-heading); line-height: 1.3;\">" + escapeHtml(c.getName()) + "</h1>\n");
        out.write("        <div style=\"color: var(--neon-coral); font-size: 1.05rem; font-weight: 600; margin-bottom: 24px;\">Organized by: <strong>" + escapeHtml(c.getOrganizer()) + "</strong></div>\n");

        // Key Schedule & Location Grid
        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; background: rgba(0, 0, 0, 0.25); padding: 20px; border-radius: var(--radius-sm); border: 1px solid var(--border-glass); margin-bottom: 28px;\">\n");
        out.write("          <div>\n");
        out.write("            <div style=\"font-size: 0.8rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700; letter-spacing: 0.05em;\">Date &amp; Time</div>\n");
        out.write("            <div style=\"font-size: 1rem; color: var(--text-white); font-weight: 600; margin-top: 4px;\">📅 " + escapeHtml(c.getFormattedDate()) + "</div>\n");
        out.write("            <div style=\"font-size: 0.85rem; color: var(--text-secondary); margin-top: 2px;\">🕒 " + escapeHtml(c.getStartTime()) + " – " + escapeHtml(c.getEndTime()) + "</div>\n");
        out.write("          </div>\n");
        out.write("          <div>\n");
        out.write("            <div style=\"font-size: 0.8rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700; letter-spacing: 0.05em;\">Venue &amp; Address</div>\n");
        out.write("            <div style=\"font-size: 1rem; color: var(--text-white); font-weight: 600; margin-top: 4px;\">📍 " + escapeHtml(c.getVenue()) + "</div>\n");
        out.write("            <div style=\"font-size: 0.85rem; color: var(--text-secondary); margin-top: 2px;\">" + escapeHtml(c.getAddress()) + " (" + escapeHtml(c.getLocality()) + ")</div>\n");
        out.write("          </div>\n");
        if (!c.getOrganizerContact().isEmpty()) {
            out.write("          <div>\n");
            out.write("            <div style=\"font-size: 0.8rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700; letter-spacing: 0.05em;\">Coordinator Contact</div>\n");
            out.write("            <div style=\"font-size: 1rem; color: var(--text-white); font-weight: 600; margin-top: 4px;\">📞 " + escapeHtml(c.getOrganizerContact()) + "</div>\n");
            out.write("            <div style=\"font-size: 0.85rem; color: var(--text-secondary); margin-top: 2px;\">Public community coordination line</div>\n");
            out.write("          </div>\n");
        }
        out.write("        </div>\n");

        // Eligibility & Instructions Card
        out.write("        <div style=\"margin-bottom: 24px;\">\n");
        out.write("          <h3 style=\"font-size: 1.15rem; color: var(--text-white); margin: 0 0 8px;\">Donor Eligibility &amp; Instructions</h3>\n");
        out.write("          <p style=\"color: var(--text-secondary); line-height: 1.6; font-size: 0.95rem; margin: 0 0 12px;\">" + escapeHtml(c.getEligibilityInfo()) + "</p>\n");
        if (!c.getNotes().isEmpty()) {
            out.write("          <p style=\"color: var(--mint-light); font-size: 0.9rem; background: rgba(6, 214, 160, 0.08); border-left: 3px solid var(--mint); padding: 8px 12px; margin: 0;\">ℹ️ " + escapeHtml(c.getNotes()) + "</p>\n");
        }
        out.write("        </div>\n");

        // Mandatory Medical Disclaimer Notice
        out.write("        <div class=\"medical-notice-card\" style=\"background: rgba(220, 20, 60, 0.08); border-left: 3px solid var(--ruby-red); padding: 16px 20px; border-radius: var(--radius-sm); margin-bottom: 28px; display: flex; gap: 14px; align-items: flex-start;\">\n");
        out.write("          <span style=\"font-size: 1.5rem; line-height: 1;\">⚕️</span>\n");
        out.write("          <div>\n");
        out.write("            <div style=\"font-weight: 700; color: var(--text-white); font-size: 0.95rem; margin-bottom: 4px;\">Medical Transfusion &amp; Eligibility Notice</div>\n");
        out.write("            <div style=\"color: var(--text-secondary); font-size: 0.9rem; line-height: 1.5;\">Registering for a blood donation camp notifies the organizers of your interest and reserves your slot. Final donor eligibility is assessed by qualified medical staff based on health history, blood pressure, and haemoglobin screening. Registering does not imply or guarantee medical eligibility or a successful donation.</div>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Registration Section (#register)
        out.write("        <section id=\"register\" style=\"border-top: 1px solid var(--border-glass); padding-top: 24px;\">\n");

        if (isRegistered && userReg.isPresent()) {
            // Already registered view
            CampRegistration reg = userReg.get();
            out.write("          <div style=\"background: rgba(16, 185, 129, 0.08); border: 1.5px solid rgba(16, 185, 129, 0.35); border-radius: var(--radius-md); padding: 22px 24px;\">\n");
            out.write("            <div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px;\">\n");
            out.write("              <div>\n");
            out.write("                <div style=\"color: #10b981; font-weight: 700; font-size: 1.1rem;\">✓ You Are Registered for This Camp</div>\n");
            out.write("                <div style=\"color: var(--text-secondary); font-size: 0.9rem; margin-top: 4px;\">Registration ID: <strong style=\"color: var(--text-white);\">" + escapeHtml(reg.getId()) + "</strong> • Slot: <strong>" + escapeHtml(reg.getPreferredSlot()) + "</strong></div>\n");
            out.write("                <div style=\"color: var(--text-muted); font-size: 0.8rem; margin-top: 4px;\">Registered on: " + escapeHtml(reg.getRegisteredAt()) + "</div>\n");
            out.write("              </div>\n");
            out.write("              <form method=\"POST\" action=\"/camps\" style=\"margin: 0;\">\n");
            out.write("                <input type=\"hidden\" name=\"campId\" value=\"" + escapeHtml(c.getId()) + "\">\n");
            out.write("                <input type=\"hidden\" name=\"regId\" value=\"" + escapeHtml(reg.getId()) + "\">\n");
            out.write("                <input type=\"hidden\" name=\"action\" value=\"cancel\">\n");
            out.write("                <button type=\"submit\" class=\"btn btn-secondary btn-sm\" onclick=\"return confirm('Are you sure you want to cancel your camp registration?');\" style=\"color: #ef4444;\">Cancel Registration</button>\n");
            out.write("              </form>\n");
            out.write("            </div>\n");
            out.write("          </div>\n");
        } else if (!c.isRegistrationOpen()) {
            // Registration closed view
            out.write("          <div style=\"background: rgba(0, 0, 0, 0.2); border-radius: var(--radius-md); padding: 20px; text-align: center;\">\n");
            out.write("            <div style=\"font-size: 1.1rem; color: var(--text-white); font-weight: 600;\">Registration Closed</div>\n");
            out.write("            <p style=\"color: var(--text-secondary); font-size: 0.9rem; margin: 4px 0 0;\">Online registration for this camp is currently unavailable or the event has concluded.</p>\n");
            out.write("          </div>\n");
        } else if (currentUser == null) {
            // Logged-out view: prompts login
            String loginRedirect = "/camps?id=" + URLEncoder.encode(c.getId(), StandardCharsets.UTF_8);
            out.write("          <div style=\"background: rgba(220, 20, 60, 0.08); border: 1px solid rgba(220, 20, 60, 0.25); border-radius: var(--radius-md); padding: 24px; text-align: center;\">\n");
            out.write("            <h4 style=\"font-size: 1.2rem; color: var(--text-white); margin: 0 0 8px; font-family: var(--font-heading);\">Sign in to Register for this Camp</h4>\n");
            out.write("            <p style=\"color: var(--text-secondary); font-size: 0.92rem; margin: 0 0 16px;\">Use your shared Blood Buddy account to quickly reserve your preferred donation slot.</p>\n");
            out.write("            <a href=\"/login?redirect=" + URLEncoder.encode(loginRedirect, StandardCharsets.UTF_8) + "\" class=\"btn btn-primary\" style=\"padding: 10px 24px;\">Sign In to Register →</a>\n");
            out.write("          </div>\n");
        } else {
            // Logged-in registration form
            out.write("          <div>\n");
            out.write("            <h3 style=\"font-size: 1.2rem; color: var(--text-white); margin: 0 0 6px;\">Reserve Your Donation Slot</h3>\n");
            out.write("            <p style=\"color: var(--text-secondary); font-size: 0.9rem; margin: 0 0 16px;\">Registering under account: <strong>" + escapeHtml(currentUser.getFullName()) + "</strong> (" + escapeHtml(currentUser.getEmail()) + ")</p>\n");

            out.write("            <form method=\"POST\" action=\"/camps\" style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; align-items: flex-end;\">\n");
            out.write("              <input type=\"hidden\" name=\"campId\" value=\"" + escapeHtml(c.getId()) + "\">\n");

            out.write("              <div class=\"form-group\" style=\"margin-bottom: 0;\">\n");
            out.write("                <label for=\"preferredSlot\" class=\"form-label\" style=\"font-size: 0.88rem; font-weight: 600;\">Preferred Arrival Time</label>\n");
            out.write("                <select id=\"preferredSlot\" name=\"preferredSlot\" class=\"form-control form-select\">\n");
            out.write("                  <option value=\"Morning (09:00 AM - 12:00 PM)\">Morning (09:00 AM – 12:00 PM)</option>\n");
            out.write("                  <option value=\"Afternoon (12:00 PM - 03:00 PM)\">Afternoon (12:00 PM – 03:00 PM)</option>\n");
            out.write("                  <option value=\"Flexible Walk-In\">Flexible (Anytime during camp hours)</option>\n");
            out.write("                </select>\n");
            out.write("              </div>\n");

            out.write("              <div class=\"form-group\" style=\"margin-bottom: 0;\">\n");
            out.write("                <label for=\"donorGroup\" class=\"form-label\" style=\"font-size: 0.88rem; font-weight: 600;\">Your Blood Group (If known)</label>\n");
            out.write("                <select id=\"donorGroup\" name=\"bloodGroup\" class=\"form-control form-select\">\n");
            out.write("                  <option value=\"\">I don't know / Test on-site</option>\n");
            out.write("                  <option value=\"O+\">O+</option>\n");
            out.write("                  <option value=\"O-\">O- (Universal)</option>\n");
            out.write("                  <option value=\"A+\">A+</option>\n");
            out.write("                  <option value=\"A-\">A-</option>\n");
            out.write("                  <option value=\"B+\">B+</option>\n");
            out.write("                  <option value=\"B-\">B-</option>\n");
            out.write("                  <option value=\"AB+\">AB+</option>\n");
            out.write("                  <option value=\"AB-\">AB-</option>\n");
            out.write("                </select>\n");
            out.write("              </div>\n");

            out.write("              <div>\n");
            out.write("                <button type=\"submit\" class=\"btn btn-primary\" style=\"width: 100%; padding: 12px 20px; font-weight: 700;\">Confirm Camp Registration →</button>\n");
            out.write("              </div>\n");

            out.write("            </form>\n");
            out.write("          </div>\n");
        }

        out.write("        </section>\n");

        out.write("      </article>\n");

        out.write("    </div>\n");
        out.write("  </main>\n");

        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderNotFoundView(PrintWriter out, String requestedId, User currentUser, boolean isAdmin, int unreadCount) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Camp Not Found — Blood Buddy</title>\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");
        renderHeader(out, "/camps", unreadCount, isAdmin, currentUser);
        out.write("  <main class=\"container\" style=\"padding: 80px 20px; text-align: center;\">\n");
        out.write("    <div class=\"empty-state-card glass-panel\" style=\"max-width: 540px; margin: 0 auto; padding: 40px 30px; border-radius: var(--radius-md);\">\n");
        out.write("      <div style=\"font-size: 3rem; margin-bottom: 12px;\">🏕️</div>\n");
        out.write("      <h2 style=\"font-size: 1.8rem; color: var(--text-white); margin: 0 0 8px; font-family: var(--font-heading);\">Camp Not Found</h2>\n");
        out.write("      <p style=\"color: var(--text-secondary); margin: 0 0 24px;\">The blood donation camp identified by <strong>\"" + escapeHtml(requestedId != null ? requestedId : "") + "\"</strong> could not be located or has been archived.</p>\n");
        out.write("      <a href=\"/camps\" class=\"btn btn-primary\">Browse Published Camps →</a>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");
        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderHeader(PrintWriter out, String currentPath, int unreadCount, boolean isAdmin, User currentUser) {
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\">\n");
        out.write("          <img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Logo\" width=\"38\" height=\"38\">\n");
        out.write("        </div>\n");
        out.write("        <div class=\"brand-text\">\n");
        out.write("          <span class=\"brand-name\">" + AppConfig.APP_NAME + "</span>\n");
        out.write("          <span class=\"brand-tagline-sub\">" + AppConfig.TAGLINE + "</span>\n");
        out.write("        </div>\n");
        out.write("      </a>\n");
        out.write("      <nav aria-label=\"Main Navigation\">\n");
        out.write("        <ul class=\"nav-links\">\n");
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link" + ("/dashboard".equals(currentPath) ? " active-nav" : "") + "\">Home</a></li>\n");
        out.write("          <li><a href=\"/find-donors\" class=\"nav-link" + ("/find-donors".equals(currentPath) ? " active-nav" : "") + "\">Find Donors</a></li>\n");
        out.write("          <li><a href=\"/camps\" class=\"nav-link" + ("/camps".equals(currentPath) ? " active-nav" : "") + "\">Camps</a></li>\n");
        out.write("          <li><a href=\"/become-donor\" class=\"nav-link" + ("/become-donor".equals(currentPath) ? " active-nav" : "") + "\">Become a Donor</a></li>\n");
        out.write("          <li><a href=\"/request-blood\" class=\"nav-link" + ("/request-blood".equals(currentPath) ? " active-nav" : "") + "\">Request Blood</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link" + ("/view-requests".equals(currentPath) ? " active-nav" : "") + "\">View Requests</a></li>\n");
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
    }

    private void renderFooter(PrintWriter out) {
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
        out.write("            <li><a href=\"/camps\">Donation Camps</a></li>\n");
        out.write("            <li><a href=\"/become-donor\">Become a Donor</a></li>\n");
        out.write("            <li><a href=\"/request-blood\">Request Blood</a></li>\n");
        out.write("            <li><a href=\"/view-requests\">View Requests</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("      <div class=\"footer-bottom\">\n");
        out.write("        <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
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

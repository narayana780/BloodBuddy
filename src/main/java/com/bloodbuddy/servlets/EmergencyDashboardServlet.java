package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.EmergencyPrioritizationService;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencyFilter;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencyRequestItem;
import com.bloodbuddy.service.EmergencyPrioritizationService.EmergencySummary;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * EmergencyDashboardServlet renders the Emergency Control Center (Stage 9).
 * Admin-only command center providing real-time triage rankings, deadline countdowns,
 * overdue alerts, zero-match flags, and rapid clinical action routing.
 */
public class EmergencyDashboardServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final EmergencyPrioritizationService prioritizationService = new EmergencyPrioritizationService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        // 1. Guard with Administrative Authorization
        if (!authService.isAdmin(req)) {
            String redirectUrl = req.getContextPath() + "/admin/login?redirect="
                    + URLEncoder.encode("/admin/emergency", StandardCharsets.UTF_8);
            resp.sendRedirect(redirectUrl);
            return;
        }

        // 2. Optional Manual/Automated Trigger to Evaluate Alerts
        String refreshParam = req.getParameter("refresh");
        int newAlertsCount = 0;
        if ("true".equalsIgnoreCase(refreshParam) || "1".equals(refreshParam)) {
            newAlertsCount = prioritizationService.evaluateAndGenerateAlerts(LocalDateTime.now()).size();
        }

        int unreadCount = notificationService.getUnreadCount(true);

        // 3. Parse Filter Parameters
        EmergencyFilter filter = new EmergencyFilter();
        filter.setUrgency(req.getParameter("urgency"));
        filter.setDeadlineStatus(req.getParameter("deadlineStatus"));
        filter.setBloodGroup(req.getParameter("bloodGroup"));
        filter.setLocality(req.getParameter("locality"));
        filter.setMatchAvailability(req.getParameter("matchAvailability"));

        // 4. Compute Prioritized Items & Metrics
        EmergencySummary summary = prioritizationService.computeEmergencySummary();
        List<EmergencyRequestItem> filteredItems = prioritizationService.filterItems(summary.getPrioritizedItems(), filter);

        try (PrintWriter out = resp.getWriter()) {
            renderDashboardHtml(out, summary, filteredItems, filter, unreadCount, newAlertsCount);
        }
    }

    private void renderDashboardHtml(PrintWriter out, EmergencySummary summary,
                                     List<EmergencyRequestItem> items, EmergencyFilter filter,
                                     int unreadCount, int newAlertsCount) {

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Emergency Control Center — Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Real-time clinical triage, emergency deadline monitoring, and critical supply alerts.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");

        out.write("  <style>\n");
        out.write("    .emergency-card-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 16px; margin-bottom: 28px; }\n");
        out.write("    .emergency-stat-card { padding: 20px 22px; border-radius: var(--radius-md); border: 1px solid var(--border-glass); transition: transform 0.2s; position: relative; overflow: hidden; }\n");
        out.write("    .emergency-stat-card:hover { transform: translateY(-2px); }\n");
        out.write("    .pulse-glow-red { box-shadow: 0 0 25px rgba(217, 4, 41, 0.45); border-color: rgba(255, 87, 87, 0.6) !important; }\n");
        out.write("    .pulse-glow-amber { box-shadow: 0 0 25px rgba(255, 179, 71, 0.35); border-color: rgba(255, 179, 71, 0.5) !important; }\n");
        out.write("    .triage-badge { display: inline-flex; align-items: center; gap: 5px; padding: 4px 10px; border-radius: var(--radius-full); font-size: 0.76rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; }\n");
        out.write("    .triage-critical { background: rgba(217, 4, 41, 0.22); color: #FF4D6D; border: 1px solid rgba(255, 87, 87, 0.5); }\n");
        out.write("    .triage-urgent { background: rgba(255, 179, 71, 0.18); color: #FFB347; border: 1px solid rgba(255, 179, 71, 0.4); }\n");
        out.write("    .triage-normal { background: rgba(76, 201, 240, 0.15); color: var(--icy-blue-soft); border: 1px solid rgba(76, 201, 240, 0.35); }\n");
        out.write("    .tag-pill { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 0.72rem; font-weight: 700; margin-right: 4px; }\n");
        out.write("    .tag-overdue { background: #D90429; color: #fff; }\n");
        out.write("    .tag-duesoon { background: #FF5757; color: #fff; }\n");
        out.write("    .tag-nomatch { background: #E67E22; color: #fff; }\n");
        out.write("    .tag-critical { background: #7A0019; color: #FFB3BA; border: 1px solid #FF5757; }\n");
        out.write("    .countdown-overdue { color: #FF5757; font-weight: 700; animation: blink-subtle 2s infinite ease-in-out; }\n");
        out.write("    .countdown-duesoon { color: #FFB347; font-weight: 700; }\n");
        out.write("    .countdown-ontime { color: var(--mint-light); font-weight: 600; }\n");
        out.write("    @keyframes blink-subtle { 0%, 100% { opacity: 1; } 50% { opacity: 0.65; } }\n");
        out.write("  </style>\n");
        out.write("</head>\n<body>\n");

        // Header Navigation
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\"><img src=\"/static/images/logo.svg\" alt=\"Blood Buddy\" width=\"38\" height=\"38\"></div>\n");
        out.write("        <div class=\"brand-text\"><span class=\"brand-name\">" + AppConfig.APP_NAME + "</span><span class=\"brand-tagline-sub\">" + AppConfig.TAGLINE + "</span></div>\n");
        out.write("      </a>\n");
        out.write("      <nav aria-label=\"Main Navigation\">\n");
        out.write("        <ul class=\"nav-links\">\n");
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
        out.write("          <li><a href=\"/admin/emergency\" class=\"nav-link active-nav\" style=\"color: var(--ruby-red);\">🚨 Control Center</a></li>\n");
        out.write("          <li><a href=\"/admin/analytics\" class=\"nav-link\">📊 Analytics</a></li>\n");
        out.write("          <li><a href=\"/notifications\" class=\"nav-link\">🔔 Alerts " + (unreadCount > 0 ? "<span class=\"notif-badge\">" + unreadCount + "</span>" : "") + "</a></li>\n");
        out.write("          <li><a href=\"/admin/logout\" class=\"nav-link\" style=\"color: var(--text-muted);\">Logout</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        out.write("        <a href=\"/admin/emergency?refresh=true\" class=\"btn btn-primary btn-sm\">\n");
        out.write("          <span>⚡ Evaluate &amp; Sync Alerts</span>\n");
        out.write("        </a>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </header>\n");

        out.write("  <main class=\"request-board-main\">\n");
        out.write("    <div class=\"container\">\n");

        // Sync Feedback Banner if triggered
        if (newAlertsCount > 0) {
            out.write("      <div style=\"background: rgba(6, 214, 160, 0.15); border: 1px solid rgba(6, 214, 160, 0.4); border-radius: var(--radius-sm); padding: 12px 18px; margin-bottom: 22px; color: var(--mint-light); font-size: 0.9rem; display: flex; align-items: center; justify-content: space-between;\">\n");
            out.write("        <div>✓ <strong>Alert Synchronization Complete:</strong> " + newAlertsCount + " new emergency administrative notification(s) generated into the Notifications Center.</div>\n");
            out.write("        <a href=\"/notifications\" style=\"color: #fff; text-decoration: underline;\">View in Alerts &rarr;</a>\n");
            out.write("      </div>\n");
        }

        // Header Title Banner
        out.write("      <div class=\"request-header-section\" style=\"margin-bottom: 24px;\">\n");
        out.write("        <div class=\"stage-badge stage-badge-urgent\">Emergency Control Center</div>\n");
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 16px;\">\n");
        out.write("          <div>\n");
        out.write("            <h1 class=\"section-title\" style=\"margin-bottom: 6px;\">Emergency Control Center</h1>\n");
        out.write("            <p class=\"section-subtitle\" style=\"margin-bottom: 0;\">Active clinical requisition triage, deadline monitoring, and critical supply flags across Rajahmundry healthcare facilities.</p>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"status-pill\" style=\"background: rgba(255, 87, 87, 0.15); border-color: rgba(255, 87, 87, 0.4); color: var(--neon-coral);\">\n");
        out.write("            <span class=\"status-dot\" style=\"background: var(--neon-coral);\"></span>\n");
        out.write("            <span>Server Clock: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) + "</span>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Summary Metric Cards (Section 2)
        renderSummaryCards(out, summary);

        // Filter Controls Bar (Section 6)
        renderFilterBar(out, filter);

        // Prioritized Requisitions Board (Section 6 & 7)
        renderPrioritizedList(out, items);

        out.write("    </div>\n");
        out.write("  </main>\n");

        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Emergency Control Center • Calculated from real stored records. All rights reserved.</div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");

        out.write("</body>\n</html>\n");
    }

    private void renderSummaryCards(PrintWriter out, EmergencySummary s) {
        out.write("      <section class=\"emergency-card-grid\">\n");

        // 1. Critical Active
        String critGlow = s.getCriticalActiveCount() > 0 ? "pulse-glow-red" : "";
        renderStatCard(out, "Critical Demands", String.valueOf(s.getCriticalActiveCount()), "🚨",
                "High-priority surgical/ICU emergencies", "var(--neon-coral)", critGlow);

        // 2. Urgent Active
        renderStatCard(out, "Urgent Demands", String.valueOf(s.getUrgentActiveCount()), "⚠️",
                "Priority acute clinical transfusions", "#FFB347", "");

        // 3. Overdue Requisitions
        String overGlow = s.getOverdueCount() > 0 ? "pulse-glow-red" : "";
        renderStatCard(out, "Overdue Requisitions", String.valueOf(s.getOverdueCount()), "⏰",
                "Required deadline in past & active", "var(--ruby-red)", overGlow);

        // 4. Due Soon (Within 2 Hours)
        String dueGlow = s.getDueSoonCount() > 0 ? "pulse-glow-amber" : "";
        renderStatCard(out, "Due Soon (<2h)", String.valueOf(s.getDueSoonCount()), "⏳",
                "Approaching 2-hour deadline window", "var(--icy-blue)", dueGlow);

        // 5. Zero Donor Matches
        String noMatchGlow = s.getNoMatchesCount() > 0 ? "pulse-glow-amber" : "";
        renderStatCard(out, "Zero Matches", String.valueOf(s.getNoMatchesCount()), "🚫",
                "0 candidates in regional donor pool", "#FF9F43", noMatchGlow);

        // 6. Awaiting Admin Review
        renderStatCard(out, "Awaiting Review", String.valueOf(s.getAwaitingReviewCount()), "📋",
                "Active requests in Pending status", "var(--mint-light)", "");

        out.write("      </section>\n");
    }

    private void renderStatCard(PrintWriter out, String title, String val, String icon,
                                String sub, String valColor, String extraClass) {
        out.write("        <div class=\"emergency-stat-card glass-panel " + extraClass + "\">\n");
        out.write("          <div style=\"display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px;\">\n");
        out.write("            <span style=\"font-size: 0.78rem; font-weight: 700; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.04em;\">" + escapeHtml(title) + "</span>\n");
        out.write("            <span style=\"font-size: 1.25rem;\">" + icon + "</span>\n");
        out.write("          </div>\n");
        out.write("          <div style=\"font-size: 2.2rem; font-weight: 800; font-family: var(--font-heading); color: " + valColor + "; line-height: 1.1; margin-bottom: 4px;\">" + escapeHtml(val) + "</div>\n");
        out.write("          <div style=\"font-size: 0.76rem; color: var(--text-muted); line-height: 1.3;\">" + escapeHtml(sub) + "</div>\n");
        out.write("        </div>\n");
    }

    private void renderFilterBar(PrintWriter out, EmergencyFilter filter) {
        out.write("      <section class=\"glass-panel\" style=\"padding: 18px 22px; border-radius: var(--radius-md); margin-bottom: 24px; border: 1px solid var(--border-glass);\">\n");
        out.write("        <form method=\"GET\" action=\"/admin/emergency\" style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 12px; align-items: flex-end;\">\n");

        // Urgency Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"urgency\" style=\"font-size: 0.78rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Urgency Triage</label>\n");
        out.write("            <select name=\"urgency\" id=\"urgency\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">All Urgencies</option>\n");
        out.write("              <option value=\"Critical\" " + ("Critical".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">Critical Only</option>\n");
        out.write("              <option value=\"Urgent\" " + ("Urgent".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">Urgent Only</option>\n");
        out.write("              <option value=\"Normal\" " + ("Normal".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">Normal Only</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Deadline Status Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"deadlineStatus\" style=\"font-size: 0.78rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Deadline Status</label>\n");
        out.write("            <select name=\"deadlineStatus\" id=\"deadlineStatus\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getDeadlineStatus()) ? "selected" : "") + ">All Deadlines</option>\n");
        out.write("              <option value=\"OVERDUE\" " + ("OVERDUE".equalsIgnoreCase(filter.getDeadlineStatus()) ? "selected" : "") + ">⏰ Overdue Only</option>\n");
        out.write("              <option value=\"DUE_SOON\" " + ("DUE_SOON".equalsIgnoreCase(filter.getDeadlineStatus()) ? "selected" : "") + ">⏳ Due Soon (<2h)</option>\n");
        out.write("              <option value=\"ON_SCHEDULE\" " + ("ON_SCHEDULE".equalsIgnoreCase(filter.getDeadlineStatus()) ? "selected" : "") + ">On Schedule</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Blood Group Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"bloodGroup\" style=\"font-size: 0.78rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Blood Group</label>\n");
        out.write("            <select name=\"bloodGroup\" id=\"bloodGroup\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getBloodGroup()) ? "selected" : "") + ">All Blood Groups</option>\n");
        for (String g : BloodCompatibility.ALL_GROUPS) {
            out.write("              <option value=\"" + g + "\" " + (g.equalsIgnoreCase(filter.getBloodGroup()) ? "selected" : "") + ">" + g + "</option>\n");
        }
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Locality Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"locality\" style=\"font-size: 0.78rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Hospital Locality</label>\n");
        out.write("            <select name=\"locality\" id=\"locality\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getLocality()) ? "selected" : "") + ">All Localities</option>\n");
        for (String loc : SeedDataProvider.LOCALITIES) {
            out.write("              <option value=\"" + loc + "\" " + (loc.equalsIgnoreCase(filter.getLocality()) ? "selected" : "") + ">" + loc + "</option>\n");
        }
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Match Availability Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"matchAvailability\" style=\"font-size: 0.78rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Match Availability</label>\n");
        out.write("            <select name=\"matchAvailability\" id=\"matchAvailability\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getMatchAvailability()) ? "selected" : "") + ">All Matches</option>\n");
        out.write("              <option value=\"NO_MATCHES\" " + ("NO_MATCHES".equalsIgnoreCase(filter.getMatchAvailability()) ? "selected" : "") + ">🚫 Zero Matches (Review Needed)</option>\n");
        out.write("              <option value=\"HAS_MATCHES\" " + ("HAS_MATCHES".equalsIgnoreCase(filter.getMatchAvailability()) ? "selected" : "") + ">✓ Potential Donors Available</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        out.write("          <div style=\"display: flex; gap: 8px;\">\n");
        out.write("            <button type=\"submit\" class=\"btn btn-primary\" style=\"padding: 9px 16px; font-size: 0.85rem;\">\n");
        out.write("              <span>Filter</span>\n");
        out.write("            </button>\n");
        out.write("            <a href=\"/admin/emergency\" class=\"btn btn-secondary\" style=\"padding: 9px 12px; font-size: 0.85rem;\" title=\"Reset filters\">\n");
        out.write("              <span>↺</span>\n");
        out.write("            </a>\n");
        out.write("          </div>\n");
        out.write("        </form>\n");
        out.write("      </section>\n");
    }

    private void renderPrioritizedList(PrintWriter out, List<EmergencyRequestItem> items) {
        out.write("      <section class=\"emergency-list-section\" style=\"margin-bottom: 40px;\">\n");
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;\">\n");
        out.write("          <div>\n");
        out.write("            <h2 style=\"margin: 0; font-size: 1.35rem; font-family: var(--font-heading); color: var(--text-white);\">Prioritized Emergency Requisitions</h2>\n");
        out.write("            <p style=\"margin: 2px 0 0; font-size: 0.82rem; color: var(--text-muted);\">Ranked by urgency tier (Critical &gt; Urgent &gt; Normal), deadline expiration, approaching window, and match deficit.</p>\n");
        out.write("          </div>\n");
        out.write("          <div style=\"font-size: 0.85rem; color: var(--text-secondary);\">\n");
        out.write("            Showing <strong>" + items.size() + "</strong> Active Requisition" + (items.size() == 1 ? "" : "s") + "\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        if (items.isEmpty()) {
            out.write("        <div class=\"glass-panel\" style=\"padding: 48px 24px; text-align: center; border-radius: var(--radius-md);\">\n");
            out.write("          <div style=\"font-size: 2.5rem; margin-bottom: 12px;\">🛡️</div>\n");
            out.write("          <h3 style=\"margin: 0 0 6px; color: var(--text-white); font-size: 1.2rem;\">No Active Emergency Requisitions Matching Filter</h3>\n");
            out.write("          <p style=\"margin: 0; color: var(--text-muted); font-size: 0.88rem;\">All active clinical requisitions are either fulfilled, cancelled, or outside selected filter constraints.</p>\n");
            out.write("        </div>\n");
            out.write("      </section>\n");
            return;
        }

        out.write("        <div style=\"display: flex; flex-direction: column; gap: 14px;\">\n");

        int rankNumber = 1;
        for (EmergencyRequestItem item : items) {
            BloodRequest r = item.getRequest();
            String urgency = r.getUrgency() != null ? r.getUrgency().trim() : "Normal";

            String cardBorder = "var(--border-glass)";
            if (item.isOverdue() && "Critical".equalsIgnoreCase(urgency)) {
                cardBorder = "rgba(255, 87, 87, 0.7)";
            } else if (item.isOverdue()) {
                cardBorder = "rgba(255, 87, 87, 0.4)";
            } else if (item.isDueSoon()) {
                cardBorder = "rgba(255, 179, 71, 0.45)";
            }

            String countdownClass = item.isOverdue() ? "countdown-overdue" : (item.isDueSoon() ? "countdown-duesoon" : "countdown-ontime");

            out.write("          <article class=\"glass-panel\" style=\"padding: 20px 24px; border-radius: var(--radius-md); border: 1px solid " + cardBorder + "; position: relative;\">\n");

            out.write("            <div style=\"display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 14px; margin-bottom: 12px;\">\n");
            out.write("              <div style=\"display: flex; align-items: center; gap: 12px;\">\n");
            out.write("                <div style=\"width: 32px; height: 32px; border-radius: var(--radius-full); background: rgba(255,255,255,0.08); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 0.85rem; color: var(--text-secondary);\">\n");
            out.write("                  #" + rankNumber + "\n");
            out.write("                </div>\n");
            out.write("                <div>\n");
            out.write("                  <a href=\"/matching?requestId=" + r.getId() + "\" style=\"font-size: 1.15rem; font-weight: 700; color: var(--text-white); text-decoration: none; font-family: var(--font-heading);\">\n");
            out.write("                    " + r.getId() + "\n");
            out.write("                  </a>\n");
            out.write("                  <span style=\"margin-left: 8px; font-size: 0.82rem; color: var(--text-muted);\">Logged: " + escapeHtml(r.getCreatedAt()) + "</span>\n");
            out.write("                </div>\n");
            out.write("              </div>\n");

            // Triage Badge & Status
            out.write("              <div style=\"display: flex; align-items: center; gap: 8px; flex-wrap: wrap;\">\n");
            if ("Critical".equalsIgnoreCase(urgency)) {
                out.write("                <span class=\"triage-badge triage-critical\">🚨 Critical</span>\n");
            } else if ("Urgent".equalsIgnoreCase(urgency)) {
                out.write("                <span class=\"triage-badge triage-urgent\">⚠️ Urgent</span>\n");
            } else {
                out.write("                <span class=\"triage-badge triage-normal\">Normal</span>\n");
            }

            out.write("                <span class=\"status-pill\" style=\"padding: 4px 10px; font-size: 0.75rem;\">\n");
            out.write("                  <span class=\"status-dot\"></span>\n");
            out.write("                  <span>" + escapeHtml(r.getStatus()) + "</span>\n");
            out.write("                </span>\n");
            out.write("              </div>\n");
            out.write("            </div>\n");

            // Request Details Grid
            out.write("            <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; margin-bottom: 14px; background: rgba(0,0,0,0.22); padding: 14px 18px; border-radius: var(--radius-sm);\">\n");

            // Blood Group & Units
            out.write("              <div>\n");
            out.write("                <div style=\"font-size: 0.74rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700;\">Blood Group &amp; Demand</div>\n");
            out.write("                <div style=\"font-size: 1.25rem; font-weight: 800; color: var(--neon-coral); margin-top: 2px;\">\n");
            out.write("                  " + r.getBloodGroup() + " <span style=\"font-size: 0.88rem; font-weight: 600; color: var(--text-primary);\">(" + r.getUnits() + " unit" + (r.getUnits() == 1 ? "" : "s") + ")</span>\n");
            out.write("                </div>\n");
            out.write("              </div>\n");

            // Hospital Facility
            out.write("              <div>\n");
            out.write("                <div style=\"font-size: 0.74rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700;\">Hospital Facility</div>\n");
            out.write("                <div style=\"font-size: 0.92rem; font-weight: 600; color: var(--text-white); margin-top: 2px;\">" + escapeHtml(r.getHospitalName()) + "</div>\n");
            out.write("                <div style=\"font-size: 0.78rem; color: var(--text-secondary);\">" + escapeHtml(r.getHospitalLocality()) + "</div>\n");
            out.write("              </div>\n");

            // Deadline & Remaining Time
            out.write("              <div>\n");
            out.write("                <div style=\"font-size: 0.74rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700;\">Deadline Status</div>\n");
            out.write("                <div class=\"" + countdownClass + "\" style=\"font-size: 0.95rem; margin-top: 2px;\">" + escapeHtml(item.getTimeRemainingText()) + "</div>\n");
            out.write("                <div style=\"font-size: 0.78rem; color: var(--text-secondary);\">Required by: " + escapeHtml(r.getRequiredBy()) + "</div>\n");
            out.write("              </div>\n");

            // Match Availability
            out.write("              <div>\n");
            out.write("                <div style=\"font-size: 0.74rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700;\">Donor Match Availability</div>\n");
            if (item.isHasNoMatches()) {
                out.write("                <div style=\"font-size: 0.95rem; font-weight: 700; color: #FF9F43; margin-top: 2px;\">⚠️ 0 Matches in Pool</div>\n");
                out.write("                <div style=\"font-size: 0.76rem; color: var(--text-muted);\">Clinical coordinator outreach advised</div>\n");
            } else {
                out.write("                <div style=\"font-size: 0.95rem; font-weight: 700; color: var(--mint-light); margin-top: 2px;\">✓ " + item.getMatchCount() + " Donor Candidate" + (item.getMatchCount() == 1 ? "" : "s") + "</div>\n");
                out.write("                <div style=\"font-size: 0.76rem; color: var(--text-muted);\">Ranked via smart matching engine</div>\n");
            }
            out.write("              </div>\n");

            out.write("            </div>\n");

            // Prioritization Reason & Highlight Tags
            out.write("            <div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;\">\n");
            out.write("              <div style=\"display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-size: 0.8rem; color: var(--text-secondary);\">\n");
            for (String tag : item.getHighlightTags()) {
                if ("OVERDUE".equals(tag)) {
                    out.write("<span class=\"tag-pill tag-overdue\">OVERDUE</span>");
                } else if ("DUE_SOON".equals(tag)) {
                    out.write("<span class=\"tag-pill tag-duesoon\">DUE SOON</span>");
                } else if ("NO_MATCHES".equals(tag)) {
                    out.write("<span class=\"tag-pill tag-nomatch\">0 MATCHES</span>");
                } else if ("CRITICAL".equals(tag)) {
                    out.write("<span class=\"tag-pill tag-critical\">CRITICAL</span>");
                }
            }
            out.write("                <span>" + escapeHtml(item.getReasonForPrioritization()) + "</span>\n");
            out.write("              </div>\n");

            // Actions (Review matches & Update status modal/form)
            out.write("              <div style=\"display: flex; gap: 8px;\">\n");
            out.write("                <a href=\"/matching?requestId=" + r.getId() + "\" class=\"btn btn-secondary\" style=\"padding: 6px 14px; font-size: 0.82rem;\">\n");
            out.write("                  <span>🔍 Review Matches</span>\n");
            out.write("                </a>\n");

            // Rapid Status Update Form through existing Stage 6 workflow
            out.write("                <form method=\"POST\" action=\"/admin/update-request-status\" style=\"display: flex; gap: 4px; margin: 0;\">\n");
            out.write("                  <input type=\"hidden\" name=\"requestId\" value=\"" + r.getId() + "\">\n");
            out.write("                  <input type=\"hidden\" name=\"redirect\" value=\"/admin/emergency\">\n");
            out.write("                  <select name=\"status\" class=\"form-input\" style=\"padding: 5px 8px; font-size: 0.8rem; height: auto;\">\n");
            out.write("                    <option value=\"In Progress\" " + ("In Progress".equalsIgnoreCase(r.getStatus()) ? "selected" : "") + ">In Progress</option>\n");
            out.write("                    <option value=\"Fulfilled\" " + ("Fulfilled".equalsIgnoreCase(r.getStatus()) ? "selected" : "") + ">Fulfilled</option>\n");
            out.write("                    <option value=\"Cancelled\" " + ("Cancelled".equalsIgnoreCase(r.getStatus()) ? "selected" : "") + ">Cancelled</option>\n");
            out.write("                  </select>\n");
            out.write("                  <button type=\"submit\" class=\"btn btn-primary\" style=\"padding: 6px 12px; font-size: 0.82rem;\">\n");
            out.write("                    <span>Update</span>\n");
            out.write("                  </button>\n");
            out.write("                </form>\n");

            out.write("              </div>\n");
            out.write("            </div>\n");

            out.write("          </article>\n");
            rankNumber++;
        }

        out.write("        </div>\n");
        out.write("      </section>\n");
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

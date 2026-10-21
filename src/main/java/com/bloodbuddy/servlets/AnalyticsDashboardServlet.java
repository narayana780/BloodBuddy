package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.service.AnalyticsService;
import com.bloodbuddy.service.AnalyticsService.AnalyticsFilter;
import com.bloodbuddy.service.AnalyticsService.AnalyticsSummary;
import com.bloodbuddy.service.AuthService;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

/**
 * AnalyticsDashboardServlet renders the Blood Buddy Analytics & Reports Dashboard (Stage 8).
 * Admin-only intelligence portal providing 6 interactive charts, summary metrics,
 * actionable insights, date/triage filters, and CSV report export triggers.
 */
public class AnalyticsDashboardServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        // 1. Guard with Administrative Authorization (Section 9)
        if (!authService.isAdmin(req)) {
            String redirectUrl = req.getContextPath() + "/admin/login?redirect="
                    + URLEncoder.encode("/admin/analytics", StandardCharsets.UTF_8);
            resp.sendRedirect(redirectUrl);
            return;
        }

        int unreadCount = notificationService.getUnreadCount(true);

        // 2. Parse Filter Parameters
        AnalyticsFilter filter = new AnalyticsFilter();
        filter.setDatePreset(req.getParameter("preset"));
        filter.setDateField(req.getParameter("dateField"));
        filter.setBloodGroup(req.getParameter("bloodGroup"));
        filter.setLocality(req.getParameter("locality"));
        filter.setStatus(req.getParameter("status"));
        filter.setUrgency(req.getParameter("urgency"));

        String startStr = req.getParameter("startDate");
        String endStr = req.getParameter("endDate");
        if (startStr != null && !startStr.trim().isEmpty()) {
            try {
                filter.setCustomStartDate(LocalDate.parse(startStr.trim()));
            } catch (DateTimeParseException ignored) {
            }
        }
        if (endStr != null && !endStr.trim().isEmpty()) {
            try {
                filter.setCustomEndDate(LocalDate.parse(endStr.trim()));
            } catch (DateTimeParseException ignored) {
            }
        }

        // 3. Compute Metrics
        AnalyticsSummary summary = analyticsService.computeSummary(filter);

        try (PrintWriter out = resp.getWriter()) {
            renderDashboardHtml(out, summary, filter, unreadCount);
        }
    }

    private void renderDashboardHtml(PrintWriter out, AnalyticsSummary summary,
                                     AnalyticsFilter filter, int unreadCount) {

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Blood Buddy Analytics — Actionable Demand &amp; Donor Insights</title>\n");
        out.write("  <meta name=\"description\" content=\"Understand donor availability and blood demand through actionable insights in Rajahmundry.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");

        // Inline Print & Chart Stylesheet Enhancements
        out.write("  <style>\n");
        out.write("    @media print {\n");
        out.write("      body { background: #fff !important; color: #000 !important; }\n");
        out.write("      .header, .filter-bar-container, .export-actions-bar, .footer, .btn, .no-print { display: none !important; }\n");
        out.write("      .glass-panel, .analytics-stat-card, .chart-card { background: #fff !important; border: 1px solid #ccc !important; box-shadow: none !important; color: #000 !important; break-inside: avoid; }\n");
        out.write("      .section-title, h1, h2, h3, h4 { color: #000 !important; }\n");
        out.write("      .stat-val { color: #D90429 !important; text-shadow: none !important; }\n");
        out.write("      .print-header { display: block !important; margin-bottom: 20px; border-bottom: 2px solid #D90429; padding-bottom: 10px; }\n");
        out.write("    }\n");
        out.write("    .print-header { display: none; }\n");
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
        out.write("          <li><a href=\"/find-donors\" class=\"nav-link\">Find Donors</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
        out.write("          <li><a href=\"/admin/emergency\" class=\"nav-link\" style=\"color: var(--ruby-red);\">🚨 Control Center</a></li>\n");
        out.write("          <li><a href=\"/admin/analytics\" class=\"nav-link active-nav\">📊 Analytics</a></li>\n");
        out.write("          <li><a href=\"/notifications\" class=\"nav-link\">🔔 Alerts " + (unreadCount > 0 ? "<span class=\"notif-badge\">" + unreadCount + "</span>" : "") + "</a></li>\n");
        out.write("          <li><a href=\"/admin/logout\" class=\"nav-link\" style=\"color: var(--text-muted);\">Logout</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        out.write("        <button onclick=\"window.print()\" class=\"btn btn-secondary btn-sm\">\n");
        out.write("          <span>🖨️ Print Report</span>\n");
        out.write("        </button>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </header>\n");

        out.write("  <main class=\"request-board-main\">\n");
        out.write("    <div class=\"container\">\n");

        // Print header for physical browser print
        out.write("      <div class=\"print-header\">\n");
        out.write("        <h1 style=\"margin: 0; font-size: 1.8rem;\">Blood Buddy — Executive Analytics &amp; Reports</h1>\n");
        out.write("        <p style=\"margin: 4px 0 0; color: #555;\">Export generated on " + LocalDate.now() + " | Rajahmundry Healthcare Blood Network</p>\n");
        out.write("      </div>\n");

        // Dashboard Title Bar
        out.write("      <div class=\"request-header-section\" style=\"margin-bottom: 24px;\">\n");
        out.write("        <div class=\"stage-badge stage-badge-urgent\">Operational Analytics</div>\n");
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 16px;\">\n");
        out.write("          <div>\n");
        out.write("            <h1 class=\"section-title\" style=\"margin-bottom: 6px;\">Blood Buddy Analytics</h1>\n");
        out.write("            <p class=\"section-subtitle\" style=\"margin-bottom: 0;\">Understand donor availability and blood demand through actionable insights.</p>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"status-pill\" style=\"background: rgba(6, 214, 160, 0.15); border-color: rgba(6, 214, 160, 0.4); color: var(--mint-light);\">\n");
        out.write("            <span class=\"status-dot\"></span>\n");
        out.write("            <span>Live Data Sync: Actual Local Records</span>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Filter Controls Bar (Section 5)
        renderFilterControls(out, filter);

        // Actionable Insights Panel (Section 6)
        renderInsightsPanel(out, summary);

        // Summary Statistics Cards (Section 3)
        renderSummaryStatsGrid(out, summary);

        // Interactive Charts (Section 4)
        renderChartsGrid(out, summary);

        // CSV Export Actions Bar (Section 7)
        renderExportBar(out, filter);

        out.write("    </div>\n");
        out.write("  </main>\n");

        out.write("  <footer class=\"footer no-print\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Operational Analytics &amp; Reports. Calculated from real stored records. All rights reserved.</div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");

        // Client-Side Interactive JavaScript for Dynamic Filters & Chart Tooltips
        renderClientScript(out);

        out.write("</body>\n</html>\n");
    }

    private void renderFilterControls(PrintWriter out, AnalyticsFilter filter) {
        out.write("      <section class=\"filter-bar-container glass-panel no-print\" style=\"padding: 20px 24px; border-radius: var(--radius-md); margin-bottom: 28px; border: 1px solid var(--border-glass);\">\n");
        out.write("        <form method=\"GET\" action=\"/admin/analytics\" id=\"analyticsFilterForm\" style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; align-items: flex-end;\">\n");

        // Preset Date Range
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"preset\" style=\"font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Date Interval</label>\n");
        out.write("            <select name=\"preset\" id=\"preset\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getDatePreset()) ? "selected" : "") + ">All Time</option>\n");
        out.write("              <option value=\"7_DAYS\" " + ("7_DAYS".equalsIgnoreCase(filter.getDatePreset()) ? "selected" : "") + ">Last 7 Days</option>\n");
        out.write("              <option value=\"30_DAYS\" " + ("30_DAYS".equalsIgnoreCase(filter.getDatePreset()) ? "selected" : "") + ">Last 30 Days</option>\n");
        out.write("              <option value=\"CUSTOM\" " + ("CUSTOM".equalsIgnoreCase(filter.getDatePreset()) ? "selected" : "") + ">Custom Range...</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Date Field Basis
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"dateField\" style=\"font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Date Basis</label>\n");
        out.write("            <select name=\"dateField\" id=\"dateField\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"CREATED_AT\" " + ("CREATED_AT".equalsIgnoreCase(filter.getDateField()) ? "selected" : "") + ">Request Logged Date</option>\n");
        out.write("              <option value=\"REQUIRED_BY\" " + ("REQUIRED_BY".equalsIgnoreCase(filter.getDateField()) ? "selected" : "") + ">Required-by Date</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Blood Group Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"bloodGroup\" style=\"font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Blood Group</label>\n");
        out.write("            <select name=\"bloodGroup\" id=\"bloodGroup\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getBloodGroup()) ? "selected" : "") + ">All Blood Groups</option>\n");
        for (String g : BloodCompatibility.ALL_GROUPS) {
            out.write("              <option value=\"" + g + "\" " + (g.equalsIgnoreCase(filter.getBloodGroup()) ? "selected" : "") + ">" + g + "</option>\n");
        }
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Locality Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"locality\" style=\"font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Hospital Locality</label>\n");
        out.write("            <select name=\"locality\" id=\"locality\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getLocality()) ? "selected" : "") + ">All Localities</option>\n");
        for (String loc : SeedDataProvider.LOCALITIES) {
            out.write("              <option value=\"" + loc + "\" " + (loc.equalsIgnoreCase(filter.getLocality()) ? "selected" : "") + ">" + loc + "</option>\n");
        }
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Request Status Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"status\" style=\"font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Request Status</label>\n");
        out.write("            <select name=\"status\" id=\"status\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getStatus()) ? "selected" : "") + ">All Statuses</option>\n");
        out.write("              <option value=\"Pending\" " + ("Pending".equalsIgnoreCase(filter.getStatus()) ? "selected" : "") + ">Pending</option>\n");
        out.write("              <option value=\"In Progress\" " + ("In Progress".equalsIgnoreCase(filter.getStatus()) ? "selected" : "") + ">In Progress</option>\n");
        out.write("              <option value=\"Completed\" " + ("Completed".equalsIgnoreCase(filter.getStatus()) || "Fulfilled".equalsIgnoreCase(filter.getStatus()) ? "selected" : "") + ">Completed / Fulfilled</option>\n");
        out.write("              <option value=\"Cancelled\" " + ("Cancelled".equalsIgnoreCase(filter.getStatus()) ? "selected" : "") + ">Cancelled</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Urgency Filter
        out.write("          <div class=\"form-group\" style=\"margin: 0;\">\n");
        out.write("            <label for=\"urgency\" style=\"font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); margin-bottom: 4px; display: block;\">Urgency Triage</label>\n");
        out.write("            <select name=\"urgency\" id=\"urgency\" class=\"form-input\" style=\"padding: 8px 10px; font-size: 0.85rem;\">\n");
        out.write("              <option value=\"ALL\" " + ("ALL".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">All Urgencies</option>\n");
        out.write("              <option value=\"Normal\" " + ("Normal".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">Normal</option>\n");
        out.write("              <option value=\"Urgent\" " + ("Urgent".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">Urgent</option>\n");
        out.write("              <option value=\"Critical\" " + ("Critical".equalsIgnoreCase(filter.getUrgency()) ? "selected" : "") + ">Critical</option>\n");
        out.write("            </select>\n");
        out.write("          </div>\n");

        // Submit & Reset Buttons
        out.write("          <div style=\"display: flex; gap: 8px;\">\n");
        out.write("            <button type=\"submit\" class=\"btn btn-primary\" style=\"flex: 1; padding: 9px 14px; font-size: 0.85rem;\">\n");
        out.write("              <span>Apply</span>\n");
        out.write("            </button>\n");
        out.write("            <a href=\"/admin/analytics\" class=\"btn btn-secondary\" style=\"padding: 9px 12px; font-size: 0.85rem;\" title=\"Reset all filters\">\n");
        out.write("              <span>↺</span>\n");
        out.write("            </a>\n");
        out.write("          </div>\n");

        out.write("        </form>\n");
        out.write("      </section>\n");
    }

    private void renderInsightsPanel(PrintWriter out, AnalyticsSummary summary) {
        out.write("      <section class=\"insights-section glass-panel\" style=\"background: linear-gradient(135deg, rgba(28, 16, 54, 0.8) 0%, rgba(45, 18, 75, 0.7) 100%); border: 1px solid rgba(255, 87, 87, 0.3); border-radius: var(--radius-md); padding: 22px 28px; margin-bottom: 28px; box-shadow: var(--shadow-glow-card);\">\n");
        out.write("        <div style=\"display: flex; align-items: center; gap: 10px; margin-bottom: 12px;\">\n");
        out.write("          <span style=\"font-size: 1.4rem;\">💡</span>\n");
        out.write("          <h2 style=\"margin: 0; font-size: 1.25rem; font-family: var(--font-heading); color: var(--text-white);\">Actionable Insights &amp; Operational Observations</h2>\n");
        out.write("        </div>\n");

        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px;\">\n");
        for (String insight : summary.getActionableInsights()) {
            out.write("          <div style=\"background: rgba(255, 255, 255, 0.04); border-left: 3px solid var(--neon-coral); border-radius: 4px; padding: 12px 16px; font-size: 0.88rem; line-height: 1.5; color: var(--text-primary);\">\n");
            out.write("            " + escapeHtml(insight) + "\n");
            out.write("          </div>\n");
        }
        out.write("        </div>\n");
        out.write("      </section>\n");
    }

    private void renderSummaryStatsGrid(PrintWriter out, AnalyticsSummary s) {
        out.write("      <section class=\"summary-stats-section\" style=\"margin-bottom: 32px;\">\n");
        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px;\">\n");

        // Total Donors
        renderStatCard(out, "Registered Donors", String.valueOf(s.getTotalDonors()), "👥", "Total recorded lifesaver profiles", "var(--icy-blue-soft)");

        // Available Donors
        renderStatCard(out, "Available Lifesavers", String.valueOf(s.getAvailableDonors()), "💚", "Confirmed available for immediate matching", "var(--mint-light)");

        // Unavailable Donors
        renderStatCard(out, "Unavailable / Resting", String.valueOf(s.getUnavailableDonors()), "💤", "Resting interval or status standby", "var(--text-muted)");

        // Total Requisitions
        renderStatCard(out, "Total Requisitions", String.valueOf(s.getTotalRequests()), "📋", "Hospital blood unit demands logged", "var(--text-white)");

        // Pending Backlog
        renderStatCard(out, "Pending Requisitions", String.valueOf(s.getPendingRequests()), "⏳", "Awaiting donor coordination", "var(--neon-coral)");

        // In Progress
        renderStatCard(out, "In Progress", String.valueOf(s.getInProgressRequests()), "🔄", "Donors matched and active", "var(--icy-blue)");

        // Completed / Fulfilled
        renderStatCard(out, "Fulfilled Demands", String.valueOf(s.getCompletedRequests()), "✓", "Successfully provided transfusions", "var(--mint)");

        // Cancelled
        renderStatCard(out, "Cancelled Requisitions", String.valueOf(s.getCancelledRequests()), "✕", "Withdrawn or cancelled demands", "var(--text-muted)");

        // Urgent & Critical Triage
        renderStatCard(out, "Urgent / Critical", String.valueOf(s.getUrgentAndCriticalRequests()), "🚨", "High-priority clinical requisitions", "var(--ruby-red)");

        // Requisitions without potential matches
        renderStatCard(out, "Unmatched Demands", String.valueOf(s.getRequestsWithNoMatches()), "⚠️", "Zero candidates in current local pool", "#FFB347");

        // Completion Rate
        String rateText = String.format(Locale.ENGLISH, "%.1f%%", s.getCompletionRate());
        renderStatCard(out, "Fulfillment Rate", rateText, "📈", "Fulfilled vs active non-cancelled demands", "var(--mint-light)");

        out.write("        </div>\n");
        out.write("      </section>\n");
    }

    private void renderStatCard(PrintWriter out, String title, String value, String icon, String subtitle, String color) {
        out.write("          <div class=\"analytics-stat-card glass-panel\" style=\"padding: 20px 22px; border-radius: var(--radius-md); border: 1px solid var(--border-glass); transition: transform 0.2s;\">\n");
        out.write("            <div style=\"display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px;\">\n");
        out.write("              <span style=\"font-size: 0.8rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.04em;\">" + escapeHtml(title) + "</span>\n");
        out.write("              <span style=\"font-size: 1.3rem;\">" + icon + "</span>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"stat-val\" style=\"font-size: 2.2rem; font-weight: 800; font-family: var(--font-heading); color: " + color + "; line-height: 1.1; margin-bottom: 6px;\">" + escapeHtml(value) + "</div>\n");
        out.write("            <div style=\"font-size: 0.78rem; color: var(--text-muted); line-height: 1.4;\">" + escapeHtml(subtitle) + "</div>\n");
        out.write("          </div>\n");
    }

    private void renderChartsGrid(PrintWriter out, AnalyticsSummary s) {
        out.write("      <section class=\"charts-grid-section\" style=\"margin-bottom: 36px;\">\n");
        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(480px, 1fr)); gap: 24px;\">\n");

        // Chart A: Blood Group Distribution
        renderChartCard(out, "Blood Group Distribution (Donors)", "Donor pool counts across all eight blood groups", () -> {
            renderBarChart(out, s.getDonorBloodGroupCounts(), "Donors", "var(--grad-pink-crimson)", s.getTotalDonors());
        });

        // Chart B: Request Status Overview
        renderChartCard(out, "Request Status Overview", "Requisitions categorized by lifecycle status", () -> {
            renderStatusBars(out, s.getStatusCounts(), s.getTotalRequests());
        });

        // Chart C: Blood Demand by Group
        renderChartCard(out, "Blood Demand by Group (Requisitions)", "Requisition frequency and requested units by blood type", () -> {
            renderBarChart(out, s.getRequestBloodGroupCounts(), "Requests", "var(--grad-blood)", s.getTotalRequests());
        });

        // Chart D: Donor Availability Comparison
        renderChartCard(out, "Donor Availability Breakdown", "Active volunteer readiness comparison", () -> {
            renderAvailabilityComparison(out, s.getAvailableDonors(), s.getUnavailableDonors(), s.getTotalDonors());
        });

        // Chart E: Locality-Wise Demand
        renderChartCard(out, "Locality-Wise Demand (Hospital Facilities)", "Patient requisitions grouped by facility locality", () -> {
            renderLocalityBars(out, s.getLocalityDemandCounts(), s.getTotalRequests());
        });

        // Chart F: Request Urgency Triage
        renderChartCard(out, "Request Urgency Triage", "Triage breakdown across Normal, Urgent, and Critical requisitions", () -> {
            renderUrgencyBars(out, s.getUrgencyCounts(), s.getTotalRequests());
        });

        out.write("        </div>\n");
        out.write("      </section>\n");
    }

    private void renderChartCard(PrintWriter out, String title, String subtitle, Runnable chartRenderer) {
        out.write("        <article class=\"chart-card glass-panel\" style=\"padding: 24px 26px; border-radius: var(--radius-lg); border: 1px solid var(--border-glass);\">\n");
        out.write("          <div style=\"margin-bottom: 18px;\">\n");
        out.write("            <h3 style=\"margin: 0 0 4px; font-size: 1.15rem; font-family: var(--font-heading); color: var(--text-white);\">" + escapeHtml(title) + "</h3>\n");
        out.write("            <p style=\"margin: 0; font-size: 0.82rem; color: var(--text-muted);\">" + escapeHtml(subtitle) + "</p>\n");
        out.write("          </div>\n");
        chartRenderer.run();
        out.write("        </article>\n");
    }

    private void renderBarChart(PrintWriter out, Map<String, Integer> data, String unitLabel, String gradient, int total) {
        if (data.isEmpty() || total == 0) {
            renderEmptyChartState(out, "No record counts for current selection");
            return;
        }

        int maxVal = 1;
        for (int v : data.values()) {
            if (v > maxVal) maxVal = v;
        }

        out.write("          <div style=\"display: flex; flex-direction: column; gap: 10px;\">\n");
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            int count = e.getValue();
            double pct = ((double) count / maxVal) * 100.0;
            double sharePct = total > 0 ? ((double) count / total) * 100.0 : 0.0;

            out.write("            <div style=\"display: grid; grid-template-columns: 45px 1fr 70px; align-items: center; gap: 12px;\">\n");
            out.write("              <span style=\"font-weight: 700; color: var(--text-primary); font-size: 0.9rem;\">" + escapeHtml(e.getKey()) + "</span>\n");
            out.write("              <div style=\"background: rgba(255, 255, 255, 0.05); border-radius: 9999px; height: 16px; overflow: hidden;\">\n");
            out.write("                <div style=\"width: " + Math.max(2, pct) + "%; height: 100%; background: " + gradient + "; border-radius: 9999px; transition: width 0.6s ease;\"></div>\n");
            out.write("              </div>\n");
            out.write("              <div style=\"text-align: right; font-size: 0.82rem; color: var(--text-secondary);\">\n");
            out.write("                <strong>" + count + "</strong> <span style=\"color: var(--text-muted); font-size: 0.75rem;\">(" + String.format(Locale.ENGLISH, "%.0f%%", sharePct) + ")</span>\n");
            out.write("              </div>\n");
            out.write("            </div>\n");
        }
        out.write("          </div>\n");
    }

    private void renderStatusBars(PrintWriter out, Map<String, Integer> data, int total) {
        if (total == 0) {
            renderEmptyChartState(out, "Zero requisitions logged in current view");
            return;
        }

        out.write("          <div style=\"display: flex; flex-direction: column; gap: 14px;\">\n");
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            String status = e.getKey();
            int count = e.getValue();
            double pct = total > 0 ? ((double) count / total) * 100.0 : 0.0;

            String barColor;
            if ("Pending".equalsIgnoreCase(status)) barColor = "var(--neon-coral)";
            else if ("In Progress".equalsIgnoreCase(status)) barColor = "var(--icy-blue)";
            else if ("Completed".equalsIgnoreCase(status)) barColor = "var(--mint)";
            else barColor = "var(--text-muted)";

            out.write("            <div>\n");
            out.write("              <div style=\"display: flex; justify-content: space-between; margin-bottom: 4px; font-size: 0.85rem;\">\n");
            out.write("                <span style=\"color: var(--text-primary); font-weight: 600;\">" + escapeHtml(status) + "</span>\n");
            out.write("                <span style=\"color: var(--text-secondary);\">" + count + " Requisition" + (count == 1 ? "" : "s") + " (" + String.format(Locale.ENGLISH, "%.1f%%", pct) + ")</span>\n");
            out.write("              </div>\n");
            out.write("              <div style=\"background: rgba(255, 255, 255, 0.05); border-radius: 9999px; height: 14px; overflow: hidden;\">\n");
            out.write("                <div style=\"width: " + Math.max(1, pct) + "%; height: 100%; background: " + barColor + "; border-radius: 9999px; transition: width 0.6s ease;\"></div>\n");
            out.write("              </div>\n");
            out.write("            </div>\n");
        }
        out.write("          </div>\n");
    }

    private void renderAvailabilityComparison(PrintWriter out, int avail, int unavail, int total) {
        if (total == 0) {
            renderEmptyChartState(out, "No donor profiles found");
            return;
        }

        double availPct = ((double) avail / total) * 100.0;
        double unavailPct = ((double) unavail / total) * 100.0;

        out.write("          <div>\n");
        out.write("            <div style=\"display: flex; height: 28px; border-radius: var(--radius-sm); overflow: hidden; margin-bottom: 18px; border: 1px solid var(--border-glass);\">\n");
        out.write("              <div style=\"width: " + availPct + "%; background: var(--grad-cyan-mint); transition: width 0.6s;\" title=\"Available: " + avail + "\"></div>\n");
        out.write("              <div style=\"width: " + unavailPct + "%; background: rgba(255, 255, 255, 0.15); transition: width 0.6s;\" title=\"Standby/Resting: " + unavail + "\"></div>\n");
        out.write("            </div>\n");

        out.write("            <div style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 16px;\">\n");
        out.write("              <div style=\"background: rgba(6, 214, 160, 0.1); border: 1px solid rgba(6, 214, 160, 0.3); border-radius: var(--radius-sm); padding: 14px;\">\n");
        out.write("                <div style=\"font-size: 0.78rem; text-transform: uppercase; color: var(--mint-light); font-weight: 700;\">Active Available</div>\n");
        out.write("                <div style=\"font-size: 1.8rem; font-weight: 800; color: var(--text-white); font-family: var(--font-heading); margin: 4px 0;\">" + avail + "</div>\n");
        out.write("                <div style=\"font-size: 0.8rem; color: var(--text-secondary);\">" + String.format(Locale.ENGLISH, "%.1f%% of donor pool", availPct) + "</div>\n");
        out.write("              </div>\n");

        out.write("              <div style=\"background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border-glass); border-radius: var(--radius-sm); padding: 14px;\">\n");
        out.write("                <div style=\"font-size: 0.78rem; text-transform: uppercase; color: var(--text-muted); font-weight: 700;\">Standby / Resting</div>\n");
        out.write("                <div style=\"font-size: 1.8rem; font-weight: 800; color: var(--text-white); font-family: var(--font-heading); margin: 4px 0;\">" + unavail + "</div>\n");
        out.write("                <div style=\"font-size: 0.8rem; color: var(--text-secondary);\">" + String.format(Locale.ENGLISH, "%.1f%% resting interval", unavailPct) + "</div>\n");
        out.write("              </div>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
    }

    private void renderLocalityBars(PrintWriter out, Map<String, Integer> data, int total) {
        if (data.isEmpty() || total == 0) {
            renderEmptyChartState(out, "No hospital locality records for current view");
            return;
        }

        int maxVal = 1;
        for (int v : data.values()) {
            if (v > maxVal) maxVal = v;
        }

        out.write("          <div style=\"display: flex; flex-direction: column; gap: 10px;\">\n");
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            int count = e.getValue();
            double pct = ((double) count / maxVal) * 100.0;

            out.write("            <div style=\"display: grid; grid-template-columns: 140px 1fr 40px; align-items: center; gap: 10px;\">\n");
            out.write("              <span style=\"font-size: 0.82rem; color: var(--text-secondary); text-overflow: ellipsis; overflow: hidden; white-space: nowrap;\">📍 " + escapeHtml(e.getKey()) + "</span>\n");
            out.write("              <div style=\"background: rgba(255, 255, 255, 0.05); border-radius: 9999px; height: 12px; overflow: hidden;\">\n");
            out.write("                <div style=\"width: " + Math.max(2, pct) + "%; height: 100%; background: linear-gradient(90deg, #4CC9F0, #00F0FF); border-radius: 9999px; transition: width 0.6s ease;\"></div>\n");
            out.write("              </div>\n");
            out.write("              <span style=\"font-size: 0.82rem; font-weight: 700; color: var(--text-white); text-align: right;\">" + count + "</span>\n");
            out.write("            </div>\n");
        }
        out.write("          </div>\n");
    }

    private void renderUrgencyBars(PrintWriter out, Map<String, Integer> data, int total) {
        if (total == 0) {
            renderEmptyChartState(out, "Zero requisitions logged in current view");
            return;
        }

        out.write("          <div style=\"display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px;\">\n");
        for (Map.Entry<String, Integer> e : data.entrySet()) {
            String urgency = e.getKey();
            int count = e.getValue();
            double pct = total > 0 ? ((double) count / total) * 100.0 : 0.0;

            String borderColor;
            String textColor;
            String icon;

            if ("Critical".equalsIgnoreCase(urgency)) {
                borderColor = "rgba(217, 4, 41, 0.5)";
                textColor = "var(--ruby-red)";
                icon = "🚨";
            } else if ("Urgent".equalsIgnoreCase(urgency)) {
                borderColor = "rgba(255, 87, 87, 0.5)";
                textColor = "var(--neon-coral)";
                icon = "⚠️";
            } else {
                borderColor = "rgba(76, 201, 240, 0.4)";
                textColor = "var(--icy-blue-soft)";
                icon = "🕒";
            }

            out.write("            <div style=\"background: rgba(0, 0, 0, 0.25); border: 1px solid " + borderColor + "; border-radius: var(--radius-sm); padding: 16px; text-align: center;\">\n");
            out.write("              <div style=\"font-size: 1.5rem; margin-bottom: 6px;\">" + icon + "</div>\n");
            out.write("              <div style=\"font-size: 0.8rem; text-transform: uppercase; font-weight: 700; color: " + textColor + "; letter-spacing: 0.05em;\">" + escapeHtml(urgency) + "</div>\n");
            out.write("              <div style=\"font-size: 2rem; font-weight: 800; color: var(--text-white); font-family: var(--font-heading); margin: 4px 0;\">" + count + "</div>\n");
            out.write("              <div style=\"font-size: 0.78rem; color: var(--text-muted);\">" + String.format(Locale.ENGLISH, "%.1f%% of demands", pct) + "</div>\n");
            out.write("            </div>\n");
        }
        out.write("          </div>\n");
    }

    private void renderEmptyChartState(PrintWriter out, String message) {
        out.write("          <div style=\"padding: 30px 10px; text-align: center; color: var(--text-muted); font-size: 0.88rem;\">\n");
        out.write("            <div style=\"font-size: 1.8rem; margin-bottom: 6px;\">📊</div>\n");
        out.write("            <div>" + escapeHtml(message) + "</div>\n");
        out.write("          </div>\n");
    }

    private void renderExportBar(PrintWriter out, AnalyticsFilter filter) {
        String filterParams = buildExportQueryString(filter);

        out.write("      <section class=\"export-actions-bar glass-panel no-print\" style=\"padding: 24px 28px; border-radius: var(--radius-md); border: 1px solid var(--border-glass); margin-bottom: 40px;\">\n");
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px;\">\n");
        out.write("          <div>\n");
        out.write("            <h3 style=\"margin: 0 0 4px; font-size: 1.15rem; color: var(--text-white); font-family: var(--font-heading);\">📥 Export Reports &amp; Records (CSV)</h3>\n");
        out.write("            <p style=\"margin: 0; font-size: 0.82rem; color: var(--text-secondary);\">Formula-injection protected, UTF-8 encoded datasets generated on the Java backend.</p>\n");
        out.write("          </div>\n");
        out.write("          <div style=\"display: flex; gap: 10px; flex-wrap: wrap;\">\n");
        out.write("            <a href=\"/admin/analytics/export/requests?" + filterParams + "\" class=\"btn btn-secondary\" style=\"padding: 10px 18px; font-size: 0.85rem;\">\n");
        out.write("              <span>Requisitions CSV</span>\n");
        out.write("            </a>\n");
        out.write("            <a href=\"/admin/analytics/export/donors?" + filterParams + "\" class=\"btn btn-secondary\" style=\"padding: 10px 18px; font-size: 0.85rem;\">\n");
        out.write("              <span>Donors CSV</span>\n");
        out.write("            </a>\n");
        out.write("            <a href=\"/admin/analytics/export/summary?" + filterParams + "\" class=\"btn btn-primary\" style=\"padding: 10px 18px; font-size: 0.85rem;\">\n");
        out.write("              <span>Summary Metrics CSV</span>\n");
        out.write("            </a>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("      </section>\n");
    }

    private String buildExportQueryString(AnalyticsFilter f) {
        StringBuilder sb = new StringBuilder();
        sb.append("preset=").append(urlEncode(f.getDatePreset()));
        sb.append("&dateField=").append(urlEncode(f.getDateField()));
        sb.append("&bloodGroup=").append(urlEncode(f.getBloodGroup()));
        sb.append("&locality=").append(urlEncode(f.getLocality()));
        sb.append("&status=").append(urlEncode(f.getStatus()));
        sb.append("&urgency=").append(urlEncode(f.getUrgency()));
        if (f.getCustomStartDate() != null) {
            sb.append("&startDate=").append(urlEncode(f.getCustomStartDate().toString()));
        }
        if (f.getCustomEndDate() != null) {
            sb.append("&endDate=").append(urlEncode(f.getCustomEndDate().toString()));
        }
        return sb.toString();
    }

    private static String urlEncode(String val) {
        if (val == null) return "";
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }

    private void renderClientScript(PrintWriter out) {
        out.write("  <script>\n");
        out.write("    (function() {\n");
        out.write("      // Quick interactive preset toggling without page reloading issues\n");
        out.write("      const presetSelect = document.getElementById('preset');\n");
        out.write("      if (presetSelect) {\n");
        out.write("        presetSelect.addEventListener('change', function() {\n");
        out.write("          if (this.value === 'CUSTOM') {\n");
        out.write("            const start = prompt('Enter Start Date (YYYY-MM-DD):', '2026-10-01');\n");
        out.write("            const end = prompt('Enter End Date (YYYY-MM-DD):', '2026-10-31');\n");
        out.write("            if (start && end) {\n");
        out.write("              const form = document.getElementById('analyticsFilterForm');\n");
        out.write("              let sInput = document.createElement('input');\n");
        out.write("              sInput.type = 'hidden';\n");
        out.write("              sInput.name = 'startDate';\n");
        out.write("              sInput.value = start;\n");
        out.write("              let eInput = document.createElement('input');\n");
        out.write("              eInput.type = 'hidden';\n");
        out.write("              eInput.name = 'endDate';\n");
        out.write("              eInput.value = end;\n");
        out.write("              form.appendChild(sInput);\n");
        out.write("              form.appendChild(eInput);\n");
        out.write("              form.submit();\n");
        out.write("            }\n");
        out.write("          }\n");
        out.write("        });\n");
        out.write("      }\n");
        out.write("    })();\n");
        out.write("  </script>\n");
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

package com.bloodbuddy.servlets;

import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.service.AnalyticsService;
import com.bloodbuddy.service.AnalyticsService.AnalyticsFilter;
import com.bloodbuddy.service.AnalyticsService.AnalyticsSummary;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorService;
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
import java.util.List;

/**
 * AnalyticsExportServlet handles secure, authenticated CSV report exports (Stage 8).
 * Guards endpoints with administrative session authentication, applies filters,
 * prevents spreadsheet formula injection, and streams UTF-8 CSV documents.
 */
public class AnalyticsExportServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final BloodRequestService requestService = new BloodRequestService();
    private final DonorService donorService = new DonorService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 1. Guard with Administrative Authorization
        if (!authService.isAdmin(req)) {
            String targetPath = req.getServletPath() + (req.getPathInfo() != null ? req.getPathInfo() : "");
            resp.sendRedirect(req.getContextPath() + "/admin/login?redirect="
                    + URLEncoder.encode(targetPath, StandardCharsets.UTF_8));
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            pathInfo = "";
        }

        // 2. Build Filter
        AnalyticsFilter filter = buildFilterFromParams(req);

        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/csv; charset=UTF-8");

        try (PrintWriter out = resp.getWriter()) {
            if (pathInfo.contains("/requests") || req.getServletPath().endsWith("/export-requests")) {
                resp.setHeader("Content-Disposition", "attachment; filename=\"blood_buddy_requests_report.csv\"");
                List<BloodRequest> allRequests = requestService.getAllRequests();
                List<BloodRequest> filtered = analyticsService.filterRequests(allRequests, filter);
                out.write(analyticsService.generateRequestsCsv(filtered));

            } else if (pathInfo.contains("/donors") || req.getServletPath().endsWith("/export-donors")) {
                resp.setHeader("Content-Disposition", "attachment; filename=\"blood_buddy_donors_report.csv\"");
                List<Donor> allDonors = donorService.getAllDemonstrationDonors();
                List<Donor> filtered = analyticsService.filterDonors(allDonors, filter);
                out.write(analyticsService.generateDonorsCsv(filtered));

            } else {
                // Summary report default
                resp.setHeader("Content-Disposition", "attachment; filename=\"blood_buddy_analytics_summary.csv\"");
                AnalyticsSummary summary = analyticsService.computeSummary(filter);
                out.write(analyticsService.generateSummaryCsv(summary));
            }
        }
    }

    private AnalyticsFilter buildFilterFromParams(HttpServletRequest req) {
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
        return filter;
    }
}

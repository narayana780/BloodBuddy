package com.bloodbuddy.servlets;

import com.bloodbuddy.service.AnalyticsService;
import com.bloodbuddy.service.AnalyticsService.AnalyticsFilter;
import com.bloodbuddy.service.AnalyticsService.AnalyticsSummary;
import com.bloodbuddy.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * AnalyticsApiServlet exposes structured JSON data for real-time interactive charts (Stage 8).
 * Guards endpoints with administrative authentication and renders pure JSON without external dependencies.
 */
public class AnalyticsApiServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final AnalyticsService analyticsService = new AnalyticsService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        // Enforce administrative access control
        if (!authService.isAdmin(req)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            try (PrintWriter out = resp.getWriter()) {
                out.write("{\"error\": \"Unauthorized. Administrator authentication required.\"}");
            }
            return;
        }

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

        AnalyticsSummary summary = analyticsService.computeSummary(filter);

        try (PrintWriter out = resp.getWriter()) {
            out.write(toJson(summary));
        }
    }

    private String toJson(AnalyticsSummary s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"totalDonors\": ").append(s.getTotalDonors()).append(",\n");
        sb.append("  \"availableDonors\": ").append(s.getAvailableDonors()).append(",\n");
        sb.append("  \"unavailableDonors\": ").append(s.getUnavailableDonors()).append(",\n");
        sb.append("  \"totalRequests\": ").append(s.getTotalRequests()).append(",\n");
        sb.append("  \"pendingRequests\": ").append(s.getPendingRequests()).append(",\n");
        sb.append("  \"inProgressRequests\": ").append(s.getInProgressRequests()).append(",\n");
        sb.append("  \"completedRequests\": ").append(s.getCompletedRequests()).append(",\n");
        sb.append("  \"cancelledRequests\": ").append(s.getCancelledRequests()).append(",\n");
        sb.append("  \"urgentAndCriticalRequests\": ").append(s.getUrgentAndCriticalRequests()).append(",\n");
        sb.append("  \"requestsWithNoMatches\": ").append(s.getRequestsWithNoMatches()).append(",\n");
        sb.append("  \"completionRate\": ").append(String.format(java.util.Locale.ENGLISH, "%.2f", s.getCompletionRate())).append(",\n");

        sb.append("  \"donorBloodGroupCounts\": ").append(mapToJson(s.getDonorBloodGroupCounts())).append(",\n");
        sb.append("  \"requestBloodGroupCounts\": ").append(mapToJson(s.getRequestBloodGroupCounts())).append(",\n");
        sb.append("  \"requestUnitsByBloodGroup\": ").append(mapToJson(s.getRequestUnitsByBloodGroup())).append(",\n");
        sb.append("  \"statusCounts\": ").append(mapToJson(s.getStatusCounts())).append(",\n");
        sb.append("  \"urgencyCounts\": ").append(mapToJson(s.getUrgencyCounts())).append(",\n");
        sb.append("  \"localityDemandCounts\": ").append(mapToJson(s.getLocalityDemandCounts())).append(",\n");

        sb.append("  \"actionableInsights\": [\n");
        for (int i = 0; i < s.getActionableInsights().size(); i++) {
            sb.append("    \"").append(escapeJson(s.getActionableInsights().get(i))).append("\"");
            if (i < s.getActionableInsights().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String mapToJson(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            sb.append("\"").append(escapeJson(e.getKey())).append("\": ").append(e.getValue());
            if (i < map.size() - 1) sb.append(", ");
            i++;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

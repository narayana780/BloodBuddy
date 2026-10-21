package com.bloodbuddy.servlets;

import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * AdminUpdateStatusServlet handles authenticated request status updates (Stage 6 / Stage 7).
 * Restricts updates to authenticated administrators and automatically fires notifications.
 */
public class AdminUpdateStatusServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final BloodRequestService requestService = new BloodRequestService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String requestId = req.getParameter("requestId");
        String newStatus = req.getParameter("status");
        String returnTo = req.getParameter("returnTo");

        // 1. Enforce Admin Authorization
        if (!authService.isAdmin(req)) {
            String redirectUrl = req.getContextPath() + "/admin/login?redirect="
                    + URLEncoder.encode("/matching?requestId=" + (requestId != null ? requestId : ""), StandardCharsets.UTF_8);
            resp.sendRedirect(redirectUrl);
            return;
        }

        // 2. Validate Request ID
        if (requestId == null || requestId.trim().isEmpty()) {
            resp.sendRedirect(req.getContextPath() + "/view-requests");
            return;
        }

        Optional<BloodRequest> reqOpt = requestService.getRequestById(requestId.trim());
        if (reqOpt.isEmpty()) {
            resp.sendRedirect(req.getContextPath() + "/view-requests");
            return;
        }

        BloodRequest existing = reqOpt.get();
        String oldStatus = existing.getStatus();

        // 3. Validate Status Choice
        if (newStatus == null || newStatus.trim().isEmpty()) {
            newStatus = "Pending";
        }
        String cleanStatus = newStatus.trim();
        if (!"Pending".equalsIgnoreCase(cleanStatus)
                && !"In Progress".equalsIgnoreCase(cleanStatus)
                && !"Fulfilled".equalsIgnoreCase(cleanStatus)
                && !"Completed".equalsIgnoreCase(cleanStatus)
                && !"Cancelled".equalsIgnoreCase(cleanStatus)) {
            cleanStatus = "Pending";
        }

        // 4. Update Status and Emit Notification
        boolean updated = requestService.updateRequestStatus(existing.getId(), cleanStatus);
        if (updated && !cleanStatus.equalsIgnoreCase(oldStatus)) {
            notificationService.notifyStatusChanged(existing, oldStatus, cleanStatus);
        }

        // 5. Safe Redirect
        String redirect = req.getParameter("redirect");
        if (redirect != null && !redirect.trim().isEmpty() && redirect.startsWith("/")) {
            resp.sendRedirect(req.getContextPath() + redirect + (redirect.contains("?") ? "&" : "?")
                    + "statusUpdated=" + URLEncoder.encode(existing.getId(), StandardCharsets.UTF_8));
            return;
        }

        if ("view-requests".equalsIgnoreCase(returnTo)) {
            resp.sendRedirect(req.getContextPath() + "/view-requests?statusUpdated=" + URLEncoder.encode(existing.getId(), StandardCharsets.UTF_8));
        } else if ("emergency".equalsIgnoreCase(returnTo)) {
            resp.sendRedirect(req.getContextPath() + "/admin/emergency?statusUpdated=" + URLEncoder.encode(existing.getId(), StandardCharsets.UTF_8));
        } else {
            resp.sendRedirect(req.getContextPath() + "/matching?requestId=" + URLEncoder.encode(existing.getId(), StandardCharsets.UTF_8) + "&statusUpdated=true");
        }
    }
}

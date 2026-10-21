package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * NotificationsServlet manages the in-app notification center (Stage 7).
 * Renders notifications, unread counts, and server-validated mark-as-read actions.
 */
public class NotificationsServlet extends HttpServlet {

    private final NotificationService notificationService = new NotificationService();
    private final AuthService authService = new AuthService();
    private final com.bloodbuddy.service.ChatService chatService = new com.bloodbuddy.service.ChatService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        String categoryFilter = req.getParameter("category");

        String userId = (currentUser != null) ? currentUser.getId() : null;
        List<AppNotification> notifications = notificationService.getNotifications(userId, isAdmin);
        int unreadCount = notificationService.getUnreadCount(userId, isAdmin);
        int unreadMsgCount = (currentUser != null) ? chatService.getUnreadMessageCount(currentUser.getId()) : 0;

        try (PrintWriter out = resp.getWriter()) {
            renderNotificationsPage(out, notifications, unreadCount, unreadMsgCount, isAdmin, categoryFilter, currentUser);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        String action = req.getParameter("action");
        String notifId = req.getParameter("id");

        if ("mark-all-read".equalsIgnoreCase(action)) {
            notificationService.markAllAsRead(isAdmin);
        } else if ("mark-read".equalsIgnoreCase(action) && notifId != null && !notifId.trim().isEmpty()) {
            notificationService.markAsRead(notifId.trim());
        }

        resp.sendRedirect(req.getContextPath() + "/notifications");
    }

    private void renderNotificationsPage(PrintWriter out, List<AppNotification> list,
                                         int unreadCount, int unreadMsgCount, boolean isAdmin, String categoryFilter,
                                         User currentUser) {

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Notifications Center (" + unreadCount + " Unread) | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Real-time alerts for blood requisitions, donor matches, and hospital coordination status changes.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
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
        out.write("        <a href=\"/notifications\" class=\"nav-utility-link active-nav\" title=\"View Notifications\">\n");
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

        out.write("  <main class=\"request-board-main\">\n");
        out.write("    <div class=\"container\">\n");

        // Page Header
        out.write("      <div class=\"request-header-section\" style=\"margin-bottom: 24px;\">\n");
        out.write("        <div class=\"stage-badge stage-badge-urgent\">Community Alerts &amp; Notifications</div>\n");
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: 16px;\">\n");
        out.write("          <div>\n");
        out.write("            <h1 class=\"section-title\" style=\"margin-bottom: 6px;\">Notifications &amp; Activity Stream</h1>\n");
        out.write("            <p class=\"section-subtitle\" style=\"margin-bottom: 0;\">Verified system alerts for requisitions, candidate matches, and hospital coordination.</p>\n");
        out.write("          </div>\n");
        out.write("          <div style=\"display: flex; gap: 12px; align-items: center;\">\n");
        if (unreadCount > 0) {
            out.write("            <form method=\"POST\" action=\"/notifications\" style=\"margin: 0;\">\n");
            out.write("              <input type=\"hidden\" name=\"action\" value=\"mark-all-read\">\n");
            out.write("              <button type=\"submit\" class=\"btn btn-secondary\" style=\"padding: 10px 18px; font-size: 0.85rem;\">\n");
            out.write("                <span>✓ Mark All as Read</span>\n");
            out.write("              </button>\n");
            out.write("            </form>\n");
        }
        out.write("            <div class=\"status-pill\" style=\"background: rgba(230, 25, 75, 0.15); border-color: rgba(230, 25, 75, 0.4);\">\n");
        out.write("              <span class=\"status-dot\"></span>\n");
        out.write("              <span>" + unreadCount + " Unread Alert" + (unreadCount == 1 ? "" : "s") + "</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Filter Bar
        out.write("      <div class=\"filter-bar-container\" style=\"margin-bottom: 24px; padding: 14px 20px;\">\n");
        out.write("        <div style=\"display: flex; gap: 10px; flex-wrap: wrap; align-items: center;\">\n");
        out.write("          <span style=\"font-size: 0.85rem; font-weight: 600; color: var(--text-muted);\">Filter by:</span>\n");
        out.write("          <a href=\"/notifications\" class=\"btn " + (categoryFilter == null || categoryFilter.isEmpty() ? "btn-primary" : "btn-secondary") + "\" style=\"padding: 6px 14px; font-size: 0.8rem;\">All Alerts</a>\n");
        out.write("          <a href=\"/notifications?category=" + AppNotification.CAT_MATCHES_FOUND + "\" class=\"btn " + (AppNotification.CAT_MATCHES_FOUND.equals(categoryFilter) ? "btn-primary" : "btn-secondary") + "\" style=\"padding: 6px 14px; font-size: 0.8rem;\">⚡ Matches Found</a>\n");
        out.write("          <a href=\"/notifications?category=" + AppNotification.CAT_REQUEST_SUBMITTED + "\" class=\"btn " + (AppNotification.CAT_REQUEST_SUBMITTED.equals(categoryFilter) ? "btn-primary" : "btn-secondary") + "\" style=\"padding: 6px 14px; font-size: 0.8rem;\">🚨 New Requests</a>\n");
        out.write("          <a href=\"/notifications?category=" + AppNotification.CAT_STATUS_CHANGED + "\" class=\"btn " + (AppNotification.CAT_STATUS_CHANGED.equals(categoryFilter) ? "btn-primary" : "btn-secondary") + "\" style=\"padding: 6px 14px; font-size: 0.8rem;\">🔄 Status Changes</a>\n");
        out.write("          <a href=\"/notifications?category=" + AppNotification.CAT_NO_MATCHES + "\" class=\"btn " + (AppNotification.CAT_NO_MATCHES.equals(categoryFilter) ? "btn-primary" : "btn-secondary") + "\" style=\"padding: 6px 14px; font-size: 0.8rem;\">⚠️ Unmatched Requests</a>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Notification Cards List
        int displayedCount = 0;
        out.write("      <div class=\"notif-list-container\" style=\"display: flex; flex-direction: column; gap: 16px;\">\n");

        for (AppNotification notif : list) {
            if (categoryFilter != null && !categoryFilter.trim().isEmpty() && !notif.getCategory().equalsIgnoreCase(categoryFilter.trim())) {
                continue;
            }
            displayedCount++;

            String icon;
            String categoryBadgeClass;
            String categoryLabel;

            switch (notif.getCategory()) {
                case AppNotification.CAT_MATCHES_FOUND:
                    icon = "⚡";
                    categoryBadgeClass = "badge-matches";
                    categoryLabel = "Smart Matches Found";
                    break;
                case AppNotification.CAT_NO_MATCHES:
                    icon = "⚠️";
                    categoryBadgeClass = "badge-no-matches";
                    categoryLabel = "No Donors Found";
                    break;
                case AppNotification.CAT_STATUS_CHANGED:
                    icon = "🔄";
                    categoryBadgeClass = "badge-status";
                    categoryLabel = "Status Updated";
                    break;
                case AppNotification.CAT_REQUEST_SUBMITTED:
                default:
                    icon = "🚨";
                    categoryBadgeClass = "badge-request";
                    categoryLabel = "Request Logged";
                    break;
            }

            String cardBg = notif.isRead() ? "rgba(22, 14, 46, 0.65)" : "rgba(35, 18, 68, 0.9)";
            String borderGlow = notif.isRead() ? "var(--border-glass)" : "1px solid rgba(255, 87, 87, 0.4)";

            out.write("        <article class=\"notif-card glass-panel\" style=\"background: " + cardBg + "; border: " + borderGlow + "; border-radius: var(--radius-md); padding: 20px 24px; transition: transform 0.2s ease;\">\n");
            out.write("          <div style=\"display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap;\">\n");

            // Left icon & content
            out.write("            <div style=\"display: flex; gap: 16px; align-items: flex-start; flex: 1; min-width: 260px;\">\n");
            out.write("              <div style=\"font-size: 2rem; line-height: 1; padding: 10px; background: rgba(255, 255, 255, 0.05); border-radius: 12px; border: 1px solid var(--border-glass);\">" + icon + "</div>\n");
            out.write("              <div style=\"flex: 1;\">\n");
            out.write("                <div style=\"display: flex; gap: 10px; align-items: center; margin-bottom: 6px; flex-wrap: wrap;\">\n");
            out.write("                  <span class=\"notif-cat-badge " + categoryBadgeClass + "\">" + categoryLabel + "</span>\n");
            if (!notif.isRead()) {
                out.write("                  <span class=\"notif-unread-dot\" title=\"Unread notification\">● NEW</span>\n");
            }
            if (notif.isAdminOnly()) {
                out.write("                  <span class=\"notif-admin-pill\">🛡️ Admin Restricted</span>\n");
            }
            out.write("                  <span style=\"color: var(--text-muted); font-size: 0.8rem;\">🕒 " + escapeHtml(notif.getTimestamp()) + "</span>\n");
            out.write("                </div>\n");

            out.write("                <h3 style=\"font-size: 1.12rem; margin: 0 0 6px; color: var(--text-primary); font-family: var(--font-heading);\">" + escapeHtml(notif.getTitle()) + "</h3>\n");
            out.write("                <p style=\"color: var(--text-secondary); margin: 0 0 10px; font-size: 0.92rem; line-height: 1.5;\">" + escapeHtml(notif.getMessage()) + "</p>\n");

            if (!notif.getLinkUrl().isEmpty()) {
                out.write("                <a href=\"" + escapeHtml(notif.getLinkUrl()) + "\" class=\"notif-action-link\" style=\"display: inline-flex; align-items: center; gap: 6px; color: var(--icy-blue-soft); font-size: 0.88rem; font-weight: 600; text-decoration: none;\">\n");
                out.write("                  <span>Open Associated Request &amp; Matches</span>\n");
                out.write("                  <span>→</span>\n");
                out.write("                </a>\n");
            }

            out.write("              </div>\n");
            out.write("            </div>\n");

            // Right Actions
            out.write("            <div style=\"display: flex; gap: 10px; align-items: center;\">\n");
            if (!notif.isRead()) {
                out.write("              <form method=\"POST\" action=\"/notifications\" style=\"margin: 0;\">\n");
                out.write("                <input type=\"hidden\" name=\"action\" value=\"mark-read\">\n");
                out.write("                <input type=\"hidden\" name=\"id\" value=\"" + escapeHtml(notif.getId()) + "\">\n");
                out.write("                <button type=\"submit\" class=\"btn btn-secondary\" style=\"padding: 7px 14px; font-size: 0.8rem;\" title=\"Mark notification as read\">\n");
                out.write("                  <span>Mark Read</span>\n");
                out.write("                </button>\n");
                out.write("              </form>\n");
            } else {
                out.write("              <span style=\"color: var(--text-muted); font-size: 0.8rem;\">✓ Read</span>\n");
            }
            out.write("            </div>\n");

            out.write("          </div>\n");
            out.write("        </article>\n");
        }

        if (displayedCount == 0) {
            out.write("        <div class=\"empty-state-card\" style=\"padding: 50px 20px;\">\n");
            out.write("          <div class=\"empty-icon\">🔔</div>\n");
            out.write("          <h2 class=\"empty-title\">No Notifications to Display</h2>\n");
            out.write("          <p class=\"empty-desc\">No system alerts match the current selection. As blood requisitions and smart matches occur, updates will be logged here.</p>\n");
            out.write("          <div class=\"empty-actions\">\n");
            out.write("            <a href=\"/view-requests\" class=\"btn btn-primary\"><span>View Blood Requisitions</span><span class=\"btn-icon\">📋</span></a>\n");
            out.write("            <a href=\"/dashboard\" class=\"btn btn-secondary\"><span>Return Home</span></a>\n");
            out.write("          </div>\n");
            out.write("        </div>\n");
        }

        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Community Alerts &amp; Notifications. All rights reserved. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
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

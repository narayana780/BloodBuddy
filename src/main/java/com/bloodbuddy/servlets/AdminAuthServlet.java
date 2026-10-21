package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * AdminAuthServlet handles administrative login, session management, and logout (Stage 6 / Stage 7).
 * Strictly guards administrative routes and coordinates authorized hospital workflows.
 */
public class AdminAuthServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getServletPath();
        if ("/admin/logout".equalsIgnoreCase(path)) {
            authService.logout(req);
            resp.sendRedirect(req.getContextPath() + "/dashboard?loggedOut=true");
            return;
        }

        if (authService.isAdmin(req)) {
            // Already logged in
            String redirect = req.getParameter("redirect");
            if (redirect != null && !redirect.trim().isEmpty() && redirect.startsWith("/")) {
                resp.sendRedirect(req.getContextPath() + redirect);
            } else {
                resp.sendRedirect(req.getContextPath() + "/view-requests");
            }
            return;
        }

        String redirect = req.getParameter("redirect");
        String error = req.getParameter("error");

        try (PrintWriter out = resp.getWriter()) {
            renderLoginPage(out, redirect, error != null ? "Invalid credentials. Please verify your administrative username and password." : null);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String path = req.getServletPath();
        if ("/admin/logout".equalsIgnoreCase(path)) {
            authService.logout(req);
            resp.sendRedirect(req.getContextPath() + "/dashboard?loggedOut=true");
            return;
        }

        String user = req.getParameter("username");
        String pass = req.getParameter("password");
        String redirect = req.getParameter("redirect");

        boolean success = authService.login(req, user, pass);
        if (success) {
            if (redirect != null && !redirect.trim().isEmpty() && redirect.startsWith("/")) {
                resp.sendRedirect(req.getContextPath() + redirect);
            } else {
                resp.sendRedirect(req.getContextPath() + "/view-requests");
            }
        } else {
            String redirectParam = (redirect != null && !redirect.trim().isEmpty())
                    ? "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)
                    : "";
            resp.sendRedirect(req.getContextPath() + "/admin/login?error=1" + redirectParam);
        }
    }

    private void renderLoginPage(PrintWriter out, String redirect, String errorMessage) {
        int unreadCount = notificationService.getUnreadCount(false);

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Hospital Staff & Admin Sign In | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Authorized access for medical coordinators and blood bank supervisors.\">\n");
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
        out.write("          <li><a href=\"/become-donor\" class=\"nav-link\">Become a Donor</a></li>\n");
        out.write("          <li><a href=\"/request-blood\" class=\"nav-link\">Request Blood</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        out.write("        <a href=\"/login\" class=\"btn btn-primary btn-sm\">Sign In</a>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </header>\n");

        out.write("  <main class=\"profile-main\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 520px;\">\n");
        out.write("      <div class=\"auth-card glass-panel\" style=\"padding: 36px 30px; margin-top: 20px; border-radius: var(--radius-lg);\">\n");
        out.write("        <div style=\"text-align: center; margin-bottom: 24px;\">\n");
        out.write("          <div style=\"font-size: 2.8rem; margin-bottom: 8px;\">🛡️</div>\n");
        out.write("          <span class=\"stage-badge stage-badge-urgent\">Authorized Staff Access</span>\n");
        out.write("          <h1 style=\"font-size: 1.8rem; margin: 12px 0 6px;\">Authorized Staff Sign In</h1>\n");
        out.write("          <p style=\"color: var(--text-secondary); font-size: 0.9rem;\">Hospital coordination portal for managing donor cross-matches and private contact access.</p>\n");
        out.write("        </div>\n");

        if (errorMessage != null) {
            out.write("        <div class=\"validation-summary-box\" style=\"margin-bottom: 20px;\">\n");
            out.write("          <div class=\"val-error-title\">⚠️ Authentication Failed</div>\n");
            out.write("          <p style=\"margin: 0; font-size: 0.88rem;\">" + escapeHtml(errorMessage) + "</p>\n");
            out.write("        </div>\n");
        }

        out.write("        <form method=\"POST\" action=\"/admin/login\" class=\"auth-form\">\n");
        if (redirect != null && !redirect.trim().isEmpty()) {
            out.write("          <input type=\"hidden\" name=\"redirect\" value=\"" + escapeHtml(redirect) + "\">\n");
        }

        out.write("          <div class=\"form-group\" style=\"margin-bottom: 18px;\">\n");
        out.write("            <label for=\"username\" style=\"display: block; font-weight: 600; margin-bottom: 8px; color: var(--text-primary);\">Administrative Username</label>\n");
        out.write("            <input type=\"text\" id=\"username\" name=\"username\" required class=\"form-input\" placeholder=\"e.g. admin\" autocomplete=\"username\" style=\"width: 100%;\">\n");
        out.write("          </div>\n");

        out.write("          <div class=\"form-group\" style=\"margin-bottom: 24px;\">\n");
        out.write("            <label for=\"password\" style=\"display: block; font-weight: 600; margin-bottom: 8px; color: var(--text-primary);\">Access Password</label>\n");
        out.write("            <input type=\"password\" id=\"password\" name=\"password\" required class=\"form-input\" placeholder=\"••••••••••••\" autocomplete=\"current-password\" style=\"width: 100%;\">\n");
        out.write("          </div>\n");

        out.write("          <div class=\"auth-demo-credentials\" style=\"background: rgba(255, 255, 255, 0.04); border: 1px solid var(--border-glass); border-radius: var(--radius-sm); padding: 12px 14px; margin-bottom: 22px; font-size: 0.82rem; color: var(--text-muted);\">\n");
        out.write("            <strong>Hospital Coordinator Access:</strong><br>\n");
        out.write("            Username: <code style=\"color: var(--neon-coral);\">" + AppConfig.ADMIN_DEFAULT_USERNAME + "</code> &nbsp;|&nbsp; Password: <code style=\"color: var(--neon-coral);\">" + AppConfig.ADMIN_DEFAULT_PASSWORD + "</code>\n");
        out.write("          </div>\n");

        out.write("          <button type=\"submit\" class=\"btn btn-primary\" style=\"width: 100%; justify-content: center; padding: 14px;\">\n");
        out.write("            <span>Sign In to Hospital Portal</span>\n");
        out.write("            <span class=\"btn-icon\">→</span>\n");
        out.write("          </button>\n");
        out.write("        </form>\n");

        out.write("        <div style=\"text-align: center; margin-top: 20px; display: flex; flex-direction: column; gap: 8px;\">\n");
        out.write("          <a href=\"/login\" style=\"color: var(--icy-blue); font-size: 0.9rem; font-weight: 600; text-decoration: none;\">Looking for Donor or Requester Sign In? Click here</a>\n");
        out.write("          <a href=\"/dashboard\" style=\"color: var(--text-muted); font-size: 0.88rem; text-decoration: none;\">← Return to Public Dashboard</a>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Hospital Staff &amp; Coordinator Portal. All rights reserved.</div>\n");
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

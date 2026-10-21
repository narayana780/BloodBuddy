package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * UserAuthServlet provides unified authentication for ordinary users (both donors and requesters)
 * as well as discreet coordinator entry. Serves /login, /login.html, /register, /signup, /logout, and /account.
 */
public class UserAuthServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final DonorService donorService = new DonorService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getServletPath();

        if ("/logout".equalsIgnoreCase(path)) {
            authService.logout(req);
            resp.sendRedirect(req.getContextPath() + "/dashboard?loggedOut=true");
            return;
        }

        if ("/register".equalsIgnoreCase(path) || "/signup".equalsIgnoreCase(path)) {
            if (authService.isLoggedIn(req)) {
                User currentUser = authService.getCurrentUser(req);
                String purpose = req.getParameter("purpose");
                if (purpose == null || purpose.trim().isEmpty()) {
                    purpose = req.getParameter("intent");
                }
                if ("donate".equalsIgnoreCase(purpose)) {
                    if (currentUser != null && currentUser.isDonor()) {
                        resp.sendRedirect(req.getContextPath() + "/account");
                    } else {
                        resp.sendRedirect(req.getContextPath() + "/complete-donor-profile");
                    }
                } else if ("request".equalsIgnoreCase(purpose)) {
                    resp.sendRedirect(req.getContextPath() + "/request-blood");
                } else {
                    resp.sendRedirect(req.getContextPath() + "/account");
                }
                return;
            }
            String purpose = req.getParameter("purpose");
            if (purpose == null || purpose.trim().isEmpty()) {
                purpose = req.getParameter("intent");
            }
            if (purpose == null || purpose.trim().isEmpty()) {
                purpose = "both";
            }
            try (PrintWriter out = resp.getWriter()) {
                renderRegisterPage(out, null, "", "", "", "", purpose);
            }
            return;
        }

        if ("/account".equalsIgnoreCase(path) || "/profile".equalsIgnoreCase(path)) {
            if (!authService.isLoggedIn(req)) {
                resp.sendRedirect(req.getContextPath() + "/login?redirect=/account");
                return;
            }
            try (PrintWriter out = resp.getWriter()) {
                renderAccountPage(out, req);
            }
            return;
        }

        // /login, /login.html, /signin
        if (authService.isLoggedIn(req)) {
            String redirect = req.getParameter("redirect");
            if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
                resp.sendRedirect(req.getContextPath() + redirect);
            } else {
                resp.sendRedirect(req.getContextPath() + "/dashboard");
            }
            return;
        }

        String redirect = req.getParameter("redirect");
        String errorParam = req.getParameter("error");
        boolean justRegistered = "true".equalsIgnoreCase(req.getParameter("registered"));
        boolean justLoggedOut = "true".equalsIgnoreCase(req.getParameter("loggedOut"));

        String errorMessage = null;
        if ("invalid_credentials".equalsIgnoreCase(errorParam) || "1".equalsIgnoreCase(errorParam)) {
            errorMessage = "Invalid email/username or password. Please check your credentials and try again.";
        }

        String successMessage = null;
        if (justRegistered) {
            successMessage = "Account registered successfully! Please sign in with your email and password.";
        } else if (justLoggedOut) {
            successMessage = "You have been securely signed out.";
        }

        try (PrintWriter out = resp.getWriter()) {
            renderLoginPage(out, redirect, errorMessage, successMessage);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        String path = req.getServletPath();

        if ("/logout".equalsIgnoreCase(path)) {
            authService.logout(req);
            resp.sendRedirect(req.getContextPath() + "/dashboard?loggedOut=true");
            return;
        }

        if ("/register".equalsIgnoreCase(path) || "/signup".equalsIgnoreCase(path)) {
            handleRegister(req, resp);
            return;
        }

        // Login submission
        String identifier = req.getParameter("identifier");
        if (identifier == null || identifier.trim().isEmpty()) {
            identifier = req.getParameter("username");
        }
        String password = req.getParameter("password");
        String redirect = req.getParameter("redirect");

        User user = authService.loginUser(req, identifier, password);
        if (user != null) {
            if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
                resp.sendRedirect(req.getContextPath() + redirect);
            } else if (user.isAdmin()) {
                resp.sendRedirect(req.getContextPath() + "/admin/emergency");
            } else {
                resp.sendRedirect(req.getContextPath() + "/dashboard?signedIn=true");
            }
        } else {
            String redirectParam = (redirect != null && !redirect.trim().isEmpty())
                    ? "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)
                    : "";
            resp.sendRedirect(req.getContextPath() + "/login?error=invalid_credentials" + redirectParam);
        }
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String fullName = req.getParameter("fullName");
        String email = req.getParameter("email");
        String username = req.getParameter("username");
        String phone = req.getParameter("phone");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");
        String purpose = req.getParameter("purpose");
        String termsAccepted = req.getParameter("terms");

        if (purpose == null || purpose.trim().isEmpty()) {
            purpose = "both";
        }

        if (termsAccepted == null) {
            resp.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) {
                renderRegisterPage(out, "You must accept the terms of service and privacy notice to create an account.",
                        fullName, email, username, phone, purpose);
            }
            return;
        }

        try {
            User newUser = authService.registerUser(fullName, email, username, phone, password, confirmPassword, purpose);
            // Automatically sign in the user
            authService.loginUser(req, newUser.getEmail(), password);
            
            // Redirect based on selected account purpose
            if ("donate".equalsIgnoreCase(newUser.getPurpose())) {
                resp.sendRedirect(req.getContextPath() + "/complete-donor-profile");
            } else if ("request".equalsIgnoreCase(newUser.getPurpose())) {
                resp.sendRedirect(req.getContextPath() + "/request-blood");
            } else {
                resp.sendRedirect(req.getContextPath() + "/account?welcome=true");
            }
        } catch (AuthService.ValidationException e) {
            resp.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) {
                renderRegisterPage(out, e.getMessage(), fullName, email, username, phone, purpose);
            }
        }
    }

    private void renderLoginPage(PrintWriter out, String redirect, String errorMessage, String successMessage) {
        int unreadCount = notificationService.getUnreadCount(false);

        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Sign In — Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Sign in to your Blood Buddy account to manage donations, request blood, and coordinate live assistance.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/login", unreadCount, false, null);

        out.write("  <main class=\"profile-main\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 480px;\">\n");
        out.write("      <div class=\"auth-card glass-panel\" style=\"padding: 38px 32px; margin-top: 24px; border-radius: var(--radius-lg);\">\n");
        out.write("        <div style=\"text-align: center; margin-bottom: 24px;\">\n");
        out.write("          <div style=\"font-size: 2.8rem; margin-bottom: 8px;\">🩸</div>\n");
        out.write("          <h1 style=\"font-size: 1.8rem; margin: 0 0 8px; color: var(--text-white); font-family: var(--font-heading);\">Sign In to Blood Buddy</h1>\n");
        out.write("          <p style=\"color: var(--text-secondary); font-size: 0.92rem; margin: 0;\">One unified account for both voluntary donors and blood requesters.</p>\n");
        out.write("        </div>\n");

        if (errorMessage != null) {
            out.write("        <div class=\"alert alert-error\" style=\"background: rgba(255, 68, 68, 0.15); border: 1px solid rgba(255, 68, 68, 0.5); border-radius: var(--radius-sm); padding: 12px 16px; margin-bottom: 20px; color: #ff8888; font-size: 0.88rem;\">\n");
            out.write("          ⚠️ " + escapeHtml(errorMessage) + "\n");
            out.write("        </div>\n");
        }

        if (successMessage != null) {
            out.write("        <div class=\"alert alert-success\" style=\"background: rgba(0, 200, 83, 0.15); border: 1px solid rgba(0, 200, 83, 0.5); border-radius: var(--radius-sm); padding: 12px 16px; margin-bottom: 20px; color: #4ade80; font-size: 0.88rem;\">\n");
            out.write("          ✓ " + escapeHtml(successMessage) + "\n");
            out.write("        </div>\n");
        }

        out.write("        <form method=\"POST\" action=\"/login\" class=\"auth-form\">\n");
        if (redirect != null && !redirect.trim().isEmpty()) {
            out.write("          <input type=\"hidden\" name=\"redirect\" value=\"" + escapeHtml(redirect) + "\">\n");
        }

        out.write("          <div class=\"form-group\" style=\"margin-bottom: 18px;\">\n");
        out.write("            <label for=\"identifier\" style=\"display: block; font-weight: 600; margin-bottom: 8px; color: var(--text-primary);\">Email or Username</label>\n");
        out.write("            <div class=\"input-wrap\">\n");
        out.write("              <input type=\"text\" id=\"identifier\" name=\"identifier\" required class=\"form-control\" placeholder=\"e.g. name@example.com or username\" autocomplete=\"username\" style=\"width: 100%;\">\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("          <div class=\"form-group\" style=\"margin-bottom: 24px;\">\n");
        out.write("            <label for=\"password\" style=\"display: block; font-weight: 600; margin-bottom: 8px; color: var(--text-primary);\">Password</label>\n");
        out.write("            <div class=\"input-wrap\">\n");
        out.write("              <input type=\"password\" id=\"password\" name=\"password\" required class=\"form-control\" placeholder=\"••••••••••••\" autocomplete=\"current-password\" style=\"width: 100%;\">\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("          <button type=\"submit\" class=\"btn btn-primary\" style=\"width: 100%; justify-content: center; padding: 14px; font-size: 1rem;\">\n");
        out.write("            <span>Sign In</span>\n");
        out.write("            <span class=\"btn-icon\">→</span>\n");
        out.write("          </button>\n");
        out.write("        </form>\n");

        out.write("        <div style=\"text-align: center; margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border-glass);\">\n");
        out.write("          <span style=\"color: var(--text-secondary); font-size: 0.9rem;\">New to Blood Buddy?</span>\n");
        out.write("          <a href=\"/register\" style=\"color: var(--neon-coral); font-weight: 600; text-decoration: none; margin-left: 6px;\">Create an Account →</a>\n");
        out.write("        </div>\n");

        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderRegisterPage(PrintWriter out, String errorMessage, String fullName, String email, String username, String phone, String selectedPurpose) {
        int unreadCount = notificationService.getUnreadCount(false);
        String currentPurpose = (selectedPurpose != null && !selectedPurpose.trim().isEmpty())
                ? selectedPurpose.trim().toLowerCase() : "both";

        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Create an Account — Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Create a unified Blood Buddy account to register as a voluntary blood donor, request blood in emergencies, and track requisitions.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/register", unreadCount, false, null);

        out.write("  <main class=\"profile-main\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 560px;\">\n");
        out.write("      <div class=\"auth-card glass-panel\" style=\"padding: 38px 32px; margin-top: 20px; border-radius: var(--radius-lg);\">\n");
        out.write("        <div style=\"text-align: center; margin-bottom: 24px;\">\n");
        out.write("          <div style=\"font-size: 2.8rem; margin-bottom: 8px;\">✨</div>\n");
        out.write("          <h1 style=\"font-size: 1.8rem; margin: 0 0 8px; color: var(--text-white); font-family: var(--font-heading);\">Create Your Account</h1>\n");
        out.write("          <p style=\"color: var(--text-secondary); font-size: 0.92rem; margin: 0;\">One unified account gives you full access to donate blood, request assistance, or both.</p>\n");
        out.write("        </div>\n");

        if (errorMessage != null) {
            out.write("        <div class=\"alert alert-error\" style=\"background: rgba(255, 68, 68, 0.15); border: 1px solid rgba(255, 68, 68, 0.5); border-radius: var(--radius-sm); padding: 12px 16px; margin-bottom: 20px; color: #ff8888; font-size: 0.88rem;\">\n");
            out.write("          ⚠️ " + escapeHtml(errorMessage) + "\n");
            out.write("        </div>\n");
        }

        out.write("        <form method=\"POST\" action=\"/register\" class=\"auth-form\">\n");

        // Purpose Selection Radio Cards
        out.write("          <div class=\"form-group\" style=\"margin-bottom: 20px;\">\n");
        out.write("            <label style=\"display: block; font-weight: 600; margin-bottom: 8px; color: var(--text-primary);\">Account Purpose <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("            <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px;\">\n");

        // Option 1: Donate
        boolean isDonate = "donate".equalsIgnoreCase(currentPurpose);
        out.write("              <label style=\"display: block; cursor: pointer; background: rgba(255, 255, 255, " + (isDonate ? "0.08" : "0.03") + "); border: 1.5px solid " + (isDonate ? "var(--neon-coral)" : "var(--border-glass)") + "; border-radius: var(--radius-md); padding: 12px 10px; text-align: center;\">\n");
        out.write("                <input type=\"radio\" name=\"purpose\" value=\"donate\"" + (isDonate ? " checked" : "") + " style=\"margin-bottom: 6px;\">\n");
        out.write("                <div style=\"font-weight: 700; color: var(--text-white); font-size: 0.92rem;\">🩸 Donate Blood</div>\n");
        out.write("                <div style=\"font-size: 0.75rem; color: var(--text-muted); margin-top: 4px;\">Join voluntary donor directory</div>\n");
        out.write("              </label>\n");

        // Option 2: Request
        boolean isRequest = "request".equalsIgnoreCase(currentPurpose);
        out.write("              <label style=\"display: block; cursor: pointer; background: rgba(255, 255, 255, " + (isRequest ? "0.08" : "0.03") + "); border: 1.5px solid " + (isRequest ? "var(--neon-coral)" : "var(--border-glass)") + "; border-radius: var(--radius-md); padding: 12px 10px; text-align: center;\">\n");
        out.write("                <input type=\"radio\" name=\"purpose\" value=\"request\"" + (isRequest ? " checked" : "") + " style=\"margin-bottom: 6px;\">\n");
        out.write("                <div style=\"font-weight: 700; color: var(--text-white); font-size: 0.92rem;\">🚨 Request Blood</div>\n");
        out.write("                <div style=\"font-size: 0.75rem; color: var(--text-muted); margin-top: 4px;\">Submit patient requisitions</div>\n");
        out.write("              </label>\n");

        // Option 3: Both
        boolean isBoth = "both".equalsIgnoreCase(currentPurpose) || (!isDonate && !isRequest);
        out.write("              <label style=\"display: block; cursor: pointer; background: rgba(255, 255, 255, " + (isBoth ? "0.08" : "0.03") + "); border: 1.5px solid " + (isBoth ? "var(--neon-coral)" : "var(--border-glass)") + "; border-radius: var(--radius-md); padding: 12px 10px; text-align: center;\">\n");
        out.write("                <input type=\"radio\" name=\"purpose\" value=\"both\"" + (isBoth ? " checked" : "") + " style=\"margin-bottom: 6px;\">\n");
        out.write("                <div style=\"font-weight: 700; color: var(--text-white); font-size: 0.92rem;\">🤝 Both</div>\n");
        out.write("                <div style=\"font-size: 0.75rem; color: var(--text-muted); margin-top: 4px;\">Donate and request when needed</div>\n");
        out.write("              </label>\n");

        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("          <div class=\"form-group\" style=\"margin-bottom: 16px;\">\n");
        out.write("            <label for=\"fullName\" style=\"display: block; font-weight: 600; margin-bottom: 6px; color: var(--text-primary);\">Full Name <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("            <input type=\"text\" id=\"fullName\" name=\"fullName\" required class=\"form-control\" placeholder=\"e.g. Rajesh Kumar\" value=\"" + escapeHtml(fullName) + "\">\n");
        out.write("          </div>\n");

        out.write("          <div class=\"form-group\" style=\"margin-bottom: 16px;\">\n");
        out.write("            <label for=\"email\" style=\"display: block; font-weight: 600; margin-bottom: 6px; color: var(--text-primary);\">Email Address <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("            <input type=\"email\" id=\"email\" name=\"email\" required class=\"form-control\" placeholder=\"e.g. rajesh@example.com\" value=\"" + escapeHtml(email) + "\">\n");
        out.write("          </div>\n");

        out.write("          <div class=\"form-grid-2col\" style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px;\">\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"username\" style=\"display: block; font-weight: 600; margin-bottom: 6px; color: var(--text-primary);\">Username</label>\n");
        out.write("              <input type=\"text\" id=\"username\" name=\"username\" class=\"form-control\" placeholder=\"e.g. rajesh_k\" value=\"" + escapeHtml(username) + "\">\n");
        out.write("              <span style=\"font-size: 0.75rem; color: var(--text-muted);\">Optional; auto-generated if blank.</span>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"phone\" style=\"display: block; font-weight: 600; margin-bottom: 6px; color: var(--text-primary);\">Phone Number</label>\n");
        out.write("              <input type=\"tel\" id=\"phone\" name=\"phone\" class=\"form-control\" placeholder=\"e.g. +91 98480 12345\" value=\"" + escapeHtml(phone) + "\">\n");
        out.write("              <span style=\"font-size: 0.75rem; color: var(--text-muted);\">Used for emergency matching alerts.</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("          <div class=\"form-grid-2col\" style=\"display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 20px;\">\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"password\" style=\"display: block; font-weight: 600; margin-bottom: 6px; color: var(--text-primary);\">Password <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("              <input type=\"password\" id=\"password\" name=\"password\" required minlength=\"6\" class=\"form-control\" placeholder=\"At least 6 characters\">\n");
        out.write("            </div>\n");
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"confirmPassword\" style=\"display: block; font-weight: 600; margin-bottom: 6px; color: var(--text-primary);\">Confirm Password <span style=\"color: var(--neon-coral);\">*</span></label>\n");
        out.write("              <input type=\"password\" id=\"confirmPassword\" name=\"confirmPassword\" required minlength=\"6\" class=\"form-control\" placeholder=\"Re-enter password\">\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("          <div class=\"form-group\" style=\"margin-bottom: 22px; background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-glass); border-radius: var(--radius-sm); padding: 12px 14px;\">\n");
        out.write("            <label style=\"display: flex; gap: 10px; align-items: flex-start; cursor: pointer; font-size: 0.85rem; color: var(--text-secondary); line-height: 1.5;\">\n");
        out.write("              <input type=\"checkbox\" name=\"terms\" value=\"1\" required style=\"margin-top: 3px;\">\n");
        out.write("              <span>I agree to the <strong>Terms of Service</strong> and <strong>Privacy Notice</strong>. I understand that Blood Buddy is a coordination platform and that medical eligibility and blood cross-matching require qualified hospital laboratory verification.</span>\n");
        out.write("            </label>\n");
        out.write("          </div>\n");

        out.write("          <button type=\"submit\" class=\"btn btn-primary\" style=\"width: 100%; justify-content: center; padding: 14px; font-size: 1rem;\">\n");
        out.write("            <span>Create Account</span>\n");
        out.write("            <span class=\"btn-icon\">→</span>\n");
        out.write("          </button>\n");
        out.write("        </form>\n");

        out.write("        <div style=\"text-align: center; margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border-glass);\">\n");
        out.write("          <span style=\"color: var(--text-secondary); font-size: 0.9rem;\">Already have an account?</span>\n");
        out.write("          <a href=\"/login\" style=\"color: var(--neon-coral); font-weight: 600; text-decoration: none; margin-left: 6px;\">Sign In →</a>\n");
        out.write("        </div>\n");

        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderAccountPage(PrintWriter out, HttpServletRequest req) {
        User user = authService.getCurrentUser(req);
        boolean isAdmin = authService.isAdmin(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        // Check if user is linked to a donor
        Optional<Donor> linkedDonor = Optional.empty();
        if (user != null && user.isDonor()) {
            linkedDonor = donorService.findDonorById(user.getLinkedDonorId());
        }

        String purposeDesc = "Voluntary Blood Donor & Requisitioner";
        if (user != null) {
            if (User.PURPOSE_DONATE.equalsIgnoreCase(user.getPurpose())) {
                purposeDesc = "Voluntary Blood Donor";
            } else if (User.PURPOSE_REQUEST.equalsIgnoreCase(user.getPurpose())) {
                purposeDesc = "Blood Requisitioner";
            }
        }

        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>My Account — Blood Buddy</title>\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/account", unreadCount, isAdmin, user);

        out.write("  <main class=\"profile-main\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 860px;\">\n");
        out.write("      <div class=\"glass-panel\" style=\"padding: 34px 30px; border-radius: var(--radius-lg); margin-bottom: 24px;\">\n");
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 20px;\">\n");
        out.write("          <div>\n");
        out.write("            <span class=\"stage-badge stage-badge-verified\">" + escapeHtml(purposeDesc) + "</span>\n");
        out.write("            <h1 style=\"font-size: 2rem; margin: 8px 0 4px; color: var(--text-white); font-family: var(--font-heading);\">" + escapeHtml(user.getFullName()) + "</h1>\n");
        out.write("            <div style=\"color: var(--text-secondary); font-size: 0.92rem;\">@" + escapeHtml(user.getUsername()) + " &bull; Member since " + escapeHtml(user.getRegisteredAt()) + "</div>\n");
        out.write("          </div>\n");
        out.write("          <div>\n");
        out.write("            <a href=\"/logout\" class=\"btn btn-secondary btn-sm\">Sign Out</a>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px;\">\n");
        out.write("          <div style=\"background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 16px;\">\n");
        out.write("            <div style=\"font-size: 0.8rem; color: var(--text-muted); text-transform: uppercase;\">Email Address</div>\n");
        out.write("            <div style=\"font-size: 1rem; color: var(--text-primary); font-weight: 600; margin-top: 4px;\">" + escapeHtml(user.getEmail()) + "</div>\n");
        out.write("          </div>\n");
        out.write("          <div style=\"background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 16px;\">\n");
        out.write("            <div style=\"font-size: 0.8rem; color: var(--text-muted); text-transform: uppercase;\">Phone Number</div>\n");
        out.write("            <div style=\"font-size: 1rem; color: var(--text-primary); font-weight: 600; margin-top: 4px;\">" + (user.getPhone().isEmpty() ? "Not specified" : escapeHtml(user.getPhone())) + "</div>\n");
        out.write("          </div>\n");
        out.write("          <div style=\"background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 16px;\">\n");
        out.write("            <div style=\"font-size: 0.8rem; color: var(--text-muted); text-transform: uppercase;\">Account Focus</div>\n");
        out.write("            <div style=\"font-size: 1rem; color: var(--neon-coral); font-weight: 600; margin-top: 4px;\">" + escapeHtml(purposeDesc) + "</div>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Flash Banner for updates
        boolean isJustUpdated = "true".equalsIgnoreCase(req.getParameter("updated"));
        if (isJustUpdated) {
            out.write("        <div class=\"alert alert-success\" style=\"background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.4); border-radius: var(--radius-sm); padding: 12px 18px; margin-bottom: 20px; color: #10b981; font-size: 0.9rem;\">\n");
            out.write("          ✓ <strong>Availability Updated:</strong> Your donor availability status has been saved and is active in the voluntary directory.\n");
            out.write("        </div>\n");
        }

        // Dual Workflow Notice
        out.write("        <div style=\"background: rgba(0, 240, 255, 0.05); border: 1px solid rgba(0, 240, 255, 0.2); border-radius: var(--radius-md); padding: 14px 18px; margin-bottom: 22px; font-size: 0.88rem; color: var(--text-secondary);\">\n");
        out.write("          💡 <strong>Unified Member Access:</strong> Your Blood Buddy account lets you both volunteer as a blood donor and submit blood requisitions anytime. You never need to register a separate account.\n");
        out.write("        </div>\n");

        // Donor Status Card
        out.write("        <div style=\"background: rgba(0, 0, 0, 0.25); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 22px 24px; margin-bottom: 20px;\">\n");
        out.write("          <h3 style=\"margin-top: 0; color: var(--text-white); font-size: 1.15rem;\">🩸 Voluntary Blood Donor Status</h3>\n");
        if (linkedDonor.isPresent()) {
            Donor d = linkedDonor.get();
            out.write("          <div style=\"display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px;\">\n");
            out.write("            <div>\n");
            out.write("              <p style=\"color: var(--text-secondary); font-size: 0.92rem; margin: 0 0 8px;\">You are registered as a voluntary blood donor with blood group <strong style=\"color: var(--neon-coral);\">" + escapeHtml(d.getBloodGroup()) + "</strong> in <strong>" + escapeHtml(d.getLocality()) + "</strong>.</p>\n");
            out.write("              <div style=\"margin: 6px 0 12px;\">" + DonorProfileServlet.renderAvailabilityBadge(d) + "</div>\n");
            out.write("            </div>\n");
            out.write("            <a href=\"/donor-profile?id=" + escapeHtml(d.getId()) + "\" class=\"btn btn-secondary btn-sm\">View Public Profile →</a>\n");
            out.write("          </div>\n");
            out.write("          <form method=\"POST\" action=\"/update-donor-availability\" style=\"margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border-glass); display: flex; gap: 12px; align-items: center; flex-wrap: wrap;\">\n");
            out.write("            <input type=\"hidden\" name=\"donorId\" value=\"" + escapeHtml(d.getId()) + "\">\n");
            out.write("            <input type=\"hidden\" name=\"returnTo\" value=\"/account\">\n");
            out.write("            <label for=\"accAvailSelect\" style=\"font-size: 0.88rem; font-weight: 600; color: var(--text-primary);\">Update Your Status:</label>\n");
            out.write("            <select id=\"accAvailSelect\" name=\"availability\" class=\"form-control form-select\" style=\"width: auto; padding: 6px 12px; font-size: 0.88rem;\">\n");
            out.write("              <option value=\"Available\"" + (d.isEligible() ? " selected" : "") + ">🟢 Available — Ready to respond</option>\n");
            out.write("              <option value=\"Unavailable\"" + (!d.isEligible() ? " selected" : "") + ">🟠 Temporarily Unavailable — Resting</option>\n");
            out.write("            </select>\n");
            out.write("            <button type=\"submit\" class=\"btn btn-primary btn-sm\">Save Status</button>\n");
            out.write("          </form>\n");
        } else {
            out.write("          <p style=\"color: var(--text-secondary); font-size: 0.92rem;\">Join our voluntary donor directory to help emergency patients in Rajahmundry locate compatible lifesavers.</p>\n");
            out.write("          <div style=\"margin-top: 14px;\">\n");
            out.write("            <a href=\"/complete-donor-profile\" class=\"btn btn-primary btn-sm\">Complete Donor Profile →</a>\n");
            out.write("          </div>\n");
        }
        out.write("        </div>\n");

        // Blood Requisitions Card
        out.write("        <div style=\"background: rgba(0, 0, 0, 0.25); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 22px 24px;\">\n");
        out.write("          <h3 style=\"margin-top: 0; color: var(--text-white); font-size: 1.15rem;\">🏥 Emergency Blood Requisitions</h3>\n");
        out.write("          <p style=\"color: var(--text-secondary); font-size: 0.92rem;\">Need blood for a patient or family member? You can submit an emergency blood requisition or track live requests anytime.</p>\n");
        out.write("          <div style=\"display: flex; gap: 12px; flex-wrap: wrap; margin-top: 14px;\">\n");
        out.write("            <a href=\"/request-blood\" class=\"btn btn-primary btn-sm\">Submit Blood Request →</a>\n");
        out.write("            <a href=\"/view-requests\" class=\"btn btn-secondary btn-sm\">View All Requests</a>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderHeader(PrintWriter out, String currentPath, int unreadCount, boolean isAdmin, User user) {
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\"><img src=\"/static/images/logo.svg\" alt=\"Blood Buddy\" width=\"38\" height=\"38\"></div>\n");
        out.write("        <div class=\"brand-text\"><span class=\"brand-name\">" + AppConfig.APP_NAME + "</span><span class=\"brand-tagline-sub\">" + AppConfig.TAGLINE + "</span></div>\n");
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

        if (user != null) {
            String displayName = user.getFullName();
            if (displayName.length() > 16) {
                displayName = displayName.substring(0, 14) + "…";
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
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Voluntary Blood Donation Coordination Network.</div>\n");
        out.write("      <div>Preliminary ABO/Rh compatibility requires professional hospital confirmation. &bull; <a href=\"/admin/login\" style=\"color: var(--text-muted); text-decoration: none;\">Hospital Staff Portal</a></div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

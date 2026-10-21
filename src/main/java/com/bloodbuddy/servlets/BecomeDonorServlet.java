package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * BecomeDonorServlet renders the informational guide explaining voluntary donation,
 * donation steps, and eligibility guidance, leading prospective donors to the shared
 * registration system.
 */
public class BecomeDonorServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        try (PrintWriter out = resp.getWriter()) {
            renderInformationalPage(out, unreadCount, isAdmin, currentUser);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Redirection to the appropriate workflow if invoked via POST
        User currentUser = authService.getCurrentUser(req);
        if (currentUser != null) {
            resp.sendRedirect(req.getContextPath() + "/complete-donor-profile");
        } else {
            resp.sendRedirect(req.getContextPath() + "/register?purpose=donate");
        }
    }

    private void renderInformationalPage(PrintWriter out, int unreadCount, boolean isAdmin, User currentUser) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Become a Blood Donor — Save Lives | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Learn how voluntary blood donation saves lives in Rajahmundry. Understand the three-step donation journey and register to become a donor.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/become-donor", unreadCount, isAdmin, currentUser);

        out.write("  <main class=\"become-donor-main\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 900px;\">\n");

        // 1. Hero Section
        out.write("      <section class=\"become-donor-hero text-center\" style=\"margin-bottom: 40px;\">\n");
        out.write("        <div class=\"stage-badge stage-badge-verified\">Voluntary Donor Community</div>\n");
        out.write("        <h1 class=\"find-donors-title\" style=\"font-size: 2.6rem; margin: 12px 0 16px;\">Become a Blood Donor</h1>\n");
        out.write("        <p class=\"find-donors-subtitle\" style=\"font-size: 1.15rem; line-height: 1.6; max-width: 720px; margin: 0 auto 28px;\">\n");
        out.write("          Voluntary blood donation is one of the most powerful contributions you can make to your community.\n");
        out.write("          Every unit of whole blood donated can help save up to <strong>three lives</strong> across Rajahmundry hospitals\n");
        out.write("          — providing critical transfusions for accident survivors, surgical patients, mothers during childbirth, and cancer therapies.\n");
        out.write("        </p>\n");

        // Action Buttons Row (Prominent CTA & Secondary Link)
        out.write("        <div style=\"display: flex; justify-content: center; align-items: center; gap: 18px; flex-wrap: wrap; margin-bottom: 20px;\">\n");
        if (currentUser == null) {
            out.write("          <a href=\"/register?purpose=donate\" class=\"btn btn-primary\" style=\"padding: 16px 36px; font-size: 1.1rem; font-weight: 700;\">\n");
            out.write("            <span>Register to Donate</span>\n");
            out.write("            <span class=\"btn-icon\">🩸</span>\n");
            out.write("          </a>\n");
            out.write("          <a href=\"/login\" class=\"btn btn-secondary\" style=\"padding: 16px 28px; font-size: 1rem;\">\n");
            out.write("            <span>Already have an account? Sign In</span>\n");
            out.write("            <span class=\"btn-icon\">→</span>\n");
            out.write("          </a>\n");
        } else if (!currentUser.isDonor()) {
            out.write("          <a href=\"/complete-donor-profile\" class=\"btn btn-primary\" style=\"padding: 16px 36px; font-size: 1.1rem; font-weight: 700;\">\n");
            out.write("            <span>Complete Your Donor Profile</span>\n");
            out.write("            <span class=\"btn-icon\">🩸</span>\n");
            out.write("          </a>\n");
            out.write("          <a href=\"/account\" class=\"btn btn-secondary\" style=\"padding: 16px 28px; font-size: 1rem;\">\n");
            out.write("            <span>Go to My Account</span>\n");
            out.write("            <span class=\"btn-icon\">👤</span>\n");
            out.write("          </a>\n");
        } else {
            out.write("          <a href=\"/donor-profile?id=" + escapeHtml(currentUser.getLinkedDonorId()) + "\" class=\"btn btn-primary\" style=\"padding: 16px 36px; font-size: 1.1rem; font-weight: 700;\">\n");
            out.write("            <span>View Your Donor Profile</span>\n");
            out.write("            <span class=\"btn-icon\">✓</span>\n");
            out.write("          </a>\n");
            out.write("          <a href=\"/account\" class=\"btn btn-secondary\" style=\"padding: 16px 28px; font-size: 1rem;\">\n");
            out.write("            <span>Manage Availability</span>\n");
            out.write("            <span class=\"btn-icon\">⚙️</span>\n");
            out.write("          </a>\n");
        }
        out.write("        </div>\n");
        out.write("      </section>\n");

        // 2. Three-Step Donation Process
        out.write("      <section class=\"donation-process-section\" style=\"margin-bottom: 44px;\">\n");
        out.write("        <div style=\"text-align: center; margin-bottom: 28px;\">\n");
        out.write("          <h2 style=\"font-size: 1.8rem; color: var(--text-white); font-family: var(--font-heading); margin-bottom: 8px;\">The Simple 3-Step Process</h2>\n");
        out.write("          <p style=\"color: var(--text-secondary); font-size: 0.95rem;\">Becoming a registered lifesaver on Blood Buddy is transparent, secure, and straightforward.</p>\n");
        out.write("        </div>\n");

        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 20px;\">\n");

        // Step 1 Card
        out.write("          <div class=\"glass-panel\" style=\"padding: 28px 24px; border-radius: var(--radius-lg); position: relative; border-top: 3px solid var(--neon-coral);\">\n");
        out.write("            <div style=\"width: 44px; height: 44px; border-radius: 50%; background: rgba(255, 75, 75, 0.15); color: var(--neon-coral); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 1.2rem; margin-bottom: 16px;\">1</div>\n");
        out.write("            <h3 style=\"font-size: 1.25rem; color: var(--text-white); margin: 0 0 10px;\">Create an Account</h3>\n");
        out.write("            <p style=\"color: var(--text-secondary); font-size: 0.92rem; line-height: 1.5; margin: 0;\">\n");
        out.write("              Register with your name and secure password on our shared registration page.\n");
        out.write("              One unified account lets you donate blood, request patient assistance, or both whenever needed.\n");
        out.write("            </p>\n");
        out.write("          </div>\n");

        // Step 2 Card
        out.write("          <div class=\"glass-panel\" style=\"padding: 28px 24px; border-radius: var(--radius-lg); position: relative; border-top: 3px solid var(--ruby-red);\">\n");
        out.write("            <div style=\"width: 44px; height: 44px; border-radius: 50%; background: rgba(224, 17, 95, 0.15); color: var(--ruby-red); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 1.2rem; margin-bottom: 16px;\">2</div>\n");
        out.write("            <h3 style=\"font-size: 1.25rem; color: var(--text-white); margin: 0 0 10px;\">Complete Your Donor Profile</h3>\n");
        out.write("            <p style=\"color: var(--text-secondary); font-size: 0.92rem; line-height: 1.5; margin: 0;\">\n");
        out.write("              Provide your confirmed blood group, age, and Rajahmundry locality.\n");
        out.write("              This helps our smart matching system connect you with compatible local requisitions while safeguarding your private contact information.\n");
        out.write("            </p>\n");
        out.write("          </div>\n");

        // Step 3 Card
        out.write("          <div class=\"glass-panel\" style=\"padding: 28px 24px; border-radius: var(--radius-lg); position: relative; border-top: 3px solid var(--icy-blue);\">\n");
        out.write("            <div style=\"width: 44px; height: 44px; border-radius: 50%; background: rgba(0, 240, 255, 0.15); color: var(--icy-blue); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 1.2rem; margin-bottom: 16px;\">3</div>\n");
        out.write("            <h3 style=\"font-size: 1.25rem; color: var(--text-white); margin: 0 0 10px;\">Manage Your Availability</h3>\n");
        out.write("            <p style=\"color: var(--text-secondary); font-size: 0.92rem; line-height: 1.5; margin: 0;\">\n");
        out.write("              You stay in full control. Toggle between \"Available\" and \"Temporarily Resting\" anytime.\n");
        out.write("              When resting after a donation or traveling, simply update your preference without unregistering.\n");
        out.write("            </p>\n");
        out.write("          </div>\n");

        out.write("        </div>\n");
        out.write("      </section>\n");

        // 3. General Donation Considerations & Clinical Medical Disclaimer
        out.write("      <section class=\"considerations-section\" style=\"margin-bottom: 40px;\">\n");
        out.write("        <div class=\"glass-panel\" style=\"padding: 32px 30px; border-radius: var(--radius-lg);\">\n");
        out.write("          <h2 style=\"font-size: 1.45rem; color: var(--text-white); margin: 0 0 16px; font-family: var(--font-heading);\">\n");
        out.write("            📋 General Donation Considerations\n");
        out.write("          </h2>\n");
        out.write("          <p style=\"color: var(--text-secondary); font-size: 0.92rem; margin-bottom: 20px;\">\n");
        out.write("            Most healthy adults are eligible to donate whole blood. The general guidelines below reflect standard clinical donation prerequisites:\n");
        out.write("          </p>\n");

        out.write("          <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 14px; margin-bottom: 24px;\">\n");
        out.write("            <div style=\"background: rgba(255, 255, 255, 0.02); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 14px 16px;\">\n");
        out.write("              <strong style=\"color: var(--text-white); display: block; margin-bottom: 4px;\">🎂 Age Range</strong>\n");
        out.write("              <span style=\"color: var(--text-secondary); font-size: 0.88rem;\">Between 18 and 65 years of age. First-time donors should be in good general health.</span>\n");
        out.write("            </div>\n");
        out.write("            <div style=\"background: rgba(255, 255, 255, 0.02); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 14px 16px;\">\n");
        out.write("              <strong style=\"color: var(--text-white); display: block; margin-bottom: 4px;\">⚖️ Healthy Body Weight</strong>\n");
        out.write("              <span style=\"color: var(--text-secondary); font-size: 0.88rem;\">Minimum weight of 45 kg (whole blood collection safety standard).</span>\n");
        out.write("            </div>\n");
        out.write("            <div style=\"background: rgba(255, 255, 255, 0.02); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 14px 16px;\">\n");
        out.write("              <strong style=\"color: var(--text-white); display: block; margin-bottom: 4px;\">⏳ Donation Intervals</strong>\n");
        out.write("              <span style=\"color: var(--text-secondary); font-size: 0.88rem;\">At least 90 days for men and 120 days for women between consecutive whole blood donations.</span>\n");
        out.write("            </div>\n");
        out.write("            <div style=\"background: rgba(255, 255, 255, 0.02); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 14px 16px;\">\n");
        out.write("              <strong style=\"color: var(--text-white); display: block; margin-bottom: 4px;\">🩺 Vital Readiness</strong>\n");
        out.write("              <span style=\"color: var(--text-secondary); font-size: 0.88rem;\">Adequately hydrated, well-rested, and free from active acute infections, fever, or medication contraindications.</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        // Medical Notice Callout
        out.write("          <div class=\"demo-disclaimer-card\" style=\"margin: 0;\">\n");
        out.write("            <span class=\"disclaimer-icon\">⚠️</span>\n");
        out.write("            <div class=\"disclaimer-content\">\n");
        out.write("              <strong style=\"color: #fca5a5;\">Important Medical Determination Notice:</strong>\n");
        out.write("              <span>The general considerations above are provided solely for educational and preliminary guidance. They do <strong>not</strong> constitute a final clinical medical determination. Actual donor eligibility, hemoglobin level testing (&ge; 12.5 g/dL), medical history review, and laboratory cross-matching must always be confirmed by qualified medical personnel at the hospital or licensed blood bank before any blood donation.</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("        </div>\n");
        out.write("      </section>\n");

        // 4. Final Bottom Call to Action
        out.write("      <section class=\"bottom-cta-card text-center glass-panel\" style=\"padding: 36px 30px; border-radius: var(--radius-lg); margin-bottom: 40px;\">\n");
        out.write("        <h3 style=\"font-size: 1.6rem; color: var(--text-white); margin: 0 0 10px; font-family: var(--font-heading);\">Ready to Make an Impact?</h3>\n");
        out.write("        <p style=\"color: var(--text-secondary); font-size: 0.95rem; max-width: 580px; margin: 0 auto 24px;\">\n");
        out.write("          Join our network of voluntary donors across Rajahmundry. Registration takes under two minutes on our unified portal.\n");
        out.write("        </p>\n");
        out.write("        <div style=\"display: flex; justify-content: center; gap: 16px; flex-wrap: wrap;\">\n");
        if (currentUser == null) {
            out.write("          <a href=\"/register?purpose=donate\" class=\"btn btn-primary\" style=\"padding: 14px 32px; font-size: 1.05rem;\">\n");
            out.write("            <span>Register to Donate</span>\n");
            out.write("            <span class=\"btn-icon\">🩸</span>\n");
            out.write("          </a>\n");
            out.write("          <a href=\"/login\" class=\"btn btn-secondary\" style=\"padding: 14px 26px; font-size: 0.95rem;\">\n");
            out.write("            <span>Already have an account? Sign In</span>\n");
            out.write("          </a>\n");
        } else if (!currentUser.isDonor()) {
            out.write("          <a href=\"/complete-donor-profile\" class=\"btn btn-primary\" style=\"padding: 14px 32px; font-size: 1.05rem;\">\n");
            out.write("            <span>Complete Your Donor Profile</span>\n");
            out.write("            <span class=\"btn-icon\">🩸</span>\n");
            out.write("          </a>\n");
        } else {
            out.write("          <a href=\"/donor-profile?id=" + escapeHtml(currentUser.getLinkedDonorId()) + "\" class=\"btn btn-primary\" style=\"padding: 14px 32px; font-size: 1.05rem;\">\n");
            out.write("            <span>View Your Profile</span>\n");
            out.write("          </a>\n");
        }
        out.write("        </div>\n");
        out.write("      </section>\n");

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
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("          <li><a href=\"/find-donors\" class=\"nav-link\">Find Donors</a></li>\n");
        out.write("          <li><a href=\"/camps\" class=\"nav-link\">Camps</a></li>\n");
        out.write("          <li><a href=\"/become-donor\" class=\"nav-link active-nav\">Become a Donor</a></li>\n");
        out.write("          <li><a href=\"/request-blood\" class=\"nav-link\">Request Blood</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link\">View Requests</a></li>\n");
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

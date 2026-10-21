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
 * FeaturePlaceholderServlet serves dedicated, informative routes for future-stage actions
 * and provides the complete About, Help, and College Demonstration Guide (Stage 10).
 */
public class FeaturePlaceholderServlet extends HttpServlet {

    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getServletPath();
        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);

        try (PrintWriter out = resp.getWriter()) {
            if ("/camps".equalsIgnoreCase(path)) {
                renderCampsPage(out, unreadCount, isAdmin, currentUser);
            } else {
                renderAboutAndGuidePage(out, unreadCount, isAdmin, currentUser);
            }
        }
    }

    private void renderCampsPage(PrintWriter out, int unreadCount, boolean isAdmin, User currentUser) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Voluntary Donation Drives — Blood Buddy</title>\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body class=\"placeholder-body\">\n");
        renderHeader(out, "/camps", unreadCount, isAdmin, currentUser);

        out.write("  <main class=\"container placeholder-main\">\n");
        out.write("    <div class=\"placeholder-card glass-panel\" style=\"padding: 40px 30px; border-radius: var(--radius-lg);\">\n");
        out.write("      <div class=\"stage-badge stage-badge-urgent\">Community Outreach</div>\n");
        out.write("      <h1 class=\"placeholder-title\" style=\"font-size: 2rem; margin: 12px 0 16px;\">Voluntary Blood Donation Drives</h1>\n");
        out.write("      <p class=\"placeholder-desc\" style=\"color: var(--text-secondary); max-width: 600px; margin: 0 auto 24px;\">Scheduled community donation drives and hospital blood camps organized across Rajahmundry & East Godavari.</p>\n");
        out.write("      <div class=\"info-block\" style=\"text-align: left; max-width: 580px; margin: 0 auto 24px; background: rgba(0,0,0,0.25); padding: 20px; border-radius: var(--radius-md); border: 1px solid var(--border-glass);\">\n");
        out.write("        <h4 style=\"color: var(--neon-coral); margin-top: 0;\">Scheduled Donation Drives:</h4>\n");
        out.write("        <ul style=\"color: var(--text-primary); line-height: 1.8; font-size: 0.92rem; padding-left: 20px;\">\n");
        out.write("          <li><strong>Rotary Club Blood Drive:</strong> Danavaipeta Community Hall (Weekends)</li>\n");
        out.write("          <li><strong>Red Cross Blood Centre:</strong> Kambala Tank, Rajahmundry (Daily 9 AM - 5 PM)</li>\n");
        out.write("          <li><strong>Government Hospital Blood Centre:</strong> Danavaipeta, Rajahmundry (24/7 Transfusion Wing)</li>\n");
        out.write("        </ul>\n");
        out.write("      </div>\n");
        out.write("      <div class=\"placeholder-actions\" style=\"display: flex; gap: 12px; justify-content: center; flex-wrap: wrap;\">\n");
        out.write("        <a href=\"/dashboard\" class=\"btn btn-primary\"><span>Return to Dashboard</span><span class=\"btn-icon\">→</span></a>\n");
        out.write("        <a href=\"/become-donor\" class=\"btn btn-secondary\"><span>Register as Individual Donor</span></a>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");
        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderAboutAndGuidePage(PrintWriter out, int unreadCount, boolean isAdmin, User currentUser) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Platform Guide & Operational Protocols | Blood Buddy</title>\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");
        renderHeader(out, "/about", unreadCount, isAdmin, currentUser);

        out.write("  <main class=\"profile-main\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 960px;\">\n");

        // 1. Safety & Transfusion Protocol Notice
        out.write("      <div class=\"medical-disclaimer-box\" style=\"background: rgba(255, 107, 107, 0.12); border: 1px solid rgba(255, 107, 107, 0.4); border-radius: var(--radius-md); padding: 18px 22px; margin-bottom: 24px; display: flex; gap: 14px; align-items: flex-start;\">\n");
        out.write("        <div style=\"font-size: 1.8rem; line-height: 1;\">⚠️</div>\n");
        out.write("        <div style=\"font-size: 0.9rem; line-height: 1.6; color: var(--text-primary);\">\n");
        out.write("          <strong style=\"color: var(--neon-coral); font-size: 1rem;\">Medical &amp; Transfusion Safety Protocol:</strong><br>\n");
        out.write("          Blood Buddy connects voluntary blood donors with patients and healthcare facilities in Rajahmundry.<br>\n");
        out.write("          <strong>Preliminary Compatibility:</strong> Algorithmic donor matches are based strictly on standard ABO/Rh compatibility rules. They represent <em>preliminary eligibility only</em> and do <strong>NOT</strong> substitute for formal serological cross-matching, antibody screening, or professional hospital laboratory authorization. In real medical emergencies, always contact verified hospital blood banks directly or dial emergency services at <strong>108</strong>.\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // 2. Project Hero Card
        out.write("      <div class=\"glass-panel\" style=\"padding: 32px 28px; border-radius: var(--radius-lg); margin-bottom: 26px;\">\n");
        out.write("        <div style=\"display: flex; gap: 16px; align-items: center; margin-bottom: 16px;\">\n");
        out.write("          <div style=\"font-size: 2.6rem;\">🩸</div>\n");
        out.write("          <div>\n");
        out.write("            <h1 style=\"font-size: 2rem; margin: 0; color: var(--text-white); font-family: var(--font-heading);\">" + AppConfig.APP_NAME + "</h1>\n");
        out.write("            <p style=\"color: var(--neon-coral); margin: 4px 0 0; font-weight: 600; font-size: 1rem;\">" + AppConfig.TAGLINE + "</p>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("        <p style=\"color: var(--text-secondary); font-size: 0.95rem; line-height: 1.6; margin: 0 0 16px;\">\n");
        out.write("          A pure Java web application bridging voluntary lifesavers, hospital clinical coordinators, and emergency requisitions in real time. Built with Java Servlets, embedded Apache Tomcat, HTML5, CSS3, and crash-resistant local file persistence in JSON format.\n");
        out.write("        </p>\n");
        out.write("        <div style=\"display: flex; gap: 10px; flex-wrap: wrap;\">\n");
        out.write("          <span class=\"stage-badge stage-badge-verified\">Java 21</span>\n");
        out.write("          <span class=\"stage-badge stage-badge-verified\">Jakarta Servlet 6.0</span>\n");
        out.write("          <span class=\"stage-badge stage-badge-verified\">Embedded Tomcat 10.1</span>\n");
        out.write("          <span class=\"stage-badge stage-badge-verified\">Zero External Database</span>\n");
        out.write("          <span class=\"stage-badge stage-badge-verified\">Zero External APIs</span>\n");
        out.write("          <span class=\"stage-badge stage-badge-urgent\">Verified Operational Pipeline</span>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // 3. Complete Architecture & Feature Catalog
        out.write("      <div class=\"glass-panel\" style=\"padding: 30px 28px; border-radius: var(--radius-lg); margin-bottom: 26px;\">\n");
        out.write("        <h2 style=\"font-size: 1.4rem; color: var(--text-white); margin-top: 0; margin-bottom: 18px;\">📋 Platform Architecture &amp; Feature Directory</h2>\n");
        out.write("        <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px;\">\n");

        renderFeatureCard(out, "Landing Experience", "/landing", "Design system, responsive layout, 3D droplet canvas, and interactive role selection.", "Public");
        renderFeatureCard(out, "Home Dashboard", "/dashboard", "Universal donor/recipient matrix, blood group breakdown, and quick actions.", "Public");
        renderFeatureCard(out, "Find Donors Directory", "/find-donors", "Multi-criteria search by blood group, locality, and name with privacy shielding.", "Public");
        renderFeatureCard(out, "Become a Donor", "/become-donor", "Age bounds (18–65), eligibility verification, and instant persistence.", "Public");
        renderFeatureCard(out, "Blood Requisition Board", "/view-requests", "Urgency triage, unit bounds (1–20), hospital facility routing, and phone shield.", "Public");
        renderFeatureCard(out, "Authorized Staff Access", "/admin/login", "Secure session invalidation, staff login, and request lifecycle management.", "Admin");
        renderFeatureCard(out, "Smart Donor Matching", "/matching", "Algorithmic ABO/Rh compatibility, locality proximity ranking, and notifications.", "Hybrid");
        renderFeatureCard(out, "Analytics & Intelligence", "/admin/analytics", "Interactive charts, trend metrics, and injection-protected CSV exports.", "Admin");
        renderFeatureCard(out, "Emergency Control Center", "/admin/emergency", "Triage prioritization scoring, overdue detection, and 2h deadline countdown.", "Admin");
        renderFeatureCard(out, "Platform Safety Protocols", "/about", "Security auditing, zero broken links, atomic persistence, and operational guide.", "Public");

        out.write("        </div>\n");
        out.write("      </div>\n");

        // 4. Administrator Credentials & Environment Configuration
        out.write("      <div class=\"glass-panel\" style=\"padding: 30px 28px; border-radius: var(--radius-lg); margin-bottom: 26px;\">\n");
        out.write("        <h2 style=\"font-size: 1.4rem; color: var(--text-white); margin-top: 0; margin-bottom: 14px;\">🛡️ Administrator Credentials &amp; Configuration</h2>\n");
        out.write("        <p style=\"color: var(--text-secondary); font-size: 0.9rem; line-height: 1.6; margin-bottom: 16px;\">\n");
        out.write("          Hospital coordinators and system administrators authenticate to manage clinical statuses, view private contact details, and inspect emergency analytics.\n");
        out.write("        </p>\n");
        out.write("        <div style=\"background: rgba(0, 0, 0, 0.35); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 18px 20px; margin-bottom: 18px;\">\n");
        out.write("          <div style=\"font-size: 0.95rem; font-weight: 700; color: var(--mint); margin-bottom: 8px;\">Default Coordinator Credentials:</div>\n");
        out.write("          <div style=\"font-family: monospace; font-size: 0.95rem; color: var(--text-primary);\">\n");
        out.write("            Username: <span style=\"color: var(--neon-coral); font-weight: 700;\">" + AppConfig.ADMIN_DEFAULT_USERNAME + "</span><br>\n");
        out.write("            Password: <span style=\"color: var(--neon-coral); font-weight: 700;\">" + AppConfig.ADMIN_DEFAULT_PASSWORD + "</span>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("        <h4 style=\"color: var(--text-white); font-size: 1rem; margin-bottom: 8px;\">Environment Variable Overrides:</h4>\n");
        out.write("        <p style=\"color: var(--text-secondary); font-size: 0.88rem; line-height: 1.6; margin-bottom: 10px;\">\n");
        out.write("          Credentials and thresholds can be configured via environment variables for secure hospital operations:\n");
        out.write("        </p>\n");
        out.write("        <ul style=\"color: var(--text-primary); font-size: 0.88rem; line-height: 1.7; padding-left: 20px; font-family: monospace;\">\n");
        out.write("          <li><strong style=\"color: var(--icy-blue);\">BLOODBUDDY_ADMIN_USER</strong>: Sets administrative username (default: admin)</li>\n");
        out.write("          <li><strong style=\"color: var(--icy-blue);\">BLOODBUDDY_ADMIN_PASSWORD</strong>: Sets administrative password (default: bloodbuddy2026)</li>\n");
        out.write("          <li><strong style=\"color: var(--icy-blue);\">BLOODBUDDY_DUE_SOON_HOURS</strong>: Hours threshold for due-soon emergency alert (default: 2)</li>\n");
        out.write("          <li><strong style=\"color: var(--icy-blue);\">PORT</strong>: Server HTTP listening port (default: 8080)</li>\n");
        out.write("        </ul>\n");
        out.write("      </div>\n");

        // 5. Data Storage & Backup Procedures
        out.write("      <div class=\"glass-panel\" style=\"padding: 30px 28px; border-radius: var(--radius-lg); margin-bottom: 26px;\">\n");
        out.write("        <h2 style=\"font-size: 1.4rem; color: var(--text-white); margin-top: 0; margin-bottom: 14px;\">💾 Local Data Persistence &amp; Backup Procedures</h2>\n");
        out.write("        <p style=\"color: var(--text-secondary); font-size: 0.9rem; line-height: 1.6; margin-bottom: 14px;\">\n");
        out.write("          Blood Buddy strictly uses structured JSON files inside the project's local <code style=\"color: var(--icy-blue);\">data/</code> directory. All write operations use <strong>ReentrantReadWriteLock</strong> concurrency controls and atomic temporary-file swap replacement (<code style=\"color: var(--icy-blue);\">ATOMIC_MOVE</code>) to prevent data corruption during unexpected power loss or concurrent access.\n");
        out.write("        </p>\n");
        out.write("        <div style=\"background: rgba(0, 0, 0, 0.3); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 16px 20px; font-size: 0.88rem; line-height: 1.7;\">\n");
        out.write("          <div>📁 <strong>data/registered_donors.json</strong> — Registered voluntary donor profiles.</div>\n");
        out.write("          <div>📁 <strong>data/blood_requests.json</strong> — Emergency patient requisitions and status states.</div>\n");
        out.write("          <div>📁 <strong>data/notifications.json</strong> — In-app staff and user notification logs with deduplication keys.</div>\n");
        out.write("        </div>\n");
        out.write("        <div style=\"margin-top: 14px; font-size: 0.88rem; color: var(--text-secondary);\">\n");
        out.write("          <strong>Backup Command:</strong> Copy the entire <code style=\"color: var(--mint);\">data/</code> folder to any backup directory.<br>\n");
        out.write("          <strong>Restore:</strong> Replace files in <code style=\"color: var(--mint);\">data/</code> and restart the server.\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // 6. Platform Operational Workflow
        out.write("      <div class=\"glass-panel\" style=\"padding: 30px 28px; border-radius: var(--radius-lg); margin-bottom: 26px;\">\n");
        out.write("        <h2 style=\"font-size: 1.4rem; color: var(--text-white); margin-top: 0; margin-bottom: 14px;\">📋 Platform Operational Workflow</h2>\n");
        out.write("        <ol style=\"color: var(--text-primary); font-size: 0.9rem; line-height: 1.8; padding-left: 22px; margin: 0;\">\n");
        out.write("          <li><strong>Explore Landing Page (<a href=\"/landing\" style=\"color: var(--icy-blue);\">/landing</a>):</strong> View branding, 3D droplet canvas, and interactive role selection.</li>\n");
        out.write("          <li><strong>Browse Dashboard (<a href=\"/dashboard\" style=\"color: var(--icy-blue);\">/dashboard</a>):</strong> Test the live interactive ABO/Rh compatibility matrix.</li>\n");
        out.write("          <li><strong>Register a Donor (<a href=\"/become-donor\" style=\"color: var(--icy-blue);\">/become-donor</a>):</strong> Register a new donor; verify server-side age &amp; locality validation.</li>\n");
        out.write("          <li><strong>Search Donors (<a href=\"/find-donors\" style=\"color: var(--icy-blue);\">/find-donors</a>):</strong> Locate the donor by blood group and locality; observe privacy shielding of phone numbers.</li>\n");
        out.write("          <li><strong>Submit Blood Request (<a href=\"/request-blood\" style=\"color: var(--icy-blue);\">/request-blood</a>):</strong> Submit an emergency requisition; note immediate validation.</li>\n");
        out.write("          <li><strong>View Requisition Board (<a href=\"/view-requests\" style=\"color: var(--icy-blue);\">/view-requests</a>):</strong> Observe the new request with patient phone privacy protection.</li>\n");
        out.write("          <li><strong>Run Smart Matching (<a href=\"/matching\" style=\"color: var(--icy-blue);\">/matching</a>):</strong> Inspect ABO/Rh compatibility sorting and locality proximity ranking.</li>\n");
        out.write("          <li><strong>Check Notifications (<a href=\"/notifications\" style=\"color: var(--icy-blue);\">/notifications</a>):</strong> Verify alerts generated without duplicates.</li>\n");
        out.write("          <li><strong>Admin Sign In (<a href=\"/admin/login\" style=\"color: var(--icy-blue);\">/admin/login</a>):</strong> Log in using <code style=\"color: var(--neon-coral);\">admin</code> / <code style=\"color: var(--neon-coral);\">bloodbuddy2026</code>.</li>\n");
        out.write("          <li><strong>Emergency Control Center (<a href=\"/admin/emergency\" style=\"color: var(--icy-blue);\">/admin/emergency</a>):</strong> Review prioritized triage queue, overdue labels, and 2h countdowns.</li>\n");
        out.write("          <li><strong>Update Status:</strong> Change a request status from Pending to In Progress or Completed; observe live priority re-calculation.</li>\n");
        out.write("          <li><strong>Executive Analytics (<a href=\"/admin/analytics\" style=\"color: var(--icy-blue);\">/admin/analytics</a>):</strong> Review operational charts and export CSV reports.</li>\n");
        out.write("          <li><strong>Admin Logout:</strong> Restricted endpoints redirect unauthenticated requests securely to login.</li>\n");
        out.write("        </ol>\n");
        out.write("      </div>\n");

        out.write("    </div>\n");
        out.write("  </main>\n");
        renderFooter(out);
        out.write("</body>\n</html>");
    }

    private void renderFeatureCard(PrintWriter out, String title, String url, String desc, String badge) {
        String badgeClass = "stage-badge-verified";
        if ("Admin".equalsIgnoreCase(badge)) badgeClass = "stage-badge-urgent";
        if ("Hybrid".equalsIgnoreCase(badge)) badgeClass = "stage-badge-nearby";

        out.write("          <div style=\"background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 14px 16px;\">\n");
        out.write("            <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;\">\n");
        out.write("              <a href=\"" + url + "\" style=\"color: var(--text-white); font-weight: 700; text-decoration: none; font-size: 0.92rem;\">" + title + "</a>\n");
        out.write("              <span class=\"stage-badge " + badgeClass + "\" style=\"font-size: 0.72rem; padding: 2px 8px;\">" + badge + "</span>\n");
        out.write("            </div>\n");
        out.write("            <p style=\"color: var(--text-muted); font-size: 0.82rem; margin: 0; line-height: 1.4;\">" + desc + "</p>\n");
        out.write("          </div>\n");
    }

    private void renderHeader(PrintWriter out, String currentPath, int unreadCount, boolean isAdmin, User currentUser) {
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
    }

    private void renderFooter(PrintWriter out) {
        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Voluntary Blood Donation Coordination Network. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("      <div>Preliminary ABO/Rh compatibility requires professional hospital confirmation.</div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
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

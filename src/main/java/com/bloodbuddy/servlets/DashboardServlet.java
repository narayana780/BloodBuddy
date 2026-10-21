package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
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
import java.util.List;

/**
 * DashboardServlet renders the Blood Buddy Home Dashboard.
 * 100% Java Servlet business logic, validation, and HTML generation.
 * Zero JavaScript used per Stage 2 requirements.
 */
public class DashboardServlet extends HttpServlet {

    private final DonorService donorService = new DonorService();
    private final NotificationService notificationService = new NotificationService();
    private final AuthService authService = new AuthService();
    private final com.bloodbuddy.service.ChatService chatService = new com.bloodbuddy.service.ChatService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processDashboard(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Standard HTML form submission handled on the server
        processDashboard(req, resp);
    }

    private void processDashboard(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String rawGroup = req.getParameter("bloodGroup");
        String selectedGroup = null;
        String validationError = null;

        if (rawGroup != null && !rawGroup.trim().isEmpty()) {
            String trimmed = rawGroup.trim().toUpperCase();
            if (BloodCompatibility.isValidGroup(trimmed)) {
                selectedGroup = trimmed;
            } else {
                validationError = "Invalid blood group '" + escapeHtml(rawGroup) + "'. Defaulted to Universal Donor (O-).";
                selectedGroup = "O-";
            }
        } else {
            // Default selection on fresh arrival
            selectedGroup = "O-";
        }

        List<String> canGiveTo = BloodCompatibility.getCanGiveTo(selectedGroup);
        List<String> canReceiveFrom = BloodCompatibility.getCanReceiveFrom(selectedGroup);
        String clinicalSummary = BloodCompatibility.getClinicalSummary(selectedGroup);
        List<Donor> allDonors = donorService.getAllDemonstrationDonors();

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);
        int unreadMsgCount = (currentUser != null) ? chatService.getUnreadMessageCount(currentUser.getId()) : 0;

        try (PrintWriter out = resp.getWriter()) {
            renderHtml(out, selectedGroup, canGiveTo, canReceiveFrom, clinicalSummary, allDonors, validationError, unreadCount, unreadMsgCount, isAdmin, currentUser);
        }
    }

    private void renderHtml(PrintWriter out, String selectedGroup, List<String> canGiveTo,
                            List<String> canReceiveFrom, String clinicalSummary,
                            List<Donor> allDonors, String validationError, int unreadCount, int unreadMsgCount, boolean isAdmin, User currentUser) {

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Blood Buddy — One Drop. A Thousand Hopes.</title>\n");
        out.write("  <meta name=\"description\" content=\"Blood Buddy Home Dashboard. Voluntary blood donation platform connecting donors and healthcare seekers in Rajahmundry.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        // 1. Navigation Header
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\">\n");
        out.write("          <img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Droplet Logo\" width=\"38\" height=\"38\">\n");
        out.write("        </div>\n");
        out.write("        <div class=\"brand-text\">\n");
        out.write("          <span class=\"brand-name\">" + AppConfig.APP_NAME + "</span>\n");
        out.write("          <span class=\"brand-tagline-sub\">" + AppConfig.TAGLINE + "</span>\n");
        out.write("        </div>\n");
        out.write("      </a>\n");
        out.write("      <nav aria-label=\"Main Navigation\">\n");
        out.write("        <ul class=\"nav-links\">\n");
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link active-nav\">Home</a></li>\n");
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
        out.write("        <a href=\"/notifications\" class=\"nav-utility-link\" title=\"View Notifications\">\n");
        out.write("          <span>🔔 Alerts" + (unreadCount > 0 ? " <span class=\"notif-badge\">" + unreadCount + "</span>" : "") + "</span>\n");
        out.write("        </a>\n");
        if (currentUser != null) {
            String displayName = currentUser.getFullName();
            if (displayName.length() > 14) {
                displayName = displayName.substring(0, 12) + "…";
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

        out.write("  <main>\n");

        // Validation Error Banner if any
        if (validationError != null) {
            out.write("    <div class=\"container\" style=\"padding-top: 100px;\">\n");
            out.write("      <div class=\"validation-alert-banner\" role=\"alert\">\n");
            out.write("        <span class=\"alert-icon\">⚠️</span>\n");
            out.write("        <span>" + validationError + "</span>\n");
            out.write("      </div>\n");
            out.write("    </div>\n");
        }

        // 2. Hero Section with 3D-Inspired CSS Droplet & Glass Cards
        out.write("    <section class=\"hero\" id=\"hero\">\n");
        out.write("      <div class=\"container hero-grid\">\n");
        out.write("        <div class=\"hero-content\">\n");
        out.write("          <div class=\"hero-label\">\n");
        out.write("            <span class=\"hero-label-dot\"></span>\n");
        out.write("            <span class=\"hero-label-text\">Community Blood Network • Rajahmundry & Regional Centers</span>\n");
        out.write("          </div>\n");
        out.write("          <h1 class=\"hero-title\">\n");
        out.write("            <span class=\"highlight-red\">One Drop.</span><br>A Thousand Hopes.\n");
        out.write("          </h1>\n");
        out.write("          <p class=\"hero-lead\">\n");
        out.write("            Welcome to <strong>Blood Buddy</strong> — bridging the vital minutes during medical emergencies. Connect voluntary donors, families, and healthcare providers with rapid compatibility intelligence and localized coordination across Rajahmundry.\n");
        out.write("          </p>\n");
        out.write("          <div class=\"hero-actions\">\n");
        out.write("            <a href=\"/find-donors\" class=\"btn btn-primary\">\n");
        out.write("              <span>Find Donors</span>\n");
        out.write("              <span class=\"btn-icon\">→</span>\n");
        out.write("            </a>\n");
        out.write("            <a href=\"#how-it-works\" class=\"btn btn-secondary\">\n");
        out.write("              <span>How It Works</span>\n");
        out.write("              <span class=\"btn-icon\">↓</span>\n");
        out.write("            </a>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"hero-info-strip\">\n");
        out.write("            <div class=\"info-strip-item\">\n");
        out.write("              <span class=\"info-strip-value\">8 Groups</span>\n");
        out.write("              <span class=\"info-strip-label\">Full Compatibility Matrix</span>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"info-strip-item\">\n");
        out.write("              <span class=\"info-strip-value\">" + allDonors.size() + "+ Donors</span>\n");
        out.write("              <span class=\"info-strip-label\">Registered Community Lifesavers</span>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"info-strip-item\">\n");
        out.write("              <span class=\"info-strip-value\">Voluntary</span>\n");
        out.write("              <span class=\"info-strip-label\">Community Coordination</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Hero Right: Central 3D-Inspired CSS Droplet & Floating Glass Cards
        out.write("        <div class=\"hero-visual\">\n");
        out.write("          <div class=\"visual-canvas\">\n");
        out.write("            <div class=\"ambient-glow-circle\"></div>\n");
        out.write("            <div class=\"hero-ecg-wrap\" id=\"heroEcgWrap\" aria-hidden=\"true\">\n");
        out.write("              <svg class=\"hero-ecg-svg\" viewBox=\"0 0 800 140\" preserveAspectRatio=\"none\" width=\"100%\" height=\"100%\">\n");
        out.write("                <defs>\n");
        out.write("                  <filter id=\"ecgGlowFilterDash\" x=\"-20%\" y=\"-50%\" width=\"140%\" height=\"200%\">\n");
        out.write("                    <feGaussianBlur in=\"SourceGraphic\" stdDeviation=\"3.5\" result=\"blur1\" />\n");
        out.write("                    <feGaussianBlur in=\"SourceGraphic\" stdDeviation=\"8\" result=\"blur2\" />\n");
        out.write("                    <feMerge><feMergeNode in=\"blur2\" /><feMergeNode in=\"blur1\" /><feMergeNode in=\"SourceGraphic\" /></feMerge>\n");
        out.write("                  </filter>\n");
        out.write("                  <linearGradient id=\"ecgLineGradDash\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"0%\">\n");
        out.write("                    <stop offset=\"0%\" stop-color=\"#E53935\" stop-opacity=\"0.0\" />\n");
        out.write("                    <stop offset=\"10%\" stop-color=\"#E53935\" stop-opacity=\"0.85\" />\n");
        out.write("                    <stop offset=\"42%\" stop-color=\"#FF5252\" stop-opacity=\"0.5\" />\n");
        out.write("                    <stop offset=\"58%\" stop-color=\"#FF5252\" stop-opacity=\"0.5\" />\n");
        out.write("                    <stop offset=\"90%\" stop-color=\"#E53935\" stop-opacity=\"0.85\" />\n");
        out.write("                    <stop offset=\"100%\" stop-color=\"#E53935\" stop-opacity=\"0.0\" />\n");
        out.write("                  </linearGradient>\n");
        out.write("                </defs>\n");
        out.write("                <path class=\"ecg-base-line\" d=\"M 0,70 L 50,70 Q 70,62 90,70 L 105,75 L 125,12 L 138,102 L 150,70 Q 175,54 200,70 L 270,70 L 400,70 L 530,70 L 590,70 Q 610,62 630,70 L 645,75 L 665,10 L 678,104 L 690,70 Q 715,54 740,70 L 800,70\" fill=\"none\" stroke=\"url(#ecgLineGradDash)\" stroke-width=\"2.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\" />\n");
        out.write("                <path class=\"ecg-pulse-trace\" id=\"ecgPulseTrace\" d=\"M 0,70 L 50,70 Q 70,62 90,70 L 105,75 L 125,12 L 138,102 L 150,70 Q 175,54 200,70 L 270,70 L 400,70 L 530,70 L 590,70 Q 610,62 630,70 L 645,75 L 665,10 L 678,104 L 690,70 Q 715,54 740,70 L 800,70\" fill=\"none\" stroke=\"#FF8A80\" stroke-width=\"3.2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" filter=\"url(#ecgGlowFilterDash)\" />\n");
        out.write("              </svg>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"heart-3d-hero-container\" id=\"heart3DContainer\" role=\"img\" aria-label=\"Anatomical 3D Human Heart Beating Continuously\">\n");
        out.write("              <canvas id=\"heartCanvas\" class=\"heart-canvas\"></canvas>\n");
        out.write("              <div class=\"heart-fallback\" id=\"heartFallback\" style=\"display: none; opacity: 0;\">\n");
        out.write("                <img src=\"/static/images/heart-fallback.svg\" alt=\"Anatomical Human Heart\" width=\"460\" height=\"510\" class=\"heart-fallback-img\">\n");
        out.write("              </div>\n");
        out.write("              <div class=\"heart-loader\" id=\"heartLoader\" aria-hidden=\"true\">\n");
        out.write("                <span class=\"heart-pulse-spinner\"></span>\n");
        out.write("              </div>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"hero-system-status\">\n");
        out.write("              <span class=\"status-indicator\"></span>\n");
        out.write("              <span>Community Service • Selected Group: <strong>" + selectedGroup + "</strong></span>\n");
        out.write("            </div>\n");


        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </section>\n");

        // 3. Quick-Action Cards Section
        out.write("    <section class=\"quick-actions-section\" id=\"quick-actions\">\n");
        out.write("      <div class=\"container\">\n");
        out.write("        <div class=\"section-head\">\n");
        out.write("          <span class=\"section-tag\">Care Coordination</span>\n");
        out.write("          <h2 class=\"section-title\">Quick Actions</h2>\n");
        out.write("          <p class=\"section-desc\">Essential pathways for voluntary donors, emergency seekers, and healthcare coordinators.</p>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"action-cards-grid\">\n");

        // Card 1: Find Donors
        out.write("          <article class=\"action-card\">\n");
        out.write("            <div class=\"action-card-header\">\n");
        out.write("              <div class=\"action-icon-box box-cyan\">🔍</div>\n");
        out.write("              <span class=\"badge-stage\" style=\"color: var(--mint-light); border-color: rgba(6, 214, 160, 0.3);\">Verified Directory</span>\n");
        out.write("            </div>\n");
        out.write("            <h3 class=\"action-card-title\">Find Donors</h3>\n");
        out.write("            <p class=\"action-card-desc\">Search verified donors across Rajahmundry localities with radius filters and blood compatibility match.</p>\n");
        out.write("            <a href=\"/find-donors\" class=\"action-card-btn btn-secondary\">\n");
        out.write("              <span>Search Donors</span>\n");
        out.write("              <span class=\"btn-icon\">→</span>\n");
        out.write("            </a>\n");
        out.write("          </article>\n");

        // Card 2: Request Blood
        out.write("          <article class=\"action-card highlight-action\">\n");
        out.write("            <div class=\"action-card-header\">\n");
        out.write("              <div class=\"action-icon-box box-coral\">🚨</div>\n");
        out.write("              <span class=\"badge-stage\" style=\"background: rgba(255, 87, 87, 0.2); color: var(--neon-coral); border-color: rgba(255, 87, 87, 0.4);\">Urgent Requests</span>\n");
        out.write("            </div>\n");
        out.write("            <h3 class=\"action-card-title\">Request Blood</h3>\n");
        out.write("            <p class=\"action-card-desc\">Initiate urgent blood requisitions for admitted hospital patients across Rajahmundry with priority triage.</p>\n");
        out.write("            <a href=\"/request-blood\" class=\"action-card-btn btn-primary\">\n");
        out.write("              <span>Request Blood</span>\n");
        out.write("              <span class=\"btn-icon\">→</span>\n");
        out.write("            </a>\n");
        out.write("          </article>\n");

        // Card 3: Become a Donor
        out.write("          <article class=\"action-card\">\n");
        out.write("            <div class=\"action-card-header\">\n");
        out.write("              <div class=\"action-icon-box box-mint\">❤️</div>\n");
        out.write("              <span class=\"badge-stage\" style=\"background: rgba(6, 214, 160, 0.15); color: var(--mint); border-color: rgba(6, 214, 160, 0.3);\">Join Network</span>\n");
        out.write("            </div>\n");
        out.write("            <h3 class=\"action-card-title\">Become a Donor</h3>\n");
        out.write("            <p class=\"action-card-desc\">Sign up as a voluntary lifesaver. Register your availability, locality, and group to respond to emergency requisitions.</p>\n");
        out.write("            <a href=\"/become-donor\" class=\"action-card-btn btn-primary\">\n");
        out.write("              <span>Register as Donor</span>\n");
        out.write("              <span class=\"btn-icon\">→</span>\n");
        out.write("            </a>\n");
        out.write("          </article>\n");

        // Card 4: View Blood Requests
        out.write("          <article class=\"action-card\">\n");
        out.write("            <div class=\"action-card-header\">\n");
        out.write("              <div class=\"action-icon-box box-purple\">📋</div>\n");
        out.write("              <span class=\"badge-stage\" style=\"background: rgba(0, 240, 255, 0.15); color: var(--icy-blue); border-color: rgba(0, 240, 255, 0.3);\">Community Board</span>\n");
        out.write("            </div>\n");
        out.write("            <h3 class=\"action-card-title\">View Blood Requests</h3>\n");
        out.write("            <p class=\"action-card-desc\">Monitor live patient requisitions, urgency triage statuses, hospital requirements, and required unit deadlines.</p>\n");
        out.write("            <a href=\"/view-requests\" class=\"action-card-btn btn-secondary\">\n");
        out.write("              <span>View Requisitions</span>\n");
        out.write("              <span class=\"btn-icon\">→</span>\n");
        out.write("            </a>\n");
        out.write("          </article>\n");

        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </section>\n");

        // 4. Blood Group Selector Section (Server-Side Form Submission, Zero JS)
        out.write("    <section class=\"compat-section\" id=\"blood-selector\">\n");
        out.write("      <div class=\"container\">\n");
        out.write("        <div class=\"section-head\">\n");
        out.write("          <span class=\"section-tag\">Server-Side Clinical Matrix</span>\n");
        out.write("          <h2 class=\"section-title\">Blood Group Compatibility Selector</h2>\n");
        out.write("          <p class=\"section-desc\">Select any blood group below. Submission is processed directly on the server by Java Servlets using standard HTML form submission without JavaScript.</p>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"compat-card\">\n");

        // Form with 8 blood group buttons
        out.write("          <form method=\"GET\" action=\"/dashboard#blood-selector\" class=\"blood-selector-form\">\n");
        out.write("            <fieldset class=\"blood-selector-fieldset\">\n");
        out.write("              <legend class=\"visually-hidden\">Select Blood Group</legend>\n");
        out.write("              <div class=\"blood-cards-grid\">\n");

        for (String group : BloodCompatibility.ALL_GROUPS) {
            boolean isActive = group.equalsIgnoreCase(selectedGroup);
            out.write("                <button type=\"submit\" name=\"bloodGroup\" value=\"" + group + "\" class=\"blood-card-btn" + (isActive ? " active" : "") + "\">\n");
            out.write("                  <span class=\"blood-card-badge\">" + group + "</span>\n");
            out.write("                  <span class=\"blood-card-label\">" + (isActive ? "Selected" : "Select") + "</span>\n");
            out.write("                </button>\n");
        }

        out.write("              </div>\n");
        out.write("            </fieldset>\n");
        out.write("          </form>\n");

        // Server-Side Results Card
        out.write("          <div class=\"compat-results-wrapper\">\n");
        out.write("            <div class=\"selected-group-banner\">\n");
        out.write("              <span class=\"banner-pill\">Server Processed Result</span>\n");
        out.write("              <h3 class=\"banner-title\">Blood Group: <span class=\"banner-highlight\">" + selectedGroup + "</span></h3>\n");
        out.write("            </div>\n");

        out.write("            <div class=\"compat-results-grid\">\n");
        out.write("              <div class=\"compat-box\">\n");
        out.write("                <div class=\"compat-box-header\">\n");
        out.write("                  <div class=\"compat-badge-icon box-coral\">↑</div>\n");
        out.write("                  <h4 class=\"compat-box-title\">Can Donate Blood To:</h4>\n");
        out.write("                </div>\n");
        out.write("                <div class=\"blood-tags-list\">\n");

        for (String g : canGiveTo) {
            out.write("                  <span class=\"blood-tag give\">" + g + "</span>\n");
        }

        out.write("                </div>\n");
        out.write("              </div>\n");

        out.write("              <div class=\"compat-box\">\n");
        out.write("                <div class=\"compat-box-header\">\n");
        out.write("                  <div class=\"compat-badge-icon box-cyan\">↓</div>\n");
        out.write("                  <h4 class=\"compat-box-title\">Can Receive Blood From:</h4>\n");
        out.write("                </div>\n");
        out.write("                <div class=\"blood-tags-list\">\n");

        for (String g : canReceiveFrom) {
            out.write("                  <span class=\"blood-tag receive\">" + g + "</span>\n");
        }

        out.write("                </div>\n");
        out.write("              </div>\n");
        out.write("            </div>\n");

        out.write("            <div class=\"compat-summary-box\">\n");
        out.write("              <span class=\"summary-icon\">ℹ️</span>\n");
        out.write("              <p class=\"compat-summary-text\"><strong>Medical Insight:</strong> " + clinicalSummary + "</p>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </section>\n");

        // 5. Registered Donors Section (Rajahmundry Localities)
        out.write("    <section class=\"donors-section\" id=\"registered-donors\">\n");
        out.write("      <div class=\"container\">\n");
        out.write("        <div class=\"section-head\">\n");
        out.write("          <span class=\"section-tag\">Community Network</span>\n");
        out.write("          <h2 class=\"section-title\">Registered Donors (Rajahmundry Area)</h2>\n");
        out.write("          <p class=\"section-desc\">Active voluntary donors registered across Rajahmundry localities. Filtered or matched by selected blood group <strong>(" + selectedGroup + ")</strong>.</p>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"demo-disclaimer-card\">\n");
        out.write("          <span class=\"disclaimer-icon\">ℹ️</span>\n");
        out.write("          <div class=\"disclaimer-content\">\n");
        out.write("            <strong>Voluntary Donor Notice &amp; Safety Protocol:</strong>\n");
        out.write("            <span>Donor availability reflects voluntary registrations. Compatibility must be verified by laboratory cross-matching before transfusion. For life-threatening emergencies, contact the hospital blood bank or dial 108 directly.</span>\n");
        out.write("          </div>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"donors-grid\">\n");

        for (Donor d : allDonors) {
            boolean isExactMatch = d.getBloodGroup().equalsIgnoreCase(selectedGroup);
            boolean isCompatibleGive = canGiveTo.contains(d.getBloodGroup());
            boolean isCompatibleReceive = canReceiveFrom.contains(d.getBloodGroup());

            out.write("          <article class=\"donor-card" + (isExactMatch ? " match-card" : "") + "\">\n");
            out.write("            <div class=\"donor-card-top\">\n");
            out.write("              <div class=\"donor-avatar\">\n");
            out.write("                <span>" + d.getName().substring(0, 1) + "</span>\n");
            out.write("              </div>\n");
            out.write("              <div class=\"donor-group-badge" + (isExactMatch ? " badge-exact" : "") + "\">\n");
            out.write("                " + d.getBloodGroup() + "\n");
            out.write("              </div>\n");
            out.write("            </div>\n");
            out.write("            <div class=\"donor-card-body\">\n");
            out.write("              <h4 class=\"donor-name\">" + d.getName() + "</h4>\n");
            out.write("              <p class=\"donor-locality\">📍 " + d.getLocality() + "</p>\n");
            out.write("              <div class=\"donor-meta-row\">\n");
            out.write("                <span class=\"donor-badge-tag\">" + d.getBadge() + "</span>\n");
            out.write("                <span class=\"donor-last-donated\">🕒 " + d.getLastDonated() + "</span>\n");
            out.write("              </div>\n");
            out.write("            </div>\n");
            out.write("            <div class=\"donor-card-footer\">\n");
            if (isExactMatch) {
                out.write("              <span class=\"match-status-tag exact\">★ Exact Blood Group Match</span>\n");
            } else if (isCompatibleReceive) {
                out.write("              <span class=\"match-status-tag compatible\">✓ Compatible Donor For " + selectedGroup + "</span>\n");
            } else {
                out.write("              <span class=\"match-status-tag general\">Community Hero</span>\n");
            }
            out.write("            </div>\n");
            out.write("          </article>\n");
        }

        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </section>\n");

        // 6. How It Works Section
        out.write("    <section class=\"how-it-works\" id=\"how-it-works\">\n");
        out.write("      <div class=\"container\">\n");
        out.write("        <div class=\"section-head\">\n");
        out.write("          <span class=\"section-tag\">Transparent &amp; Reliable</span>\n");
        out.write("          <h2 class=\"section-title\">How Blood Buddy Works</h2>\n");
        out.write("          <p class=\"section-desc\">Designed for life-saving speed during medical emergencies with zero friction.</p>\n");
        out.write("        </div>\n");

        out.write("        <div class=\"steps-grid\">\n");
        out.write("          <article class=\"step-card\">\n");
        out.write("            <span class=\"step-number\">01</span>\n");
        out.write("            <div class=\"step-icon-wrap step-icon-1\">📋</div>\n");
        out.write("            <h3 class=\"step-title\">1. Register &amp; Verify</h3>\n");
        out.write("            <p class=\"step-desc\">Voluntary donors specify their blood type and local Rajahmundry zone. Identity checks ensure medical authenticity.</p>\n");
        out.write("          </article>\n");
        out.write("          <article class=\"step-card\">\n");
        out.write("            <span class=\"step-number\">02</span>\n");
        out.write("            <div class=\"step-icon-wrap step-icon-2\">📍</div>\n");
        out.write("            <h3 class=\"step-title\">2. Localized Matching</h3>\n");
        out.write("            <p class=\"step-desc\">When a patient needs blood, our engine matches compatible types within a 5-10 km radius across hospital wards.</p>\n");
        out.write("          </article>\n");
        out.write("          <article class=\"step-card\">\n");
        out.write("            <span class=\"step-number\">03</span>\n");
        out.write("            <div class=\"step-icon-wrap step-icon-3\">❤️</div>\n");
        out.write("            <h3 class=\"step-title\">3. Coordinate &amp; Transfuse</h3>\n");
        out.write("            <p class=\"step-desc\">Direct, secure liaison with accredited blood bank staff and hospital labs guarantees safe, timely transfusions.</p>\n");
        out.write("          </article>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </section>\n");

        out.write("  </main>\n");

        // 7. Footer (Zero JavaScript)
        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container\">\n");
        out.write("      <div class=\"footer-top\">\n");
        out.write("        <div>\n");
        out.write("          <div class=\"brand\">\n");
        out.write("            <div class=\"brand-logo-wrap\">\n");
        out.write("              <img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Logo\" width=\"36\" height=\"36\">\n");
        out.write("            </div>\n");
        out.write("            <div class=\"brand-text\">\n");
        out.write("              <span class=\"brand-name\">" + AppConfig.APP_NAME + "</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("          <p class=\"footer-brand-desc\">" + AppConfig.TAGLINE + "<br>Community-focused blood donation and emergency matching platform for Rajahmundry and surrounding regions.</p>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Navigation</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"/dashboard\">Home Dashboard</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Find Donors</a></li>\n");
        out.write("            <li><a href=\"/request-blood\">Request Blood</a></li>\n");
        out.write("            <li><a href=\"/camps\">Donation Camps</a></li>\n");
        out.write("            <li><a href=\"/more\">More Info</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Platform Services</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"/dashboard\">Home Dashboard</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Find Donors Directory</a></li>\n");
        out.write("            <li><a href=\"/become-donor\">Become a Donor</a></li>\n");
        out.write("            <li><a href=\"/request-blood\">Emergency Requisitions</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Healthcare Emergency</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"tel:108\" style=\"color: var(--neon-coral); font-weight: 700;\">🚨 Emergency Helpline: 108</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Rajahmundry Govt Hospital Blood Bank</a></li>\n");
        out.write("            <li><a href=\"/about\">Red Cross Society Rajahmundry</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("      <div class=\"footer-bottom\">\n");
        out.write("        <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved. &bull; <a href=\"/admin/login\" style=\"color: var(--text-muted); text-decoration: none;\">Hospital Staff Portal</a></div>\n");
        out.write("        <div class=\"backend-status-badge\">\n");
        out.write("          <span class=\"status-dot\"></span> System Active &amp; Operational\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");

        // 3D Heart Visualization (Three.js WebGL & Anatomical GLTF Model)
        out.write("  <script src=\"/static/js/three.min.js\"></script>\n");
        out.write("  <script src=\"/static/js/GLTFLoader.js\"></script>\n");
        out.write("  <script src=\"/static/js/heart-3d.js?v=3.0\"></script>\n");
        out.write("  <script src=\"/static/js/main.js\"></script>\n");
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

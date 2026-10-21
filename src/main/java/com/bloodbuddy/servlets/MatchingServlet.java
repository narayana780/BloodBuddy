package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.DonorMatch;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.BloodRequestService;
import com.bloodbuddy.service.DonorMatchingService;
import com.bloodbuddy.service.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * MatchingServlet renders the Smart Donor Matching Results page (Stage 7).
 * Connects blood requests with compatible available lifesaver records.
 * Provides transparent explanations, privacy protection for donor contact info,
 * and hospital admin workflow integration.
 */
public class MatchingServlet extends HttpServlet {

    private final BloodRequestService requestService = new BloodRequestService();
    private final DonorMatchingService matchingService = new DonorMatchingService();
    private final NotificationService notificationService = new NotificationService();
    private final AuthService authService = new AuthService();
    private final com.bloodbuddy.service.ChatService chatService = new com.bloodbuddy.service.ChatService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String reqIdParam = req.getParameter("requestId");
        if (reqIdParam == null || reqIdParam.trim().isEmpty()) {
            reqIdParam = req.getParameter("id");
        }

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);
        int unreadMsgCount = (currentUser != null) ? chatService.getUnreadMessageCount(currentUser.getId()) : 0;

        try (PrintWriter out = resp.getWriter()) {
            if (reqIdParam == null || reqIdParam.trim().isEmpty()) {
                // Render request selector page
                renderRequestSelectorPage(out, requestService.getAllRequests(), unreadCount, unreadMsgCount, isAdmin, currentUser);
                return;
            }

            String cleanId = reqIdParam.trim();
            Optional<BloodRequest> reqOpt = requestService.getRequestById(cleanId);

            if (reqOpt.isEmpty()) {
                renderNotFoundPage(out, cleanId, unreadCount, unreadMsgCount, isAdmin, currentUser);
                return;
            }

            BloodRequest request = reqOpt.get();
            List<DonorMatch> matches = matchingService.findMatches(request);

            // Automatically record notification on match evaluation (with duplicate protection)
            if (matches.isEmpty()) {
                notificationService.notifyNoMatches(request);
            } else {
                notificationService.notifyMatchesFound(request, matches.size());
            }

            renderMatchingPage(out, request, matches, unreadCount, unreadMsgCount, isAdmin, currentUser);
        }
    }

    private void renderMatchingPage(PrintWriter out, BloodRequest request, List<DonorMatch> matches,
                                    int unreadCount, int unreadMsgCount, boolean isAdmin, User currentUser) {

        long exactMatchCount = matches.stream().filter(DonorMatch::isExactMatch).count();
        long compatibleCount = matches.size() - exactMatchCount;

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Smart Donor Matches for " + escapeHtml(request.getId()) + " | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Intelligent ABO/Rh donor matching results for requisition " + escapeHtml(request.getId()) + ".\">\n");
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

        out.write("  <main class=\"request-board-main\">\n");
        out.write("    <div class=\"container\">\n");

        // Top Banner / Stage Indicator
        out.write("      <div class=\"matching-top-bar\" style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px;\">\n");
        out.write("        <div style=\"display: flex; gap: 10px; align-items: center;\">\n");
        out.write("          <span class=\"stage-badge stage-badge-urgent\">Smart Donor Matching</span>\n");
        if (isAdmin) {
            out.write("          <span class=\"status-pill\" style=\"background: rgba(6, 214, 160, 0.15); border-color: rgba(6, 214, 160, 0.4); color: var(--mint-light);\">🛡️ Admin Coordination Active</span>\n");
        }
        out.write("        </div>\n");
        out.write("        <div>\n");
        out.write("          <a href=\"/matching\" class=\"btn btn-secondary\" style=\"padding: 6px 14px; font-size: 0.8rem;\">Select Another Request ▾</a>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Requisition Context Card (Midnight Glassmorphism)
        out.write("      <section class=\"match-requisition-card glass-panel\" style=\"background: linear-gradient(135deg, rgba(28, 16, 54, 0.85) 0%, rgba(42, 18, 75, 0.7) 100%); border: 1px solid rgba(255, 87, 87, 0.3); border-radius: var(--radius-lg); padding: 28px 32px; margin-bottom: 30px; box-shadow: var(--shadow-glow-card);\">\n");
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 20px;\">\n");

        // Left info
        out.write("          <div style=\"flex: 1; min-width: 300px;\">\n");
        out.write("            <div style=\"display: flex; gap: 12px; align-items: center; margin-bottom: 10px;\">\n");
        out.write("              <span class=\"req-id-badge\">" + escapeHtml(request.getId()) + "</span>\n");
        out.write("              <span class=\"urgency-badge urgency-" + escapeHtml(request.getUrgency().toLowerCase()) + "\">" + escapeHtml(request.getUrgency()) + " Priority</span>\n");
        out.write("              <span class=\"req-status-pill status-" + escapeHtml(request.getStatus().toLowerCase()) + "\">● " + escapeHtml(request.getStatus()) + "</span>\n");
        out.write("            </div>\n");
        out.write("            <h1 style=\"font-size: 1.8rem; margin: 0 0 10px; color: var(--text-white); font-family: var(--font-heading);\">Patient: " + escapeHtml(request.getPatientName()) + "</h1>\n");
        out.write("            <div style=\"color: var(--text-secondary); font-size: 0.95rem; line-height: 1.6;\">\n");
        out.write("              <div>🏥 <strong>Hospital:</strong> " + escapeHtml(request.getHospitalName()) + "</div>\n");
        out.write("              <div>📍 <strong>Facility Locality:</strong> " + escapeHtml(request.getHospitalLocality()) + "</div>\n");
        out.write("              <div>🕒 <strong>Required By:</strong> " + escapeHtml(request.getRequiredBy()) + "</div>\n");
        if (!request.getNotes().isEmpty()) {
            out.write("              <div style=\"margin-top: 6px;\">📝 <strong>Clinical Notes:</strong> " + escapeHtml(request.getNotes()) + "</div>\n");
        }
        out.write("            </div>\n");
        out.write("          </div>\n");

        // Right Blood Badge & Stats
        out.write("          <div style=\"display: flex; flex-direction: column; align-items: flex-end; gap: 14px;\">\n");
        out.write("            <div style=\"display: flex; align-items: center; gap: 16px; background: rgba(0, 0, 0, 0.25); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 14px 22px;\">\n");
        out.write("              <div class=\"req-blood-badge\" style=\"padding: 8px 18px;\">\n");
        out.write("                <span class=\"req-drop-icon\" style=\"font-size: 1.6rem;\">🩸</span>\n");
        out.write("                <span class=\"req-group-text\" style=\"font-size: 2rem;\">" + escapeHtml(request.getBloodGroup()) + "</span>\n");
        out.write("              </div>\n");
        out.write("              <div style=\"text-align: left;\">\n");
        out.write("                <div style=\"font-size: 1.4rem; font-weight: 700; color: var(--text-white);\">" + request.getUnits() + " Unit" + (request.getUnits() > 1 ? "s" : "") + "</div>\n");
        out.write("                <div style=\"font-size: 0.8rem; color: var(--text-muted);\">Requisition Demand</div>\n");
        out.write("              </div>\n");
        out.write("            </div>\n");

        // Admin Workflow: Status update controls
        if (isAdmin) {
            out.write("            <div style=\"background: rgba(255, 255, 255, 0.05); border: 1px solid var(--border-glass); border-radius: var(--radius-sm); padding: 10px 14px; text-align: right;\">\n");
            out.write("              <form method=\"POST\" action=\"/admin/update-request-status\" style=\"display: flex; gap: 8px; align-items: center; margin: 0;\">\n");
            out.write("                <input type=\"hidden\" name=\"requestId\" value=\"" + escapeHtml(request.getId()) + "\">\n");
            out.write("                <label style=\"font-size: 0.82rem; color: var(--text-secondary); font-weight: 600;\">Update Status:</label>\n");
            out.write("                <select name=\"status\" class=\"form-input\" style=\"padding: 4px 8px; font-size: 0.82rem; height: auto;\">\n");
            out.write("                  <option value=\"Pending\" " + ("Pending".equalsIgnoreCase(request.getStatus()) ? "selected" : "") + ">Pending</option>\n");
            out.write("                  <option value=\"In Progress\" " + ("In Progress".equalsIgnoreCase(request.getStatus()) ? "selected" : "") + ">In Progress</option>\n");
            out.write("                  <option value=\"Fulfilled\" " + ("Fulfilled".equalsIgnoreCase(request.getStatus()) ? "selected" : "") + ">Fulfilled</option>\n");
            out.write("                </select>\n");
            out.write("                <button type=\"submit\" class=\"btn btn-primary\" style=\"padding: 4px 10px; font-size: 0.8rem;\">Save</button>\n");
            out.write("              </form>\n");
            out.write("            </div>\n");
        }
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Match summary bar
        out.write("        <div style=\"display: flex; justify-content: space-between; align-items: center; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--border-glass); flex-wrap: wrap; gap: 12px;\">\n");
        out.write("          <div style=\"display: flex; gap: 18px; align-items: center; flex-wrap: wrap;\">\n");
        out.write("            <span style=\"font-size: 1rem; font-weight: 700; color: var(--text-white);\">Candidate Pool Analysis:</span>\n");
        out.write("            <span class=\"badge-matches\" style=\"padding: 5px 12px; font-size: 0.85rem; border-radius: 9999px;\">⚡ Total Matches: " + matches.size() + "</span>\n");
        out.write("            <span style=\"color: var(--neon-coral); font-size: 0.85rem; font-weight: 600;\">🩸 " + exactMatchCount + " Exact Group</span>\n");
        out.write("            <span style=\"color: var(--icy-blue-soft); font-size: 0.85rem; font-weight: 600;\">🧬 " + compatibleCount + " Compatible Alternative</span>\n");
        out.write("          </div>\n");
        out.write("          <div style=\"font-size: 0.82rem; color: var(--text-muted);\">\n");
        out.write("            Logged: " + escapeHtml(request.getCreatedAt()) + "\n");
        out.write("          </div>\n");
        out.write("        </div>\n");
        out.write("      </section>\n");

        // Mandatory Preliminary Match Disclaimer
        out.write("      <div class=\"medical-disclaimer-box\" style=\"background: rgba(230, 25, 75, 0.1); border: 1px solid rgba(230, 25, 75, 0.35); border-radius: var(--radius-md); padding: 16px 20px; margin-bottom: 30px; display: flex; gap: 14px; align-items: flex-start;\">\n");
        out.write("        <div style=\"font-size: 1.5rem; line-height: 1;\">⚠️</div>\n");
        out.write("        <div style=\"font-size: 0.88rem; line-height: 1.5; color: var(--text-primary);\">\n");
        out.write("          <strong style=\"color: var(--neon-coral);\">Preliminary Match Notice — Medical Confirmation Required:</strong><br>\n");
        out.write("          These algorithmic matches are calculated from ABO/Rh red-blood-cell compatibility tables and volunteer availability records. Blood-group compatibility alone does NOT guarantee a medically safe or suitable donation. <em>All clinical transfusions strictly require standard serological cross-matching and formal authorization by qualified hospital laboratory staff.</em>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Matches Section
        out.write("      <section class=\"matches-grid-section\">\n");

        if (matches.isEmpty()) {
            // Empathetic, helpful empty state per Section 4
            out.write("        <div class=\"empty-state-card glass-panel\" style=\"padding: 60px 24px; text-align: center; border-radius: var(--radius-lg);\">\n");
            out.write("          <div style=\"font-size: 3.5rem; margin-bottom: 12px;\">🩸</div>\n");
            out.write("          <h2 style=\"font-size: 1.8rem; margin: 0 0 10px; color: var(--text-white); font-family: var(--font-heading);\">No Matching Donors Found in Local Records</h2>\n");
            out.write("          <p style=\"max-width: 620px; margin: 0 auto 24px; color: var(--text-secondary); line-height: 1.6;\">\n");
            out.write("            Currently, no registered donors match requested blood group <strong>" + escapeHtml(request.getBloodGroup()) + "</strong> with confirmed available status in Rajahmundry.<br>\n");
            out.write("            <em>Please do not delay care. Take immediate direct action below:</em>\n");
            out.write("          </p>\n");

            out.write("          <div style=\"max-width: 560px; margin: 0 auto 28px; background: rgba(0, 0, 0, 0.3); border: 1px solid var(--border-glass); border-radius: var(--radius-md); padding: 18px 24px; text-align: left;\">\n");
            out.write("            <h4 style=\"margin: 0 0 10px; color: var(--neon-coral); font-size: 1rem;\">🚨 Immediate Authorized Contacts:</h4>\n");
            out.write("            <ul style=\"margin: 0; padding-left: 20px; color: var(--text-primary); font-size: 0.9rem; line-height: 1.7;\">\n");
            out.write("              <li><strong>Emergency Medical Helpline:</strong> Call <a href=\"tel:108\" style=\"color: var(--neon-coral); font-weight: 700;\">108</a> immediately.</li>\n");
            out.write("              <li><strong>Rajahmundry Govt General Hospital Blood Bank:</strong> Danavaipeta (<a href=\"tel:08832450000\" style=\"color: var(--icy-blue-soft);\">0883-2450000</a>)</li>\n");
            out.write("              <li><strong>Red Cross Society Blood Centre:</strong> Kambala Tank, Rajahmundry</li>\n");
            out.write("              <li>Contact the treating hospital's on-duty clinical blood coordinator directly.</li>\n");
            out.write("            </ul>\n");
            out.write("          </div>\n");

            out.write("          <div style=\"display: flex; gap: 14px; justify-content: center; flex-wrap: wrap;\">\n");
            out.write("            <a href=\"/find-donors?bloodGroup=" + URLEncoder.encode(request.getBloodGroup(), StandardCharsets.UTF_8) + "\" class=\"btn btn-primary\">\n");
            out.write("              <span>Search Full Voluntary Directory</span>\n");
            out.write("              <span class=\"btn-icon\">🔍</span>\n");
            out.write("            </a>\n");
            out.write("            <a href=\"/view-requests\" class=\"btn btn-secondary\">\n");
            out.write("              <span>Return to Requisitions Board</span>\n");
            out.write("            </a>\n");
            out.write("          </div>\n");
            out.write("        </div>\n");

        } else {
            // Matches Grid
            out.write("        <div style=\"margin-bottom: 18px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;\">\n");
            out.write("          <h2 style=\"font-size: 1.4rem; margin: 0; color: var(--text-white); font-family: var(--font-heading);\">Ranked Candidate Lifesavers</h2>\n");
            out.write("          <div style=\"font-size: 0.85rem; color: var(--text-muted);\">Ordered transparently by Compatibility Tier, Availability, and Locality</div>\n");
            out.write("        </div>\n");

            out.write("        <div class=\"requests-grid\" style=\"display: grid; grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); gap: 24px;\">\n");

            int rankNumber = 1;
            for (DonorMatch match : matches) {
                Donor donor = match.getDonor();
                boolean isExact = match.isExactMatch();

                String tierBadgeClass = isExact ? "badge-exact-tier" : "badge-compat-tier";
                String tierLabel = isExact ? "🩸 Exact Blood-Group Match" : "🧬 Potentially Compatible Alternative";

                String locBadgeClass;
                String locLabel;
                switch (match.getLocationMatch()) {
                    case SAME_LOCALITY:
                        locBadgeClass = "badge-same-loc";
                        locLabel = "📍 Same Locality (" + escapeHtml(donor.getLocality()) + ")";
                        break;
                    case NEARBY_LOCALITY:
                        locBadgeClass = "badge-nearby-loc";
                        locLabel = "📍 Nearby Locality (" + escapeHtml(donor.getLocality()) + ")";
                        break;
                    case OTHER_LOCALITY:
                        locBadgeClass = "badge-other-loc";
                        locLabel = "📍 " + escapeHtml(donor.getLocality());
                        break;
                    default:
                        locBadgeClass = "badge-other-loc";
                        locLabel = "📍 Locality Not Specified";
                        break;
                }

                out.write("          <article class=\"match-card glass-panel " + (isExact ? "card-exact-border" : "card-compat-border") + "\" style=\"border-radius: var(--radius-md); padding: 24px; display: flex; flex-direction: column; justify-content: space-between; position: relative; transition: all 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);\">\n");

                // Header Tier & Rank
                out.write("            <div>\n");
                out.write("              <div style=\"display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; gap: 10px;\">\n");
                out.write("                <span class=\"match-tier-badge " + tierBadgeClass + "\">" + tierLabel + "</span>\n");
                out.write("                <span class=\"match-rank-badge\" title=\"Match ranking score: " + match.getRankScore() + "\">#" + rankNumber + " Candidate</span>\n");
                out.write("              </div>\n");

                // Donor Name & Blood Group Row
                out.write("              <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;\">\n");
                out.write("                <div>\n");
                out.write("                  <h3 style=\"font-size: 1.25rem; margin: 0 0 4px; color: var(--text-white); font-family: var(--font-heading);\">" + escapeHtml(donor.getName()) + "</h3>\n");
                out.write("                  <span style=\"font-size: 0.82rem; color: var(--text-muted);\">ID: " + escapeHtml(donor.getId()) + " • " + escapeHtml(donor.getBadge()) + "</span>\n");
                out.write("                </div>\n");
                out.write("                <div class=\"req-blood-badge\" style=\"padding: 6px 14px;\">\n");
                out.write("                  <span class=\"req-drop-icon\" style=\"font-size: 1.2rem;\">🩸</span>\n");
                out.write("                  <span class=\"req-group-text\" style=\"font-size: 1.4rem;\">" + escapeHtml(donor.getBloodGroup()) + "</span>\n");
                out.write("                </div>\n");
                out.write("              </div>\n");

                // Locality and Availability Badges
                out.write("              <div style=\"display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px;\">\n");
                out.write("                <span class=\"match-loc-badge " + locBadgeClass + "\">" + locLabel + "</span>\n");
                out.write("                " + DonorProfileServlet.renderAvailabilityBadge(donor) + "\n");
                out.write("              </div>\n");

                // Transparent Explanations List
                out.write("              <div style=\"background: rgba(0, 0, 0, 0.25); border: 1px solid var(--border-glass); border-radius: var(--radius-sm); padding: 12px 14px; margin-bottom: 16px;\">\n");
                out.write("                <div style=\"font-size: 0.78rem; text-transform: uppercase; font-weight: 700; color: var(--text-muted); letter-spacing: 0.05em; margin-bottom: 6px;\">Match Rationale:</div>\n");
                out.write("                <ul style=\"margin: 0; padding-left: 18px; color: var(--text-secondary); font-size: 0.82rem; line-height: 1.5;\">\n");
                for (String exp : match.getMatchExplanations()) {
                    out.write("                  <li>" + escapeHtml(exp) + "</li>\n");
                }
                out.write("                </ul>\n");
                out.write("              </div>\n");
                out.write("            </div>\n");

                // Card Footer with Privacy Shield / Admin Phone Access
                out.write("            <div style=\"border-top: 1px solid var(--border-glass); padding-top: 14px; margin-top: 10px;\">\n");
                if (isAdmin) {
                    // Authorized Hospital Admin View
                    String contact = donor.getContactInfo();
                    if (contact == null || contact.trim().isEmpty()) {
                        contact = "Volunteer coordination via Rajahmundry Blood Centre";
                    }
                    out.write("              <div style=\"background: rgba(6, 214, 160, 0.1); border: 1px solid rgba(6, 214, 160, 0.3); border-radius: var(--radius-sm); padding: 10px 12px; margin-bottom: 10px;\">\n");
                    out.write("                <div style=\"font-size: 0.75rem; text-transform: uppercase; font-weight: 700; color: var(--mint-light); margin-bottom: 4px;\">🛡️ Hospital Staff Coordination Contact:</div>\n");
                    out.write("                <div style=\"font-size: 0.95rem; font-weight: 600; color: var(--text-white); font-family: monospace;\">" + escapeHtml(contact) + "</div>\n");
                    out.write("              </div>\n");
                    out.write("              <div style=\"display: flex; gap: 8px;\">\n");
                    out.write("                <a href=\"/messages?start=true&donorId=" + URLEncoder.encode(donor.getId(), StandardCharsets.UTF_8) + "&requestId=" + URLEncoder.encode(request.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-primary\" style=\"flex: 1; justify-content: center; padding: 8px; font-size: 0.82rem;\">\n");
                    out.write("                  <span>💬 Chat with Donor</span>\n");
                    out.write("                </a>\n");
                    out.write("                <a href=\"/donor-profile?id=" + URLEncoder.encode(donor.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-secondary\" style=\"flex: 1; justify-content: center; padding: 8px; font-size: 0.82rem;\">\n");
                    out.write("                  <span>Full Profile</span>\n");
                    out.write("                </a>\n");
                    out.write("              </div>\n");
                } else {
                    // Public / Non-Admin View: Strictly Shielded per Section 4 & 7
                    out.write("              <div style=\"display: flex; gap: 8px; align-items: center; color: var(--text-muted); font-size: 0.82rem; margin-bottom: 10px;\">\n");
                    out.write("                <span>🔒</span>\n");
                    out.write("                <span>Contact info shielded for donor privacy. Chat securely below.</span>\n");
                    out.write("              </div>\n");
                    out.write("              <div style=\"display: flex; gap: 8px; flex-wrap: wrap;\">\n");
                    out.write("                <a href=\"/messages?start=true&donorId=" + URLEncoder.encode(donor.getId(), StandardCharsets.UTF_8) + "&requestId=" + URLEncoder.encode(request.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-primary\" style=\"flex: 1; justify-content: center; padding: 8px; font-size: 0.82rem;\" title=\"Direct private chat with matched donor\">\n");
                    out.write("                  <span>💬 Chat with Donor</span>\n");
                    out.write("                </a>\n");
                    out.write("                <a href=\"/donor-profile?id=" + URLEncoder.encode(donor.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-secondary\" style=\"padding: 8px 12px; font-size: 0.82rem;\">\n");
                    out.write("                  <span>Public Profile</span>\n");
                    out.write("                </a>\n");
                    out.write("                <a href=\"/admin/login?redirect=" + URLEncoder.encode("/matching?requestId=" + request.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-secondary\" style=\"padding: 8px 10px; font-size: 0.82rem;\" title=\"Staff sign-in to view contact\">\n");
                    out.write("                  <span>🛡️ Staff</span>\n");
                    out.write("                </a>\n");
                    out.write("              </div>\n");
                }
                out.write("            </div>\n");

                out.write("          </article>\n");
                rankNumber++;
            }

            out.write("        </div>\n");
        }

        out.write("      </section>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Smart Donor Matching. Preliminary ABO/Rh compatibility reference. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
        out.write("</body>\n</html>\n");
    }

    private void renderRequestSelectorPage(PrintWriter out, List<BloodRequest> allRequests,
                                           int unreadCount, int unreadMsgCount, boolean isAdmin, User currentUser) {

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Select Request for Smart Matching | Blood Buddy</title>\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        // Header Navigation
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\">\n");
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
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link active-nav\">View Requests</a></li>\n");
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

        out.write("  <main class=\"request-board-main\">\n");
        out.write("    <div class=\"container\">\n");
        out.write("      <div class=\"request-header-section\" style=\"margin-bottom: 24px;\">\n");
        out.write("        <div class=\"stage-badge stage-badge-urgent\">Smart Donor Matching</div>\n");
        out.write("        <h1 class=\"section-title\">Select a Blood Requisition</h1>\n");
        out.write("        <p class=\"section-subtitle\">Choose any active patient requisition to run the ABO/Rh compatibility and locality matching algorithm.</p>\n");
        out.write("      </div>\n");

        out.write("      <div style=\"display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 20px;\">\n");
        for (BloodRequest r : allRequests) {
            out.write("        <div class=\"glass-panel\" style=\"padding: 22px; border-radius: var(--radius-md); border: 1px solid var(--border-glass);\">\n");
            out.write("          <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;\">\n");
            out.write("            <span class=\"req-id-badge\">" + escapeHtml(r.getId()) + "</span>\n");
            out.write("            <span class=\"urgency-badge urgency-" + escapeHtml(r.getUrgency().toLowerCase()) + "\">" + escapeHtml(r.getUrgency()) + "</span>\n");
            out.write("          </div>\n");
            out.write("          <h3 style=\"margin: 0 0 6px; font-size: 1.15rem; color: var(--text-white);\">" + escapeHtml(r.getPatientName()) + "</h3>\n");
            out.write("          <div style=\"font-size: 0.88rem; color: var(--text-secondary); margin-bottom: 14px;\">\n");
            out.write("            <div>🩸 Group: <strong>" + escapeHtml(r.getBloodGroup()) + "</strong> (" + r.getUnits() + " units)</div>\n");
            out.write("            <div>🏥 " + escapeHtml(r.getHospitalName()) + "</div>\n");
            out.write("            <div>📍 " + escapeHtml(r.getHospitalLocality()) + "</div>\n");
            out.write("          </div>\n");
            out.write("          <a href=\"/matching?requestId=" + URLEncoder.encode(r.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-primary\" style=\"width: 100%; justify-content: center; padding: 10px;\">\n");
            out.write("            <span>Find Matching Donors</span>\n");
            out.write("            <span class=\"btn-icon\">⚡</span>\n");
            out.write("          </a>\n");
            out.write("        </div>\n");
        }
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
        out.write("</body>\n</html>\n");
    }

    private void renderNotFoundPage(PrintWriter out, String invalidId, int unreadCount, int unreadMsgCount, boolean isAdmin, User currentUser) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"UTF-8\"><title>Request Not Found | Blood Buddy</title><link rel=\"stylesheet\" href=\"/static/css/style.css\"></head><body>\n");
        out.write("  <div class=\"container\" style=\"padding-top: 60px;\">\n");
        out.write("    <div class=\"empty-state-card glass-panel\" style=\"padding: 50px 24px; text-align: center; border-radius: var(--radius-lg);\">\n");
        out.write("      <div class=\"empty-icon\">🔍</div>\n");
        out.write("      <h2 class=\"empty-title\">Requisition Not Found</h2>\n");
        out.write("      <p class=\"empty-desc\">No blood requisition was found matching ID: <strong>" + escapeHtml(invalidId) + "</strong>.</p>\n");
        out.write("      <div class=\"empty-actions\">\n");
        out.write("        <a href=\"/matching\" class=\"btn btn-primary\"><span>Select from Active Requests</span><span class=\"btn-icon\">📋</span></a>\n");
        out.write("        <a href=\"/view-requests\" class=\"btn btn-secondary\"><span>View Requests Board</span></a>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </div>\n</body></html>\n");
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

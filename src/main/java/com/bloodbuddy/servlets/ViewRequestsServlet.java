package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.service.BloodRequestService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.NotificationService;

/**
 * ViewRequestsServlet displays all submitted blood requisitions loaded from local storage.
 * Provides client-side JavaScript filtering and server-side fallback.
 * Strictly shields confidential contact phone numbers from public viewing.
 */
public class ViewRequestsServlet extends HttpServlet {

    private final BloodRequestService requestService = new BloodRequestService();
    private final NotificationService notificationService = new NotificationService();
    private final AuthService authService = new AuthService();
    private final com.bloodbuddy.service.ChatService chatService = new com.bloodbuddy.service.ChatService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(currentUser != null ? currentUser.getId() : null, isAdmin);
        int unreadMsgCount = (currentUser != null) ? chatService.getUnreadMessageCount(currentUser.getId()) : 0;

        String selectedGroup = req.getParameter("bloodGroup");
        String selectedUrgency = req.getParameter("urgency");
        String query = req.getParameter("q");
        String filterParam = req.getParameter("filter");
        boolean isMyFilter = "my".equalsIgnoreCase(filterParam) && currentUser != null;

        String cleanGroup = (selectedGroup != null) ? selectedGroup.trim() : "";
        String cleanUrgency = (selectedUrgency != null) ? selectedUrgency.trim() : "";
        String cleanQuery = (query != null) ? query.trim() : "";

        // Server-side loaded data from Java backend
        List<BloodRequest> allRequests = requestService.getAllRequests();
        List<BloodRequest> filteredRequests;

        if (isMyFilter) {
            final String uPhone = currentUser.getPhone() != null ? currentUser.getPhone().replaceAll("[^0-9]", "") : "";
            final String uName = currentUser.getFullName() != null ? currentUser.getFullName().trim().toLowerCase() : "";
            filteredRequests = allRequests.stream()
                    .filter(r -> {
                        String rPhone = r.getContactPhone() != null ? r.getContactPhone().replaceAll("[^0-9]", "") : "";
                        String rPerson = r.getContactPerson() != null ? r.getContactPerson().trim().toLowerCase() : "";
                        return (!uPhone.isEmpty() && rPhone.equals(uPhone)) || (!uName.isEmpty() && rPerson.contains(uName));
                    })
                    .toList();
        } else {
            filteredRequests = requestService.filterRequests(cleanGroup, cleanUrgency, cleanQuery);
        }

        try (PrintWriter out = resp.getWriter()) {
            renderHtml(out, allRequests, filteredRequests, cleanGroup, cleanUrgency, cleanQuery, unreadCount, unreadMsgCount, isAdmin, currentUser, isMyFilter);
        }
    }

    private void renderHtml(PrintWriter out, List<BloodRequest> allRequests,
                            List<BloodRequest> displayRequests, String selectedGroup,
                            String selectedUrgency, String query, int unreadCount, int unreadMsgCount,
                            boolean isAdmin, User currentUser, boolean isMyFilter) {

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Blood Requests Board — Live Emergency Requisitions | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Browse active patient blood requisitions across Rajahmundry hospitals. Filter by urgency, blood group, and locality.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        // Header Navigation
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
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("          <li><a href=\"/find-donors\" class=\"nav-link\">Find Donors</a></li>\n");
        out.write("          <li><a href=\"/camps\" class=\"nav-link\">Camps</a></li>\n");
        out.write("          <li><a href=\"/become-donor\" class=\"nav-link\">Become a Donor</a></li>\n");
        out.write("          <li><a href=\"/request-blood\" class=\"nav-link\">Request Blood</a></li>\n");
        out.write("          <li><a href=\"/view-requests\" class=\"nav-link active-nav\">View Requests</a></li>\n");
        out.write("        </ul>\n");
        out.write("      </nav>\n");
        out.write("      <div class=\"nav-actions\">\n");
        if (currentUser != null) {
            out.write("        <a href=\"/messages\" class=\"nav-utility-link\" title=\"Direct Messaging\">\n");
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

        // Page Header
        out.write("      <div class=\"request-header-section\">\n");
        out.write("        <div class=\"stage-badge stage-badge-urgent\">Active Requisitions Board</div>\n");
        out.write("        <h1 class=\"request-title\">Patient Blood Requisitions</h1>\n");
        out.write("        <p class=\"request-subtitle\">Verified patient blood requisitions across Rajahmundry healthcare facilities. Connect admitted patients directly with compatible volunteer lifesavers.</p>\n");
        out.write("      </div>\n");

        // Student Prototype & Safety Notice (Rule 7)
        out.write("      <div class=\"emergency-notice-card\" role=\"alert\">\n");
        out.write("        <div class=\"notice-icon-box\">⚠️</div>\n");
        out.write("        <div class=\"notice-content\">\n");
        out.write("          <h4 class=\"notice-heading\">Emergency Coordination &amp; Safety Protocol</h4>\n");
        out.write("          <p>Blood Buddy coordinates voluntary community blood requisitions. For active life-threatening clinical emergencies, please <strong>immediately contact treating hospital emergency desks or dial <a href=\"tel:108\" class=\"emergency-tel\">108</a></strong>.</p>\n");
        out.write("          <div class=\"notice-sub\">🔒 <strong>Privacy Assured:</strong> Patient phone numbers are kept strictly confidential and coordinated through authorized medical staff.</div>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        if (currentUser != null) {
            out.write("      <div style=\"display: flex; gap: 0.75rem; margin-bottom: 1.5rem; align-items: center; flex-wrap: wrap;\">\n");
            out.write("        <a href=\"/view-requests\" class=\"btn " + (!isMyFilter ? "btn-primary" : "btn-secondary") + " btn-sm\">All Active Requisitions (" + allRequests.size() + ")</a>\n");
            out.write("        <a href=\"/view-requests?filter=my\" class=\"btn " + (isMyFilter ? "btn-primary" : "btn-secondary") + " btn-sm\">📋 My Submitted Requisitions</a>\n");
            out.write("      </div>\n");
        }

        // Interactive Filter Panel
        out.write("      <section class=\"search-panel-card\" aria-label=\"Filter Blood Requests\">\n");
        out.write("        <form method=\"GET\" action=\"/view-requests\" class=\"donor-search-form\" id=\"filterForm\">\n");
        out.write("          <div class=\"search-fields-grid\" style=\"grid-template-columns: 1fr 1fr 1.4fr;\">\n");

        // Blood Group Filter
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"filterBloodGroup\" class=\"form-label\">Blood Group</label>\n");
        out.write("              <select id=\"filterBloodGroup\" name=\"bloodGroup\" class=\"form-control form-select\">\n");
        out.write("                <option value=\"ALL\"" + (selectedGroup.isEmpty() || "ALL".equalsIgnoreCase(selectedGroup) ? " selected" : "") + ">All Blood Groups (Any)</option>\n");
        for (String g : BloodCompatibility.ALL_GROUPS) {
            boolean isSel = g.equalsIgnoreCase(selectedGroup);
            out.write("                <option value=\"" + g + "\"" + (isSel ? " selected" : "") + ">" + g + "</option>\n");
        }
        out.write("              </select>\n");
        out.write("            </div>\n");

        // Urgency Filter
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"filterUrgency\" class=\"form-label\">Urgency Level</label>\n");
        out.write("              <select id=\"filterUrgency\" name=\"urgency\" class=\"form-control form-select\">\n");
        out.write("                <option value=\"ALL\"" + (selectedUrgency.isEmpty() || "ALL".equalsIgnoreCase(selectedUrgency) ? " selected" : "") + ">All Urgency Levels</option>\n");
        out.write("                <option value=\"Critical\"" + ("Critical".equalsIgnoreCase(selectedUrgency) ? " selected" : "") + ">🚨 Critical</option>\n");
        out.write("                <option value=\"Urgent\"" + ("Urgent".equalsIgnoreCase(selectedUrgency) ? " selected" : "") + ">🟠 Urgent</option>\n");
        out.write("                <option value=\"Normal\"" + ("Normal".equalsIgnoreCase(selectedUrgency) ? " selected" : "") + ">🟢 Normal</option>\n");
        out.write("              </select>\n");
        out.write("            </div>\n");

        // Hospital / Locality Search
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"searchQuery\" class=\"form-label\">Hospital / Locality</label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">🔍</span>\n");
        out.write("                <input type=\"text\" id=\"searchQuery\" name=\"q\" class=\"form-control\" \n");
        out.write("                       placeholder=\"e.g. Bollineni, Danavaipeta...\" value=\"" + escapeHtml(query) + "\">\n");
        out.write("              </div>\n");
        out.write("            </div>\n");

        out.write("          </div>\n"); // End search-fields-grid

        out.write("          <div class=\"search-actions-row\">\n");
        out.write("            <div class=\"search-actions-left\">\n");
        out.write("              <button type=\"submit\" class=\"btn btn-primary\">\n");
        out.write("                <span>Filter Requisitions</span>\n");
        out.write("                <span class=\"btn-icon\">🔍</span>\n");
        out.write("              </button>\n");
        out.write("              <a href=\"/view-requests\" class=\"btn btn-secondary\" id=\"resetFilterBtn\">\n");
        out.write("                <span>Reset Filters</span>\n");
        out.write("                <span class=\"btn-icon\">↺</span>\n");
        out.write("              </a>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"search-status-counter\" id=\"counterText\">\n");
        out.write("              Showing <strong id=\"visibleCount\">" + displayRequests.size() + "</strong> of <strong>" + allRequests.size() + "</strong> total requisitions\n");
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("        </form>\n");
        out.write("      </section>\n");

        // Requests Cards Board Grid
        out.write("      <section class=\"requests-board-section\" aria-label=\"Blood Requests List\">\n");

        if (allRequests.isEmpty()) {
            out.write("        <div class=\"empty-state-card\">\n");
            out.write("          <div class=\"empty-icon\">📋</div>\n");
            out.write("          <h3 class=\"empty-title\">No Blood Requests Submitted Yet</h3>\n");
            out.write("          <p class=\"empty-desc\">No patient requisitions have been logged. You can submit the first verified blood request below.</p>\n");
            out.write("          <div class=\"empty-actions\">\n");
            out.write("            <a href=\"/request-blood\" class=\"btn btn-primary\"><span>Submit Blood Request</span><span class=\"btn-icon\">🚨</span></a>\n");
            out.write("          </div>\n");
            out.write("        </div>\n");
        } else {
            out.write("        <div class=\"requests-cards-grid\" id=\"requestsGrid\">\n");

            for (BloodRequest r : displayRequests) {
                String urgencyClass = "urgency-" + r.getUrgency().toLowerCase();

                out.write("          <article class=\"request-card\" data-group=\"" + escapeHtml(r.getBloodGroup()) + "\" \n");
                out.write("                   data-urgency=\"" + escapeHtml(r.getUrgency()) + "\" \n");
                out.write("                   data-search=\"" + escapeHtml(r.getHospitalName() + " " + r.getHospitalLocality() + " " + r.getPatientName()).toLowerCase() + "\">\n");

                // Card Top Strip
                out.write("            <div class=\"req-card-header\">\n");
                out.write("              <div class=\"req-id-wrap\">\n");
                out.write("                <span class=\"req-id-pill\">ID: " + escapeHtml(r.getId()) + "</span>\n");
                out.write("              </div>\n");
                out.write("              <span class=\"urgency-badge " + urgencyClass + "\">" + escapeHtml(r.getUrgency()) + "</span>\n");
                out.write("            </div>\n");

                // Main Group & Units Row
                out.write("            <div class=\"req-card-blood-row\">\n");
                out.write("              <div class=\"req-blood-badge\">\n");
                out.write("                <span class=\"req-drop-icon\">🩸</span>\n");
                out.write("                <span class=\"req-group-text\">" + escapeHtml(r.getBloodGroup()) + "</span>\n");
                out.write("              </div>\n");
                out.write("              <div class=\"req-units-info\">\n");
                out.write("                <span class=\"req-units-count\">" + r.getUnits() + " Unit" + (r.getUnits() > 1 ? "s" : "") + "</span>\n");
                out.write("                <span class=\"req-status-pill status-" + r.getStatus().toLowerCase() + "\">● " + escapeHtml(r.getStatus()) + "</span>\n");
                out.write("              </div>\n");
                out.write("            </div>\n");

                boolean canViewSensitive = isAdmin || (currentUser != null && (
                        (currentUser.getFullName() != null && currentUser.getFullName().equalsIgnoreCase(r.getContactPerson())) ||
                        (currentUser.getPhone() != null && !currentUser.getPhone().isEmpty() && currentUser.getPhone().equals(r.getContactPhone()))
                ));

                // Details Content
                out.write("            <div class=\"req-card-body\">\n");
                if (canViewSensitive) {
                    out.write("              <div class=\"req-patient-name\">👤 " + escapeHtml(r.getPatientName()) + "</div>\n");
                } else {
                    out.write("              <div class=\"req-patient-name\" style=\"color: var(--text-muted); font-size: 0.88rem;\">👤 Patient: Confidential (Hospital Coordinated)</div>\n");
                }
                out.write("              <div class=\"req-hospital-info\">\n");
                out.write("                <span class=\"req-hospital-title\">🏥 " + escapeHtml(r.getHospitalName()) + "</span>\n");
                out.write("                <span class=\"req-locality-title\">📍 " + escapeHtml(r.getHospitalLocality()) + "</span>\n");
                out.write("              </div>\n");
                out.write("              <div class=\"req-deadline-row\">\n");
                out.write("                <span class=\"deadline-label\">Required By:</span>\n");
                out.write("                <span class=\"deadline-val\">🕒 " + escapeHtml(r.getRequiredBy()) + "</span>\n");
                out.write("              </div>\n");

                if (canViewSensitive && !r.getNotes().isEmpty()) {
                    out.write("              <div class=\"req-notes-snippet\">\n");
                    out.write("                <strong>Notes:</strong> " + escapeHtml(r.getNotes()) + "\n");
                    out.write("              </div>\n");
                }

                out.write("            </div>\n");

                // Card Footer with Actions
                out.write("            <div class=\"req-card-footer\" style=\"display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap;\">\n");
                out.write("              <span class=\"req-created-time\">Logged: " + escapeHtml(r.getCreatedAt()) + "</span>\n");
                out.write("              <div style=\"display: flex; gap: 8px;\">\n");
                out.write("                <a href=\"/matching?requestId=" + URLEncoder.encode(r.getId(), StandardCharsets.UTF_8) + "\" class=\"btn btn-primary req-action-btn\" style=\"background: var(--grad-blood); padding: 8px 14px; font-size: 0.82rem;\" title=\"Run Smart Donor Matching for " + escapeHtml(r.getId()) + "\">\n");
                out.write("                  <span>⚡ Matches</span>\n");
                out.write("                </a>\n");
                out.write("                <a href=\"/find-donors?bloodGroup=" + URLEncoder.encode(r.getBloodGroup(), StandardCharsets.UTF_8) + "\" class=\"btn btn-secondary req-action-btn\" style=\"padding: 8px 12px; font-size: 0.82rem;\" title=\"Search donors compatible with " + escapeHtml(r.getBloodGroup()) + "\">\n");
                out.write("                  <span>🔍 Donors</span>\n");
                out.write("                </a>\n");
                out.write("              </div>\n");
                out.write("            </div>\n");

                out.write("          </article>\n");
            }

            out.write("        </div>\n");
        }

        out.write("      </section>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        // Footer
        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container\">\n");
        out.write("      <div class=\"footer-top\">\n");
        out.write("        <div>\n");
        out.write("          <div class=\"brand\">\n");
        out.write("            <div class=\"brand-logo-wrap\"><img src=\"/static/images/logo.svg\" alt=\"Blood Buddy\" width=\"36\" height=\"36\"></div>\n");
        out.write("            <div class=\"brand-text\"><span class=\"brand-name\">" + AppConfig.APP_NAME + "</span></div>\n");
        out.write("          </div>\n");
        out.write("          <p class=\"footer-brand-desc\">" + AppConfig.TAGLINE + "<br>Community-focused patient requisition and voluntary coordination.</p>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Navigation</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"/dashboard\">Home Dashboard</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Find Donors</a></li>\n");
        out.write("            <li><a href=\"/become-donor\">Become a Donor</a></li>\n");
        out.write("            <li><a href=\"/request-blood\">Submit Requisition</a></li>\n");
        out.write("            <li><a href=\"/view-requests\">View Requisitions</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"footer-col\">\n");
        out.write("          <h5>Emergency Contacts</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"tel:108\" style=\"color: var(--neon-coral); font-weight: 700;\">🚨 Emergency Helpline: 108</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Rajahmundry Govt Hospital Blood Bank</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("      <div class=\"footer-bottom\">\n");
        out.write("        <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved. <a href=\"/admin/login\" style=\"color: var(--text-muted); font-size: 0.8rem; margin-left: 1rem;\">Hospital Staff Portal</a></div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");

        // Client-side JavaScript filtering for convenient instant interactions (Allowed in Stage 5 per Rule 1 & Rule 5)
        out.write("  <script>\n");
        out.write("    (function() {\n");
        out.write("      const groupSelect = document.getElementById('filterBloodGroup');\n");
        out.write("      const urgencySelect = document.getElementById('filterUrgency');\n");
        out.write("      const searchInput = document.getElementById('searchQuery');\n");
        out.write("      const grid = document.getElementById('requestsGrid');\n");
        out.write("      const visibleCountElem = document.getElementById('visibleCount');\n");
        out.write("      if (!grid) return;\n");
        out.write("      const cards = Array.from(grid.getElementsByClassName('request-card'));\n");
        out.write("\n");
        out.write("      function applyClientFilter() {\n");
        out.write("        const selGroup = groupSelect.value.trim().toUpperCase();\n");
        out.write("        const selUrgency = urgencySelect.value.trim().toLowerCase();\n");
        out.write("        const q = searchInput.value.trim().toLowerCase();\n");
        out.write("        let visible = 0;\n");
        out.write("\n");
        out.write("        cards.forEach(card => {\n");
        out.write("          const cGroup = (card.getAttribute('data-group') || '').toUpperCase();\n");
        out.write("          const cUrgency = (card.getAttribute('data-urgency') || '').toLowerCase();\n");
        out.write("          const cSearch = (card.getAttribute('data-search') || '').toLowerCase();\n");
        out.write("\n");
        out.write("          const matchGroup = (selGroup === 'ALL' || selGroup === '' || cGroup === selGroup);\n");
        out.write("          const matchUrgency = (selUrgency === 'all' || selUrgency === '' || cUrgency === selUrgency);\n");
        out.write("          const matchSearch = (q === '' || cSearch.includes(q));\n");
        out.write("\n");
        out.write("          if (matchGroup && matchUrgency && matchSearch) {\n");
        out.write("            card.style.display = '';\n");
        out.write("            visible++;\n");
        out.write("          } else {\n");
        out.write("            card.style.display = 'none';\n");
        out.write("          }\n");
        out.write("        });\n");
        out.write("\n");
        out.write("        if (visibleCountElem) {\n");
        out.write("          visibleCountElem.textContent = visible;\n");
        out.write("        }\n");
        out.write("      }\n");
        out.write("\n");
        out.write("      if (groupSelect) groupSelect.addEventListener('change', applyClientFilter);\n");
        out.write("      if (urgencySelect) urgencySelect.addEventListener('change', applyClientFilter);\n");
        out.write("      if (searchInput) searchInput.addEventListener('input', applyClientFilter);\n");
        out.write("    })();\n");
        out.write("  </script>\n");

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

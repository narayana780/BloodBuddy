package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodCompatibility;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.ChatService;
import com.bloodbuddy.service.DonorService;
import com.bloodbuddy.service.NotificationService;
import com.bloodbuddy.service.SeedDataProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * FindDonorsServlet handles the functional Find Donors page.
 * Processes GET queries for partial name searches, blood group filtering,
 * and Rajahmundry locality filtering.
 * Pure Java backend logic, HTML5 form submission, and zero JavaScript.
 */
public class FindDonorsServlet extends HttpServlet {

    private final DonorService donorService = new DonorService();
    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();
    private final ChatService chatService = new ChatService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String rawName = req.getParameter("name");
        String rawGroup = req.getParameter("bloodGroup");
        String rawCity = req.getParameter("city");
        String rawLocality = req.getParameter("locality");

        String nameQuery = (rawName != null) ? rawName.trim() : "";
        String bloodGroup = (rawGroup != null) ? rawGroup.trim() : "";
        String city = (rawCity != null) ? rawCity.trim() : "";
        String locality = (rawLocality != null) ? rawLocality.trim() : "";

        // Server-side validation
        String validationNote = null;
        if (!bloodGroup.isEmpty() && !"ALL".equalsIgnoreCase(bloodGroup) && !BloodCompatibility.isValidGroup(bloodGroup)) {
            validationNote = "Invalid blood group filter '" + escapeHtml(bloodGroup) + "' was ignored.";
            bloodGroup = "";
        }
        if (!locality.isEmpty() && !"ALL".equalsIgnoreCase(locality) && !SeedDataProvider.isValidLocality(locality)) {
            validationNote = "Unrecognized locality filter '" + escapeHtml(locality) + "' was ignored.";
            locality = "";
        }

        // Execute multi-criteria search
        List<Donor> matchedDonors = donorService.searchDonors(nameQuery, bloodGroup, locality);

        // Filter by city if selected
        if (!city.isEmpty() && !"ALL".equalsIgnoreCase(city)) {
            matchedDonors = matchedDonors.stream()
                    .filter(d -> city.equalsIgnoreCase(d.getCity()))
                    .toList();
        }

        // Sensible ranking: rank available donors first, followed by temporarily unavailable donors
        matchedDonors = matchedDonors.stream()
                .sorted((d1, d2) -> Boolean.compare(d2.isEligible(), d1.isEligible()))
                .toList();

        int totalAvailableCount = (int) donorService.getAllDemonstrationDonors().stream()
                .filter(Donor::isEligible)
                .count();

        boolean isAdmin = authService.isAdmin(req);
        User currentUser = authService.getCurrentUser(req);
        int unreadCount = notificationService.getUnreadCount(isAdmin);
        int unreadMsgCount = (currentUser != null) ? chatService.getUnreadMessageCount(currentUser.getId()) : 0;

        try (PrintWriter out = resp.getWriter()) {
            renderHtml(out, nameQuery, bloodGroup, city, locality, matchedDonors, totalAvailableCount, validationNote, unreadCount, unreadMsgCount, isAdmin, currentUser);
        }
    }

    private void renderHtml(PrintWriter out, String nameQuery, String selectedGroup, String selectedCity, String selectedLocality,
                            List<Donor> donors, int totalAvailableCount, String validationNote,
                            int unreadCount, int unreadMsgCount, boolean isAdmin, User currentUser) {

        boolean hasActiveFilter = !nameQuery.isEmpty() || 
                (!selectedGroup.isEmpty() && !"ALL".equalsIgnoreCase(selectedGroup)) || 
                (!selectedCity.isEmpty() && !"ALL".equalsIgnoreCase(selectedCity)) ||
                (!selectedLocality.isEmpty() && !"ALL".equalsIgnoreCase(selectedLocality));

        out.write("<!DOCTYPE html>\n");
        out.write("<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Find Donors — Find Your Lifeline | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Search voluntary blood donors across Rajahmundry localities with name search, blood group filters, and verified donor profiles.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        // Header Navigation
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\">\n");
        out.write("          <img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Logo\" width=\"38\" height=\"38\">\n");
        out.write("        </div>\n");
        out.write("        <div class=\"brand-text\">\n");
        out.write("          <span class=\"brand-name\">" + AppConfig.APP_NAME + "</span>\n");
        out.write("          <span class=\"brand-tagline-sub\">" + AppConfig.TAGLINE + "</span>\n");
        out.write("        </div>\n");
        out.write("      </a>\n");
        out.write("      <nav aria-label=\"Main Navigation\">\n");
        out.write("        <ul class=\"nav-links\">\n");
        out.write("          <li><a href=\"/dashboard\" class=\"nav-link\">Home</a></li>\n");
        out.write("          <li><a href=\"/find-donors\" class=\"nav-link active-nav\">Find Donors</a></li>\n");
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

        out.write("  <main class=\"find-donors-main\">\n");
        out.write("    <div class=\"container\">\n");

        // Validation Note Banner if parameters were invalid
        if (validationNote != null) {
            out.write("      <div class=\"validation-alert-banner\" role=\"alert\">\n");
            out.write("        <span class=\"alert-icon\">⚠️</span>\n");
            out.write("        <span>" + validationNote + "</span>\n");
            out.write("      </div>\n");
        }

        // Hero Banner / Title Area
        out.write("      <div class=\"find-donors-header\">\n");
        out.write("        <div class=\"stage-badge\">Verified Donor Directory</div>\n");
        out.write("        <h1 class=\"find-donors-title\">Find Potential Blood Donors</h1>\n");
        out.write("        <p class=\"find-donors-subtitle\">Search voluntary donors recorded as available across Rajahmundry. Filter by name, blood group, city, or locality.</p>\n");
        out.write("      </div>\n");

        // Privacy & Verification Protocol
        out.write("      <div class=\"demo-disclaimer-card\">\n");
        out.write("        <span class=\"disclaimer-icon\">ℹ️</span>\n");
        out.write("        <div class=\"disclaimer-content\">\n");
        out.write("          <strong>Donor Privacy &amp; Clinical Safety Protocol:</strong>\n");
        out.write("          <span>Direct contact details are protected to safeguard donor privacy. Donors shown are recorded as available in our directory; availability is not claimed to be verified in real time. Compatibility and physical fitness must be confirmed by laboratory cross-matching before transfusion. For urgent clinical emergencies, dial 108.</span>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        // Search & Filter Panel (HTML Form with zero JS)
        out.write("      <section class=\"search-panel-card\" aria-label=\"Search and Filter Donors\">\n");
        out.write("        <form method=\"GET\" action=\"/find-donors\" class=\"donor-search-form\">\n");
        out.write("          <div class=\"search-fields-grid\" style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px;\">\n");

        // 1. Name Search Input
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"donorNameInput\" class=\"form-label\">Donor Name</label>\n");
        out.write("              <div class=\"input-wrap\">\n");
        out.write("                <span class=\"input-icon\">🔍</span>\n");
        out.write("                <input type=\"text\" id=\"donorNameInput\" name=\"name\" class=\"form-control\" \n");
        out.write("                       placeholder=\"Search by name...\" \n");
        out.write("                       value=\"" + escapeHtml(nameQuery) + "\" maxlength=\"50\">\n");
        out.write("              </div>\n");
        out.write("            </div>\n");

        // 2. Blood Group Dropdown
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"bloodGroupSelect\" class=\"form-label\">Blood Group</label>\n");
        out.write("              <select id=\"bloodGroupSelect\" name=\"bloodGroup\" class=\"form-control form-select\">\n");
        out.write("                <option value=\"ALL\"" + (selectedGroup.isEmpty() || "ALL".equalsIgnoreCase(selectedGroup) ? " selected" : "") + ">All Blood Groups (Any)</option>\n");
        for (String g : BloodCompatibility.ALL_GROUPS) {
            boolean isSel = g.equalsIgnoreCase(selectedGroup);
            out.write("                <option value=\"" + g + "\"" + (isSel ? " selected" : "") + ">" + g + " (" + getGroupRoleSummary(g) + ")</option>\n");
        }
        out.write("              </select>\n");
        out.write("            </div>\n");

        // 3. City Filter Dropdown
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"citySelect\" class=\"form-label\">City</label>\n");
        out.write("              <select id=\"citySelect\" name=\"city\" class=\"form-control form-select\">\n");
        out.write("                <option value=\"ALL\"" + (selectedCity.isEmpty() || "ALL".equalsIgnoreCase(selectedCity) ? " selected" : "") + ">All Cities</option>\n");
        out.write("                <option value=\"Rajahmundry\"" + ("Rajahmundry".equalsIgnoreCase(selectedCity) || selectedCity.isEmpty() ? " selected" : "") + ">Rajahmundry</option>\n");
        out.write("              </select>\n");
        out.write("            </div>\n");

        // 4. Locality Dropdown
        out.write("            <div class=\"form-group\">\n");
        out.write("              <label for=\"localitySelect\" class=\"form-label\">Locality (Rajahmundry)</label>\n");
        out.write("              <select id=\"localitySelect\" name=\"locality\" class=\"form-control form-select\">\n");
        out.write("                <option value=\"ALL\"" + (selectedLocality.isEmpty() || "ALL".equalsIgnoreCase(selectedLocality) ? " selected" : "") + ">All Rajahmundry Localities</option>\n");
        for (String loc : SeedDataProvider.getLocalities()) {
            boolean isSel = loc.equalsIgnoreCase(selectedLocality);
            out.write("                <option value=\"" + loc + "\"" + (isSel ? " selected" : "") + ">📍 " + loc + "</option>\n");
        }
        out.write("              </select>\n");
        out.write("            </div>\n");

        out.write("          </div>\n");

        // Search Controls Row (Clear Search Button & Reset Filters Button)
        out.write("          <div class=\"search-actions-row\" style=\"margin-top: 18px;\">\n");
        out.write("            <div class=\"search-actions-left\">\n");
        out.write("              <button type=\"submit\" class=\"btn btn-primary\">\n");
        out.write("                <span>Search Donors</span>\n");
        out.write("                <span class=\"btn-icon\">🔍</span>\n");
        out.write("              </button>\n");
        out.write("              <a href=\"/find-donors\" class=\"btn btn-secondary\">\n");
        out.write("                <span>Reset Filters</span>\n");
        out.write("                <span class=\"btn-icon\">↺</span>\n");
        out.write("              </a>\n");
        out.write("            </div>\n");
        out.write("            <div class=\"search-status-counter\">\n");
        out.write("              Showing <strong>" + donors.size() + "</strong> available donors (out of <strong>" + totalAvailableCount + "</strong> recorded)\n");
        if (hasActiveFilter) {
            out.write("              <span class=\"filtered-pill\">(Filtered)</span>\n");
        }
        out.write("            </div>\n");
        out.write("          </div>\n");

        out.write("        </form>\n");
        out.write("      </section>\n");

        // Results Section
        out.write("      <section class=\"results-section\" aria-label=\"Donor Search Results\">\n");

        if (donors.isEmpty()) {
            // Empty Results State
            out.write("        <div class=\"empty-state-card\">\n");
            out.write("          <div class=\"empty-icon\">🔍</div>\n");
            out.write("          <h3 class=\"empty-title\">No Potential Donors Match Your Search</h3>\n");
            out.write("          <p class=\"empty-desc\">We couldn't find any available donors matching your selected filters:\n");
            if (!nameQuery.isEmpty()) {
                out.write("            <br>• Name query: <strong>\"" + escapeHtml(nameQuery) + "\"</strong>\n");
            }
            if (!selectedGroup.isEmpty() && !"ALL".equalsIgnoreCase(selectedGroup)) {
                out.write("            <br>• Blood group: <strong>" + escapeHtml(selectedGroup) + "</strong>\n");
            }
            if (!selectedCity.isEmpty() && !"ALL".equalsIgnoreCase(selectedCity)) {
                out.write("            <br>• City: <strong>" + escapeHtml(selectedCity) + "</strong>\n");
            }
            if (!selectedLocality.isEmpty() && !"ALL".equalsIgnoreCase(selectedLocality)) {
                out.write("            <br>• Locality: <strong>" + escapeHtml(selectedLocality) + "</strong>\n");
            }
            out.write("          </p>\n");
            out.write("          <div class=\"empty-actions\">\n");
            out.write("            <a href=\"/find-donors\" class=\"btn btn-primary\">\n");
            out.write("              <span>Reset Filters</span>\n");
            out.write("              <span class=\"btn-icon\">↺</span>\n");
            out.write("            </a>\n");
            out.write("            <a href=\"/request-blood\" class=\"btn btn-secondary\">\n");
            out.write("              <span>Emergency Request Blood</span>\n");
            out.write("              <span class=\"btn-icon\">🚨</span>\n");
            out.write("            </a>\n");
            out.write("          </div>\n");
            out.write("        </div>\n");
        } else {
            // Donor Results Cards Grid
            out.write("        <div class=\"donors-grid\">\n");
            for (Donor d : donors) {
                out.write("          <article class=\"donor-card donor-card-rich\">\n");
                out.write("            <div class=\"donor-card-top\">\n");
                out.write("              <div class=\"donor-avatar\">\n");
                out.write("                <span>" + d.getName().substring(0, 1) + "</span>\n");
                out.write("              </div>\n");
                out.write("              <div class=\"donor-group-badge badge-exact\">\n");
                out.write("                " + d.getBloodGroup() + "\n");
                out.write("              </div>\n");
                out.write("            </div>\n");
                out.write("            <div class=\"donor-card-body\">\n");
                out.write("              <div class=\"donor-id-pill\">ID: " + escapeHtml(d.getId()) + "</div>\n");
                out.write("              <h3 class=\"donor-name\">" + escapeHtml(d.getName()) + "</h3>\n");
                out.write("              <p class=\"donor-locality\">📍 " + escapeHtml(d.getLocality()) + ", " + escapeHtml(d.getCity()) + "</p>\n");
                out.write("              <div class=\"donor-meta-row\">\n");
                out.write("                <span class=\"donor-badge-tag\">" + escapeHtml(d.getBadge()) + "</span>\n");
                out.write("                <span class=\"donor-compat-hint\" style=\"font-size: 0.8rem; color: var(--text-secondary);\">" + d.getBloodGroup() + " (" + getGroupRoleSummary(d.getBloodGroup()) + ")</span>\n");
                out.write("              </div>\n");
                out.write("              <div class=\"donor-meta-row\" style=\"margin-top: 4px;\">\n");
                out.write("                <span class=\"donor-last-donated\">🕒 Last Donated: " + escapeHtml(d.getLastDonated()) + "</span>\n");
                out.write("              </div>\n");
                out.write("              <div class=\"donor-availability-row\" style=\"margin-top: 8px;\">\n");
                out.write("                " + DonorProfileServlet.renderAvailabilityBadge(d) + "\n");
                out.write("              </div>\n");
                out.write("            </div>\n");
                out.write("            <div class=\"donor-card-footer rich-footer\">\n");
                out.write("              <a href=\"/messages?start=true&donorId=" + URLEncoder.encode(d.getId(), StandardCharsets.UTF_8) + "\" class=\"donor-action-btn\" style=\"background: rgba(211, 47, 47, 0.18); color: var(--text-white); border-color: rgba(211, 47, 47, 0.5); font-weight: 600;\" title=\"Initiate private, secure chat with donor\">\n");
                out.write("                <span>Chat with Donor</span>\n");
                out.write("                <span class=\"btn-icon\">💬</span>\n");
                out.write("              </a>\n");
                out.write("              <a href=\"/donor-profile?id=" + URLEncoder.encode(d.getId(), StandardCharsets.UTF_8) + "\" class=\"donor-action-btn\" style=\"background: rgba(0, 240, 255, 0.12); color: var(--icy-blue); border-color: rgba(0, 240, 255, 0.3);\" title=\"View registered donor profile\">\n");
                out.write("                <span>View Profile</span>\n");
                out.write("                <span class=\"btn-icon\">↗</span>\n");
                out.write("              </a>\n");
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
        out.write("            <div class=\"brand-logo-wrap\">\n");
        out.write("              <img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Logo\" width=\"36\" height=\"36\">\n");
        out.write("            </div>\n");
        out.write("            <div class=\"brand-text\">\n");
        out.write("              <span class=\"brand-name\">" + AppConfig.APP_NAME + "</span>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("          <p class=\"footer-brand-desc\">" + AppConfig.TAGLINE + "<br>Find voluntary lifesavers across Rajahmundry with real-time compatibility intelligence.</p>\n");
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
        out.write("          <h5>Emergency Contacts</h5>\n");
        out.write("          <ul class=\"footer-links\">\n");
        out.write("            <li><a href=\"tel:108\" style=\"color: var(--neon-coral); font-weight: 700;\">🚨 Emergency Helpline: 108</a></li>\n");
        out.write("            <li><a href=\"/find-donors\">Rajahmundry Govt Hospital Blood Bank</a></li>\n");
        out.write("            <li><a href=\"/about\">Red Cross Society Rajahmundry</a></li>\n");
        out.write("          </ul>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("      <div class=\"footer-bottom\">\n");
        out.write("        <div>&copy; 2026 Blood Buddy. Community Blood Donation Coordination. All rights reserved.</div>\n");
        out.write("        <div class=\"backend-status-badge\">\n");
        out.write("          <span class=\"status-dot\"></span> System Active &amp; Operational\n");
        out.write("        </div>\n");
        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
        out.write("</body>\n</html>\n");
    }

    private String getGroupRoleSummary(String group) {
        switch (group) {
            case "O-": return "Universal Donor";
            case "O+": return "High Demand";
            case "A-": return "Rare";
            case "A+": return "Common";
            case "B-": return "Rare";
            case "B+": return "Frequent";
            case "AB-": return "Rarest";
            case "AB+": return "Universal Recipient";
            default: return "";
        }
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

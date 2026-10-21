package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.BloodRequest;
import com.bloodbuddy.model.ChatMessage;
import com.bloodbuddy.model.Conversation;
import com.bloodbuddy.model.Donor;
import com.bloodbuddy.model.User;
import com.bloodbuddy.service.AuthService;
import com.bloodbuddy.service.ChatService;
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
 * ChatServlet manages the private, secure in-app messaging feature.
 * Renders the Conversations Inbox and Private Chat Window.
 * Provides progressive enhancement bounded polling, accept/decline approval workflow,
 * and strict participant authorization guards.
 */
public class ChatServlet extends HttpServlet {

    private final ChatService chatService = new ChatService();
    private final AuthService authService = new AuthService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User currentUser = authService.getCurrentUser(req);
        if (currentUser == null) {
            String fullUri = req.getRequestURI();
            if (req.getQueryString() != null) {
                fullUri += "?" + req.getQueryString();
            }
            resp.sendRedirect(req.getContextPath() + "/login?redirect=" + URLEncoder.encode(fullUri, StandardCharsets.UTF_8));
            return;
        }

        boolean isAdmin = authService.isAdmin(req);
        int unreadNotifCount = notificationService.getUnreadCount(currentUser.getId(), isAdmin);
        int totalUnreadMsgCount = chatService.getUnreadMessageCount(currentUser.getId());

        // 1. Polling JSON API endpoint
        String pollParam = req.getParameter("poll");
        if ("true".equalsIgnoreCase(pollParam) || "1".equalsIgnoreCase(pollParam)) {
            handlePollingRequest(req, resp, currentUser);
            return;
        }

        // 2. Start conversation flow via GET link (e.g. from Find Donors or Matching)
        String startParam = req.getParameter("start");
        String donorIdParam = req.getParameter("donorId");
        if ("true".equalsIgnoreCase(startParam) || (donorIdParam != null && !donorIdParam.trim().isEmpty())) {
            String requestId = req.getParameter("requestId");
            System.out.println("[ChatServlet] Initiating chat with donorProfileId=" + donorIdParam + " by requester=" + currentUser.getId() + " (" + currentUser.getUsername() + ")");
            try {
                Conversation conv = chatService.startConversation(currentUser, donorIdParam, requestId);
                System.out.println("[ChatServlet] Conversation initiated successfully id=" + conv.getId() + " status=" + conv.getStatus() + " donorUserId=" + conv.getDonorUserId());
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + conv.getId());
                return;
            } catch (Exception e) {
                System.out.println("[ChatServlet] Error starting conversation: " + e.getMessage());
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unable to initiate conversation.";
                resp.sendRedirect(req.getContextPath() + "/messages?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
                return;
            }
        }

        // 3. Conversation Window or Inbox View
        String convIdParam = req.getParameter("id");
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        try (PrintWriter out = resp.getWriter()) {
            if (convIdParam != null && !convIdParam.trim().isEmpty()) {
                String cleanConvId = convIdParam.trim();
                System.out.println("[ChatServlet] Loading conversation window id=" + cleanConvId + " for user=" + currentUser.getId() + " (" + currentUser.getUsername() + ")");
                Optional<Conversation> convOpt = chatService.getConversation(cleanConvId, currentUser.getId());

                if (convOpt.isEmpty()) {
                    System.out.println("[ChatServlet] Authorization denied or not found for convId=" + cleanConvId + " user=" + currentUser.getId());
                    renderForbiddenOrNotFound(out, cleanConvId, unreadNotifCount, totalUnreadMsgCount, isAdmin, currentUser);
                    return;
                }

                Conversation conv = convOpt.get();
                // Mark incoming messages as read upon viewing
                chatService.markMessagesAsRead(conv.getId(), currentUser.getId());
                List<ChatMessage> messages = chatService.getMessages(conv.getId(), currentUser.getId());

                String otherUserId = conv.getOtherParticipantUserId(currentUser.getId());
                Optional<User> otherUser = chatService.getUserById(otherUserId);
                Optional<Donor> donorProfile = chatService.getDonorById(conv.getDonorProfileId());
                Optional<BloodRequest> linkedRequest = conv.hasLinkedRequest()
                        ? chatService.getBloodRequestById(conv.getRequestId()) : Optional.empty();

                String errorBanner = req.getParameter("error");
                String successBanner = req.getParameter("success");

                renderChatWindow(out, conv, messages, otherUser, donorProfile, linkedRequest,
                        unreadNotifCount, totalUnreadMsgCount, isAdmin, currentUser, errorBanner, successBanner);
            } else {
                // Conversations Inbox View
                List<Conversation> conversations = chatService.getConversationsForUser(currentUser.getId());
                System.out.println("[ChatServlet] Inbox loaded for user=" + currentUser.getId() + " (" + currentUser.getUsername() + ", linkedDonor=" + currentUser.getLinkedDonorId() + ") returned " + conversations.size() + " conversation(s)");
                String errorBanner = req.getParameter("error");
                renderInbox(out, conversations, unreadNotifCount, totalUnreadMsgCount, isAdmin, currentUser, errorBanner);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        User currentUser = authService.getCurrentUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        String action = req.getParameter("action");
        String convId = req.getParameter("id");

        if ("send".equalsIgnoreCase(action)) {
            String text = req.getParameter("message");
            try {
                chatService.sendMessage(convId, currentUser, text);
                boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(req.getHeader("X-Requested-With"));
                if (isAjax) {
                    resp.setContentType("application/json;charset=UTF-8");
                    resp.getWriter().write("{\"success\":true}");
                    return;
                }
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8));
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : "Failed to send message.";
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8)
                        + "&error=" + URLEncoder.encode(errMsg, StandardCharsets.UTF_8));
            }
            return;
        }

        if ("accept".equalsIgnoreCase(action)) {
            try {
                chatService.acceptConversation(convId, currentUser);
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8)
                        + "&success=" + URLEncoder.encode("Chat request accepted. You can now exchange messages.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8)
                        + "&error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            }
            return;
        }

        if ("decline".equalsIgnoreCase(action)) {
            try {
                chatService.declineConversation(convId, currentUser);
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8)
                        + "&success=" + URLEncoder.encode("Chat request declined.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8)
                        + "&error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            }
            return;
        }

        if ("close".equalsIgnoreCase(action)) {
            try {
                chatService.closeConversation(convId, currentUser);
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8)
                        + "&success=" + URLEncoder.encode("Conversation closed.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + URLEncoder.encode(convId, StandardCharsets.UTF_8)
                        + "&error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            }
            return;
        }

        if ("start".equalsIgnoreCase(action)) {
            String donorProfileId = req.getParameter("donorId");
            String requestId = req.getParameter("requestId");
            try {
                Conversation c = chatService.startConversation(currentUser, donorProfileId, requestId);
                resp.sendRedirect(req.getContextPath() + "/messages?id=" + c.getId());
            } catch (Exception e) {
                resp.sendRedirect(req.getContextPath() + "/find-donors?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
            }
            return;
        }

        resp.sendRedirect(req.getContextPath() + "/messages");
    }

    private void handlePollingRequest(HttpServletRequest req, HttpServletResponse resp, User currentUser) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        String convId = req.getParameter("id");
        if (convId == null || convId.trim().isEmpty()) {
            int unreadTotal = chatService.getUnreadMessageCount(currentUser.getId());
            resp.getWriter().write("{\"unreadTotal\":" + unreadTotal + "}");
            return;
        }

        Optional<Conversation> convOpt = chatService.getConversation(convId.trim(), currentUser.getId());
        if (convOpt.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Forbidden\"}");
            return;
        }

        Conversation conv = convOpt.get();
        // Mark messages as read while actively viewing
        chatService.markMessagesAsRead(conv.getId(), currentUser.getId());

        List<ChatMessage> allMessages = chatService.getMessages(conv.getId(), currentUser.getId());
        String sinceMsgId = req.getParameter("since");

        int startIndex = 0;
        if (sinceMsgId != null && !sinceMsgId.trim().isEmpty()) {
            for (int i = 0; i < allMessages.size(); i++) {
                if (allMessages.get(i).getId().equalsIgnoreCase(sinceMsgId.trim())) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        List<ChatMessage> newMessages = allMessages.subList(startIndex, allMessages.size());

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":\"").append(escapeJson(conv.getStatus())).append("\",");
        sb.append("\"unreadTotal\":").append(chatService.getUnreadMessageCount(currentUser.getId())).append(",");
        sb.append("\"newMessages\":[");
        for (int i = 0; i < newMessages.size(); i++) {
            ChatMessage m = newMessages.get(i);
            boolean isMine = m.getSenderUserId().equalsIgnoreCase(currentUser.getId());
            sb.append("{");
            sb.append("\"id\":\"").append(escapeJson(m.getId())).append("\",");
            sb.append("\"isMine\":").append(isMine).append(",");
            sb.append("\"text\":\"").append(escapeJson(m.getMessageText())).append("\",");
            sb.append("\"createdAt\":\"").append(escapeJson(m.getCreatedAt())).append("\",");
            sb.append("\"read\":").append(m.isRead());
            sb.append("}");
            if (i < newMessages.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        sb.append("}");

        resp.getWriter().write(sb.toString());
    }

    private void renderInbox(PrintWriter out, List<Conversation> conversations, int unreadNotifCount,
                             int unreadMsgCount, boolean isAdmin, User currentUser, String errorBanner) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Messages &amp; Direct Donor Consultations | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Secure, private communication with matched blood donors and requesters.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/messages", unreadNotifCount, unreadMsgCount, isAdmin, currentUser);

        out.write("  <main class=\"inbox-main-wrap\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 960px;\">\n");

        if (errorBanner != null && !errorBanner.trim().isEmpty()) {
            out.write("      <div class=\"validation-alert-banner\" role=\"alert\" style=\"margin-bottom: 20px;\">\n");
            out.write("        <span class=\"alert-icon\">⚠️</span>\n");
            out.write("        <span>" + escapeHtml(errorBanner) + "</span>\n");
            out.write("      </div>\n");
        }

        out.write("      <div class=\"inbox-header-row\">\n");
        out.write("        <div>\n");
        out.write("          <div class=\"stage-badge stage-badge-verified\">Direct In-App Coordination</div>\n");
        out.write("          <h1 class=\"inbox-page-title\">Conversations &amp; Direct Messages</h1>\n");
        out.write("          <p class=\"inbox-page-desc\">Communicate privately with matched voluntary lifesavers and patient representatives. Personal phone numbers remain shielded.</p>\n");
        out.write("        </div>\n");
        out.write("        <div class=\"inbox-header-actions\">\n");
        out.write("          <a href=\"/find-donors\" class=\"btn btn-secondary btn-sm\"><span>🔍 Find Donors</span></a>\n");
        out.write("          <a href=\"/view-requests\" class=\"btn btn-secondary btn-sm\"><span>📋 Requisitions</span></a>\n");
        out.write("        </div>\n");
        out.write("      </div>\n");

        if (conversations.isEmpty()) {
            out.write("      <div class=\"empty-state-card glass-panel\" style=\"padding: 60px 24px; text-align: center; border-radius: var(--radius-lg); margin-top: 24px;\">\n");
            out.write("        <div style=\"font-size: 3.5rem; margin-bottom: 12px;\">💬</div>\n");
            out.write("        <h3 style=\"font-size: 1.5rem; margin: 0 0 10px; color: var(--text-white); font-family: var(--font-heading);\">No Direct Conversations Yet</h3>\n");
            out.write("        <p style=\"max-width: 540px; margin: 0 auto 24px; color: var(--text-secondary); line-height: 1.6;\">\n");
            out.write("          When you identify compatible lifesavers in Find Donors or receive direct match recommendations, click <strong>Chat with Donor</strong> to begin a private consultation.\n");
            out.write("        </p>\n");
            out.write("        <div style=\"display: flex; gap: 14px; justify-content: center; flex-wrap: wrap;\">\n");
            out.write("          <a href=\"/find-donors\" class=\"btn btn-primary\"><span>Explore Voluntary Donors Directory</span><span class=\"btn-icon\">🔍</span></a>\n");
            out.write("          <a href=\"/request-blood\" class=\"btn btn-secondary\"><span>Submit Blood Requisition</span><span class=\"btn-icon\">🚨</span></a>\n");
            out.write("        </div>\n");
            out.write("      </div>\n");
        } else {
            out.write("      <div class=\"inbox-card-list\" style=\"margin-top: 24px; display: flex; flex-direction: column; gap: 14px;\">\n");
            for (Conversation c : conversations) {
                String otherUserId = c.getOtherParticipantUserId(currentUser.getId());
                Optional<User> otherUser = chatService.getUserById(otherUserId);
                Optional<Donor> donorProfile = chatService.getDonorById(c.getDonorProfileId());
                int unreadInConv = chatService.getUnreadMessageCountForConversation(c.getId(), currentUser.getId());

                String otherName = otherUser.map(User::getFullName).orElse("Community Member");
                if (c.isRequester(currentUser.getId()) && donorProfile.isPresent()) {
                    otherName = donorProfile.get().getName();
                }

                String statusBadgeClass = "badge-status-pending";
                String statusLabel = "⏳ Pending Approval";
                if (c.isActive()) {
                    statusBadgeClass = "badge-status-available";
                    statusLabel = "● Active Conversation";
                } else if (c.isDeclined()) {
                    statusBadgeClass = "badge-status-unavailable";
                    statusLabel = "✕ Declined";
                } else if (c.isClosed()) {
                    statusBadgeClass = "badge-status-none";
                    statusLabel = "Closed";
                }

                List<ChatMessage> msgs = chatService.getMessages(c.getId(), currentUser.getId());
                String previewText = "Chat request initiated. Awaiting response.";
                if (!msgs.isEmpty()) {
                    ChatMessage lastMsg = msgs.get(msgs.size() - 1);
                    previewText = (lastMsg.getSenderUserId().equalsIgnoreCase(currentUser.getId()) ? "You: " : "") + lastMsg.getMessageText();
                    if (previewText.length() > 65) {
                        previewText = previewText.substring(0, 62) + "…";
                    }
                }

                out.write("        <article class=\"inbox-item-card glass-panel" + (unreadInConv > 0 ? " unread-conversation" : "") + "\">\n");
                out.write("          <div class=\"inbox-item-left\">\n");
                out.write("            <div class=\"inbox-avatar\">\n");
                out.write("              <span>" + (otherName.length() > 0 ? otherName.substring(0, 1).toUpperCase() : "U") + "</span>\n");
                out.write("            </div>\n");
                out.write("            <div class=\"inbox-item-info\">\n");
                out.write("              <div class=\"inbox-item-title-row\">\n");
                out.write("                <h3 class=\"inbox-item-name\">" + escapeHtml(otherName) + "</h3>\n");
                if (donorProfile.isPresent()) {
                    out.write("                <span class=\"badge-matches\" style=\"padding: 2px 8px; font-size: 0.75rem;\">🩸 " + escapeHtml(donorProfile.get().getBloodGroup()) + "</span>\n");
                    out.write("                <span style=\"font-size: 0.78rem; color: var(--text-muted);\">📍 " + escapeHtml(donorProfile.get().getLocality()) + "</span>\n");
                }
                if (c.hasLinkedRequest()) {
                    out.write("                <span class=\"badge-compat-tier\" style=\"padding: 2px 8px; font-size: 0.72rem;\">Requisition: " + escapeHtml(c.getRequestId()) + "</span>\n");
                }
                out.write("              </div>\n");
                out.write("              <p class=\"inbox-item-preview\">" + escapeHtml(previewText) + "</p>\n");
                out.write("              <div class=\"inbox-item-meta-row\">\n");
                out.write("                <span class=\"badge-availability " + statusBadgeClass + "\" style=\"font-size: 0.75rem;\">" + statusLabel + "</span>\n");
                out.write("                <span class=\"inbox-item-time\">🕒 " + escapeHtml(c.getLastActivityAt()) + "</span>\n");
                out.write("              </div>\n");
                out.write("            </div>\n");
                out.write("          </div>\n");
                out.write("          <div class=\"inbox-item-right\">\n");
                if (unreadInConv > 0) {
                    out.write("            <span class=\"notif-badge\" style=\"font-size: 0.85rem; padding: 4px 10px;\">" + unreadInConv + " New</span>\n");
                }
                out.write("            <a href=\"/messages?id=" + c.getId() + "\" class=\"btn btn-secondary btn-sm\"><span>Open Chat</span><span class=\"btn-icon\">→</span></a>\n");
                out.write("          </div>\n");
                out.write("        </article>\n");
            }
            out.write("      </div>\n");
        }

        out.write("    </div>\n");
        out.write("  </main>\n");

        renderFooter(out);
        out.write("</body>\n</html>\n");
    }

    private void renderChatWindow(PrintWriter out, Conversation conv, List<ChatMessage> messages,
                                  Optional<User> otherUser, Optional<Donor> donorProfile, Optional<BloodRequest> linkedRequest,
                                  int unreadNotifCount, int unreadMsgCount, boolean isAdmin, User currentUser,
                                  String errorBanner, String successBanner) {
        boolean isDonor = conv.isDonor(currentUser.getId());
        if (!isDonor && currentUser.getLinkedDonorId() != null && !currentUser.getLinkedDonorId().trim().isEmpty()) {
            isDonor = currentUser.getLinkedDonorId().trim().equalsIgnoreCase(conv.getDonorProfileId());
        }
        String otherName = otherUser.map(User::getFullName).orElse("Community Member");
        if (!isDonor && donorProfile.isPresent()) {
            otherName = donorProfile.get().getName();
        }

        String lastMsgId = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getId();

        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        out.write("  <title>Chat with " + escapeHtml(otherName) + " | Blood Buddy</title>\n");
        out.write("  <meta name=\"description\" content=\"Secure direct consultation between requester and voluntary donor.\">\n");
        out.write("  <link rel=\"icon\" type=\"image/svg+xml\" href=\"/static/images/logo.svg\">\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");

        renderHeader(out, "/messages", unreadNotifCount, unreadMsgCount, isAdmin, currentUser);

        out.write("  <main class=\"chat-window-wrap\">\n");
        out.write("    <div class=\"container\" style=\"max-width: 860px;\">\n");

        if (errorBanner != null && !errorBanner.trim().isEmpty()) {
            out.write("      <div class=\"validation-alert-banner\" role=\"alert\" style=\"margin-bottom: 14px;\">\n");
            out.write("        <span class=\"alert-icon\">⚠️</span>\n");
            out.write("        <span>" + escapeHtml(errorBanner) + "</span>\n");
            out.write("      </div>\n");
        }

        if (successBanner != null && !successBanner.trim().isEmpty()) {
            out.write("      <div class=\"success-alert-banner\" role=\"alert\" style=\"margin-bottom: 14px; background: rgba(0, 137, 123, 0.15); border: 1px solid var(--teal-soft); color: var(--teal-light); padding: 12px 18px; border-radius: var(--radius-md);\">\n");
            out.write("        <span>✓ " + escapeHtml(successBanner) + "</span>\n");
            out.write("      </div>\n");
        }

        // Chat Container Card
        out.write("      <div class=\"chat-box-card glass-panel\">\n");

        // Header
        out.write("        <div class=\"chat-box-header\">\n");
        out.write("          <div class=\"chat-header-user-info\">\n");
        out.write("            <a href=\"/messages\" class=\"chat-back-link\" title=\"Return to Inbox\">← Inbox</a>\n");
        out.write("            <div class=\"chat-avatar\">\n");
        out.write("              <span>" + (otherName.length() > 0 ? otherName.substring(0, 1).toUpperCase() : "U") + "</span>\n");
        out.write("            </div>\n");
        out.write("            <div>\n");
        out.write("              <h2 class=\"chat-header-name\">" + escapeHtml(otherName) + "</h2>\n");
        out.write("              <div class=\"chat-header-sub\">\n");
        if (donorProfile.isPresent()) {
            out.write("                <span class=\"badge-matches\" style=\"padding: 2px 7px; font-size: 0.72rem;\">🩸 " + escapeHtml(donorProfile.get().getBloodGroup()) + "</span>\n");
            out.write("                <span>📍 " + escapeHtml(donorProfile.get().getLocality()) + "</span>\n");
        } else {
            out.write("                <span>👤 Authorized Requester</span>\n");
        }
        out.write("                <span class=\"chat-status-pill " + conv.getStatus().toLowerCase() + "\">" + conv.getStatus() + "</span>\n");
        out.write("              </div>\n");
        out.write("            </div>\n");
        out.write("          </div>\n");
        out.write("          <div class=\"chat-header-actions\">\n");
        if (conv.isActive()) {
            out.write("            <form method=\"POST\" action=\"/messages\" onsubmit=\"return confirm('Are you sure you want to close this conversation? Historical messages will be preserved.');\">\n");
            out.write("              <input type=\"hidden\" name=\"action\" value=\"close\">\n");
            out.write("              <input type=\"hidden\" name=\"id\" value=\"" + escapeHtml(conv.getId()) + "\">\n");
            out.write("              <button type=\"submit\" class=\"btn btn-secondary btn-sm\" style=\"font-size: 0.78rem; padding: 6px 12px;\">Close Chat</button>\n");
            out.write("            </form>\n");
        }
        out.write("          </div>\n");
        out.write("        </div>\n");

        // Requisition banner if linked
        if (linkedRequest.isPresent()) {
            BloodRequest req = linkedRequest.get();
            out.write("        <div class=\"chat-request-context-strip\">\n");
            out.write("          <span style=\"font-weight: 700; color: var(--ruby-hover);\">🩸 Linked Requisition:</span>\n");
            out.write("          <span><strong>" + escapeHtml(req.getId()) + "</strong> — " + req.getUnits() + " unit(s) of " + escapeHtml(req.getBloodGroup()) + " at " + escapeHtml(req.getHospitalName()) + " (" + escapeHtml(req.getHospitalLocality()) + ")</span>\n");
            out.write("          <span class=\"badge-urgency urgency-" + req.getUrgency().toLowerCase() + "\" style=\"margin-left: auto; font-size: 0.72rem; padding: 2px 8px;\">" + escapeHtml(req.getUrgency()) + "</span>\n");
            out.write("        </div>\n");
        }

        // Privacy reassurance badge
        out.write("        <div class=\"chat-privacy-notice-strip\">\n");
        out.write("          <span>🔒 Direct Secure Channel: Private contact phone numbers are never disclosed. All coordination is contained within Blood Buddy.</span>\n");
        out.write("        </div>\n");

        // Message History
        out.write("        <div class=\"chat-messages-container\" id=\"chatMessagesContainer\" data-conv-id=\"" + escapeHtml(conv.getId()) + "\" data-status=\"" + escapeHtml(conv.getStatus()) + "\" data-last-id=\"" + escapeHtml(lastMsgId) + "\">\n");

        if (messages.isEmpty()) {
            out.write("          <div class=\"chat-empty-hint\" id=\"chatEmptyHint\" style=\"text-align: center; color: var(--text-muted); padding: 40px 10px;\">\n");
            out.write("            <div style=\"font-size: 2rem; margin-bottom: 8px;\">🤝</div>\n");
            out.write("            <p>No messages exchanged yet. Send a message below to coordinate donation availability.</p>\n");
            out.write("          </div>\n");
        } else {
            for (ChatMessage m : messages) {
                boolean isMine = m.getSenderUserId().equalsIgnoreCase(currentUser.getId());
                out.write("          <div class=\"message-bubble-wrap " + (isMine ? "bubble-outgoing" : "bubble-incoming") + "\" id=\"msg-" + escapeHtml(m.getId()) + "\">\n");
                out.write("            <div class=\"message-bubble\">\n");
                out.write("              <div class=\"message-text\">" + escapeHtml(m.getMessageText()).replace("\n", "<br>") + "</div>\n");
                out.write("              <div class=\"message-meta\">\n");
                out.write("                <span class=\"message-time\">" + escapeHtml(m.getCreatedAt()) + "</span>\n");
                if (isMine) {
                    out.write("                <span class=\"message-status-tick\">" + (m.isRead() ? "✓✓ Read" : "✓ Sent") + "</span>\n");
                }
                out.write("              </div>\n");
                out.write("            </div>\n");
                out.write("          </div>\n");
            }
        }
        out.write("        </div>\n");

        // Bottom Composer or Approval Controls based on conversation state
        if (conv.isPending()) {
            out.write("        <div class=\"chat-pending-panel\">\n");
            if (isDonor) {
                // Donor has authority to Accept or Decline
                out.write("          <div class=\"pending-prompt\">\n");
                out.write("            <h4>⏳ Donation Consultation Request</h4>\n");
                out.write("            <p><strong>" + escapeHtml(otherName) + "</strong> wants to consult with you directly about donating blood" + (linkedRequest.isPresent() ? " for patient " + escapeHtml(linkedRequest.get().getPatientName()) : "") + ". Do you accept this chat request?</p>\n");
                out.write("          </div>\n");
                out.write("          <div class=\"pending-actions-row\">\n");
                out.write("            <form method=\"POST\" action=\"/messages\" style=\"display: inline;\">\n");
                out.write("              <input type=\"hidden\" name=\"action\" value=\"accept\">\n");
                out.write("              <input type=\"hidden\" name=\"id\" value=\"" + escapeHtml(conv.getId()) + "\">\n");
                out.write("              <button type=\"submit\" class=\"btn btn-primary\" style=\"padding: 10px 22px;\">✓ Accept Chat Request</button>\n");
                out.write("            </form>\n");
                out.write("            <form method=\"POST\" action=\"/messages\" style=\"display: inline;\">\n");
                out.write("              <input type=\"hidden\" name=\"action\" value=\"decline\">\n");
                out.write("              <input type=\"hidden\" name=\"id\" value=\"" + escapeHtml(conv.getId()) + "\">\n");
                out.write("              <button type=\"submit\" class=\"btn btn-secondary\" style=\"padding: 10px 22px;\">✕ Decline</button>\n");
                out.write("            </form>\n");
                out.write("          </div>\n");
            } else {
                // Requester waiting for donor approval
                out.write("          <div class=\"pending-waiting-notice\">\n");
                out.write("            <div style=\"font-size: 1.8rem; margin-bottom: 8px;\">⏳</div>\n");
                out.write("            <h4 style=\"margin: 0 0 6px; color: var(--text-white);\">Pending Donor Acceptance</h4>\n");
                out.write("            <p style=\"margin: 0; color: var(--text-secondary); font-size: 0.92rem;\">Your chat request has been delivered to <strong>" + escapeHtml(otherName) + "</strong>. Direct messaging will unlock immediately once the donor accepts.</p>\n");
                out.write("          </div>\n");
            }
            out.write("        </div>\n");
        } else if (conv.isDeclined()) {
            out.write("        <div class=\"chat-terminal-state-banner declined\">\n");
            out.write("          <span>✕ This conversation request was declined. Messaging is disabled.</span>\n");
            out.write("        </div>\n");
        } else if (conv.isClosed()) {
            out.write("        <div class=\"chat-terminal-state-banner closed\">\n");
            out.write("          <span>🔒 This conversation has been closed. Historical messages are preserved above.</span>\n");
            out.write("        </div>\n");
        } else {
            // Active Conversation Composer
            out.write("        <div class=\"chat-composer-wrap\">\n");
            out.write("          <form method=\"POST\" action=\"/messages\" id=\"chatComposerForm\" class=\"chat-composer-form\">\n");
            out.write("            <input type=\"hidden\" name=\"action\" value=\"send\">\n");
            out.write("            <input type=\"hidden\" name=\"id\" value=\"" + escapeHtml(conv.getId()) + "\">\n");
            out.write("            <div class=\"composer-input-row\">\n");
            out.write("              <textarea name=\"message\" id=\"chatMessageInput\" class=\"chat-textarea\" \n");
            out.write("                        placeholder=\"Type a secure message... (Press Enter to send, Shift+Enter for new line)\" \n");
            out.write("                        maxlength=\"" + AppConfig.MAX_CHAT_MESSAGE_LENGTH + "\" required></textarea>\n");
            out.write("              <button type=\"submit\" id=\"chatSendBtn\" class=\"btn btn-primary chat-send-btn\" title=\"Send message\">\n");
            out.write("                <span>Send</span>\n");
            out.write("                <span class=\"btn-icon\">📤</span>\n");
            out.write("              </button>\n");
            out.write("            </div>\n");
            out.write("            <div class=\"composer-footer-row\">\n");
            out.write("              <span id=\"charCountHint\" style=\"font-size: 0.75rem; color: var(--text-muted);\">Max " + AppConfig.MAX_CHAT_MESSAGE_LENGTH + " characters</span>\n");
            out.write("              <span id=\"pollingStatusBadge\" style=\"font-size: 0.75rem; color: var(--teal-light); display: inline-flex; align-items: center; gap: 5px;\">\n");
            out.write("                <span style=\"width: 6px; height: 6px; border-radius: 50%; background: var(--teal-soft); display: inline-block;\"></span> Connected (Active Polling)\n");
            out.write("              </span>\n");
            out.write("            </div>\n");
            out.write("          </form>\n");
            out.write("        </div>\n");
        }

        out.write("      </div>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");

        // Polling JavaScript for Bounded Real-time Updates & Enter-to-Send UX
        out.write("  <script>\n");
        out.write("  (function() {\n");
        out.write("    var container = document.getElementById('chatMessagesContainer');\n");
        out.write("    if (container) {\n");
        out.write("      container.scrollTop = container.scrollHeight;\n");
        out.write("    }\n");
        out.write("\n");
        out.write("    var form = document.getElementById('chatComposerForm');\n");
        out.write("    var textarea = document.getElementById('chatMessageInput');\n");
        out.write("    var sendBtn = document.getElementById('chatSendBtn');\n");
        out.write("    var convId = container ? container.getAttribute('data-conv-id') : null;\n");
        out.write("    var lastId = container ? container.getAttribute('data-last-id') : '';\n");
        out.write("\n");
        out.write("    // Enter to Send, Shift+Enter for new line\n");
        out.write("    if (textarea && form) {\n");
        out.write("      textarea.addEventListener('keydown', function(e) {\n");
        out.write("        if (e.key === 'Enter' && !e.shiftKey) {\n");
        out.write("          e.preventDefault();\n");
        out.write("          if (textarea.value.trim().length > 0) {\n");
        out.write("            form.dispatchEvent(new Event('submit', { cancelable: true }));\n");
        out.write("          }\n");
        out.write("        }\n");
        out.write("      });\n");
        out.write("\n");
        out.write("      // Progressive AJAX Send to avoid full-page flash\n");
        out.write("      form.addEventListener('submit', function(e) {\n");
        out.write("        var text = textarea.value.trim();\n");
        out.write("        if (!text) {\n");
        out.write("          e.preventDefault();\n");
        out.write("          return;\n");
        out.write("        }\n");
        out.write("        if (window.fetch) {\n");
        out.write("          e.preventDefault();\n");
        out.write("          if (sendBtn) sendBtn.disabled = true;\n");
        out.write("          var params = new URLSearchParams();\n");
        out.write("          params.append('action', 'send');\n");
        out.write("          params.append('id', convId);\n");
        out.write("          params.append('message', text);\n");
        out.write("\n");
        out.write("          fetch('/messages', {\n");
        out.write("            method: 'POST',\n");
        out.write("            headers: { 'X-Requested-With': 'XMLHttpRequest', 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },\n");
        out.write("            body: params.toString()\n");
        out.write("          }).then(function(res) {\n");
        out.write("            if (sendBtn) sendBtn.disabled = false;\n");
        out.write("            if (res.ok) {\n");
        out.write("              textarea.value = '';\n");
        out.write("              pollMessages();\n");
        out.write("            } else {\n");
        out.write("              form.submit(); // fallback to standard submit\n");
        out.write("            }\n");
        out.write("          }).catch(function() {\n");
        out.write("            if (sendBtn) sendBtn.disabled = false;\n");
        out.write("            form.submit();\n");
        out.write("          });\n");
        out.write("        }\n");
        out.write("      });\n");
        out.write("    }\n");
        out.write("\n");
        out.write("    // Bounded Polling Engine (every 4s, pauses when tab is hidden)\n");
        out.write("    var pollInterval = 4000;\n");
        out.write("    var pollTimer = null;\n");
        out.write("    var inFlight = false;\n");
        out.write("\n");
        out.write("    function pollMessages() {\n");
        out.write("      if (inFlight || !convId) return;\n");
        out.write("      if (document.hidden) return; // Bounded: pause when inactive\n");
        out.write("\n");
        out.write("      inFlight = true;\n");
        out.write("      var url = '/messages?id=' + encodeURIComponent(convId) + '&poll=true&since=' + encodeURIComponent(lastId);\n");
        out.write("      fetch(url)\n");
        out.write("        .then(function(r) { return r.json(); })\n");
        out.write("        .then(function(data) {\n");
        out.write("          inFlight = false;\n");
        out.write("          if (data && data.status) {\n");
        out.write("            var currentStatus = container ? container.getAttribute('data-status') : '';\n");
        out.write("            if (currentStatus && currentStatus !== data.status) {\n");
        out.write("              window.location.reload();\n");
        out.write("              return;\n");
        out.write("            }\n");
        out.write("          }\n");
        out.write("          if (data && data.newMessages && data.newMessages.length > 0) {\n");
        out.write("            var emptyHint = document.getElementById('chatEmptyHint');\n");
        out.write("            if (emptyHint) emptyHint.remove();\n");
        out.write("\n");
        out.write("            data.newMessages.forEach(function(m) {\n");
        out.write("              lastId = m.id;\n");
        out.write("              var wrap = document.createElement('div');\n");
        out.write("              wrap.className = 'message-bubble-wrap ' + (m.isMine ? 'bubble-outgoing' : 'bubble-incoming');\n");
        out.write("              wrap.id = 'msg-' + m.id;\n");
        out.write("\n");
        out.write("              var bubble = document.createElement('div');\n");
        out.write("              bubble.className = 'message-bubble';\n");
        out.write("\n");
        out.write("              var textEl = document.createElement('div');\n");
        out.write("              textEl.className = 'message-text';\n");
        out.write("              textEl.textContent = m.text;\n");
        out.write("\n");
        out.write("              var meta = document.createElement('div');\n");
        out.write("              meta.className = 'message-meta';\n");
        out.write("              meta.innerHTML = '<span class=\"message-time\">' + m.createdAt + '</span>' + (m.isMine ? ' <span class=\"message-status-tick\">' + (m.read ? '✓✓ Read' : '✓ Sent') + '</span>' : '');\n");
        out.write("\n");
        out.write("              bubble.appendChild(textEl);\n");
        out.write("              bubble.appendChild(meta);\n");
        out.write("              wrap.appendChild(bubble);\n");
        out.write("              container.appendChild(wrap);\n");
        out.write("            });\n");
        out.write("            container.scrollTop = container.scrollHeight;\n");
        out.write("          }\n");
        out.write("        })\n");
        out.write("        .catch(function() {\n");
        out.write("          inFlight = false;\n");
        out.write("        });\n");
        out.write("    }\n");
        out.write("\n");
        out.write("    if (convId) {\n");
        out.write("      pollTimer = setInterval(pollMessages, pollInterval);\n");
        out.write("      document.addEventListener('visibilitychange', function() {\n");
        out.write("        if (!document.hidden) {\n");
        out.write("          pollMessages();\n");
        out.write("        }\n");
        out.write("      });\n");
        out.write("    }\n");
        out.write("  })();\n");
        out.write("  </script>\n");

        renderFooter(out);
        out.write("</body>\n</html>\n");
    }

    private void renderForbiddenOrNotFound(PrintWriter out, String requestedId, int unreadNotifCount,
                                           int unreadMsgCount, boolean isAdmin, User currentUser) {
        out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        out.write("  <meta charset=\"UTF-8\">\n");
        out.write("  <title>Access Restricted | Blood Buddy</title>\n");
        out.write("  <link rel=\"stylesheet\" href=\"/static/css/style.css\">\n");
        out.write("</head>\n<body>\n");
        renderHeader(out, "/messages", unreadNotifCount, unreadMsgCount, isAdmin, currentUser);
        out.write("  <main class=\"container\" style=\"padding: 80px 20px; text-align: center;\">\n");
        out.write("    <div class=\"glass-panel\" style=\"max-width: 540px; margin: 0 auto; padding: 40px 24px; border-radius: var(--radius-lg);\">\n");
        out.write("      <div style=\"font-size: 3rem; margin-bottom: 12px;\">🔒</div>\n");
        out.write("      <h2 style=\"color: var(--text-white); margin-bottom: 10px;\">Conversation Inaccessible</h2>\n");
        out.write("      <p style=\"color: var(--text-secondary); line-height: 1.6; margin-bottom: 24px;\">You are not authorized to view conversation <strong>" + escapeHtml(requestedId) + "</strong> or this chat does not exist. Only designated participants may access private donor exchanges.</p>\n");
        out.write("      <a href=\"/messages\" class=\"btn btn-primary\"><span>Return to Inbox</span><span class=\"btn-icon\">←</span></a>\n");
        out.write("    </div>\n");
        out.write("  </main>\n");
        renderFooter(out);
        out.write("</body>\n</html>\n");
    }

    private void renderHeader(PrintWriter out, String activeNav, int unreadNotifCount,
                              int unreadMsgCount, boolean isAdmin, User currentUser) {
        out.write("  <header class=\"header\" id=\"navbar\">\n");
        out.write("    <div class=\"container nav-container\">\n");
        out.write("      <a href=\"/dashboard\" class=\"brand\" aria-label=\"Blood Buddy Home\">\n");
        out.write("        <div class=\"brand-logo-wrap\"><img src=\"/static/images/logo.svg\" alt=\"Blood Buddy Logo\" width=\"38\" height=\"38\"></div>\n");
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
        out.write("        <a href=\"/messages\" class=\"nav-utility-link" + ("/messages".equals(activeNav) ? " active-nav" : "") + "\" title=\"Direct Messages\">\n");
        out.write("          <span>💬 Messages" + (unreadMsgCount > 0 ? " <span class=\"notif-badge\">" + unreadMsgCount + "</span>" : "") + "</span>\n");
        out.write("        </a>\n");
        out.write("        <a href=\"/notifications\" class=\"nav-utility-link\" title=\"View Notifications\">\n");
        out.write("          <span>🔔 Alerts" + (unreadNotifCount > 0 ? " <span class=\"notif-badge\">" + unreadNotifCount + "</span>" : "") + "</span>\n");
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
    }

    private void renderFooter(PrintWriter out) {
        out.write("  <footer class=\"footer\">\n");
        out.write("    <div class=\"container footer-bottom\">\n");
        out.write("      <div>&copy; 2026 Blood Buddy. Secure In-App Donor Communication. Protected Contact Details.</div>\n");
        out.write("      <div class=\"backend-status-badge\"><span class=\"status-dot\"></span> Encrypted In-App Tunnel Active</div>\n");
        out.write("    </div>\n");
        out.write("  </footer>\n");
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

package com.bloodbuddy.service;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.model.AppNotification;
import com.bloodbuddy.model.User;
import com.bloodbuddy.repository.UserRepository;
import com.bloodbuddy.util.PasswordUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service managing user authentication, registration, password hashing, and session handling.
 * Unifies access for voluntary donors and blood requesters while keeping administrative
 * functions strictly guarded.
 */
public class AuthService {

    public static final String SESSION_ADMIN_KEY = "bloodbuddy_admin_user";
    public static final String SESSION_USER_KEY = "bloodbuddy_user_obj";
    public static final String SESSION_USER_ID = "bloodbuddy_user_id";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AuthService() {
        this(UserRepository.getInstance(), new NotificationService());
    }

    public AuthService(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public boolean isAdmin(HttpServletRequest req) {
        if (req == null) {
            return false;
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        if (session.getAttribute(SESSION_ADMIN_KEY) != null) {
            return true;
        }
        Object userObj = session.getAttribute(SESSION_USER_KEY);
        if (userObj instanceof User u) {
            return u.isAdmin();
        }
        return false;
    }

    public boolean isLoggedIn(HttpServletRequest req) {
        if (req == null) {
            return false;
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        return session.getAttribute(SESSION_USER_KEY) != null || session.getAttribute(SESSION_ADMIN_KEY) != null;
    }

    public User getCurrentUser(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }

        Object u = session.getAttribute(SESSION_USER_KEY);
        if (u instanceof User user) {
            Optional<User> fresh = userRepository.findById(user.getId());
            if (fresh.isPresent()) {
                session.setAttribute(SESSION_USER_KEY, fresh.get());
                return fresh.get();
            }
            return user;
        }

        // Virtual user for logged-in administrator
        String adminUser = (String) session.getAttribute(SESSION_ADMIN_KEY);
        if (adminUser != null) {
            return new User("ADMIN-01", "Hospital Coordinator (" + adminUser + ")",
                    "admin@bloodbuddy.org", adminUser, "", "", "", User.ROLE_ADMIN, "Active", "");
        }

        return null;
    }

    public String getLoggedInUser(HttpServletRequest req) {
        User u = getCurrentUser(req);
        if (u != null) {
            return u.getUsername();
        }
        return null;
    }

    /**
     * Backward-compatible login method for administrative credentials.
     */
    public boolean login(HttpServletRequest req, String username, String password) {
        User u = loginUser(req, username, password);
        return u != null && u.isAdmin();
    }

    /**
     * Unified login for both ordinary users (donors & requesters) and system administrators.
     */
    public User loginUser(HttpServletRequest req, String identifier, String password) {
        if (identifier == null || password == null) {
            return null;
        }
        String cleanId = identifier.trim();
        String cleanPass = password.trim();

        // 1. Check if administrative credentials match
        if (AppConfig.getAdminUsername().equalsIgnoreCase(cleanId)
                && AppConfig.getAdminPassword().equals(cleanPass)) {
            HttpSession session = req.getSession(true);
            session.setAttribute(SESSION_ADMIN_KEY, cleanId);
            User adminUser = new User("ADMIN-01", "Hospital Coordinator (" + cleanId + ")",
                    "admin@bloodbuddy.org", cleanId, "", "", "", User.ROLE_ADMIN, "Active", "");
            session.setAttribute(SESSION_USER_KEY, adminUser);
            session.setAttribute(SESSION_USER_ID, adminUser.getId());
            session.setMaxInactiveInterval(1800); // 30 mins
            return adminUser;
        }

        // 2. Check ordinary user repository
        Optional<User> opt = userRepository.findByEmailOrUsername(cleanId);
        if (opt.isEmpty()) {
            return null;
        }

        User user = opt.get();
        boolean valid = PasswordUtils.verifyPassword(cleanPass, user.getSalt(), user.getPasswordHash());
        if (!valid) {
            return null;
        }

        // Setup session
        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_USER_KEY, user);
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setMaxInactiveInterval(1800); // 30 mins

        return user;
    }

    /**
     * Validates and registers a new unified user account.
     * Used by both prospective donors and blood requesters.
     */
    public User registerUser(String fullName, String email, String username, String phone,
                             String password, String confirmPassword) throws ValidationException {
        return registerUser(fullName, email, username, phone, password, confirmPassword, User.PURPOSE_BOTH);
    }

    /**
     * Validates and registers a new unified user account with designated account purpose.
     * Used by donors, blood requesters, or dual-purpose community members.
     */
    public User registerUser(String fullName, String email, String username, String phone,
                             String password, String confirmPassword, String purpose) throws ValidationException {
        // Full Name validation
        if (fullName == null || fullName.trim().length() < 2 || fullName.trim().length() > AppConfig.MAX_NAME_LENGTH) {
            throw new ValidationException("Full name must be between 2 and " + AppConfig.MAX_NAME_LENGTH + " characters.");
        }

        // Email validation
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("Please provide a valid email address (e.g. name@example.com).");
        }

        // Username validation
        String cleanUsername = username != null ? username.trim() : "";
        if (cleanUsername.isEmpty()) {
            // Derive username from email prefix if empty
            cleanUsername = email.trim().split("@")[0].replaceAll("[^A-Za-z0-9_]", "");
            if (cleanUsername.length() < 3) {
                cleanUsername = cleanUsername + "user";
            }
        }
        if (!USERNAME_PATTERN.matcher(cleanUsername).matches()) {
            throw new ValidationException("Username must be 3–30 characters long and contain only letters, numbers, and underscores.");
        }

        // Prevent username collision with admin
        if (AppConfig.getAdminUsername().equalsIgnoreCase(cleanUsername)) {
            throw new ValidationException("This username is reserved. Please choose another username.");
        }

        // Phone validation
        String cleanPhone = phone != null ? phone.trim() : "";
        if (!cleanPhone.isEmpty() && cleanPhone.replaceAll("[^0-9]", "").length() < 10) {
            throw new ValidationException("Please provide a valid contact phone number with at least 10 digits.");
        }

        // Password validation
        if (password == null || password.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters long.");
        }

        if (confirmPassword == null || !password.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match. Please re-enter.");
        }

        // Purpose normalization
        String cleanPurpose = User.PURPOSE_BOTH;
        if (purpose != null) {
            String p = purpose.trim().toLowerCase();
            if (User.PURPOSE_DONATE.equals(p) || User.PURPOSE_REQUEST.equals(p) || User.PURPOSE_BOTH.equals(p)) {
                cleanPurpose = p;
            }
        }

        // Uniqueness checks
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ValidationException("An account with this email address already exists. Please sign in instead.");
        }
        if (userRepository.findByUsername(cleanUsername).isPresent()) {
            throw new ValidationException("This username is already taken. Please choose another.");
        }

        // Hash password
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);
        String nowStr = LocalDateTime.now().format(DATE_FMT);

        User newUser = new User(null, fullName.trim(), email.trim().toLowerCase(), cleanUsername.toLowerCase(),
                cleanPhone, hash, salt, User.ROLE_USER, nowStr, null, cleanPurpose);

        User saved = userRepository.save(newUser);

        // Record welcome notification
        try {
            notificationService.notifyUserRegistered(saved.getFullName(), saved.getId());
        } catch (Exception ignored) {}

        return saved;
    }

    public void linkDonorToUser(String userId, String donorId) {
        if (userId == null || donorId == null) return;
        userRepository.findById(userId).ifPresent(u -> {
            u.setLinkedDonorId(donorId);
            userRepository.update(u);
        });
    }

    public void logout(HttpServletRequest req) {
        if (req == null) {
            return;
        }
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ADMIN_KEY);
            session.removeAttribute(SESSION_USER_KEY);
            session.removeAttribute(SESSION_USER_ID);
            session.invalidate();
        }
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}

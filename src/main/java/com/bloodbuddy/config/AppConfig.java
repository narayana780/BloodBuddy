package com.bloodbuddy.config;

import java.io.File;

/**
 * Application Configuration for Blood Buddy platform.
 */
public final class AppConfig {

    public static final String APP_NAME = "Blood Buddy";
    public static final String TAGLINE = "One Drop. A Thousand Hopes.";
    public static final String APP_VERSION = "1.0.0-SNAPSHOT";
    public static final int DEFAULT_PORT = 8080;

    // Base storage directory for local file storage in subsequent stages
    public static final String DATA_DIR = System.getProperty("user.dir") + File.separator + "data";
    public static final String REGISTERED_DONORS_FILE = DATA_DIR + File.separator + "registered_donors.json";
    public static final String BLOOD_REQUESTS_FILE = DATA_DIR + File.separator + "blood_requests.json";
    public static final String NOTIFICATIONS_FILE = DATA_DIR + File.separator + "notifications.json";
    public static final String USERS_FILE = DATA_DIR + File.separator + "users.json";
    public static final String CAMPS_FILE = DATA_DIR + File.separator + "camps.json";
    public static final String CAMP_REGISTRATIONS_FILE = DATA_DIR + File.separator + "camp_registrations.json";
    public static final String CONVERSATIONS_FILE = DATA_DIR + File.separator + "conversations.json";
    public static final String MESSAGES_FILE = DATA_DIR + File.separator + "messages.json";
    public static final int MAX_CHAT_MESSAGE_LENGTH = 1000;

    // Admin authentication credentials (Stage 6/7)
    public static final String ADMIN_DEFAULT_USERNAME = "admin";
    public static final String ADMIN_DEFAULT_PASSWORD = "bloodbuddy2026";

    public static String getAdminUsername() {
        String envUser = System.getenv("BLOODBUDDY_ADMIN_USER");
        if (envUser != null && !envUser.trim().isEmpty()) {
            return envUser.trim();
        }
        return ADMIN_DEFAULT_USERNAME;
    }

    public static String getAdminPassword() {
        String envPass = System.getenv("BLOODBUDDY_ADMIN_PASSWORD");
        if (envPass != null && !envPass.trim().isEmpty()) {
            return envPass.trim();
        }
        return ADMIN_DEFAULT_PASSWORD;
    }

    // Donor validation rules (Stage 4)
    public static final int MIN_DONOR_AGE = 18;
    public static final int MAX_DONOR_AGE = 65;
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_CITY_LENGTH = 50;
    public static final int MAX_LOCALITY_LENGTH = 60;
    public static final int MAX_CONTACT_LENGTH = 100;

    // Blood request rules (Stage 5)
    public static final int MIN_BLOOD_UNITS = 1;
    public static final int MAX_BLOOD_UNITS = 20;
    public static final int MAX_HOSPITAL_NAME_LENGTH = 100;
    public static final int MAX_NOTES_LENGTH = 500;

    // Emergency Prioritization & Deadline rules (Stage 9)
    public static final int DEFAULT_DUE_SOON_HOURS_THRESHOLD = 2;

    public static int getDueSoonHoursThreshold() {
        String env = System.getenv("BLOODBUDDY_DUE_SOON_HOURS");
        if (env != null && !env.trim().isEmpty()) {
            try {
                int val = Integer.parseInt(env.trim());
                if (val > 0) return val;
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_DUE_SOON_HOURS_THRESHOLD;
    }

    private AppConfig() {
        // Prevent instantiation
    }

    public static int getServerPort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_PORT;
    }

    public static void initializeStorage() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("[AppConfig] Created local storage directory at: " + dir.getAbsolutePath());
            }
        }
    }
}

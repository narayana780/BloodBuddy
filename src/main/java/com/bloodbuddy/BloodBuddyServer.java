package com.bloodbuddy;

import com.bloodbuddy.config.AppConfig;
import com.bloodbuddy.servlets.AdminAuthServlet;
import com.bloodbuddy.servlets.AdminCampsServlet;
import com.bloodbuddy.servlets.AdminUpdateStatusServlet;
import com.bloodbuddy.servlets.AnalyticsApiServlet;
import com.bloodbuddy.servlets.AnalyticsDashboardServlet;
import com.bloodbuddy.servlets.AnalyticsExportServlet;
import com.bloodbuddy.servlets.BecomeDonorServlet;
import com.bloodbuddy.servlets.CampsServlet;
import com.bloodbuddy.servlets.ChatServlet;
import com.bloodbuddy.servlets.CompleteDonorProfileServlet;
import com.bloodbuddy.servlets.DashboardServlet;
import com.bloodbuddy.servlets.DonorProfileServlet;
import com.bloodbuddy.servlets.EmergencyDashboardServlet;
import com.bloodbuddy.servlets.FeaturePlaceholderServlet;
import com.bloodbuddy.servlets.FindDonorsServlet;
import com.bloodbuddy.servlets.HealthCheckServlet;
import com.bloodbuddy.servlets.LandingServlet;
import com.bloodbuddy.servlets.MatchingServlet;
import com.bloodbuddy.servlets.NotificationsServlet;
import com.bloodbuddy.servlets.RequestBloodServlet;
import com.bloodbuddy.servlets.RequestConfirmationServlet;
import com.bloodbuddy.servlets.StaticResourceServlet;
import com.bloodbuddy.servlets.UserAuthServlet;
import com.bloodbuddy.servlets.ViewRequestsServlet;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

/**
 * Main application entry point that starts an embedded Tomcat web server for Blood Buddy.
 */
public class BloodBuddyServer {

    public static void main(String[] args) {
        AppConfig.initializeStorage();
        String host = AppConfig.getServerHost();
        int port = AppConfig.getServerPort();

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        org.apache.catalina.connector.Connector connector = tomcat.getConnector();
        connector.setPort(port);
        connector.setProperty("address", host);


        // Set base directory for Tomcat work files
        File tempBaseDir = new File(System.getProperty("java.io.tmpdir"), "bloodbuddy-tomcat");
        if (!tempBaseDir.exists()) {
            tempBaseDir.mkdirs();
        }
        tomcat.setBaseDir(tempBaseDir.getAbsolutePath());

        // Context path: "" (root)
        File docBase = new File(".");
        Context ctx = tomcat.addContext("", docBase.getAbsolutePath());

        // 0. LandingServlet for Stage 1 Landing Page presentation
        Tomcat.addServlet(ctx, "LandingServlet", new LandingServlet());
        ctx.addServletMappingDecoded("/landing", "LandingServlet");
        ctx.addServletMappingDecoded("/welcome", "LandingServlet");
        ctx.addServletMappingDecoded("/index.html", "LandingServlet");

        // 1. DashboardServlet for root, /home, and /dashboard
        Tomcat.addServlet(ctx, "DashboardServlet", new DashboardServlet());
        ctx.addServletMappingDecoded("/", "DashboardServlet");
        ctx.addServletMappingDecoded("/home", "DashboardServlet");
        ctx.addServletMappingDecoded("/dashboard", "DashboardServlet");
        ctx.addServletMappingDecoded("/compatibility", "DashboardServlet");

        // 2. FindDonorsServlet for functional Find Donors page (Stage 3)
        Tomcat.addServlet(ctx, "FindDonorsServlet", new FindDonorsServlet());
        ctx.addServletMappingDecoded("/find-donors", "FindDonorsServlet");

        // 3. BecomeDonorServlet for informational guidance on becoming a donor
        Tomcat.addServlet(ctx, "BecomeDonorServlet", new BecomeDonorServlet());
        ctx.addServletMappingDecoded("/become-donor", "BecomeDonorServlet");

        // 3b. CompleteDonorProfileServlet for registered users completing donor details
        Tomcat.addServlet(ctx, "CompleteDonorProfileServlet", new CompleteDonorProfileServlet());
        ctx.addServletMappingDecoded("/complete-donor-profile", "CompleteDonorProfileServlet");

        // 4. DonorProfileServlet for Demo Profile View & Availability Updates (Stage 4)
        Tomcat.addServlet(ctx, "DonorProfileServlet", new DonorProfileServlet());
        ctx.addServletMappingDecoded("/donor-profile", "DonorProfileServlet");
        ctx.addServletMappingDecoded("/update-donor-availability", "DonorProfileServlet");

        // 5. RequestBloodServlet for patient blood requisitions (Stage 5)
        Tomcat.addServlet(ctx, "RequestBloodServlet", new RequestBloodServlet());
        ctx.addServletMappingDecoded("/request-blood", "RequestBloodServlet");

        // 6. RequestConfirmationServlet for requisition confirmation (Stage 5)
        Tomcat.addServlet(ctx, "RequestConfirmationServlet", new RequestConfirmationServlet());
        ctx.addServletMappingDecoded("/request-confirmation", "RequestConfirmationServlet");

        // 7. ViewRequestsServlet for live blood request board (Stage 5)
        Tomcat.addServlet(ctx, "ViewRequestsServlet", new ViewRequestsServlet());
        ctx.addServletMappingDecoded("/view-requests", "ViewRequestsServlet");
        ctx.addServletMappingDecoded("/my-requests", "ViewRequestsServlet");
        ctx.addServletMappingDecoded("/requests", "ViewRequestsServlet");

        // 8. MatchingServlet for Smart Donor Matching (Stage 7)
        Tomcat.addServlet(ctx, "MatchingServlet", new MatchingServlet());
        ctx.addServletMappingDecoded("/matching", "MatchingServlet");
        ctx.addServletMappingDecoded("/donor-matches", "MatchingServlet");
        ctx.addServletMappingDecoded("/requests/matches", "MatchingServlet");

        // 9. NotificationsServlet for In-App Notifications Center (Stage 7)
        Tomcat.addServlet(ctx, "NotificationsServlet", new NotificationsServlet());
        ctx.addServletMappingDecoded("/notifications", "NotificationsServlet");

        // 10. AdminAuthServlet for staff login/logout (Stage 6/7)
        Tomcat.addServlet(ctx, "AdminAuthServlet", new AdminAuthServlet());
        ctx.addServletMappingDecoded("/admin/login", "AdminAuthServlet");
        ctx.addServletMappingDecoded("/admin/logout", "AdminAuthServlet");
        ctx.addServletMappingDecoded("/admin", "AdminAuthServlet");

        // 11. AdminUpdateStatusServlet for request status updates (Stage 6/7)
        Tomcat.addServlet(ctx, "AdminUpdateStatusServlet", new AdminUpdateStatusServlet());
        ctx.addServletMappingDecoded("/admin/update-request-status", "AdminUpdateStatusServlet");

        // 12. FeaturePlaceholderServlet for remaining future-stage actions and About/Help Guide
        Tomcat.addServlet(ctx, "FeaturePlaceholderServlet", new FeaturePlaceholderServlet());
        ctx.addServletMappingDecoded("/more", "FeaturePlaceholderServlet");
        ctx.addServletMappingDecoded("/about", "FeaturePlaceholderServlet");
        ctx.addServletMappingDecoded("/help", "FeaturePlaceholderServlet");

        // 12b. CampsServlet for Community Blood Donation Camps
        Tomcat.addServlet(ctx, "CampsServlet", new CampsServlet());
        ctx.addServletMappingDecoded("/camps", "CampsServlet");
        ctx.addServletMappingDecoded("/camp-details", "CampsServlet");

        // 12c. AdminCampsServlet for organizer management
        Tomcat.addServlet(ctx, "AdminCampsServlet", new AdminCampsServlet());
        ctx.addServletMappingDecoded("/admin/camps", "AdminCampsServlet");

        // 13. AnalyticsDashboardServlet for executive analytics and intelligence (Stage 8)
        Tomcat.addServlet(ctx, "AnalyticsDashboardServlet", new AnalyticsDashboardServlet());
        ctx.addServletMappingDecoded("/admin/analytics", "AnalyticsDashboardServlet");
        ctx.addServletMappingDecoded("/analytics", "AnalyticsDashboardServlet");

        // 14. AnalyticsExportServlet for secure formula-protected CSV exports (Stage 8)
        Tomcat.addServlet(ctx, "AnalyticsExportServlet", new AnalyticsExportServlet());
        ctx.addServletMappingDecoded("/admin/analytics/export/*", "AnalyticsExportServlet");
        ctx.addServletMappingDecoded("/admin/analytics/export", "AnalyticsExportServlet");
        ctx.addServletMappingDecoded("/admin/export-requests", "AnalyticsExportServlet");
        ctx.addServletMappingDecoded("/admin/export-donors", "AnalyticsExportServlet");
        ctx.addServletMappingDecoded("/admin/export-summary", "AnalyticsExportServlet");

        // 15. AnalyticsApiServlet for JSON reporting data (Stage 8)
        Tomcat.addServlet(ctx, "AnalyticsApiServlet", new AnalyticsApiServlet());
        ctx.addServletMappingDecoded("/api/analytics", "AnalyticsApiServlet");

        // 16. EmergencyDashboardServlet for Emergency Control Center (Stage 9)
        Tomcat.addServlet(ctx, "EmergencyDashboardServlet", new EmergencyDashboardServlet());
        ctx.addServletMappingDecoded("/admin/emergency", "EmergencyDashboardServlet");
        ctx.addServletMappingDecoded("/admin/control-center", "EmergencyDashboardServlet");

        // 17. UserAuthServlet for unified user login, registration, and account management
        Tomcat.addServlet(ctx, "UserAuthServlet", new UserAuthServlet());
        ctx.addServletMappingDecoded("/login", "UserAuthServlet");
        ctx.addServletMappingDecoded("/login.html", "UserAuthServlet");
        ctx.addServletMappingDecoded("/signin", "UserAuthServlet");
        ctx.addServletMappingDecoded("/register", "UserAuthServlet");
        ctx.addServletMappingDecoded("/signup", "UserAuthServlet");
        ctx.addServletMappingDecoded("/logout", "UserAuthServlet");
        ctx.addServletMappingDecoded("/account", "UserAuthServlet");
        ctx.addServletMappingDecoded("/profile", "UserAuthServlet");

        // 18. ChatServlet for secure in-app messaging between requesters and donors
        Tomcat.addServlet(ctx, "ChatServlet", new ChatServlet());
        ctx.addServletMappingDecoded("/messages", "ChatServlet");
        ctx.addServletMappingDecoded("/messages/*", "ChatServlet");
        ctx.addServletMappingDecoded("/chat", "ChatServlet");
        ctx.addServletMappingDecoded("/conversations", "ChatServlet");

        // 4. StaticResourceServlet for all /static/* assets (CSS, SVG, images)
        Tomcat.addServlet(ctx, "StaticResourceServlet", new StaticResourceServlet());
        ctx.addServletMappingDecoded("/static/*", "StaticResourceServlet");

        // 5. HealthCheckServlet for API status checks
        Tomcat.addServlet(ctx, "HealthCheckServlet", new HealthCheckServlet());
        ctx.addServletMappingDecoded("/api/status", "HealthCheckServlet");
        ctx.addServletMappingDecoded("/health", "HealthCheckServlet");
        ctx.addServletMappingDecoded("/healthz", "HealthCheckServlet");

        try {
            System.out.println("==================================================");
            System.out.println("  Starting " + AppConfig.APP_NAME + " Web Server");
            System.out.println("  Tagline: " + AppConfig.TAGLINE);
            System.out.println("  Host:    " + host);
            System.out.println("  Port:    " + port);
            System.out.println("  URL:     http://" + (host.equals("0.0.0.0") ? "localhost" : host) + ":" + port);
            System.out.println("==================================================");

            tomcat.start();
            System.out.println("[BloodBuddyServer] Server started successfully and listening on " + host + ":" + port);

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("[BloodBuddyServer] Stopping server...");
                    tomcat.stop();
                    tomcat.destroy();
                } catch (LifecycleException e) {
                    System.err.println("[BloodBuddyServer] Error during server stop: " + e.getMessage());
                }
            }));

            tomcat.getServer().await();
        } catch (LifecycleException e) {
            System.err.println("[BloodBuddyServer] Fatal error starting server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

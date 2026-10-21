package com.bloodbuddy.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * LandingServlet handles root navigation and renders the Blood Buddy landing page.
 */
public class LandingServlet extends HttpServlet {

    private static final String TEMPLATE_RESOURCE = "/templates/index.html";
    private static final String DEV_FILE_PATH = "src/main/resources/templates/index.html";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        byte[] content = loadLandingPage();
        if (content != null) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentLength(content.length);
            try (OutputStream out = resp.getOutputStream()) {
                out.write(content);
                out.flush();
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Landing page template not found.");
        }
    }

    private byte[] loadLandingPage() {
        // 1. Try local dev file system first (for live changes during development)
        File devFile = new File(DEV_FILE_PATH);
        if (devFile.exists() && devFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(devFile)) {
                return fis.readAllBytes();
            } catch (IOException ignored) {
            }
        }

        // 2. Fall back to classpath resource
        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException ignored) {
        }

        return null;
    }
}

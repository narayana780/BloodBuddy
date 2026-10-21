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
import java.util.HashMap;
import java.util.Map;

/**
 * StaticResourceServlet serves CSS, JavaScript, SVG, and other static assets.
 */
public class StaticResourceServlet extends HttpServlet {

    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("css", "text/css; charset=UTF-8");
        MIME_TYPES.put("js", "application/javascript; charset=UTF-8");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("json", "application/json; charset=UTF-8");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("ttf", "font/ttf");
        MIME_TYPES.put("glb", "model/gltf-binary");
        MIME_TYPES.put("gltf", "model/gltf+json");
        MIME_TYPES.put("bin", "application/octet-stream");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.contains("..") || pathInfo.contains("\\")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid resource path.");
            return;
        }

        // Determine content type
        String extension = "";
        int dotIdx = pathInfo.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < pathInfo.length() - 1) {
            extension = pathInfo.substring(dotIdx + 1).toLowerCase();
        }
        String contentType = MIME_TYPES.getOrDefault(extension, "application/octet-stream");
        resp.setContentType(contentType);

        // Security / cache headers
        resp.setHeader("Cache-Control", "no-cache, must-revalidate");

        byte[] data = loadResource(pathInfo);
        if (data != null) {
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentLength(data.length);
            try (OutputStream out = resp.getOutputStream()) {
                out.write(data);
                out.flush();
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Static resource not found: " + pathInfo);
        }
    }

    private byte[] loadResource(String path) {
        // Strip leading slash if needed
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

        // 1. Try local dev file system with canonical path boundary verification
        File baseDir = new File("src/main/resources/static");
        File devFile = new File(baseDir, normalizedPath);
        try {
            if (devFile.exists() && devFile.isFile() && devFile.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
                try (FileInputStream fis = new FileInputStream(devFile)) {
                    return fis.readAllBytes();
                }
            }
        } catch (IOException ignored) {
        }

        // 2. Try classpath
        String resourcePath = "/static/" + normalizedPath;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return is.readAllBytes();
            }
        } catch (IOException ignored) {
        }

        return null;
    }
}

package com.bloodbuddy.servlets;

import com.bloodbuddy.config.AppConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

/**
 * HealthCheckServlet exposes basic runtime health information.
 */
public class HealthCheckServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_OK);

        try (PrintWriter writer = resp.getWriter()) {
            writer.write(String.format(
                "{\"status\":\"UP\",\"app\":\"%s\",\"tagline\":\"%s\",\"version\":\"%s\",\"timestamp\":\"%s\"}",
                AppConfig.APP_NAME,
                AppConfig.TAGLINE,
                AppConfig.APP_VERSION,
                Instant.now().toString()
            ));
        }
    }
}

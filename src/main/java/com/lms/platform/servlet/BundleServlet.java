package com.lms.platform.servlet;

import com.lms.platform.dao.CourseDAO;
import com.lms.platform.model.Course;
import com.lms.platform.service.BundleService;
import com.lms.platform.util.AppConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.*;
import java.net.URLConnection;
import java.nio.file.*;

/**
 * Serves course bundle files under /course/{courseId}/{resource}
 * For HTML files: injects window.LMS bridge script before </head>
 */
@WebServlet(urlPatterns = "/course/*")
public class BundleServlet extends HttpServlet {

    private final CourseDAO courseDAO = new CourseDAO();

    /** Returns the context path as seen by the browser (proxy-aware) */
    private String proxyContext(HttpServletRequest req) {
        String proxy = AppConfig.get("lms.proxy.context", "").trim();
        if (!proxy.isEmpty()) return "/".equals(proxy) ? "" : proxy;
        return req.getContextPath();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() < 2) {
            resp.sendError(404); return;
        }

        // Parse /course/{id}/resource/path
        String[] parts = pathInfo.substring(1).split("/", 2);
        long courseId;
        try { courseId = Long.parseLong(parts[0]); } catch (NumberFormatException e) { resp.sendError(404); return; }

        String resource = parts.length > 1 ? parts[1] : "";

        try {
            Course course = courseDAO.findById(courseId);
            if (course == null) { resp.sendError(404, "Cours non trouve"); return; }

            // Check access: session required
            HttpSession session = req.getSession(false);
            if (session == null || session.getAttribute("userId") == null) {
                String eCtx = proxyContext(req);
                resp.sendRedirect(eCtx + "/login.html"); return;
            }

            // Default to entry-point if no resource specified
            if (resource.isEmpty()) resource = course.getEntryPoint();

            BundleService bs = (BundleService) getServletContext().getAttribute("bundleService");
            Path filePath = bs.resolveResource(course, resource);
            if (filePath == null) { resp.sendError(404, "Ressource non trouvee: " + resource); return; }

            // Determine content type
            String contentType = URLConnection.guessContentTypeFromName(filePath.toString());
            if (contentType == null) {
                String ext = resource.substring(resource.lastIndexOf('.') + 1).toLowerCase();
                contentType = switch (ext) {
                    case "html", "htm" -> "text/html;charset=UTF-8";
                    case "css" -> "text/css;charset=UTF-8";
                    case "js", "mjs" -> "application/javascript;charset=UTF-8";
                    case "json" -> "application/json;charset=UTF-8";
                    case "svg" -> "image/svg+xml";
                    case "png" -> "image/png";
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "gif" -> "image/gif";
                    case "woff2" -> "font/woff2";
                    case "woff" -> "font/woff";
                    case "ttf" -> "font/ttf";
                    case "ico" -> "image/x-icon";
                    default -> "application/octet-stream";
                };
            }
            resp.setContentType(contentType);

            // For HTML files: inject window.LMS bridge
            if (resource.endsWith(".html") || resource.endsWith(".htm")) {
                String html = Files.readString(filePath);
                String injected = injectLmsBridge(html, req, course);
                resp.setContentType("text/html;charset=UTF-8");
                resp.getWriter().write(injected);
            } else {
                // Binary / other files: stream directly
                resp.setContentLength((int) Files.size(filePath));
                try (OutputStream out = resp.getOutputStream()) {
                    Files.copy(filePath, out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(500, e.getMessage());
        }
    }

    /**
     * Injects the window.LMS bridge script before </head>.
     * This provides the contract between the bundle and the platform.
     */
    private String injectLmsBridge(String html, HttpServletRequest req, Course course) {
        HttpSession session = req.getSession(false);
        if (session == null) return html;

        long userId = (long) session.getAttribute("userId");
        String displayName = (String) session.getAttribute("displayName");
        String role = (String) session.getAttribute("role");
        String ctx = proxyContext(req);

        String script = """
        <script>
        // ── LMS Platform Bridge ──
        // Injected by the platform. Provides integration API to bundles.
        window.LMS = {
            user: {
                id: %d,
                displayName: "%s",
                role: "%s"
            },
            course: {
                id: %d,
                title: "%s",
                bundleId: "%s",
                quizEnabled: %s,
                passThreshold: %d,
                maxAttempts: %d
            },
            contextPath: "%s",

            // Submit quiz results to the platform
            async submitQuiz(results) {
                const payload = Object.assign({}, results, { courseId: this.course.id });
                const resp = await fetch(this.contextPath + '/api/quiz/submit', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                return await resp.json();
            },

            // Get quiz status (attempts used, best score)
            async getQuizStatus() {
                const resp = await fetch(this.contextPath + '/api/student/quiz-status/' + this.course.id);
                return await resp.json();
            },

            // Navigate back to the platform dashboard
            goToDashboard() {
                window.location.href = this.contextPath + '/app.html';
            },

            // Navigate to course list
            goToCourses() {
                window.location.href = this.contextPath + '/app.html#courses';
            }
        };
        console.log('[LMS] Platform bridge injected for course: ' + window.LMS.course.title);
        </script>
        """.formatted(
                userId,
                escapeJs(displayName),
                role,
                course.getId(),
                escapeJs(course.getTitle()),
                escapeJs(course.getBundleId()),
                course.isQuizEnabled(),
                course.getPassThreshold(),
                course.getMaxAttempts(),
                ctx
        );

        // Inject before </head>
        int headClose = html.toLowerCase().indexOf("</head>");
        if (headClose >= 0) {
            return html.substring(0, headClose) + script + html.substring(headClose);
        } else {
            // No </head>? Prepend to body
            return script + html;
        }
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}

package com.lms.platform.servlet;

import com.lms.platform.dao.PluginDAO;
import com.lms.platform.model.Plugin;
import com.lms.platform.util.AppConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;

/**
 * Serves plugin files under /plugin/{pluginId}/{resource}
 * For HTML files: injects window.LMS_PLUGIN bridge before </head>
 */
@WebServlet(urlPatterns = "/plugin/*")
public class PluginServlet extends HttpServlet {

    private final PluginDAO pluginDAO = new PluginDAO();

    private String proxyContext(HttpServletRequest req) {
        String proxy = AppConfig.get("lms.proxy.context", "").trim();
        if (!proxy.isEmpty()) return "/".equals(proxy) ? "" : proxy;
        return req.getContextPath();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() < 2) { resp.sendError(404); return; }

        // Parse /plugin/{pluginId}/resource/path
        String[] parts = pathInfo.substring(1).split("/", 2);
        String pluginId = parts[0];
        String resource = parts.length > 1 ? parts[1] : "index.html";

        try {
            Plugin plugin = pluginDAO.findByPluginId(pluginId);
            if (plugin == null) { resp.sendError(404, "Plugin not found: " + pluginId); return; }
            if (plugin.getStatus() != Plugin.Status.ACTIVE) { resp.sendError(403, "Plugin disabled"); return; }

            // Check role access
            HttpSession session = req.getSession(false);
            if (session != null) {
                String role = (String) session.getAttribute("role");
                if (!plugin.isAccessibleBy(role)) { resp.sendError(403, "Access denied"); return; }
            }

            // Resolve file
            String dataDir = AppConfig.get("lms.data.dir", System.getProperty("user.home") + "/lms-data");
            Path pluginDir = Path.of(dataDir, "plugins", pluginId);
            Path file = pluginDir.resolve(resource).normalize();

            // Security: prevent path traversal
            if (!file.startsWith(pluginDir)) { resp.sendError(403, "Access denied"); return; }
            if (!Files.exists(file)) {
                // Try entry point
                if (resource.equals("index.html") || resource.isEmpty()) {
                    file = pluginDir.resolve(plugin.getEntryPoint()).normalize();
                }
                if (!Files.exists(file)) { resp.sendError(404, "File not found: " + resource); return; }
            }

            // Set content type
            String ct = getServletContext().getMimeType(file.toString());
            if (ct == null) ct = "application/octet-stream";
            resp.setContentType(ct);

            // For HTML: inject LMS bridge
            if (ct.contains("html")) {
                resp.setCharacterEncoding("UTF-8");
                String html = Files.readString(file);
                html = injectPluginBridge(html, req, plugin);
                resp.getWriter().write(html);
            } else {
                resp.setContentLengthLong(Files.size(file));
                try (InputStream is = Files.newInputStream(file)) {
                    is.transferTo(resp.getOutputStream());
                }
            }

        } catch (Exception e) {
            resp.sendError(500, e.getMessage());
        }
    }

    /**
     * Injects window.LMS_PLUGIN bridge before </head>.
     * Provides: user info, plugin config, API helper, navigation.
     */
    private String injectPluginBridge(String html, HttpServletRequest req, Plugin plugin) {
        HttpSession session = req.getSession(false);
        if (session == null) return html;

        long userId = (long) session.getAttribute("userId");
        String displayName = (String) session.getAttribute("displayName");
        String role = (String) session.getAttribute("role");
        String ctx = proxyContext(req);

        String script = """
        <script>
        // ── LMS Plugin Bridge ──
        window.LMS_PLUGIN = {
            user: { id: %d, displayName: "%s", role: "%s" },
            plugin: { id: "%s", name: "%s", version: "%s" },
            config: %s,
            contextPath: "%s",

            // Fetch wrapper with auth (same session)
            async api(path, options) {
                const resp = await fetch(this.contextPath + '/api' + path, options);
                return await resp.json();
            },

            // Get users list (if admin)
            async getUsers() { return this.api('/admin/users'); },

            // Get groups
            async getGroups() { return this.api('/groups/all'); },

            // Get courses
            async getCourses() { return this.api('/public/catalog'); },

            // Read plugin config
            getConfig(key, defaultValue) {
                try { return JSON.parse(this.config)[key] || defaultValue; }
                catch(e) { return defaultValue; }
            },

            // Save plugin config (admin only)
            async saveConfig(configObj) {
                return this.api('/admin/plugins/' + this.plugin.id + '/config', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ config: JSON.stringify(configObj) })
                });
            },

            // Navigate back to portal
            goToPortal() { window.location.href = this.contextPath + '/app.html'; },

            // Navigate to another plugin
            goToPlugin(pluginId) { window.location.href = this.contextPath + '/app.html#plugin-' + pluginId; },

            // Show notification in parent frame (if in iframe)
            notify(message, type) {
                if (window.parent !== window) {
                    window.parent.postMessage({ type: 'lms-plugin-notify', message, level: type || 'info' }, '*');
                }
            }
        };
        console.log('[LMS Plugin] Bridge loaded: ' + window.LMS_PLUGIN.plugin.name + ' v' + window.LMS_PLUGIN.plugin.version);
        </script>
        """.formatted(
                userId,
                escapeJs(displayName),
                role,
                escapeJs(plugin.getPluginId()),
                escapeJs(plugin.getName()),
                escapeJs(plugin.getVersion()),
                plugin.getConfigJson() != null ? plugin.getConfigJson() : "{}",
                escapeJs(ctx)
        );

        int headEnd = html.indexOf("</head>");
        if (headEnd < 0) headEnd = html.indexOf("<body");
        if (headEnd < 0) return script + html;
        return html.substring(0, headEnd) + script + html.substring(headEnd);
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}

package com.lms.platform.filter;

import com.lms.platform.dao.AuditDAO;
import com.lms.platform.util.AppConfig;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;

@WebFilter(urlPatterns = "/*")
public class AuthFilter implements Filter {

    private static final Set<String> PUBLIC = Set.of(
        "/home.html", "/login.html", "/register.html",
        "/api/auth/login", "/api/auth/logout",
        "/api/auth/session", "/api/auth/settings",
        "/api/auth/register",
        "/api/auth/oauth/google", "/api/auth/oauth/google/callback",
        "/api/auth/oauth/microsoft", "/api/auth/oauth/microsoft/callback",
        "/api/public/", "/api/public/catalog", "/api/public/documents", "/api/public/company", "/api/public/search",
        "/css/", "/js/", "/assets/", "/favicon.ico"
    );

    private final AuditDAO auditDAO = new AuditDAO();

    /**
     * Returns the effective context path as seen by the browser.
     * Behind a reverse proxy mapping / -> /lms/, the browser sees "/"
     * while Tomcat sees "/lms". This method returns the proxy-side path.
     */
    private String effectiveContext(HttpServletRequest req) {
        String proxy = AppConfig.get("lms.proxy.context", "").trim();
        if (!proxy.isEmpty()) {
            // Configured proxy context (e.g. "/" or "/app")
            return "/".equals(proxy) ? "" : proxy;
        }
        // No proxy: use real Tomcat context path
        return req.getContextPath();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest hReq = (HttpServletRequest) req;
        HttpServletResponse hRes = (HttpServletResponse) res;
        String path = hReq.getRequestURI();
        String ctx = hReq.getContextPath();
        String raw = path.startsWith(ctx) ? path.substring(ctx.length()) : path;
        final String rel = raw.isEmpty() ? "/" : raw;

        // Public resources — no auth needed
        if ("/".equals(rel) || PUBLIC.stream().anyMatch(p -> rel.startsWith(p) || rel.equals(p))) {
            chain.doFilter(req, res); return;
        }

        // Check session
        HttpSession session = hReq.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            if (rel.startsWith("/api/")) {
                hRes.setStatus(401); hRes.setContentType("application/json;charset=UTF-8");
                hRes.getWriter().write("{\"error\":true,\"message\":\"Non authentifie\"}");
            } else {
                // Redirect using the proxy-aware context, not Tomcat's internal one
                String eCtx = effectiveContext(hReq);
                hRes.sendRedirect(eCtx + "/login.html");
            }
            return;
        }

        // Audit: track mutating API calls
        if (rel.startsWith("/api/") && !"GET".equals(hReq.getMethod())) {
            auditDAO.log(
                (long) session.getAttribute("userId"),
                (String) session.getAttribute("displayName"),
                hReq.getMethod() + " " + rel, "",
                hReq.getRemoteAddr(), session.getId()
            );
        }

        // Role-based access — admin endpoints
        String role = (String) session.getAttribute("role");
        if (rel.startsWith("/api/admin") && !"ADMIN".equals(role)) {
            hRes.setStatus(403); hRes.setContentType("application/json;charset=UTF-8");
            hRes.getWriter().write("{\"error\":true,\"message\":\"Acces refuse\"}");
            return;
        }

        chain.doFilter(req, res);
    }
}

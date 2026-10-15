package com.lms.platform.servlet;

import com.lms.platform.dao.*;
import com.lms.platform.model.*;
import com.lms.platform.service.BundleService;
import com.lms.platform.service.OAuthService;
import com.lms.platform.service.BackupService;
import com.lms.platform.service.SearchService;
import com.lms.platform.service.AiService;
import com.lms.platform.service.AiJobManager;
import com.lms.platform.dao.PluginDAO;
import com.lms.platform.model.Plugin;
import com.lms.platform.util.*;
import com.google.gson.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;

@WebServlet(urlPatterns = "/api/*")
@MultipartConfig(maxFileSize = 200_000_000, maxRequestSize = 200_000_000)
public class ApiServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();
    private final CourseDAO courseDAO = new CourseDAO();
    private final QuizResultDAO quizDAO = new QuizResultDAO();
    private final AuditDAO auditDAO = new AuditDAO();
    private final PluginDAO pluginDAO = new PluginDAO();
    private final SettingDAO settingDAO = new SettingDAO();
    private final Gson gson = JsonHelper.gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonHelper.json(resp);
        String p = req.getPathInfo() != null ? req.getPathInfo() : "";
        try {
            // ── Auth ──
            if ("/auth/session".equals(p)) { getSession(req, resp); return; }
            if ("/auth/settings".equals(p)) { getPublicSettings(resp); return; }

            // ── OAuth2 redirects (GET) ──
            if ("/auth/oauth/google".equals(p)) { oauthRedirect(req, resp, "google"); return; }
            if ("/auth/oauth/google/callback".equals(p)) { oauthCallback(req, resp, "google"); return; }
            if ("/auth/oauth/microsoft".equals(p)) { oauthRedirect(req, resp, "microsoft"); return; }
            if ("/auth/oauth/microsoft/callback".equals(p)) { oauthCallback(req, resp, "microsoft"); return; }

            // ── Public pages (no auth) ──
            if ("/public/catalog".equals(p)) { getPublicCatalog(resp, Course.ContentType.FORMATION); return; }
            if ("/public/documents".equals(p)) { getPublicCatalog(resp, Course.ContentType.DOCUMENT); return; }
            if ("/public/company".equals(p)) { getCompanyInfo(resp); return; }
            if ("/public/search".equals(p)) { doSearch(req, resp); return; }

            // ── Profile (authenticated users) ──
            if ("/profile".equals(p)) { getProfile(req, resp); return; }

            // ── Plugins (active for current user role) ──
            if ("/plugins/active".equals(p)) {
                String role = (String) req.getSession().getAttribute("role");
                JsonHelper.ok(resp, pluginDAO.findActiveForRole(role)); return;
            }

            // ── AI Streaming (SSE — must be GET for EventSource) ──
            if ("/formateur/ai/stream".equals(p)) {
                if (!checkRole(req, resp, "FORMATEUR", "ADMIN")) return;
                doAiStream(req, resp); return;
            }

            // ── GED (documents) ──
            if (p.startsWith("/ged/")) {
                if (!checkRole(req, resp, "GED", "ETUDIANT", "FORMATEUR", "ADMIN")) return;
                long uid = userId(req);
                if ("/ged/documents".equals(p)) {
                    JsonHelper.ok(resp, courseDAO.findPublishedDocumentsForUser(uid)); return;
                }
                JsonHelper.err(resp, 404, "Not found"); return;
            }

            // ── Admin ──
            if (p.startsWith("/admin/")) {
                if (!checkRole(req, resp, "ADMIN")) return;
                // Backup export returns XML, not JSON
                if ("/admin/backup/export".equals(p)) { doBackupExport(req, resp); return; }
                switch (p) {
                    case "/admin/users" -> JsonHelper.ok(resp, userDAO.findAll());
                    case "/admin/groups" -> JsonHelper.ok(resp, userDAO.findAllGroups());
                    case "/admin/pending" -> JsonHelper.ok(resp, userDAO.findPending());
                    case "/admin/pending/count" -> { JsonObject j = new JsonObject(); j.addProperty("count", userDAO.countPending()); JsonHelper.ok(resp, j); }
                    case "/admin/courses" -> JsonHelper.ok(resp, courseDAO.findAllByType(Course.ContentType.FORMATION));
                    case "/admin/documents" -> JsonHelper.ok(resp, courseDAO.findAllByType(Course.ContentType.DOCUMENT));
                    case "/admin/all-content" -> JsonHelper.ok(resp, courseDAO.findAll());
                    case "/admin/settings" -> JsonHelper.ok(resp, settingDAO.getAll());
                    case "/admin/settings/debug" -> debugSettings(resp);
                    case "/admin/plugins" -> JsonHelper.ok(resp, pluginDAO.findAll());
                    case "/admin/system" -> getSystemStats(resp);
                    case "/admin/audit" -> JsonHelper.ok(resp, auditDAO.findRecent(200));
                    case "/admin/sessions" -> JsonHelper.ok(resp, auditDAO.activeSessions());
                    case "/admin/stats" -> getAdminDashboard(resp);
                    case "/admin/results" -> JsonHelper.ok(resp, quizDAO.findAll());
                    default -> {
                        if (p.startsWith("/admin/user-groups/"))
                            JsonHelper.ok(resp, userDAO.getUserGroupIds(longParam(p, "/admin/user-groups/")));
                        else if (p.startsWith("/admin/course-groups/"))
                            JsonHelper.ok(resp, courseDAO.getCourseGroupIds(longParam(p, "/admin/course-groups/")));
                        else if (p.startsWith("/admin/course-backups/"))
                            JsonHelper.ok(resp, courseDAO.getBackups(longParam(p, "/admin/course-backups/")));
                        else JsonHelper.err(resp, 404, "Not found");
                    }
                }
                return;
            }

            // ── Formateur ──
            if (p.startsWith("/formateur/")) {
                if (!checkRole(req, resp, "FORMATEUR", "ADMIN")) return;
                long uid = userId(req);
                switch (p) {
                    case "/formateur/courses" -> {
                        String role = (String) req.getSession().getAttribute("role");
                        JsonHelper.ok(resp, "ADMIN".equals(role) ?
                            courseDAO.findAllByType(Course.ContentType.FORMATION) :
                            courseDAO.findByAuthorAndType(uid, Course.ContentType.FORMATION));
                    }
                    case "/formateur/results" -> JsonHelper.ok(resp, quizDAO.findAll());
                    case "/formateur/ai/config" -> {
                        JsonObject j = new JsonObject();
                        j.addProperty("enabled", aiService.isEnabled());
                        j.addProperty("model", aiService.getModel());
                        j.addProperty("systemPrompt", aiService.getSystemPrompt());
                        JsonHelper.ok(resp, j);
                    }
                    default -> {
                        if (p.startsWith("/formateur/results/course/"))
                            JsonHelper.ok(resp, quizDAO.findByCourse(longParam(p, "/formateur/results/course/")));
                        else if (p.startsWith("/formateur/ai/job/")) {
                            String jobId = p.substring("/formateur/ai/job/".length());
                            AiJobManager.AiJob job = AiJobManager.getInstance().getJob(jobId);
                            if (job == null) JsonHelper.err(resp, 404, "Job introuvable: " + jobId);
                            else JsonHelper.ok(resp, job.toJson());
                        }
                        else JsonHelper.err(resp, 404, "Not found");
                    }
                }
                return;
            }

            // ── Groups (Admin + Formateur) ──
            if (p.startsWith("/groups/")) {
                if (!checkRole(req, resp, "ADMIN", "FORMATEUR")) return;
                if (p.equals("/groups/all")) {
                    JsonHelper.ok(resp, userDAO.findAllGroups()); return;
                }
                // /groups/{id}/members → users in this group
                if (p.matches("/groups/\\d+/members")) {
                    long gid = Long.parseLong(p.split("/")[2]);
                    JsonHelper.ok(resp, userDAO.findByGroup(gid)); return;
                }
                // /groups/{id}/courses → courses assigned to this group
                if (p.matches("/groups/\\d+/courses")) {
                    long gid = Long.parseLong(p.split("/")[2]);
                    JsonHelper.ok(resp, courseDAO.findByGroup(gid)); return;
                }
                // /groups/{id}/available-users → all users NOT in this group
                if (p.matches("/groups/\\d+/available-users")) {
                    long gid = Long.parseLong(p.split("/")[2]);
                    var inGroup = userDAO.findByGroup(gid);
                    var all = userDAO.findAll();
                    var inGroupIds = inGroup.stream().map(u -> u.getId()).toList();
                    var available = all.stream().filter(u -> !inGroupIds.contains(u.getId())).toList();
                    JsonHelper.ok(resp, available); return;
                }
                // /groups/{id}/available-courses → all courses NOT assigned to this group
                if (p.matches("/groups/\\d+/available-courses")) {
                    long gid = Long.parseLong(p.split("/")[2]);
                    var inGroup = courseDAO.findByGroup(gid);
                    var all = courseDAO.findAll();
                    var inGroupIds = inGroup.stream().map(c -> c.getId()).toList();
                    var available = all.stream().filter(c -> !inGroupIds.contains(c.getId())).toList();
                    JsonHelper.ok(resp, available); return;
                }
                JsonHelper.err(resp, 404, "Not found"); return;
            }

            // ── Student ──
            if (p.startsWith("/student/")) {
                long uid = userId(req);
                switch (p) {
                    case "/student/courses" -> JsonHelper.ok(resp, courseDAO.findPublishedForUser(uid));
                    case "/student/results" -> JsonHelper.ok(resp, quizDAO.findByUser(uid));
                    case "/student/progress" -> JsonHelper.ok(resp, quizDAO.studentProgress(uid));
                    default -> {
                        if (p.startsWith("/student/quiz-status/")) {
                            long cid = longParam(p, "/student/quiz-status/");
                            Course co = courseDAO.findById(cid);
                            JsonObject j = new JsonObject();
                            j.addProperty("attemptsUsed", quizDAO.getAttemptCount(uid, cid));
                            j.addProperty("attemptsMax", co != null ? co.getMaxAttempts() : 0);
                            j.addProperty("bestScore", quizDAO.getBestScore(uid, cid));
                            JsonHelper.ok(resp, j);
                        } else JsonHelper.err(resp, 404, "Not found");
                    }
                }
                return;
            }

            // ── Reporting ──
            if ("/reporting/global".equals(p)) { JsonHelper.ok(resp, quizDAO.globalStats()); return; }
            if ("/reporting/by-course".equals(p)) { JsonHelper.ok(resp, quizDAO.statsByCourse()); return; }
            if (p.startsWith("/reporting/student/")) {
                JsonHelper.ok(resp, quizDAO.studentProgress(longParam(p, "/reporting/student/"))); return;
            }

            JsonHelper.err(resp, 404, "Not found");
        } catch (Exception e) { e.printStackTrace(); JsonHelper.err(resp, 500, e.getMessage()); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonHelper.json(resp);
        String p = req.getPathInfo() != null ? req.getPathInfo() : "";
        try {
            // ── Auth ──
            if ("/auth/login".equals(p)) { doLogin(req, resp); return; }
            if ("/auth/logout".equals(p)) { doLogout(req, resp); return; }
            if ("/auth/change-password".equals(p)) { doChangePassword(req, resp); return; }
            if ("/auth/register".equals(p)) { doRegister(req, resp); return; }

            // ── Profile (self-service, all authenticated users) ──
            if ("/profile".equals(p)) { updateProfile(req, resp); return; }
            if ("/profile/password".equals(p)) { changeOwnPassword(req, resp); return; }
            if ("/profile/photo".equals(p)) { uploadProfilePhoto(req, resp); return; }

            // ── Admin ──
            if (p.startsWith("/admin/")) {
                if (!checkRole(req, resp, "ADMIN")) return;
                switch (p) {
                    case "/admin/users" -> createUser(req, resp);
                    case "/admin/groups" -> createGroup(req, resp);
                    case "/admin/user-groups" -> manageUserGroups(req, resp);
                    case "/admin/course-groups" -> manageCourseGroups(req, resp);
                    case "/admin/users/import-csv" -> importCsv(req, resp);
                    case "/admin/approve" -> approveUser(req, resp);
                    case "/admin/reject" -> rejectUser(req, resp);
                    case "/admin/backup/restore" -> doBackupRestore(req, resp);
                    case "/admin/reindex" -> { int n = searchService.reindexAll(); JsonObject j2 = JsonHelper.success(); j2.addProperty("indexed", n); JsonHelper.ok(resp, j2); }
                    case "/admin/plugins/upload" -> uploadPlugin(req, resp);
                    default -> {
                        if (p.startsWith("/admin/course-rollback/"))
                            rollbackCourse(req, resp, p);
                        else JsonHelper.err(resp, 404, "Not found");
                    }
                }
                return;
            }

            // ── Formateur ──
            if ("/formateur/upload".equals(p)) {
                if (!checkRole(req, resp, "FORMATEUR", "ADMIN")) return;
                uploadBundle(req, resp); return;
            }
            if ("/formateur/ai/generate".equals(p)) {
                if (!checkRole(req, resp, "FORMATEUR", "ADMIN")) return;
                doAiGenerate(req, resp); return;
            }
            if ("/formateur/ai/submit".equals(p)) {
                if (!checkRole(req, resp, "FORMATEUR", "ADMIN")) return;
                doAiSubmitJob(req, resp); return;
            }
            if ("/formateur/ai/publish".equals(p)) {
                if (!checkRole(req, resp, "FORMATEUR", "ADMIN")) return;
                doAiPublish(req, resp); return;
            }

            // ── Groups (Admin + Formateur) ──
            if (p.startsWith("/groups/")) {
                if (!checkRole(req, resp, "ADMIN", "FORMATEUR")) return;
                if (p.equals("/groups/create")) {
                    JsonObject body = JsonHelper.readBody(req);
                    Group g = new Group(); g.setName(JsonHelper.str(body, "name")); g.setDescription(JsonHelper.str(body, "description"));
                    long id = userDAO.createGroup(g);
                    JsonObject r = JsonHelper.success(); r.addProperty("id", id);
                    JsonHelper.ok(resp, r); return;
                }
                // /groups/{id}/members → replace all members
                if (p.matches("/groups/\\d+/members")) {
                    long gid = Long.parseLong(p.split("/")[2]);
                    JsonObject body = JsonHelper.readBody(req);
                    JsonArray arr = body.getAsJsonArray("userIds");
                    java.util.List<Long> ids = new java.util.ArrayList<>();
                    if (arr != null) for (var el : arr) ids.add(el.getAsLong());
                    userDAO.setGroupMembers(gid, ids);
                    JsonHelper.ok(resp, JsonHelper.success()); return;
                }
                // /groups/{id}/courses → replace all courses
                if (p.matches("/groups/\\d+/courses")) {
                    long gid = Long.parseLong(p.split("/")[2]);
                    JsonObject body = JsonHelper.readBody(req);
                    JsonArray arr = body.getAsJsonArray("courseIds");
                    java.util.List<Long> ids = new java.util.ArrayList<>();
                    if (arr != null) for (var el : arr) ids.add(el.getAsLong());
                    courseDAO.setGroupCourses(gid, ids);
                    JsonHelper.ok(resp, JsonHelper.success()); return;
                }
                JsonHelper.err(resp, 404, "Not found"); return;
            }

            // ── Quiz ──
            if ("/quiz/submit".equals(p)) { submitQuiz(req, resp); return; }

            JsonHelper.err(resp, 404, "Not found");
        } catch (Exception e) { e.printStackTrace(); JsonHelper.err(resp, 500, e.getMessage()); }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonHelper.json(resp);
        String p = req.getPathInfo() != null ? req.getPathInfo() : "";
        try {
            // Groups: Admin + Formateur
            if (p.matches("/groups/\\d+")) {
                if (!checkRole(req, resp, "ADMIN", "FORMATEUR")) return;
                long gid = Long.parseLong(p.split("/")[2]);
                JsonObject body = JsonHelper.readBody(req);
                userDAO.updateGroup(gid, JsonHelper.str(body, "name"), JsonHelper.str(body, "description"));
                JsonHelper.ok(resp, JsonHelper.success()); return;
            }
            // Admin only
            if (!checkRole(req, resp, "ADMIN")) return;
            if ("/admin/settings".equals(p)) { updateSettings(req, resp); return; }
            if (p.startsWith("/admin/users/")) { updateUser(req, resp, p); return; }
            if (p.startsWith("/admin/groups/")) { updateGroup(req, resp, p); return; }
            if (p.startsWith("/admin/courses/") && p.endsWith("/status")) { updateCourseStatus(req, resp, p); return; }
            // Plugin management
            if (p.startsWith("/admin/plugins/") && p.endsWith("/status")) { updatePluginStatus(req, resp, p); return; }
            if (p.startsWith("/admin/plugins/") && p.endsWith("/config")) { updatePluginConfig(req, resp, p); return; }
            if (p.startsWith("/admin/plugins/") && !p.endsWith("/status") && !p.endsWith("/config")) { updatePluginMeta(req, resp, p); return; }
            JsonHelper.err(resp, 404, "Not found");
        } catch (Exception e) { JsonHelper.err(resp, 500, e.getMessage()); }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonHelper.json(resp);
        String p = req.getPathInfo() != null ? req.getPathInfo() : "";
        try {
            // Groups: Admin + Formateur
            if (p.matches("/groups/\\d+")) {
                if (!checkRole(req, resp, "ADMIN", "FORMATEUR")) return;
                long gid = Long.parseLong(p.split("/")[2]);
                JsonHelper.ok(resp, wrap(userDAO.deleteGroup(gid))); return;
            }
            // Admin only
            if (!checkRole(req, resp, "ADMIN")) return;
            if (p.startsWith("/admin/users/")) { JsonHelper.ok(resp, wrap(userDAO.deleteUser(longParam(p, "/admin/users/")))); return; }
            if (p.startsWith("/admin/groups/")) { JsonHelper.ok(resp, wrap(userDAO.deleteGroup(longParam(p, "/admin/groups/")))); return; }
            if (p.startsWith("/admin/courses/")) { JsonHelper.ok(resp, wrap(courseDAO.deleteCourse(longParam(p, "/admin/courses/")))); return; }
            if (p.startsWith("/admin/results/")) { JsonHelper.ok(resp, wrap(quizDAO.delete(longParam(p, "/admin/results/")))); return; }
            if (p.startsWith("/admin/plugins/")) { deletePlugin(req, resp, p); return; }
            JsonHelper.err(resp, 404, "Not found");
        } catch (Exception e) { JsonHelper.err(resp, 500, e.getMessage()); }
    }

    // ═══ Auth Handlers ═══

    private void doLogin(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        String username = JsonHelper.str(body, "username");
        String password = JsonHelper.str(body, "password");

        User user = userDAO.findByUsername(username);
        if (user == null) {
            auditDAO.log(0, username, "LOGIN_FAILED", "Utilisateur inconnu", req.getRemoteAddr(), "");
            JsonHelper.err(resp, 401, "Identifiants incorrects"); return;
        }
        if (user.getPasswordHash().startsWith("OAUTH:")) {
            String provider = user.getPasswordHash().replace("OAUTH:", "");
            JsonHelper.err(resp, 401, "Ce compte utilise la connexion " + provider + ". Utilisez le bouton correspondant."); return;
        }
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            auditDAO.log(0, username, "LOGIN_FAILED", "Tentative echouee", req.getRemoteAddr(), "");
            JsonHelper.err(resp, 401, "Identifiants incorrects"); return;
        }
        if (!user.isActive()) {
            JsonHelper.err(resp, 403, "Votre compte est en attente d'activation par un administrateur."); return;
        }

        HttpSession session = req.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole().name());
        session.setAttribute("displayName", user.displayName());
        session.setMaxInactiveInterval(7200);
        userDAO.updateLastLogin(user.getId());
        auditDAO.log(user.getId(), user.displayName(), "LOGIN", "Connexion reussie", req.getRemoteAddr(), session.getId());

        JsonObject r = new JsonObject();
        r.addProperty("success", true);
        r.addProperty("userId", user.getId());
        r.addProperty("username", user.getUsername());
        r.addProperty("nom", user.getNom());
        r.addProperty("prenom", user.getPrenom());
        r.addProperty("role", user.getRole().name());
        r.addProperty("displayName", user.displayName());
        r.addProperty("mustChangePassword", user.isMustChangePassword());
        JsonHelper.ok(resp, r);
    }

    private void doLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s != null) {
            auditDAO.log(userId(req), (String) s.getAttribute("displayName"), "LOGOUT", "", req.getRemoteAddr(), s.getId());
            s.invalidate();
        }
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void doChangePassword(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        long uid = userId(req);
        String current = JsonHelper.str(body, "currentPassword");
        String newPwd = JsonHelper.str(body, "newPassword");
        if (newPwd.length() < 6) { JsonHelper.err(resp, 400, "Mot de passe trop court (min 6)"); return; }
        User user = userDAO.findById(uid);
        if (!PasswordUtil.verify(current, user.getPasswordHash())) { JsonHelper.err(resp, 401, "Mot de passe actuel incorrect"); return; }
        userDAO.updatePassword(uid, PasswordUtil.hash(newPwd));
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void getSession(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        HttpSession s = req.getSession(false);
        JsonObject j = new JsonObject();
        if (s != null && s.getAttribute("userId") != null) {
            j.addProperty("authenticated", true);
            long uid = (long) s.getAttribute("userId");
            j.addProperty("userId", uid);
            j.addProperty("username", (String) s.getAttribute("username"));
            j.addProperty("role", (String) s.getAttribute("role"));
            j.addProperty("displayName", (String) s.getAttribute("displayName"));
            // Load photo and bio from DB
            try {
                User u = userDAO.findById(uid);
                if (u != null) {
                    j.addProperty("photoUrl", u.getPhotoUrl() != null ? u.getPhotoUrl() : "");
                    j.addProperty("bio", u.getBio() != null ? u.getBio() : "");
                }
            } catch (Exception ignored) {}
        } else { j.addProperty("authenticated", false); }
        JsonHelper.ok(resp, j);
    }

    private void getPublicSettings(HttpServletResponse resp) throws Exception {
        Map<String, String> all = settingDAO.getAll();
        JsonObject j = new JsonObject();
        j.addProperty("platformName", all.getOrDefault("platform_name", "LMS Platform"));
        j.addProperty("primaryColor", all.getOrDefault("primary_color", "#4285f4"));
        j.addProperty("logoUrl", all.getOrDefault("logo_url", ""));
        // Registration & OAuth availability
        j.addProperty("registrationEnabled", OAuthService.isRegistrationEnabled());
        j.addProperty("googleOAuthEnabled", OAuthService.isGoogleEnabled());
        j.addProperty("microsoftOAuthEnabled", OAuthService.isMicrosoftEnabled());
        // Allowed roles for self-registration
        j.addProperty("registrationRoles", AppConfig.get("lms.registration.roles", "ETUDIANT,FORMATEUR"));
        JsonHelper.ok(resp, j);
    }

    /** Public course catalog — published courses only, no auth required */
    private void getPublicCatalog(HttpServletResponse resp, Course.ContentType type) throws Exception {
        List<Course> all = courseDAO.findAllByType(type);
        JsonArray arr = new JsonArray();
        for (Course c : all) {
            if (c.getStatus() != Course.Status.PUBLISHED) continue;
            JsonObject co = new JsonObject();
            co.addProperty("id", c.getId());
            co.addProperty("title", c.getTitle());
            co.addProperty("description", c.getDescription());
            co.addProperty("category", c.getCategory());
            co.addProperty("level", c.getLevel());
            co.addProperty("duration", c.getDuration());
            co.addProperty("tags", c.getTags());
            co.addProperty("authorName", c.getAuthorName());
            co.addProperty("quizEnabled", c.isQuizEnabled());
            co.addProperty("questionsCount", c.getQuestionsCount());
            co.addProperty("contentType", c.getContentType().name());
            co.addProperty("version", c.getVersion());
            arr.add(co);
        }
        JsonHelper.ok(resp, arr);
    }

    /** Full-text search across all published content */
    private void doSearch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String q = req.getParameter("q");
        if (q == null || q.trim().length() < 2) { JsonHelper.err(resp, 400, "Recherche: min 2 caracteres"); return; }
        String type = req.getParameter("type"); // FORMATION, DOCUMENT, or null for all
        List<Course> results = courseDAO.search(q.trim(), type);
        JsonHelper.ok(resp, results);
    }

    /** Public company info from settings */
    private void getCompanyInfo(HttpServletResponse resp) throws Exception {
        Map<String, String> all = settingDAO.getAll();
        JsonObject j = new JsonObject();
        j.addProperty("platformName", all.getOrDefault("platform_name", "LMS Platform"));
        j.addProperty("primaryColor", all.getOrDefault("primary_color", "#4285f4"));
        j.addProperty("logoUrl", all.getOrDefault("logo_url", ""));
        j.addProperty("companyDescription", all.getOrDefault("company_description",
                "Plateforme de formation professionnelle en ligne. Accédez à des cours interactifs, passez des QCMs de validation et suivez votre progression."));
        j.addProperty("companyMission", all.getOrDefault("company_mission",
                "Démocratiser l'accès à la formation technique de qualité grâce à une plateforme moderne, légère et auto-hébergée."));
        j.addProperty("companyContact", all.getOrDefault("company_contact", ""));
        j.addProperty("registrationEnabled", OAuthService.isRegistrationEnabled());
        j.addProperty("googleOAuthEnabled", OAuthService.isGoogleEnabled());
        j.addProperty("microsoftOAuthEnabled", OAuthService.isMicrosoftEnabled());
        JsonHelper.ok(resp, j);
    }

    // ═══ Self-Registration ═══

    private void doRegister(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!OAuthService.isRegistrationEnabled()) {
            JsonHelper.err(resp, 403, "L'inscription en ligne est desactivee"); return;
        }
        JsonObject body = JsonHelper.readBody(req);
        String nom = JsonHelper.str(body, "nom");
        String prenom = JsonHelper.str(body, "prenom");
        String email = JsonHelper.str(body, "email");
        String username = JsonHelper.str(body, "username");
        String password = JsonHelper.str(body, "password");
        String roleStr = JsonHelper.str(body, "role").toUpperCase();

        if (nom.isEmpty() || prenom.isEmpty() || username.isEmpty() || password.length() < 6) {
            JsonHelper.err(resp, 400, "Tous les champs sont requis (mot de passe min 6 caracteres)"); return;
        }
        // Validate role is allowed for registration
        String allowedRoles = AppConfig.get("lms.registration.roles", "ETUDIANT,FORMATEUR");
        if (!allowedRoles.toUpperCase().contains(roleStr) || "ADMIN".equals(roleStr)) {
            JsonHelper.err(resp, 400, "Role non autorise pour l'inscription"); return;
        }
        if (userDAO.findByUsername(username) != null) {
            JsonHelper.err(resp, 409, "Cet identifiant est deja utilise"); return;
        }
        if (!email.isEmpty() && userDAO.findByEmail(email) != null) {
            JsonHelper.err(resp, 409, "Cette adresse email est deja utilisee"); return;
        }

        User u = new User();
        u.setUsername(username); u.setNom(nom); u.setPrenom(prenom); u.setEmail(email);
        u.setPasswordHash(PasswordUtil.hash(password));
        u.setRole(User.Role.valueOf(roleStr));
        u.setActive(false); // ← Pending admin approval
        u.setMustChangePassword(false);
        long id = userDAO.createUser(u);

        auditDAO.log(id, prenom + " " + nom, "REGISTER",
                "Inscription en ligne (" + roleStr + ") — en attente d'activation",
                req.getRemoteAddr(), "");

        JsonObject r = JsonHelper.success();
        r.addProperty("id", id);
        r.addProperty("message", "Inscription enregistree. Votre compte sera active par un administrateur.");
        JsonHelper.ok(resp, r);
    }

    // ═══ OAuth2 ═══

    private void oauthRedirect(HttpServletRequest req, HttpServletResponse resp, String provider) throws IOException {
        String eCtx = effectiveContext(req);
        String baseUrl = req.getScheme() + "://" + req.getServerName() +
                (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());
        String callbackUrl = baseUrl + eCtx + "/api/auth/oauth/" + provider + "/callback";

        String role = req.getParameter("role");
        // Store role in session for the callback
        HttpSession session = req.getSession(true);
        session.setAttribute("oauth_role", role != null ? role : "ETUDIANT");

        String authUrl = switch (provider) {
            case "google" -> OAuthService.googleAuthUrl(callbackUrl);
            case "microsoft" -> OAuthService.microsoftAuthUrl(callbackUrl);
            default -> null;
        };
        if (authUrl == null) { JsonHelper.err(resp, 400, "Provider inconnu"); return; }
        resp.sendRedirect(authUrl);
    }

    private void oauthCallback(HttpServletRequest req, HttpServletResponse resp, String provider) throws Exception {
        String code = req.getParameter("code");
        String error = req.getParameter("error");
        String eCtx = effectiveContext(req);

        if (error != null || code == null) {
            resp.sendRedirect(eCtx + "/login.html?error=oauth_denied");
            return;
        }

        String baseUrl = req.getScheme() + "://" + req.getServerName() +
                (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());
        String callbackUrl = baseUrl + eCtx + "/api/auth/oauth/" + provider + "/callback";

        OAuthService.OAuthUserInfo info = switch (provider) {
            case "google" -> OAuthService.googleCallback(code, callbackUrl);
            case "microsoft" -> OAuthService.microsoftCallback(code, callbackUrl);
            default -> null;
        };
        if (info == null || info.email().isEmpty()) {
            resp.sendRedirect(eCtx + "/login.html?error=oauth_failed");
            return;
        }

        // Check if user already exists by email
        User user = userDAO.findByEmail(info.email());
        if (user == null) {
            // Create new account (pending activation)
            HttpSession oauthSession = req.getSession(false);
            String roleStr = (oauthSession != null && oauthSession.getAttribute("oauth_role") != null)
                    ? (String) oauthSession.getAttribute("oauth_role") : "ETUDIANT";
            User.Role role;
            try { role = User.Role.valueOf(roleStr.toUpperCase()); } catch (Exception e) { role = User.Role.ETUDIANT; }
            if (role == User.Role.ADMIN) role = User.Role.ETUDIANT;

            long id = userDAO.createOAuthUser(info.email(), info.nom(), info.prenom(), role, provider, false);
            auditDAO.log(id, info.prenom() + " " + info.nom(), "OAUTH_REGISTER",
                    "Inscription via " + provider + " (" + roleStr + ") — en attente",
                    req.getRemoteAddr(), "");
            resp.sendRedirect(eCtx + "/login.html?registered=pending&provider=" + provider);
            return;
        }

        // User exists
        if (!user.isActive()) {
            resp.sendRedirect(eCtx + "/login.html?error=account_pending");
            return;
        }

        // Log in the user
        HttpSession session = req.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole().name());
        session.setAttribute("displayName", user.displayName());
        session.setMaxInactiveInterval(7200);
        userDAO.updateLastLogin(user.getId());
        auditDAO.log(user.getId(), user.displayName(), "OAUTH_LOGIN",
                "Connexion via " + provider, req.getRemoteAddr(), session.getId());

        resp.sendRedirect(eCtx + "/app.html");
    }

    private String effectiveContext(HttpServletRequest req) {
        String proxy = AppConfig.get("lms.proxy.context", "").trim();
        if (!proxy.isEmpty()) return "/".equals(proxy) ? "" : proxy;
        return req.getContextPath();
    }

    // ═══ Approve / Reject ═══

    private void approveUser(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        long userId = JsonHelper.longVal(body, "userId", 0);
        boolean activated = userDAO.activateUser(userId);
        if (activated) {
            User u = userDAO.findById(userId);
            auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                    "APPROVE_USER", "Activation de " + (u != null ? u.displayName() : "ID:" + userId),
                    req.getRemoteAddr(), req.getSession().getId());
        }
        JsonHelper.ok(resp, wrap(activated));
    }

    private void rejectUser(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        long userId = JsonHelper.longVal(body, "userId", 0);
        User u = userDAO.findById(userId);
        boolean deleted = userDAO.deleteUser(userId);
        if (deleted) {
            auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                    "REJECT_USER", "Rejet de " + (u != null ? u.displayName() : "ID:" + userId),
                    req.getRemoteAddr(), req.getSession().getId());
        }
        JsonHelper.ok(resp, wrap(deleted));
    }

    // ═══ Profile (Self-service) ═══

    private void getProfile(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        long uid = userId(req);
        User u = userDAO.findById(uid);
        if (u == null) { JsonHelper.err(resp, 404, "Utilisateur introuvable"); return; }
        JsonObject j = new JsonObject();
        j.addProperty("id", u.getId());
        j.addProperty("username", u.getUsername());
        j.addProperty("nom", u.getNom());
        j.addProperty("prenom", u.getPrenom());
        j.addProperty("email", u.getEmail());
        j.addProperty("role", u.getRole().name());
        j.addProperty("photoUrl", u.getPhotoUrl() != null ? u.getPhotoUrl() : "");
        j.addProperty("bio", u.getBio() != null ? u.getBio() : "");
        j.addProperty("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
        j.addProperty("lastLogin", u.getLastLogin() != null ? u.getLastLogin().toString() : "");
        JsonHelper.ok(resp, j);
    }

    private void updateProfile(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        long uid = userId(req);
        JsonObject body = JsonHelper.readBody(req);
        String newUsername = JsonHelper.str(body, "username").trim();
        String email = JsonHelper.str(body, "email").trim();
        String bio = JsonHelper.str(body, "bio").trim();

        if (newUsername.isEmpty()) { JsonHelper.err(resp, 400, "Le nom d'utilisateur est obligatoire"); return; }
        if (newUsername.length() < 3) { JsonHelper.err(resp, 400, "Le nom d'utilisateur doit faire au moins 3 caractères"); return; }

        // Check bio word count (max 50 words)
        if (!bio.isEmpty()) {
            int wordCount = bio.split("\\s+").length;
            if (wordCount > 50) { JsonHelper.err(resp, 400, "La description est limitée à 50 mots (actuellement " + wordCount + ")"); return; }
        }

        // Check username uniqueness (if changed)
        User current = userDAO.findById(uid);
        if (!current.getUsername().equals(newUsername)) {
            User existing = userDAO.findByUsername(newUsername);
            if (existing != null) { JsonHelper.err(resp, 409, "Ce nom d'utilisateur est déjà pris"); return; }
        }

        userDAO.updateProfile(uid, newUsername, email, bio);

        // Update session
        req.getSession().setAttribute("username", newUsername);
        req.getSession().setAttribute("displayName", current.getPrenom() + " " + current.getNom());

        auditDAO.log(uid, (String) req.getSession().getAttribute("displayName"),
                "PROFILE_UPDATE", "Username: " + newUsername, req.getRemoteAddr(), req.getSession().getId());

        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void changeOwnPassword(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        long uid = userId(req);
        JsonObject body = JsonHelper.readBody(req);
        String currentPwd = JsonHelper.str(body, "currentPassword");
        String newPwd = JsonHelper.str(body, "newPassword");
        String confirmPwd = JsonHelper.str(body, "confirmPassword");

        if (currentPwd.isEmpty() || newPwd.isEmpty()) { JsonHelper.err(resp, 400, "Tous les champs sont obligatoires"); return; }
        if (!newPwd.equals(confirmPwd)) { JsonHelper.err(resp, 400, "Les mots de passe ne correspondent pas"); return; }
        if (newPwd.length() < 4) { JsonHelper.err(resp, 400, "Le mot de passe doit faire au moins 4 caractères"); return; }

        User u = userDAO.findById(uid);
        // Verify current password (skip for OAuth users)
        if (!u.getPasswordHash().startsWith("OAUTH:")) {
            if (!PasswordUtil.verify(currentPwd, u.getPasswordHash())) {
                JsonHelper.err(resp, 403, "Mot de passe actuel incorrect"); return;
            }
        }

        userDAO.updatePassword(uid, PasswordUtil.hash(newPwd));

        auditDAO.log(uid, (String) req.getSession().getAttribute("displayName"),
                "PASSWORD_CHANGE", "Self-service", req.getRemoteAddr(), req.getSession().getId());

        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void uploadProfilePhoto(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        long uid = userId(req);
        var part = req.getPart("photo");
        if (part == null || part.getSize() == 0) { JsonHelper.err(resp, 400, "Fichier photo manquant"); return; }

        // Validate file type
        String contentType = part.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            JsonHelper.err(resp, 400, "Le fichier doit être une image (JPEG, PNG, GIF)"); return;
        }

        // Limit size (500KB)
        if (part.getSize() > 500_000) {
            JsonHelper.err(resp, 400, "L'image ne doit pas dépasser 500 Ko"); return;
        }

        // Read and convert to base64 data URI
        byte[] bytes = part.getInputStream().readAllBytes();
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
        String dataUri = "data:" + contentType + ";base64," + base64;

        userDAO.updatePhoto(uid, dataUri);

        auditDAO.log(uid, (String) req.getSession().getAttribute("displayName"),
                "PHOTO_UPLOAD", "Size: " + part.getSize() + " bytes", req.getRemoteAddr(), req.getSession().getId());

        JsonObject j = JsonHelper.success();
        j.addProperty("photoUrl", dataUri);
        JsonHelper.ok(resp, j);
    }

    // ═══ Plugin Management ═══

    private void uploadPlugin(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        var part = req.getPart("plugin");
        if (part == null || part.getSize() == 0) { JsonHelper.err(resp, 400, "Fichier plugin manquant"); return; }

        // Extract to temp
        Path tmpDir = Files.createTempDirectory("plugin-upload-");
        Path zipFile = tmpDir.resolve("plugin.zip");
        try (InputStream is = part.getInputStream()) { Files.copy(is, zipFile, StandardCopyOption.REPLACE_EXISTING); }

        // Extract ZIP
        Path extractDir = tmpDir.resolve("extracted");
        Files.createDirectories(extractDir);
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) { Files.createDirectories(extractDir.resolve(entry.getName())); continue; }
                Path target = extractDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(extractDir)) continue; // security
                Files.createDirectories(target.getParent());
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Read plugin.xml
        Path descriptorFile = extractDir.resolve("plugin.xml");
        if (!Files.exists(descriptorFile)) { JsonHelper.err(resp, 400, "plugin.xml manquant dans le ZIP"); return; }

        var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptorFile.toFile());
        var root = doc.getDocumentElement();
        var meta = root.getElementsByTagName("metadata").item(0);

        String pluginId = xmlText(meta, "id");
        if (pluginId == null || pluginId.isBlank()) { JsonHelper.err(resp, 400, "id manquant dans plugin.xml"); return; }

        Plugin p = new Plugin();
        p.setPluginId(pluginId);
        p.setName(xmlText(meta, "name") != null ? xmlText(meta, "name") : pluginId);
        p.setDescription(xmlText(meta, "description") != null ? xmlText(meta, "description") : "");
        p.setIcon(xmlText(meta, "icon") != null ? xmlText(meta, "icon") : "🧩");
        p.setVersion(xmlText(meta, "version") != null ? xmlText(meta, "version") : "1.0.0");
        p.setAuthor(xmlText(meta, "author") != null ? xmlText(meta, "author") : "");
        p.setRoles(xmlText(meta, "roles") != null ? xmlText(meta, "roles") : "*");
        p.setCategory(xmlText(meta, "category") != null ? xmlText(meta, "category") : "Modules");
        p.setSortOrder(xmlText(meta, "sort-order") != null ? Integer.parseInt(xmlText(meta, "sort-order")) : 100);

        var contentEl = root.getElementsByTagName("content").item(0);
        p.setEntryPoint(xmlText(contentEl, "entry-point") != null ? xmlText(contentEl, "entry-point") : "index.html");
        p.setStatus(Plugin.Status.ACTIVE);
        p.setConfigJson("{}");

        // Deploy files to {dataDir}/plugins/{pluginId}/
        String dataDir = AppConfig.get("lms.data.dir", System.getProperty("user.home") + "/lms-data");
        Path targetDir = Path.of(dataDir, "plugins", pluginId);
        if (Files.exists(targetDir)) {
            // Delete existing files
            try (var walk = Files.walk(targetDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
            }
        }
        Files.createDirectories(targetDir);

        // Copy extracted files
        try (var walk = Files.walk(extractDir)) {
            walk.forEach(source -> {
                try {
                    Path dest = targetDir.resolve(extractDir.relativize(source));
                    if (Files.isDirectory(source)) Files.createDirectories(dest);
                    else Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            });
        }

        // Save or update in DB
        Plugin existing = pluginDAO.findByPluginId(pluginId);
        long id;
        if (existing != null) {
            p.setId(existing.getId());
            p.setConfigJson(existing.getConfigJson()); // preserve config
            pluginDAO.update(p);
            id = existing.getId();
        } else {
            id = pluginDAO.install(p);
        }

        // Cleanup temp
        try (var walk = Files.walk(tmpDir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
        }

        auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                "PLUGIN_INSTALL", pluginId + " v" + p.getVersion(), req.getRemoteAddr(), req.getSession().getId());

        JsonObject j = JsonHelper.success();
        j.addProperty("id", id);
        j.addProperty("pluginId", pluginId);
        j.addProperty("name", p.getName());
        JsonHelper.ok(resp, j);
    }

    private void updatePluginStatus(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        // /admin/plugins/{id}/status
        String idStr = p.replace("/admin/plugins/", "").replace("/status", "");
        long id = Long.parseLong(idStr);
        JsonObject body = JsonHelper.readBody(req);
        String status = JsonHelper.str(body, "status").toUpperCase();
        pluginDAO.setStatus(id, Plugin.Status.valueOf(status));
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void updatePluginConfig(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        // /admin/plugins/{pluginId}/config — accepts pluginId string
        String pluginId = p.replace("/admin/plugins/", "").replace("/config", "");
        Plugin plugin = pluginDAO.findByPluginId(pluginId);
        if (plugin == null) {
            try { plugin = pluginDAO.findById(Long.parseLong(pluginId)); } catch (Exception ignored) {}
        }
        if (plugin == null) { JsonHelper.err(resp, 404, "Plugin introuvable"); return; }
        JsonObject body = JsonHelper.readBody(req);
        plugin.setConfigJson(JsonHelper.str(body, "config"));
        pluginDAO.update(plugin);
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void updatePluginMeta(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        long id = longParam(p, "/admin/plugins/");
        Plugin plugin = pluginDAO.findById(id);
        if (plugin == null) { JsonHelper.err(resp, 404, "Plugin introuvable"); return; }
        JsonObject body = JsonHelper.readBody(req);
        if (body.has("name")) plugin.setName(JsonHelper.str(body, "name"));
        if (body.has("icon")) plugin.setIcon(JsonHelper.str(body, "icon"));
        if (body.has("roles")) plugin.setRoles(JsonHelper.str(body, "roles"));
        if (body.has("category")) plugin.setCategory(JsonHelper.str(body, "category"));
        if (body.has("sortOrder")) plugin.setSortOrder(body.get("sortOrder").getAsInt());
        if (body.has("description")) plugin.setDescription(JsonHelper.str(body, "description"));
        pluginDAO.update(plugin);
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void deletePlugin(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        long id = longParam(p, "/admin/plugins/");
        Plugin plugin = pluginDAO.findById(id);
        if (plugin != null) {
            // Delete files
            String dataDir = AppConfig.get("lms.data.dir", System.getProperty("user.home") + "/lms-data");
            Path pluginDir = Path.of(dataDir, "plugins", plugin.getPluginId());
            if (Files.exists(pluginDir)) {
                try (var walk = Files.walk(pluginDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
                }
            }
            pluginDAO.delete(id);
            auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                    "PLUGIN_UNINSTALL", plugin.getPluginId(), req.getRemoteAddr(), req.getSession().getId());
        }
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private String xmlText(org.w3c.dom.Node parent, String tag) {
        if (parent == null) return null;
        var nodes = ((org.w3c.dom.Element) parent).getElementsByTagName(tag);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }

    // ═══ Settings Debug ═══

    private void debugSettings(HttpServletResponse resp) throws Exception {
        JsonObject j = new JsonObject();

        // 1. Raw DB content
        JsonObject raw = new JsonObject();
        try (var c = DatabaseManager.getInstance().getConnection();
             var ps = c.prepareStatement("SELECT setting_key, LENGTH(setting_value) as val_len, SUBSTRING(setting_value, 1, 50) as val_preview FROM settings ORDER BY setting_key");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("length", rs.getInt("val_len"));
                entry.addProperty("preview", rs.getString("val_preview"));
                raw.add(rs.getString("setting_key"), entry);
            }
        }
        j.add("rawDb", raw);

        // 2. AI config resolution
        JsonObject ai = new JsonObject();
        String dbKey = settingDAO.get("ai_api_key");
        String fileKey = AppConfig.get("lms.ai.api-key", "");
        ai.addProperty("db_ai_api_key", dbKey != null ? dbKey.substring(0, Math.min(8, dbKey.length())) + "***" : "NULL");
        ai.addProperty("db_ai_api_key_length", dbKey != null ? dbKey.length() : 0);
        ai.addProperty("file_ai_api_key", !fileKey.isBlank() ? fileKey.substring(0, Math.min(8, fileKey.length())) + "***" : "EMPTY");
        ai.addProperty("effective_key_source", (dbKey != null && !dbKey.isBlank()) ? "DB" : (!fileKey.isBlank() ? "FILE" : "NONE"));
        ai.addProperty("ai_enabled", aiService.isEnabled());
        ai.addProperty("ai_model_db", settingDAO.get("ai_model"));
        ai.addProperty("ai_model_effective", aiService.getModel());
        ai.addProperty("system_prompt_db_length", settingDAO.get("ai_system_prompt") != null ? settingDAO.get("ai_system_prompt").length() : 0);
        j.add("aiConfig", ai);

        // 3. Write/Read test
        JsonObject test = new JsonObject();
        try {
            String testKey = "_diag_test_" + System.currentTimeMillis();
            settingDAO.set(testKey, "hello_diag");
            String readBack = settingDAO.get(testKey);
            test.addProperty("writeRead", "hello_diag".equals(readBack) ? "OK" : "MISMATCH: " + readBack);
            // Cleanup
            try (var c = DatabaseManager.getInstance().getConnection();
                 var ps = c.prepareStatement("DELETE FROM settings WHERE setting_key=?")) {
                ps.setString(1, testKey); ps.executeUpdate();
            }
        } catch (Exception e) {
            test.addProperty("writeRead", "ERROR: " + e.getMessage());
        }
        j.add("dbTest", test);

        // 4. Table row count
        try (var c = DatabaseManager.getInstance().getConnection();
             var ps = c.prepareStatement("SELECT COUNT(*) FROM settings");
             var rs = ps.executeQuery()) {
            rs.next();
            j.addProperty("totalSettings", rs.getInt(1));
        }

        // 5. H2 DB path
        j.addProperty("dataDir", AppConfig.get("lms.data.dir", "(default)"));
        j.addProperty("dbUrl", DatabaseManager.getInstance().getConnection().getMetaData().getURL());

        JsonHelper.ok(resp, j);
    }

    // ═══ AI Async Job Submit ═══

    private void doAiSubmitJob(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!aiService.isEnabled()) {
            JsonHelper.err(resp, 503, "Agent IA non configure"); return;
        }
        JsonObject body = JsonHelper.readBody(req);
        String type = JsonHelper.str(body, "type"); // "plan" or "course"
        String prompt = JsonHelper.str(body, "prompt");

        if (prompt.isEmpty()) { JsonHelper.err(resp, 400, "Prompt manquant"); return; }

        String jobId = AiJobManager.getInstance().submitJob(type, prompt, aiService);

        auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                "AI_JOB_SUBMIT", type + " job: " + jobId,
                req.getRemoteAddr(), req.getSession().getId());

        JsonObject j = new JsonObject();
        j.addProperty("jobId", jobId);
        j.addProperty("status", "PENDING");
        JsonHelper.ok(resp, j);
    }

    // ═══ AI Streaming (SSE) ═══

    private void doAiStream(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!aiService.isEnabled()) {
            resp.sendError(503, "Agent IA non configure");
            return;
        }

        String prompt = req.getParameter("prompt");
        if (prompt == null || prompt.isBlank()) {
            resp.sendError(400, "Parametre prompt manquant");
            return;
        }

        // SSE headers
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("X-Accel-Buffering", "no"); // Disable nginx/proxy buffering
        resp.flushBuffer();

        var writer = resp.getWriter();

        aiService.generateStream(prompt, new AiService.StreamCallback() {
            @Override
            public void onDelta(String text) throws Exception {
                // Escape for SSE data line (newlines become separate data: lines)
                String escaped = text.replace("\n", "\ndata: ");
                writer.write("data: " + escaped + "\n\n");
                writer.flush();
            }

            @Override
            public void onComplete(String usage) throws Exception {
                JsonObject j = new JsonObject();
                j.addProperty("type", "done");
                j.addProperty("usage", usage);
                writer.write("event: done\ndata: " + j.toString() + "\n\n");
                writer.flush();
                writer.close();
            }

            @Override
            public void onError(String error) throws Exception {
                JsonObject j = new JsonObject();
                j.addProperty("type", "error");
                j.addProperty("message", error);
                writer.write("event: error\ndata: " + j.toString() + "\n\n");
                writer.flush();
                writer.close();
            }
        });
    }

    // ═══ Backup / Restore ═══

    private final BackupService backupService = new BackupService();
    private final SearchService searchService = new SearchService();
    private final AiService aiService = new AiService();

    private void doBackupExport(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        byte[] xml = backupService.exportXml();
        String filename = "lms-backup-" + java.time.LocalDate.now() + ".xml";
        resp.setContentType("application/xml;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        resp.setContentLength(xml.length);
        resp.getOutputStream().write(xml);

        auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                "BACKUP_EXPORT", "Taille: " + xml.length + " octets",
                req.getRemoteAddr(), req.getSession().getId());
    }

    private void doBackupRestore(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        var part = req.getPart("file");
        if (part == null) { JsonHelper.err(resp, 400, "Fichier XML manquant"); return; }

        // Safety: auto-backup current state before restoring
        String autoBackupPath = null;
        try {
            byte[] autoBackup = backupService.exportXml();
            String dataDir = (String) getServletContext().getAttribute("dataDir");
            if (dataDir != null) {
                String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                java.io.File backupDir = new java.io.File(dataDir, "backups");
                backupDir.mkdirs();
                java.io.File autoFile = new java.io.File(backupDir, "pre-restore-" + ts + ".xml");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(autoFile)) {
                    fos.write(autoBackup);
                }
                autoBackupPath = autoFile.getAbsolutePath();
                System.out.println("[LMS-Backup] Auto-backup saved: " + autoBackupPath + " (" + autoBackup.length + " bytes)");
            }
        } catch (Exception e) {
            System.err.println("[LMS-Backup] WARN: auto-backup failed: " + e.getMessage());
        }

        BackupService.RestoreResult result = backupService.restoreXml(part.getInputStream());

        auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                "BACKUP_RESTORE", "Schema " + result.schemaVersion + ", " + result.totalRows + " rows, " +
                        result.warnings.size() + " warnings",
                req.getRemoteAddr(), req.getSession().getId());

        JsonObject j = new JsonObject();
        j.addProperty("success", result.success);
        j.addProperty("schemaVersion", result.schemaVersion);
        j.addProperty("exportedAt", result.exportedAt);
        j.addProperty("totalRows", result.totalRows);
        if (autoBackupPath != null) j.addProperty("autoBackup", autoBackupPath);
        if (result.error != null) j.addProperty("error", result.error);

        JsonObject tables = new JsonObject();
        result.tableResults.forEach((table, counts) -> {
            JsonObject t = new JsonObject();
            t.addProperty("restored", counts[0]);
            t.addProperty("skipped", counts[1]);
            tables.add(table, t);
        });
        j.add("tables", tables);

        JsonArray warnings = new JsonArray();
        result.warnings.forEach(warnings::add);
        j.add("warnings", warnings);

        JsonHelper.ok(resp, j);
    }

    // ═══ Admin Handlers ═══

    private void createUser(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        User u = new User();
        u.setUsername(JsonHelper.str(body, "username"));
        u.setNom(JsonHelper.str(body, "nom"));
        u.setPrenom(JsonHelper.str(body, "prenom"));
        u.setEmail(JsonHelper.str(body, "email"));
        u.setRole(User.Role.valueOf(JsonHelper.str(body, "role").toUpperCase()));
        String pwd = JsonHelper.str(body, "password");
        if (pwd.isEmpty()) pwd = u.getUsername();
        u.setPasswordHash(PasswordUtil.hash(pwd));
        u.setActive(true);
        u.setMustChangePassword(true);
        if (userDAO.findByUsername(u.getUsername()) != null) { JsonHelper.err(resp, 409, "Username deja utilise"); return; }
        long id = userDAO.createUser(u);
        JsonObject r = JsonHelper.success(); r.addProperty("id", id);
        JsonHelper.ok(resp, r);
    }

    private void updateUser(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        long id = longParam(p, "/admin/users/");
        User u = userDAO.findById(id);
        if (u == null) { JsonHelper.err(resp, 404, "Utilisateur non trouve"); return; }
        JsonObject body = JsonHelper.readBody(req);
        if (body.has("username")) u.setUsername(JsonHelper.str(body, "username"));
        if (body.has("nom")) u.setNom(JsonHelper.str(body, "nom"));
        if (body.has("prenom")) u.setPrenom(JsonHelper.str(body, "prenom"));
        if (body.has("email")) u.setEmail(JsonHelper.str(body, "email"));
        if (body.has("role")) u.setRole(User.Role.valueOf(JsonHelper.str(body, "role").toUpperCase()));
        if (body.has("active")) u.setActive(JsonHelper.bool(body, "active"));
        if (body.has("password") && !JsonHelper.str(body, "password").isEmpty())
            userDAO.updatePassword(id, PasswordUtil.hash(JsonHelper.str(body, "password")));
        userDAO.updateUser(u);
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void createGroup(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        Group g = new Group(); g.setName(JsonHelper.str(body, "name")); g.setDescription(JsonHelper.str(body, "description"));
        long id = userDAO.createGroup(g);
        JsonObject r = JsonHelper.success(); r.addProperty("id", id);
        JsonHelper.ok(resp, r);
    }

    private void updateGroup(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        long id = longParam(p, "/admin/groups/");
        JsonObject body = JsonHelper.readBody(req);
        userDAO.updateGroup(id, JsonHelper.str(body, "name"), JsonHelper.str(body, "description"));
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void manageUserGroups(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        long userId = JsonHelper.longVal(body, "userId", 0);
        JsonArray groupIds = body.getAsJsonArray("groupIds");
        // Remove all then re-add
        for (long gid : userDAO.getUserGroupIds(userId)) userDAO.removeUserFromGroup(userId, gid);
        if (groupIds != null) for (var g : groupIds) userDAO.addUserToGroup(userId, g.getAsLong());
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void manageCourseGroups(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        long courseId = JsonHelper.longVal(body, "courseId", 0);
        JsonArray groupIds = body.getAsJsonArray("groupIds");
        for (long gid : courseDAO.getCourseGroupIds(courseId)) courseDAO.unassignGroup(courseId, gid);
        if (groupIds != null) for (var g : groupIds) courseDAO.assignGroup(courseId, g.getAsLong());
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void importCsv(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        String csv = JsonHelper.str(body, "csv");
        String role = JsonHelper.str(body, "role");
        if (role.isEmpty()) role = "ETUDIANT";
        int count = userDAO.importCsv(csv, User.Role.valueOf(role.toUpperCase()));
        JsonObject r = JsonHelper.success(); r.addProperty("imported", count);
        JsonHelper.ok(resp, r);
    }

    private void updateSettings(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        for (var entry : body.entrySet()) {
            String val = "";
            try {
                if (entry.getValue() != null && !entry.getValue().isJsonNull()) {
                    val = entry.getValue().getAsString();
                }
            } catch (Exception e) { val = entry.getValue().toString(); }
            settingDAO.set(entry.getKey(), val);
        }
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void updateCourseStatus(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        String idStr = p.replace("/admin/courses/", "").replace("/status", "");
        long id = Long.parseLong(idStr);
        Course co = courseDAO.findById(id);
        if (co == null) { JsonHelper.err(resp, 404, "Cours non trouve"); return; }
        JsonObject body = JsonHelper.readBody(req);
        String status = JsonHelper.str(body, "status").toUpperCase();
        co.setStatus(Course.Status.valueOf(status));
        if ("PUBLISHED".equals(status) && co.getPublishedAt() == null)
            co.setPublishedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        courseDAO.updateCourse(co);
        JsonHelper.ok(resp, JsonHelper.success());
    }

    private void rollbackCourse(HttpServletRequest req, HttpServletResponse resp, String p) throws Exception {
        long courseId = longParam(p, "/admin/course-rollback/");
        JsonObject body = JsonHelper.readBody(req);
        long backupId = JsonHelper.longVal(body, "backupId", 0);
        BundleService bs = (BundleService) getServletContext().getAttribute("bundleService");
        boolean ok = bs.rollback(courseId, backupId);
        if (ok) JsonHelper.ok(resp, JsonHelper.success());
        else JsonHelper.err(resp, 400, "Rollback echoue");
    }

    // ═══ Formateur ═══

    private void uploadBundle(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        var part = req.getPart("file");
        if (part == null) { JsonHelper.err(resp, 400, "Fichier ZIP manquant"); return; }

        // Content type: FORMATION (default) or DOCUMENT
        String ctParam = req.getParameter("content_type");
        if (ctParam == null) { var ctPart = req.getPart("content_type"); if (ctPart != null) ctParam = new String(ctPart.getInputStream().readAllBytes()).trim(); }
        Course.ContentType contentType = Course.ContentType.FORMATION;
        if ("DOCUMENT".equalsIgnoreCase(ctParam)) contentType = Course.ContentType.DOCUMENT;

        BundleService bs = (BundleService) getServletContext().getAttribute("bundleService");
        Course co = bs.deployBundle(part.getInputStream(), userId(req));

        // Set content type and update
        co.setContentType(contentType);
        courseDAO.updateCourse(co);

        // Index content for search
        searchService.indexCourse(co);

        String typeLabel = contentType == Course.ContentType.DOCUMENT ? "DOCUMENT" : "FORMATION";
        auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                "BUNDLE_UPLOAD", typeLabel + ": " + co.getTitle() + " v" + co.getVersion(),
                req.getRemoteAddr(), req.getSession().getId());
        JsonHelper.ok(resp, co);
    }

    // ═══ AI Course Generation ═══

    private void doAiGenerate(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!aiService.isEnabled()) {
            JsonHelper.err(resp, 503, "Agent IA non configure. Demandez a l'administrateur de saisir la cle API Anthropic dans Parametres."); return;
        }
        JsonObject body = JsonHelper.readBody(req);
        String title = JsonHelper.str(body, "title");
        String description = JsonHelper.str(body, "description");
        String category = JsonHelper.str(body, "category");
        String level = JsonHelper.str(body, "level");
        String duration = JsonHelper.str(body, "duration");
        String modules = JsonHelper.str(body, "modules");
        String instructions = JsonHelper.str(body, "instructions");
        boolean withQuiz = body.has("withQuiz") && body.get("withQuiz").getAsBoolean();
        int quizQuestions = JsonHelper.intVal(body, "quizQuestions", 20);

        // Build prompt from course specs
        StringBuilder prompt = new StringBuilder();
        prompt.append("Génère un support de cours HTML complet avec les spécifications suivantes :\n\n");
        prompt.append("TITRE : ").append(title).append("\n");
        if (!description.isEmpty()) prompt.append("DESCRIPTION : ").append(description).append("\n");
        if (!category.isEmpty()) prompt.append("CATÉGORIE : ").append(category).append("\n");
        if (!level.isEmpty()) prompt.append("NIVEAU : ").append(level).append("\n");
        if (!duration.isEmpty()) prompt.append("DURÉE : ").append(duration).append("\n");
        if (!modules.isEmpty()) prompt.append("MODULES / PLAN DU COURS :\n").append(modules).append("\n\n");
        if (!instructions.isEmpty()) prompt.append("INSTRUCTIONS SPÉCIFIQUES DU FORMATEUR :\n").append(instructions).append("\n\n");
        if (withQuiz) prompt.append("Inclure un lien vers quiz.html en fin de cours (").append(quizQuestions).append(" questions prévues).\n");
        prompt.append("\nGénère le fichier index.html complet.");

        AiService.AiResult result = aiService.generate(prompt.toString());

        JsonObject j = new JsonObject();
        j.addProperty("success", result.success());
        if (result.content() != null) j.addProperty("html", result.content());
        j.addProperty("message", result.message());
        JsonHelper.ok(resp, j);
    }

    private void doAiPublish(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        JsonObject body = JsonHelper.readBody(req);
        String bundleId = JsonHelper.str(body, "bundleId");
        String title = JsonHelper.str(body, "title");
        String description = JsonHelper.str(body, "description");
        String category = JsonHelper.str(body, "category");
        String level = JsonHelper.str(body, "level");
        String duration = JsonHelper.str(body, "duration");
        String version = JsonHelper.str(body, "version");
        if (version.isEmpty()) version = "1.0.0";
        String html = JsonHelper.str(body, "html");
        String contentType = JsonHelper.str(body, "contentType");
        if (contentType.isEmpty()) contentType = "FORMATION";
        boolean withQuiz = body.has("withQuiz") && body.get("withQuiz").getAsBoolean();
        int quizQuestions = JsonHelper.intVal(body, "quizQuestions", 0);
        int passThreshold = JsonHelper.intVal(body, "passThreshold", 70);
        int maxAttempts = JsonHelper.intVal(body, "maxAttempts", 3);

        if (bundleId.isEmpty() || title.isEmpty() || html.isEmpty()) {
            JsonHelper.err(resp, 400, "bundleId, title et html sont obligatoires"); return;
        }
        // Sanitize bundleId
        bundleId = bundleId.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-{2,}", "-");

        // Build bundle.xml
        String bundleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bundle version=\"1.0\">\n" +
                "  <metadata>\n" +
                "    <id>" + escXml(bundleId) + "</id>\n" +
                "    <title>" + escXml(title) + "</title>\n" +
                "    <description>" + escXml(description) + "</description>\n" +
                "    <version>" + escXml(version) + "</version>\n" +
                "    <category>" + escXml(category) + "</category>\n" +
                "    <duration>" + escXml(duration) + "</duration>\n" +
                "    <level>" + escXml(level) + "</level>\n" +
                "  </metadata>\n" +
                "  <content>\n" +
                "    <entry-point>index.html</entry-point>\n" +
                "    <quiz enabled=\"" + withQuiz + "\">\n" +
                "      <file>quiz.html</file>\n" +
                "      <questions-count>" + quizQuestions + "</questions-count>\n" +
                "      <pass-threshold>" + passThreshold + "</pass-threshold>\n" +
                "      <max-attempts>" + maxAttempts + "</max-attempts>\n" +
                "    </quiz>\n" +
                "  </content>\n" +
                "</bundle>\n";

        // Create ZIP in memory
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            // bundle.xml
            zos.putNextEntry(new java.util.zip.ZipEntry("bundle.xml"));
            zos.write(bundleXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            // index.html
            zos.putNextEntry(new java.util.zip.ZipEntry("index.html"));
            zos.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        // Deploy via BundleService
        BundleService bs = (BundleService) getServletContext().getAttribute("bundleService");
        java.io.ByteArrayInputStream zipIn = new java.io.ByteArrayInputStream(baos.toByteArray());
        Course co = bs.deployBundle(zipIn, userId(req));

        // Set content type
        co.setContentType("DOCUMENT".equals(contentType) ? Course.ContentType.DOCUMENT : Course.ContentType.FORMATION);
        courseDAO.updateCourse(co);

        // Index for search
        searchService.indexCourse(co);

        auditDAO.log(userId(req), (String) req.getSession().getAttribute("displayName"),
                "AI_PUBLISH", "Cours IA: " + co.getTitle() + " v" + co.getVersion(),
                req.getRemoteAddr(), req.getSession().getId());

        JsonObject j = JsonHelper.success();
        j.addProperty("id", co.getId());
        j.addProperty("bundleId", co.getBundleId());
        j.addProperty("title", co.getTitle());
        JsonHelper.ok(resp, j);
    }

    private String escXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ═══ Quiz ═══

    private void submitQuiz(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        long uid = userId(req);
        JsonObject body = JsonHelper.readBody(req);
        long courseId = JsonHelper.longVal(body, "courseId", 0);

        // Check max attempts
        if (courseId > 0) {
            Course co = courseDAO.findById(courseId);
            if (co != null && co.getMaxAttempts() > 0) {
                int used = quizDAO.getAttemptCount(uid, courseId);
                if (used >= co.getMaxAttempts()) {
                    JsonHelper.err(resp, 403, "Nombre maximum de tentatives atteint"); return;
                }
            }
        }

        QuizResult r = new QuizResult();
        r.setUserId(uid); r.setCourseId(courseId);
        r.setScore(JsonHelper.intVal(body, "score", 0));
        r.setTotalQuestions(JsonHelper.intVal(body, "totalQuestions", 0));
        r.setCorrectAnswers(JsonHelper.intVal(body, "correctAnswers", 0));
        r.setWrongAnswers(JsonHelper.intVal(body, "wrongAnswers", 0));
        r.setSkipped(JsonHelper.intVal(body, "skipped", 0));
        r.setModuleScores(body.has("moduleScores") ? body.get("moduleScores").toString() : "{}");
        r.setDetailedAnswers(body.has("detailedAnswers") ? body.get("detailedAnswers").toString() : "[]");
        r.setDurationSeconds(JsonHelper.intVal(body, "durationSeconds", 0));

        long id = quizDAO.save(r);

        auditDAO.log(uid, (String) req.getSession().getAttribute("displayName"),
                "QUIZ_SUBMIT", "Course " + courseId + " Score: " + r.getScore() + "% (" + r.getCorrectAnswers() + "/" + r.getTotalQuestions() + ")",
                req.getRemoteAddr(), req.getSession().getId());

        JsonObject j = JsonHelper.success(); j.addProperty("id", id);
        j.addProperty("attemptNumber", r.getAttemptNumber());
        JsonHelper.ok(resp, j);
    }

    // ═══ System Stats ═══

    private void getSystemStats(HttpServletResponse resp) throws Exception {
        Runtime rt = Runtime.getRuntime();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();

        JsonObject j = new JsonObject();
        // JVM Heap
        long heapUsed = mem.getHeapMemoryUsage().getUsed();
        long heapMax = mem.getHeapMemoryUsage().getMax();
        j.addProperty("heapUsedMB", heapUsed / 1_048_576);
        j.addProperty("heapMaxMB", heapMax / 1_048_576);
        j.addProperty("heapPct", Math.round(heapUsed * 100.0 / heapMax));

        // CPU
        j.addProperty("availableProcessors", rt.availableProcessors());
        j.addProperty("systemLoadAvg", Math.round(os.getSystemLoadAverage() * 100) / 100.0);

        // Disk
        String dataDir = (String) getServletContext().getAttribute("dataDir");
        File disk = new File(dataDir != null ? dataDir : ".");
        j.addProperty("diskTotalGB", Math.round(disk.getTotalSpace() / 1_073_741_824.0 * 10) / 10.0);
        j.addProperty("diskFreeGB", Math.round(disk.getFreeSpace() / 1_073_741_824.0 * 10) / 10.0);
        j.addProperty("diskUsedPct", Math.round((1.0 - (double) disk.getFreeSpace() / disk.getTotalSpace()) * 100));

        // Threads
        j.addProperty("threadCount", threads.getThreadCount());
        j.addProperty("peakThreadCount", threads.getPeakThreadCount());

        // JVM
        j.addProperty("javaVersion", System.getProperty("java.version"));
        j.addProperty("uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

        // Network (interface info)
        try {
            var nets = java.net.NetworkInterface.getNetworkInterfaces();
            JsonArray netArr = new JsonArray();
            while (nets.hasMoreElements()) {
                var ni = nets.nextElement();
                if (ni.isUp() && !ni.isLoopback()) {
                    JsonObject n = new JsonObject();
                    n.addProperty("name", ni.getDisplayName());
                    var addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        var a = addrs.nextElement();
                        if (a instanceof java.net.Inet4Address) n.addProperty("ipv4", a.getHostAddress());
                    }
                    netArr.add(n);
                }
            }
            j.add("network", netArr);
        } catch (Exception ignored) {}

        JsonHelper.ok(resp, j);
    }

    private void getAdminDashboard(HttpServletResponse resp) throws Exception {
        JsonObject j = new JsonObject();
        j.add("usersByRole", gson.toJsonTree(userDAO.countByRole()));
        j.addProperty("pendingCount", userDAO.countPending());
        j.addProperty("publishedCourses", courseDAO.countByStatus(Course.Status.PUBLISHED));
        j.addProperty("draftCourses", courseDAO.countByStatus(Course.Status.DRAFT));
        j.add("quizStats", gson.toJsonTree(quizDAO.globalStats()));
        j.add("courseStats", gson.toJsonTree(quizDAO.statsByCourse()));
        // Raw count (no JOINs, always shows real number)
        try (var c = DatabaseManager.getInstance().getConnection();
             var ps = c.prepareStatement("SELECT COUNT(*) FROM quiz_results");
             var rs = ps.executeQuery()) {
            rs.next();
            j.addProperty("rawQuizCount", rs.getInt(1));
        }
        JsonHelper.ok(resp, j);
    }

    // ═══ Helpers ═══

    private boolean checkRole(HttpServletRequest req, HttpServletResponse resp, String... roles) throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null) { JsonHelper.err(resp, 401, "Non authentifie"); return false; }
        String userRole = (String) s.getAttribute("role");
        for (String r : roles) if (r.equals(userRole)) return true;
        JsonHelper.err(resp, 403, "Acces refuse"); return false;
    }

    private long userId(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return s != null ? (long) s.getAttribute("userId") : 0;
    }

    private long longParam(String path, String prefix) {
        return Long.parseLong(path.substring(prefix.length()).replaceAll("/.*", ""));
    }

    private JsonObject wrap(boolean success) {
        JsonObject j = new JsonObject(); j.addProperty("success", success); return j;
    }
}

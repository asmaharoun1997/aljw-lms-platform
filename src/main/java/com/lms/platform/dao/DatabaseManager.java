package com.lms.platform.dao;

import com.lms.platform.util.AppConfig;
import com.lms.platform.util.PasswordUtil;
import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private static DatabaseManager instance;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public void init(String dataDir) {
        try {
            // DB config from properties
            String configUrl = AppConfig.get("lms.db.url", "");
            if (configUrl.isEmpty()) {
                dbUrl = "jdbc:h2:" + dataDir + "/db;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
            } else {
                dbUrl = configUrl;
            }
            dbUser = AppConfig.get("lms.db.user", "sa");
            dbPassword = AppConfig.get("lms.db.password", "");

            Class.forName("org.h2.Driver");
            System.out.println("[LMS] Connexion H2: " + dbUrl);

            try (Connection c = getConnection(); Statement s = c.createStatement()) {
                // ── Schema ──
                s.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(100) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        nom VARCHAR(100) NOT NULL,
                        prenom VARCHAR(100) NOT NULL,
                        email VARCHAR(200) DEFAULT '',
                        role VARCHAR(20) NOT NULL DEFAULT 'ETUDIANT',
                        active BOOLEAN DEFAULT TRUE,
                        must_change_password BOOLEAN DEFAULT FALSE,
                        photo_url VARCHAR(500) DEFAULT '',
                        bio VARCHAR(500) DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_login TIMESTAMP
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS groups (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL UNIQUE,
                        description VARCHAR(500) DEFAULT '',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS user_groups (
                        user_id BIGINT NOT NULL, group_id BIGINT NOT NULL,
                        PRIMARY KEY (user_id, group_id),
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS courses (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        bundle_id VARCHAR(100) NOT NULL UNIQUE,
                        title VARCHAR(300) NOT NULL,
                        description CLOB DEFAULT '',
                        category VARCHAR(100) DEFAULT '',
                        tags VARCHAR(500) DEFAULT '',
                        level VARCHAR(50) DEFAULT '',
                        duration VARCHAR(50) DEFAULT '',
                        author_id BIGINT,
                        version VARCHAR(50) DEFAULT '1.0.0',
                        entry_point VARCHAR(200) DEFAULT 'index.html',
                        quiz_enabled BOOLEAN DEFAULT FALSE,
                        quiz_file VARCHAR(200) DEFAULT 'quiz.html',
                        pass_threshold INT DEFAULT 70,
                        max_attempts INT DEFAULT 0,
                        questions_count INT DEFAULT 0,
                        content_type VARCHAR(20) DEFAULT 'FORMATION',
                        status VARCHAR(20) DEFAULT 'DRAFT',
                        deployed_path VARCHAR(500),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        published_at TIMESTAMP,
                        FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS course_groups (
                        course_id BIGINT NOT NULL, group_id BIGINT NOT NULL,
                        PRIMARY KEY (course_id, group_id),
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                        FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS quiz_results (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        course_id BIGINT NOT NULL,
                        attempt_number INT DEFAULT 1,
                        score INT NOT NULL,
                        total_questions INT DEFAULT 0,
                        correct_answers INT DEFAULT 0,
                        wrong_answers INT DEFAULT 0,
                        skipped INT DEFAULT 0,
                        module_scores CLOB DEFAULT '{}',
                        detailed_answers CLOB DEFAULT '[]',
                        duration_seconds INT DEFAULT 0,
                        submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        setting_key VARCHAR(100) PRIMARY KEY,
                        setting_value CLOB
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT,
                        user_name VARCHAR(200),
                        action VARCHAR(100) NOT NULL,
                        details CLOB DEFAULT '',
                        ip_address VARCHAR(50),
                        session_id VARCHAR(100),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS bundle_backups (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        course_id BIGINT NOT NULL,
                        version VARCHAR(50),
                        backup_path VARCHAR(500),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    )""");

                // ── Search Index (full-text) ──
                s.execute("""
                    CREATE TABLE IF NOT EXISTS search_index (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        course_id BIGINT NOT NULL,
                        field_name VARCHAR(50) NOT NULL,
                        field_value CLOB,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    )""");

                // ── Schema migration: add content_type if missing (upgrade from < 1.2) ──
                try { s.execute("ALTER TABLE courses ADD COLUMN IF NOT EXISTS content_type VARCHAR(20) DEFAULT 'FORMATION'"); }
                catch (SQLException ignored) {}

                // ── Schema migration: add photo_url and bio to users (upgrade from < 1.4) ──
                try { s.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS photo_url VARCHAR(500) DEFAULT ''"); }
                catch (SQLException ignored) {}
                try { s.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(500) DEFAULT ''"); }
                catch (SQLException ignored) {}

                // ── Plugins table (upgrade from < 2.0) ──
                s.execute("""
                    CREATE TABLE IF NOT EXISTS plugins (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        plugin_id VARCHAR(100) NOT NULL UNIQUE,
                        name VARCHAR(200) NOT NULL,
                        description CLOB DEFAULT '',
                        icon VARCHAR(20) DEFAULT '🧩',
                        version VARCHAR(50) DEFAULT '1.0.0',
                        author VARCHAR(200) DEFAULT '',
                        entry_point VARCHAR(200) DEFAULT 'index.html',
                        roles VARCHAR(200) DEFAULT '*',
                        category VARCHAR(100) DEFAULT 'Modules',
                        status VARCHAR(20) DEFAULT 'ACTIVE',
                        config_json CLOB DEFAULT '{}',
                        sort_order INT DEFAULT 100,
                        installed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )""");

                // Indexes
                s.execute("CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_courses_status ON courses(status)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_courses_ctype ON courses(content_type)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_search_course ON search_index(course_id)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_quiz_user ON quiz_results(user_id)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_quiz_course ON quiz_results(course_id)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_audit_date ON audit_log(created_at DESC)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_log(user_id)");

                // Default admin
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users WHERE role='ADMIN'");
                rs.next();
                if (rs.getInt(1) == 0) {
                    String adminUser = AppConfig.get("lms.admin.username", "admin");
                    String adminPwd = AppConfig.get("lms.admin.password", "admin2025");
                    String hash = PasswordUtil.hash(adminPwd);
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO users (username,password_hash,nom,prenom,role,must_change_password) VALUES (?,?,?,?,?,?)")) {
                        ps.setString(1, adminUser); ps.setString(2, hash);
                        ps.setString(3, "Administrateur"); ps.setString(4, "Admin");
                        ps.setString(5, "ADMIN"); ps.setBoolean(6, true);
                        ps.executeUpdate();
                    }
                    System.out.println("[LMS] Compte admin cree: " + adminUser);
                }

                // Default settings
                String platName = AppConfig.get("lms.platform.name", "LMS Platform");
                String platColor = AppConfig.get("lms.platform.color", "#4285f4");
                String platLogo = AppConfig.get("lms.platform.logo", "");
                mergeSettings(c, "platform_name", platName);
                mergeSettings(c, "primary_color", platColor);
                mergeSettings(c, "logo_url", platLogo);
                mergeSettings(c, "company_description",
                    AppConfig.get("lms.company.description", "Plateforme de formation professionnelle en ligne."));
                mergeSettings(c, "company_mission",
                    AppConfig.get("lms.company.mission", "Democratiser l'acces a la formation technique de qualite."));
                mergeSettings(c, "company_contact",
                    AppConfig.get("lms.company.contact", ""));

                // AI settings (empty by default — admin configures)
                mergeSettings(c, "ai_api_key", AppConfig.get("lms.ai.api-key", ""));
                mergeSettings(c, "ai_model", AppConfig.get("lms.ai.model", "claude-sonnet-4-20250514"));
                mergeSettings(c, "ai_system_prompt", ""); // empty = use built-in default

                System.out.println("[LMS] Base H2 initialisee: " + dbUrl);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver H2 introuvable dans le classpath. Verifiez que h2-*.jar est dans WEB-INF/lib/", e);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur init BDD H2: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public void shutdown() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
        } catch (SQLException ignored) {}
    }

    private void mergeSettings(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("MERGE INTO settings KEY(setting_key) VALUES (?,?)")) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate();
        }
    }
}

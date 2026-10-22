package com.lms.platform.dao;

import com.lms.platform.util.AppConfig;
import com.lms.platform.util.PasswordUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;

public class DatabaseManager {
    private static DatabaseManager instance;
    private HikariDataSource ds;

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    /** dataDir n'est plus utilise pour la base (MySQL externe) ; garde pour compatibilite d'appel. */
    public void init(String dataDir) {
        String url = AppConfig.get("lms.db.url", "");
        if (url.isEmpty()) {
            throw new IllegalStateException("LMS_DB_URL (ou lms.db.url) est obligatoire : ex. jdbc:mysql://HOTE:3306/lms");
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(AppConfig.get("lms.db.user", "lms"));
        cfg.setPassword(AppConfig.get("lms.db.password", ""));
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setPoolName("lms-pool");
        cfg.setMaximumPoolSize(AppConfig.getInt("lms.db.pool.max", 5));   // db-f1-micro : peu de connexions
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(10_000);
        cfg.setMaxLifetime(25 * 60_000L);  // < wait_timeout cote serveur
        ds = new HikariDataSource(cfg);
        // Ne jamais afficher l'URL complete avec identifiants : seulement l'hote/base
        System.out.println("[LMS] Pool MySQL cree (max=" + cfg.getMaximumPoolSize() + ")");

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
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        last_login DATETIME NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(100) NOT NULL UNIQUE,
                        description VARCHAR(500) DEFAULT '',
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS user_groups (
                        user_id BIGINT NOT NULL, group_id BIGINT NOT NULL,
                        PRIMARY KEY (user_id, group_id),
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS courses (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        bundle_id VARCHAR(100) NOT NULL UNIQUE,
                        title VARCHAR(300) NOT NULL,
                        description LONGTEXT DEFAULT (''),
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
                        deployed_path VARCHAR(500) NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        published_at DATETIME NULL,
                        FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS course_groups (
                        course_id BIGINT NOT NULL, group_id BIGINT NOT NULL,
                        PRIMARY KEY (course_id, group_id),
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                        FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
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
                        module_scores LONGTEXT DEFAULT ('{}'),
                        detailed_answers LONGTEXT DEFAULT ('[]'),
                        duration_seconds INT DEFAULT 0,
                        submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        setting_key VARCHAR(100) PRIMARY KEY,
                        setting_value LONGTEXT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT,
                        user_name VARCHAR(200),
                        action VARCHAR(100) NOT NULL,
                        details LONGTEXT DEFAULT (''),
                        ip_address VARCHAR(50),
                        session_id VARCHAR(100),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS bundle_backups (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        course_id BIGINT NOT NULL,
                        version VARCHAR(50),
                        backup_path VARCHAR(500),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                // ── Search Index (full-text) ──
                s.execute("""
                    CREATE TABLE IF NOT EXISTS search_index (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        course_id BIGINT NOT NULL,
                        field_name VARCHAR(50) NOT NULL,
                        field_value LONGTEXT,
                        FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                // ── Plugins table (upgrade from < 2.0) ──
                s.execute("""
                    CREATE TABLE IF NOT EXISTS plugins (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        plugin_id VARCHAR(100) NOT NULL UNIQUE,
                        name VARCHAR(200) NOT NULL,
                        description LONGTEXT DEFAULT (''),
                        icon VARCHAR(20) DEFAULT '🧩',
                        version VARCHAR(50) DEFAULT '1.0.0',
                        author VARCHAR(200) DEFAULT '',
                        entry_point VARCHAR(200) DEFAULT 'index.html',
                        roles VARCHAR(200) DEFAULT '*',
                        category VARCHAR(100) DEFAULT 'Modules',
                        status VARCHAR(20) DEFAULT 'ACTIVE',
                        config_json LONGTEXT DEFAULT ('{}'),
                        sort_order INT DEFAULT 100,
                        installed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

                // Indexes (idempotents : code 1061 = index deja existant)
                createIndex(s, "idx_users_role", "users", "role");
                createIndex(s, "idx_courses_status", "courses", "status");
                createIndex(s, "idx_courses_ctype", "courses", "content_type");
                createIndex(s, "idx_search_course", "search_index", "course_id");
                createIndex(s, "idx_quiz_user", "quiz_results", "user_id");
                createIndex(s, "idx_quiz_course", "quiz_results", "course_id");
                createIndex(s, "idx_audit_date", "audit_log", "created_at");
                createIndex(s, "idx_audit_user", "audit_log", "user_id");

                // Default admin
                int admins;
                try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users WHERE role='ADMIN'")) { rs.next(); admins = rs.getInt(1); }
                if (admins == 0) {
                    String adminUser = AppConfig.get("lms.admin.username", "admin");
                    String adminPwd = AppConfig.get("lms.admin.password", "");
                    if (adminPwd.isEmpty()) {
                        throw new IllegalStateException("Aucun admin en base : LMS_ADMIN_PASSWORD est obligatoire pour le creer");
                    }
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

                System.out.println("[LMS] Base MySQL initialisee");
        } catch (SQLException e) {
            throw new RuntimeException("Erreur init BDD MySQL: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void shutdown() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    private void createIndex(Statement s, String name, String table, String column) throws SQLException {
        try {
            s.execute("CREATE INDEX " + name + " ON " + table + "(" + column + ")");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1061) throw e;   // 1061 = Duplicate key name
        }
    }

    private void mergeSettings(Connection c, String key, String value) throws SQLException {
        // Ne pas ecraser une valeur deja modifiee par l'admin : insere seulement si absente
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO settings (setting_key,setting_value) VALUES (?,?)")) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate();
        }
    }
}

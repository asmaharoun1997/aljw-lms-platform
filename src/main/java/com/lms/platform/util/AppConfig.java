package com.lms.platform.util;

import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Charge lms.properties depuis le classpath (WEB-INF/classes/ ou CATALINA_BASE/lib/).
 * Priorité : variables d'environnement LMS_* > System properties (-D) > lms.properties > défauts codés.
 * Les secrets (mot de passe BDD, admin, clé API) ne sont jamais affichés dans les logs.
 */
public class AppConfig {

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    /** Variable d'environnement -> clé de configuration */
    private static final Map<String,String> ENV = Map.of(
        "LMS_DB_URL", "lms.db.url",
        "LMS_DB_USER", "lms.db.user",
        "LMS_DB_PASSWORD", "lms.db.password",
        "LMS_ADMIN_USERNAME", "lms.admin.username",
        "LMS_ADMIN_PASSWORD", "lms.admin.password",
        "LMS_DATA_DIR", "lms.data.dir",
        "LMS_AI_API_KEY", "lms.ai.api-key"
    );
    private static boolean isSecret(String key) {
        return key.contains("password") || key.contains("api-key") || key.equals("lms.db.url");
    }

    public static synchronized void load() {
        if (loaded) return;
        // 1. Load defaults
        props.setProperty("lms.data.dir", "./data/lms");
        props.setProperty("lms.db.user", "lms");
        props.setProperty("lms.db.password", "");
        props.setProperty("lms.admin.username", "admin");
        props.setProperty("lms.admin.password", "");   // obligatoire via LMS_ADMIN_PASSWORD
        props.setProperty("lms.session.timeout.minutes", "120");
        props.setProperty("lms.upload.max.size", "200000000");
        props.setProperty("lms.platform.name", "LMS Platform");
        props.setProperty("lms.platform.color", "#4285f4");
        props.setProperty("lms.platform.logo", "");

        // 2. Load from classpath
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("lms.properties")) {
            if (is != null) {
                props.load(is);
                System.out.println("[LMS] Configuration chargee depuis lms.properties");
            } else {
                System.out.println("[LMS] WARN: lms.properties non trouve dans le classpath, utilisation des defauts");
            }
        } catch (Exception e) {
            System.err.println("[LMS] WARN: Erreur lecture lms.properties: " + e.getMessage());
        }

        // 3. System properties override (ex: -Dlms.data.dir=/custom/path)
        for (String key : props.stringPropertyNames()) {
            String sysVal = System.getProperty(key);
            if (sysVal != null && !sysVal.isEmpty()) {
                props.setProperty(key, sysVal);
                System.out.println("[LMS] Override systeme: " + key + (isSecret(key) ? " (valeur masquee)" : "=" + sysVal));
            }
        }

        // 4. Variables d'environnement (priorite maximale)
        for (Map.Entry<String,String> e : ENV.entrySet()) {
            String v = System.getenv(e.getKey());
            if (v != null && !v.isEmpty()) {
                props.setProperty(e.getValue(), v);
                System.out.println("[LMS] Override env: " + e.getKey() + (isSecret(e.getValue()) ? " (valeur masquee)" : "=" + v));
            }
        }

        loaded = true;

        // Log effective config
        System.out.println("[LMS] Config effective:");
        System.out.println("[LMS]   data.dir    = " + get("lms.data.dir"));
        System.out.println("[LMS]   db.user     = " + get("lms.db.user"));
        System.out.println("[LMS]   db.url      = " + (get("lms.db.url").isEmpty() ? "ABSENTE" : "definie"));
        System.out.println("[LMS]   db.password = " + (get("lms.db.password").isEmpty() ? "VIDE" : "definie"));
        System.out.println("[LMS]   admin.pwd   = " + (get("lms.admin.password").isEmpty() ? "ABSENT" : "defini"));
        System.out.println("[LMS]   admin.user  = " + get("lms.admin.username"));
        System.out.println("[LMS]   platform    = " + get("lms.platform.name"));
    }

    public static String get(String key) {
        return props.getProperty(key, "");
    }

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try { return Integer.parseInt(get(key)); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public static long getLong(String key, long defaultValue) {
        try { return Long.parseLong(get(key)); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}

package com.lms.platform.listener;

import com.lms.platform.dao.DatabaseManager;
import com.lms.platform.service.BundleService;
import com.lms.platform.util.AppConfig;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.io.File;

@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            System.out.println("[LMS] =========================================");
            System.out.println("[LMS] Demarrage LMS Platform...");
            System.out.println("[LMS] =========================================");

            // 1. Load configuration
            AppConfig.load();

            // 2. Resolve and create data directory
            String dataDir = AppConfig.get("lms.data.dir", "./data/lms");
            File dataDirFile = new File(dataDir);
            if (!dataDirFile.isAbsolute()) {
                // Relative path: resolve from CATALINA_BASE or working dir
                String catalinaBase = System.getProperty("catalina.base", System.getProperty("user.dir", "."));
                dataDirFile = new File(catalinaBase, dataDir);
            }
            dataDir = dataDirFile.getCanonicalPath();

            if (!dataDirFile.exists()) {
                boolean created = dataDirFile.mkdirs();
                if (!created) {
                    System.err.println("[LMS] ERREUR: Impossible de creer le repertoire: " + dataDir);
                    System.err.println("[LMS] Verifiez les permissions sur: " + dataDirFile.getParent());
                    throw new RuntimeException("Cannot create data directory: " + dataDir);
                }
            }

            if (!dataDirFile.canWrite()) {
                System.err.println("[LMS] ERREUR: Repertoire non inscriptible: " + dataDir);
                throw new RuntimeException("Data directory not writable: " + dataDir);
            }

            System.out.println("[LMS] Data dir: " + dataDir);

            // 3. Initialize database
            DatabaseManager.getInstance().init(dataDir);

            // 4. Initialize bundle service
            BundleService bundleService = new BundleService();
            bundleService.init(dataDir);

            // 5. Store in servlet context
            sce.getServletContext().setAttribute("bundleService", bundleService);
            sce.getServletContext().setAttribute("dataDir", dataDir);

            System.out.println("[LMS] =========================================");
            System.out.println("[LMS] Plateforme prete.");
            System.out.println("[LMS] =========================================");

        } catch (Exception e) {
            System.err.println("[LMS] !! ERREUR FATALE AU DEMARRAGE !!");
            System.err.println("[LMS] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            // Re-throw to prevent Tomcat from marking the context as started
            throw new RuntimeException("LMS Platform startup failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            DatabaseManager.getInstance().shutdown();
        } catch (Exception ignored) {}
        System.out.println("[LMS] Plateforme arretee.");
    }
}

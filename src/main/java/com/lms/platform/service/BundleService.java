package com.lms.platform.service;

import com.lms.platform.model.Course;
import com.lms.platform.dao.CourseDAO;

import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.*;

public class BundleService {

    private final CourseDAO courseDAO = new CourseDAO();
    private String bundlesDir;
    private String backupsDir;

    public void init(String dataDir) {
        bundlesDir = dataDir + "/bundles";
        backupsDir = dataDir + "/backups";
        new File(bundlesDir).mkdirs();
        new File(backupsDir).mkdirs();
    }

    public String getBundlesDir() { return bundlesDir; }

    /**
     * Deploys a bundle ZIP. If the bundle already exists, backs up the old version first.
     * Returns the Course object with metadata parsed from bundle.xml.
     */
    public Course deployBundle(InputStream zipStream, long authorId) throws Exception {
        // 1. Save ZIP to temp
        Path tempZip = Files.createTempFile("bundle-", ".zip");
        try {
            Files.copy(zipStream, tempZip, StandardCopyOption.REPLACE_EXISTING);

            // 2. Extract bundle.xml from ZIP without fully extracting
            Course meta = parseBundleXml(tempZip);
            if (meta == null) throw new IllegalArgumentException("bundle.xml manquant ou invalide dans le ZIP");

            String bundleId = meta.getBundleId();
            Path targetDir = Path.of(bundlesDir, bundleId);

            // 3. Check if course exists → backup
            Course existing = courseDAO.findByBundleId(bundleId);
            if (existing != null && Files.isDirectory(targetDir)) {
                backupBundle(existing);
            }

            // 4. Extract ZIP to target directory
            if (Files.isDirectory(targetDir)) deleteDir(targetDir.toFile());
            Files.createDirectories(targetDir);
            extractZip(tempZip, targetDir);

            // 5. Save or update in DB
            meta.setAuthorId(authorId);
            meta.setDeployedPath(targetDir.toString());
            meta.setStatus(Course.Status.DRAFT);

            if (existing != null) {
                meta.setId(existing.getId());
                meta.setStatus(existing.getStatus()); // keep current status
                courseDAO.updateCourse(meta);
                return meta;
            } else {
                long id = courseDAO.create(meta);
                meta.setId(id);
                return meta;
            }
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    /** Creates a backup of the current bundle version */
    public void backupBundle(Course course) throws Exception {
        Path sourceDir = Path.of(course.getDeployedPath());
        if (!Files.isDirectory(sourceDir)) return;

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String backupName = course.getBundleId() + "_v" + course.getVersion() + "_" + ts;
        Path backupZip = Path.of(backupsDir, backupName + ".zip");

        createZipFromDir(sourceDir, backupZip);
        courseDAO.createBackup(course.getId(), course.getVersion(), backupZip.toString());
        System.out.println("[LMS] Backup created: " + backupZip);
    }

    /** Rollback a course to a specific backup */
    public boolean rollback(long courseId, long backupId) throws Exception {
        Course course = courseDAO.findById(courseId);
        if (course == null) return false;

        var backups = courseDAO.getBackups(courseId);
        var backup = backups.stream().filter(b -> (long) b.get("id") == backupId).findFirst();
        if (backup.isEmpty()) return false;

        Path backupZip = Path.of((String) backup.get().get("backupPath"));
        if (!Files.exists(backupZip)) return false;

        // Backup current first
        backupBundle(course);

        // Extract backup over current
        Path targetDir = Path.of(course.getDeployedPath());
        if (Files.isDirectory(targetDir)) deleteDir(targetDir.toFile());
        Files.createDirectories(targetDir);
        extractZip(backupZip, targetDir);

        // Update version in DB
        course.setVersion((String) backup.get().get("version") + "-rollback");
        courseDAO.updateCourse(course);
        return true;
    }

    /** Resolve the filesystem path for a course resource */
    public Path resolveResource(Course course, String resource) {
        if (course.getDeployedPath() == null) return null;
        Path base = Path.of(course.getDeployedPath());
        Path resolved = base.resolve(resource).normalize();
        // Security: prevent path traversal
        if (!resolved.startsWith(base)) return null;
        if (!Files.exists(resolved)) return null;
        return resolved;
    }

    // ── XML parsing ──
    private Course parseBundleXml(Path zipFile) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Handle bundle.xml at root or in a single subdirectory
                if (name.equals("bundle.xml") || name.endsWith("/bundle.xml")) {
                    return parseXml(zis);
                }
            }
        }
        return null;
    }

    private Course parseXml(InputStream is) throws Exception {
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(is);
        Element root = doc.getDocumentElement();

        Course c = new Course();
        c.setBundleId(text(root, "id"));
        c.setTitle(text(root, "title"));
        c.setDescription(text(root, "description"));
        c.setVersion(text(root, "version", "1.0.0"));
        c.setCategory(text(root, "category"));
        c.setTags(text(root, "tags"));
        c.setLevel(text(root, "level"));
        c.setDuration(text(root, "duration"));
        c.setEntryPoint(text(root, "entry-point", "index.html"));

        NodeList quizNodes = root.getElementsByTagName("quiz");
        if (quizNodes.getLength() > 0) {
            Element quiz = (Element) quizNodes.item(0);
            c.setQuizEnabled("true".equals(quiz.getAttribute("enabled")));
            c.setQuizFile(text(quiz, "file", "quiz.html"));
            c.setQuestionsCount(intText(quiz, "questions-count", 0));
            c.setPassThreshold(intText(quiz, "pass-threshold", 70));
            c.setMaxAttempts(intText(quiz, "max-attempts", 0));
        }

        if (c.getBundleId() == null || c.getBundleId().isEmpty())
            throw new IllegalArgumentException("bundle.xml: <id> obligatoire");
        if (c.getTitle() == null || c.getTitle().isEmpty())
            throw new IllegalArgumentException("bundle.xml: <title> obligatoire");

        return c;
    }

    private String text(Element parent, String tag) { return text(parent, tag, ""); }
    private String text(Element parent, String tag, String def) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() > 0 && nl.item(0).getTextContent() != null)
            return nl.item(0).getTextContent().trim();
        return def;
    }
    private int intText(Element parent, String tag, int def) {
        String val = text(parent, tag);
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return def; }
    }

    // ── ZIP utilities ──
    private void extractZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path outPath = targetDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(targetDir)) continue; // security
                Files.createDirectories(outPath.getParent());
                Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void createZipFromDir(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            Files.walk(sourceDir).filter(Files::isRegularFile).forEach(path -> {
                try {
                    String entryName = sourceDir.relativize(path).toString();
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        }
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory()) { File[] children = dir.listFiles(); if (children != null) for (File f : children) deleteDir(f); }
        dir.delete();
    }
}

package com.lms.platform.dao;

import com.lms.platform.model.Course;
import java.sql.*;
import java.util.*;

public class CourseDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public long create(Course co) throws SQLException {
        String sql = """
            INSERT INTO courses (bundle_id,title,description,category,tags,level,duration,author_id,version,
            entry_point,quiz_enabled,quiz_file,pass_threshold,max_attempts,questions_count,content_type,status,deployed_path)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, co.getBundleId()); ps.setString(2, co.getTitle()); ps.setString(3, co.getDescription());
            ps.setString(4, co.getCategory()); ps.setString(5, co.getTags()); ps.setString(6, co.getLevel());
            ps.setString(7, co.getDuration()); ps.setLong(8, co.getAuthorId()); ps.setString(9, co.getVersion());
            ps.setString(10, co.getEntryPoint()); ps.setBoolean(11, co.isQuizEnabled());
            ps.setString(12, co.getQuizFile()); ps.setInt(13, co.getPassThreshold());
            ps.setInt(14, co.getMaxAttempts()); ps.setInt(15, co.getQuestionsCount());
            ps.setString(16, co.getContentType().name());
            ps.setString(17, co.getStatus().name()); ps.setString(18, co.getDeployedPath());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return -1;
    }

    public Course findById(long id) throws SQLException {
        String sql = "SELECT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c LEFT JOIN users u ON c.author_id=u.id WHERE c.id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public Course findByBundleId(String bundleId) throws SQLException {
        String sql = "SELECT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c LEFT JOIN users u ON c.author_id=u.id WHERE c.bundle_id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bundleId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public List<Course> findAll() throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c LEFT JOIN users u ON c.author_id=u.id ORDER BY c.title";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Course> findAllByType(Course.ContentType type) throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c LEFT JOIN users u ON c.author_id=u.id WHERE c.content_type=? ORDER BY c.title";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type.name());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public List<Course> findByAuthorAndType(long authorId, Course.ContentType type) throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c LEFT JOIN users u ON c.author_id=u.id WHERE c.author_id=? AND c.content_type=? ORDER BY c.title";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, authorId); ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public List<Course> findByAuthor(long authorId) throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = "SELECT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c LEFT JOIN users u ON c.author_id=u.id WHERE c.author_id=? ORDER BY c.title";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, authorId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public List<Course> findPublishedForUser(long userId) throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = """
            SELECT DISTINCT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c
            LEFT JOIN users u ON c.author_id=u.id
            JOIN course_groups cg ON c.id=cg.course_id
            JOIN user_groups ug ON cg.group_id=ug.group_id
            WHERE ug.user_id=? AND c.status='PUBLISHED' AND c.content_type='FORMATION'
            ORDER BY c.title""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public boolean updateCourse(Course co) throws SQLException {
        String sql = """
            UPDATE courses SET title=?,description=?,category=?,tags=?,level=?,duration=?,version=?,
            entry_point=?,quiz_enabled=?,quiz_file=?,pass_threshold=?,max_attempts=?,questions_count=?,
            content_type=?,status=?,deployed_path=?,published_at=? WHERE id=?""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, co.getTitle()); ps.setString(2, co.getDescription());
            ps.setString(3, co.getCategory()); ps.setString(4, co.getTags());
            ps.setString(5, co.getLevel()); ps.setString(6, co.getDuration());
            ps.setString(7, co.getVersion()); ps.setString(8, co.getEntryPoint());
            ps.setBoolean(9, co.isQuizEnabled()); ps.setString(10, co.getQuizFile());
            ps.setInt(11, co.getPassThreshold()); ps.setInt(12, co.getMaxAttempts());
            ps.setInt(13, co.getQuestionsCount()); ps.setString(14, co.getContentType().name());
            ps.setString(15, co.getStatus().name());
            ps.setString(16, co.getDeployedPath()); ps.setTimestamp(17, co.getPublishedAt());
            ps.setLong(18, co.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteCourse(long id) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM courses WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        }
    }

    public List<Course> findByGroup(long groupId) throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = """
            SELECT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c
            LEFT JOIN users u ON c.author_id=u.id
            JOIN course_groups cg ON c.id=cg.course_id
            WHERE cg.group_id=? ORDER BY c.title""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public void setGroupCourses(long groupId, List<Long> courseIds) throws SQLException {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM course_groups WHERE group_id=?")) {
                ps.setLong(1, groupId); ps.executeUpdate();
            }
            if (courseIds != null && !courseIds.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO course_groups (course_id,group_id) VALUES (?,?)")) {
                    for (long cid : courseIds) { ps.setLong(1, cid); ps.setLong(2, groupId); ps.addBatch(); }
                    ps.executeBatch();
                }
            }
        }
    }

    // ── Course-Group ──
    public void assignGroup(long courseId, long groupId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO course_groups (course_id,group_id) VALUES (?,?)")) {
            ps.setLong(1, courseId); ps.setLong(2, groupId); ps.executeUpdate();
        }
    }

    public void unassignGroup(long courseId, long groupId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM course_groups WHERE course_id=? AND group_id=?")) {
            ps.setLong(1, courseId); ps.setLong(2, groupId); ps.executeUpdate();
        }
    }

    public List<Long> getCourseGroupIds(long courseId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT group_id FROM course_groups WHERE course_id=?")) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getLong(1)); }
        }
        return ids;
    }

    // ── Bundle Backup ──
    public void createBackup(long courseId, String version, String path) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bundle_backups (course_id,version,backup_path) VALUES (?,?,?)")) {
            ps.setLong(1, courseId); ps.setString(2, version); ps.setString(3, path); ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> getBackups(long courseId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM bundle_backups WHERE course_id=? ORDER BY created_at DESC")) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id")); m.put("version", rs.getString("version"));
                    m.put("backupPath", rs.getString("backup_path")); m.put("createdAt", rs.getTimestamp("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    // ── Content Type Filters ──

    public List<Course> findPublishedDocumentsForUser(long userId) throws SQLException {
        List<Course> list = new ArrayList<>();
        String sql = """
            SELECT DISTINCT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c
            LEFT JOIN users u ON c.author_id=u.id
            JOIN course_groups cg ON c.id=cg.course_id
            JOIN user_groups ug ON cg.group_id=ug.group_id
            WHERE ug.user_id=? AND c.status='PUBLISHED' AND c.content_type='DOCUMENT'
            ORDER BY c.title""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    // ── Search Index ──

    public void indexContent(long courseId, Map<String, String> fields) throws SQLException {
        try (Connection c = db.getConnection()) {
            // Clear existing index for this course
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM search_index WHERE course_id=?")) {
                ps.setLong(1, courseId); ps.executeUpdate();
            }
            // Insert new entries
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO search_index (course_id,field_name,field_value) VALUES (?,?,?)")) {
                for (var entry : fields.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                    ps.setLong(1, courseId);
                    ps.setString(2, entry.getKey());
                    ps.setString(3, entry.getValue().toLowerCase());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    /** Full-text search across title, description, tags, category, and indexed content */
    public List<Course> search(String query, String contentTypeFilter) throws SQLException {
        List<Course> list = new ArrayList<>();
        String q = "%" + query.toLowerCase().trim() + "%";
        String sql = """
            SELECT DISTINCT c.*, CONCAT(u.prenom,' ',u.nom) as author_name FROM courses c
            LEFT JOIN users u ON c.author_id=u.id
            LEFT JOIN search_index si ON c.id=si.course_id
            WHERE c.status='PUBLISHED'
            AND (LOWER(c.title) LIKE ? OR LOWER(c.description) LIKE ?
                 OR LOWER(c.tags) LIKE ? OR LOWER(c.category) LIKE ?
                 OR LOWER(si.field_value) LIKE ?)""";
        if (contentTypeFilter != null && !contentTypeFilter.isEmpty()) {
            sql += " AND c.content_type=?";
        }
        sql += " ORDER BY c.title";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, q); ps.setString(2, q); ps.setString(3, q); ps.setString(4, q); ps.setString(5, q);
            if (contentTypeFilter != null && !contentTypeFilter.isEmpty()) ps.setString(6, contentTypeFilter);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    // ── Stats ──
    public int countByStatus(Course.Status status) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM courses WHERE status=?")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private Course map(ResultSet rs) throws SQLException {
        Course co = new Course();
        co.setId(rs.getLong("id")); co.setBundleId(rs.getString("bundle_id"));
        co.setTitle(rs.getString("title")); co.setDescription(rs.getString("description"));
        co.setCategory(rs.getString("category")); co.setTags(rs.getString("tags"));
        co.setLevel(rs.getString("level")); co.setDuration(rs.getString("duration"));
        co.setAuthorId(rs.getLong("author_id")); co.setVersion(rs.getString("version"));
        co.setEntryPoint(rs.getString("entry_point")); co.setQuizEnabled(rs.getBoolean("quiz_enabled"));
        co.setQuizFile(rs.getString("quiz_file")); co.setPassThreshold(rs.getInt("pass_threshold"));
        co.setMaxAttempts(rs.getInt("max_attempts")); co.setQuestionsCount(rs.getInt("questions_count"));
        co.setStatus(Course.Status.valueOf(rs.getString("status")));
        try { co.setContentType(Course.ContentType.valueOf(rs.getString("content_type"))); } catch (Exception e) { co.setContentType(Course.ContentType.FORMATION); }
        co.setDeployedPath(rs.getString("deployed_path"));
        co.setCreatedAt(rs.getTimestamp("created_at")); co.setPublishedAt(rs.getTimestamp("published_at"));
        try { co.setAuthorName(rs.getString("author_name")); } catch (SQLException ignored) {}
        return co;
    }
}

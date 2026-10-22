package com.lms.platform.dao;

import com.lms.platform.model.QuizResult;
import java.sql.*;
import java.util.*;

public class QuizResultDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public long save(QuizResult r) throws SQLException {
        // Calculate attempt number
        int attempt = getAttemptCount(r.getUserId(), r.getCourseId()) + 1;
        r.setAttemptNumber(attempt);
        String sql = """
            INSERT INTO quiz_results (user_id,course_id,attempt_number,score,total_questions,correct_answers,
            wrong_answers,skipped,module_scores,detailed_answers,duration_seconds) VALUES (?,?,?,?,?,?,?,?,?,?,?)""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, r.getUserId()); ps.setLong(2, r.getCourseId()); ps.setInt(3, attempt);
            ps.setInt(4, r.getScore()); ps.setInt(5, r.getTotalQuestions()); ps.setInt(6, r.getCorrectAnswers());
            ps.setInt(7, r.getWrongAnswers()); ps.setInt(8, r.getSkipped());
            ps.setString(9, r.getModuleScores()); ps.setString(10, r.getDetailedAnswers());
            ps.setInt(11, r.getDurationSeconds());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return -1;
    }

    public int getAttemptCount(long userId, long courseId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM quiz_results WHERE user_id=? AND course_id=?")) {
            ps.setLong(1, userId); ps.setLong(2, courseId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public int getBestScore(long userId, long courseId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(score),0) FROM quiz_results WHERE user_id=? AND course_id=?")) {
            ps.setLong(1, userId); ps.setLong(2, courseId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public List<QuizResult> findByCourse(long courseId) throws SQLException {
        return query("SELECT r.*, COALESCE(CONCAT(u.prenom,' ',u.nom), CONCAT('Utilisateur #',r.user_id)) as user_name, COALESCE(c.title, CONCAT('Cours #',r.course_id)) as course_title FROM quiz_results r LEFT JOIN users u ON r.user_id=u.id LEFT JOIN courses c ON r.course_id=c.id WHERE r.course_id=? ORDER BY r.submitted_at DESC", courseId);
    }

    public List<QuizResult> findByUser(long userId) throws SQLException {
        return query("SELECT r.*, COALESCE(CONCAT(u.prenom,' ',u.nom), CONCAT('Utilisateur #',r.user_id)) as user_name, COALESCE(c.title, CONCAT('Cours #',r.course_id)) as course_title FROM quiz_results r LEFT JOIN users u ON r.user_id=u.id LEFT JOIN courses c ON r.course_id=c.id WHERE r.user_id=? ORDER BY r.submitted_at DESC", userId);
    }

    public List<QuizResult> findAll() throws SQLException {
        List<QuizResult> list = new ArrayList<>();
        String sql = "SELECT r.*, COALESCE(CONCAT(u.prenom,' ',u.nom), CONCAT('Utilisateur #',r.user_id)) as user_name, COALESCE(c.title, CONCAT('Cours #',r.course_id)) as course_title FROM quiz_results r LEFT JOIN users u ON r.user_id=u.id LEFT JOIN courses c ON r.course_id=c.id ORDER BY r.submitted_at DESC";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public QuizResult findById(long id) throws SQLException {
        String sql = "SELECT r.*, COALESCE(CONCAT(u.prenom,' ',u.nom), CONCAT('Utilisateur #',r.user_id)) as user_name, COALESCE(c.title, CONCAT('Cours #',r.course_id)) as course_title FROM quiz_results r LEFT JOIN users u ON r.user_id=u.id LEFT JOIN courses c ON r.course_id=c.id WHERE r.id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public boolean delete(long id) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM quiz_results WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        }
    }

    // ── Reporting ──

    /** Global stats across all courses */
    public Map<String, Object> globalStats() throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        String sql = "SELECT COUNT(*) cnt, COALESCE(AVG(score),0) avg_s, COALESCE(MAX(score),0) best, COALESCE(MIN(score),0) worst, " +
                "COALESCE(SUM(CASE WHEN score>=70 THEN 1 ELSE 0 END),0) pass_c, COALESCE(AVG(duration_seconds),0) avg_d FROM quiz_results";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                m.put("totalSessions", rs.getInt("cnt")); m.put("avgScore", Math.round(rs.getDouble("avg_s")*10)/10.0);
                m.put("bestScore", rs.getInt("best")); m.put("worstScore", rs.getInt("worst"));
                m.put("passCount", rs.getInt("pass_c"));
                m.put("failCount", rs.getInt("cnt") - rs.getInt("pass_c"));
                m.put("avgDuration", Math.round(rs.getDouble("avg_d")));
            }
        }
        return m;
    }

    /** Per-course stats */
    public List<Map<String, Object>> statsByCourse() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.title, c.pass_threshold, COUNT(r.id) cnt, COALESCE(AVG(r.score),0) avg_s,
            COUNT(DISTINCT r.user_id) students,
            COALESCE(SUM(CASE WHEN r.score>=c.pass_threshold THEN 1 ELSE 0 END),0) pass_c
            FROM courses c LEFT JOIN quiz_results r ON c.id=r.course_id
            WHERE c.quiz_enabled=TRUE AND c.content_type='FORMATION'
            GROUP BY c.id, c.title, c.pass_threshold ORDER BY c.title""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("courseId", rs.getLong("id")); m.put("courseTitle", rs.getString("title"));
                m.put("attempts", rs.getInt("cnt")); m.put("avgScore", Math.round(rs.getDouble("avg_s")*10)/10.0);
                m.put("uniqueStudents", rs.getInt("students")); m.put("passCount", rs.getInt("pass_c"));
                list.add(m);
            }
        }
        return list;
    }

    /** Student progress: best score per course */
    public List<Map<String, Object>> studentProgress(long userId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.title, c.pass_threshold, MAX(r.score) best, COUNT(r.id) attempts,
            MAX(r.submitted_at) last_attempt
            FROM quiz_results r JOIN courses c ON r.course_id=c.id
            WHERE r.user_id=? GROUP BY c.id, c.title, c.pass_threshold ORDER BY c.title""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("courseId", rs.getLong("id")); m.put("courseTitle", rs.getString("title"));
                    m.put("passThreshold", rs.getInt("pass_threshold"));
                    m.put("bestScore", rs.getInt("best")); m.put("attempts", rs.getInt("attempts"));
                    m.put("passed", rs.getInt("best") >= rs.getInt("pass_threshold"));
                    m.put("lastAttempt", rs.getTimestamp("last_attempt"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    private List<QuizResult> query(String sql, long paramId) throws SQLException {
        List<QuizResult> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, paramId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    private QuizResult map(ResultSet rs) throws SQLException {
        QuizResult r = new QuizResult();
        r.setId(rs.getLong("id")); r.setUserId(rs.getLong("user_id")); r.setCourseId(rs.getLong("course_id"));
        r.setAttemptNumber(rs.getInt("attempt_number")); r.setScore(rs.getInt("score"));
        r.setTotalQuestions(rs.getInt("total_questions")); r.setCorrectAnswers(rs.getInt("correct_answers"));
        r.setWrongAnswers(rs.getInt("wrong_answers")); r.setSkipped(rs.getInt("skipped"));
        r.setModuleScores(rs.getString("module_scores")); r.setDetailedAnswers(rs.getString("detailed_answers"));
        r.setDurationSeconds(rs.getInt("duration_seconds")); r.setSubmittedAt(rs.getTimestamp("submitted_at"));
        try { r.setUserName(rs.getString("user_name")); } catch (SQLException ignored) {}
        try { r.setCourseTitle(rs.getString("course_title")); } catch (SQLException ignored) {}
        return r;
    }
}

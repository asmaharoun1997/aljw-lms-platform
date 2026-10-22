package com.lms.platform.dao;

import com.lms.platform.model.AuditLog;
import java.sql.*;
import java.util.*;

public class AuditDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public void log(long userId, String userName, String action, String details, String ip, String sessionId) {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_log (user_id,user_name,action,details,ip_address,session_id) VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, userId); ps.setString(2, userName); ps.setString(3, action);
            ps.setString(4, details); ps.setString(5, ip); ps.setString(6, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) { System.err.println("[LMS] Audit log error: " + e.getMessage()); }
    }

    public List<AuditLog> findRecent(int limit) throws SQLException {
        List<AuditLog> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public List<AuditLog> findByUser(long userId, int limit) throws SQLException {
        List<AuditLog> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM audit_log WHERE user_id=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setLong(1, userId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public List<Map<String,Object>> activeSessions() throws SQLException {
        List<Map<String,Object>> list = new ArrayList<>();
        String sql = """
            SELECT user_id, user_name, session_id, MAX(created_at) last_activity,
            MIN(CASE WHEN action='LOGIN' THEN created_at END) login_time, COUNT(*) actions
            FROM audit_log WHERE created_at > DATE_SUB(NOW(), INTERVAL 24 HOUR)
            GROUP BY user_id, user_name, session_id ORDER BY last_activity DESC""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("userId", rs.getLong("user_id")); m.put("userName", rs.getString("user_name"));
                m.put("sessionId", rs.getString("session_id")); m.put("lastActivity", rs.getTimestamp("last_activity"));
                m.put("loginTime", rs.getTimestamp("login_time")); m.put("actions", rs.getInt("actions"));
                list.add(m);
            }
        }
        return list;
    }

    public int cleanup(int daysOld) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
            ps.setInt(1, daysOld); return ps.executeUpdate();
        }
    }

    private AuditLog map(ResultSet rs) throws SQLException {
        AuditLog a = new AuditLog();
        a.setId(rs.getLong("id")); a.setUserId(rs.getLong("user_id")); a.setUserName(rs.getString("user_name"));
        a.setAction(rs.getString("action")); a.setDetails(rs.getString("details"));
        a.setIpAddress(rs.getString("ip_address")); a.setSessionId(rs.getString("session_id"));
        a.setCreatedAt(rs.getTimestamp("created_at"));
        return a;
    }
}

package com.lms.platform.dao;

import java.sql.*;
import java.util.*;

public class SettingDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public String get(String key) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT setting_value FROM settings WHERE setting_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString(1); }
        }
        return null;
    }

    public void set(String key, String value) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("MERGE INTO settings KEY(setting_key) VALUES (?,?)")) {
            ps.setString(1, key); ps.setString(2, value); ps.executeUpdate();
        }
    }

    public Map<String,String> getAll() throws SQLException {
        Map<String,String> m = new LinkedHashMap<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT setting_key, setting_value FROM settings ORDER BY setting_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) m.put(rs.getString("setting_key"), rs.getString("setting_value"));
        }
        return m;
    }
}

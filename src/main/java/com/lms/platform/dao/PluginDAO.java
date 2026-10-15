package com.lms.platform.dao;

import com.lms.platform.model.Plugin;
import java.sql.*;
import java.util.*;

public class PluginDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public long install(Plugin p) throws SQLException {
        String sql = """
            INSERT INTO plugins (plugin_id,name,description,icon,version,author,entry_point,roles,category,status,config_json,sort_order)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getPluginId()); ps.setString(2, p.getName());
            ps.setString(3, p.getDescription()); ps.setString(4, p.getIcon());
            ps.setString(5, p.getVersion()); ps.setString(6, p.getAuthor());
            ps.setString(7, p.getEntryPoint()); ps.setString(8, p.getRoles());
            ps.setString(9, p.getCategory()); ps.setString(10, p.getStatus().name());
            ps.setString(11, p.getConfigJson()); ps.setInt(12, p.getSortOrder());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return -1;
    }

    public boolean update(Plugin p) throws SQLException {
        String sql = "UPDATE plugins SET name=?,description=?,icon=?,version=?,author=?,entry_point=?,roles=?,category=?,status=?,config_json=?,sort_order=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getName()); ps.setString(2, p.getDescription());
            ps.setString(3, p.getIcon()); ps.setString(4, p.getVersion());
            ps.setString(5, p.getAuthor()); ps.setString(6, p.getEntryPoint());
            ps.setString(7, p.getRoles()); ps.setString(8, p.getCategory());
            ps.setString(9, p.getStatus().name()); ps.setString(10, p.getConfigJson());
            ps.setInt(11, p.getSortOrder()); ps.setLong(12, p.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public Plugin findById(long id) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM plugins WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public Plugin findByPluginId(String pluginId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM plugins WHERE plugin_id=?")) {
            ps.setString(1, pluginId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public List<Plugin> findAll() throws SQLException {
        List<Plugin> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM plugins ORDER BY sort_order, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<Plugin> findActiveForRole(String role) throws SQLException {
        List<Plugin> all = findAll();
        return all.stream().filter(p -> p.isAccessibleBy(role)).toList();
    }

    public boolean delete(long id) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM plugins WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        }
    }

    public boolean setStatus(long id, Plugin.Status status) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE plugins SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setString(1, status.name()); ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Plugin map(ResultSet rs) throws SQLException {
        Plugin p = new Plugin();
        p.setId(rs.getLong("id")); p.setPluginId(rs.getString("plugin_id"));
        p.setName(rs.getString("name")); p.setDescription(rs.getString("description"));
        p.setIcon(rs.getString("icon")); p.setVersion(rs.getString("version"));
        p.setAuthor(rs.getString("author")); p.setEntryPoint(rs.getString("entry_point"));
        p.setRoles(rs.getString("roles")); p.setCategory(rs.getString("category"));
        p.setStatus(Plugin.Status.valueOf(rs.getString("status")));
        p.setConfigJson(rs.getString("config_json")); p.setSortOrder(rs.getInt("sort_order"));
        p.setInstalledAt(rs.getTimestamp("installed_at")); p.setUpdatedAt(rs.getTimestamp("updated_at"));
        return p;
    }
}

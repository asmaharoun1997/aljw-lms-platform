package com.lms.platform.dao;

import com.lms.platform.model.User;
import com.lms.platform.model.Group;
import com.lms.platform.util.PasswordUtil;
import java.sql.*;
import java.util.*;

public class UserDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    // ── Users CRUD ──
    public long createUser(User u) throws SQLException {
        String sql = "INSERT INTO users (username,password_hash,nom,prenom,email,role,active,must_change_password) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getUsername()); ps.setString(2, u.getPasswordHash());
            ps.setString(3, u.getNom()); ps.setString(4, u.getPrenom());
            ps.setString(5, u.getEmail()); ps.setString(6, u.getRole().name());
            ps.setBoolean(7, u.isActive()); ps.setBoolean(8, u.isMustChangePassword());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return -1;
    }

    public User findById(long id) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public User findByUsername(String username) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE LOWER(username)=LOWER(?)")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM users ORDER BY nom, prenom");
             ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        return list;
    }

    public List<User> findByRole(User.Role role) throws SQLException {
        List<User> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE role=? ORDER BY nom, prenom")) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public List<User> findByGroup(long groupId) throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.* FROM users u JOIN user_groups ug ON u.id=ug.user_id WHERE ug.group_id=? ORDER BY u.nom";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        }
        return list;
    }

    public boolean updateUser(User u) throws SQLException {
        String sql = "UPDATE users SET username=?,nom=?,prenom=?,email=?,role=?,active=?,must_change_password=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u.getUsername()); ps.setString(2, u.getNom()); ps.setString(3, u.getPrenom());
            ps.setString(4, u.getEmail()); ps.setString(5, u.getRole().name());
            ps.setBoolean(6, u.isActive()); ps.setBoolean(7, u.isMustChangePassword()); ps.setLong(8, u.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updatePassword(long userId, String newHash) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET password_hash=?, must_change_password=FALSE WHERE id=?")) {
            ps.setString(1, newHash); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public void updateLastLogin(long userId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET last_login=CURRENT_TIMESTAMP WHERE id=?")) {
            ps.setLong(1, userId); ps.executeUpdate();
        }
    }

    public boolean deleteUser(long id) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        }
    }

    // ── Groups CRUD ──
    public long createGroup(Group g) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO groups (name,description) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, g.getName()); ps.setString(2, g.getDescription());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return -1;
    }

    public List<Group> findAllGroups() throws SQLException {
        List<Group> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM groups ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { Group g = new Group(); g.setId(rs.getLong("id")); g.setName(rs.getString("name"));
                g.setDescription(rs.getString("description")); g.setCreatedAt(rs.getTimestamp("created_at")); list.add(g); }
        }
        return list;
    }

    public boolean deleteGroup(long id) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM groups WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        }
    }

    public boolean updateGroup(long id, String name, String desc) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE groups SET name=?,description=? WHERE id=?")) {
            ps.setString(1, name); ps.setString(2, desc); ps.setLong(3, id); return ps.executeUpdate() > 0;
        }
    }

    // ── Pending / Registration ──
    public List<User> findPending() throws SQLException {
        List<User> list = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE active=FALSE ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(map(rs)); }
        return list;
    }

    public int countPending() throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE active=FALSE");
             ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
    }

    public boolean activateUser(long id) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET active=TRUE WHERE id=?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        }
    }

    /** Create user from OAuth (already verified email, no password needed) */
    public long createOAuthUser(String email, String nom, String prenom, User.Role role, String provider, boolean autoActivate) throws SQLException {
        String username = email.split("@")[0] + "_" + provider.toLowerCase();
        if (findByUsername(username) != null) username = username + "_" + System.currentTimeMillis() % 10000;
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash("OAUTH:" + provider.toUpperCase()); // marker: no local password
        u.setNom(nom); u.setPrenom(prenom); u.setEmail(email);
        u.setRole(role); u.setActive(autoActivate); u.setMustChangePassword(false);
        return createUser(u);
    }

    public User findByEmail(String email) throws SQLException {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE LOWER(email)=LOWER(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        }
        return null;
    }

    public void setGroupMembers(long groupId, List<Long> userIds) throws SQLException {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM user_groups WHERE group_id=?")) {
                ps.setLong(1, groupId); ps.executeUpdate();
            }
            if (userIds != null && !userIds.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO user_groups (user_id,group_id) VALUES (?,?)")) {
                    for (long uid : userIds) { ps.setLong(1, uid); ps.setLong(2, groupId); ps.addBatch(); }
                    ps.executeBatch();
                }
            }
        }
    }

    // ── User-Group associations ──
    public void addUserToGroup(long userId, long groupId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "MERGE INTO user_groups (user_id,group_id) VALUES (?,?)")) {
            ps.setLong(1, userId); ps.setLong(2, groupId); ps.executeUpdate();
        }
    }

    public void removeUserFromGroup(long userId, long groupId) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM user_groups WHERE user_id=? AND group_id=?")) {
            ps.setLong(1, userId); ps.setLong(2, groupId); ps.executeUpdate();
        }
    }

    public List<Long> getUserGroupIds(long userId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT group_id FROM user_groups WHERE user_id=?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getLong(1)); }
        }
        return ids;
    }

    // ── CSV Import ──
    public int importCsv(String csvContent, User.Role defaultRole) throws SQLException {
        int count = 0;
        String[] lines = csvContent.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) { // skip header
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("[;,]", -1);
            if (parts.length < 3) continue;
            String nom = parts[0].trim(), prenom = parts[1].trim(), username = parts[2].trim();
            String email = parts.length > 3 ? parts[3].trim() : "";
            String password = parts.length > 4 ? parts[4].trim() : username; // default pwd = username
            if (findByUsername(username) != null) continue; // skip existing
            User u = new User();
            u.setUsername(username); u.setNom(nom); u.setPrenom(prenom); u.setEmail(email);
            u.setPasswordHash(PasswordUtil.hash(password)); u.setRole(defaultRole);
            u.setActive(true); u.setMustChangePassword(true);
            createUser(u);
            count++;
        }
        return count;
    }

    // ── Stats ──
    public Map<String, Integer> countByRole() throws SQLException {
        Map<String, Integer> m = new LinkedHashMap<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT role, COUNT(*) cnt FROM users GROUP BY role ORDER BY role");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) m.put(rs.getString("role"), rs.getInt("cnt"));
        }
        return m;
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id")); u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setNom(rs.getString("nom")); u.setPrenom(rs.getString("prenom"));
        u.setEmail(rs.getString("email")); u.setRole(User.Role.valueOf(rs.getString("role")));
        u.setActive(rs.getBoolean("active")); u.setMustChangePassword(rs.getBoolean("must_change_password"));
        u.setCreatedAt(rs.getTimestamp("created_at")); u.setLastLogin(rs.getTimestamp("last_login"));
        try { u.setPhotoUrl(rs.getString("photo_url")); } catch (SQLException ignored) {}
        try { u.setBio(rs.getString("bio")); } catch (SQLException ignored) {}
        return u;
    }

    /** Update user profile (self-service: username, email, bio) */
    public boolean updateProfile(long userId, String username, String email, String bio) throws SQLException {
        String sql = "UPDATE users SET username=?, email=?, bio=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username); ps.setString(2, email); ps.setString(3, bio);
            ps.setLong(4, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Update user photo URL */
    public boolean updatePhoto(long userId, String photoUrl) throws SQLException {
        String sql = "UPDATE users SET photo_url=? WHERE id=?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, photoUrl); ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }
}

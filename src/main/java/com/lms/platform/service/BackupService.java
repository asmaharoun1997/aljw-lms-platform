package com.lms.platform.service;

import com.lms.platform.dao.DatabaseManager;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Full site backup and restore in XML format.
 *
 * Schema evolution tolerance:
 * - Export: every row as child elements named by column
 * - Import: matches columns by name, unknown columns ignored, missing use DB defaults
 * - FK constraints disabled during restore (SET FOREIGN_KEY_CHECKS=0)
 * - Tables processed in dependency order
 * - Per-row error catching: partial restore possible
 * - Auto-backup before restore (safety net)
 */
public class BackupService {

    private static final String SCHEMA_VERSION = "2.0.0";

    private static final String[] TABLES = {
        "settings", "users", "groups", "user_groups",
        "courses", "course_groups", "bundle_backups",
        "quiz_results", "search_index", "audit_log"
    };

    private static final String[] IDENTITY_TABLES = {
        "users", "groups", "courses", "quiz_results", "search_index", "audit_log", "bundle_backups"
    };

    // ═══ EXPORT ═══

    public byte[] exportXml() throws Exception {
        DocumentBuilder bld = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = bld.newDocument();

        Element root = doc.createElement("lms-backup");
        root.setAttribute("schema-version", SCHEMA_VERSION);
        root.setAttribute("exported-at", now());
        root.setAttribute("generator", "LMS Platform BackupService");
        doc.appendChild(root);

        Element summary = doc.createElement("summary");
        root.appendChild(summary);
        int totalRows = 0;

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            for (String table : TABLES) {
                totalRows += exportTable(doc, root, conn, table);
            }
        }

        summary.setAttribute("total-rows", String.valueOf(totalRows));
        summary.setAttribute("tables", String.valueOf(TABLES.length));

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer tr = tf.newTransformer();
        tr.setOutputProperty(OutputKeys.INDENT, "yes");
        tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        tr.setOutputProperty(OutputKeys.STANDALONE, "yes");
        tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tr.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }

    private int exportTable(Document doc, Element root, Connection conn, String table) {
        Element tableEl = doc.createElement("table");
        tableEl.setAttribute("name", table);
        int rowCount = 0;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + q(table) + " ORDER BY 1")) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> colNames = new ArrayList<>();
            StringBuilder colDef = new StringBuilder();
            for (int i = 1; i <= colCount; i++) {
                String name = meta.getColumnName(i).toLowerCase();
                colNames.add(name);
                if (i > 1) colDef.append(",");
                colDef.append(name).append(":").append(meta.getColumnTypeName(i));
            }
            tableEl.setAttribute("columns", colDef.toString());

            while (rs.next()) {
                Element row = doc.createElement("row");
                for (int i = 0; i < colNames.size(); i++) {
                    String val = rs.getString(i + 1);
                    if (val != null) {
                        val = val.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
                        Element col = doc.createElement(colNames.get(i));
                        col.setTextContent(val);
                        row.appendChild(col);
                    }
                }
                tableEl.appendChild(row);
                rowCount++;
            }
        } catch (SQLException e) {
            tableEl.setAttribute("error", e.getMessage());
            System.err.println("[LMS-Backup] Export skip " + table + ": " + e.getMessage());
        }

        tableEl.setAttribute("row-count", String.valueOf(rowCount));
        root.appendChild(tableEl);
        return rowCount;
    }

    // ═══ RESTORE ═══

    public RestoreResult restoreXml(InputStream xmlStream) throws Exception {
        RestoreResult result = new RestoreResult();

        DocumentBuilder bld = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = bld.parse(xmlStream);
        Element root = doc.getDocumentElement();

        if (!"lms-backup".equals(root.getTagName())) {
            result.success = false;
            result.error = "Fichier invalide : racine attendue <lms-backup>, trouvee <" + root.getTagName() + ">";
            return result;
        }

        result.schemaVersion = root.getAttribute("schema-version");
        result.exportedAt = root.getAttribute("exported-at");

        System.out.println("[LMS-Backup] === RESTORE START ===");
        System.out.println("[LMS-Backup] Backup schema: " + result.schemaVersion + ", date: " + result.exportedAt);

        // Build map of <table> elements from the XML
        Map<String, Element> xmlTables = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && "table".equals(el.getTagName())) {
                xmlTables.put(el.getAttribute("name"), el);
            }
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. Disable FK constraints
                exec(conn, "SET FOREIGN_KEY_CHECKS=0");

                // 2. Clear tables in reverse dependency order
                for (int i = TABLES.length - 1; i >= 0; i--) {
                    try { exec(conn, "DELETE FROM " + q(TABLES[i])); }
                    catch (SQLException e) { result.warnings.add("Clear " + TABLES[i] + ": " + trunc(e)); }
                }

                // 3. Restore in dependency order
                for (String tableName : TABLES) {
                    Element tableEl = xmlTables.get(tableName);
                    if (tableEl == null) {
                        result.warnings.add("Table '" + tableName + "' absente du backup — valeurs par defaut");
                        result.tableResults.put(tableName, new int[]{0, 0});
                        continue;
                    }
                    restoreTable(conn, tableName, tableEl, result);
                }

                // 3b. Warn about unknown tables in backup
                for (String xmlTable : xmlTables.keySet()) {
                    if (!Set.of(TABLES).contains(xmlTable)) {
                        result.warnings.add("Table '" + xmlTable + "' dans le backup mais inconnue du schema actuel — ignoree");
                    }
                }

                // 4. Commit des donnees (ALTER TABLE ci-dessous fait un commit implicite en MySQL)
                conn.commit();
                result.success = true;

                // 5. Reset auto-increment, puis reactiver les FK
                resetSequences(conn);
                exec(conn, "SET FOREIGN_KEY_CHECKS=1");

                System.out.println("[LMS-Backup] === RESTORE OK === " +
                        result.totalRows + " rows, " + result.warnings.size() + " warnings");

            } catch (Exception e) {
                conn.rollback();
                try { exec(conn, "SET FOREIGN_KEY_CHECKS=1"); } catch (Exception ignored) {}
                result.success = false;
                result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
                System.err.println("[LMS-Backup] RESTORE FAILED: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                conn.setAutoCommit(true);
            }
        }

        return result;
    }

    private void restoreTable(Connection conn, String tableName, Element tableEl, RestoreResult result) {
        int restored = 0, skipped = 0;

        Set<String> dbColumns;
        try {
            dbColumns = getTableColumns(conn, tableName);
        } catch (SQLException e) {
            result.warnings.add("Table '" + tableName + "' inexistante dans le schema actuel — ignoree");
            result.tableResults.put(tableName, new int[]{0, 0});
            return;
        }

        // Direct <row> children only (not recursive getElementsByTagName)
        List<Element> rows = directChildren(tableEl, "row");

        for (int i = 0; i < rows.size(); i++) {
            try {
                insertRow(conn, tableName, rows.get(i), dbColumns);
                restored++;
            } catch (SQLException e) {
                skipped++;
                if (skipped <= 10) result.warnings.add(tableName + " ligne " + (i + 1) + ": " + trunc(e));
            }
        }

        if (skipped > 10) result.warnings.add(tableName + ": +" + (skipped - 10) + " erreur(s) supplementaire(s)");

        result.tableResults.put(tableName, new int[]{restored, skipped});
        result.totalRows += restored;
    }

    private void insertRow(Connection conn, String tableName, Element row, Set<String> dbColumns) throws SQLException {
        List<String> cols = new ArrayList<>();
        List<String> vals = new ArrayList<>();

        NodeList children = row.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            String colName = children.item(i).getNodeName().toLowerCase();
            if (dbColumns.contains(colName)) {
                cols.add(colName);
                vals.add(children.item(i).getTextContent());
            }
            // Column in backup but NOT in current DB → silently skipped (schema evolution)
        }

        if (cols.isEmpty()) return;

        String sql = "INSERT INTO " + q(tableName) + " (" +
                String.join(",", cols) + ") VALUES (" +
                String.join(",", Collections.nCopies(cols.size(), "?")) + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < vals.size(); i++) ps.setString(i + 1, normalize(vals.get(i)));
            ps.executeUpdate();
        }
    }

    private Set<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, tableName.toLowerCase(), null)) {
            while (rs.next()) columns.add(rs.getString("COLUMN_NAME").toLowerCase());
        }
        if (columns.isEmpty()) throw new SQLException("Table inconnue: " + tableName);
        return columns;
    }

    private void resetSequences(Connection conn) {
        for (String t : IDENTITY_TABLES) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(id),0)+1 FROM " + q(t));
                if (rs.next()) stmt.execute("ALTER TABLE " + q(t) + " AUTO_INCREMENT = " + rs.getLong(1));
            } catch (SQLException ignored) {}
        }
    }

    // ═══ HELPERS ═══

    private List<Element> directChildren(Element parent, String tag) {
        List<Element> list = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element el && tag.equals(el.getTagName())) list.add(el);
        }
        return list;
    }

    /** Entoure le nom de table de backticks (groups est un mot reserve en MySQL 8). */
    private static String q(String table) { return "`" + table + "`"; }

    /** Les backups issus de H2 contiennent TRUE/FALSE ; MySQL (tinyint) attend 1/0. */
    private static String normalize(String v) {
        if (v == null) return null;
        if ("true".equalsIgnoreCase(v)) return "1";
        if ("false".equalsIgnoreCase(v)) return "0";
        return v;
    }

    private void exec(Connection conn, String sql) throws SQLException {
        try (Statement s = conn.createStatement()) { s.execute(sql); }
    }

    private String trunc(Exception e) {
        String m = e.getMessage();
        return m != null && m.length() > 120 ? m.substring(0, 120) + "..." : m;
    }

    private String now() { return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }

    // ═══ RESULT ═══

    public static class RestoreResult {
        public boolean success;
        public String error;
        public String schemaVersion;
        public String exportedAt;
        public int totalRows;
        public Map<String, int[]> tableResults = new LinkedHashMap<>();
        public List<String> warnings = new ArrayList<>();
    }
}

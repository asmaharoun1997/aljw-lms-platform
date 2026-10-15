package com.lms.platform.model;

import java.sql.Timestamp;

/**
 * Plugin model — represents a deployable module/application
 * that extends the LMS portal with additional features.
 *
 * A plugin is a ZIP containing:
 *   - plugin.xml (descriptor)
 *   - index.html (entry point)
 *   - Any additional HTML/JS/CSS/assets
 *
 * Plugins are served via /plugin/{pluginId}/resource
 * and appear as nav items in the sidebar based on role.
 */
public class Plugin {
    private long id;
    private String pluginId;       // unique slug: "inventory-manager"
    private String name;           // display name: "Gestion d'inventaire"
    private String description;
    private String icon;           // emoji: "📦"
    private String version;
    private String author;
    private String entryPoint;     // default: "index.html"
    private String roles;          // comma-separated: "ADMIN,FORMATEUR" or "*" for all
    private String category;       // "Outils", "Formation", "Gestion", etc.
    private Status status;
    private String configJson;     // plugin-specific settings (JSON)
    private int sortOrder;         // sidebar display order
    private Timestamp installedAt;
    private Timestamp updatedAt;

    public enum Status { ACTIVE, DISABLED, ERROR }

    // ── Getters/Setters ──
    public long getId(){return id;}public void setId(long v){id=v;}
    public String getPluginId(){return pluginId;}public void setPluginId(String v){pluginId=v;}
    public String getName(){return name;}public void setName(String v){name=v;}
    public String getDescription(){return description;}public void setDescription(String v){description=v;}
    public String getIcon(){return icon;}public void setIcon(String v){icon=v;}
    public String getVersion(){return version;}public void setVersion(String v){version=v;}
    public String getAuthor(){return author;}public void setAuthor(String v){author=v;}
    public String getEntryPoint(){return entryPoint;}public void setEntryPoint(String v){entryPoint=v;}
    public String getRoles(){return roles;}public void setRoles(String v){roles=v;}
    public String getCategory(){return category;}public void setCategory(String v){category=v;}
    public Status getStatus(){return status;}public void setStatus(Status v){status=v;}
    public String getConfigJson(){return configJson;}public void setConfigJson(String v){configJson=v;}
    public int getSortOrder(){return sortOrder;}public void setSortOrder(int v){sortOrder=v;}
    public Timestamp getInstalledAt(){return installedAt;}public void setInstalledAt(Timestamp v){installedAt=v;}
    public Timestamp getUpdatedAt(){return updatedAt;}public void setUpdatedAt(Timestamp v){updatedAt=v;}

    /** Check if a given role has access to this plugin */
    public boolean isAccessibleBy(String role) {
        if (status != Status.ACTIVE) return false;
        if ("*".equals(roles) || roles == null || roles.isBlank()) return true;
        for (String r : roles.split(",")) {
            if (r.trim().equalsIgnoreCase(role)) return true;
        }
        return false;
    }
}

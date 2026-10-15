package com.lms.platform.model;
import java.sql.Timestamp;
public class User {
    public enum Role { ADMIN, FORMATEUR, ETUDIANT, GED }
    private long id;
    private String username, passwordHash, nom, prenom, email;
    private String photoUrl, bio;
    private Role role;
    private boolean active, mustChangePassword;
    private Timestamp createdAt, lastLogin;
    public User(){}
    public long getId(){return id;}public void setId(long v){id=v;}
    public String getUsername(){return username;}public void setUsername(String v){username=v;}
    public String getPasswordHash(){return passwordHash;}public void setPasswordHash(String v){passwordHash=v;}
    public String getNom(){return nom;}public void setNom(String v){nom=v;}
    public String getPrenom(){return prenom;}public void setPrenom(String v){prenom=v;}
    public String getEmail(){return email;}public void setEmail(String v){email=v;}
    public String getPhotoUrl(){return photoUrl;}public void setPhotoUrl(String v){photoUrl=v;}
    public String getBio(){return bio;}public void setBio(String v){bio=v;}
    public Role getRole(){return role;}public void setRole(Role v){role=v;}
    public boolean isActive(){return active;}public void setActive(boolean v){active=v;}
    public boolean isMustChangePassword(){return mustChangePassword;}public void setMustChangePassword(boolean v){mustChangePassword=v;}
    public Timestamp getCreatedAt(){return createdAt;}public void setCreatedAt(Timestamp v){createdAt=v;}
    public Timestamp getLastLogin(){return lastLogin;}public void setLastLogin(Timestamp v){lastLogin=v;}
    public String displayName(){return prenom+" "+nom;}
}

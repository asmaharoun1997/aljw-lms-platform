package com.lms.platform.model;
import java.sql.Timestamp;
public class AuditLog {
    private long id, userId;
    private String userName, action, details, ipAddress, sessionId;
    private Timestamp createdAt;
    public AuditLog(){}
    public long getId(){return id;}public void setId(long v){id=v;}
    public long getUserId(){return userId;}public void setUserId(long v){userId=v;}
    public String getUserName(){return userName;}public void setUserName(String v){userName=v;}
    public String getAction(){return action;}public void setAction(String v){action=v;}
    public String getDetails(){return details;}public void setDetails(String v){details=v;}
    public String getIpAddress(){return ipAddress;}public void setIpAddress(String v){ipAddress=v;}
    public String getSessionId(){return sessionId;}public void setSessionId(String v){sessionId=v;}
    public Timestamp getCreatedAt(){return createdAt;}public void setCreatedAt(Timestamp v){createdAt=v;}
}

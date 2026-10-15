package com.lms.platform.model;
import java.sql.Timestamp;
public class Group {
    private long id; private String name, description; private Timestamp createdAt;
    public Group(){}
    public long getId(){return id;}public void setId(long v){id=v;}
    public String getName(){return name;}public void setName(String v){name=v;}
    public String getDescription(){return description;}public void setDescription(String v){description=v;}
    public Timestamp getCreatedAt(){return createdAt;}public void setCreatedAt(Timestamp v){createdAt=v;}
}
